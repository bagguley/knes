package bagguley.knes

import java.lang.RuntimeException
import kotlin.math.floor

open class DirectAccessMapper(nes: Nes) : Mapper(nes) {
    override fun writeLow(address: Int, value: Int) {
        if (address < 0x2000) {
            // Mirroring of RAM:
            nes.cpu.mem[address and 0x7FF] = value
        } else if (address > 0x4017) {
            nes.cpu.mem[address] = value
        } else if (address in 0x2008..0x3fff) {
            regWrite(0x2000 + (address and 0x7), value)
        } else {
            regWrite(address, value)
        }
    }

    override fun load(addr: Int): Int {
        // Wrap around:
        val address = addr and 0xFFFF

        // Check address range:
        return if (address > 0x4017) {
            // ROM:
            nes.cpu.mem[address]
        } else if (address >= 0x2000) {
            // I/O Ports.
            regLoad(address)
        } else {
            // RAM (mirrored)
            nes.cpu.mem[address and 0x7FF]
        }
    }

    override fun regLoad(address: Int): Int {

        val result: Int = when (address shr 12) { // use fourth nibble (0xF000)
            0 -> 0
            1 -> 0
            2, 3 ->
            // PPU Registers
            when (address and 0x7) {
                0x0 ->
                // 0x2000:
                // PPU Control Register 1.
                // (the value is stored both
                // in main memory and in the
                // PPU as flags):
                // (not in the real NES)
                nes.cpu.mem[0x2000]

                0x1 ->
                // 0x2001:
                // PPU Control Register 2.
                // (the value is stored both
                // in main memory and in the
                // PPU as flags):
                // (not in the real NES)
                nes.cpu.mem[0x2001]

                0x2 ->
                // 0x2002:
                // PPU Status Register.
                // The value is stored in
                // main memory in addition
                // to as flags in the PPU.
                // (not in the real NES)
                nes.ppu.readStatusRegister()

                0x3 -> 0

                0x4 ->
                // 0x2004:
                // Sprite Memory read.
                nes.ppu.sramLoad()
                0x5 -> 0

                0x6 -> 0

                0x7 ->
                // 0x2007:
                // VRAM read:
                nes.ppu.vramLoad()

                else -> throw RuntimeException("Bad address - regLoad")
            }
            4 ->
            // Sound+Joypad registers
            when (address - 0x4015) {
                0 ->
                // 0x4015:
                // Sound channel enable, DMC Status
                nes.papu.readReg(address)

                1 ->
                // 0x4016:
                // Joystick 1 + Strobe
                joy1Read()

                2 ->
                // 0x4017:
                // Joystick 2 + Strobe
                if (mousePressed) {

                    // Check for white pixel nearby:
                    val sx = 0.coerceAtLeast(mouseX - 4)
                    val ex = 256.coerceAtMost(mouseX + 4)
                    val sy = 0.coerceAtLeast(mouseY - 4)
                    val ey = 240.coerceAtMost(mouseY + 4)
                    var w = 0

                    for (y in sy until ey) {
                        var breakSimHack = false
                        for (x in sx until ex) {
                            if (!breakSimHack) {
                                if (nes.ppu.buffer[(y shl 8) + x] == 0xFFFFFF) {
                                    w = w or (0x1 shl 3)
                                    nes.ui.debug("Clicked on white!")
                                    //break
                                    breakSimHack = true
                                }
                            }
                        }
                    }

                    w = w or (if (mousePressed) (0x1 shl 4) else 0)
                    (joy2Read() or w) and 0xFFFF
                }
                else {
                    joy2Read()
                }
                else -> throw RuntimeException("Bad address 2 regLoad")
            }
            else -> throw RuntimeException("Bad address 3 regLoad")
        }

        return result
    }

    override fun regWrite(address: Int, value: Int) {
        when (address) {
            0x2000 -> {
                // PPU Control register 1
                nes.cpu.mem[address] = value
                nes.ppu.updateControlReg1(value)
            }

            0x2001 -> {
                // PPU Control register 2
                nes.cpu.mem[address] = value
                nes.ppu.updateControlReg2(value)
            }

            0x2003 ->
            // Set Sprite RAM address:
            nes.ppu.writeSRAMAddress(value)

            0x2004 ->
            // Write to Sprite RAM:
            nes.ppu.sramWrite(value)

            0x2005 ->
            // Screen Scroll offsets:
            nes.ppu.scrollWrite(value)

            0x2006 ->
            // Set VRAM address:
            nes.ppu.writeVRAMAddress(value)

            0x2007 ->
            // Write to VRAM:
            nes.ppu.vramWrite(value)

            0x4014 ->
            // Sprite Memory DMA Access
            nes.ppu.sramDMA(value)

            0x4015 ->
            // Sound Channel Switch, DMC Status
            nes.papu.writeReg(address, value)

            0x4016 -> {
            // Joystick 1 + Strobe
                if ((value and 1) == 0 && (joypadLastWrite and 1) == 1) {
                    joy1StrobeState = 0
                    joy2StrobeState = 0
                }
                joypadLastWrite = value
            }

            0x4017 ->
            // Sound channel frame sequencer:
            nes.papu.writeReg(address, value)

            else ->
            // Sound registers
            ////System.out.println("write to sound reg")
            if (address in 0x4000..0x4017) {
                nes.papu.writeReg(address,value)
            }
        }
    }

    override fun joy1Read(): Int {
        val ret: Int = when (joy1StrobeState) {
            0,1,2,3,4,5,6,7 -> nes.keyboard.state[joy1StrobeState]
            8,9,10,11,12,13,14,15,16,17,18 -> 0
            19 -> 1
            else -> 0
        }

        joy1StrobeState += 1
        if (joy1StrobeState == 24) {
            joy1StrobeState = 0
        }

        return ret
    }

