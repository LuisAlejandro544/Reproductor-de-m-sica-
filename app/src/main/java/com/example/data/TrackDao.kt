package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackDao {
    @Query("SELECT * FROM tracks ORDER BY dateAdded DESC")
    fun getAllTracks(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE id = :id LIMIT 1")
    suspend fun getTrackById(id: Long): TrackEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrack(track: TrackEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTracks(tracks: List<TrackEntity>)

    @Query("DELETE FROM tracks WHERE id = :id")
    suspend fun deleteTrackById(id: Long)

    @Query("UPDATE tracks SET artworkPath = :artworkPath WHERE id = :id")
    suspend fun updateArtworkPath(id: Long, artworkPath: String?)

    @Query("UPDATE tracks SET title = :title, artist = :artist, album = :album WHERE id = :id")
    suspend fun updateTrackMetadata(id: Long, title: String, artist: String, album: String)

    @Query("DELETE FROM tracks")
    suspend fun deleteAll()
}
