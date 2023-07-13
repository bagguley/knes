package bagguley.knes.papu

import bagguley.knes.IRQ_NORMAL
import bagguley.knes.Nes
import kotlin.math.floor

class Papu(val nes: Nes) {
    val square1 = ChannelSquare(this, true)
    val square2 = ChannelSquare(this, false)
    val triangle = ChannelTriangle(this)
    val noise = ChannelNoise(this)
    val dmc = ChannelDM(this)

    val frameIrqCounter = null
    var frameIrqCounterMax: Int = 4
    var initCounter: Int = 2048
    var channelEnableValue: Int = 0

    val bufferSize: Int = 4096
    var bufferIndex:Int = 0
    var sampleRate: Int = 44100

    lateinit var lengthLookup:IntArray
    lateinit var dmcFreqLookup:IntArray
    lateinit var noiseWavelengthLookup:IntArray
    lateinit var square_table:IntArray
    lateinit var tnd_table:IntArray
    var sampleBuffer:IntArray = IntArray(bufferSize * 2)

    var frameIrqEnabled: Boolean = false
    var frameIrqActive: Boolean = false
    val frameClockNow = null
    var startedPlaying:Boolean = false
    val recordOutput:Boolean = false
    var initingHardware:Boolean = false

    var masterFrameCounter: Int = 0
    var derivedFrameCounter: Int = 0
    var countSequence: Int = 0
    var sampleTimer:Int = 0
    var frameTime:Int = 0
    var sampleTimerMax:Int = 0
    var sampleCount: Int = 0
    var triValue:Int = 0

    var smpSquare1: Int = 0
    var smpSquare2: Int = 0
    var smpTriangle: Int = 0
    var smpDmc: Int = 0
    var accCount: Int = 0

    // DC removal vars:
    var prevSampleL = 0
    var prevSampleR = 0
    var smpAccumL = 0
    var smpAccumR = 0

    // DAC range:
    var dacRange = 0
    var dcValue = 0

    // Master volume:
    var masterVolume = 256
    set(value) {
        var aValue = value
        if (aValue < 0) {
            aValue = 0
        }
        if (aValue > 256) {
            aValue = 256
        }
        field = aValue
        updateStereoPos()
    }

    // Stereo positioning:
    var stereoPosLSquare1:Int = 0
    var stereoPosLSquare2:Int = 0
    var stereoPosLTriangle:Int = 0
    var stereoPosLNoise:Int = 0
    var stereoPosLDMC:Int = 0
    var stereoPosRSquare1:Int = 0
    var stereoPosRSquare2:Int = 0
    var stereoPosRTriangle:Int = 0
    var stereoPosRNoise:Int = 0
    var stereoPosRDMC:Int = 0

    var extraCycles:Int = 0

    var maxSample: Int = 0
    var minSample: Int = 0

    // Panning:
    val panning = intArrayOf(80, 170, 100, 150, 128)

    init {
        setPanning(panning)

        // Initialize lookup tables:
        initLengthLookup()
        initDmcFrequencyLookup()
        initNoiseWavelengthLookup()
        initDACtables()

        // Init sound registers:
        for (i in 0 until 0x14) {
            if (i == 0x10) {
                writeReg(0x4010, 0x10)
            } else {
                writeReg(0x4000 + i, 0)
            }
        }

        reset()
    }

    fun reset() {
        sampleRate = nes.opts.sampleRate
        sampleTimerMax = floor((1024.0 * nes.opts.cpuFreqNtsc * nes.opts.preferredFrameRate) / (sampleRate * 60.0)).toInt()

        frameTime = floor((14915.0 * nes.opts.preferredFrameRate) / 60.0).toInt()

        sampleTimer = 0
        bufferIndex = 0

        updateChannelEnable(0)
        masterFrameCounter = 0
        derivedFrameCounter = 0
        countSequence = 0
        sampleCount = 0
        initCounter = 2048
        frameIrqEnabled = false
        initingHardware = false

        resetCounter()

        square1.reset()
        square2.reset()
        triangle.reset()
        noise.reset()
        dmc.reset()

        bufferIndex = 0
        accCount = 0
        smpSquare1 = 0
        smpSquare2 = 0
        smpTriangle = 0
        smpDmc = 0

        frameIrqEnabled = false
        frameIrqCounterMax = 4

        channelEnableValue = 0xFF
        startedPlaying = false
        prevSampleL = 0
        prevSampleR = 0
        smpAccumL = 0
        smpAccumR = 0

        maxSample = -500000
        minSample = 500000
    }

