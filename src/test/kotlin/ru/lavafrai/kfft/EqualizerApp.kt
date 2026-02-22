package ru.lavafrai.kfft

import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.awt.geom.Path2D
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.prefs.Preferences
import javax.sound.sampled.*
import javax.swing.*
import javax.swing.border.EmptyBorder
import javax.swing.filechooser.FileNameExtensionFilter
import kotlin.math.*

/** Преобразует значение в дБ в линейный коэффициент усиления.
 *  Значения ≤ [negInfDb] считаются «тишиной» и возвращают 0.0. */
fun dbToLinear(db: Double, negInfDb: Double = -70.0): Double =
    if (db <= negInfDb) 0.0 else 10.0.pow(db / 20.0)

/** Преобразует линейный коэффициент усиления в значение в дБ. */
fun linearToDb(linear: Double): Double =
    20.0 * log10(linear.coerceAtLeast(1e-12))

fun main() {
    try {
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName())
    } catch (_: Exception) {}
    SwingUtilities.invokeLater {
        EqualizerApp()
    }
}

class EqualizerApp : JFrame("KFFT Equalizer") {

    companion object {
        /** Size of the FFT buffer (must be power of 2). Affects frequency resolution and latency. */
        const val FFT_SIZE = 4096
        /** Fixed sample rate used for all audio I/O and DSP math. */
        const val SAMPLE_RATE = 48000.0
    }

    private var channelSamples: Array<DoubleArray>? = null
    private var audioFormat: AudioFormat? = null

    @Volatile private var playbackRunning = false
    private var playbackThread: Thread? = null
    private var sourceLine: SourceDataLine? = null
    @Volatile private var currentSamplePos = 0

    @Volatile private var eqEnabled = true

    // EQ bands
    private val bandFrequencies = doubleArrayOf(32.0, 64.0, 125.0, 250.0, 500.0, 1000.0, 2000.0, 4000.0, 8000.0, 16000.0)
    val bandGains = DoubleArray(10) { 1.0 }
    val bandDb = DoubleArray(10) { 0.0 }

    @Volatile var volumeDb = 0.0
    @Volatile var volumeLinear = 1.0

    private val prefs = Preferences.userNodeForPackage(EqualizerApp::class.java)

    // UI
    private val spectrumPanel = SpectrumPanel()
    private val eqPanel = EqualizerPanel(this)
    private val btnOpen = JButton("  Открыть файл  ")
    private val btnPlay = JButton("  \u25B6 Играть  ")
    private val btnStop = JButton("  \u25A0 Стоп  ")
    private val chkEQ = JCheckBox("EQ", true)
    private val btnReset = JButton("Сброс")
    private val lblStatus = JLabel(" Загрузите аудиофайл (MP3 / WAV)")
    private val progressBar = JProgressBar(0, 1000).apply {
        isStringPainted = false
        preferredSize = Dimension(300, 14)
    }

    private val volumeSlider = JSlider(-70, 6, 0).apply {
        preferredSize = Dimension(130, 26)
        toolTipText = "Громкость (дБ)"
    }
    private val lblVolume = JLabel("Vol: 0.0 dB").apply { foreground = Color.LIGHT_GRAY }

    // Presets
    private val presets = mapOf(
        "Flat"       to doubleArrayOf(0.0, 0.0,  0.0,  0.0,  0.0,  0.0,  0.0,  0.0,  0.0,  0.0),
        "Bass Boost" to doubleArrayOf(16.0, 12.0,  8.0,  2.0,  0.0,  0.0,  0.0,  0.0,  0.0,  0.0),
        "Treble"     to doubleArrayOf(0.0, 0.0,  0.0,  0.0,  0.0,  2.0,  6.0,  10.0,  14.0,  16.0),
        "V-Shape"    to doubleArrayOf(14.0, 10.0,  4.0, -2.0, -6.0, -6.0, -2.0,  4.0,  10.0,  14.0),
        "Mid Scoop"  to doubleArrayOf(6.0, 2.0, -2.0, -6.0, -10.0, -10.0, -6.0, -2.0,  2.0,  6.0),
        "Vocal"      to doubleArrayOf(-4.0,-2.0, 0.0,  6.0,  10.0,  10.0,  6.0,  0.0, -2.0, -4.0),
    )

    @Volatile private var spectrumRunning = false

