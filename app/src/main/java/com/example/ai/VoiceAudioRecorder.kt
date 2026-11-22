package com.example.ai

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

private const val TAG = "VoiceAudioRecorder"
private const val SAMPLE_RATE_HZ = 16_000
private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
private const val MAX_RECORD_SECONDS = 25

/**
 * In-app PCM -> WAV audio recorder designed for on-device Cactus Whistle voice transcription.
 * Captures 16 kHz 16-bit mono WAV (32 KB/s, max ~800 KB for 25s) while emitting real-time
 * normalized microphone amplitude (0f..1f) for the AI Voice Orb & waveform.
 */
class VoiceAudioRecorder {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _amplitude = MutableStateFlow(0f)
    val amplitude: StateFlow<Float> = _amplitude.asStateFlow()

    private val _elapsedSeconds = MutableStateFlow(0)
    val elapsedSeconds: StateFlow<Int> = _elapsedSeconds.asStateFlow()

    private var audioRecord: AudioRecord? = null
    private var captureJob: Job? = null
    private var pcmStream: ByteArrayOutputStream? = null
    private var onAutoStopCallback: ((ByteArray) -> Unit)? = null

    @SuppressLint("MissingPermission")
    fun startRecording(
        onAutoStop: (ByteArray) -> Unit = {},
        onError: (String) -> Unit = {}
    ): Boolean {
        if (_isRecording.value) return true

        val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE_HZ, CHANNEL_CONFIG, AUDIO_FORMAT)
        if (minBuf == AudioRecord.ERROR || minBuf == AudioRecord.ERROR_BAD_VALUE) {
            onError("Microphone hardware is unavailable on this device.")
            return false
        }
        val bufferSize = (minBuf * 2).coerceAtLeast(4096)

        val recorder = try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE_HZ,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            ).let { r ->
                if (r.state == AudioRecord.STATE_INITIALIZED) r else {
                    r.release()
                    AudioRecord(
                        MediaRecorder.AudioSource.MIC,
                        SAMPLE_RATE_HZ,
                        CHANNEL_CONFIG,
                        AUDIO_FORMAT,
                        bufferSize
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize AudioRecord", e)
            onError(e.localizedMessage ?: "Could not access microphone.")
            return false
        }

        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            onError("Could not initialize microphone recorder.")
            return false
        }

        return try {
            recorder.startRecording()
            audioRecord = recorder
            val out = ByteArrayOutputStream()
            pcmStream = out
            onAutoStopCallback = onAutoStop
            _isRecording.value = true
            _elapsedSeconds.value = 0
            _amplitude.value = 0.08f

            captureJob = scope.launch {
                val readBuffer = ShortArray(bufferSize / 2)
                val startTimeMs = System.currentTimeMillis()
                while (isActive && _isRecording.value) {
                    val readCount = recorder.read(readBuffer, 0, readBuffer.size)
                    if (readCount > 0) {
                        // Compute RMS amplitude for smooth real-time AI waveform reactivity
                        var sumSquares = 0.0
                        val byteChunk = ByteArray(readCount * 2)
                        for (i in 0 until readCount) {
                            val sample = readBuffer[i]
                            sumSquares += sample.toDouble() * sample.toDouble()
                            byteChunk[i * 2] = (sample.toInt() and 0xFF).toByte()
                            byteChunk[i * 2 + 1] = ((sample.toInt() shr 8) and 0xFF).toByte()
                        }
                        synchronized(out) {
                            out.write(byteChunk)
                        }
                        val rms = sqrt(sumSquares / readCount).toFloat()
                        val normalized = (rms / 6500f).coerceIn(0.06f, 1f)
                        _amplitude.value = normalized

                        val elapsed = ((System.currentTimeMillis() - startTimeMs) / 1000L).toInt()
                        _elapsedSeconds.value = elapsed
                        if (elapsed >= MAX_RECORD_SECONDS) {
                            val wav = finalizeInternal()
                            if (wav != null && wav.size > 44) {
                                onAutoStopCallback?.invoke(wav)
                            }
                            break
                        }
                    }
                }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "startRecording failed", e)
            recorder.release()
            audioRecord = null
            _isRecording.value = false
            onError(e.localizedMessage ?: "Failed to start recording.")
            false
        }
    }

    /** Stops recording and returns the encoded WAV bytes (or null if too short / empty). */
    fun stopRecording(): ByteArray? {
        return finalizeInternal()
    }

    /** Cancels recording and discards audio data. */
    fun cancelRecording() {
        finalizeInternal()
    }

    private fun finalizeInternal(): ByteArray? {
        if (!_isRecording.value && audioRecord == null) return null
        _isRecording.value = false
        _amplitude.value = 0f

        captureJob?.cancel()
        captureJob = null

        val recorder = audioRecord
        audioRecord = null
        try {
            recorder?.stop()
        } catch (_: Exception) {
        }
        try {
            recorder?.release()
        } catch (_: Exception) {
        }

        val stream = pcmStream
        pcmStream = null
        val pcmBytes = if (stream != null) {
            synchronized(stream) { stream.toByteArray() }
        } else {
            ByteArray(0)
        }
        // Require at least ~0.25s of audio (~8 KB at 16kHz 16-bit mono)
        if (pcmBytes.size < 4000) return null
        return wrapPcmInWav(pcmBytes)
    }

    fun release() {
        cancelRecording()
        scope.cancel()
    }

    private fun wrapPcmInWav(pcmData: ByteArray): ByteArray {
        val totalDataLen = pcmData.size + 36
        val byteRate = SAMPLE_RATE_HZ * 2 // 16-bit mono = 2 bytes per sample
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray(Charsets.US_ASCII))
            putInt(totalDataLen)
            put("WAVE".toByteArray(Charsets.US_ASCII))
            put("fmt ".toByteArray(Charsets.US_ASCII))
            putInt(16) // PCM chunk size
            putShort(1) // AudioFormat = 1 (PCM)
            putShort(1) // NumChannels = 1 (Mono)
            putInt(SAMPLE_RATE_HZ)
            putInt(byteRate)
            putShort(2) // BlockAlign = NumChannels * BitsPerSample / 8
            putShort(16) // BitsPerSample
            put("data".toByteArray(Charsets.US_ASCII))
            putInt(pcmData.size)
        }.array()

        return ByteArray(header.size + pcmData.size).also { out ->
            System.arraycopy(header, 0, out, 0, header.size)
            System.arraycopy(pcmData, 0, out, header.size, pcmData.size)
        }
    }
}
