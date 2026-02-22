package ru.lavafrai.kfft

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Optimized O(n²) Discrete Fourier Transform implementation exploiting Hermitian symmetry.
 *
 * For a real-valued input of length N, the forward transform computes only
 * the first N/2 + 1 frequency bins (indices 0..N/2), since the remaining bins are
 * complex conjugates. This halves the work compared to [NaiveDiscreteFourierTransform].
 *
 * The inverse transform reconstructs the full real signal from the N/2 + 1 stored bins
 * by implicitly mirroring the conjugate-symmetric part.
 *
 * **Output buffer size:** the [ComplexBuffer] passed to [forward] must have at least N/2 + 1 elements.
 *
 * This implementation works with any input size (not limited to powers of two).
 *
 * @see FastFourierTransform for an O(n log n) alternative (requires power-of-two size).
 */
class DiscreteFourierTransform: FourierTransform {
    /**
     * Computes the forward DFT of a real-valued signal, writing N/2 + 1 complex bins to [output].
     *
     * @param input the real-valued time-domain signal of length N.
     * @param output a [ComplexBuffer] of at least N/2 + 1 elements to receive the spectrum.
     */
    override fun forward(input: DoubleArray, output: ComplexBuffer) {
        val n = input.size

        for (k in 0..n / 2) {
            var sumReal = 0.0
            var sumImag = 0.0
            val omega = 2.0 * PI * k / n

            for (j in 0 until n) {
                val angle = omega * j
                sumReal += input[j] * cos(angle)
                sumImag -= input[j] * sin(angle)
            }
            output.real[k] = sumReal
            output.imag[k] = sumImag
        }
    }

    /**
     * Reconstructs the real-valued time-domain signal from N/2 + 1 complex frequency bins.
     *
     * Exploits Hermitian symmetry to recover all N samples from the half-spectrum stored in [input].
     *
     * @param input a [ComplexBuffer] containing the half-spectrum (N/2 + 1 bins), where `input.size` equals the full signal length N.
     * @param output a [DoubleArray] of length N to receive the reconstructed signal.
     */
    override fun inverse(input: ComplexBuffer, output: DoubleArray) {
        val n = input.size
        val maxPairedK = (n - 1) / 2

        for (j in 0 until n) {
            val omega = 2.0 * PI * j / n
            var sumReal = input.real[0]

            for (k in 1..maxPairedK) {
                val angle = omega * k
                val term = input.real[k] * cos(angle) - input.imag[k] * sin(angle)
                sumReal += 2.0 * term
            }

            if (n % 2 == 0) {
                val halfN = n / 2
                val angle = omega * halfN
                sumReal += input.real[halfN] * cos(angle) - input.imag[halfN] * sin(angle)
            }

            output[j] = sumReal / n
        }
    }
}