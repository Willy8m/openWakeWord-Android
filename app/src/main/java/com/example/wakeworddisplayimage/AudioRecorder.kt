package com.example.wakeworddisplayimage

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.util.Log
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.example.wakeworddisplayimage.ml.ModelV11
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.tensorflow.lite.DataType
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.util.LinkedList


class AudioRecorder(private val context: MainActivity, private val viewModel: MainViewModel) {

    private val scope = CoroutineScope(Dispatchers.IO) // Scope for background tasks

    private var isListening = true
    private var isRecording = false
    private lateinit var model : ModelV11
    private lateinit var audioRecord: AudioRecord
    private val sampleRate = 16000 // Sample rate in Hz
    private val channel = AudioFormat.CHANNEL_IN_MONO
    private val encoding = AudioFormat.ENCODING_PCM_16BIT
    private var bufferSize16bit: Int = sampleRate * 2  // multiply by 2 because 16BIT encoding requires 2 bytes per sample
    private var audioData = ByteArray(bufferSize16bit)
    private var audioData2 = ByteArray(bufferSize16bit)
    private val audioFromStorageId : Int = R.raw.go1s
    private var audioFromStorageData = ByteArray(bufferSize16bit)
    private var inputBuffer = ByteBuffer.allocateDirect(16000 * 4).order(ByteOrder.nativeOrder())
    private val scoreQueue = LinkedList<Float>()
    private val maxSize = 7

