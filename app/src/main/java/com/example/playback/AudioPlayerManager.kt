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
        private const val KEY_SPATIAL_AUDIO_ENABLED = "spatial_audio_enabled"
        private const val KEY_SPATIAL_AUDIO_SPEED = "spatial_audio_speed"
        private const val KEY_SPATIAL_AUDIO_DEPTH = "spatial_audio_depth"
        private const val KEY_SPATIAL_AUDIO_REVERB = "spatial_audio_reverb"

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

    // Temporizador de Sueño (Sleep Timer) desacoplado
    val sleepTimerManager = SleepTimerManager(
        onPausePlayback = { pause() },
        onSetVolume = { vol -> setOutputVolume(vol) },
        onGetVolume = { getOutputVolume() }
    )

    // Audio Espacial 360° / Efecto 8D Nativo en C++ (Oboe Exclusivo)
    private val _isSpatialAudioEnabled = MutableStateFlow(sharedPrefs.getBoolean(KEY_SPATIAL_AUDIO_ENABLED, false))
    val isSpatialAudioEnabled: StateFlow<Boolean> = _isSpatialAudioEnabled.asStateFlow()

    private val _spatialAudioSpeed = MutableStateFlow(sharedPrefs.getFloat(KEY_SPATIAL_AUDIO_SPEED, 0.08f))
    val spatialAudioSpeed: StateFlow<Float> = _spatialAudioSpeed.asStateFlow()

    private val _spatialAudioDepth = MutableStateFlow(sharedPrefs.getFloat(KEY_SPATIAL_AUDIO_DEPTH, 0.85f))
    val spatialAudioDepth: StateFlow<Float> = _spatialAudioDepth.asStateFlow()

    private val _spatialAudioReverb = MutableStateFlow(sharedPrefs.getFloat(KEY_SPATIAL_AUDIO_REVERB, 0.22f))
    val spatialAudioReverb: StateFlow<Float> = _spatialAudioReverb.asStateFlow()

    init {
        // Inicializar Oboe y sincronizar ecualizador y audio espacial
        if (OboeAudioBridge.isNativeReady()) {
            OboeAudioBridge.nativeInit()
            OboeAudioBridge.setSpatialAudioEnabledSafe(_isSpatialAudioEnabled.value)
            OboeAudioBridge.setSpatialAudioSpeedSafe(_spatialAudioSpeed.value)
            OboeAudioBridge.setSpatialAudioDepthSafe(_spatialAudioDepth.value)
            OboeAudioBridge.setSpatialAudioReverbSafe(_spatialAudioReverb.value)
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

    // Funciones de volumen maestro con soporte para desvanecimiento suave (fade)
    fun setOutputVolume(vol: Float) {
        val clampedVol = vol.coerceIn(0.0f, 1.0f)
        exoPlayer.volume = clampedVol
        if (OboeAudioBridge.isNativeReady()) {
            OboeAudioBridge.nativeSetVolume(clampedVol)
        }
    }

    fun getOutputVolume(): Float {
        return if (_activeEngine.value == AudioEngineType.OBOE_CPP && OboeAudioBridge.isNativeReady()) {
            OboeAudioBridge.nativeGetVolume()
        } else {
            exoPlayer.volume
        }
    }

    // Funciones de Audio Espacial 360° / Efecto 8D Nativo (C++ Oboe)
    fun setSpatialAudioEnabled(enabled: Boolean) {
        _isSpatialAudioEnabled.value = enabled
        sharedPrefs.edit().putBoolean(KEY_SPATIAL_AUDIO_ENABLED, enabled).apply()
        OboeAudioBridge.setSpatialAudioEnabledSafe(enabled)
        Log.i(TAG, "Audio Espacial 360/8D configurado: $enabled")
    }

    fun setSpatialAudioSpeed(speedHz: Float) {
        val clamped = speedHz.coerceIn(0.01f, 0.5f)
        _spatialAudioSpeed.value = clamped
        sharedPrefs.edit().putFloat(KEY_SPATIAL_AUDIO_SPEED, clamped).apply()
        OboeAudioBridge.setSpatialAudioSpeedSafe(clamped)
    }

    fun setSpatialAudioDepth(depth: Float) {
        val clamped = depth.coerceIn(0.1f, 1.0f)
        _spatialAudioDepth.value = clamped
        sharedPrefs.edit().putFloat(KEY_SPATIAL_AUDIO_DEPTH, clamped).apply()
        OboeAudioBridge.setSpatialAudioDepthSafe(clamped)
    }

    fun setSpatialAudioReverb(reverb: Float) {
        val clamped = reverb.coerceIn(0.0f, 0.6f)
        _spatialAudioReverb.value = clamped
        sharedPrefs.edit().putFloat(KEY_SPATIAL_AUDIO_REVERB, clamped).apply()
        OboeAudioBridge.setSpatialAudioReverbSafe(clamped)
    }

    // Funciones del Temporizador de Sueño (Sleep Timer)
    fun startSleepTimer(minutes: Int) = sleepTimerManager.startTimer(minutes)
    fun startEndOfTrackSleepTimer() = sleepTimerManager.startEndOfTrackTimer()
    fun addSleepTimerMinutes(minutes: Int) = sleepTimerManager.addMinutes(minutes)
    fun cancelSleepTimer() = sleepTimerManager.cancelTimer()

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
                            // En modo Oboe, preparar ExoPlayer en silencio con los metadatos de la pista
                            // para que MediaSessionService y la notificación del sistema muestren la carátula,
                            // título, artista y controles multimedia en segundo plano
                            syncExoPlayerForMediaSession(track, 0L, isPlaying = true)
                            startPlaybackService()
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
                startPlaybackService()
                playTrackWithExoPlayer(track, 0L, true)
            }
        }
    }

    private fun startPlaybackService() {
        try {
            val serviceIntent = Intent(context, RitmoMediaSessionService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "No se pudo iniciar RitmoMediaSessionService", e)
        }
    }

    private fun syncExoPlayerForMediaSession(track: TrackEntity, startPositionMs: Long = 0L, isPlaying: Boolean) {
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

        // Sincronizar item de ExoPlayer con volumen 0 para el motor Oboe
        exoPlayer.volume = 0f
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
        if (startPositionMs > 0L) {
            exoPlayer.seekTo(startPositionMs)
        }
        exoPlayer.playWhenReady = isPlaying
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

        exoPlayer.volume = 1f
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
        startPlaybackService()
        if (_activeEngine.value == AudioEngineType.OBOE_CPP && OboeAudioBridge.isNativeReady()) {
            OboeAudioBridge.nativePlay()
            exoPlayer.playWhenReady = true
            _isPlaying.value = true
            startProgressTracker()
        } else {
            exoPlayer.play()
        }
    }

    fun pause() {
        if (_activeEngine.value == AudioEngineType.OBOE_CPP && OboeAudioBridge.isNativeReady()) {
            OboeAudioBridge.nativePause()
            exoPlayer.playWhenReady = false
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
            _currentPosition.value = 0L
            _isPlaying.value = true
            playCurrentTrack()
        } else {
            seekTo(0L)
            pause()
        }
    }

    fun previous() {
        if (_currentPosition.value > 3000L) {
            seekTo(0L)
            if (!_isPlaying.value) {
                play()
            }
            return
        }
        val prevTrack = queueManager.getPreviousTrack()
        if (prevTrack != null) {
            _currentPosition.value = 0L
            _isPlaying.value = true
            playCurrentTrack()
        } else {
            seekTo(0L)
            if (!_isPlaying.value) {
                play()
            }
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
        // Notificar al temporizador de sueño si está configurado en modo fin de pista
        if (sleepTimerManager.status.value.isActive && sleepTimerManager.status.value.isEndOfTrack) {
            sleepTimerManager.onTrackFinished()
            _isPlaying.value = false
            stopProgressTracker()
            return
        }

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
        sleepTimerManager.cancelTimer()
        exoPlayer.release()
        if (OboeAudioBridge.isNativeReady()) {
            OboeAudioBridge.nativeRelease()
        }
    }
}
