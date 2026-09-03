package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        TrackEntity::class,
        PlaylistEntity::class,
        PlaylistTrackCrossRef::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun trackDao(): TrackDao
    abstract fun playlistDao(): PlaylistDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "ritmo_music.db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                try {
                    com.pluto.plugins.rooms.db.PlutoRoomsDBWatcher.watch("ritmo_music.db", AppDatabase::class.java)
                } catch (_: Throwable) {
                    // Ignorado si Pluto no está activo en modo release o pruebas unitarias
                }
                instance
            }
        }
    }
}
