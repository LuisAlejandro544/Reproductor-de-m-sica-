package com.example.playback

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer

/**
 * Procesador de audio para Media3 / ExoPlayer que aplica el ecualizador
 * paramétrico de 10 bandas con filtros Biquad IIR implementado en C++
 * a nivel de muestra PCM en tiempo real.
 */
class Media3EqualizerAudioProcessor : BaseAudioProcessor() {

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        return if (inputAudioFormat.encoding == C.ENCODING_PCM_16BIT) {
            inputAudioFormat
        } else {
            AudioProcessor.AudioFormat.NOT_SET
        }
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        val outputBuffer = replaceOutputBuffer(remaining)
        val offset = outputBuffer.position()
        outputBuffer.put(inputBuffer)

        if (OboeAudioBridge.isNativeReady() && inputAudioFormat.encoding == C.ENCODING_PCM_16BIT) {
            OboeAudioBridge.nativeMedia3ProcessDirect(
                byteBuffer = outputBuffer,
                offsetBytes = offset,
                lengthBytes = remaining,
                sampleRate = inputAudioFormat.sampleRate,
                channelCount = inputAudioFormat.channelCount
            )
        }

        outputBuffer.flip()
    }
}