    init {
        defaultCloseOperation = EXIT_ON_CLOSE
        preferredSize = Dimension(1200, 750)
        layout = BorderLayout()

        setupUI()
        setupListeners()
        loadSettings()

        pack()
        setLocationRelativeTo(null)
        isVisible = true

        addWindowListener(object : WindowAdapter() {
            override fun windowClosing(e: WindowEvent?) {
                saveSettings()
                stopPlayback()
            }
        })
    }

    private fun setupUI() {
        val root = JPanel(BorderLayout(0, 0)).apply {
            background = Color(30, 30, 40)
        }

        // -- Top toolbar --
        val toolbar = JPanel(BorderLayout(8, 0)).apply {
            background = Color(40, 40, 55)
            border = EmptyBorder(2, 6, 2, 6)
        }

        val controlsLeft = JPanel(FlowLayout(FlowLayout.LEFT, 6, 4)).apply {
            isOpaque = false
            add(btnOpen)
            add(btnPlay)
            add(btnStop)
            add(Box.createHorizontalStrut(6))
            add(chkEQ)
            add(Box.createHorizontalStrut(6))

            val combo = JComboBox(presets.keys.toTypedArray()).apply {
                addActionListener {
                    val preset = presets[selectedItem as String] ?: return@addActionListener
                    applyPreset(preset)
                }
            }
            add(JLabel("Пресет:").apply { foreground = Color.LIGHT_GRAY })
            add(combo)
            add(Box.createHorizontalStrut(6))
            add(btnReset)
            add(Box.createHorizontalStrut(12))
            add(progressBar)
        }

        // Volume pinned to the right — never wraps
        val volumePanel = JPanel(FlowLayout(FlowLayout.RIGHT, 4, 4)).apply {
            isOpaque = false
            add(lblVolume)
            add(volumeSlider)
        }

        toolbar.add(controlsLeft, BorderLayout.CENTER)
        toolbar.add(volumePanel, BorderLayout.EAST)
        btnPlay.isEnabled = false
        btnStop.isEnabled = false

        // -- Center: spectrum on top, EQ below --
        val centerSplit = JSplitPane(JSplitPane.VERTICAL_SPLIT).apply {
            topComponent = spectrumPanel.apply { preferredSize = Dimension(1000, 280) }
            bottomComponent = eqPanel.apply { preferredSize = Dimension(1000, 280) }
            resizeWeight = 0.55
            border = null
            dividerSize = 4
        }

        // -- Status bar --
        val statusBar = JPanel(FlowLayout(FlowLayout.LEFT, 6, 2)).apply {
            background = Color(35, 35, 48)
            border = EmptyBorder(2, 8, 2, 8)
            add(lblStatus.apply { foreground = Color(180, 180, 200) })
        }

        root.add(toolbar, BorderLayout.NORTH)
        root.add(centerSplit, BorderLayout.CENTER)
        root.add(statusBar, BorderLayout.SOUTH)
        contentPane = root
    }

    private fun setupListeners() {
        btnOpen.addActionListener { openFile() }
        btnPlay.addActionListener { startPlayback() }
        btnStop.addActionListener { stopPlayback() }
        chkEQ.addActionListener { eqEnabled = chkEQ.isSelected }
        btnReset.addActionListener { applyPreset(DoubleArray(10) { 0.0 }) }
        volumeSlider.addChangeListener {
            volumeDb = volumeSlider.value.toDouble()
            volumeLinear = dbToLinear(volumeDb)
            val sign = if (volumeDb > 0) "+" else ""
            lblVolume.text = "Vol: $sign${"%.1f".format(volumeDb)} dB"
        }
    }

    fun applyPreset(dbValues: DoubleArray) {
        for (i in dbValues.indices) {
            bandDb[i] = dbValues[i].coerceIn(-70.0, 70.0)
            bandGains[i] = dbToLinear(bandDb[i])
        }
        eqPanel.repaint()
    }

    fun updateBandFromDb(index: Int, db: Double) {
        bandDb[index] = db.coerceIn(-70.0, 70.0)
        bandGains[index] = dbToLinear(bandDb[index])
    }

    private fun saveSettings() {
        // for (i in bandDb.indices) {
        //     prefs.putDouble("eq_band_$i", bandDb[i])
        // }
        prefs.putDouble("volume_db", volumeDb)
        // prefs.putBoolean("eq_enabled", eqEnabled)
    }

