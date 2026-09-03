package com.example.ui.main

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.ui.MainNavigationTab
import com.example.ui.PlaylistDetailTarget
import com.example.ui.theme.*

/**
 * Barra de navegación táctil inferior (48dp target) para alternar entre
 * la biblioteca de canciones y la colección de playlists.
 */
@Composable
fun MainBottomNavBar(
    currentNavTab: MainNavigationTab,
    selectedPlaylistTarget: PlaylistDetailTarget?,
    onSelectSongs: () -> Unit,
    onSelectPlaylists: () -> Unit,
    modifier: Modifier = Modifier
) {
    NavigationBar(
        containerColor = DarkSurface,
        tonalElevation = 8.dp,
        modifier = modifier
    ) {
        NavigationBarItem(
            selected = currentNavTab == MainNavigationTab.SONGS && selectedPlaylistTarget == null,
            onClick = onSelectSongs,
            icon = {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = "Canciones"
                )
            },
            label = {
                Text(
                    text = "Canciones",
                    style = MaterialTheme.typography.labelSmall
                )
            },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Color.Black,
                selectedTextColor = GreenAccent,
                indicatorColor = GreenPrimary,
                unselectedIconColor = TextSecondary,
                unselectedTextColor = TextSecondary
            ),
            modifier = Modifier.testTag("nav_tab_songs")
        )

        NavigationBarItem(
            selected = currentNavTab == MainNavigationTab.PLAYLISTS || selectedPlaylistTarget != null,
            onClick = onSelectPlaylists,
            icon = {
                Icon(
                    imageVector = Icons.Default.QueueMusic,
                    contentDescription = "Playlists"
                )
            },
            label = {
                Text(
                    text = "Playlists",
                    style = MaterialTheme.typography.labelSmall
                )
            },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Color.Black,
                selectedTextColor = GreenAccent,
                indicatorColor = GreenPrimary,
                unselectedIconColor = TextSecondary,
                unselectedTextColor = TextSecondary
            ),
            modifier = Modifier.testTag("nav_tab_playlists")
        )
    }
}
