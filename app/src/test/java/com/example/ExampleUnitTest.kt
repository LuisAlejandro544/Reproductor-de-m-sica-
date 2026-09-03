package com.example

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import com.example.playback.EqualizerDefaults
import com.example.playback.Media3EqualizerAudioProcessor
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun testEqualizerBandsCount() {
        assertEquals(10, EqualizerDefaults.NUM_BANDS)
        assertEquals(10, EqualizerDefaults.BAND_FREQUENCIES.size)
        assertEquals(10, EqualizerDefaults.BAND_LABELS.size)
        EqualizerDefaults.PRESETS.forEach { preset ->
            assertEquals(10, preset.gains.size)
        }
    }

    @Test
    fun testMedia3AudioProcessorConfigure() {
        val processor = Media3EqualizerAudioProcessor()
        val format16Bit = AudioProcessor.AudioFormat(44100, 2, C.ENCODING_PCM_16BIT)
        val outputFormat = processor.configure(format16Bit)
        assertEquals(format16Bit.sampleRate, outputFormat.sampleRate)
        assertEquals(format16Bit.channelCount, outputFormat.channelCount)
        assertEquals(format16Bit.encoding, outputFormat.encoding)
    }
}
