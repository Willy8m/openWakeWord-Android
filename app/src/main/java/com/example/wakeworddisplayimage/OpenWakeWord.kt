package com.example.wakeworddisplayimage

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.util.Log
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.example.wakeworddisplayimage.ml.Alexa20000100001200
import com.example.wakeworddisplayimage.ml.EmbeddingModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.tensorflow.lite.DataType
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.LinkedList


class OpenWakeWord (private val context: MainActivity, private val viewModel: MainViewModel) {

    // Configuration
    private val gain = 3
    private var maxPatience = 20
    private val audioBufferSizeInBytes = 1280 * 4
    private val maxScores = 1

    // Models
    private lateinit var melspecOnnx: OrtSession
    private lateinit var embeddingModel : EmbeddingModel
    private lateinit var wakewordModel : Alexa20000100001200

    // Buffers
    private val newAudioData = FloatArray(1280)
    private val rawDataBuffer = FloatArray(1760)
    private val melspecBuffer = Array(1) { Array(76) { Array (32) { FloatArray (1) } } }
    private val embeddingBuffer = Array(1) { Array (16) { FloatArray(96) } }
    private val scoreQueue = LinkedList<Float>()
    private var averagedConfidence = 0f
    private var confidence = FloatArray(1)

    // Other
    private lateinit var env: OrtEnvironment
    private var isListening = true
    private val mediaPlayer = MediaPlayer.create(context, R.raw.ping_sound)
    private val requestPermissionLauncher: ActivityResultLauncher<String> =
        context.registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                Toast.makeText(context, "Permission granted", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Permission is required", Toast.LENGTH_SHORT).show()
            }
        }

    // #################
    //  --- Methods ---
    // #################

    private fun initializeModels() {
        try {
            env = OrtEnvironment.getEnvironment()
            val modelPath = context.assets.open("melspectrogram.onnx").readBytes()
            melspecOnnx = env.createSession(modelPath)
            embeddingModel = EmbeddingModel.newInstance(context)
            wakewordModel = Alexa20000100001200.newInstance(context)
        } catch (ex : Exception) {
            Log.e("openWakeWord", "FAILED TO LOAD MODELS")
            throw ex
        }
    }

    private fun initializeMicrophone() : AudioRecord? {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            val audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                16000,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_FLOAT,
                audioBufferSizeInBytes
            )
            if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
                Log.e("AudioRecord", "AudioRecord initialization failed")
            }
            return audioRecord
        } else {
            Toast.makeText(context, "Audio permission required", Toast.LENGTH_SHORT).show()
            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return null
        }
    }

    fun startListeningForKeyword() {
        initializeModels()
        val audioRecord = initializeMicrophone()
        audioRecord?.startRecording()
        val scope = CoroutineScope(Dispatchers.IO) // Scope for background tasks
        var patience = 0

        scope.launch {
            while (isListening) {
                val sizeInFloats = 1280
                val offsetInFloats = 0
                val floatsRead = audioRecord?.read(newAudioData, offsetInFloats, sizeInFloats, AudioRecord.READ_BLOCKING)
                if (floatsRead == 1280) {

                    bufferRawData()
                    bufferMelspec()
                    bufferEmbeddings()
                    getWakeWordPrediction()

                    if (patience > 0) {
                        patience -= 1
                    } else if (confidence[0] > 0.35 && patience == 0) {
                        patience = maxPatience  //  Number of frames to wait until next detection
                        withContext(Dispatchers.Main) { viewModel.addCount() }
                        playSound()
                        Log.d("MODEL", "Detected Keyword! Confidence $averagedConfidence Patience $patience")
                    }
                } else {
                    Log.e("openWakeWord", "Failed to read audio data")
                }
            }
        }.start()
    }

    private suspend fun getWakeWordPrediction() {
        val wakewordPrediction = wakewordModelPredict(wakewordInput(embeddingBuffer))
        confidence = wakewordPrediction.floatArray
        addScore(confidence[0])
        withContext(Dispatchers.Main) { viewModel.updatePredictionScore(confidence) }
    }

    private fun bufferEmbeddings() {
        val embeddingPredictions = embeddingModelPredict(embeddingInput(melspecBuffer))
        val newEmbeddings = embeddingPredictions.floatArray

        // Move old data to the start
        for (i in 0 until 15) {
            for (j in 0 until 96) {
                embeddingBuffer[0][i][j] = embeddingBuffer[0][i + 1][j]
            }
        }

        // Add the new data at the end
        for (j in 0 until 96) {
            embeddingBuffer[0][15][j] = newEmbeddings[j]
        }
    }

    private fun bufferRawData() {
        for (i in newAudioData.indices) {
            newAudioData[i] = gain * newAudioData[i]
        }
        System.arraycopy(rawDataBuffer, 1280, rawDataBuffer, 0, 480)  // Move old data to the start
        System.arraycopy(newAudioData, 0, rawDataBuffer, 480, 1280)  // Put new data at the end
    }

    private fun bufferMelspec() {
        val melspecPredictions = melspecOnnxPredict(rawDataBuffer)
        
        for (i in 0 until 68) {
            for (j in 0 until 32) {
                melspecBuffer[0][i][j][0] = melspecBuffer[0][i + 8][j][0]
            }
        }

        // Add the new data at the end
        for (i in 0 until 8) {
            for (j in 0 until 32) {
                melspecBuffer[0][68 + i][j][0] = 2 + melspecPredictions[0][0][i][j] / 10
            }
        }
    }

    private fun melspecOnnxPredict(floatArray: FloatArray) : Array<Array<Array<FloatArray>>> {
        val floatBuffer = FloatBuffer.wrap(floatArray)
        val prediction = runInference(floatBuffer)
        return prediction!!
    }

    fun runInference(inputData: FloatBuffer?): Array<Array<Array<FloatArray>>>? {
        try {
            val inputTensor = OnnxTensor.createTensor(env, inputData, longArrayOf(1, 1760))
            val inputs: MutableMap<String, OnnxTensor> = HashMap()
            inputs["input"] = inputTensor

            val result: OrtSession.Result = melspecOnnx.run(inputs)
            val outputTensor = result[0] as OnnxTensor
            val output = outputTensor.value as Array<Array<Array<FloatArray>>>

            inputTensor.close()
            outputTensor.close()
            return output
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
            return null
        }
    }

    private fun embeddingInput(data: Array<Array<Array<FloatArray>>>): ByteBuffer {
        val flattenedData = FloatArray(1 * 76 * 32 * 1)
        var index = 0
        for (i in 0 until 76) {
            for (j in 0 until 32) {
                flattenedData[index++] = data[0][i][j][0]
            }
        }
        val byteBuffer = ByteBuffer.allocateDirect(flattenedData.size * 4).order(ByteOrder.nativeOrder())
        byteBuffer.asFloatBuffer().put(flattenedData)
        return byteBuffer
    }

    private fun embeddingModelPredict(byteBuffer: ByteBuffer) : TensorBuffer {
        val inputFeature0 = TensorBuffer.createFixedSize(intArrayOf(1, 76, 32, 1), DataType.FLOAT32)
        inputFeature0.loadBuffer(byteBuffer)
        val outputs = embeddingModel.process(inputFeature0)
        val outputFeature0 = outputs.outputFeature0AsTensorBuffer
        return outputFeature0
    }

    private fun wakewordInput(data: Array<Array<FloatArray>>): ByteBuffer {
        val flattenedData = FloatArray(1 * 1 * 16 * 96)
        var index = 0
        for (i in 0 until 16) {
            for (j in 0 until 96) {
                flattenedData[index++] = data[0][i][j]
            }
        }
        val byteBuffer = ByteBuffer.allocateDirect(flattenedData.size * 4).order(ByteOrder.nativeOrder())
        byteBuffer.asFloatBuffer().put(flattenedData)
        return byteBuffer
    }

    private fun wakewordModelPredict(byteBuffer: ByteBuffer) : TensorBuffer {
        val inputFeature0 = TensorBuffer.createFixedSize(intArrayOf(1, 16, 96), DataType.FLOAT32)
        inputFeature0.loadBuffer(byteBuffer)
        val outputs = wakewordModel.process(inputFeature0)
        val outputFeature0 = outputs.outputFeature0AsTensorBuffer
        return outputFeature0
    }

    fun addScore(newScore: Float) {
        if (scoreQueue.size == maxScores) {
            scoreQueue.pollFirst() // Remove the oldest score
        }
        scoreQueue.add(newScore) // Add the new score
        averagedConfidence = scoreQueue.average().toFloat()
    }

    fun playSound () {
        if (mediaPlayer.isPlaying) {mediaPlayer.stop(); mediaPlayer.prepare()}
        mediaPlayer.start()
    }
}
