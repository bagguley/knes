package bagguley.knes.papu

class ChannelNoise(val papu: Papu) {
    var isEnabled:Boolean = false
    set(value) {
        field = value
        if (!value) {
            lengthCounter = 0
        }
        updateSampleValue()
    }
    var envDecayDisable:Boolean = false
    var envDecayLoopEnable:Boolean = false
    var lengthCounterEnable:Boolean = false
    var envReset:Boolean = false
    var shiftNow: Boolean = false

    var lengthCounter:Int = 0
    var progTimerCount:Int = 0
    var progTimerMax:Int = 0
    var envDecayRate:Int = 0
    var envDecayCounter:Int = 0
    var envVolume:Int = 0
    var masterVolume:Int = 0
    var shiftReg = 1 shl 14
    var randomBit:Int = 0
    var randomMode:Int = 0
    var sampleValue:Int = 0
    var accValue:Int = 0
    var accCount:Int = 1
    var tmp:Int = 0

    init {
        reset()
    }

    fun reset() {
        progTimerCount = 0
        progTimerMax = 0
        isEnabled = false
        lengthCounter = 0
        lengthCounterEnable = false
        envDecayDisable = false
        envDecayLoopEnable = false
        shiftNow = false
        envDecayRate = 0
        envDecayCounter = 0
        envVolume = 0
        masterVolume = 0
        shiftReg = 1
        randomBit = 0
        randomMode = 0
        sampleValue = 0
        tmp = 0
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
        }
        else {
            envDecayCounter -= 1
            if (envDecayCounter <= 0) {
                // Normal handling:
                envDecayCounter = envDecayRate + 1
                if (envVolume > 0) {
                    envVolume -= 1
                }
                else {
                    envVolume = if (envDecayLoopEnable) 0xF else 0
                }
            }
        }
        masterVolume = if (envDecayDisable) envDecayRate else envVolume
        updateSampleValue()
    }

    fun updateSampleValue() {
        if (isEnabled && lengthCounter > 0) {
            sampleValue = randomBit * masterVolume
        }
    }

    fun writeReg(address:Int, value:Int) {
        when (address) {
            0x400C -> {
                // Volume/Envelope decay:
                envDecayDisable = ((value and 0x10) != 0)
                envDecayRate = value and 0xF
                envDecayLoopEnable = ((value and 0x20) != 0)
                lengthCounterEnable = ((value and 0x20) == 0)
                masterVolume = if (envDecayDisable) envDecayRate else envVolume
            }
            0x400E -> {
                // Programmable timer:
                progTimerMax = papu.getNoiseWaveLength(value and 0xF)
                randomMode = value shr 7

            }
            0x400F -> {
                // Length counter
                lengthCounter = papu.getLengthMax(value and 248)
                envReset = true
            }
        }
        // Update:
        //updateSampleValue()
    }

    /*fun setEnabled(value:Boolean) {
        isEnabled = value
        if (!value) {
            lengthCounter = 0
        }
        updateSampleValue()
    }*/ // WB

    fun getLengthStatus() = if (lengthCounter==0 || !isEnabled) 0 else 1
}