    fun readReg(address: Int): Int {
        // Read 0x4015:
        var tmp = 0
        tmp = tmp or (square1.getLengthStatus())
        tmp = tmp or (square2.getLengthStatus() shl 1)
        tmp = tmp or (triangle.getLengthStatus() shl 2)
        tmp = tmp or (noise.getLengthStatus() shl 3)
        tmp = tmp or (dmc.getLengthStatus() shl 4)
        tmp = tmp or ((if (frameIrqActive && frameIrqEnabled) 1 else 0) shl 6)
        tmp = tmp or (dmc.getIrqStatus() shl 7)

        frameIrqActive = false
        dmc.irqGenerated = false

        return tmp and 0xFFFF
    }

    fun writeReg(address: Int, value: Int) {
        if (address in 0x4000..0x4003) {
            // Square Wave 1 Control
            square1.writeReg(address, value)
            ////System.out.println("Square Write")
        } else if (address in 0x4004..0x4007) {
            // Square 2 Control
            square2.writeReg(address, value)
        } else if (address in 0x4008..0x400b) {
            // Triangle Control
            triangle.writeReg(address, value)
        } else if (address in 0x400C..0x400F) {
            // Noise Control
            noise.writeReg(address, value)
        } else if (address == 0x4010) {
            // DMC Play mode & DMA frequency
            dmc.writeReg(address, value)
        } else if (address == 0x4011) {
            // DMC Delta Counter
            dmc.writeReg(address, value)
        } else if (address == 0x4012) {
            // DMC Play code starting address
            dmc.writeReg(address, value)
        } else if (address == 0x4013) {
            // DMC Play code length
            dmc.writeReg(address, value)
        } else if (address == 0x4015) {
            // Channel enable
            updateChannelEnable(value)

            if (value != 0 && initCounter > 0) {
                // Start hardware initialization
                initingHardware = true
            }

            // DMC/IRQ Status
            dmc.writeReg(address, value)
        } else if (address == 0x4017) {
            // Frame counter control
            countSequence = (value shr 7) and 1
            masterFrameCounter = 0
            frameIrqActive = false

            frameIrqEnabled = ((value shr 6) and 0x1) == 0

            if (countSequence == 0) {
                // NTSC:
                frameIrqCounterMax = 4
                derivedFrameCounter = 4
            } else {
                // PAL:
                frameIrqCounterMax = 5
                derivedFrameCounter = 0
                frameCounterTick()
            }
        }
    }

    fun resetCounter() {
        derivedFrameCounter = if (countSequence == 0) 4 else 0
    }

    // Updates channel enable status.
    // This is done on writes to the
    // channel enable register (0x4015),
    // and when the user enables/disables channels
    // in the GUI.
    fun updateChannelEnable(value: Int) {
        channelEnableValue = value and 0xFFFF
        square1.isEnabled = ((value and 1) != 0)
        square2.isEnabled = ((value and 2) != 0)
        triangle.isEnabled = ((value and 4) != 0)
        noise.isEnabled = ((value and 8) != 0)
        dmc.isEnabled = ((value and 16) != 0)
    }

