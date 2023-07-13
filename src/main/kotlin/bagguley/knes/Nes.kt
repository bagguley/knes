package bagguley.knes

import bagguley.knes.papu.Papu
import bagguley.knes.ppu.Ppu
import java.util.*

class Nes(val ui: DummyUi, var preferredFrameRate: Int = 60, val showDisplay: Boolean = true) {
    val opts = NesOpts()
    var isRunning: Boolean = false
    var fpsFrameCount: Int = 0
    var romData: Optional<RomData> = Optional.empty() // TODO
    var lastFpsTime: Optional<Date> = Optional.empty()
    var rom: Optional<Rom> = Optional.empty()
    var mmap: Optional<Mapper> = Optional.empty() // TODO
    var frameTime = 1000 / preferredFrameRate
    var crashMessage: String = ""
    lateinit var lastFrameTime: Date

    lateinit var frameInterval: MyTimer
    lateinit var fpsInterval: MyTimer

    val cpu = Cpu(this)
    val ppu = Ppu(this)
    val papu = Papu(this)
    val keyboard = Keyboard()

    val mappers: Array<(() -> Mapper)?> = Array(100) { null }

    init {
        mappers[0] = { DirectAccessMapper(this) }
        mappers[1] = { NintendoMMC1Mapper(this) }
        mappers[4] = { NintendoMMC3Mapper(this) }

        ui.updateStatus("Ready to load a ROM.")
    }

    fun reset() {
        mmap.map { x -> x.reset() }
        cpu.reset()
        ppu.reset()
        papu.reset()
    }

    fun start() {
        if (rom.isPresent && rom.get().valid) {
            if (false == isRunning) {
                isRunning = true

                frameInterval = ui.setInterval(this::frame, frameTime)

                resetFps()
                printFps()

                fpsInterval = ui.setInterval(this::printFps, opts.fpsInterval)
            }
        } else {
            ui.updateStatus("There is no ROM loaded, or it is invalid.")
        }
    }

    fun frame() {
        ppu.startFrame()
        var cycles: Int
        val emulateSound = opts.emulateSound

        var endLoop = false

        while (!endLoop) {
            if (cpu.cyclesToHalt == 0) {
                // Execute a CPU instruction
                cycles = cpu.emulate()
                if (emulateSound) {
                    papu.clockFrameCounter(cycles)
                }
                cycles *= 3
            }
            else {
                if (cpu.cyclesToHalt > 8) {
                    cycles = 24
                    if (emulateSound) {
                        papu.clockFrameCounter(8)
                    }
                    cpu.cyclesToHalt -= 8
                }
                else {
                    cycles = cpu.cyclesToHalt * 3
                    if (emulateSound) {
                        papu.clockFrameCounter(cpu.cyclesToHalt)
                    }
                    cpu.cyclesToHalt = 0
                }
            }

            while (!endLoop && cycles > 0) {
                cycles -= 1
                if(ppu.curX == ppu.spr0HitX && ppu.f_spVisibility == 1 && ppu.scanline - 21 == ppu.spr0HitY) {
                    // Set sprite 0 hit flag:
                    ppu.setStatusFlag(ppu.STATUS_SPRITE0HIT, true)
                }

                if (ppu.requestEndFrame) {
                    ppu.nmiCounter -= 1
                    if (ppu.nmiCounter == 0) {
                        ppu.requestEndFrame = false
                        ppu.startVBlank()
                        //break FRAMELOOP
                        endLoop = true
                    }
                }

                if (!endLoop) {
                    ppu.curX += 1
                    if (ppu.curX == 341) {
                        ppu.curX = 0
                        ppu.endScanline()
                    }
                }
            }
        }
        fpsFrameCount += 1
        lastFrameTime = Date()
    }

    fun printFps() {
        /*val now = new Date()
        val s = if (lastFpsTime.isDefined) {
          val time = fpsFrameCount / ((now.getTime - lastFpsTime.get.getTime()) / 1000.0)
          f"Running: $time%.2f FPS"
        } else "Running"
        ui.updateStatus(s)
        fpsFrameCount = 0
        lastFpsTime = Option(now)*/
    }

    fun resetFps() {
        lastFpsTime = Optional.empty()
        fpsFrameCount = 0
    }

    fun stop() {
        ui.clearInterval(frameInterval)
        ui.clearInterval(fpsInterval)
        isRunning = false
    }

    fun stop(message: String) {
        stop()
        crashMessage = message
    }

    fun reloadRom() {
        if (romData.isPresent) loadRom(romData.get())
    }

    fun loadRom(data: RomData): Boolean {
        if (isRunning) stop()

        ui.updateStatus("Loading ROM...")

        // Load ROM file
        rom = Optional.of(Rom(this))
        rom.get().load(data)

        if (rom.get().valid) {
            reset()
            mmap = rom.get().createMapper()
            if (!mmap.isPresent) {
                return false // TODO
            }

            mmap.get().loadRom()
            ppu.setMirroring(rom.get().getMirroringType())
            romData = Optional.of(data)

            ui.updateStatus("Successfully loaded. Ready to be started.")
        } else {
            ui.updateStatus("Invalid ROM!")
        }

        return rom.get().valid
    }

    fun setFrameRate(rate: Int) {
        preferredFrameRate = rate
        frameTime = 1000 / rate
        // papu.setSampleRate(sampleRate, false)  // TODO where is this method?
    }
}