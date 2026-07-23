package com.trace.app.voice

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import com.trace.app.data.MAX_AUDIO_CLIP_DURATION_SEC
import com.trace.app.data.SAMPLE_RATE
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Manages mic capture for the push-to-talk voice loop.
 *
 * Performance notes:
 * - The [AudioRecord] instance is created once and kept alive between
 *   recordings to avoid the OS audio-session setup cost on every press.
 * - The recording loop runs on [Dispatchers.IO] and never touches the main thread.
 * - [onAmplitude] is called from the IO thread; callers must dispatch to Main
 *   if they need to update Compose state (e.g. via a StateFlow).
 */
class VoiceManager {
    private val TAG = "VoiceManager"
    private val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
    private val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT

    // Minimum OS buffer size, computed once.
    private val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        .coerceAtLeast(4096) // guard against -1 / 0 error codes

    // Reuse the same AudioRecord across sessions to avoid per-press setup cost.
    // Recreated on demand if it was released (see [obtainAudioRecord]).
    private var audioRecord: AudioRecord? = null

    @SuppressLint("MissingPermission")
    private fun obtainAudioRecord(): AudioRecord {
        val existing = audioRecord
        if (existing != null && existing.state == AudioRecord.STATE_INITIALIZED) {
            return existing
        }
        // Either first use or the previous instance was released — build a fresh one.
        val created = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            minBufferSize,
        )
        audioRecord = created
        return created
    }

    private val audioStream = ByteArrayOutputStream(minBufferSize * 8)
    private var recordingJob: Job? = null

    @Volatile
    var isRecording = false
        private set

    fun startListening(coroutineScope: CoroutineScope, onAmplitude: (Int) -> Unit = {}) {
        if (isRecording) return
        isRecording = true
        audioStream.reset()

        val buffer = ByteArray(minBufferSize)

        recordingJob = coroutineScope.launch(Dispatchers.IO) {
            val recorder = obtainAudioRecord()
            // If the recorder is in a bad state (e.g. released externally) bail out.
            if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord not initialized — cannot start recording.")
                isRecording = false
                return@launch
            }

            recorder.startRecording()
            val startMs = System.currentTimeMillis()

            while (isRecording &&
                recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING
            ) {
                val bytesRead = recorder.read(buffer, 0, buffer.size)
                if (bytesRead > 0) {
                    audioStream.write(buffer, 0, bytesRead)

                    // Amplitude for UI visualisation — scan every other sample (16-bit LE).
                    var maxAmplitude = 0
                    var i = 0
                    while (i + 1 < bytesRead) {
                        val sample = ((buffer[i].toInt() and 0xFF) or (buffer[i + 1].toInt() shl 8))
                        val amplitude = kotlin.math.abs(sample.toShort().toInt())
                        if (amplitude > maxAmplitude) maxAmplitude = amplitude
                        i += 2
                    }
                    onAmplitude(maxAmplitude)
                }

                val elapsedMs = System.currentTimeMillis() - startMs
                if (elapsedMs >= MAX_AUDIO_CLIP_DURATION_SEC * 1000L) break
            }

            stopRecordingInternal()
        }
    }

    /**
     * Stops the recording and returns the captured PCM bytes.
     * Safe to call from any thread.
     */
    fun stopListening(): ByteArray {
        isRecording = false
        recordingJob?.cancel()
        recordingJob = null
        stopRecordingInternal()

        val recordedBytes = audioStream.toByteArray()
        audioStream.reset()
        Log.d(TAG, "Stopped listening. Recorded ${recordedBytes.size} bytes.")
        return recordedBytes
    }

    /**
     * Permanently releases native resources.
     * Call once when the owning component is destroyed (e.g. ViewModel.onCleared()).
     */
    fun release() {
        isRecording = false
        recordingJob?.cancel()
        recordingJob = null
        val recorder = audioRecord
        try {
            if (recorder != null && recorder.state == AudioRecord.STATE_INITIALIZED) {
                if (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    recorder.stop()
                }
                recorder.release()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Exception during release: ${e.message}")
        } finally {
            audioRecord = null
        }
        Log.d(TAG, "VoiceManager released.")
    }

    private fun stopRecordingInternal() {
        try {
            val recorder = audioRecord
            if (recorder != null && recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                recorder.stop()
            }
        } catch (e: Exception) {
            Log.w(TAG, "stopRecordingInternal: ${e.message}")
        }
    }
}