    fun clockFrameCounter(nCyclesInput:Int) {
        var endEarly = false

        var nCycles:Int = nCyclesInput
        if (initCounter > 0) {
            if (initingHardware) {
                initCounter -= nCycles
                if (initCounter <= 0) {
                    initingHardware = false
                }
                endEarly = true
            }
        }

        if (!endEarly) {
            // Don't process ticks beyond next sampling:
            nCycles += extraCycles
            val maxCycles = sampleTimerMax - sampleTimer
            if ((nCycles shl 10) > maxCycles) {
                extraCycles = ((nCycles shl 10) - maxCycles) shr 10
                nCycles -= extraCycles
            } else {
                extraCycles = 0
            }

            /*var dmc = dmc
            var triangle = triangle
            var square1 = square1
            var square2 = square2
            var noise = noise*/

            // Clock DMC:
            if (dmc.isEnabled) {

                dmc.shiftCounter -= (nCycles shl 3)
                while(dmc.shiftCounter <= 0 && dmc.dmaFrequency > 0) {
                    dmc.shiftCounter += dmc.dmaFrequency
                    dmc.clockDmc()
                }
            }

            // Clock Triangle channel Prog timer:
            if (triangle.progTimerMax > 0) {

                triangle.progTimerCount -= nCycles
                while(triangle.progTimerCount <= 0) {
                    triangle.progTimerCount += triangle.progTimerMax+1
                    if (triangle.linearCounter>0 && triangle.lengthCounter > 0) {

                        triangle.triangleCounter += 1
                        triangle.triangleCounter = triangle.triangleCounter and 0x1F

                        if (triangle.isEnabled) {
                            if (triangle.triangleCounter>=0x10) {
                                // Normal value.
                                triangle.sampleValue = (triangle.triangleCounter and 0xF)
                            } else {
                                // Inverted value.
                                triangle.sampleValue = (0xF - (triangle.triangleCounter and 0xF))
                            }
                            triangle.sampleValue = triangle.sampleValue shl 4
                        }
                    }
                }
            }

            // Clock Square channel 1 Prog timer:
            square1.progTimerCount -= nCycles
            if (square1.progTimerCount <= 0) {

                square1.progTimerCount += (square1.progTimerMax+1) shl 1

                square1.squareCounter += 1
                square1.squareCounter = square1.squareCounter and 0x7
                square1.updateSampleValue()

            }

            // Clock Square channel 2 Prog timer:
            square2.progTimerCount -= nCycles
            if (square2.progTimerCount <= 0) {

                square2.progTimerCount += (square2.progTimerMax+1) shl 1

                square2.squareCounter += 1
                square2.squareCounter = square2.squareCounter and 0x7
                square2.updateSampleValue()

            }

            // Clock noise channel Prog timer:
            var acc_c:Int = nCycles
            if (noise.progTimerCount-acc_c > 0) {

                // Do all cycles at once:
                noise.progTimerCount -= acc_c
                noise.accCount       += acc_c
                noise.accValue       += acc_c * noise.sampleValue

            } else {

                // Slow-step:
                while(acc_c > 0) {
                    acc_c -= 1
                    noise.progTimerCount -= 1
                    if (noise.progTimerCount <= 0 && noise.progTimerMax>0) {

                        // Update noise shift register:
                        noise.shiftReg = noise.shiftReg shl 1
                        noise.tmp = (((noise.shiftReg shl (if (noise.randomMode==0) 1 else 6)) xor noise.shiftReg) and 0x8000 )
                        if (noise.tmp != 0) {

                            // Sample value must be 0.
                            noise.shiftReg = noise.shiftReg or 0x01
                            noise.randomBit = 0
                            noise.sampleValue = 0

                        }else{

                            // Find sample value:
                            noise.randomBit = 1
                            if (noise.isEnabled && noise.lengthCounter>0) {
                                noise.sampleValue = noise.masterVolume
                            }else{
                                noise.sampleValue = 0
                            }

                        }

                        noise.progTimerCount += noise.progTimerMax

                    }

                    noise.accValue += noise.sampleValue
                    noise.accCount += 1

                }
            }


            // Frame IRQ handling:
            if (frameIrqEnabled && frameIrqActive){
                nes.cpu.requestIrq(IRQ_NORMAL)
            }

            // Clock frame counter at double CPU speed:
            masterFrameCounter += (nCycles shl 1)
            if (masterFrameCounter >= frameTime) {
                // 240Hz tick:
                masterFrameCounter -= frameTime
                frameCounterTick()
            }

            // Accumulate sample value:
            accSample(nCycles)

            // Clock sample timer:
            sampleTimer += nCycles shl 10
            if (sampleTimer >= sampleTimerMax) {
                // Sample channels:
                sample()
                sampleTimer -= sampleTimerMax
            }
        }
    }

