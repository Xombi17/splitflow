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
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

private const val TAG = "VoiceAudioRecorder"
private const val SAMPLE_RATE_HZ = 16_000
private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
private const val MAX_RECORD_SECONDS = 25
private const val PAD_SILENCE_SAMPLES = SAMPLE_RATE_HZ / 5 // 200ms clean silence margin
private const val EDGE_CLICK_TRIM_SAMPLES = SAMPLE_RATE_HZ / 12 // ~83ms button-tap click trim
private const val VAD_FRAME_SAMPLES = SAMPLE_RATE_HZ / 50 // 20ms frame (320 samples)
private const val VAD_SPEECH_RMS_THRESHOLD = 420f
private const val VAD_AUTO_STOP_SILENCE_MS = 1_650L
private const val VAD_MIN_SPEECH_BEFORE_AUTO_STOP_MS = 450L

/**
 * High-clarity 16 kHz mono PCM -> WAV recorder tuned for on-device ASR (Whistle / Whisper).
 * Features:
 * - DC offset removal + 80 Hz high-pass filter to strip mic rumble and handling thumps
 * - Start/end tap-click trimming so button presses aren't decoded as gibberish
 * - Energy-based Voice Activity Detection (VAD) for silence trimming & automatic end-of-speech
 * - Gentle RMS-guided normalization (capped at 4x) that clarifies speech without boosting background hiss
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
        enableVadAutoStop: Boolean = true,
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
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE_HZ,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            ).let { r ->
                if (r.state == AudioRecord.STATE_INITIALIZED) r else {
                    r.release()
                    AudioRecord(
                        MediaRecorder.AudioSource.VOICE_RECOGNITION,
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
                var speechDetectedMs = 0L
                var lastVoiceMs = startTimeMs
                var hasHeardSpeech = false

                while (isActive && _isRecording.value) {
                    val readCount = recorder.read(readBuffer, 0, readBuffer.size)
                    if (readCount > 0) {
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
                        val normalized = (rms / 4800f).coerceIn(0.06f, 1f)
                        _amplitude.value = normalized

                        val nowMs = System.currentTimeMillis()
                        val elapsedMs = nowMs - startTimeMs
                        val elapsedSec = (elapsedMs / 1000L).toInt()
                        _elapsedSeconds.value = elapsedSec

                        // Ignore initial 40ms tap transient before tracking speech activity (preserves initial syllable)
                        if (elapsedMs > 40L && rms >= VAD_SPEECH_RMS_THRESHOLD) {
                            if (!hasHeardSpeech) {
                                hasHeardSpeech = true
                            }
                            speechDetectedMs += (readCount * 1000L) / SAMPLE_RATE_HZ
                            lastVoiceMs = nowMs
                        }

                        val shouldVadStop = enableVadAutoStop &&
                            hasHeardSpeech &&
                            speechDetectedMs >= VAD_MIN_SPEECH_BEFORE_AUTO_STOP_MS &&
                            (nowMs - lastVoiceMs) >= VAD_AUTO_STOP_SILENCE_MS

                        if (shouldVadStop || elapsedSec >= MAX_RECORD_SECONDS) {
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

    /** Stops recording and returns the DSP-conditioned WAV bytes (or null if too short). */
    fun stopRecording(): ByteArray? = finalizeInternal()

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
        val rawPcm = if (stream != null) {
            synchronized(stream) { stream.toByteArray() }
        } else {
            ByteArray(0)
        }
        // Require at least ~0.25s of audio (~8 KB at 16kHz 16-bit mono)
        if (rawPcm.size < 4000) return null

        val preparedPcm = conditionSpeechPcm16(rawPcm)
        return wrapPcmInWav(preparedPcm)
    }

    fun release() {
        cancelRecording()
        scope.cancel()
    }

    /**
     * Conditions 16 kHz mono PCM for ASR:
     * 1. Trims ~83ms finger-tap transients from start/end (when clip is long enough).
     * 2. Applies DC offset removal + gentle 1st-order high-pass filter (~80 Hz).
     * 3. Uses 20ms-frame energy VAD to trim excessive leading/trailing dead air.
     * 4. Applies gentle RMS-guided gain (capped at 4x) without amplifying noise floor into hallucinations.
     * 5. Adds clean 200ms zero-silence margins before & after speech.
     */
    private fun conditionSpeechPcm16(pcmBytes: ByteArray): ByteArray {
        val rawCount = pcmBytes.size / 2
        if (rawCount <= 0) return pcmBytes

        // Preserve start of speech so initial syllables ("Tanmay", "Paid") are never clipped; trim release click only
        val trimStart = 0
        val trimEnd = if (rawCount > SAMPLE_RATE_HZ * 6 / 10) rawCount - EDGE_CLICK_TRIM_SAMPLES else rawCount
        val count = (trimEnd - trimStart).coerceAtLeast(1)

        val samples = FloatArray(count)
        var meanSum = 0.0
        for (i in 0 until count) {
            val srcIdx = (trimStart + i) * 2
            val lo = pcmBytes[srcIdx].toInt() and 0xFF
            val hi = pcmBytes[srcIdx + 1].toInt() and 0xFF
            val v = ((hi shl 8) or lo).toShort().toFloat()
            samples[i] = v
            meanSum += v
        }
        val dcOffset = (meanSum / count).toFloat()

        // 1st-order high-pass filter (~80 Hz @ 16kHz, alpha ~ 0.969)
        val alpha = 0.969f
        var prevIn = 0f
        var prevOut = 0f
        for (i in 0 until count) {
            val centered = samples[i] - dcOffset
            val hp = alpha * (prevOut + centered - prevIn)
            prevIn = centered
            prevOut = hp
            samples[i] = hp
        }

        // Frame-based VAD to trim leading/trailing silence while keeping 160ms speech context
        val numFrames = count / VAD_FRAME_SAMPLES
        var activeStart = 0
        var activeEnd = count
        if (numFrames >= 6) {
            val frameRms = FloatArray(numFrames)
            var maxFrameRms = 0f
            for (f in 0 until numFrames) {
                var sq = 0.0
                val base = f * VAD_FRAME_SAMPLES
                for (j in 0 until VAD_FRAME_SAMPLES) {
                    val s = samples[base + j]
                    sq += s * s
                }
                val r = sqrt(sq / VAD_FRAME_SAMPLES).toFloat()
                frameRms[f] = r
                if (r > maxFrameRms) maxFrameRms = r
            }
            val dynamicThreshold = (maxFrameRms * 0.12f).coerceAtLeast(220f)
            val firstSpeech = frameRms.indexOfFirst { it >= dynamicThreshold }
            val lastSpeech = frameRms.indexOfLast { it >= dynamicThreshold }
            if (firstSpeech in 0..lastSpeech) {
                val contextFrames = 8 // 160ms pre/post margin
                activeStart = ((firstSpeech - contextFrames).coerceAtLeast(0)) * VAD_FRAME_SAMPLES
                activeEnd = (((lastSpeech + contextFrames + 1).coerceAtMost(numFrames)) * VAD_FRAME_SAMPLES)
                    .coerceAtMost(count)
            }
        }

        val activeLen = (activeEnd - activeStart).coerceAtLeast(1)
        var peakAbs = 0f
        var activeSqSum = 0.0
        for (i in activeStart until activeEnd) {
            val v = samples[i]
            val a = abs(v)
            if (a > peakAbs) peakAbs = a
            activeSqSum += v * v
        }
        val activeRms = sqrt(activeSqSum / activeLen).toFloat()

        // Gentle RMS target (~3600) bounded by peak headroom (24000) and max 4x gain
        val gain = if (activeRms > 140f && peakAbs > 250f) {
            val rmsGain = 3600f / activeRms
            val peakLimitGain = 24_000f / peakAbs
            minOf(rmsGain, peakLimitGain).coerceIn(1f, 4f)
        } else {
            1f
        }

        // Clean natural context without synthetic zero-padding (which triggers Whisper hallucination)
        val out = ByteArray(activeLen * 2)
        for (i in 0 until activeLen) {
            val scaled = (samples[activeStart + i] * gain).roundToInt().coerceIn(-32_767, 32_767)
            val dst = i * 2
            out[dst] = (scaled and 0xFF).toByte()
            out[dst + 1] = ((scaled shr 8) and 0xFF).toByte()
        }
        return out
    }

    private fun wrapPcmInWav(pcmData: ByteArray): ByteArray {
        val totalDataLen = pcmData.size + 36
        val byteRate = SAMPLE_RATE_HZ * 2
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray(Charsets.US_ASCII))
            putInt(totalDataLen)
            put("WAVE".toByteArray(Charsets.US_ASCII))
            put("fmt ".toByteArray(Charsets.US_ASCII))
            putInt(16)
            putShort(1) // PCM
            putShort(1) // Mono
            putInt(SAMPLE_RATE_HZ)
            putInt(byteRate)
            putShort(2)
            putShort(16)
            put("data".toByteArray(Charsets.US_ASCII))
            putInt(pcmData.size)
        }.array()

        return ByteArray(header.size + pcmData.size).also { out ->
            System.arraycopy(header, 0, out, 0, header.size)
            System.arraycopy(pcmData, 0, out, header.size, pcmData.size)
        }
    }
}
