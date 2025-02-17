package com.example.wakeworddisplayimage

import android.Manifest
import android.annotation.SuppressLint
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.example.wakeworddisplayimage.ui.theme.WakeWordDisplayImageTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar

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
//                    Row {
//                        AudioRecorderUI()
//                    }
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
//    val calendar: Calendar = Calendar.getInstance()
//    val hour: Int = calendar.get(Calendar.HOUR_OF_DAY)
//    val minute: Int = calendar.get(Calendar.MINUTE)
//    val second: Int = calendar.get(Calendar.SECOND)

    val recorder = remember { AudioRecorder(context) }
    var isRecording by remember { mutableStateOf(false) }
//    var audioFilePath by remember { mutableStateOf<String?>(null) }
//    val mediaPlayer = remember { MediaPlayer() }
    val coroutineScope = rememberCoroutineScope()

    val words = listOf("test", "maia", "hola-maia", "ari", "hola-ari", "ayud", "hola-ayud", "alexa")
    var selectedWord by remember { mutableStateOf(words[0]) }
    var countdownText by remember { mutableStateOf("") }
    var recordingCount by remember { mutableStateOf(0) }
    var totalRecordings by remember { mutableStateOf(5) }
    val recordingDurationMillis = 4000L // 4 seconds
    val offset = 0.05f // simulated offset [0, 1] to show minimum waveform value
    val gain = 3  // simulated gain for the waveform to show smaller noise too

    // Waveform State
    val audioWaveform = remember { mutableStateOf<List<Float>>(listOf()) }
    val totalWaveformSamples = 170 // Number of points to display for 4 seconds

    fun generateWaveform() {
        recorder.setAmplitudeListener { amplitude ->
            // Normalize amplitude (max value for getMaxAmplitude is 32767)
            val normalizedAmplitude = amplitude / 32767f
            val waveform = audioWaveform.value.toMutableList()
            waveform.add(normalizedAmplitude)
            if (waveform.size > totalWaveformSamples) waveform.removeAt(0) // Accumulate samples progressively
            audioWaveform.value = waveform
        }
    }

    fun startRecordingSequence() {
        coroutineScope.launch {
            recordingCount = 0
            // Countdown
            for (count in 3 downTo 1) {
                countdownText = "$count..."
                delay(1000L)
            }
            for (i in 1..totalRecordings) {
                // Start Recording
                countdownText = ""
                recorder.startRecording(selectedWord)
                isRecording = true
                generateWaveform()
                delay(recordingDurationMillis) // Record for 5 seconds

                // Stop Recording
                recorder.stopRecording()
                isRecording = false
                audioWaveform.value = listOf() // Clear waveform after stopping
                recordingCount++
                countdownText = ""
            }
        }
    }

    Column(
        modifier = Modifier.padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.Start
    ) {
        // Word Selection
        Text(text = "Select a word:", fontSize = 4.em)
        Spacer(modifier = Modifier.height(8.dp))
        var expanded by remember { mutableStateOf(false) }
        Box(modifier = Modifier
            .fillMaxWidth()
            .background(Color.LightGray)
            .clickable { expanded = true }
            .padding(8.dp)) {
            Text(text = selectedWord, fontSize = 4.em)
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                words.forEach { word ->
                    DropdownMenuItem(
                        text = { Text(text = word, fontSize = 4.em) },
                        onClick = {
                            selectedWord = word
                            expanded = false
                        })
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Total Recordings Selector
        Text(text = "Select number of recordings:", fontSize = 4.em)
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(onClick = { if (totalRecordings > 1) totalRecordings-- }) {
                Text("-", fontSize = 4.em)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = "$totalRecordings", fontSize = 4.em)
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = { totalRecordings++ }) {
                Text("+", fontSize = 4.em)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Waveform Display
        if (isRecording) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .background(Color.LightGray)
            ) {
                val waveform = audioWaveform.value
                val step = size.width / totalWaveformSamples

                waveform.forEachIndexed { index, amplitude ->
                    val x = step * index
                    val y = size.height / 2

                    drawLine(
                        color = Color.Blue,
                        start = Offset(x, y - (gain * amplitude + offset) * size.height / 2),
                        end = Offset(x, y + (gain * amplitude + offset) * size.height / 2),
                        strokeWidth = step
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

//        // Display Progress
//        if (isRecording) {
//            Text(
//                text = "Recording ${recordingCount + 1} of $totalRecordings",
//                fontSize = 6.em
//            )
//        }
//
//        Spacer(modifier = Modifier.height(16.dp))

        // Countdown and Repeating Text
        Text(text = countdownText, fontSize = 6.em)

        Spacer(modifier = Modifier.height(16.dp))

        // Start Recording Button
        Button(
            onClick = {
                if (!isRecording) {
                    startRecordingSequence()
                }
            },
            enabled = !isRecording
        ) {
            Text(if (isRecording) "Recording..." else "Start Recording", fontSize = 4.em)
        }
    }
}