    private val requestPermissionLauncher: ActivityResultLauncher<String> =
        context.registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                Toast.makeText(context, "Permission granted", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Permission is required", Toast.LENGTH_SHORT).show()
            }
        }

    fun initializeModel() {
        try {
            model = ModelV11.newInstance(context)
        } catch (ex : Exception) {
            Toast.makeText(context, "Failed to load model", Toast.LENGTH_SHORT).show()
            throw ex
        }
    }

    // Function to add a new score and compute the average
    fun addScore(newScore: Float): Float {
        if (scoreQueue.size == maxSize) {
            scoreQueue.pollFirst() // Remove the oldest score
        }
        scoreQueue.add(newScore) // Add the new score

        // Compute the average
        return scoreQueue.average().toFloat()
    }

    // Function to update the buffer with new audio data
    private fun updateBuffer(newAudioData: ByteBuffer) {

        // Shift the old data to the beginning
        inputBuffer.position(newAudioData.capacity())

        // Copy remaining seconds into temporary buffer
        val remainingData = ByteArray(inputBuffer.remaining())
        inputBuffer.get(remainingData)
        inputBuffer.clear()  // Reset position for writing

        // Write the remaining data back to the start of the buffer
        inputBuffer.put(remainingData)

        // Append the new data at the end
        inputBuffer.put(newAudioData)

        // Set the limit to the current position and reset position to 0 for reading
        inputBuffer.flip()
    }

    fun isBufferZeroed(buffer: ByteBuffer): Boolean {
        for (i in 0 until buffer.limit()) {
            if (buffer.get(i) != 0.toByte()) {
                return false
            }
        }
        return true
    }

    fun startListeningForKeyword() {
        initializeModel()
        //val minBufferSize = 2000
        val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channel, encoding)

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            minBufferSize
        )

        // Check if initialization was successful
        if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
            Log.e("AudioRecord", "AudioRecord initialization failed")
            return
        }

        audioRecord.startRecording()
        val audioBuffer = ShortArray(minBufferSize / 2) // or use a bufferSize larger if needed

        var patience = 0
        val mediaPlayer = MediaPlayer.create(context, R.raw.ping_sound)
        scope.launch {
            while (isListening) {
//                val bytesRead = audioRecord.read(audioBuffer, 0, audioBuffer.size)

                val bytesRead = audioRecord.read(audioBuffer, 0, audioBuffer.size, AudioRecord.READ_BLOCKING)

                if (bytesRead > 0) {
                    val newAudioData = preprocessAudio(audioBuffer)
                    updateBuffer(newAudioData)

                    val prediction = modelProcessing(inputBuffer)
                    val confidence = prediction.floatArray
                    val averagedConfidence = addScore(confidence[1])

                    if (averagedConfidence > 0.9 && patience == 0) {
                        Log.d("MODEL", "Detected Keyword! Confidence $averagedConfidence")
                        //Toast.makeText(context, "Detected Keyword! Confidence $averagedConfidence", Toast.LENGTH_SHORT).show()

                        if (mediaPlayer.isPlaying) {
                            mediaPlayer.stop()
                            mediaPlayer.prepare() // Reset the player to prepare for next sound
                        }
                        mediaPlayer.start()

                        withContext(Dispatchers.Main) {
                            viewModel.updateKeywordCount()
                        }
                        patience += 48
                    } else if (patience > 0) {
                        patience -= 1
                    }
                } else {
                    Log.e("AudioRecord", "Failed to read audio data")
                }
            }
        }.start()
    }

    private fun preprocessAudio(audioBuffer: ShortArray): ByteBuffer {
        val out = ByteBuffer.allocateDirect(audioBuffer.size * 4).order(ByteOrder.nativeOrder())
        for (sample in audioBuffer) {
            val normalizedSample = sample.toFloat() / 32768.0f // Normalize to the range -1.0 to 1.0
            out.putFloat(normalizedSample)
        }
        out.rewind() // Ensure the buffer is ready to be read from
        return out
    }

    fun initializeMicrophone() {
        if (ContextCompat.checkSelfPermission(context,
                Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {

            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channel,
                encoding,
                bufferSize16bit
            )

        } else {
            Toast.makeText(context, "Audio permission required", Toast.LENGTH_SHORT).show()
            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun modelProcessing(byteBuffer: ByteBuffer) : TensorBuffer {
        val inputFeature0 = TensorBuffer.createFixedSize(intArrayOf(1, 16000), DataType.FLOAT32)
        inputFeature0.loadBuffer(byteBuffer)
        val outputs = model.process(inputFeature0)
        val outputFeature0 = outputs.outputFeature0AsTensorBuffer

        return outputFeature0
    }

    fun startRecording() {
        audioRecord.startRecording()
        audioRecord.read(audioData, 0, audioData.size)
        audioRecord.read(audioData2, 0, audioData2.size)
        audioRecord.stop()
    }

    fun playRecording() {
        val at = AudioTrack(
            AudioTrack.MODE_STREAM,
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            encoding,
            bufferSize16bit,
            AudioTrack.MODE_STREAM
        )
        at.play()

        at.write(audioData,0,  audioData.size)
        at.write(audioData2, 0, audioData2.size)
    }

    fun predictRecording() {
        val buffer = convertByteArrayToFloat32Buffer(audioData2)
        val prediction = modelProcessing(buffer)
        val confidence = prediction.floatArray
        viewModel.updatePredictionScore(confidence)
    }

    fun loadAudioFromStorage() {
        val inputStream = context.resources.openRawResource(audioFromStorageId)
        val inputBytes = inputStream.readBytes()
        audioFromStorageData = trimHeader(inputBytes)
        Toast.makeText(context, "Audio from storage loaded successfully", Toast.LENGTH_SHORT).show()
    }

    fun playAudioFromStorage() {
        Toast.makeText(context, "Playing audio from storage", Toast.LENGTH_SHORT).show()
        val at = AudioTrack(
            AudioTrack.MODE_STREAM,
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            encoding,
            1200,
            AudioTrack.MODE_STREAM
        )
        at.play()
        at.write(audioFromStorageData, 0, audioFromStorageData.size)
    }

    fun predictAudioFromStorage() {
        val buffer = convertByteArrayToFloat32Buffer(audioFromStorageData)
        val prediction = modelProcessing(buffer)
        val confidence = prediction.floatArray
        viewModel.updatePredictionScore(confidence)
    }

    fun startRecordingToFile() {
        val filePath = "${context.applicationInfo.dataDir}/audio_recorded_${System.currentTimeMillis()}.pcm"
        val bufferSize = bufferSize16bit

        val audioData = ByteArray(bufferSize)
        isRecording = true
        Log.d("MainActivity", "Recording started")
        Toast.makeText(context, "Starting recording", Toast.LENGTH_SHORT).show()

        // Initialize AudioRecord here
        audioRecord = AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, channel, encoding, bufferSize16bit)

        Thread {
            try {
                audioRecord.startRecording()
                FileOutputStream(filePath).use { fos ->
                    while (isRecording) {
                        val readBytes = audioRecord.read(audioData, 0, bufferSize)
                        if (readBytes > 0) {
                            fos.write(audioData, 0, readBytes)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Error during recording: ${e.message}", e)
            } finally {
                audioRecord.stop()
                audioRecord.release()
            }

            Log.d("MainActivity", "Recording stopped. Audio recorded to $filePath")
        }.start()
    }

    fun stopRecordingToFile() {
        isRecording = false
        Log.d("MainActivity", "Audio recording stopped")
        Toast.makeText(context, "Finished recording", Toast.LENGTH_SHORT).show()
    }

    private fun byteArrayToFloatArray(byteArray: ByteArray): FloatArray {
        // Create a ByteBuffer from the ByteArray and set the order to little-endian
        val byteBuffer = ByteBuffer.wrap(byteArray).order(ByteOrder.LITTLE_ENDIAN)

        // Create a FloatArray of the correct size (each float is 4 bytes)
        val floatArray = FloatArray(byteArray.size / 4)

        // Convert the ByteArray to FloatArray
        byteBuffer.asFloatBuffer().get(floatArray)

        return floatArray
    }

    private fun byteArrayToBits(byteArray: ByteArray): String {
        // Convert each byte to its 8-bit binary representation and join them into a single string
        return byteArray.joinToString("") {
            it.toInt().and(0xFF).toString(2).padStart(8, '0')
        }
    }

    /**
     * Converts a 16-bit signed ByteArray to a 32-bit signed float ByteBuffer
     * @return a 32-bit signed float ByteBuffer.
     */
    private fun convertByteArrayToFloat32Buffer(byteArray: ByteArray): ByteBuffer {

        val floatBuffer = ByteBuffer.allocateDirect(byteArray.size / 2 * 4) // each float32 takes 4 bytes
        floatBuffer.order(ByteOrder.nativeOrder()) // Ensure native byte order

        // Convert the byte array to 16-bit signed integers and normalize to float32
        for (i in byteArray.indices step 2) {
            val sample = ((byteArray[i + 1].toInt() shl 8) or (byteArray[i].toInt() and 0xFF)).toShort()
            val normalizedSample = sample / 32768.0f
            floatBuffer.putFloat(normalizedSample)
        }

        // Prepare the buffer for reading
        floatBuffer.rewind()

        return floatBuffer
    }

    private fun convertByteArrayToFloat16Buffer(byteArray: ByteArray): ByteBuffer {
        // Create a ByteBuffer for the float32 output
        val floatBuffer = ByteBuffer.allocateDirect(byteArray.size / 2 * 4 ) // each float32 takes 4 bytes
        floatBuffer.order(ByteOrder.nativeOrder()) // Ensure native byte order

        // Convert the byte array to 16-bit signed integers and normalize to float32
        for (i in byteArray.indices step 2) {
            val sample = ((byteArray[i + 1].toInt() shl 8) or (byteArray[i].toInt() and 0xFF)).toShort()
            val normalizedSample = sample / 32768.0f
            floatBuffer.putFloat(normalizedSample)
        }

        // Prepare the buffer for reading
        floatBuffer.rewind()

        return floatBuffer
    }

    private fun trimHeader(byteArray: ByteArray): ByteArray {
        return if (byteArray.size > 32_043) {
            // Trim the byte array
            byteArray.copyOfRange(44, 32_044)
        } else {
            // If it's already 64,000 bytes or less, return as is
            byteArray.copyOf(32_000)
        }
    }

    private fun loadAudioAsByteBuffer(audioName: String): ByteBuffer {

        val file = File(context.filesDir, audioName)
        val fileInputStream = FileInputStream(file)
        val fileChannel: FileChannel = fileInputStream.channel
        val size = fileChannel.size().toInt()

        // Allocate ByteBuffer with the size of the file
        val byteBuffer = ByteBuffer.allocate(size)

        // Read file into ByteBuffer
        fileChannel.read(byteBuffer)


        // Flip the buffer to prepare for reading
        byteBuffer.flip()

        // Close the input stream and channel
        fileChannel.close()
        fileInputStream.close()
        return byteBuffer

    }
}
