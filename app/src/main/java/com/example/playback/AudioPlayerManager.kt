package com.example.playback

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.example.data.TrackEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

enum class PlaybackRepeatMode {
    OFF,
    ALL,
    ONE
}

class AudioPlayerManager(private val context: Context) {

    companion object {
        private const val TAG = "AudioPlayerManager"
        private const val PREFS_NAME = "ritmo_audio_prefs"
        private const val KEY_AUDIO_ENGINE = "selected_audio_engine"
        private const val KEY_ENGINE_PROMPTED = "engine_selection_prompted"
    }

    private val sharedPrefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val playerScope = CoroutineScope(Dispatchers.Main + Job())
    private var progressJob: Job? = null

    private val exoPlayer: ExoPlayer = ExoPlayer.Builder(context).build()

    private val _activeEngine = MutableStateFlow(loadSavedEngine())
    val activeEngine: StateFlow<AudioEngineType> = _activeEngine.asStateFlow()

    private val _currentTrack = MutableStateFlow<TrackEntity?>(null)
    val currentTrack: StateFlow<TrackEntity?> = _currentTrack.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    private val _repeatMode = MutableStateFlow(PlaybackRepeatMode.OFF)
    val repeatMode: StateFlow<PlaybackRepeatMode> = _repeatMode.asStateFlow()

    private val _isShuffle = MutableStateFlow(false)
    val isShuffle: StateFlow<Boolean> = _isShuffle.asStateFlow()

    private var currentPlaylist: List<TrackEntity> = emptyList()
    private var shuffledIndices: List<Int> = emptyList()
    private var currentShuffleIndex = 0

