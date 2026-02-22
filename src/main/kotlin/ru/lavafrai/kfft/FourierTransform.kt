package ru.lavafrai.kfft

interface FourierTransform {
    fun forward(input: DoubleArray, output: ComplexBuffer)
    fun inverse(input: ComplexBuffer, output: DoubleArray)
}