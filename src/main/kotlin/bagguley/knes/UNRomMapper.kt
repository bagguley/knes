package bagguley.knes

class UNRomMapper(nes: Nes) : DirectAccessMapper(nes)  {
    override fun write(address:Int, value:Int) {
        // Writes to addresses other than MMC registers are handled by NoMapper.
        if (address < 0x8000) {
            super.write(address, value)
        } else {
            // This is a ROM bank select command.
            // Swap in the given ROM bank at 0x8000:
            loadRomBank(value, 0x8000)
        }
    }

    override fun loadRom() {
        if (!nes.rom.get().valid) {
            nes.ui.alert("UNROM: Invalid ROM! Unable to load.")
        } else {
            // Load PRG-ROM:
            loadRomBank(0, 0x8000)
            loadRomBank(nes.rom.get().romCount - 1, 0xC000)

            // Load CHR-ROM:
            loadCHRROM()

            // Do Reset-Interrupt:
            nes.cpu.requestIrq(IRQ_RESET)
        }
    }
}