    fun accSample(cycles: Int) {
        // Special treatment for triangle channel - need to interpolate.
        if (triangle.sampleCondition) {
            triValue = floor((triangle.progTimerCount shl 4).toDouble() / (triangle.progTimerMax + 1)).toInt()
            if (triValue > 16) {
                triValue = 16
            }

            if (triangle.triangleCounter >= 16) {
                triValue = 16 - triValue
            }

            // Add non-interpolated sample value:
            triValue += triangle.sampleValue
        }

        // Now sample normally:
        when (cycles) {
            2 -> {
                smpTriangle += triValue shl 1
                smpDmc      += dmc.sample shl 1
                smpSquare1  += square1.sampleValue shl 1
                smpSquare2  += square2.sampleValue shl 1
                accCount    += 2

            }
            4 -> {
                smpTriangle += triValue shl 2
                smpDmc      += dmc.sample shl 2
                smpSquare1  += square1.sampleValue shl 2
                smpSquare2  += square2.sampleValue shl 2
                accCount    += 4

            }
            else -> {
                smpTriangle += cycles * triValue
                smpDmc      += cycles * dmc.sample
                smpSquare1  += cycles * square1.sampleValue
                smpSquare2  += cycles * square2.sampleValue
                accCount    += cycles

            }
        }
    }

    fun frameCounterTick() {
        derivedFrameCounter += 1
        if (derivedFrameCounter >= frameIrqCounterMax) {
            derivedFrameCounter = 0
        }

        if (derivedFrameCounter == 1 || derivedFrameCounter == 3) {

            // Clock length & sweep:
            triangle.clockLengthCounter()
            square1.clockLengthCounter()
            square2.clockLengthCounter()
            noise.clockLengthCounter()
            square1.clockSweep()
            square2.clockSweep()
        }

        if (derivedFrameCounter in 0..3) {

            // Clock linear & decay:
            square1.clockEnvDecay()
            square2.clockEnvDecay()
            noise.clockEnvDecay()
            triangle.clockLinearCounter()
        }

        if (derivedFrameCounter == 3 && countSequence == 0) {

            // Enable IRQ:
            frameIrqActive = true
        }

        // End of 240Hz tick
    }

