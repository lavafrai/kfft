package ru.lavafrai.kfft

import java.io.Closeable
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

class MultiThreadedFastFourierTransform(
    private val threads: Int
) : FourierTransform, Closeable {
    private val executor: ExecutorService = Executors.newFixedThreadPool(threads) { runnable ->
        Thread(runnable).apply {
            isDaemon = true
            name = "FFT-Worker"
        }
    }

    override fun forward(input: DoubleArray, output: ComplexBuffer) {
        val n = input.size
        require(isPowerOfTwo(n)) { "FastFourierTransform requires input size to be a power of two, but got $n" }

        for (i in 0 until n) {
            output.real[i] = input[i]
            output.imag[i] = 0.0
        }
        processInPlace(output.real, output.imag, invert = false)
    }

    override fun inverse(input: ComplexBuffer, output: DoubleArray) {
        val n = input.size
        require(isPowerOfTwo(n)) { "FastFourierTransform requires input size to be a power of two, but got $n" }

        val tempReal = input.real.copyOf()
        val tempImag = input.imag.copyOf()

        processInPlace(tempReal, tempImag, invert = true)

        for (i in 0 until n) {
            output[i] = tempReal[i] / n
        }
    }

    private fun isPowerOfTwo(n: Int): Boolean = n > 0 && (n and (n - 1)) == 0

    private fun processInPlace(real: DoubleArray, imag: DoubleArray, invert: Boolean) {
        val n = real.size

        var j = 0
        for (i in 0 until n - 1) {
            if (i < j) {
                val tempR = real[i]; real[i] = real[j]; real[j] = tempR
                val tempI = imag[i]; imag[i] = imag[j]; imag[j] = tempI
            }
            var m = n shr 1
            while (m <= j) {
                j -= m
                m = m shr 1
            }
            j += m
        }

        val angleSign = if (invert) 1.0 else -1.0
        var len = 2

        while (len <= n) {
            val halfLen = len shr 1
            val angleStep = 2.0 * PI / len * angleSign

            val totalButterflies = n / 2

            if (totalButterflies < 4096 || threads == 1) {
                processStageChunk(real, imag, len, halfLen, angleStep, 0, totalButterflies)
            } else {
                val butterfliesPerThread = totalButterflies / threads
                val tasks = ArrayList<Callable<Unit>>(threads)

                for (t in 0 until threads) {
                    val startIdx = t * butterfliesPerThread
                    val endIdx = if (t == threads - 1) totalButterflies else (t + 1) * butterfliesPerThread

                    tasks.add(Callable {
                        processStageChunk(real, imag, len, halfLen, angleStep, startIdx, endIdx)
                    })
                }

                val futures = executor.invokeAll(tasks)
                for (f in futures) f.get()
            }
            len = len shl 1
        }
    }

    private fun processStageChunk(
        real: DoubleArray, imag: DoubleArray,
        len: Int, halfLen: Int, angleStep: Double,
        startIdx: Int, endIdx: Int
    ) {
        var currentIdx = startIdx

        while (currentIdx < endIdx) {
            val block = currentIdx / halfLen
            val kStart = currentIdx % halfLen
            val i = block * len

            val kEnd = min(halfLen, kStart + (endIdx - currentIdx))

            val initialAngle = angleStep * kStart
            var wReal = cos(initialAngle)
            var wImag = sin(initialAngle)

            val wStepReal = cos(angleStep)
            val wStepImag = sin(angleStep)

            for (k in kStart until kEnd) {
                val u = i + k
                val v = u + halfLen

                val tr = wReal * real[v] - wImag * imag[v]
                val ti = wReal * imag[v] + wImag * real[v]

                real[v] = real[u] - tr
                imag[v] = imag[u] - ti
                real[u] += tr
                imag[u] += ti

                val nextWReal = wReal * wStepReal - wImag * wStepImag
                val nextWImag = wReal * wStepImag + wImag * wStepReal
                wReal = nextWReal
                wImag = nextWImag
            }

            currentIdx += (kEnd - kStart)
        }
    }

    override fun close() {
        executor.shutdown()
        try {
            if (!executor.awaitTermination(1, TimeUnit.SECONDS)) {
                executor.shutdownNow()
            }
        } catch (e: InterruptedException) {
            executor.shutdownNow()
            Thread.currentThread().interrupt()
        }
    }
}