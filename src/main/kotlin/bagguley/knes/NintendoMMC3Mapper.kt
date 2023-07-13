package bagguley.knes

class NintendoMMC3Mapper(nes: Nes): DirectAccessMapper(nes) {
    companion object {
        const val CMD_SEL_2_1K_VROM_0000 = 0
        const val CMD_SEL_2_1K_VROM_0800 = 1
        const val CMD_SEL_1K_VROM_1000 = 2
        const val CMD_SEL_1K_VROM_1400 = 3
        const val CMD_SEL_1K_VROM_1800 = 4
        const val CMD_SEL_1K_VROM_1C00 = 5
        const val CMD_SEL_ROM_PAGE1 = 6
        const val CMD_SEL_ROM_PAGE2 = 7
    }

    var command:Int = 0
    var prgAddressSelect:Int = 0
    var chrAddressSelect:Int = 0
    var irqCounter:Int = 0
    var irqLatchValue:Int = 0
    var irqEnable:Int = 0
    var prgAddressChanged:Boolean = false

    override fun write(address:Int, value:Int) {
        // Writes to addresses other than MMC registers are handled by NoMapper.
        if (address < 0x8000) {
            super.write(address, value)
        } else {
            when(address) {
                0x8000 -> {
                    // Command/Address Select register
                    command = value and 7
                    val tmp = (value shr 6) and 1
                    if (tmp != prgAddressSelect) {
                        prgAddressChanged = true
                    }
                    prgAddressSelect = tmp
                    chrAddressSelect = (value shr 7) and 1
                }
                0x8001 -> {
                    // Page number for command
                    executeCommand(command, value)
                }
                0xA000 -> {
                    // Mirroring select
                    if ((value and 1) != 0) {
                        nes.ppu.setMirroring(Rom.HORIZONTAL_MIRRORING)
                    }
                    else {
                        nes.ppu.setMirroring(Rom.VERTICAL_MIRRORING)
                    }
                }
                0xA001 -> {
                    // SaveRAM Toggle
                    // TODO
                    //nes.getRom().setSaveState((value&1)!=0)
                }
                0xC000 -> {
                    // IRQ Counter register
                    irqCounter = value
                    //nes.ppu.mapperIrqCounter = 0
                }
                0xC001 -> {
                    // IRQ Latch register
                    irqLatchValue = value
                }
                0xE000 -> {
                    // IRQ Control Reg 0 (disable)
                    //irqCounter = irqLatchValue;
                    irqEnable = 0
                }
                0xE001 -> {
                    // IRQ Control Reg 1 (enable)
                    irqEnable = 1
                }
                else -> {
                    // Not a MMC3 register.
                    // The game has probably crashed,
                    // since it tries to write to ROM..
                    // IGNORE.
                }
            }
        }
    }

    fun executeCommand(cmd:Int, arg:Int) {
        when(cmd) {
            CMD_SEL_2_1K_VROM_0000 -> {
                // Select 2 1KB VROM pages at 0x0000:
                if (chrAddressSelect == 0) {
                    load1kVromBank(arg, 0x0000)
                    load1kVromBank(arg + 1, 0x0400)
                }
                else {
                    load1kVromBank(arg, 0x1000)
                    load1kVromBank(arg + 1, 0x1400)
                }
            }

            CMD_SEL_2_1K_VROM_0800 -> {
                // Select 2 1KB VROM pages at 0x0800:
                if (chrAddressSelect == 0) {
                    load1kVromBank(arg, 0x0800)
                    load1kVromBank(arg + 1, 0x0C00)
                }
                else {
                    load1kVromBank(arg, 0x1800)
                    load1kVromBank(arg + 1, 0x1C00)
                }
            }

            CMD_SEL_1K_VROM_1000 -> {
                // Select 1K VROM Page at 0x1000:
                if (chrAddressSelect == 0) {
                    load1kVromBank(arg, 0x1000)
                }
                else {
                    load1kVromBank(arg, 0x0000)
                }
            }

            CMD_SEL_1K_VROM_1400 -> {
                // Select 1K VROM Page at 0x1400:
                if (chrAddressSelect == 0) {
                    load1kVromBank(arg, 0x1400)
                }
                else {
                    load1kVromBank(arg, 0x0400)
                }
            }

            CMD_SEL_1K_VROM_1800 -> {
                // Select 1K VROM Page at 0x1800:
                if (chrAddressSelect == 0) {
                    load1kVromBank(arg, 0x1800)
                }
                else {
                    load1kVromBank(arg, 0x0800)
                }
            }

            CMD_SEL_1K_VROM_1C00 -> {
                // Select 1K VROM Page at 0x1C00:
                if (chrAddressSelect == 0) {
                    load1kVromBank(arg, 0x1C00)
                }else {
                    load1kVromBank(arg, 0x0C00)
                }
            }

            CMD_SEL_ROM_PAGE1 -> {
                if (prgAddressChanged) {
                    // Load the two hardwired banks:
                    if (prgAddressSelect == 0) {
                        load8kRomBank(((nes.rom.get().romCount - 1) * 2), 0xC000)
                    }
                    else {
                        load8kRomBank(((nes.rom.get().romCount - 1) * 2), 0x8000)
                    }
                    prgAddressChanged = false
                }

                // Select first switchable ROM page:
                if (prgAddressSelect == 0) {
                    load8kRomBank(arg, 0x8000);
                }
                else {
                    load8kRomBank(arg, 0xC000);
                }
            }

            CMD_SEL_ROM_PAGE2 -> {
                // Select second switchable ROM page:
                load8kRomBank(arg, 0xA000);

                // hardwire appropriate bank:
                if (prgAddressChanged) {
                    // Load the two hardwired banks:
                    if (prgAddressSelect == 0) {
                        load8kRomBank(((nes.rom.get().romCount - 1) * 2), 0xC000)
                    }
                    else {
                        load8kRomBank(((nes.rom.get().romCount - 1) * 2), 0x8000)
                    }
                    prgAddressChanged = false;
                }
            }
        }
    }

    override fun loadRom() {
        if (!nes.rom.get().valid) {
            nes.ui.alert("MMC3: Invalid ROM! Unable to load.");
        } else {
            // Load hardwired PRG banks (0xC000 and 0xE000):
            load8kRomBank(((nes.rom.get().romCount - 1) * 2), 0xC000)
            load8kRomBank(((nes.rom.get().romCount - 1) * 2) + 1, 0xE000)

            // Load swappable PRG banks (0x8000 and 0xA000):
            load8kRomBank(0, 0x8000);
            load8kRomBank(1, 0xA000);

            // Load CHR-ROM:
            loadCHRROM();

            // Load Battery RAM (if present):
            loadBatteryRam();

            // Do Reset-Interrupt:
            nes.cpu.requestIrq(IRQ_RESET);
        }
    }

    override fun clockIrqCounter() {
        if (irqEnable == 1) {
            irqCounter -= 1
            if (irqCounter < 0) {
                // Trigger IRQ:
                //nes.getCpu().doIrq();
                nes.cpu.requestIrq(IRQ_NORMAL)
                irqCounter = irqLatchValue
            }
        }
    }
}