    // Samples the channels, mixes the output together,
    // writes to buffer and (if enabled) file.
    fun sample() {
        var sq_index: Int
        var tnd_index: Int

        if (accCount > 0) {
            smpSquare1 = smpSquare1 shl 4
            smpSquare1 = floor(smpSquare1.toDouble() / accCount).toInt()

            smpSquare2 = smpSquare2 shl 4
            smpSquare2 = floor(smpSquare2.toDouble() / accCount).toInt()

            smpTriangle = floor(smpTriangle.toDouble() / accCount).toInt()

            smpDmc = smpDmc shl 4
            smpDmc = floor(smpDmc.toDouble() / accCount).toInt()

            accCount = 0
        }
        else {
            smpSquare1 = square1.sampleValue shl 4
            smpSquare2 = square2.sampleValue shl 4
            smpTriangle = triangle.sampleValue
            smpDmc = dmc.sample shl 4
        }

        val smpNoise:Int = floor((noise.accValue shl 4).toDouble() / noise.accCount).toInt()
        noise.accValue = smpNoise shr 4
        noise.accCount = 1

        // Stereo sound.

        // Left channel:
        sq_index  = (smpSquare1 * stereoPosLSquare1 + smpSquare2 * stereoPosLSquare2) shr 8
        tnd_index = (3 * smpTriangle * stereoPosLTriangle + (smpNoise shl 1) * stereoPosLNoise + smpDmc * stereoPosLDMC) shr 8

        // println("SQ " + sq_index)
        // println("TND " + tnd_index)

        if (sq_index >= square_table.size) {
            sq_index  = square_table.size-1
        }
        if (tnd_index >= tnd_table.size) {
            tnd_index = tnd_table.size - 1
        }

        if (sq_index < 0) println("SQ $sq_index")
        if (tnd_index < 0){
            println("SQ $sq_index")
            println("TND $tnd_index")
            println("smpTriangle $smpTriangle")
            println("stereoPosLTriangle $stereoPosLTriangle")
            println("smpNoise $smpNoise")
            println("sterepPosLNoise $stereoPosLNoise")
            println("smpDmc $smpDmc")
            println("stereoPosLDMC $stereoPosLDMC")
        }

        var sampleValueL = square_table[sq_index] + tnd_table[tnd_index] - dcValue

        // Right channel:
        sq_index = (smpSquare1 * stereoPosRSquare1 + smpSquare2 * stereoPosRSquare2) shr 8
        tnd_index = (3 * smpTriangle * stereoPosRTriangle + (smpNoise shl 1) * stereoPosRNoise + smpDmc * stereoPosRDMC) shr 8

        if (sq_index >= square_table.size) {
            sq_index = square_table.size - 1
        }
        if (tnd_index >= tnd_table.size) {
            tnd_index = tnd_table.size - 1
        }

        var sampleValueR = square_table[sq_index] + tnd_table[tnd_index] - dcValue

        // Remove DC from left channel:
        val smpDiffL = sampleValueL - prevSampleL
        prevSampleL += smpDiffL
        smpAccumL += smpDiffL - (smpAccumL shr 10)
        sampleValueL = smpAccumL

        // Remove DC from right channel:
        val smpDiffR     = sampleValueR - prevSampleR
        prevSampleR += smpDiffR
        smpAccumR  += smpDiffR - (smpAccumR shr 10)
        sampleValueR = smpAccumR

        // Write:
        if (sampleValueL > maxSample) {
            maxSample = sampleValueL
        }
        if (sampleValueL < minSample) {
            minSample = sampleValueL
        }
        sampleBuffer[bufferIndex] = sampleValueL
        bufferIndex += 1
        sampleBuffer[bufferIndex] = sampleValueR
        bufferIndex += 1

        // Write full buffer
        if (bufferIndex == sampleBuffer.size) {
            nes.ui.writeAudio(sampleBuffer)
            sampleBuffer = IntArray(bufferSize*2)
            bufferIndex = 0
        }

        // Reset sampled values:
        smpSquare1 = 0
        smpSquare2 = 0
        smpTriangle = 0
        smpDmc = 0
    }

    fun getLengthMax(value:Int): Int {
        return lengthLookup[value shr 3]
    }

    fun getNoiseWaveLength(value:Int) = if (value in 0..0xf) noiseWavelengthLookup[value] else 0

    fun getDmcFrequency(value: Int):Int = if (value in 0..0xf) dmcFreqLookup[value] else 0

    fun setPanning(pos: IntArray) {
        /*for (i <- 0 until 5) {
             panning(i) = pos(i)
        }*/
        updateStereoPos()
    }

    /*fun setMasterVolume(value: Int) {
        var aValue = value
        if (aValue < 0) {
            aValue = 0
        }
        if (aValue > 256) {
            aValue = 256
        }
        masterVolume = aValue
        updateStereoPos()
    }*/ // WB

    fun updateStereoPos() {
        stereoPosLSquare1 = (panning[0] * masterVolume) shr 8
        stereoPosLSquare2 = (panning[1] * masterVolume) shr 8
        stereoPosLTriangle = (panning[2] * masterVolume) shr 8
        stereoPosLNoise = (panning[3] * masterVolume) shr 8
        stereoPosLDMC = (panning[4] * masterVolume) shr 8

        stereoPosRSquare1 = masterVolume - stereoPosLSquare1
        stereoPosRSquare2 = masterVolume - stereoPosLSquare2
        stereoPosRTriangle = masterVolume - stereoPosLTriangle
        stereoPosRNoise = masterVolume - stereoPosLNoise
        stereoPosRDMC = masterVolume - stereoPosLDMC
    }