    private fun loadSettings() {
        // val dbValues = DoubleArray(10) { i -> prefs.getDouble("eq_band_$i", 0.0) }
        // applyPreset(dbValues)
        volumeDb = prefs.getDouble("volume_db", 0.0).coerceIn(-70.0, 6.0)
        volumeLinear = dbToLinear(volumeDb)
        volumeSlider.value = volumeDb.toInt()
        val sign = if (volumeDb > 0) "+" else ""
        lblVolume.text = "Vol: $sign${"%.1f".format(volumeDb)} dB"
        // eqEnabled = prefs.getBoolean("eq_enabled", true)
        // chkEQ.isSelected = eqEnabled
    }

    // ==================== FILE LOADING ====================

    private fun openFile() {
        val chooser = JFileChooser().apply {
            fileFilter = FileNameExtensionFilter("Аудиофайлы (MP3, WAV)", "mp3", "wav")
            dialogTitle = "Выберите аудиофайл"
        }
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return

        val file = chooser.selectedFile
        lblStatus.text = " Загрузка: ${file.name}..."
        stopPlayback()

        Thread {
            try {
                loadAudioFile(file)
                SwingUtilities.invokeLater {
                    btnPlay.isEnabled = true
                    title = "KFFT Equalizer - ${file.name}"
                    lblStatus.text = " ${file.name} | ${SAMPLE_RATE.toInt()} Hz, " +
                            "${audioFormat!!.sampleSizeInBits} бит, моно | " +
                            "Длительность: ${channelSamples!![0].size / SAMPLE_RATE.toInt()}с"
                }
            } catch (e: Exception) {
                e.printStackTrace()
                SwingUtilities.invokeLater {
                    lblStatus.text = " Ошибка: ${e.message}"
                    JOptionPane.showMessageDialog(this@EqualizerApp, e.message, "Ошибка", JOptionPane.ERROR_MESSAGE)
                }
            }
        }.start()
    }

    private fun loadAudioFile(file: File) {
        val rawStream = AudioSystem.getAudioInputStream(file)
        val base = rawStream.format

        // Step 1: decode to PCM 16-bit LE at the *native* sample rate
        val nativePcm = AudioFormat(
            AudioFormat.Encoding.PCM_SIGNED, base.sampleRate, 16,
            base.channels, base.channels * 2, base.sampleRate, false
        )

        val pcmStream = if (base.encoding == AudioFormat.Encoding.PCM_SIGNED
            && base.sampleSizeInBits == 16 && !base.isBigEndian
        ) rawStream else AudioSystem.getAudioInputStream(nativePcm, rawStream)

        val baos = ByteArrayOutputStream()
        val buf = ByteArray(16384)
        var n: Int
        while (pcmStream.read(buf).also { n = it } != -1) baos.write(buf, 0, n)
        pcmStream.close(); rawStream.close()

        val bytes = baos.toByteArray()
        val channels = nativePcm.channels
        val totalSamples = bytes.size / 2
        val perChannel = totalSamples / channels

        // Step 2: convert bytes → per-channel double arrays at native rate
        val nativeData = Array(channels) { DoubleArray(perChannel) }
        for (i in 0 until totalSamples) {
            val off = i * 2
            val s = ((bytes[off + 1].toInt() shl 8) or (bytes[off].toInt() and 0xFF)).toShort().toInt()
            nativeData[i % channels][i / channels] = s / 32768.0
        }

        // Step 3: resample to SAMPLE_RATE if the native rate differs
        val nativeRate = nativePcm.sampleRate.toDouble()
        val chData = if (abs(nativeRate - SAMPLE_RATE) < 1.0) {
            nativeData
        } else {
            val ratio = nativeRate / SAMPLE_RATE
            val newLen = (perChannel / ratio).toInt()
            Array(channels) { ch ->
                DoubleArray(newLen) { i ->
                    val srcPos = i * ratio
                    val idx = srcPos.toInt()
                    val frac = srcPos - idx
                    if (idx + 1 < perChannel) {
                        nativeData[ch][idx] * (1.0 - frac) + nativeData[ch][idx + 1] * frac
                    } else {
                        nativeData[ch][idx.coerceAtMost(perChannel - 1)]
                    }
                }
            }
        }

        // Step 4: mix down to mono
        val monoLen = chData[0].size
        val monoData = if (chData.size == 1) {
            chData[0]
        } else {
            DoubleArray(monoLen) { i ->
                var sum = 0.0
                for (ch in chData) sum += ch[i]
                sum / chData.size
            }
        }

        audioFormat = AudioFormat(
            AudioFormat.Encoding.PCM_SIGNED, SAMPLE_RATE.toFloat(), 16,
            1, 2, SAMPLE_RATE.toFloat(), false
        )
        channelSamples = arrayOf(monoData)
    }

