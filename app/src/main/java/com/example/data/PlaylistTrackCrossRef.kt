package com.example.data

import androidx.room.Entity
import androidx.room.Index

/**
 * Tabla de unión muchos a muchos entre [PlaylistEntity] y [TrackEntity].
 * Contiene el orden de adición para mantener la secuencia de pistas en la lista.
 */
@Entity(
    tableName = "playlist_tracks",
    primaryKeys = ["playlistId", "trackId"],
    indices = [
        Index(value = ["trackId"]),
        Index(value = ["playlistId"])
    ]
)
data class PlaylistTrackCrossRef(
    val playlistId: Long,
    val trackId: Long,
    val addedAt: Long = System.currentTimeMillis()
)
