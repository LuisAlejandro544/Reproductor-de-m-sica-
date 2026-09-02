package com.example

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.example.data.TrackEntity
import com.example.ui.components.MiniPlayer
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class GreetingScreenshotTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun greeting_screenshot() {
    val sampleTrack = TrackEntity(
      id = 1L,
      title = "Canción de Ejemplo",
      artist = "Artista Local",
      album = "Álbum de Prueba",
      durationMs = 214000L,
      filePath = "/mock/path.mp3"
    )

    composeTestRule.setContent {
      MyApplicationTheme {
        MiniPlayer(
          track = sampleTrack,
          isPlaying = true,
          currentPositionMs = 65000L,
          durationMs = 214000L,
          onPlayPause = {},
          onNext = {},
          onExpand = {}
        )
      }
    }

    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/greeting.png")
  }
}
