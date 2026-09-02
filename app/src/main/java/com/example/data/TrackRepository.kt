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
}
