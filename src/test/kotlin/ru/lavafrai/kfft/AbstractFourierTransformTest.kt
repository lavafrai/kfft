package ru.lavafrai.kfft

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

abstract class AbstractFourierTransformTest {
    abstract fun createTransform(): FourierTransform

    @Test
    fun `Test small`() {
        val input = doubleArrayOf(1.0, 2.0, 3.0, 4.0)
        val transform = createTransform() // 3. Заменяем жесткий вызов на фабрику
        val output = ComplexBuffer.allocate(input.size)
        transform.forward(input, output)

        val inverseOutput = DoubleArray(input.size)
        transform.inverse(output, inverseOutput)

        for (i in input.indices) {
            assertEquals(input[i], inverseOutput[i], 1e-6)
        }
    }

    @Test
    fun `Test sine`() {
        val n = 4096
        val periods = 12
        val input = DoubleArray(n) { sin(2.0 * PI * periods * it / n) }
        val transform = createTransform()
        val output = ComplexBuffer.allocate(input.size)
        transform.forward(input, output)

        val magnitudes = DoubleArray(input.size / 2 + 1) { output.getMagnitude(it) }
        val maxIndex = magnitudes.indices.maxByOrNull { magnitudes[it] } ?: 0

        assertEquals(periods, maxIndex)

        val inverseOutput = DoubleArray(input.size)
        transform.inverse(output, inverseOutput)

        for (i in input.indices) {
            assertEquals(input[i], inverseOutput[i], 1e-6)
        }
    }

    @Test
    fun `Test zero input`() {
        val n = 1024
        val input = DoubleArray(n) { 0.0 }
        val transform = createTransform()
        val output = ComplexBuffer.allocate(input.size)
        transform.forward(input, output)

        for (i in 0..n / 2) {
            assertEquals(0.0, output.real[i], 1e-6)
            assertEquals(0.0, output.imag[i], 1e-6)
        }

        val inverseOutput = DoubleArray(input.size)
        transform.inverse(output, inverseOutput)

        for (i in input.indices) {
            assertEquals(0.0, inverseOutput[i], 1e-6)
        }
    }

    @Test
    fun `Test constant input`() {
        val n = 1024
        val value = 5.0
        val input = DoubleArray(n) { value }
        val transform = createTransform()
        val output = ComplexBuffer.allocate(input.size)
        transform.forward(input, output)

        assertEquals(value * n, output.real[0], 1e-6)
        assertEquals(0.0, output.imag[0], 1e-6)

        for (i in 1..n / 2) {
            assertEquals(0.0, output.real[i], 1e-6)
            assertEquals(0.0, output.imag[i], 1e-6)
        }

        val inverseOutput = DoubleArray(input.size)
        transform.inverse(output, inverseOutput)

        for (i in input.indices) {
            assertEquals(value, inverseOutput[i], 1e-6)
        }
    }

    @Test
    open fun `Test odd length input`() {
        val input = doubleArrayOf(1.0, 2.0, 3.0)
        val transform = createTransform()
        val output = ComplexBuffer.allocate(input.size)
        transform.forward(input, output)

        val inverseOutput = DoubleArray(input.size)
        transform.inverse(output, inverseOutput)

        for (i in input.indices) {
            assertEquals(input[i], inverseOutput[i], 1e-6)
        }
    }

    @Test
    fun `Test single element input`() {
        val input = doubleArrayOf(42.0)
        val transform = createTransform()
        val output = ComplexBuffer.allocate(input.size)
        transform.forward(input, output)

        assertEquals(42.0, output.real[0], 1e-6)
        assertEquals(0.0, output.imag[0], 1e-6)

        val inverseOutput = DoubleArray(input.size)
        transform.inverse(output, inverseOutput)

        assertEquals(42.0, inverseOutput[0], 1e-6)
    }

    @Test
    fun `Test Nyquist frequency`() {
        val n = 1024
        val input = DoubleArray(n) { if (it % 2 == 0) 1.0 else -1.0 }

        val transform = createTransform()
        val output = ComplexBuffer.allocate(input.size)
        transform.forward(input, output)

        val nyquistIndex = n / 2
        assertEquals(n.toDouble(), output.real[nyquistIndex], 1e-6)
        assertEquals(0.0, output.imag[nyquistIndex], 1e-6)

        val inverseOutput = DoubleArray(input.size)
        transform.inverse(output, inverseOutput)

        for (i in input.indices) {
            assertEquals(input[i], inverseOutput[i], 1e-6)
        }
    }

    @Test
    fun `Test Impulse`() {
        val n = 8
        val input = DoubleArray(n)
        input[0] = 1.0

        val transform = createTransform()
        val output = ComplexBuffer.allocate(input.size)
        transform.forward(input, output)

        for (i in 0..n / 2) {
            assertEquals(1.0, output.real[i], 1e-6)
            assertEquals(0.0, output.imag[i], 1e-6)
        }

        val inverseOutput = DoubleArray(input.size)
        transform.inverse(output, inverseOutput)

        assertEquals(1.0, inverseOutput[0], 1e-6)
        for (i in 1 until n) {
            assertEquals(0.0, inverseOutput[i], 1e-6)
        }
    }

    @Test
    fun `Test Amplitude`() {
        val n = 1024
        val k = 10
        val amplitude = 3.0
        val input = DoubleArray(n) { amplitude * cos(2.0 * PI * k * it / n) }

        val transform = createTransform()
        val output = ComplexBuffer.allocate(input.size)
        transform.forward(input, output)

        val expectedPeak = (amplitude * n) / 2.0
        assertEquals(expectedPeak, output.real[k], 1e-6)
        assertEquals(0.0, output.imag[k], 1e-6)

        val inverseOutput = DoubleArray(n)
        transform.inverse(output, inverseOutput)
        for(i in input.indices) {
            assertEquals(input[i], inverseOutput[i], 1e-6)
        }
    }

    @Test
    fun `Test Random signal`() {
        val n = 128
        val input = DoubleArray(n) { Random.nextDouble() * 100 - 50 }

        val transform = createTransform()
        val output = ComplexBuffer.allocate(input.size)
        transform.forward(input, output)

        val inverseOutput = DoubleArray(input.size)
        transform.inverse(output, inverseOutput)

        for (i in input.indices) {
            assertEquals(input[i], inverseOutput[i], 1e-9)
        }
    }

    @Test
    fun `Test Large input`() {
        val n = 32768
        val input = DoubleArray(n) { Random.nextDouble() * 100 - 50 }

        val transform = createTransform()
        val output = ComplexBuffer.allocate(input.size)
        transform.forward(input, output)

        val inverseOutput = DoubleArray(input.size)
        transform.inverse(output, inverseOutput)

        for (i in input.indices) {
            assertEquals(input[i], inverseOutput[i], 1e-9)
        }
    }
}