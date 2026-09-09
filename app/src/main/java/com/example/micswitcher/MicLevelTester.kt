package com.example.micswitcher

import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import kotlin.concurrent.thread
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Live mic level meter: opens an AudioRecord on the given device and reports
 * RMS amplitude back on the main thread as a 0-100 level plus an approximate
 * dBFS value. Nothing is written to disk — this is purely for confirming a
 * device is actually picking up sound before (or instead of) recording.
 */
class MicLevelTester(private val onLevel: (level: Int, dbfs: Float) -> Unit) {

    private val sampleRate = 44100
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT

    private var audioRecord: AudioRecord? = null
    private var thread: Thread? = null
    @Volatile private var running = false
    private val mainHandler = Handler(Looper.getMainLooper())

    fun start(device: AudioDeviceInfo): Boolean {
        val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        if (minBufferSize <= 0) return false

        val record = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            channelConfig,
            audioFormat,
            minBufferSize * 2
        )
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            return false
        }
        record.setPreferredDevice(device)
        audioRecord = record
        running = true
        record.startRecording()

        thread = thread(start = true) {
            val buffer = ShortArray(minBufferSize / 2)
            while (running) {
                val read = record.read(buffer, 0, buffer.size)
                if (read > 0) {
                    var sum = 0.0
                    for (i in 0 until read) {
                        val sample = buffer[i].toDouble()
                        sum += sample * sample
                    }
                    val rms = sqrt(sum / read)
                    val dbfs = if (rms > 0) 20 * log10(rms / 32768.0) else -96.0
                    // Roughly map -60dBFS..0dBFS onto a 0-100 meter.
                    val level = (((dbfs + 60) / 60) * 100).toInt().coerceIn(0, 100)
                    mainHandler.post { onLevel(level, dbfs.toFloat()) }
                }
            }
        }
        return true
    }

    fun stop() {
        if (!running) return
        running = false
        try {
            audioRecord?.stop()
        } catch (_: Exception) {
        }
        thread?.join(500)
        audioRecord?.release()
        audioRecord = null
    }
}
