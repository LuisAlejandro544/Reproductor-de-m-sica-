package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.ui.theme.DarkSurfaceElevated
import com.example.ui.theme.GreenDark
import com.example.ui.theme.GreenPrimary
import java.io.File

@Composable
fun AlbumArtView(
    artworkPath: String?,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 8.dp,
    iconSize: Dp = 24.dp
) {
    val shape = RoundedCornerShape(cornerRadius)
    val hasValidArt = !artworkPath.isNullOrBlank() && File(artworkPath).exists()

    Box(
        modifier = modifier
            .clip(shape)
            .background(DarkSurfaceElevated),
        contentAlignment = Alignment.Center
    ) {
        if (hasValidArt) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(File(artworkPath!!))
                    .crossfade(true)
                    .build(),
                contentDescription = "Carátula del álbum",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // Stylized gradient placeholder
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            listOf(
                                GreenDark.copy(alpha = 0.6f),
                                DarkSurfaceElevated,
                                Color(0xFF1B2A23)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = GreenPrimary.copy(alpha = 0.75f),
                    modifier = Modifier.size(iconSize)
                )
            }
        }
    }
}
