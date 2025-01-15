package com.example.wakeworddisplayimage

import android.content.Context
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper

class AudioRecorder(private val context: Context) {
    private var mediaRecorder: MediaRecorder? = null
    private var outputFile: String? = null

    fun startRecording() {
        val fileName = "${context.externalCacheDir?.absolutePath}/audio_${System.currentTimeMillis()}.mp3"
        outputFile = fileName
        mediaRecorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setOutputFile(fileName)
            prepare()
            start()
        }

        // Stop recording after 2 seconds
        Handler(Looper.getMainLooper()).postDelayed({
            stopRecording()
        }, 2000)
    }

    fun stopRecording(): String? {
        mediaRecorder?.apply {
            stop()
            release()
        }
        mediaRecorder = null
        return outputFile
    }
}