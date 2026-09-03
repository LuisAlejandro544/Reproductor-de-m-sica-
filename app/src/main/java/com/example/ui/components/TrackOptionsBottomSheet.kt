package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.PlaylistRemove
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.TrackEntity
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.GreenAccent
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

/**
 * Hoja modal táctil inferior para opciones de canciones.
 * Reemplaza el uso de Popup/DropdownMenu dentro de listas para prevenir
 * retenciones y fugas de memoria en AndroidComposeViewAccessibilityDelegateCompat.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackOptionsBottomSheet(
    track: TrackEntity,
    onDismiss: () -> Unit,
    onEditArtwork: () -> Unit,
    onEditMetadata: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    onAddToPlaylist: () -> Unit = {},
    onToggleLiked: (() -> Unit)? = null,
    onToggleFavorite: (() -> Unit)? = null,
    onRemoveFromPlaylist: (() -> Unit)? = null
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = DarkSurface,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        modifier = modifier.testTag("track_options_bottom_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            // Cabecera con info de la pista
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                AlbumArtView(
                    artworkPath = track.artworkPath,
                    modifier = Modifier.size(52.dp),
                    cornerRadius = 8.dp,
                    iconSize = 24.dp
                )
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        ),
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = track.artist,
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Opción: Añadir a Playlist
            TrackOptionRow(
                icon = Icons.Default.PlaylistAdd,
                iconTint = GreenAccent,
                title = "Añadir a playlist...",
                testTag = "option_add_to_playlist",
                onClick = {
                    onDismiss()
                    onAddToPlaylist()
                }
            )

            // Opción: Me gusta (Corazón)
            onToggleLiked?.let { toggleLike ->
                TrackOptionRow(
                    icon = if (track.isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    iconTint = if (track.isLiked) Color(0xFFE91E63) else TextSecondary,
                    title = if (track.isLiked) "Quitar de 'Me gusta'" else "Marcar como 'Me gusta'",
                    testTag = "option_toggle_liked",
                    onClick = {
                        onDismiss()
                        toggleLike()
                    }
                )
            }

            // Opción: Favorito (Estrella)
            onToggleFavorite?.let { toggleFav ->
                TrackOptionRow(
                    icon = if (track.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                    iconTint = if (track.isFavorite) Color(0xFFFFB300) else TextSecondary,
                    title = if (track.isFavorite) "Quitar de 'Mis favoritos'" else "Marcar como 'Favorito'",
                    testTag = "option_toggle_favorite",
                    onClick = {
                        onDismiss()
                        toggleFav()
                    }
                )
            }

            // Opción condicional: Quitar de esta playlist específica
            onRemoveFromPlaylist?.let { removeAction ->
                TrackOptionRow(
                    icon = Icons.Default.PlaylistRemove,
                    iconTint = MaterialTheme.colorScheme.error,
                    title = "Quitar de esta playlist",
                    testTag = "option_remove_from_current_playlist",
                    onClick = {
                        onDismiss()
                        removeAction()
                    }
                )
            }

            // Opción: Cambiar carátula
            TrackOptionRow(
                icon = Icons.Default.AddPhotoAlternate,
                iconTint = GreenAccent,
                title = "Cambiar carátula (Lossless WebP)",
                testTag = "option_change_artwork",
                onClick = {
                    onDismiss()
                    onEditArtwork()
                }
            )

            // Opción: Editar metadatos en Rust
            TrackOptionRow(
                icon = Icons.Default.Edit,
                iconTint = MaterialTheme.colorScheme.tertiary,
                title = "Editar con Rust (Tags ID3/Vorbis)",
                testTag = "option_edit_metadata",
                onClick = {
                    onDismiss()
                    onEditMetadata()
                }
            )

            // Opción: Eliminar de la biblioteca
            TrackOptionRow(
                icon = Icons.Default.DeleteOutline,
                iconTint = MaterialTheme.colorScheme.error,
                title = "Eliminar de la biblioteca",
                testTag = "option_delete_track",
                isDestructive = true,
                onClick = {
                    onDismiss()
                    onDelete()
                }
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun TrackOptionRow(
    icon: ImageVector,
    iconTint: androidx.compose.ui.graphics.Color,
    title: String,
    testTag: String,
    isDestructive: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp)
            .testTag(testTag)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontWeight = FontWeight.Medium,
                fontSize = 15.sp
            ),
            color = if (isDestructive) MaterialTheme.colorScheme.error else TextPrimary
        )
    }
}
