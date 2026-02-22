package ru.lavafrai.kfft

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

data class Complex(val real: Double, val imag: Double) {
    operator fun plus(other: Complex) = Complex(
        this.real + other.real,
        this.imag + other.imag
    )

    operator fun times(other: Complex) = Complex(
        this.real * other.real - this.imag * other.imag,
        this.real * other.imag + this.imag * other.real
    )
}

class NaiveDiscreteFourierTransform : FourierTransform {
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