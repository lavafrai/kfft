package ru.lavafrai.kfft

import kotlin.math.atan2
import kotlin.math.sqrt

class ComplexBuffer private constructor(
    val real: DoubleArray,
    val imag: DoubleArray
) {
    val size: Int get() = real.size

    fun get(index: Int): Pair<Double, Double> {
        return Pair(real[index], imag[index])
    }

    fun getMagnitude(index: Int): Double {
        return sqrt(real[index] * real[index] + imag[index] * imag[index])
    }

    fun getPhase(index: Int): Double {
        return atan2(imag[index], real[index])
    }

    companion object {
        @JvmStatic
        fun allocate(n: Int): ComplexBuffer {
            return ComplexBuffer(DoubleArray(n), DoubleArray(n))
        }
    }
}