package ru.lavafrai.kfft

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class DiscreteFourierTransform: FourierTransform {
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