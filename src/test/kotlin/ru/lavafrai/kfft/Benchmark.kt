package ru.lavafrai.kfft

import org.knowm.xchart.SwingWrapper
import org.knowm.xchart.XYChart
import org.knowm.xchart.XYChartBuilder
import org.knowm.xchart.style.Styler
import kotlin.math.PI
import kotlin.math.sin
import kotlin.system.measureNanoTime

fun main() {
    // === КОНФИГУРАЦИЯ БЕНЧМАРКА ===
    // Диапазоны размеров (в виде степени двойки)
    val slowRange = 4..14  // от 16 до 16384 элементов
    val fastRange = 4..26  // от 16 до 1,048,576 элементов

    val slowSizes = slowRange.map { 1 shl it }
    val fastSizes = fastRange.map { 1 shl it }

    val naive = NaiveDiscreteFourierTransform()
    val dft = DiscreteFourierTransform()
    val fft = FastFourierTransform()
    val mtFft = MultiThreadedFastFourierTransform(Runtime.getRuntime().availableProcessors())

    println("Начинаем бенчмарк... Это займет несколько минут.")
    println("Медленные алгоритмы будут тестироваться до N = ${slowSizes.last()}.")
    println("Быстрые алгоритмы будут тестироваться до N = ${fastSizes.last()}.")

    val results = mutableMapOf<String, MutableMap<Int, Double>>()
    results["Naive DFT"] = mutableMapOf()
    results["Optimized DFT"] = mutableMapOf()
    results["FFT"] = mutableMapOf()
    results["MT-FFT"] = mutableMapOf()

    for (size in fastSizes) {
        println("Измерение N = $size...")

        if (size > 500_000) {
            val memRequired = size * 8 * 2 / 1024 / 1024
            println("  (Создается массив на ${memRequired}MB, может потребоваться -Xmx JVM опция)")
        }

        val input = DoubleArray(size) { sin(2.0 * PI * 10 * it / size) }

        val fastIterations = when {
            size > 10_000_000 -> 5
            size > 1_000_000 -> 10
            size > 250_000 -> 20
            size > 32_000 -> 50
            else -> 100
        }
        results["FFT"]!![size] = measureAlgorithm(fft, input, fastIterations)
        results["MT-FFT"]!![size] = measureAlgorithm(mtFft, input, fastIterations)

        if (size in slowSizes.toSet()) {
            val slowIterations = if (size > 4096) 2 else 10
            results["Naive DFT"]!![size] = measureAlgorithm(naive, input, slowIterations)
            results["Optimized DFT"]!![size] = measureAlgorithm(dft, input, slowIterations)
        }
    }

    mtFft.close()
    println("Вычисления завершены! Строим графики...")

    val xSlow = slowSizes.map { it.toDouble() }.toDoubleArray()
    val yNaive = slowSizes.map { results["Naive DFT"]!![it]!! }.toDoubleArray()
    val yDft = slowSizes.map { results["Optimized DFT"]!![it]!! }.toDoubleArray()

    val xFast = fastSizes.map { it.toDouble() }.toDoubleArray()
    val yFft = fastSizes.map { results["FFT"]!![it]!! }.toDoubleArray()
    val yMtFft = fastSizes.map { results["MT-FFT"]!![it]!! }.toDoubleArray()


    val charts = mutableListOf<XYChart>()

    charts.add(XYChartBuilder().width(800).height(600)
        .title("Медленные алгоритмы (O(N^2))")
        .xAxisTitle("Размер массива (N)").yAxisTitle("Время (миллисекунды)")
        .build().apply {
            styler.legendPosition = Styler.LegendPosition.InsideNW
            addSeries("Naive DFT", xSlow, yNaive)
            addSeries("Optimized DFT (Half-spectrum)", xSlow, yDft)
        })

    charts.add(XYChartBuilder().width(800).height(600)
        .title("Быстрые алгоритмы (O(N log N))")
        .xAxisTitle("Размер массива (N)").yAxisTitle("Время (миллисекунды)")
        .build().apply {
            styler.legendPosition = Styler.LegendPosition.InsideNW
            styler.isYAxisLogarithmic = true
            styler.isXAxisLogarithmic = true
            addSeries("FFT (1 thread)", xFast, yFft)
            addSeries("MT-FFT (${Runtime.getRuntime().availableProcessors()} threads)", xFast, yMtFft)
        })

    charts.add(XYChartBuilder().width(800).height(600)
        .title("Сравнение всех алгоритмов (Логарифмическая шкала Y)")
        .xAxisTitle("Размер массива (N)").yAxisTitle("Время (мс, Log10)")
        .build().apply {
            styler.legendPosition = Styler.LegendPosition.InsideNW
            styler.isYAxisLogarithmic = true
            styler.isXAxisLogarithmic = true

            addSeries("Naive DFT", xSlow, yNaive)
            addSeries("Optimized DFT", xSlow, yDft)
            addSeries("FFT", xFast, yFft)
            addSeries("MultiThreaded FFT", xFast, yMtFft)
        })

    SwingWrapper(charts).displayChartMatrix()
}


/**
 * Функция для "прогрева" (warm-up) JVM и точного замера времени.
 * Возвращает среднее время выполнения в МИЛЛИСЕКУНДАХ.
 */
fun measureAlgorithm(transform: FourierTransform, input: DoubleArray, iterations: Int): Double {
    val output = ComplexBuffer.allocate(input.size)

    val warmupIterations = minOf(3, iterations)
    for (i in 0 until warmupIterations) {
        transform.forward(input, output)
    }

    var totalTimeNs = 0L
    for (i in 0 until iterations) {
        totalTimeNs += measureNanoTime {
            transform.forward(input, output)
        }
    }

    return (totalTimeNs.toDouble() / iterations) / 1_000_000.0
}