package com.example.data

import kotlinx.coroutines.flow.Flow
import java.io.File

class TrackRepository(private val trackDao: TrackDao) {
    val allTracks: Flow<List<TrackEntity>> = trackDao.getAllTracks()

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

    suspend fun updateTrackMetadata(id: Long, title: String, artist: String, album: String) {
        trackDao.updateTrackMetadata(id, title, artist, album)
    }

    suspend fun deleteTrack(track: TrackEntity) {
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
