package bagguley.knes.papu

import bagguley.knes.IRQ_NORMAL

class ChannelDM(val papu: Papu) {
    companion object {
        const val MODE_NORMAL = 0
        const val MODE_LOOP = 1
        const val MODE_IRQ = 2
    }

    var isEnabled:Boolean = false
    set(value) {
        if ((!field) && value) {
            playLengthCounter = playLength
        }
        field = value
    }
    var hasSample:Boolean = false
    var irqGenerated:Boolean = false

    var playMode:Int = 0
    var dmaFrequency:Int = 0
    var dmaCounter:Int = 0
    var deltaCounter:Int = 0
    var playStartAddress:Int = 0
    var playAddress:Int = 0
    var playLength:Int = 0
    var playLengthCounter:Int = 0
    var shiftCounter:Int = 0
    var reg4012:Int = 0
    var reg4013:Int = 0
    var sample:Int = 0
    var dacLsb:Int = 0
    var data:Int = 0

    init {
        reset()
    }

    fun clockDmc() {
        // Only alter DAC value if the sample buffer has data:
        if (hasSample) {
            if ((data and 1) == 0) {

                // Decrement delta:
                if (deltaCounter > 0) {
                    deltaCounter -= 1
                }
            }
            else {
                // Increment delta:
                if (deltaCounter < 63) {
                    deltaCounter += 1
                }
            }

            // Update sample value:
            sample = if (isEnabled) (deltaCounter shl 1) + dacLsb else 0

            // Update shift register:
            data = data shr 1
        }

        dmaCounter -= 1
        if (dmaCounter <= 0) {

            // No more sample bits.
            hasSample = false
            endOfSample()
            dmaCounter = 8

        }

        if (irqGenerated) {
            papu.nes.cpu.requestIrq(IRQ_NORMAL)
        }
    }

    fun endOfSample() {
        if (playLengthCounter == 0 && playMode == MODE_LOOP) {

            // Start from beginning of sample:
            playAddress = playStartAddress
            playLengthCounter = playLength

        }

        if (playLengthCounter > 0) {

            // Fetch next sample:
            nextSample()

            if (playLengthCounter == 0) {

                // Last byte of sample fetched, generate IRQ:
                if (playMode == MODE_IRQ) {
                    // Generate IRQ:
                    irqGenerated = true

                }

            }

        }
    }

    fun nextSample() {
        // Fetch byte:
        data = papu.nes.mmap.get().load(playAddress)
        papu.nes.cpu.haltCycles(4)

        playLengthCounter -= 1
        playAddress += 1
        if (playAddress > 0xFFFF) {
            playAddress = 0x8000
        }

        hasSample = true
    }


    fun writeReg(address: Int, value: Int) {
        when (address) {
            0x4010 -> {

                // Play mode, DMA Frequency
                if ((value shr 6) == 0) {
                    playMode = MODE_NORMAL
                }
                else if (((value shr 6) and 1) == 1) {
                    playMode = MODE_LOOP
                }
                else if ((value shr 6) == 2) {
                    playMode = MODE_IRQ
                }

                if ((value and 0x80) == 0) {
                    irqGenerated = false
                }

                dmaFrequency = papu.getDmcFrequency(value and 0xF)

            }
            0x4011 -> {

                // Delta counter load register:
                deltaCounter = (value shr 1) and 63
                dacLsb = value and 1
                sample = ((deltaCounter shl 1) + dacLsb) // update sample value

            }
            0x4012 -> {

                // DMA address load register
                playStartAddress = (value shl 6) or 0x0C000
                playAddress = playStartAddress
                reg4012 = value

            }
            0x4013 -> {

                // Length of play code
                playLength = (value shl 4) + 1
                playLengthCounter = playLength
                reg4013 = value

            }
            0x4015 -> {

                // DMC/IRQ Status
                if (((value shr 4) and 1) == 0) {
                    // Disable:
                    playLengthCounter = 0
                }
                else {
                    // Restart:
                    playAddress = playStartAddress
                    playLengthCounter = playLength
                }
                irqGenerated = false
            }
        }
    }

    /*fun setEnabled(value:Boolean) {
        if ((!isEnabled) && value) {
            playLengthCounter = playLength
        }
        isEnabled = value
    }*/ // WB

    fun getLengthStatus() = if (playLengthCounter == 0 || !isEnabled) 0 else 1

    fun getIrqStatus() = if (irqGenerated) 1 else 0

    fun reset() {
        isEnabled = false
        irqGenerated = false
        playMode = MODE_NORMAL
        dmaFrequency = 0
        dmaCounter = 0
        deltaCounter = 0
        playStartAddress = 0
        playAddress = 0
        playLength = 0
        playLengthCounter = 0
        sample = 0
        dacLsb = 0
        shiftCounter = 0
        reg4012 = 0
        reg4013 = 0
        data = 0
    }
}