package com.example.data

import kotlinx.coroutines.flow.Flow
import java.io.File

class TrackRepository(
    private val trackDao: TrackDao,
    private val playlistDao: PlaylistDao
) {
    val allTracks: Flow<List<TrackEntity>> = trackDao.getAllTracks()
    val likedTracks: Flow<List<TrackEntity>> = trackDao.getLikedTracks()
    val favoriteTracks: Flow<List<TrackEntity>> = trackDao.getFavoriteTracks()
    val allPlaylists: Flow<List<PlaylistEntity>> = playlistDao.getAllPlaylists()

    suspend fun insertTracks(tracks: List<TrackEntity>) {
        trackDao.insertTracks(tracks)
    }

    suspend fun insertTrack(track: TrackEntity): Long {
        return trackDao.insertTrack(track)
    }

    suspend fun getTrackById(id: Long): TrackEntity? {
        return trackDao.getTrackById(id)
    }

    suspend fun updateArtwork(trackId: Long, artworkPath: String?) {
        trackDao.updateArtworkPath(trackId, artworkPath)
    }

    suspend fun updateLyrics(trackId: Long, lyrics: String?) {
        trackDao.updateLyrics(trackId, lyrics)
    }

    suspend fun updateTrackMetadata(id: Long, title: String, artist: String, album: String) {
        trackDao.updateTrackMetadata(id, title, artist, album)
    }

    suspend fun toggleLiked(trackId: Long, isLiked: Boolean) {
        trackDao.updateLiked(trackId, isLiked)
    }

    suspend fun toggleFavorite(trackId: Long, isFavorite: Boolean) {
        trackDao.updateFavorite(trackId, isFavorite)
    }

    suspend fun createPlaylist(name: String, description: String = ""): Long {
        return playlistDao.insertPlaylist(
            PlaylistEntity(name = name.trim(), description = description.trim())
        )
    }

    suspend fun updatePlaylist(playlist: PlaylistEntity) {
        playlistDao.updatePlaylist(playlist)
    }

    suspend fun deletePlaylist(playlistId: Long) {
        playlistDao.clearTracksFromPlaylist(playlistId)
        playlistDao.deletePlaylistById(playlistId)
    }

    suspend fun addTrackToPlaylist(playlistId: Long, trackId: Long) {
        playlistDao.addTrackToPlaylist(PlaylistTrackCrossRef(playlistId = playlistId, trackId = trackId))
    }

    suspend fun removeTrackFromPlaylist(playlistId: Long, trackId: Long) {
        playlistDao.removeTrackFromPlaylist(playlistId, trackId)
    }

    fun getTracksForPlaylist(playlistId: Long): Flow<List<TrackEntity>> {
        return playlistDao.getTracksForPlaylist(playlistId)
    }

    fun getTrackCountForPlaylist(playlistId: Long): Flow<Int> {
        return playlistDao.getTrackCountForPlaylist(playlistId)
    }

    fun getPlaylistIdsForTrack(trackId: Long): Flow<List<Long>> {
        return playlistDao.getPlaylistIdsForTrack(trackId)
    }

    suspend fun getPlaylistIdsForTrackSync(trackId: Long): List<Long> {
        return playlistDao.getPlaylistIdsForTrackSync(trackId)
    }

    suspend fun deleteTrack(track: TrackEntity) {
        playlistDao.removeTrackFromAllPlaylists(track.id)
        trackDao.deleteTrackById(track.id)
        // Clean up audio file and artwork if they exist in private storage
        try {
            val audioFile = File(track.filePath)
            if (audioFile.exists()) {
                audioFile.delete()
            }
            track.artworkPath?.let { artPath ->
                val artFile = File(artPath)
                if (artFile.exists()) {
                    artFile.delete()
                }
            }
        } catch (_: Exception) {
        }
    }

    suspend fun clearAllTracks() {
        trackDao.deleteAll()
    }
}