    // ==================== REALTIME PLAYBACK ====================

    private fun startPlayback() {
        val ch = channelSamples ?: return
        val fmt = audioFormat ?: return
        stopPlayback()

        playbackRunning = true
        currentSamplePos = 0
        btnPlay.isEnabled = false
        btnStop.isEnabled = true
        lblStatus.text = " \u25B6 Воспроизведение - эквалайзер применяется в реальном времени"

        playbackThread = Thread {
            try {
                realtimeLoop(ch, fmt)
            } catch (e: Exception) {
                if (playbackRunning) e.printStackTrace()
            } finally {
                playbackRunning = false
                spectrumRunning = false
                SwingUtilities.invokeLater {
                    btnPlay.isEnabled = channelSamples != null
                    btnStop.isEnabled = false
                    progressBar.value = 0
                    lblStatus.text = " \u25A0 Остановлено"
                }
            }
        }.apply { isDaemon = true; name = "playback"; start() }

        startSpectrumViz(ch)
    }

    private fun realtimeLoop(chSamples: Array<DoubleArray>, fmt: AudioFormat) {
        val mono = chSamples[0]
        val total = mono.size
        val processor = AudioProcessor(FFT_SIZE, SAMPLE_RATE, bandFrequencies)

        val lineBufferBytes = FFT_SIZE * 16
        val line = AudioSystem.getSourceDataLine(fmt)
        line.open(fmt, lineBufferBytes)
        line.start()
        sourceLine = line

        val hopBytes = ByteArray(FFT_SIZE * 2)
        val hopBuf = DoubleArray(FFT_SIZE)
        var pos = 0

        while (playbackRunning && pos + FFT_SIZE <= total) {
            processor.setBands(bandDb, eqEnabled)

            System.arraycopy(mono, pos, hopBuf, 0, FFT_SIZE)
            val out = processor.processPackage(hopBuf)

            val vol = volumeLinear
            for (i in 0 until FFT_SIZE) {
                val s = (out[i] * vol).coerceIn(-1.0, 1.0)
                val sInt = (s * 32767.0).toInt()
                hopBytes[i * 2] = sInt.toByte()
                hopBytes[i * 2 + 1] = (sInt shr 8).toByte()
            }

            line.write(hopBytes, 0, hopBytes.size)

            currentSamplePos = pos
            if (pos % (FFT_SIZE * 10) == 0) {
                val p = pos
                SwingUtilities.invokeLater {
                    progressBar.value = (p * 1000L / total).toInt()
                }
            }
            pos += FFT_SIZE
        }

        line.drain(); line.stop(); line.close()
        sourceLine = null
        playbackRunning = false
    }

    private fun stopPlayback() {
        playbackRunning = false
        spectrumRunning = false
        sourceLine?.let {
            try { it.stop() } catch (_: Exception) {}
            try { it.close() } catch (_: Exception) {}
        }
        sourceLine = null
        playbackThread?.interrupt()
        playbackThread = null
        btnStop.isEnabled = false
    }

    // ==================== SPECTRUM VIZ ====================

    private fun startSpectrumViz(chSamples: Array<DoubleArray>) {
        spectrumRunning = true
        Thread {
            val mono = chSamples[0]
            val vizFft = FastFourierTransform()
            val vizBuf = DoubleArray(FFT_SIZE)
            val vizSpec = ComplexBuffer.allocate(FFT_SIZE)
            val vizWindow = DoubleArray(FFT_SIZE) { i ->
                0.5 * (1.0 - cos(2.0 * PI * i / FFT_SIZE))
            }

            while (spectrumRunning && playbackRunning) {
                val p = currentSamplePos
                if (p + FFT_SIZE <= mono.size) {
                    for (i in 0 until FFT_SIZE) vizBuf[i] = mono[p + i] * vizWindow[i]
                    vizFft.forward(vizBuf, vizSpec)
                    val mags = DoubleArray(FFT_SIZE / 2) { vizSpec.getMagnitude(it) }
                    SwingUtilities.invokeLater { spectrumPanel.updateSpectrum(mags) }
                }
                Thread.sleep(30)
            }
            SwingUtilities.invokeLater { spectrumPanel.clearSpectrum() }
        }.apply { isDaemon = true; name = "viz"; start() }
    }

}


