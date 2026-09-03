package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.PlaylistEntity
import com.example.data.PlaylistTrackCrossRef
import com.example.data.TrackEntity
import com.example.playback.AudioEngineType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  private lateinit var database: AppDatabase

  @Before
  fun setUp() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
        .allowMainThreadQueries()
        .build()
  }

  @After
  fun tearDown() {
    database.close()
  }

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Ritmo", appName)
  }

  @Test
  fun `verify audio engine types exist and have valid properties`() {
    val exo = AudioEngineType.EXOPLAYER
    val oboe = AudioEngineType.OBOE_CPP
    assertNotNull(exo)
    assertNotNull(oboe)
    assertEquals("Media3", exo.shortName)
    assertEquals("Oboe C++", oboe.shortName)
  }

  @Test
  fun `verify playlist creation and track association in Room`() = runBlocking {
    val trackDao = database.trackDao()
    val playlistDao = database.playlistDao()

    val testTrack = TrackEntity(
        id = 1L,
        filePath = "/storage/emulated/0/Music/song.mp3",
        title = "Test Song",
        artist = "Test Artist",
        album = "Test Album",
        durationMs = 180000L,
        isLiked = true,
        isFavorite = false
    )
    trackDao.insertTrack(testTrack)

    val likedTracks = trackDao.getLikedTracks().first()
    assertEquals(1, likedTracks.size)
    assertEquals("Test Song", likedTracks[0].title)

    val playlistId = playlistDao.insertPlaylist(
        PlaylistEntity(
            name = "Rock Classics",
            description = "Best tracks of rock"
        )
    )
    assertTrue(playlistId > 0)

    playlistDao.addTrackToPlaylist(PlaylistTrackCrossRef(playlistId = playlistId, trackId = 1L))

    val playlistTracks = playlistDao.getTracksForPlaylist(playlistId).first()
    assertEquals(1, playlistTracks.size)
    assertEquals("Test Song", playlistTracks[0].title)

    val count = playlistDao.getTrackCountForPlaylist(playlistId).first()
    assertEquals(1, count)
  }
}
