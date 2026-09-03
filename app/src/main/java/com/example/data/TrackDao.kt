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

    @Query("UPDATE tracks SET isLiked = :isLiked WHERE id = :id")
    suspend fun updateLiked(id: Long, isLiked: Boolean)

    @Query("UPDATE tracks SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun updateFavorite(id: Long, isFavorite: Boolean)

    @Query("UPDATE tracks SET lyrics = :lyrics WHERE id = :id")
    suspend fun updateLyrics(id: Long, lyrics: String?)

    @Query("SELECT * FROM tracks WHERE isLiked = 1 ORDER BY dateAdded DESC")
    fun getLikedTracks(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE isFavorite = 1 ORDER BY dateAdded DESC")
    fun getFavoriteTracks(): Flow<List<TrackEntity>>

    @Query("DELETE FROM tracks")
    suspend fun deleteAll()
}
