package com.example.wakeworddisplayimage

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaRecorder
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import org.tensorflow.lite.DataType
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.io.IOException
import java.nio.ByteBuffer

class MainViewModel : ViewModel() {

    private var _predictionScores: MutableLiveData<FloatArray> = MutableLiveData(null)
    val predictionScores: LiveData<FloatArray> = _predictionScores

    fun updatePredictionScore(predictionScores: FloatArray) {
        _predictionScores.value = predictionScores
    }

    private var _keywordCount: MutableLiveData<Int> = MutableLiveData<Int>(0)
    val keywordCount: LiveData<Int> = _keywordCount

    fun updateKeywordCount() {
        _keywordCount.value = _keywordCount.value?.plus(1)
    }
}