    init {
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
                                (_currentTrack.value?.durationMs ?: 0L)
                            }
                        }
                        Player.STATE_ENDED -> {
                            handleTrackEnded()
                        }
                        else -> {}
                    }
                }
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

    fun hasPromptedEngineSelection(): Boolean {
        return sharedPrefs.getBoolean(KEY_ENGINE_PROMPTED, false)
    }

    fun markEngineSelectionPrompted() {
        sharedPrefs.edit().putBoolean(KEY_ENGINE_PROMPTED, true).apply()
    }

    fun setAudioEngine(newEngine: AudioEngineType) {
        if (_activeEngine.value == newEngine) return
        val wasPlaying = _isPlaying.value
        val track = _currentTrack.value
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

        // Reanudar en el nuevo motor si había pista activa
        if (track != null) {
            if (newEngine == AudioEngineType.OBOE_CPP && OboeAudioBridge.isNativeReady()) {
                OboeAudioBridge.nativeInit()
                val loaded = OboeAudioBridge.nativeLoadFile(track.filePath)
                if (loaded) {
                    OboeAudioBridge.nativeSeekTo(position)
                    if (wasPlaying) {
                        OboeAudioBridge.nativePlay()
                        _isPlaying.value = true
                        startProgressTracker()
                    }
                } else {
                    // Fallback a ExoPlayer si el decodificador nativo no pudo abrir el archivo
                    _activeEngine.value = AudioEngineType.EXOPLAYER
                    playTrackWithExoPlayer(track, position, wasPlaying)
                }
            } else {
                playTrackWithExoPlayer(track, position, wasPlaying)
            }
        }
    }

    private fun playTrackWithExoPlayer(track: TrackEntity, position: Long = 0L, autoPlay: Boolean = true) {
        val file = File(track.filePath)
        val uri = if (file.exists()) Uri.fromFile(file) else Uri.parse(track.filePath)
        val mediaItem = MediaItem.fromUri(uri)
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
        if (position > 0) {
            exoPlayer.seekTo(position)
        }
        if (autoPlay) {
            exoPlayer.play()
            _isPlaying.value = true
            startProgressTracker()
        }
    }

    fun playTrack(track: TrackEntity, playlist: List<TrackEntity>) {
        currentPlaylist = playlist
        updateShuffleIndices()
        _currentTrack.value = track
        _duration.value = if (track.durationMs > 0) track.durationMs else 0L

        if (_activeEngine.value == AudioEngineType.OBOE_CPP && OboeAudioBridge.isNativeReady()) {
            exoPlayer.pause()
            OboeAudioBridge.nativeInit()
            val loaded = OboeAudioBridge.nativeLoadFile(track.filePath)
            if (loaded) {
                OboeAudioBridge.nativePlay()
                _isPlaying.value = true
                val oboeDur = OboeAudioBridge.nativeGetDuration()
                if (oboeDur > 0) {
                    _duration.value = oboeDur
                }
                startProgressTracker()
                return
            } else {
                Log.w(TAG, "Oboe engine failed to load track, falling back to ExoPlayer")
            }
        }

        // ExoPlayer
        if (OboeAudioBridge.isNativeReady()) {
            OboeAudioBridge.nativePause()
        }
        playTrackWithExoPlayer(track, 0L, autoPlay = true)
    }

    fun playPause() {
        if (_currentTrack.value == null && currentPlaylist.isNotEmpty()) {
            playTrack(currentPlaylist[0], currentPlaylist)
            return
        }

        if (_activeEngine.value == AudioEngineType.OBOE_CPP && OboeAudioBridge.isNativeReady()) {
            if (_isPlaying.value) {
                OboeAudioBridge.nativePause()
                _isPlaying.value = false
                stopProgressTracker()
            } else {
                OboeAudioBridge.nativePlay()
                _isPlaying.value = true
                startProgressTracker()
            }
        } else {
            if (exoPlayer.isPlaying) {
                exoPlayer.pause()
                _isPlaying.value = false
                stopProgressTracker()
            } else {
                exoPlayer.play()
                _isPlaying.value = true
                startProgressTracker()
            }
        }
    }

    fun seekTo(positionMs: Long) {
        val clamped = positionMs.coerceIn(0L, _duration.value.coerceAtLeast(0L))
        _currentPosition.value = clamped

        if (_activeEngine.value == AudioEngineType.OBOE_CPP && OboeAudioBridge.isNativeReady()) {
            OboeAudioBridge.nativeSeekTo(clamped)
        } else {
            exoPlayer.seekTo(clamped)
        }
    }

    fun next() {
        if (currentPlaylist.isEmpty()) return
        val current = _currentTrack.value ?: return
        val currentIndex = currentPlaylist.indexOfFirst { it.id == current.id }
        if (currentIndex == -1) return

        if (_isShuffle.value && shuffledIndices.isNotEmpty()) {
            currentShuffleIndex = (currentShuffleIndex + 1) % shuffledIndices.size
            val nextTrackIndex = shuffledIndices[currentShuffleIndex]
            playTrack(currentPlaylist[nextTrackIndex], currentPlaylist)
        } else {
            val nextIndex = currentIndex + 1
            if (nextIndex < currentPlaylist.size) {
                playTrack(currentPlaylist[nextIndex], currentPlaylist)
            } else if (_repeatMode.value == PlaybackRepeatMode.ALL) {
                playTrack(currentPlaylist[0], currentPlaylist)
            }
        }
    }

    fun previous() {
        if (currentPlaylist.isEmpty()) return
        val curPos = _currentPosition.value
        if (curPos > 3000L) {
            seekTo(0L)
            return
        }

        val current = _currentTrack.value ?: return
        val currentIndex = currentPlaylist.indexOfFirst { it.id == current.id }
        if (currentIndex == -1) return

        if (_isShuffle.value && shuffledIndices.isNotEmpty()) {
            currentShuffleIndex = if (currentShuffleIndex - 1 < 0) {
                shuffledIndices.lastIndex
            } else {
                currentShuffleIndex - 1
            }
            val prevTrackIndex = shuffledIndices[currentShuffleIndex]
            playTrack(currentPlaylist[prevTrackIndex], currentPlaylist)
        } else {
            val prevIndex = currentIndex - 1
            if (prevIndex >= 0) {
                playTrack(currentPlaylist[prevIndex], currentPlaylist)
            } else if (_repeatMode.value == PlaybackRepeatMode.ALL) {
                playTrack(currentPlaylist[currentPlaylist.lastIndex], currentPlaylist)
            } else {
                seekTo(0L)
            }
        }
    }

    fun toggleRepeat() {
        _repeatMode.value = when (_repeatMode.value) {
            PlaybackRepeatMode.OFF -> PlaybackRepeatMode.ALL
            PlaybackRepeatMode.ALL -> PlaybackRepeatMode.ONE
            PlaybackRepeatMode.ONE -> PlaybackRepeatMode.OFF
        }
    }

    fun toggleShuffle() {
        _isShuffle.value = !_isShuffle.value
        if (_isShuffle.value) {
            updateShuffleIndices()
        }
    }

    private fun updateShuffleIndices() {
        if (currentPlaylist.isEmpty()) return
        shuffledIndices = currentPlaylist.indices.shuffled()
        val current = _currentTrack.value
        if (current != null) {
            val idx = currentPlaylist.indexOfFirst { it.id == current.id }
            val shuffPos = shuffledIndices.indexOf(idx)
            if (shuffPos != -1) {
                currentShuffleIndex = shuffPos
            }
        }
    }

    private fun startProgressTracker() {
        progressJob?.cancel()
        progressJob = playerScope.launch {
            while (isActive) {
                if (_activeEngine.value == AudioEngineType.OBOE_CPP && OboeAudioBridge.isNativeReady()) {
                    val pos = OboeAudioBridge.nativeGetPosition()
                    val dur = OboeAudioBridge.nativeGetDuration()
                    val isPlayingNow = OboeAudioBridge.nativeIsPlaying()

                    _currentPosition.value = pos
                    if (dur > 0) _duration.value = dur
                    if (_isPlaying.value != isPlayingNow) {
                        _isPlaying.value = isPlayingNow
                        if (!isPlayingNow && dur > 0 && pos >= dur - 200) {
                            handleTrackEnded()
                            break
                        }
                    }
                } else {
                    _currentPosition.value = exoPlayer.currentPosition
                    if (exoPlayer.duration > 0) {
                        _duration.value = exoPlayer.duration
                    }
                }
                delay(200L)
            }
        }
    }

    private fun stopProgressTracker() {
        progressJob?.cancel()
        if (_activeEngine.value == AudioEngineType.OBOE_CPP && OboeAudioBridge.isNativeReady()) {
            _currentPosition.value = OboeAudioBridge.nativeGetPosition()
        } else {
            _currentPosition.value = exoPlayer.currentPosition
        }
    }

    private fun handleTrackEnded() {
        when (_repeatMode.value) {
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
                val current = _currentTrack.value
                val currentIndex = currentPlaylist.indexOfFirst { it.id == current?.id }
                if (currentIndex != -1 && currentIndex < currentPlaylist.lastIndex) {
                    next()
                } else {
                    _isPlaying.value = false
                    seekTo(0L)
                    stopProgressTracker()
                }
            }
        }
    }

    fun release() {
        stopProgressTracker()
        exoPlayer.release()
        if (OboeAudioBridge.isNativeReady()) {
            OboeAudioBridge.nativeRelease()
        }
    }
}
