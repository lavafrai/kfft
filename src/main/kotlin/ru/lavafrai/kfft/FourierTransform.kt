package ru.lavafrai.kfft

/**
 * Common interface for all Fourier transform implementations.
 *
 * Implementations provide forward (time → frequency) and inverse (frequency → time) transforms
 * operating on real-valued signals stored as [DoubleArray] and complex spectra stored as [ComplexBuffer].
 *
 * Available implementations:
 * - [NaiveDiscreteFourierTransform] — straightforward O(n²) DFT, computes all N frequency bins.
 * - [DiscreteFourierTransform] — optimized O(n²) DFT exploiting Hermitian symmetry, computes only N/2+1 bins.
 * - [FastFourierTransform] — Cooley–Tukey radix-2 FFT, O(n log n), requires power-of-two input size.
 * - [MultiThreadedFastFourierTransform] — parallel radix-2 FFT using a thread pool.
 *
 * @see ComplexBuffer
 */
interface FourierTransform {
    /**
     * Computes the forward Fourier transform (time domain → frequency domain).
     *
     * @param input the real-valued time-domain signal.
     * @param output a pre-allocated [ComplexBuffer] to receive the frequency-domain result.
     *               Must have sufficient size for the output (implementation-dependent).
     */
    fun forward(input: DoubleArray, output: ComplexBuffer)

    /**
     * Computes the inverse Fourier transform (frequency domain → time domain).
     *
     * @param input the complex frequency-domain spectrum.
     * @param output a pre-allocated [DoubleArray] to receive the real-valued time-domain result.
     */
    fun inverse(input: ComplexBuffer, output: DoubleArray)
}