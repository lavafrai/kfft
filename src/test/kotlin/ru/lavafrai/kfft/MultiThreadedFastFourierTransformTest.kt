package ru.lavafrai.kfft

import kotlin.test.Test

class MultiThreadedFastFourierTransformTest : AbstractFourierTransformTest() {
    private var transformInstance: MultiThreadedFastFourierTransform? = null

    override fun createTransform(): FourierTransform {
        val threads = Runtime.getRuntime().availableProcessors()
        println("Using $threads threads for MultiThreadedFastFourierTransform tests")

        transformInstance = MultiThreadedFastFourierTransform(threads)
        return transformInstance!!
    }

    @Test
    override fun `Test odd length input`() {
        val input = doubleArrayOf(1.0, 2.0, 3.0)
        val transform = createTransform()
        val output = ComplexBuffer.allocate(input.size)

        kotlin.test.assertFailsWith<IllegalArgumentException> {
            transform.forward(input, output)
        }
    }
}