package ru.lavafrai.kfft

class DiscreteFourierTransformTest: AbstractFourierTransformTest() {
    override fun createTransform(): FourierTransform = DiscreteFourierTransform()
}