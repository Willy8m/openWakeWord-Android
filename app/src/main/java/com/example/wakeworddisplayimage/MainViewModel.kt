package com.example.wakeworddisplayimage

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class MainViewModel : ViewModel() {

    private var _predictionScores: MutableLiveData<FloatArray> = MutableLiveData(null)
    val predictionScores: LiveData<FloatArray> = _predictionScores

    fun updatePredictionScore(predictionScores: FloatArray) {
        _predictionScores.value = predictionScores
    }

    private var _wakewordCount: MutableLiveData<Int> = MutableLiveData<Int>(0)
    val wakewordCount: LiveData<Int> = _wakewordCount

    fun addCount() {
        _wakewordCount.value = _wakewordCount.value?.plus(1)
    }
}
