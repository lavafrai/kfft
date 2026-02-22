package ru.lavafrai.kfft

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * A simple complex number representation used internally by [NaiveDiscreteFourierTransform].
 *
 * @property real the real part.
 * @property imag the imaginary part.
 */
data class Complex(val real: Double, val imag: Double) {
    /** Returns the sum of this complex number and [other]. */
    operator fun plus(other: Complex) = Complex(
        this.real + other.real,
        this.imag + other.imag
    )

    /** Returns the product of this complex number and [other]. */
    operator fun times(other: Complex) = Complex(
        this.real * other.real - this.imag * other.imag,
        this.real * other.imag + this.imag * other.real
    )
}

/**
 * Straightforward O(n²) Discrete Fourier Transform implementation.
 *
 * Computes all N frequency bins for a signal of length N without any symmetry optimizations.
 * This implementation is the simplest and easiest to verify for correctness, but also the
 * slowest. It is suitable for small signal sizes or as a reference implementation for testing.
 *
 * The [forward] transform writes all N complex frequency bins to the output buffer.
 * The [inverse] transform reads all N bins and reconstructs the real-valued signal.
 *
 * This implementation works with any input size (not limited to powers of two).
 *
 * @see DiscreteFourierTransform for a symmetry-optimized DFT.
 * @see FastFourierTransform for an O(n log n) FFT (requires power-of-two size).
 */
class NaiveDiscreteFourierTransform : FourierTransform {
    /**
     * Computes the forward DFT, writing all N complex frequency bins to [output].
     *
     * @param input the real-valued time-domain signal of length N.
     * @param output a [ComplexBuffer] of at least N elements to receive the full spectrum.
     */
    override fun forward(input: DoubleArray, output: ComplexBuffer) {
        val n = input.size

        for (k in 0 until n) {
            var sum = Complex(0.0, 0.0)

            for (j in 0 until n) {
                val angle = 2.0 * PI * k * j / n
                val x = Complex(input[j], 0.0)
                val e = Complex(cos(angle), -sin(angle))
                sum += x * e
            }

            output.real[k] = sum.real
            output.imag[k] = sum.imag
        }
    }

    /**
     * Reconstructs the real-valued signal from all N complex frequency bins.
     *
     * @param input a [ComplexBuffer] of size N containing the full spectrum.
     * @param output a [DoubleArray] of length N to receive the reconstructed signal.
     */
    override fun inverse(input: ComplexBuffer, output: DoubleArray) {
        val n = input.size

        for (j in 0 until n) {
            var sum = Complex(0.0, 0.0)

            for (k in 0 until n) {
                val angle = 2.0 * PI * k * j / n
                val x = Complex(input.real[k], input.imag[k])
                val e = Complex(cos(angle), sin(angle))
                sum += x * e
            }

            val result = sum.real / n
            output[j] = result
        }
    }
}