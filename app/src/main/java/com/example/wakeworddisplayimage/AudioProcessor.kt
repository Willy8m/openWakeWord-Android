package com.example.wakeworddisplayimage

import org.jtransforms.fft.FloatFFT_1D
import kotlin.math.hypot
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin

class AudioProcessor {

    fun computeSTFT(audioData: FloatArray, sampleRate: Int, windowSize: Int, hopSize: Int): Array<FloatArray> {
        val fft = FloatFFT_1D(windowSize.toLong())
        val numWindows = (audioData.size - windowSize) / hopSize + 1
        val stftResult = Array(numWindows) { FloatArray(windowSize) }

        for (i in 0 until numWindows) {
            val windowStart = i * hopSize
            val window = audioData.copyOfRange(windowStart, windowStart + windowSize)

            // Apply FFT
            fft.realForward(window)

            // Calculate magnitude
            for (j in 0 until windowSize / 2) {
                stftResult[i][j] = hypot(window[2 * j], window[2 * j + 1])
            }
        }

        return stftResult
    }

    fun melToFreq(mel: Double): Double {
        return 700 * (10.0.pow(mel / 2595.0) - 1)
    }

    fun freqToMel(freq: Double): Double {
        return 2595 * log10(1 + freq / 700)
    }

    fun createMelFilterBank(sampleRate: Int, fftSize: Int, numMelBands: Int): Array<FloatArray> {
        val melMin = 0.0
        val melMax = freqToMel(sampleRate / 2.0)
        val melPoints = DoubleArray(numMelBands + 2) { i ->
            melToFreq(melMin + (melMax - melMin) * i / (numMelBands + 1))
        }
        val fftFreqs = DoubleArray(fftSize / 2 + 1) { i ->
            i * sampleRate.toDouble() / fftSize
        }

        val filterBank = Array(numMelBands) { FloatArray(fftSize / 2 + 1) }

        for (i in 0 until numMelBands) {
            val left = melPoints[i]
            val center = melPoints[i + 1]
            val right = melPoints[i + 2]

            for (j in fftFreqs.indices) {
                val freq = fftFreqs[j]
                when {
                    freq < left -> filterBank[i][j] = 0.0f
                    freq <= center -> filterBank[i][j] = ((freq - left) / (center - left)).toFloat()
                    freq <= right -> filterBank[i][j] = ((right - freq) / (right - center)).toFloat()
                    else -> filterBank[i][j] = 0.0f
                }
            }
        }

        return filterBank
    }

    fun applyMelFilterBank(stftResult: Array<FloatArray>, melFilterBank: Array<FloatArray>): Array<FloatArray> {
        val melSpectrogram = Array(melFilterBank.size) { FloatArray(stftResult.size) }

        for (i in melFilterBank.indices) {
            for (j in stftResult.indices) {
                melSpectrogram[i][j] = stftResult[j].zip(melFilterBank[i]) { mag, filter -> mag * filter }.sum()
            }
        }

        return melSpectrogram
    }

    fun computeMelSpectrogram(audioData: FloatArray, sampleRate: Int, windowSize: Int, hopSize: Int, numMelBands: Int): Array<FloatArray> {
        val stftResult = computeSTFT(audioData, sampleRate, windowSize, hopSize)
        val melFilterBank = createMelFilterBank(sampleRate, windowSize, numMelBands)
        return applyMelFilterBank(stftResult, melFilterBank)
    }

    fun main() {
        val sampleRate = 44100
        val windowSize = 2048
        val hopSize = 512
        val numMelBands = 40

        // Example audio data (replace with actual audio data)
        val audioData = FloatArray(44100) { sin(2 * Math.PI * 440 * it / sampleRate).toFloat() }

        val melSpectrogram = computeMelSpectrogram(audioData, sampleRate, windowSize, hopSize, numMelBands)

        // Print or visualize the mel-spectrogram
        melSpectrogram.forEach { band ->
            println(band.joinToString(", "))
        }
    }
}