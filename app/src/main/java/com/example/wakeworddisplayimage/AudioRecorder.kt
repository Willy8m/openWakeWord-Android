package com.example.wakeworddisplayimage

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.os.Environment
import java.io.File

class AudioRecorder(private val context: Context) {
    private var mediaRecorder: MediaRecorder? = null
    private var outputFile: String? = null
    private var amplitudeListener: ((Int) -> Unit)? = null

    fun startRecording(word: String) {
        // Save to public Music directory
        val musicDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
            "MyRecordings"
        )
        if (!musicDir.exists()) {
            musicDir.mkdirs()
        }

        // Create unique file name
        val platform = Build.MODEL
        val fileName = "${musicDir.absolutePath}/${word}_${platform}_${System.currentTimeMillis()}.mp3"
        outputFile = fileName

        // Initialize and start MediaRecorder
        mediaRecorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setOutputFile(fileName)
            prepare()
            start()
        }

        // Start amplitude monitoring in a separate thread
        startAmplitudeMonitoring()
    }

    fun stopRecording(): String? {
        mediaRecorder?.apply {
            stop()
            release()
        }
        mediaRecorder = null
        stopAmplitudeMonitoring()
        return outputFile
    }

    // Method to register an amplitude listener
    fun setAmplitudeListener(listener: (Int) -> Unit) {
        amplitudeListener = listener
    }

    private var amplitudeMonitoringThread: Thread? = null
    private var isMonitoring = false

    private fun startAmplitudeMonitoring() {
        isMonitoring = true
        amplitudeMonitoringThread = Thread {
            while (isMonitoring) {
                try {
                    val maxAmplitude = mediaRecorder?.maxAmplitude ?: 0
                    amplitudeListener?.invoke(maxAmplitude)
                    Thread.sleep(20) // Update every 20ms
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        amplitudeMonitoringThread?.start()
    }

    private fun stopAmplitudeMonitoring() {
        isMonitoring = false
        amplitudeMonitoringThread?.interrupt()
        amplitudeMonitoringThread = null
    }
}
