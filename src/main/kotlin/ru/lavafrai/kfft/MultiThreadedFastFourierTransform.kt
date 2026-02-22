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

/**
 * Multi-threaded Cooley–Tukey radix-2 Fast Fourier Transform.
 *
 * Uses a fixed thread pool to parallelize butterfly operations across FFT stages.
 * Stages with fewer than 4096 butterflies are executed on the calling thread to avoid
 * thread-dispatch overhead.
 *
 * **Input size must be a power of two**; an [IllegalArgumentException] is thrown otherwise.
 *
 * This class implements [Closeable] and owns a thread pool that should be shut down
 * when no longer needed. Usage example:
 *
 * ```kotlin
 * MultiThreadedFastFourierTransform(threads = 4).use { fft ->
 *     fft.forward(signal, spectrum)
 * }
 * ```
 *
 * In Java:
 * ```java
 * try (var fft = new MultiThreadedFastFourierTransform(4)) {
 *     fft.forward(signal, spectrum);
 * }
 * ```
 *
 * @param threads the number of worker threads in the pool.
 * @see FastFourierTransform for the single-threaded version.
 */
class MultiThreadedFastFourierTransform(
    private val threads: Int
) : FourierTransform, Closeable {
    private val executor: ExecutorService = Executors.newFixedThreadPool(threads) { runnable ->
        Thread(runnable).apply {
            isDaemon = true
            name = "FFT-Worker"
        }
    }

    /**
     * Computes the forward FFT of a real-valued signal using multiple threads.
     *
     * @param input the real-valued time-domain signal. Length must be a power of two.
     * @param output a [ComplexBuffer] of the same size as [input] to receive the spectrum.
     * @throws IllegalArgumentException if [input] size is not a power of two.
     */
    override fun forward(input: DoubleArray, output: ComplexBuffer) {
        val n = input.size
        require(isPowerOfTwo(n)) { "FastFourierTransform requires input size to be a power of two, but got $n" }

        for (i in 0 until n) {
            output.real[i] = input[i]
            output.imag[i] = 0.0
        }
        processInPlace(output.real, output.imag, invert = false)
    }

    /**
     * Computes the inverse FFT, reconstructing the real-valued signal from the full spectrum.
     *
     * @param input a [ComplexBuffer] of size N containing the spectrum. Size must be a power of two.
     * @param output a [DoubleArray] of length N to receive the reconstructed signal.
     * @throws IllegalArgumentException if [input] size is not a power of two.
     */
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

    /**
     * Shuts down the internal thread pool.
     *
     * Waits up to 1 second for running tasks to complete. If they do not finish in time,
     * the pool is forcibly terminated.
     */
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