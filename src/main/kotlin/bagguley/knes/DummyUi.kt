package bagguley.knes

import java.awt.FlowLayout
import java.awt.event.KeyEvent
import java.awt.event.KeyListener
import java.awt.image.BufferedImage
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.SourceDataLine
import javax.swing.ImageIcon
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.Timer
import kotlin.experimental.and

const val ROWS: Int = 240
const val COLS: Int = 256

fun main() {
    DummyUi()
}

class MyTimer(val interval: Int, val function: () -> Unit) {
    val timer = createTimer()

    fun createTimer(): Timer {
        val timeout = object: javax.swing.AbstractAction() {
            override fun actionPerformed(e: java.awt.event.ActionEvent) {
                function()
            }
        }

        val t = Timer(interval, timeout)
        t.isRepeats = true
        t.start()
        return t
    }

    fun stop() {
        timer.stop()
    }
}

class DummyUi : JFrame(), KeyListener {
    val image: BufferedImage = BufferedImage(256, 256, BufferedImage.TYPE_INT_RGB)
    val label = JLabel(ImageIcon(image))

    val sampleSizeInBits = 16
    val channels = 2
    val bigEndian = false
    val signed = true

    val format: AudioFormat
    val info: DataLine.Info
    val line: SourceDataLine

    val nes: Nes

    init {
        setSize(300, 300)
        defaultCloseOperation = EXIT_ON_CLOSE
        contentPane.layout = FlowLayout()
        contentPane.add(label)
        pack()
        isVisible = true

        addKeyListener(this)

        nes = Nes(this)
        format = AudioFormat(nes.opts.sampleRate.toFloat(), sampleSizeInBits, channels, signed, bigEndian)
        info = DataLine.Info(SourceDataLine::class.java, format, nes.opts.sampleRate)
        line = AudioSystem.getLine(info) as SourceDataLine
        line.open(format)
        line.start()

        //nes.loadRom(RomData("MarioBros.nes"))
        nes.loadRom(RomData.load("SuperMarioBros.nes"))
        nes.start()
    }

    fun updateStatus(arg: String) {
        println("STATUS: $arg")
    }

    fun alert(arg: String) {
        println("ALERT: $arg")
    }

    fun writeAudio(buf: IntArray) {
        val bytebuf = ByteArray(buf.size * 2)
        for (i in buf.indices) {
            val j = i * 2
            bytebuf[j] = (buf[i].toByte() and 0xff.toByte())
            bytebuf[j+1] = ((buf[i] shr 8).toByte() and 0xff.toByte())
        }
        line.write(bytebuf, 0, bytebuf.size)
    }

    fun writeFrame(buffer: IntArray, prevBuffer: IntArray) {
        for (y in 0 until ROWS) {
            for (x in 0 until COLS) {
                val pixel = buffer[y * COLS + x]

                if (pixel != prevBuffer[y * COLS + x]) {
                    image.setRGB(x, y, pixel)
                    prevBuffer[y * COLS + x]= pixel
                }
            }
        }
        label.repaint()
    }

    fun debug(message:String) {
        println("DEBUG: $message")
    }

    fun setInterval(function: () -> Unit, interval: Int): MyTimer {
        return MyTimer(interval, function)
    }

    fun clearInterval(timer: MyTimer) {
        timer.stop()
    }

    override fun keyPressed(event: KeyEvent) {
        nes.keyboard.keyDown(event)
    }

    override fun keyReleased(event: KeyEvent) {
        nes.keyboard.keyUp(event)
    }

    override fun keyTyped(event: KeyEvent) {
        // Nothing
    }
}