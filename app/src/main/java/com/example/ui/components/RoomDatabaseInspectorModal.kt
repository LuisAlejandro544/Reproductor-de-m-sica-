package com.example.ui.components

import com.example.util.showSafeToast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.debug.DatabaseStats
import com.example.debug.QueryResult
import com.example.debug.RoomDatabaseInspector
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceElevated
import com.example.ui.theme.GreenAccent
import com.example.ui.theme.GreenDark
import com.example.ui.theme.GreenPrimary
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Inspector interactivo en pantalla para la Base de Datos Room (DebugDrawer / Android-Debug-Database).
 *
 * Permite explorar tablas SQLite, inspeccionar registros de canciones, letras, playlists y relaciones,
 * así como ejecutar consultas SQL en vivo con controles táctiles optimizados para smartphone.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomDatabaseInspectorModal(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var stats by remember { mutableStateOf<DatabaseStats?>(null) }
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf("tracks", "playlists", "cross_ref", "SQL Libre")

    var queryResult by remember { mutableStateOf<QueryResult?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var customSqlQuery by remember { mutableStateOf("SELECT id, title, artist, album, durationMs, lyrics FROM tracks LIMIT 50") }
    var searchQuery by remember { mutableStateOf("") }

    // Cargar datos de la tabla activa
    fun loadCurrentData() {
        scope.launch {
            isLoading = true
            val (loadedStats, result) = withContext(Dispatchers.IO) {
                val s = RoomDatabaseInspector.getDatabaseStats(context)
                val r = when (selectedTabIndex) {
                    0 -> RoomDatabaseInspector.executeQuery(context, "SELECT id, title, artist, album, durationMs, filePath, artworkPath, lyrics FROM tracks ORDER BY id DESC LIMIT 100")
                    1 -> RoomDatabaseInspector.executeQuery(context, "SELECT id, name, description, createdAt FROM playlists ORDER BY id DESC")
                    2 -> {
                        val crossResult = RoomDatabaseInspector.executeQuery(context, "SELECT playlistId, trackId, addedAt FROM playlist_tracks LIMIT 150")
                        if (crossResult.errorMessage != null && crossResult.errorMessage.contains("no such table")) {
                            RoomDatabaseInspector.executeQuery(context, "SELECT playlistId, trackId FROM playlist_track_cross_ref LIMIT 150")
                        } else {
                            crossResult
                        }
                    }
                    3 -> RoomDatabaseInspector.executeQuery(context, customSqlQuery)
                    else -> RoomDatabaseInspector.executeQuery(context, "SELECT * FROM tracks LIMIT 50")
                }
                Pair(s, r)
            }
            stats = loadedStats
            queryResult = result
            isLoading = false
        }
    }

    LaunchedEffect(selectedTabIndex) {
        loadCurrentData()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = Modifier.testTag("room_database_inspector_modal")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            // Cabecera del Inspector
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(GreenDark.copy(alpha = 0.4f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Storage,
                            contentDescription = null,
                            tint = GreenAccent,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Inspector de Base de Datos Room",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = TextPrimary
                        )
                        Text(
                            text = "SQLite en vivo: ritmo_music.db (Versión ${stats?.version ?: 3})",
                            style = MaterialTheme.typography.labelSmall,
                            color = GreenAccent
                        )
                    }
                }

                Row {
                    IconButton(
                        onClick = { loadCurrentData() },
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refrescar datos",
                            tint = GreenAccent
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cerrar",
                            tint = TextSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Tarjeta de Estadísticas de la Base de Datos
            stats?.let { s ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "TAMAÑO EN DISCO",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                color = TextSecondary
                            )
                            Text(
                                text = s.fileSizeFormatted,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                ),
                                color = TextPrimary
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "CANCIONES",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                color = TextSecondary
                            )
                            Text(
                                text = "${s.trackCount}",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                ),
                                color = GreenAccent
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "PLAYLISTS",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                color = TextSecondary
                            )
                            Text(
                                text = "${s.playlistCount}",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                ),
                                color = Color(0xFF64B5F6)
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "RELACIONES",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                color = TextSecondary
                            )
                            Text(
                                text = "${s.crossRefCount}",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                ),
                                color = TextSecondary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Selector de Pestañas
            ScrollableTabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = DarkBackground,
                contentColor = GreenAccent,
                edgePadding = 0.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = {
                            Text(
                                text = title,
                                fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedTabIndex == index) GreenAccent else TextSecondary
                            )
                        },
                        modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Contenido según pestaña
            if (selectedTabIndex == 3) {
                // Modo SQL Libre
                Column(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = customSqlQuery,
                        onValueChange = { customSqlQuery = it },
                        label = { Text("Consulta SQL (SELECT)") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 70.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GreenAccent,
                            unfocusedBorderColor = DarkSurfaceElevated,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // Plantillas Rápidas
                    Text(
                        text = "Consultas preparadas:",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = false,
                            onClick = {
                                customSqlQuery = "SELECT id, title, artist, length(lyrics) as bytes_letra FROM tracks WHERE lyrics IS NOT NULL"
                                loadCurrentData()
                            },
                            label = { Text("Con Letras", fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(labelColor = TextPrimary)
                        )
                        FilterChip(
                            selected = false,
                            onClick = {
                                customSqlQuery = "SELECT artist, COUNT(*) as canciones FROM tracks GROUP BY artist HAVING canciones >= 3 ORDER BY canciones DESC"
                                loadCurrentData()
                            },
                            label = { Text("Artistas 3+", fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(labelColor = TextPrimary)
                        )
                        FilterChip(
                            selected = false,
                            onClick = {
                                customSqlQuery = "SELECT p.id, p.name, COUNT(c.trackId) as canciones FROM playlists p LEFT JOIN playlist_tracks c ON p.id = c.playlistId GROUP BY p.id"
                                loadCurrentData()
                            },
                            label = { Text("Listas & Conteo", fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(labelColor = TextPrimary)
                        )
                        FilterChip(
                            selected = false,
                            onClick = {
                                customSqlQuery = "PRAGMA table_info(tracks)"
                                loadCurrentData()
                            },
                            label = { Text("Schema tracks", fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(labelColor = TextPrimary)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = { loadCurrentData() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = GreenPrimary,
                            contentColor = DarkBackground
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
                    ) {
                        Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Ejecutar Consulta", fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                // Barra de Filtro Rápido para Tablas
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Filtrar registros...") },
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = TextSecondary)
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(imageVector = Icons.Default.Close, contentDescription = "Limpiar", tint = TextSecondary)
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GreenAccent,
                        unfocusedBorderColor = DarkSurfaceElevated,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Barra de Estado de la Consulta y Botón Copiar
            queryResult?.let { res ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (res.errorMessage != null) {
                            "Error en consulta"
                        } else {
                            "${res.rowCount} filas (${res.executionTimeMs} ms)"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (res.errorMessage != null) MaterialTheme.colorScheme.error else TextSecondary
                    )

                    if (res.rowCount > 0) {
                        Button(
                            onClick = {
                                val sb = StringBuilder()
                                sb.append(res.columns.joinToString(" | ")).append("\n")
                                res.rows.forEach { r ->
                                    sb.append(r.joinToString(" | ")).append("\n")
                                }
                                clipboardManager.setText(AnnotatedString(sb.toString()))
                                context.showSafeToast("${res.rowCount} filas copiadas al portapapeles")
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = DarkSurfaceElevated,
                                contentColor = GreenAccent
                            ),
                            modifier = Modifier.defaultMinSize(minHeight = 36.dp)
                        ) {
                            Icon(imageVector = Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Copiar Todo", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Visor de Registros
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = GreenAccent)
                }
            } else if (queryResult?.errorMessage != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Error SQL:\n${queryResult?.errorMessage}",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            } else {
                val res = queryResult
                if (res == null || res.rows.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No se encontraron registros.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                } else {
                    val filteredRows = remember(res.rows, searchQuery) {
                        if (searchQuery.isBlank()) {
                            res.rows
                        } else {
                            val q = searchQuery.trim().lowercase()
                            res.rows.filter { row ->
                                row.any { colVal -> colVal?.lowercase()?.contains(q) == true }
                            }
                        }
                    }

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredRows) { row ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(0.5.dp, DarkSurfaceElevated, RoundedCornerShape(10.dp))
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Registro #${row.getOrNull(0) ?: "-"}",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.Bold
                                            ),
                                            color = GreenAccent
                                        )
                                        IconButton(
                                            onClick = {
                                                val rowText = res.columns.zip(row)
                                                    .joinToString("\n") { "${it.first}: ${it.second ?: "NULL"}" }
                                                clipboardManager.setText(AnnotatedString(rowText))
                                                context.showSafeToast("Fila copiada al portapapeles")
                                            },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.ContentCopy,
                                                contentDescription = "Copiar fila",
                                                tint = TextSecondary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }

                                    res.columns.forEachIndexed { idx, colName ->
                                        val value = row.getOrNull(idx)
                                        if (colName != "id" || res.columns.size <= 2) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = "$colName: ",
                                                    style = MaterialTheme.typography.bodySmall.copy(
                                                        fontWeight = FontWeight.SemiBold,
                                                        fontFamily = FontFamily.Monospace
                                                    ),
                                                    color = TextSecondary,
                                                    modifier = Modifier.width(90.dp)
                                                )
                                                Text(
                                                    text = if (value == null) "NULL" else if (colName == "lyrics" && value.length > 50) "${value.take(45)}... [${value.length} chars]" else value,
                                                    style = MaterialTheme.typography.bodySmall.copy(
                                                        fontFamily = FontFamily.Monospace
                                                    ),
                                                    color = if (value == null) Color.Gray else if (colName == "lyrics") GreenAccent else TextPrimary,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