// =================================================================
//  Custom drawn Equalizer Panel - interactive band sliders drawn as
//  colored bars with a smooth EQ curve overlay, draggable with mouse
// =================================================================

class EqualizerPanel(private val app: EqualizerApp) : JPanel() {
    private val numBands = app.bandDb.size
    private val minDb = -70.0
    private val maxDb = 70.0

    private var dragBand = -1

    private val bandLabels = arrayOf("32", "64", "125", "250", "500", "1K", "2K", "4K", "8K", "16K")

    // Colors
    private val bgColor = Color(25, 25, 38)
    private val gridColor = Color(50, 50, 70)
    private val zeroLineColor = Color(80, 80, 110)
    private val textColor = Color(160, 160, 190)
    private val barColorPositive = Color(0, 180, 120)
    private val barColorNegative = Color(220, 70, 70)
    private val curveColor = Color(100, 200, 255, 200)
    private val knobColor = Color(255, 255, 255, 230)
    private val highlightColor = Color(255, 255, 255, 40)

    init {
        val mouse = object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                dragBand = bandAtX(e.x)
                if (dragBand >= 0) updateGain(dragBand, e.y)
            }
            override fun mouseDragged(e: MouseEvent) {
                if (dragBand >= 0) updateGain(dragBand, e.y)
            }
            override fun mouseReleased(e: MouseEvent) { dragBand = -1 }
        }
        addMouseListener(mouse)
        addMouseMotionListener(mouse)
        toolTipText = ""
    }

    override fun getToolTipText(event: MouseEvent): String {
        val b = bandAtX(event.x)
        return if (b >= 0) "${bandLabels[b]} Hz: ${"%.1f".format(app.bandDb[b])} dB" else ""
    }

    private fun bandAtX(x: Int): Int {
        val r = insetRect()
        val bw = r.width.toDouble() / numBands
        val idx = ((x - r.x) / bw).toInt()
        return if (idx in 0 until numBands) idx else -1
    }

    private fun updateGain(band: Int, mouseY: Int) {
        val r = insetRect()
        val pxPerDb = r.height / (maxDb - minDb)
        val zeroY = r.y + maxDb * pxPerDb
        val db = (zeroY - mouseY) / pxPerDb
        app.updateBandFromDb(band, db)
        repaint()
    }

    private fun insetRect(): Rectangle {
        val left = 45; val right = 15; val top = 20; val bottom = 35
        return Rectangle(left, top, width - left - right, height - top - bottom)
    }

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val g2 = g as Graphics2D
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

        val w = width; val h = height
        g2.color = bgColor; g2.fillRect(0, 0, w, h)

        val r = insetRect()
        // Zero line position: proportional within the asymmetric range
        // maxDb is at the top (r.y), minDb is at the bottom (r.y + r.height)
        val pxPerDb = r.height.toDouble() / (maxDb - minDb)
        val zeroY = (r.y + maxDb * pxPerDb).toInt()
        val bandW = r.width.toDouble() / numBands

        // -- Grid lines --
        g2.stroke = BasicStroke(1f)
        for (db in -70..70 step 10) {
            val y = (zeroY - db * pxPerDb).toInt()
            if (y < r.y || y > r.y + r.height) continue
            if (db == 0) {
                g2.color = zeroLineColor
                g2.stroke = BasicStroke(1.5f)
                g2.drawLine(r.x, y, r.x + r.width, y)
                g2.stroke = BasicStroke(1f)
            } else {
                g2.color = gridColor
                g2.drawLine(r.x, y, r.x + r.width, y)
            }
        }
        // Vertical separators
        g2.color = gridColor
        for (i in 0..numBands) {
            val x = (r.x + i * bandW).toInt()
            g2.drawLine(x, r.y, x, r.y + r.height)
        }

        // -- dB labels on left --
        g2.font = Font("SansSerif", Font.PLAIN, 10)
        val fm = g2.fontMetrics
        for (db in listOf(70, 60, 50, 40, 30, 20, 10, 0, -10, -20, -30, -40, -50, -60, -70)) {
            val y = (zeroY - db * pxPerDb).toInt()
            if (y < r.y - 4 || y > r.y + r.height + 4) continue
            val label = if (db > 0) "+$db" else "$db"
            g2.color = if (db == 0) zeroLineColor else textColor
            g2.drawString(label, r.x - fm.stringWidth(label) - 4, y + fm.ascent / 2)
        }

        // -- Bars --
        val barPad = 4
        for (i in 0 until numBands) {
            val db = app.bandDb[i]
            val barH = (db * pxPerDb).toInt()
            val bx = (r.x + i * bandW + barPad).toInt()
            val bw = (bandW - barPad * 2).toInt().coerceAtLeast(4)

            if (barH > 0) {
                val grad = GradientPaint(
                    bx.toFloat(), zeroY.toFloat(), barColorPositive.darker(),
                    bx.toFloat(), (zeroY - barH).toFloat(), barColorPositive.brighter()
                )
                g2.paint = grad
                g2.fillRoundRect(bx, zeroY - barH, bw, barH, 4, 4)
            } else if (barH < 0) {
                val grad = GradientPaint(
                    bx.toFloat(), zeroY.toFloat(), barColorNegative.darker(),
                    bx.toFloat(), (zeroY - barH).toFloat(), barColorNegative
                )
                g2.paint = grad
                g2.fillRoundRect(bx, zeroY, bw, -barH, 4, 4)
            }

            // -- Knob / handle --
            val knobY = (zeroY - db * pxPerDb).toInt()
            val knobW = bw + 6
            val knobH = 8
            val knobX = bx - 3

            if (dragBand == i) {
                g2.color = highlightColor
                g2.fillRoundRect(bx - 2, r.y, bw + 4, r.height, 6, 6)
            }

            g2.color = knobColor
            g2.fillRoundRect(knobX, knobY - knobH / 2, knobW, knobH, 4, 4)
            g2.color = Color(0, 0, 0, 80)
            g2.drawRoundRect(knobX, knobY - knobH / 2, knobW, knobH, 4, 4)

            // -- Band label --
            g2.color = textColor
            g2.font = Font("SansSerif", Font.BOLD, 11)
            val lbl = bandLabels[i]
            val lblW = g2.fontMetrics.stringWidth(lbl)
            g2.drawString(lbl, (bx + bw / 2 - lblW / 2), r.y + r.height + 15)

            // -- dB value above/below knob --
            g2.font = Font("SansSerif", Font.PLAIN, 9)
            val valStr = if (db >= 0) "+${"%.1f".format(db)}" else "${"%.1f".format(db)}"
            val valW = g2.fontMetrics.stringWidth(valStr)
            val valY = if (db >= 0) knobY - knobH / 2 - 3 else knobY + knobH / 2 + 11
            g2.color = if (db > 0.01) barColorPositive else if (db < -0.01) barColorNegative else textColor
            g2.drawString(valStr, (bx + bw / 2 - valW / 2), valY)
        }

        // -- Smooth EQ curve overlay --
        g2.color = curveColor
        g2.stroke = BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        val path = Path2D.Double()
        for (i in 0 until numBands) {
            val cx = r.x + (i + 0.5) * bandW
            val cy = zeroY - app.bandDb[i] * pxPerDb
            if (i == 0) path.moveTo(cx, cy) else path.lineTo(cx, cy)
        }
        g2.draw(path)

        // Dots on curve
        g2.color = Color.WHITE
        for (i in 0 until numBands) {
            val cx = (r.x + (i + 0.5) * bandW).toInt()
            val cy = (zeroY - app.bandDb[i] * pxPerDb).toInt()
            g2.fillOval(cx - 3, cy - 3, 6, 6)
        }

        // -- Title --
        g2.color = Color(120, 120, 150)
        g2.font = Font("SansSerif", Font.PLAIN, 11)
        g2.drawString("Эквалайзер", r.x, r.y - 5)
    }
}


