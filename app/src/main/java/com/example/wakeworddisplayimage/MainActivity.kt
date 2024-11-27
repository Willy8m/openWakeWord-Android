package com.example.wakeworddisplayimage

import android.Manifest
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
import androidx.compose.ui.graphics.Color

class MainActivity : ComponentActivity() {

    private lateinit var mediaRecorder : MediaRecorder
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

        //val audioRecorder = AudioRecorder(this@MainActivity, viewModel)
        //audioRecorder.startListeningForKeyword()

        enableEdgeToEdge()
        setContent {
            WakeWordDisplayImageTheme {
                Column(modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally)
                {
                    Row {
                        MyScore(
                            context = this@MainActivity,
                            viewModel = viewModel,
                            modifier = Modifier
                        )
                    }
                    Row {
                        MyKeywordCount(
                            context = this@MainActivity,
                            viewModel = viewModel,
                            modifier = Modifier
                        )
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
    viewModel.keywordCount.observe(context) {
        keywordCount = it
    }
    Column {
        Text(
            text = "Keyword count",
            fontSize = 30.sp,
            modifier = modifier
                .padding(horizontal = 8.dp))
        Text(
            text = "$keywordCount",
            fontSize = 60.sp,
            modifier = modifier
                .padding(horizontal = 8.dp)
                .align(alignment = Alignment.CenterHorizontally)
        )
    }
}

@Composable
fun MyScore(context: MainActivity, viewModel: MainViewModel, modifier: Modifier) {
    var predictionScores by remember {
        mutableStateOf<FloatArray?>(null)
    }
    viewModel.predictionScores.observe(context) {
        predictionScores = it
    }
    if (predictionScores != null) {
        Column {
            for (score in predictionScores!!) {
                PredictionBar(
                    score = score
                )
                Text(
                    text = "Prediction score: $score",
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
