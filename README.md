# kfft

A pure-Kotlin Fast Fourier Transform library for the JVM. Zero dependencies, Java-friendly API.
The library was designed to be used as a dependency in one of my projects.

## Features

| Implementation | Complexity | Input size | Threads |
|---|---|---|---|
| `NaiveDiscreteFourierTransform` | O(n²) | Any | 1 |
| `DiscreteFourierTransform` | O(n²) | Any | 1 |
| `FastFourierTransform` | O(n log n) | Power of 2 | 1 |
| `MultiThreadedFastFourierTransform` | O(n log n) | Power of 2 | Configurable |

- **`NaiveDiscreteFourierTransform`** — straightforward DFT, computes all N bins. Good as a reference.
- **`DiscreteFourierTransform`** — optimized DFT exploiting Hermitian symmetry (only N/2+1 bins stored).
- **`FastFourierTransform`** — Cooley-Tukey radix-2 iterative FFT.
- **`MultiThreadedFastFourierTransform`** — parallel radix-2 FFT with a configurable thread pool. Implements `Closeable`.

## Installation

### Gradle (Kotlin DSL)

```kotlin
repositories {
    maven {
        url = uri("https://reposilite.artembay.ru/releases")
    }
}

dependencies {
    implementation("ru.lavafrai:kfft:1.0.1")
}
```


## Usage

### Kotlin

#### Basic FFT (forward + inverse)

```kotlin
import ru.lavafrai.kfft.ComplexBuffer
import ru.lavafrai.kfft.FastFourierTransform

fun main() {
    val signal = DoubleArray(1024) { Math.sin(2.0 * Math.PI * 50.0 * it / 1024) }

    val fft = FastFourierTransform()
    val spectrum = ComplexBuffer.allocate(signal.size)

    // Time domain → Frequency domain
    fft.forward(signal, spectrum)

    // Read magnitudes
    for (k in 0..signal.size / 2) {
        val magnitude = spectrum.getMagnitude(k)
        val phase = spectrum.getPhase(k)
        // ...
    }

    // Frequency domain → Time domain
    val restored = DoubleArray(signal.size)
    fft.inverse(spectrum, restored)
}
```

## Examples 
Project contains two simple examples demonstrating the usage of the library:
- Benchmark comparing the performance of different implementations.
- A simple audio player with equalizer using the FFT for real-time frequency analysis.
To try them out just run one of this command in the project root:
```bash
./gradlew :runBenchmark
./gradlew :runEqualizer
```

## Generative AI usage warning
When developing this project generative AI tools were used in the next parts:
- Generation of some tests for the DFT implementations.
- Generation of readme you read ahead.
- Generation of examples code (Exclude AudioProcessor.kt class).

I don't know, maybe it's fundamentally important for you to know this, I'm not hiding it :3
