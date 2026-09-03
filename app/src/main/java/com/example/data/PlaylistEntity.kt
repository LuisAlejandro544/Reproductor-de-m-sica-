package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entidad de Base de Datos Room para listas de reproducción locales.
 * Permite guardar nombre, descripción opcional y marca de tiempo de creación.
 */
@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val description: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
