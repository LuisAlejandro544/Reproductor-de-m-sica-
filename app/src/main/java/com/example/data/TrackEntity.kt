package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tracks")
data class TrackEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val filePath: String,
    val artworkPath: String? = null,
    val dateAdded: Long = System.currentTimeMillis(),
    val isLiked: Boolean = false,
    val isFavorite: Boolean = false,
    val lyrics: String? = null
)
