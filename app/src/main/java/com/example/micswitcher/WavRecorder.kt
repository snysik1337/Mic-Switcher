package com.example.micswitcher

import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import kotlin.concurrent.thread

/**
 * Records raw PCM audio from a chosen [AudioDeviceInfo] and writes it out as a WAV file.
 * AudioRecord (not MediaRecorder) is used because AudioRecord.setPreferredDevice() is
 * reliably supported back to API 23, which is what lets us route capture to a specific
 * input device (built-in mic, wired headset, USB mic, or a connected Bluetooth mic).
 */
class WavRecorder(private val outputFile: File) {

    private val sampleRate = 44100
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT

    private var audioRecord: AudioRecord? = null
    private var recordingThread: Thread? = null
    @Volatile private var isRecording = false

    /** Switch the input device on the fly, if currently recording. */
    fun setPreferredDevice(device: AudioDeviceInfo) {
        audioRecord?.setPreferredDevice(device)
    }

    fun start(device: AudioDeviceInfo): Boolean {
        val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        if (minBufferSize <= 0) return false

        val record = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            channelConfig,
            audioFormat,
            minBufferSize * 4
        )

        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            return false
        }

        record.setPreferredDevice(device)
        audioRecord = record

        outputFile.parentFile?.mkdirs()
        val pcmFile = File(outputFile.parentFile, outputFile.name + ".pcm")

        isRecording = true
        record.startRecording()

        recordingThread = thread(start = true) {
            val buffer = ByteArray(minBufferSize)
            FileOutputStream(pcmFile).use { out ->
                while (isRecording) {
                    val read = record.read(buffer, 0, buffer.size)
                    if (read > 0) out.write(buffer, 0, read)
                }
            }
            writeWavFile(pcmFile, outputFile)
            pcmFile.delete()
        }
        return true
    }

    fun stop() {
        if (!isRecording) return
        isRecording = false
        try {
            audioRecord?.stop()
        } catch (e: Exception) {
            Log.w("WavRecorder", "stop() failed", e)
        }
        recordingThread?.join(1000)
        audioRecord?.release()
        audioRecord = null
    }

    private fun writeWavFile(pcmFile: File, wavFile: File) {
        val pcmData = pcmFile.readBytes()
        RandomAccessFile(wavFile, "rw").use { raf ->
            raf.setLength(0)
            val totalDataLen = pcmData.size + 36
            val byteRate = sampleRate * 2 // mono, 16-bit

            raf.writeBytes("RIFF")
            raf.write(intToByteArrayLE(totalDataLen))
            raf.writeBytes("WAVE")
            raf.writeBytes("fmt ")
            raf.write(intToByteArrayLE(16))
            raf.write(shortToByteArrayLE(1))  // PCM format
            raf.write(shortToByteArrayLE(1))  // mono
            raf.write(intToByteArrayLE(sampleRate))
            raf.write(intToByteArrayLE(byteRate))
            raf.write(shortToByteArrayLE(2))  // block align
            raf.write(shortToByteArrayLE(16)) // bits per sample
            raf.writeBytes("data")
            raf.write(intToByteArrayLE(pcmData.size))
            raf.write(pcmData)
        }
    }

    private fun intToByteArrayLE(value: Int): ByteArray = byteArrayOf(
        (value and 0xff).toByte(),
        ((value shr 8) and 0xff).toByte(),
        ((value shr 16) and 0xff).toByte(),
        ((value shr 24) and 0xff).toByte()
    )

    private fun shortToByteArrayLE(value: Int): ByteArray = byteArrayOf(
        (value and 0xff).toByte(),
        ((value shr 8) and 0xff).toByte()
    )
}
