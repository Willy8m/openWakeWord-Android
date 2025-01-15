package com.example.wakeworddisplayimage

import android.Manifest
import android.annotation.SuppressLint
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.wakeworddisplayimage.ui.theme.WakeWordDisplayImageTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign

class MainActivity : ComponentActivity() {

    companion object {
        init {
            System.loadLibrary("native-lib")
        }
    }
    private val requestPermissionLauncher: ActivityResultLauncher<String> =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                Toast.makeText(this, "Permission granted", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Permission is required", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val viewModel : MainViewModel by viewModels()
        requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)

        val openWakeWord = OpenWakeWord(this@MainActivity, viewModel)
        openWakeWord.startListeningForKeyword()

        enableEdgeToEdge()
        setContent {
            WakeWordDisplayImageTheme {
                Column(modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally)
                {
                    Row {
                        MyKeywordCount(context = this@MainActivity,viewModel = viewModel, modifier = Modifier)
                    }
                    Row {
                        MyScore(context = this@MainActivity, viewModel = viewModel, modifier = Modifier)
                    }
                    Row {
                        MyVolume(context = this@MainActivity, viewModel = viewModel, modifier = Modifier)
                    }
                    Row {
                        AudioRecorderUI()
                    }
                }
            }
        }
    }
}

@Composable
fun MyKeywordCount(context: MainActivity, viewModel: MainViewModel, modifier: Modifier) {
    var keywordCount by remember {
        mutableIntStateOf(0)
    }
    viewModel.wakewordCount.observe(context) {
        keywordCount = it
    }
    Column {
        Text(
            text = "Keyword count",
            fontSize = 30.sp,
            modifier = modifier
                .padding(horizontal = 8.dp)
                .align(alignment = Alignment.CenterHorizontally))
        Text(
            text = "$keywordCount",
            fontSize = 60.sp,
            modifier = modifier
                .padding(horizontal = 8.dp)
                .align(alignment = Alignment.CenterHorizontally)
        )
    }
}

@SuppressLint("DefaultLocale")
@Composable
fun MyScore(context: MainActivity, viewModel: MainViewModel, modifier: Modifier) {
    var predictionScores by remember {
        mutableStateOf<FloatArray?>(null)
    }
    viewModel.predictionScores.observe(context) {
        predictionScores = it
    }
    if (predictionScores != null) {
        Column (
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ){
            for (score in predictionScores!!) {
                PredictionBar(
                    score = score
                )
                Text(
                    text = "Prediction score: ${String.format("%.4f", score)}",  // 4 decimals
                    modifier = modifier.padding(horizontal = 8.dp)
                )
            }
        }
    }
}

@Composable
fun PredictionBar(score: Float) {
    // Ensure score is within [0, 1]
    val normalizedScore = score.coerceIn(0f, 1f)
    val percentage = (normalizedScore * 100).toInt() // Calculate percentage

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "$percentage%",
            fontSize = 20.sp,
            color = Color.Black,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // Background bar
        Box(
            modifier = Modifier
                .fillMaxWidth(0.8f) // Control bar width
                .height(24.dp)
                .background(Color.Gray, shape = RoundedCornerShape(12.dp))
        ) {
            // Foreground progress based on score
            Box(
                modifier = Modifier
                    .fillMaxWidth(normalizedScore) // Set width based on score
                    .fillMaxHeight()
                    .background(Color.Blue, shape = RoundedCornerShape(12.dp))
            )
        }
    }
}

@SuppressLint("DefaultLocale")
@Composable
fun MyVolume(context: MainActivity, viewModel: MainViewModel, modifier: Modifier) {
    var audioVolume by remember {
        mutableStateOf<Float?>(0f)
    }
    viewModel.audioVolume.observe(context) {
        audioVolume = it
    }
    if (audioVolume != null) {
        Column (
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ){
            InputVolumeBar(
                audioVolume = audioVolume!!
            )
            Text(
                text = "Audio volume: ${String.format("%.4f", audioVolume)}",
                modifier = modifier.padding(horizontal = 8.dp)
            )

        }
    }
}

