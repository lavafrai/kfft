package ru.lavafrai.kfft

import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * A buffer holding complex numbers in split real/imaginary format (Structure of Arrays).
 *
 * This layout is cache-friendly and efficient for FFT operations compared to an array of complex objects.
 * Real and imaginary parts are stored in separate [DoubleArray]s of equal length.
 *
 * Instances are created via the [allocate] factory method.
 *
 * @see FourierTransform
 */
class ComplexBuffer private constructor(
    /** The array of real parts of the complex numbers. */
    @JvmField val real: DoubleArray,
    /** The array of imaginary parts of the complex numbers. */
    @JvmField val imag: DoubleArray
) {
    /** The number of complex elements in this buffer. */
    val size: Int get() = real.size

    /**
     * Returns the real part of the complex number at the given [index].
     *
     * @param index the zero-based index of the element.
     * @return the real part value.
     */
    fun getReal(index: Int): Double = real[index]

    /**
     * Returns the imaginary part of the complex number at the given [index].
     *
     * @param index the zero-based index of the element.
     * @return the imaginary part value.
     */
    fun getImag(index: Int): Double = imag[index]

    /**
     * Returns the complex number at the given [index] as a [Pair] of (real, imaginary).
     *
     * For Java callers, prefer [getReal] and [getImag] instead.
     *
     * @param index the zero-based index of the element.
     * @return a pair of (real, imaginary) values.
     */
    fun get(index: Int): Pair<Double, Double> {
        return Pair(real[index], imag[index])
    }

    /**
     * Computes the magnitude (absolute value) of the complex number at the given [index].
     *
     * The magnitude is calculated as `sqrt(real² + imag²)`.
     *
     * @param index the zero-based index of the element.
     * @return the magnitude value.
     */
    fun getMagnitude(index: Int): Double {
        return sqrt(real[index] * real[index] + imag[index] * imag[index])
    }

    /**
     * Computes the phase angle (argument) of the complex number at the given [index].
     *
     * The phase is calculated as `atan2(imag, real)` and returned in radians.
     *
     * @param index the zero-based index of the element.
     * @return the phase angle in radians, in the range [-π, π].
     */
    fun getPhase(index: Int): Double {
        return atan2(imag[index], real[index])
    }

    companion object {
        /**
         * Allocates a new [ComplexBuffer] of the given size with all values initialized to zero.
         *
         * @param n the number of complex elements.
         * @return a new zero-initialized [ComplexBuffer].
         */
        @JvmStatic
        fun allocate(n: Int): ComplexBuffer {
            return ComplexBuffer(DoubleArray(n), DoubleArray(n))
        }
    }
}