// =================================================================
//  Spectrum visualization panel - colored frequency bars with decay
// =================================================================

class SpectrumPanel: JPanel() {
    private var magnitudes: DoubleArray? = null
    private val numBars = 80

    // Smoothing: keep previous values for gentle decay
    private var smoothed = DoubleArray(numBars)
    private val decay = 0.7

    // dB range for display: floor to ceiling (dBFS)
    private val dbFloor = -90.0
    private val dbCeiling = 0.0
    private val dbRange = dbCeiling - dbFloor

    fun updateSpectrum(mags: DoubleArray) {
        magnitudes = mags; repaint()
    }
    fun clearSpectrum() { magnitudes = null; smoothed = DoubleArray(numBars); repaint() }

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val g2 = g as Graphics2D
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

        val w = width; val h = height
        g2.color = Color(18, 18, 28); g2.fillRect(0, 0, w, h)

        val mags = magnitudes
        if (mags == null || mags.isEmpty()) {
            g2.color = Color(80, 80, 100)
            g2.font = Font("SansSerif", Font.PLAIN, 13)
            val txt = "Спектр - нажмите \u25B6 для воспроизведения"
            g2.drawString(txt, (w - g2.fontMetrics.stringWidth(txt)) / 2, h / 2)
            return
        }

        // Normalization factor: FFT magnitude for a full-scale sine = fftSize/2
        val fftNorm = EqualizerApp.FFT_SIZE / 2.0

