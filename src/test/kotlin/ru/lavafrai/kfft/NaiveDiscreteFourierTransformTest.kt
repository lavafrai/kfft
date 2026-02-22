package ru.lavafrai.kfft

class NaiveDiscreteFourierTransformTest: AbstractFourierTransformTest() {
    override fun createTransform(): FourierTransform = NaiveDiscreteFourierTransform()
}