package com.google.ai.edge.gallery.voice

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import com.google.ai.edge.gallery.data.MAX_AUDIO_CLIP_DURATION_SEC
import com.google.ai.edge.gallery.data.SAMPLE_RATE
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class VoiceManager {
    private val TAG = "VoiceManager"
    private val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
    private val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT

    private var audioRecord: AudioRecord? = null
    private var audioStream = ByteArrayOutputStream()
    private var recordingJob: Job? = null

    var isRecording = false
        private set

    @SuppressLint("MissingPermission")
    fun startListening(coroutineScope: CoroutineScope, onAmplitude: (Int) -> Unit = {}) {
        if (isRecording) return
        isRecording = true
        audioStream.reset()

        val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        audioRecord?.release()
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            minBufferSize
        )

        val buffer = ByteArray(minBufferSize)

        recordingJob = coroutineScope.launch(Dispatchers.IO) {
            audioRecord?.startRecording()
            val startMs = System.currentTimeMillis()

            while (isRecording && audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                val bytesRead = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                if (bytesRead > 0) {
                    audioStream.write(buffer, 0, bytesRead)
                    // Simple amplitude calculation for UI
                    var maxAmplitude = 0
                    for (i in 0 until bytesRead step 2) {
                        if (i + 1 < bytesRead) {
                            val sample = (buffer[i].toInt() and 0xFF) or (buffer[i + 1].toInt() shl 8)
                            val amplitude = Math.abs(sample.toShort().toInt())
                            if (amplitude > maxAmplitude) maxAmplitude = amplitude
                        }
                    }
                    onAmplitude(maxAmplitude)
                }

                val elapsedMs = System.currentTimeMillis() - startMs
                if (elapsedMs >= MAX_AUDIO_CLIP_DURATION_SEC * 1000) {
                    break
                }
            }
            stopRecordingInternal()
        }
    }

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

    private fun stopRecordingInternal() {
        if (audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
            audioRecord?.stop()
        }
        audioRecord?.release()
        audioRecord = null
    }
}