        val maxBin = mags.size
        val margin = 12
        val bottomMargin = 16
        val topMargin = 4
        val barW = (w - margin * 2).toDouble() / numBars
        val drawH = h - bottomMargin - topMargin

        for (b in 0 until numBars) {
            val lo = (maxBin * (b.toDouble() / numBars).pow(2.0)).toInt().coerceIn(0, maxBin - 1)
            val hi = (maxBin * ((b + 1.0) / numBars).pow(2.0)).toInt().coerceIn(lo + 1, maxBin)
            var sum = 0.0; var cnt = 0
            for (i in lo until hi) { sum += mags[i]; cnt++ }
            val avg = if (cnt > 0) sum / cnt else 0.0

            // Normalize to dBFS: 0 dBFS = full-scale sine
            val dbFS = linearToDb(avg / fftNorm)
            // Map dbFloor..dbCeiling -> 0..1, hard-clamp to panel bounds
            val norm = ((dbFS - dbFloor) / dbRange).coerceIn(0.0, 1.0)

            // Smooth
            smoothed[b] = max(norm, smoothed[b] * decay)
            val v = smoothed[b]

            val barH = (v * drawH).toInt().coerceAtLeast(0)
            val x = (margin + b * barW).toInt()
            val y = (h - bottomMargin - barH).coerceAtLeast(topMargin)

            val color = when {
                v < 0.4 -> Color((v / 0.4 * 80).toInt().coerceIn(0, 255), (v / 0.4 * 220).toInt().coerceIn(0, 255), 140)
                v < 0.75 -> Color(((v - 0.4) / 0.35 * 255).toInt().coerceIn(0, 255), 220, ((1.0 - (v - 0.4) / 0.35) * 140).toInt().coerceIn(0, 60))
                else -> Color(255, ((1.0 - (v - 0.75) / 0.25) * 220).toInt().coerceIn(0, 255), 30)
            }

            val grad = GradientPaint(x.toFloat(), (h - bottomMargin).toFloat(), color.darker(), x.toFloat(), y.toFloat(), color)
            g2.paint = grad
            g2.fillRoundRect(x, y, max(barW.toInt() - 1, 2), barH, 2, 2)

            // Peak cap
            g2.color = Color(255, 255, 255, 160)
            g2.fillRect(x, y, max(barW.toInt() - 1, 2), 2)
        }

        // Frequency labels
        g2.color = Color(100, 100, 130); g2.font = Font("SansSerif", Font.PLAIN, 9)
        val maxFreq = EqualizerApp.SAMPLE_RATE / 2.0
        for ((lbl, f) in listOf("100" to 100.0, "500" to 500.0, "1k" to 1000.0, "5k" to 5000.0, "10k" to 10000.0, "20k" to 20000.0)) {
            if (f > maxFreq) break
            val nx = sqrt(f / maxFreq)
            g2.drawString(lbl, (margin + nx * (w - margin * 2)).toInt(), h - 3)
        }
    }
}
