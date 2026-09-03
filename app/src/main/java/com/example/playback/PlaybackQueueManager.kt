package com.example.playback

import com.example.data.TrackEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Gestor modular de la cola de reproducción para Ritmo Music Player.
 *
 * Administra la lista de reproducción activa, el orden aleatorio (shuffle),
 * los índices de reproducción y la transición entre pistas (siguiente/anterior).
 */
class PlaybackQueueManager {

    private val _playlist = MutableStateFlow<List<TrackEntity>>(emptyList())
    val playlist: StateFlow<List<TrackEntity>> = _playlist.asStateFlow()

    private val _currentTrack = MutableStateFlow<TrackEntity?>(null)
    val currentTrack: StateFlow<TrackEntity?> = _currentTrack.asStateFlow()

    private val _isShuffle = MutableStateFlow(false)
    val isShuffle: StateFlow<Boolean> = _isShuffle.asStateFlow()

    private val _repeatMode = MutableStateFlow(PlaybackRepeatMode.OFF)
    val repeatMode: StateFlow<PlaybackRepeatMode> = _repeatMode.asStateFlow()

    private var shuffledIndices: List<Int> = emptyList()
    private var currentShuffleIndex = 0

    fun updatePlaylist(tracks: List<TrackEntity>, keepCurrentTrack: Boolean = true) {
        _playlist.value = tracks
        val current = _currentTrack.value
        if (tracks.isEmpty()) {
            _currentTrack.value = null
            shuffledIndices = emptyList()
            currentShuffleIndex = 0
            return
        }

        if (_isShuffle.value) {
            rebuildShuffleOrder()
        }

        if (!keepCurrentTrack || current == null || tracks.none { it.id == current.id }) {
            // No mantener o ya no existe
        }
    }

    fun updateCurrentTrack(track: TrackEntity) {
        if (_currentTrack.value?.id == track.id) {
            _currentTrack.value = track
        }
        val currentList = _playlist.value
        val idx = currentList.indexOfFirst { it.id == track.id }
        if (idx != -1) {
            val updated = currentList.toMutableList()
            updated[idx] = track
            _playlist.value = updated
        }
    }

    fun selectTrack(track: TrackEntity) {
        _currentTrack.value = track
        if (_isShuffle.value) {
            val idxInPlaylist = _playlist.value.indexOfFirst { it.id == track.id }
            val posInShuffle = shuffledIndices.indexOf(idxInPlaylist)
            currentShuffleIndex = if (posInShuffle >= 0) posInShuffle else 0
        }
    }

    fun toggleShuffle(): Boolean {
        val newShuffle = !_isShuffle.value
        _isShuffle.value = newShuffle
        if (newShuffle) {
            rebuildShuffleOrder()
        }
        return newShuffle
    }

    fun toggleRepeat(): PlaybackRepeatMode {
        val next = when (_repeatMode.value) {
            PlaybackRepeatMode.OFF -> PlaybackRepeatMode.ALL
            PlaybackRepeatMode.ALL -> PlaybackRepeatMode.ONE
            PlaybackRepeatMode.ONE -> PlaybackRepeatMode.OFF
        }
        _repeatMode.value = next
        return next
    }

    fun setRepeatMode(mode: PlaybackRepeatMode) {
        _repeatMode.value = mode
    }

    fun getNextTrack(forceAdvance: Boolean = false): TrackEntity? {
        val list = _playlist.value
        if (list.isEmpty()) return null

        if (!forceAdvance && _repeatMode.value == PlaybackRepeatMode.ONE) {
            return _currentTrack.value
        }

        if (_isShuffle.value && shuffledIndices.isNotEmpty()) {
            val nextShufflePos = currentShuffleIndex + 1
            return if (nextShufflePos < shuffledIndices.size) {
                currentShuffleIndex = nextShufflePos
                val track = list.getOrNull(shuffledIndices[nextShufflePos])
                _currentTrack.value = track
                track
            } else if (_repeatMode.value == PlaybackRepeatMode.ALL) {
                currentShuffleIndex = 0
                val track = list.getOrNull(shuffledIndices[0])
                _currentTrack.value = track
                track
            } else {
                null
            }
        } else {
            val current = _currentTrack.value
            val currentIndex = list.indexOfFirst { it.id == current?.id }
            val nextIndex = currentIndex + 1
            return if (nextIndex in list.indices) {
                val track = list[nextIndex]
                _currentTrack.value = track
                track
            } else if (_repeatMode.value == PlaybackRepeatMode.ALL) {
                val track = list[0]
                _currentTrack.value = track
                track
            } else {
                null
            }
        }
    }

    fun getPreviousTrack(): TrackEntity? {
        val list = _playlist.value
        if (list.isEmpty()) return null

        if (_isShuffle.value && shuffledIndices.isNotEmpty()) {
            val prevShufflePos = currentShuffleIndex - 1
            return if (prevShufflePos >= 0) {
                currentShuffleIndex = prevShufflePos
                val track = list.getOrNull(shuffledIndices[prevShufflePos])
                _currentTrack.value = track
                track
            } else {
                val lastPos = shuffledIndices.lastIndex
                currentShuffleIndex = lastPos
                val track = list.getOrNull(shuffledIndices[lastPos])
                _currentTrack.value = track
                track
            }
        } else {
            val current = _currentTrack.value
            val currentIndex = list.indexOfFirst { it.id == current?.id }
            val prevIndex = currentIndex - 1
            return if (prevIndex >= 0) {
                val track = list[prevIndex]
                _currentTrack.value = track
                track
            } else {
                val track = list.last()
                _currentTrack.value = track
                track
            }
        }
    }

    fun clear() {
        _playlist.value = emptyList()
        _currentTrack.value = null
        shuffledIndices = emptyList()
        currentShuffleIndex = 0
    }

    private fun rebuildShuffleOrder() {
        val list = _playlist.value
        if (list.isEmpty()) {
            shuffledIndices = emptyList()
            currentShuffleIndex = 0
            return
        }
        val current = _currentTrack.value
        val currentIdx = if (current != null) list.indexOfFirst { it.id == current.id } else -1

        val otherIndices = list.indices.filter { it != currentIdx }.shuffled()
        shuffledIndices = if (currentIdx >= 0) {
            listOf(currentIdx) + otherIndices
        } else {
            list.indices.shuffled()
        }
        currentShuffleIndex = 0
    }
}
