package com.example.wakeworddisplayimage

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusModifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.wakeworddisplayimage.ui.theme.WakeWordDisplayImageTheme
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

    private lateinit var mediaRecorder : MediaRecorder

    private val requestPermissionLauncher: ActivityResultLauncher<String> =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                Toast.makeText(this, "Permission granted", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Permission is required", Toast.LENGTH_SHORT).show()
            }
        }

    private lateinit var audioRecord: AudioRecord
    private var isRecording = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val viewModel : MainViewModel by viewModels()
        var audioRecorder: AudioRecorder = AudioRecorder(this@MainActivity, viewModel)
        var audioProcessor: AudioProcessor = AudioProcessor()

        requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        audioRecorder.initializeMicrophone()
        audioRecorder.initializeModel()

//        viewModel.loadImage(this)

        enableEdgeToEdge()
        setContent {
            WakeWordDisplayImageTheme {
                Column(modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally)
                {
//                    MyImage(this@MainActivity, viewModel, modifier = Modifier)
//
//                    Button(onClick = {
//                        // Ensure permission is granted before starting recording
//                        if (ContextCompat.checkSelfPermission(
//                                this@MainActivity,
//                                Manifest.permission.RECORD_AUDIO
//                            ) == PackageManager.PERMISSION_GRANTED) {
//                            audioRecorder.startRecording()
//                        } else {
//                            Toast.makeText(this@MainActivity, "Audio permission required", Toast.LENGTH_SHORT).show()
//                            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
//                        }
//                    }) {
//                        Text(text = "startRecording")
//                    }
//
//                    Button(onClick = {
//                        audioRecorder.playRecording()
//                    }) {
//                        Text(text = "playRecording")
//                    }
//
//                    Button(onClick = {
//                        audioRecorder.predictRecording()
//                    }) {
//                        Text(text = "findWakeWordInRecording")
//                    }
//
//                    Button(onClick = {
//                        audioRecorder.loadAudioFromStorage()
//                    }) {
//                        Text(text = "loadAudioFromStorage")
//                    }
//
//                    Button(onClick = {
//                        audioRecorder.playAudioFromStorage()
//                    }) {
//                        Text(text = "playAudioFromStorage")
//                    }
//
//                    Button(onClick = {
//                        audioRecorder.predictAudioFromStorage()
//                    }) {
//                        Text(text = "predictAudioFromStorage")
//                    }
//
//                    Row {
//                        MyScore(
//                            context = this@MainActivity,
//                            viewModel = viewModel,
//                            modifier = Modifier)
//                    }
//
                    Button (modifier = Modifier.padding(horizontal = 48.dp).padding(vertical = 48.dp),
                        onClick = {
                        audioRecorder.startRecordingToFile()
                        Handler(Looper.getMainLooper()).postDelayed({
                            audioRecorder.stopRecordingToFile()
                        }, 1 * 1000)
                    }){
                        Text(text = "Record Sample Audio",
                            fontSize = 30.sp,
                            modifier = Modifier.padding(horizontal = 8.dp).padding(vertical = 8.dp))
                    }

                    audioRecorder.startListeningForKeyword()

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
fun MyImage(context: MainActivity, viewModel: MainViewModel, modifier: Modifier) {
    var image by remember {
        mutableStateOf<Bitmap?>(null)
    }
    viewModel.image.observe(context) {
        image = it
    }
    if(image != null) {
        Image(
            contentScale = ContentScale.FillBounds,
            bitmap = image!!.asImageBitmap(),
            contentDescription = "Image",
            modifier = modifier
        )
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
                Text(
                    text = "Prediction score: $score",
                    modifier = modifier.padding(horizontal = 8.dp)
                )
            }
        }
    }
}
