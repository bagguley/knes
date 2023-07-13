package bagguley.knes.papu

class ChannelTriangle(val papu: Papu) {

    var isEnabled: Boolean = false
    set(value) {
        field = value
        if (!value) {
            lengthCounter = 0
        }
        updateSampleCondition()
    }

    var sampleCondition: Boolean = false
    var lengthCounterEnable: Boolean = false
    var lcHalt: Boolean = false
    var lcControl: Boolean = false

    var progTimerCount: Int = 0
    var progTimerMax: Int = 0
    var triangleCounter: Int = 0
    var lengthCounter: Int = 0
    var linearCounter: Int = 0
    var lcLoadValue: Int = 0
    var sampleValue: Int = 0
    var tmp: Int = 0

    init {
        reset()
    }

    fun reset() {
        progTimerCount = 0
        progTimerMax = 0
        triangleCounter = 0
        isEnabled = false
        sampleCondition = false
        lengthCounter = 0
        lengthCounterEnable = false
        linearCounter = 0
        lcLoadValue = 0
        lcHalt = true
        lcControl = false
        tmp = 0
        sampleValue = 0xF
    }

    fun clockLengthCounter() {
        if (lengthCounterEnable && lengthCounter > 0) {
            lengthCounter -= 1
            if (lengthCounter == 0) {
                updateSampleCondition()
            }
        }
    }

    fun clockLinearCounter() {
        if (lcHalt) {
            // Load:
            linearCounter = lcLoadValue
            updateSampleCondition()
        } else if (linearCounter > 0) {
            // Decrement:
            linearCounter -= 1
            updateSampleCondition()
        }
        if (!lcControl) {
            // Clear halt flag:
            lcHalt = false
        }
    }

    fun getLengthStatus() = if (lengthCounter == 0 || !isEnabled) 0 else 1

    fun readReg(address: Int) = 0

    fun writeReg(address: Int, value: Int) {
        when (address) {
            0x4008 -> {
                // New values for linear counter:
                lcControl = (value and 0x80) != 0
                lcLoadValue = value and 0x7F

                // Length counter enable:
                lengthCounterEnable = !lcControl
            }
            0x400A -> {
                // Programmable timer:
                progTimerMax = progTimerMax and 0x700
                progTimerMax = progTimerMax or value

            }
            0x400B -> {
                // Programmable timer, length counter
                progTimerMax = progTimerMax and 0xFF
                progTimerMax = progTimerMax or ((value and 0x07) shl 8)
                lengthCounter = papu.getLengthMax(value and 0xF8)
                lcHalt = true
            }
        }

        updateSampleCondition()
    }

    fun clockProgrammableTimer(nCycles: Int) {
        if (progTimerMax > 0) {
            progTimerCount += nCycles
            while (progTimerMax > 0 && progTimerCount >= progTimerMax) {
                progTimerCount -= progTimerMax
                if (isEnabled && lengthCounter > 0 && linearCounter > 0) {
                    clockTriangleGenerator()
                }
            }
        }
    }

    fun clockTriangleGenerator() {
        triangleCounter += 1
        triangleCounter = triangleCounter and 0x1F
    }

    /*fun setEnabled(value: Boolean) {
        isEnabled = value
        if (!value) {
            lengthCounter = 0
        }
        updateSampleCondition()
    }*/ // WB

    fun updateSampleCondition() {
        sampleCondition = isEnabled && progTimerMax > 7 && linearCounter > 0 && lengthCounter > 0
    }
}