    override fun joy2Read(): Int {
        val ret = when (joy2StrobeState) {
            in 0..7 -> nes.keyboard.state2[joy2StrobeState]
            in 8..18 -> 0
            19 -> 1
            else -> 0
        }

        joy2StrobeState += 1
        if (joy2StrobeState == 24) {
            joy2StrobeState = 0
        }

        return ret
    }

    override fun loadRom() {
        if (!nes.rom.get().valid || nes.rom.get().romCount < 1) {
            nes.ui.alert("NoMapper: Invalid ROM! Unable to load.")
        } else {
            // Load ROM into memort
            loadPRGROM()
            // Load CHR-ROM
            loadCHRROM()
            // Load Battery RAM (if present)
            loadBatteryRam()
            // Reset IRQ
            // nes.cpu.doResetInterrupt()
            nes.cpu.requestIrq(IRQ_RESET)
        }
    }

    override fun loadPRGROM() {
        if (nes.rom.get().romCount > 1) {
            // Load the two first banks into memory.
            loadRomBank(0, 0x8000)
            loadRomBank(1, 0xC000)
        } else {
            // Load the one bank into both memory locations:
            loadRomBank(0, 0x8000)
            loadRomBank(0, 0xC000)
        }
    }

    override fun loadCHRROM() {
        ////System.out.println("Loading CHR ROM..")
        if (nes.rom.get().vromCount > 0) {
            if (nes.rom.get().vromCount == 1) {
                loadVromBank(0,0x0000)
                loadVromBank(0,0x1000)
            } else {
                loadVromBank(0,0x0000)
                loadVromBank(1,0x1000)
            }
        } else {
            //System.out.println("There aren't any CHR-ROM banks..")
        }
    }

    override fun loadBatteryRam() {
        /* I think this is broken - WB
         if (nes.rom.get().batteryRam) {
            var ram = nes.rom.get().batteryRam
            if (ram != null && ram.length == 0x2000) {
              // Load Battery RAM into memory:
              Utils.copyArrayElements(ram, 0, nes.cpu.mem, 0x6000, 0x2000)
            }
          }
          */
    }

    override fun loadRomBank(bank: Int, address: Int) {
        // Loads a ROM bank into the specified address.
        val bankMod = bank % nes.rom.get().romCount
        //var data = nes.rom.rom[bank]
        //cpuMem.write(address,data,data.length)
        Utils.copyArrayElements(nes.rom.get().rom[bankMod], 0, nes.cpu.mem, address, 16384)
    }

    override fun loadVromBank(bank: Int, address: Int) {
        if (nes.rom.get().vromCount != 0) {
            nes.ppu.triggerRendering()

            Utils.copyArrayElements(nes.rom.get().vrom[bank % nes.rom.get().vromCount],
                0, nes.ppu.vramMem, address, 4096)

            val vromTile = nes.rom.get().vromTile[bank % nes.rom.get().vromCount]
            Utils.copyArrayElements(vromTile, 0, nes.ppu.ptTile, address shr 4, 256)
        }
    }

    override fun load32kRomBank(bank: Int, address: Int) {
        loadRomBank((bank*2) % nes.rom.get().romCount, address)
        loadRomBank((bank*2+1) % nes.rom.get().romCount, address+16384)
    }

    override fun load8kVromBank(bank4kStart: Int, address:Int) {
        if (nes.rom.get().vromCount != 0) {
            nes.ppu.triggerRendering()

            loadVromBank((bank4kStart) % nes.rom.get().vromCount, address)
            loadVromBank((bank4kStart + 1) % nes.rom.get().vromCount, address + 4096)
        }
    }

    override fun load1kVromBank(bank1k: Int, address: Int) {
        if (nes.rom.get().vromCount != 0) {
            nes.ppu.triggerRendering()

            val bank4k: Int = floor(bank1k / 4.0).toInt() % nes.rom.get().vromCount
            val bankoffset = (bank1k % 4) * 1024
            Utils.copyArrayElements(nes.rom.get().vrom[bank4k], 0, nes.ppu.vramMem, bankoffset, 1024)

            // Update tiles:
            var vromTile = nes.rom.get().vromTile[bank4k]
            val baseIndex = address shr 4
            for (i in 0 until 64) {
                nes.ppu.ptTile[baseIndex+i] = vromTile[((bank1k%4) shl 6) + i]
            }
        }
    }

    override fun load2kVromBank(bank2k: Int, address: Int) {
        if (nes.rom.get().vromCount != 0) {
            nes.ppu.triggerRendering()

            val bank4k = floor(bank2k / 2.0).toInt() % nes.rom.get().vromCount
            val bankoffset = (bank2k % 2) * 2048
            Utils.copyArrayElements(nes.rom.get().vrom[bank4k], bankoffset, nes.ppu.vramMem, address, 2048)

            // Update tiles:
            val vromTile = nes.rom.get().vromTile[bank4k]
            val baseIndex = address shr 4
            for (i in 0 until 128) {
                nes.ppu.ptTile[baseIndex+i] = vromTile[((bank2k%2) shl 7) + i]
            }
        }
    }

    override fun load8kRomBank(bank8k: Int, address: Int) {
        var bank16k = floor(bank8k / 2.0).toInt() % nes.rom.get().romCount
        var offset = (bank8k % 2) * 8192

        //nes.cpu.mem.write(address,nes.rom.rom[bank16k],offset,8192)
        Utils.copyArrayElements(nes.rom.get().rom[bank16k], offset, nes.cpu.mem, address, 8192)
    }

    override fun clockIrqCounter() {
        // Does nothing. This is used by the MMC3 mapper.
    }

    override fun latchAccess(address:Int) {
        // Does nothing. This is used by MMC2.
    }
}