package bagguley.knes.papu

class ChannelSquare(val papu: Papu, val square1: Boolean) {
    val dutyLookup = arrayOf(
        0, 1, 0, 0, 0, 0, 0, 0,
        0, 1, 1, 0, 0, 0, 0, 0,
        0, 1, 1, 1, 1, 0, 0, 0,
        1, 0, 0, 1, 1, 1, 1, 1)

    val impLookup = arrayOf(
        1,-1, 0, 0, 0, 0, 0, 0,
        1, 0,-1, 0, 0, 0, 0, 0,
        1, 0, 0, 0,-1, 0, 0, 0,
        -1, 0, 1, 0, 0, 0, 0, 0)

    var isEnabled:Boolean = false
    set(value) {
        field = value
        if (!value) {
            lengthCounter = 0
        }
        updateSampleValue()
    }
    var lengthCounterEnable:Boolean = false
    var sweepActive:Boolean = false
    var envDecayDisable:Boolean = false
    var envDecayLoopEnable:Boolean = false
    var envReset:Boolean = false
    var sweepCarry:Boolean = false
    var updateSweepPeriod:Boolean = false

    var progTimerCount:Int = 0
    var progTimerMax:Int = 0
    var lengthCounter:Int = 0
    var squareCounter:Int = 0
    var sweepCounter:Int = 0
    var sweepCounterMax:Int = 0
    var sweepMode:Int = 0
    var sweepShiftAmount:Int = 0
    var envDecayRate:Int = 0
    var envDecayCounter:Int = 0
    var envVolume:Int = 0
    var masterVolume:Int = 0
    var dutyMode:Int = 0
    var sweepResult:Int = 0
    var sampleValue:Int = 0
    var vol:Int = 0

    init {
        reset()
    }

    fun reset() {
        progTimerCount = 0
        progTimerMax = 0
        lengthCounter = 0
        squareCounter = 0
        sweepCounter = 0
        sweepCounterMax = 0
        sweepMode = 0
        sweepShiftAmount = 0
        envDecayRate = 0
        envDecayCounter = 0
        envVolume = 0
        masterVolume = 0
        dutyMode = 0
        vol = 0

        isEnabled = false
        lengthCounterEnable = false
        sweepActive = false
        sweepCarry = false
        envDecayDisable = false
        envDecayLoopEnable = false
    }

    fun clockLengthCounter() {
        if (lengthCounterEnable && lengthCounter > 0) {
            lengthCounter -= 1
            if (lengthCounter == 0) {
                updateSampleValue()
            }
        }
    }

    fun clockEnvDecay() {
        if (envReset) {
            // Reset envelope:
            envReset = false
            envDecayCounter = envDecayRate + 1
            envVolume = 0xF
        } else {
            envDecayCounter -= 1
            if (envDecayCounter <= 0) {
                // Normal handling:
                envDecayCounter = envDecayRate + 1
                if (envVolume > 0) {
                    envVolume -= 1
                }else{
                    envVolume = if (envDecayLoopEnable) 0xF else 0
                }
            }
        }

        masterVolume = if (envDecayDisable) envDecayRate else envVolume
        updateSampleValue()
    }

    fun clockSweep() {
        sweepCounter -= 1
        if (sweepCounter <= 0) {
            sweepCounter = sweepCounterMax + 1
            if (sweepActive && sweepShiftAmount>0 && progTimerMax>7) {

                // Calculate result from shifter:
                sweepCarry = false
                if (sweepMode == 0) {
                    progTimerMax += (progTimerMax shr sweepShiftAmount)
                    if (progTimerMax > 4095) {
                        progTimerMax = 4095
                        sweepCarry = true
                    }
                } else{
                    progTimerMax -= ((progTimerMax shr sweepShiftAmount) - (if (square1) 1 else 0))
                }
            }
        }

        if (updateSweepPeriod) {
            updateSweepPeriod = false
            sweepCounter = sweepCounterMax + 1
        }
    }

    fun updateSampleValue() {
        sampleValue = if (isEnabled && lengthCounter > 0 && progTimerMax > 7) {
            if (sweepMode == 0 && (progTimerMax + (progTimerMax shr sweepShiftAmount)) > 4095) {
                //if (sweepCarry) {
                0
            } else {
                masterVolume * dutyLookup[(dutyMode shl 3)+squareCounter]
            }
        } else {
            0
        }
    }

    fun writeReg(address:Int, value:Int) {
        val addrAdd = if (square1) 0 else 4

        when (address) {
            0x4000 + addrAdd -> {
                // Volume/Envelope decay:
                envDecayDisable = ((value and 0x10) != 0)
                envDecayRate = value and  0xF
                envDecayLoopEnable = ((value and 0x20) != 0)
                dutyMode = (value shr 6) and 0x3
                lengthCounterEnable = ((value and 0x20) == 0)
                masterVolume = if (envDecayDisable) envDecayRate else envVolume
                updateSampleValue()
            }
            0x4001+addrAdd -> {
                // Sweep:
                sweepActive = ((value and 0x80) != 0)
                sweepCounterMax = ((value shr 4) and 7)
                sweepMode = (value shr 3) and 1
                sweepShiftAmount = value and 7
                updateSweepPeriod = true
            }
            0x4002 + addrAdd -> {
                // Programmable timer:
                progTimerMax = progTimerMax and 0x700
                progTimerMax = progTimerMax or value
            }
            0x4003 + addrAdd -> {
                // Programmable timer, length counter
                progTimerMax = progTimerMax and 0xFF
                progTimerMax = progTimerMax or ((value and 0x7) shl 8)

                if (isEnabled){
                    lengthCounter = papu.getLengthMax(value and 0xF8)
                }

                envReset  = true
            }
        }
    }

    /*fun setEnabled(value:Boolean) {
        isEnabled = value
        if (!value) {
            lengthCounter = 0
        }
        updateSampleValue()
    }*/ // WB

    fun getLengthStatus() = if (lengthCounter == 0 || !isEnabled) 0 else 1
}