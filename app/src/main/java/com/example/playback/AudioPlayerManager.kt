package com.example.playback

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import com.example.data.TrackEntity
import com.example.debug.DebugLogLevel
import com.example.debug.DebugLogManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Modo de repetición de la reproducción.
 */
enum class PlaybackRepeatMode {
    OFF,
    ALL,
    ONE
}

/**
 * Orquestador principal de reproducción de audio para Ritmo.
 *
 * Arquitectura modular:
 * - Delega la gestión de cola, shuffle y repetición en [PlaybackQueueManager].
 * - Delega la configuración del ecualizador DSP de 10 bandas en [EqualizerController].
 * - Orquesta la conmutación sin fisuras entre ExoPlayer (Media3) y Google Oboe (C++ Nativo).
 */
class AudioPlayerManager(private val context: Context) {

    companion object {
        private const val TAG = "AudioPlayerManager"
        private const val PREFS_NAME = "ritmo_audio_prefs"
        private const val KEY_AUDIO_ENGINE = "selected_audio_engine"
        private const val KEY_ENGINE_PROMPTED = "engine_selection_prompted"

        @Volatile
        private var instance: AudioPlayerManager? = null

        fun getInstance(context: Context): AudioPlayerManager {
            return instance ?: synchronized(this) {
                instance ?: AudioPlayerManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val sharedPrefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val playerScope = CoroutineScope(Dispatchers.Main + Job())
    private var progressJob: Job? = null
    private var playJob: Job? = null

    // Submódulos desacoplados
    val queueManager = PlaybackQueueManager()
    val equalizerController = EqualizerController(context) { enabled, gains ->
        applyEqualizerToEngines(enabled, gains)
    }

    private val equalizerAudioProcessor = Media3EqualizerAudioProcessor()

    private val renderersFactory = object : DefaultRenderersFactory(context) {
        override fun buildAudioSink(
            context: Context,
            enableFloatOutput: Boolean,
            enableAudioTrackPlaybackParams: Boolean
        ): AudioSink? {
            return DefaultAudioSink.Builder(context)
                .setAudioProcessors(arrayOf(equalizerAudioProcessor))
                .setEnableFloatOutput(enableFloatOutput)
                .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                .build()
        }
    }

    val exoPlayer: ExoPlayer = ExoPlayer.Builder(context, renderersFactory).build()

    private val _activeEngine = MutableStateFlow(loadSavedEngine())
    val activeEngine: StateFlow<AudioEngineType> = _activeEngine.asStateFlow()

    val currentTrack: StateFlow<TrackEntity?> = queueManager.currentTrack
    val isShuffle: StateFlow<Boolean> = queueManager.isShuffle
    val repeatMode: StateFlow<PlaybackRepeatMode> = queueManager.repeatMode

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    val isEqualizerEnabled: StateFlow<Boolean> = equalizerController.isEqualizerEnabled
    val equalizerBandGains: StateFlow<List<Float>> = equalizerController.equalizerBandGains

    init {
        // Inicializar Oboe y sincronizar ecualizador
        if (OboeAudioBridge.isNativeReady()) {
            OboeAudioBridge.nativeInit()
        }
        equalizerController.initialize()

        exoPlayer.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (_activeEngine.value == AudioEngineType.EXOPLAYER) {
                    _isPlaying.value = isPlaying
                    if (isPlaying) {
                        startProgressTracker()
                    } else {
                        stopProgressTracker()
                    }
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (_activeEngine.value == AudioEngineType.EXOPLAYER) {
                    when (playbackState) {
                        Player.STATE_READY -> {
                            _duration.value = if (exoPlayer.duration > 0) {
                                exoPlayer.duration
                            } else {
                                (currentTrack.value?.durationMs ?: 0L)
                            }
                        }
                        Player.STATE_ENDED -> {
                            handleTrackEnded()
                        }
                        else -> {}
                    }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                val errorCode = error.errorCode
                val errorName = error.errorCodeName
                val msg = "ExoPlayer Error [$errorCode - $errorName]: ${error.message}"
                Log.e(TAG, msg, error)
                DebugLogManager.logError(
                    tag = "ExoPlayer",
                    message = msg,
                    rawErrorCode = errorCode,
                    throwable = error,
                    details = "Caused by: ${error.cause?.message}"
                )
            }
        })
    }

    private fun loadSavedEngine(): AudioEngineType {
        val saved = sharedPrefs.getString(KEY_AUDIO_ENGINE, AudioEngineType.EXOPLAYER.name)
        return try {
            AudioEngineType.valueOf(saved ?: AudioEngineType.EXOPLAYER.name)
        } catch (_: Exception) {
            AudioEngineType.EXOPLAYER
        }
    }

    // Funciones de ecualizador delegadas a EqualizerController
    fun setEqualizerEnabled(enabled: Boolean) = equalizerController.setEqualizerEnabled(enabled)
    fun setEqualizerBandGain(bandIndex: Int, gainDb: Float) = equalizerController.setEqualizerBandGain(bandIndex, gainDb)
    fun setEqualizerPreset(preset: EqualizerPreset) = equalizerController.setEqualizerPreset(preset)
    fun resetEqualizer() = equalizerController.resetEqualizer()

    private fun applyEqualizerToEngines(enabled: Boolean, gains: List<Float>) {
        if (OboeAudioBridge.isNativeReady()) {
            OboeAudioBridge.nativeSetEqualizerEnabled(enabled)
            gains.forEachIndexed { index, gain ->
                OboeAudioBridge.nativeSetEqualizerBandGain(index, gain)
            }
        }
    }

    fun startBackgroundService() {
        try {
            val intent = Intent(context, RitmoMediaSessionService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "No se pudo iniciar el servicio MediaSessionService", e)
        }
    }

    fun hasPromptedEngineSelection(): Boolean = sharedPrefs.getBoolean(KEY_ENGINE_PROMPTED, false)
    fun markEngineSelectionPrompted() = sharedPrefs.edit().putBoolean(KEY_ENGINE_PROMPTED, true).apply()

    fun setAudioEngine(newEngine: AudioEngineType) {
        if (_activeEngine.value == newEngine) return
        val wasPlaying = _isPlaying.value
        val track = currentTrack.value
        val position = _currentPosition.value

        // Detener motor anterior
        if (_activeEngine.value == AudioEngineType.EXOPLAYER) {
            exoPlayer.pause()
        } else {
            if (OboeAudioBridge.isNativeReady()) {
                OboeAudioBridge.nativePause()
            }
        }
        stopProgressTracker()

        _activeEngine.value = newEngine
        sharedPrefs.edit().putString(KEY_AUDIO_ENGINE, newEngine.name).apply()
        markEngineSelectionPrompted()
        Log.i(TAG, "Audio engine switched to: ${newEngine.name}")

        // Reanudar en caliente en el nuevo motor
        if (track != null) {
            playJob?.cancel()
            playJob = playerScope.launch(Dispatchers.IO) {
                if (newEngine == AudioEngineType.OBOE_CPP && OboeAudioBridge.isNativeReady()) {
                    OboeAudioBridge.nativeInit()
                    val loaded = OboeAudioBridge.nativeLoadFile(track.filePath)
                    if (loaded) {
                        OboeAudioBridge.nativeSeekTo(position)
                        if (wasPlaying) {
                            OboeAudioBridge.nativePlay()
                            withContext(Dispatchers.Main) {
                                _isPlaying.value = true
                                startProgressTracker()
                            }
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            _activeEngine.value = AudioEngineType.EXOPLAYER
                            playTrackWithExoPlayer(track, position, wasPlaying)
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        playTrackWithExoPlayer(track, position, wasPlaying)
                    }
                }
            }
        }
    }

    fun updatePlaylist(tracks: List<TrackEntity>) {
        queueManager.updatePlaylist(tracks)
    }

    fun updateCurrentTrack(track: TrackEntity) {
        queueManager.updateCurrentTrack(track)
    }

    fun playTrack(track: TrackEntity, playlist: List<TrackEntity> = emptyList()) {
        if (playlist.isNotEmpty()) {
            queueManager.updatePlaylist(playlist)
        }
        queueManager.selectTrack(track)
        playCurrentTrack()
    }

    private fun playCurrentTrack() {
        val track = currentTrack.value ?: return
        playJob?.cancel()
        playJob = playerScope.launch(Dispatchers.IO) {
            val audioFile = File(track.filePath)
            if (!audioFile.exists()) {
                val msg = "Archivo de audio no encontrado: ${track.filePath}"
                Log.e(TAG, msg)
                DebugLogManager.log(
                    tag = "AudioPlayerManager",
                    message = msg,
                    level = DebugLogLevel.ERROR,
                    rawErrorCode = 404,
                    details = "Pista: ${track.title} por ${track.artist}"
                )
                withContext(Dispatchers.Main) {
                    _isPlaying.value = false
                    stopProgressTracker()
                }
                return@launch
            }

            if (_activeEngine.value == AudioEngineType.OBOE_CPP && OboeAudioBridge.isNativeReady()) {
                try {
                    OboeAudioBridge.nativeInit()
                    val loaded = OboeAudioBridge.nativeLoadFile(track.filePath)
                    if (loaded) {
                        OboeAudioBridge.nativePlay()
                        withContext(Dispatchers.Main) {
                            _isPlaying.value = true
                            _duration.value = if (track.durationMs > 0) track.durationMs else OboeAudioBridge.nativeGetDuration()
                            _currentPosition.value = 0L
                            startProgressTracker()
                        }
                        return@launch
                    } else {
                        val oboeErr = OboeAudioBridge.getLastErrorCodeSafe()
                        val oboeErrStr = OboeAudioBridge.getLastErrorStringSafe()
                        Log.w(TAG, "Oboe no pudo abrir $track (Error $oboeErr: $oboeErrStr). Fallback a ExoPlayer.")
                        DebugLogManager.log(
                            tag = "OboePlayer",
                            message = "Fallo decodificación Oboe C++ ($oboeErr: $oboeErrStr). Conmutando a ExoPlayer.",
                            level = DebugLogLevel.WARN,
                            rawErrorCode = oboeErr
                        )
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error en motor Oboe C++", e)
                }
            }

            // ExoPlayer (Estándar o Fallback)
            withContext(Dispatchers.Main) {
                playTrackWithExoPlayer(track, 0L, true)
            }
        }
    }

    private fun playTrackWithExoPlayer(track: TrackEntity, startPositionMs: Long = 0L, playWhenReady: Boolean = true) {
        val mediaMetadata = MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(track.artist)
            .setAlbumTitle(track.album)
            .setArtworkUri(track.artworkPath?.let { Uri.fromFile(File(it)) })
            .build()

        val mediaItem = MediaItem.Builder()
            .setUri(Uri.fromFile(File(track.filePath)))
            .setMediaMetadata(mediaMetadata)
            .build()

        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
        if (startPositionMs > 0L) {
            exoPlayer.seekTo(startPositionMs)
        }
        exoPlayer.playWhenReady = playWhenReady
        if (playWhenReady) {
            _isPlaying.value = true
            startProgressTracker()
        }
    }

    fun play() {
        if (_activeEngine.value == AudioEngineType.OBOE_CPP && OboeAudioBridge.isNativeReady()) {
            OboeAudioBridge.nativePlay()
            _isPlaying.value = true
            startProgressTracker()
        } else {
            exoPlayer.play()
        }
    }

    fun pause() {
        if (_activeEngine.value == AudioEngineType.OBOE_CPP && OboeAudioBridge.isNativeReady()) {
            OboeAudioBridge.nativePause()
            _isPlaying.value = false
            stopProgressTracker()
        } else {
            exoPlayer.pause()
        }
    }

    fun playPause() {
        if (_isPlaying.value) {
            pause()
        } else {
            play()
        }
    }

    fun seekTo(positionMs: Long) {
        _currentPosition.value = positionMs
        if (_activeEngine.value == AudioEngineType.OBOE_CPP && OboeAudioBridge.isNativeReady()) {
            OboeAudioBridge.nativeSeekTo(positionMs)
        } else {
            exoPlayer.seekTo(positionMs)
        }
    }

    fun next() {
        val nextTrack = queueManager.getNextTrack(forceAdvance = true)
        if (nextTrack != null) {
            playCurrentTrack()
        } else {
            seekTo(0L)
            pause()
        }
    }

    fun previous() {
        if (_currentPosition.value > 3000L) {
            seekTo(0L)
            return
        }
        val prevTrack = queueManager.getPreviousTrack()
        if (prevTrack != null) {
            playCurrentTrack()
        } else {
            seekTo(0L)
        }
    }

    fun toggleShuffle(): Boolean = queueManager.toggleShuffle()

    fun toggleRepeat(): PlaybackRepeatMode = queueManager.toggleRepeat()

    private fun startProgressTracker() {
        stopProgressTracker()
        progressJob = playerScope.launch {
            while (isActive) {
                if (_activeEngine.value == AudioEngineType.OBOE_CPP && OboeAudioBridge.isNativeReady()) {
                    val pos = OboeAudioBridge.nativeGetPosition()
                    val dur = OboeAudioBridge.nativeGetDuration()
                    _currentPosition.value = pos
                    if (dur > 0) _duration.value = dur
                    val isEnded = OboeAudioBridge.nativeIsPlaybackEnded() ||
                            (dur > 0 && pos >= (dur - 250)) ||
                            (!OboeAudioBridge.nativeIsPlaying() && dur > 0 && pos >= (dur - 1000).coerceAtLeast(0) && _isPlaying.value)
                    if (isEnded) {
                        Log.i(TAG, "Oboe track playback completed: pos=$pos, dur=$dur. Handling track transition.")
                        handleTrackEnded()
                        break
                    }
                } else {
                    _currentPosition.value = exoPlayer.currentPosition
                    if (exoPlayer.duration > 0) {
                        _duration.value = exoPlayer.duration
                    }
                }
                delay(200)
            }
        }
    }

    private fun stopProgressTracker() {
        progressJob?.cancel()
        progressJob = null
    }

    private fun handleTrackEnded() {
        when (repeatMode.value) {
            PlaybackRepeatMode.ONE -> {
                seekTo(0L)
                if (_activeEngine.value == AudioEngineType.OBOE_CPP && OboeAudioBridge.isNativeReady()) {
                    OboeAudioBridge.nativePlay()
                } else {
                    exoPlayer.play()
                }
                _isPlaying.value = true
                startProgressTracker()
            }
            PlaybackRepeatMode.ALL -> {
                next()
            }
            PlaybackRepeatMode.OFF -> {
                val nextTrack = queueManager.getNextTrack(forceAdvance = false)
                if (nextTrack != null) {
                    playCurrentTrack()
                } else {
                    _isPlaying.value = false
                    seekTo(0L)
                    stopProgressTracker()
                }
            }
        }
    }

    fun release() {
        playJob?.cancel()
        stopProgressTracker()
        exoPlayer.release()
        if (OboeAudioBridge.isNativeReady()) {
            OboeAudioBridge.nativeRelease()
        }
    }
}