@Composable
fun InputVolumeBar(audioVolume: Float) {

    // Ensure volume is within [0, 1]
    val normalizedVolume = audioVolume.coerceIn(0f, 1f)
    val percentage = (normalizedVolume * 100).toInt() // Calculate percentage

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "$percentage%",
            fontSize = 20.sp,
            color = Color.Black,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // Background bar
        Box(
            modifier = Modifier
                .fillMaxWidth(0.8f) // Control bar width
                .height(24.dp)
                .background(Color.LightGray, shape = RoundedCornerShape(12.dp))
        ) {
            // Foreground progress based on volume
            Box(
                modifier = Modifier
                    .fillMaxWidth(normalizedVolume) // Set width based on volume
                    .fillMaxHeight()
                    .background(Color.Magenta, shape = RoundedCornerShape(12.dp))
            )
        }
    }
}

@Composable
fun AudioRecorderUI() {
    val context = LocalContext.current
    val recorder = remember { AudioRecorder(context) }
    var isRecording by remember { mutableStateOf(false) }
    var audioFilePath by remember { mutableStateOf<String?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    val mediaPlayer = remember { MediaPlayer() }

    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Start/Stop Recording Button
        Button(
            onClick = {
                if (isRecording) {
                    audioFilePath = recorder.stopRecording()
                } else {
                    recorder.startRecording()
                }
                isRecording = !isRecording
            }
        ) {
            Text(if (isRecording) "Stop Recording" else "Start Recording")
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Playback Button
        if (audioFilePath != null) {
            Button(
                onClick = {
                    if (isPlaying) {
                        mediaPlayer.stop()
                        mediaPlayer.reset()
                    } else {
                        try {
                            mediaPlayer.setDataSource(audioFilePath)
                            mediaPlayer.prepare()
                            mediaPlayer.start()
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                    isPlaying = !isPlaying
                }
            ) {
                Text(if (isPlaying) "Stop Playback" else "Play Recording")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Save Audio Button
        if (audioFilePath != null) {
            Button(
                onClick = {
                    Toast.makeText(context, "Audio saved: $audioFilePath", Toast.LENGTH_SHORT).show()
                }
            ) {
                Text("Save Recording")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Display the file path
        if (audioFilePath != null) {
            Text(
                text = "File: $audioFilePath",
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

//@Composable
//fun AudioRecorderUI() {
//    val context = LocalContext.current
//    val recorder = remember { AudioRecorder(context) }
//    var isRecording by remember { mutableStateOf(false) }
//    var audioFilePath by remember { mutableStateOf<String?>(null) }
//
//    Column(
//        modifier = Modifier.padding(16.dp),
//        verticalArrangement = Arrangement.Center,
//        horizontalAlignment = Alignment.CenterHorizontally
//    ) {
//        Button(
//            onClick = {
//                if (isRecording) {
//                    audioFilePath = recorder.stopRecording()
//                } else {
//                    recorder.startRecording()
//                }
//                isRecording = !isRecording
//            }
//        ) {
//            Text(if (isRecording) "Stop Recording" else "Start Recording")
//        }
//
//        Spacer(modifier = Modifier.height(8.dp))
//
//        if (audioFilePath != null) {
//            Text(
//                text = "Saved to: $audioFilePath",
//                textAlign = TextAlign.Center,
//                modifier = Modifier.padding(top = 8.dp)
//            )
//        }
//    }
//}

//@Composable
//fun AudioRecorderScreen() {
//    val context = LocalContext.current
//    val recorder = remember { AudioRecorder(context) }
//    var isRecording by remember { mutableStateOf(false) }
//    var audioFilePath by remember { mutableStateOf<String?>(null) }
//
//    Column(
//        modifier = Modifier
//            .fillMaxSize()
//            .padding(16.dp),
//        verticalArrangement = Arrangement.Center,
//        horizontalAlignment = Alignment.CenterHorizontally
//    ) {
//        Button(
//            onClick = {
//                if (isRecording) {
//                    // Stop recording
//                    audioFilePath = recorder.stopRecording()
//                } else {
//                    // Start recording
//                    recorder.startRecording()
//                }
//                isRecording = !isRecording
//            }
//        ) {
//            Text(if (isRecording) "Stop Recording" else "Start Recording")
//        }
//
//        Spacer(modifier = Modifier.height(16.dp))
//
//        if (audioFilePath != null) {
//            Text(text = "Saved to: $audioFilePath", textAlign = TextAlign.Center)
//        }
//    }
//}