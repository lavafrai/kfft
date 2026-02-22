package ru.lavafrai.kfft

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class FastFourierTransform: FourierTransform {
    override fun forward(input: DoubleArray, output: ComplexBuffer) {
        val n = input.size
        require(isPowerOfTwo(n)) { "FastFourierTransform requires input size to be a power of two, but got $n" }

        for (i in 0 until n) {
            output.real[i] = input[i]
            output.imag[i] = 0.0
        }

        processInPlace(output.real, output.imag, invert = false)
    }

    override fun inverse(input: ComplexBuffer, output: DoubleArray) {
        val n = input.size
        require(isPowerOfTwo(n)) { "FastFourierTransform requires input size to be a power of two, but got $n" }

        val tempReal = input.real.copyOf()
        val tempImag = input.imag.copyOf()

        processInPlace(tempReal, tempImag, invert = true)

        for (i in 0 until n) {
            output[i] = tempReal[i] / n
        }
    }

    private fun isPowerOfTwo(n: Int): Boolean {
        return n > 0 && (n and (n - 1)) == 0
    }
    private fun processInPlace(real: DoubleArray, imag: DoubleArray, invert: Boolean) {
        val n = real.size

        var j = 0
        for (i in 0 until n - 1) {
            if (i < j) {
                val tempR = real[i]; real[i] = real[j]; real[j] = tempR
                val tempI = imag[i]; imag[i] = imag[j]; imag[j] = tempI
            }
            var m = n shr 1
            while (m <= j) {
                j -= m
                m = m shr 1
            }
            j += m
        }

        val angleSign = if (invert) 1.0 else -1.0
        var len = 2

        while (len <= n) {
            val halfLen = len shr 1
            val angle = 2.0 * PI / len * angleSign

            val wStepReal = cos(angle)
            val wStepImag = sin(angle)

            for (i in 0 until n step len) {
                var wReal = 1.0
                var wImag = 0.0

                for (k in 0 until halfLen) {
                    val u = i + k
                    val v = i + k + halfLen

                    val tr = wReal * real[v] - wImag * imag[v]
                    val ti = wReal * imag[v] + wImag * real[v]

                    real[v] = real[u] - tr
                    imag[v] = imag[u] - ti
                    real[u] += tr
                    imag[u] += ti

                    val nextWReal = wReal * wStepReal - wImag * wStepImag
                    val nextWImag = wReal * wStepImag + wImag * wStepReal
                    wReal = nextWReal
                    wImag = nextWImag
                }
            }
            len = len shl 1
        }
    }
}