    fun initLengthLookup() {
        lengthLookup = intArrayOf(
            0x0A, 0xFE,
            0x14, 0x02,
            0x28, 0x04,
            0x50, 0x06,
            0xA0, 0x08,
            0x3C, 0x0A,
            0x0E, 0x0C,
            0x1A, 0x0E,
            0x0C, 0x10,
            0x18, 0x12,
            0x30, 0x14,
            0x60, 0x16,
            0xC0, 0x18,
            0x48, 0x1A,
            0x10, 0x1C,
            0x20, 0x1E
        )
    }

    fun initDmcFrequencyLookup() {
        dmcFreqLookup = IntArray(16)

        dmcFreqLookup[0x0] = 0xD60
        dmcFreqLookup[0x1] = 0xBE0
        dmcFreqLookup[0x2] = 0xAA0
        dmcFreqLookup[0x3] = 0xA00
        dmcFreqLookup[0x4] = 0x8F0
        dmcFreqLookup[0x5] = 0x7F0
        dmcFreqLookup[0x6] = 0x710
        dmcFreqLookup[0x7] = 0x6B0
        dmcFreqLookup[0x8] = 0x5F0
        dmcFreqLookup[0x9] = 0x500
        dmcFreqLookup[0xA] = 0x470
        dmcFreqLookup[0xB] = 0x400
        dmcFreqLookup[0xC] = 0x350
        dmcFreqLookup[0xD] = 0x2A0
        dmcFreqLookup[0xE] = 0x240
        dmcFreqLookup[0xF] = 0x1B0
        //for(int i=0;i<16;i++)dmcFreqLookup[i]/=8
    }

    fun initNoiseWavelengthLookup() {
        noiseWavelengthLookup = IntArray(16)

        noiseWavelengthLookup[0x0] = 0x004
        noiseWavelengthLookup[0x1] = 0x008
        noiseWavelengthLookup[0x2] = 0x010
        noiseWavelengthLookup[0x3] = 0x020
        noiseWavelengthLookup[0x4] = 0x040
        noiseWavelengthLookup[0x5] = 0x060
        noiseWavelengthLookup[0x6] = 0x080
        noiseWavelengthLookup[0x7] = 0x0A0
        noiseWavelengthLookup[0x8] = 0x0CA
        noiseWavelengthLookup[0x9] = 0x0FE
        noiseWavelengthLookup[0xA] = 0x17C
        noiseWavelengthLookup[0xB] = 0x1FC
        noiseWavelengthLookup[0xC] = 0x2FA
        noiseWavelengthLookup[0xD] = 0x3F8
        noiseWavelengthLookup[0xE] = 0x7F2
        noiseWavelengthLookup[0xF] = 0xFE4
    }

    fun initDACtables() {
        var max_sqr = 0
        var max_tnd = 0

        square_table = IntArray(32*16)
        tnd_table = IntArray(204*16)

        for (i in 0 until (32 * 16)) {
            var value: Double = 95.52 / (8128.0 / (i/16.0) + 100.0)
            value *= 0.98411
            value *= 50000.0
            val ival: Int = floor(value).toInt()

            square_table[i] = ival
            if (ival > max_sqr) {
                max_sqr = ival
            }
        }

        for (i in 0 until (204 * 16)) {
            var value: Double = 163.67 / (24329.0 / (i/16.0) + 100.0)
            value *= 0.98411
            value *= 50000.0
            val ival: Int = floor(value).toInt()

            tnd_table[i] = ival
            if (ival > max_tnd) {
                max_tnd = ival
            }

        }

        dacRange = max_sqr + max_tnd
        dcValue = dacRange/2
    }
}