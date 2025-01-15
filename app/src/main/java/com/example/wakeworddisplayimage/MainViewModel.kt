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

    private var _audioVolume: MutableLiveData<Float> = MutableLiveData(0f)
    val audioVolume: LiveData<Float> = _audioVolume

    fun updateAudioVolume(audio: FloatArray) {
        val audioVolume: Float = findMax(audio)
        _audioVolume.value = audioVolume
    }

    fun findMax(array: FloatArray?): Float {
        require(!(array == null || array.isEmpty())) { "Array must not be null or empty" }

        var max = array[0]
        for (num in array) {
            if (num > max) {
                max = num
            }
        }
        return max
    }

}
