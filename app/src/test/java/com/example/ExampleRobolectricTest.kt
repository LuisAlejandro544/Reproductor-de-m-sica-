package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.playback.AudioEngineType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

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
}
