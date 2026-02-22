package ru.lavafrai.kfft

import kotlin.math.*


class AudioProcessor(
    private val frameSize: Int,
    private val sampleRate: Double,
    private val bandFrequencies: DoubleArray,
) {
    private val halfFrameSize = frameSize / 2
    private val prevInput = DoubleArray(frameSize)
    private val fftInBuf = DoubleArray(frameSize)
    private val spectrumBuf = ComplexBuffer.allocate(frameSize)
    private val fftOutBuf = DoubleArray(frameSize)
    private val outputAccumulator = DoubleArray(frameSize + halfFrameSize)

    private val fft = FastFourierTransform()

    private val bandCount = bandFrequencies.size
    private val eqBandDb = DoubleArray(bandCount)
    private val eqBandGains = DoubleArray(bandCount) { 1.0 }
    @Volatile private var eqEnabled = true

    private val window = DoubleArray(frameSize) { i ->
        0.5 * (1.0 - cos(2.0 * PI * i / frameSize))
    }


    fun setBands(dbValues: DoubleArray, enabled: Boolean = true) {
        eqEnabled = enabled
        for (i in dbValues.indices) {
            eqBandDb[i] = dbValues[i]
            eqBandGains[i] = dbToLinear(dbValues[i])
        }
    }

    fun processPackage(samples: DoubleArray): DoubleArray {
        require(samples.size == frameSize) {
            "Expected $frameSize samples, got ${samples.size}"
        }

        if (!eqEnabled) {
            System.arraycopy(samples, 0, prevInput, 0, frameSize)
            return samples.copyOf()
        }

        System.arraycopy(outputAccumulator, frameSize, outputAccumulator, 0, halfFrameSize)
        for (i in halfFrameSize until outputAccumulator.size) outputAccumulator[i] = 0.0

        // First pass
        System.arraycopy(prevInput, halfFrameSize, fftInBuf, 0, halfFrameSize)
        System.arraycopy(samples, 0, fftInBuf, halfFrameSize, halfFrameSize)
        processSingleWindowAndAccumulate(offset = 0)

        // Second pass
        System.arraycopy(samples, 0, fftInBuf, 0, frameSize)
        processSingleWindowAndAccumulate(offset = halfFrameSize)

        System.arraycopy(samples, 0, prevInput, 0, frameSize)

        val result = DoubleArray(frameSize)
        System.arraycopy(outputAccumulator, 0, result, 0, frameSize)
        return result
    }

    private fun processSingleWindowAndAccumulate(offset: Int) {
        for (i in 0 until frameSize) {
            fftInBuf[i] *= window[i]
        }

        fft.forward(fftInBuf, spectrumBuf)

        val gainCurve = buildGainCurve()
        for (i in 0 until frameSize) {
            spectrumBuf.real[i] *= gainCurve[i]
            spectrumBuf.imag[i] *= gainCurve[i]
        }

        fft.inverse(spectrumBuf, fftOutBuf)

        for (i in 0 until frameSize) {
            outputAccumulator[offset + i] += fftOutBuf[i]
        }
    }


    private fun buildGainCurve(): DoubleArray {
        val curve = DoubleArray(frameSize)
        val step = sampleRate / frameSize
        for (bin in 0 until frameSize) {
            val freq = if (bin <= frameSize / 2) bin * step else (frameSize - bin) * step
            curve[bin] = interpolateGain(freq)
        }
        return curve
    }

    private fun interpolateGain(freq: Double): Double {
        if (freq <= 0.0) return eqBandGains[0]
        if (freq <= bandFrequencies[0]) return eqBandGains[0]
        if (freq >= bandFrequencies.last()) return eqBandGains.last()
        val lf = ln(freq)
        for (i in 0 until bandFrequencies.size - 1) {
            val l0 = ln(bandFrequencies[i]); val l1 = ln(bandFrequencies[i + 1])
            if (lf in l0..l1) {
                val t = (lf - l0) / (l1 - l0)
                val dbInterp = eqBandDb[i] * (1.0 - t) + eqBandDb[i + 1] * t
                return dbToLinear(dbInterp)
            }
        }
        return 1.0
    }
}
