package ru.lavafrai.kfft

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Cooley–Tukey radix-2 Fast Fourier Transform (single-threaded).
 *
 * Computes the DFT in O(n log n) time using an iterative in-place algorithm with
 * bit-reversal permutation. **Input size must be a power of two**; an
 * [IllegalArgumentException] is thrown otherwise.
 *
 * The forward transform writes all N complex bins to the output buffer.
 * The inverse transform reads all N bins and reconstructs the real-valued signal.
 *
 * @see MultiThreadedFastFourierTransform for a parallel version.
 * @see DiscreteFourierTransform for arbitrary-size inputs (O(n²)).
 */
class FastFourierTransform: FourierTransform {
    /**
     * Computes the forward FFT of a real-valued signal.
     *
     * @param input the real-valued time-domain signal. Length must be a power of two.
     * @param output a [ComplexBuffer] of the same size as [input] to receive the spectrum.
     * @throws IllegalArgumentException if [input] size is not a power of two.
     */
    override fun forward(input: DoubleArray, output: ComplexBuffer) {
        val n = input.size
        require(isPowerOfTwo(n)) { "FastFourierTransform requires input size to be a power of two, but got $n" }

        for (i in 0 until n) {
            output.real[i] = input[i]
            output.imag[i] = 0.0
        }

        processInPlace(output.real, output.imag, invert = false)
    }

    /**
     * Computes the inverse FFT, reconstructing the real-valued signal from the full spectrum.
     *
     * @param input a [ComplexBuffer] of size N containing the spectrum. Size must be a power of two.
     * @param output a [DoubleArray] of length N to receive the reconstructed signal.
     * @throws IllegalArgumentException if [input] size is not a power of two.
     */
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