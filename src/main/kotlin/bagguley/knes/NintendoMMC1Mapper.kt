package bagguley.knes

import kotlin.math.floor

class NintendoMMC1Mapper(nes: Nes) : DirectAccessMapper(nes) {
    var regBuffer: Int = 0
    var regBufferCounter: Int = 0
    var mirroring: Int = 0
    var oneScreenMirroring: Int = 0
    var prgSwitchingArea: Int = 0
    var prgSwitchingSize: Int = 0
    var vromSwitchingSize: Int = 0

    var romSelectionReg0: Int = 0
    var romSelectionReg1: Int = 0
    var romBankSelect: Int = 0

    override fun reset() {
        super.reset()

        // 5-bit buffer:
        regBuffer = 0
        regBufferCounter = 0

        // Register 0:
        mirroring = 0
        oneScreenMirroring = 0
        prgSwitchingArea = 1
        prgSwitchingSize = 1
        vromSwitchingSize = 0

        // Register 1:
        romSelectionReg0 = 0

        // Register 2:
        romSelectionReg1 = 0

        // Register 3:
        romBankSelect = 0
    }

    override fun write(address:Int, value:Int) {
        // Writes to addresses other than MMC registers are handled by NoMapper.
        if (address < 0x8000) {
            super.write(address, value)
        } else {
            // See what should be done with the written value:
            if ((value and 128) != 0) {

                // Reset buffering:
                regBufferCounter = 0
                regBuffer = 0

                // Reset register:
                if (getRegNumber(address) == 0) {
                    prgSwitchingArea = 1
                    prgSwitchingSize = 1
                }
            }
            else {

                // Continue buffering:
                //regBuffer = (regBuffer & (0xFF-(1<<regBufferCounter))) | ((value & (1<<regBufferCounter))<<regBufferCounter)
                regBuffer = (regBuffer and (0xFF - (1 shl regBufferCounter))) or ((value and 1) shl regBufferCounter)
                regBufferCounter += 1

                if (regBufferCounter == 5) {
                    // Use the buffered value:
                    setReg(getRegNumber(address), regBuffer)

                    // Reset buffer:
                    regBuffer = 0
                    regBufferCounter = 0
                }
            }
        }
    }

    fun setReg(reg:Int, value:Int) {
        when (reg) {
            0 -> {
                // Mirroring
                val tmp = value and 3
                if (tmp != mirroring) {
                    // Set mirroring:
                    mirroring = tmp
                    if ((mirroring and 2) == 0) {
                        // SingleScreen mirroring overrides the other setting:
                        nes.ppu.setMirroring(Rom.SINGLESCREEN_MIRRORING)
                    }
                    // Not overridden by SingleScreen mirroring.
                    else if ((mirroring and 1) != 0) {
                        nes.ppu.setMirroring(Rom.HORIZONTAL_MIRRORING)
                    }
                    else {
                        nes.ppu.setMirroring(Rom.VERTICAL_MIRRORING)
                    }
                }

                // PRG Switching Area;
                prgSwitchingArea = (value shr 2) and 1

                // PRG Switching Size:
                prgSwitchingSize = (value shr 3) and 1

                // VROM Switching Size:
                vromSwitchingSize = (value shr 4) and 1
            }
            1 -> {
                // ROM selection:
                romSelectionReg0 = (value shr 4) and 1

                // Check whether the cart has VROM:
                if (nes.rom.get().vromCount > 0) {

                    // Select VROM bank at 0x0000:
                    if (vromSwitchingSize == 0) {

                        // Swap 8kB VROM:
                        if (romSelectionReg0 == 0) {
                            load8kVromBank((value and 0xF), 0x0000)
                        }
                        else {
                            load8kVromBank(floor(nes.rom.get().vromCount / 2.0).toInt() + (value and 0xF), 0x0000)
                        }
                    }
                    else {
                        // Swap 4kB VROM:
                        if (romSelectionReg0 == 0) {
                            loadVromBank((value and 0xF), 0x0000)
                        }
                        else {
                            loadVromBank(
                                floor(nes.rom.get().vromCount / 2.0).toInt() + (value and 0xF), 0x0000)
                        }
                    }
                }
            }
            2 -> {
                // ROM selection:
                romSelectionReg1 = (value shr 4) and 1

                // Check whether the cart has VROM:
                if (nes.rom.get().vromCount > 0) {

                    // Select VROM bank at 0x1000:
                    if (vromSwitchingSize == 1) {
                        // Swap 4kB of VROM:
                        if (romSelectionReg1 == 0) {
                            loadVromBank((value and 0xF), 0x1000)
                        }
                        else {
                            loadVromBank(floor(nes.rom.get().vromCount / 2.0).toInt() + (value and 0xF), 0x1000)
                        }
                    }
                }
            }
            else -> {
                // Select ROM bank:
                // -------------------------
                val bank: Int
                var baseBank = 0

                if (nes.rom.get().romCount >= 32) {
                    // 1024 kB cart
                    if (vromSwitchingSize == 0) {
                        if (romSelectionReg0 == 1) {
                            baseBank = 16
                        }
                    }
                    else {
                        baseBank = (romSelectionReg0 or (romSelectionReg1 shl 1)) shl 3
                    }
                }
                else if (nes.rom.get().romCount >= 16) {
                    // 512 kB cart
                    if (romSelectionReg0 == 1) {
                        baseBank = 8
                    }
                }

                if (prgSwitchingSize == 0) {
                    // 32kB
                    bank = baseBank + (value and 0xF)
                    load32kRomBank(bank, 0x8000)
                }
                else {
                    // 16kB
                    bank = baseBank * 2 + (value and 0xF)
                    if (prgSwitchingArea == 0) {
                        loadRomBank(bank, 0xC000)
                    }
                    else {
                        loadRomBank(bank, 0x8000)
                    }
                }
            }
        }
    }

    fun getRegNumber(address:Int): Int {
        return when (address) {
            in 0x8000..0x9FFF -> 0
            in 0xA000..0xBFFF -> 1
            in 0xC000..0xDFFF -> 2
            else -> 3
        }
    }

    override fun loadRom() {
        if (!nes.rom.get().valid) {
            nes.ui.alert("MMC1: Invalid ROM! Unable to load.")
        } else {
            // Load PRG-ROM:
            loadRomBank(0, 0x8000)                         //   First ROM bank..
            loadRomBank(nes.rom.get().romCount - 1, 0xC000) // ..and last ROM bank.

            // Load CHR-ROM:
            loadCHRROM()

            // Load Battery RAM (if present):
            loadBatteryRam()

            // Do Reset-Interrupt:
            nes.cpu.requestIrq(IRQ_RESET)
        }
    }

    fun switchLowHighPrgRom(oldSetting: Int) {
        // Not yet
    }

    fun switch16to32() {
        // Not yet
    }

    fun switch32to16() {
        // Not yet
    }
}