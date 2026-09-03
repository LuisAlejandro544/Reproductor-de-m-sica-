package com.example.debug

import android.content.Context
import android.database.Cursor
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.AppDatabase
import java.io.File
import java.util.Locale

data class ColumnInfo(
    val cid: Int,
    val name: String,
    val type: String,
    val notNull: Boolean,
    val defaultValue: String?,
    val isPrimaryKey: Boolean
)

data class QueryResult(
    val columns: List<String>,
    val rows: List<List<String?>>,
    val rowCount: Int,
    val executionTimeMs: Long,
    val errorMessage: String? = null
)

data class DatabaseStats(
    val fileName: String,
    val fileSizeBytes: Long,
    val fileSizeFormatted: String,
    val version: Int,
    val trackCount: Long,
    val playlistCount: Long,
    val crossRefCount: Long,
    val tables: List<String>
)

/**
 * Motor de inspección en pantalla para SQLite y Base de Datos Room (estilo Android-Debug-Database / DebugDrawer).
 *
 * Permite examinar esquemas, tablas, registros y ejecutar consultas SQL en vivo directamente
 * desde el smartphone sin requerir PC ni ADB.
 */
object RoomDatabaseInspector {

    private const val DB_NAME = "ritmo_music.db"

    private fun getReadableDatabase(context: Context): SupportSQLiteDatabase {
        return AppDatabase.getDatabase(context).openHelper.readableDatabase
    }

    /**
     * Obtiene la lista de nombres de tablas de usuario en la base de datos Room.
     */
    fun getTableNames(context: Context): List<String> {
        val tables = mutableListOf<String>()
        try {
            val db = getReadableDatabase(context)
            val query = "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' AND name NOT LIKE 'android_%' ORDER BY name ASC"
            db.query(query).use { cursor ->
                while (cursor.moveToNext()) {
                    tables.add(cursor.getString(0))
                }
            }
        } catch (e: Exception) {
            DebugLogManager.log(
                tag = "RoomDatabaseInspector",
                message = "Error al listar tablas: ${e.localizedMessage}",
                level = DebugLogLevel.ERROR,
                rawErrorCode = -6001,
                details = e.stackTraceToString()
            )
        }
        return tables
    }

    /**
     * Obtiene la definición de columnas de una tabla mediante PRAGMA table_info.
     */
    fun getTableSchema(context: Context, tableName: String): List<ColumnInfo> {
        val columns = mutableListOf<ColumnInfo>()
        try {
            val db = getReadableDatabase(context)
            db.query("PRAGMA table_info(`$tableName`)").use { cursor ->
                val cidIdx = cursor.getColumnIndex("cid")
                val nameIdx = cursor.getColumnIndex("name")
                val typeIdx = cursor.getColumnIndex("type")
                val notNullIdx = cursor.getColumnIndex("notnull")
                val dfltIdx = cursor.getColumnIndex("dflt_value")
                val pkIdx = cursor.getColumnIndex("pk")

                while (cursor.moveToNext()) {
                    columns.add(
                        ColumnInfo(
                            cid = if (cidIdx >= 0) cursor.getInt(cidIdx) else 0,
                            name = if (nameIdx >= 0) cursor.getString(nameIdx) else "",
                            type = if (typeIdx >= 0) cursor.getString(typeIdx) else "",
                            notNull = if (notNullIdx >= 0) cursor.getInt(notNullIdx) == 1 else false,
                            defaultValue = if (dfltIdx >= 0) cursor.getString(dfltIdx) else null,
                            isPrimaryKey = if (pkIdx >= 0) cursor.getInt(pkIdx) == 1 else false
                        )
                    )
                }
            }
        } catch (e: Exception) {
            DebugLogManager.log(
                tag = "RoomDatabaseInspector",
                message = "Error al obtener esquema de $tableName: ${e.localizedMessage}",
                level = DebugLogLevel.WARN
            )
        }
        return columns
    }

    /**
     * Ejecuta una consulta SQL en vivo y devuelve el resultado en formato tabular.
     */
    fun executeQuery(context: Context, sqlQuery: String, limit: Int = 150): QueryResult {
        val startNanos = System.nanoTime()
        val trimmedQuery = sqlQuery.trim()

        try {
            val db = getReadableDatabase(context)
            db.query(trimmedQuery).use { cursor ->
                val columns = cursor.columnNames.toList()
                val rows = mutableListOf<List<String?>>()

                var count = 0
                while (cursor.moveToNext() && count < limit) {
                    val row = mutableListOf<String?>()
                    for (i in 0 until cursor.columnCount) {
                        row.add(
                            if (cursor.isNull(i)) {
                                null
                            } else {
                                when (cursor.getType(i)) {
                                    Cursor.FIELD_TYPE_BLOB -> "[BLOB]"
                                    else -> cursor.getString(i)
                                }
                            }
                        )
                    }
                    rows.add(row)
                    count++
                }

                val elapsedMs = (System.nanoTime() - startNanos) / 1_000_000
                return QueryResult(
                    columns = columns,
                    rows = rows,
                    rowCount = rows.size,
                    executionTimeMs = elapsedMs
                )
            }
        } catch (e: Exception) {
            val elapsedMs = (System.nanoTime() - startNanos) / 1_000_000
            DebugLogManager.log(
                tag = "RoomDatabaseInspector",
                message = "Error SQL en consulta: ${e.localizedMessage}",
                level = DebugLogLevel.WARN,
                rawErrorCode = -6002,
                details = trimmedQuery
            )
            return QueryResult(
                columns = emptyList(),
                rows = emptyList(),
                rowCount = 0,
                executionTimeMs = elapsedMs,
                errorMessage = e.localizedMessage ?: "Error desconocido en consulta SQL"
            )
        }
    }

    /**
     * Recopila estadísticas técnicas del archivo de base de datos y de las tablas de Room.
     */
    fun getDatabaseStats(context: Context): DatabaseStats {
        val dbFile = context.getDatabasePath(DB_NAME)
        val fileSizeBytes = if (dbFile.exists()) dbFile.length() else 0L
        val formattedSize = formatFileSize(fileSizeBytes)

        var trackCount = 0L
        var playlistCount = 0L
        var crossRefCount = 0L
        var version = 3
        val tables = getTableNames(context)

        try {
            val db = getReadableDatabase(context)
            version = db.version

            fun countTable(tableName: String): Long {
                return try {
                    db.query("SELECT COUNT(*) FROM `$tableName`").use {
                        if (it.moveToFirst()) it.getLong(0) else 0L
                    }
                } catch (_: Exception) { 0L }
            }

            trackCount = countTable("tracks")
            playlistCount = countTable("playlists")
            crossRefCount = countTable("playlist_track_cross_ref")
        } catch (e: Exception) {
            DebugLogManager.log(
                tag = "RoomDatabaseInspector",
                message = "Error al obtener estadísticas de DB: ${e.localizedMessage}",
                level = DebugLogLevel.WARN
            )
        }

        return DatabaseStats(
            fileName = DB_NAME,
            fileSizeBytes = fileSizeBytes,
            fileSizeFormatted = formattedSize,
            version = version,
            trackCount = trackCount,
            playlistCount = playlistCount,
            crossRefCount = crossRefCount,
            tables = tables
        )
    }

    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes <= 0 -> "0 B"
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> String.format(Locale.US, "%.2f KB", bytes / 1024.0)
            else -> String.format(Locale.US, "%.2f MB", bytes / (1024.0 * 1024.0))
        }
    }
}
