package ru.lavafrai.kfft

import kotlin.test.Test
import kotlin.test.assertFailsWith

class FastFourierTransformTest: AbstractFourierTransformTest() {
    override fun createTransform(): FourierTransform = FastFourierTransform()

    @Test
    override fun `Test odd length input`() {
        val input = doubleArrayOf(1.0, 2.0, 3.0)
        val transform = createTransform()
        val output = ComplexBuffer.allocate(input.size)

        assertFailsWith<IllegalArgumentException> {
            transform.forward(input, output)
        }

        val badComplexBuffer = ComplexBuffer.allocate(3)
        val badInverseOutput = DoubleArray(3)
        assertFailsWith<IllegalArgumentException> {
            transform.inverse(badComplexBuffer, badInverseOutput)
        }
    }
}