package bagguley.knes

abstract class Mapper(var nes: Nes) {
    var joy1StrobeState: Int = 0
    var joy2StrobeState: Int = 0
    var joypadLastWrite: Int = 0

    var mousePressed: Boolean = false
    var mouseX: Int = 0
    var mouseY: Int = 0

    open fun reset() {
        joy1StrobeState = 0
        joy2StrobeState = 0
        joypadLastWrite = 0

        mousePressed = false
        mouseX = 0
        mouseY = 0
    }

    open fun write(address: Int, value: Int) {
        if (address < 0x2000) {
            // Mirroring of RAM:
            nes.cpu.mem[address and 0x7FF] = value

        } else if (address > 0x4017) {
            nes.cpu.mem[address] = value
            if (address in 0x6000..0x7fff) {
                // Write to SaveRAM. Store in file:
                // TODO: not yet
                //if(nes.rom!=null)
                //    nes.rom.writeBatteryRam(address,value)
            }
        } else if (address in 0x2008..0x3fff) {
            regWrite(0x2000 + (address and 0x7), value)
        } else {
            regWrite(address, value)
        }
    }

    abstract fun writeLow(address: Int, value: Int)
    abstract fun load(addr: Int): Int
    abstract fun regLoad(address: Int): Int
    abstract fun regWrite(address: Int, value: Int)
    abstract fun joy1Read(): Int
    abstract fun joy2Read(): Int
    abstract fun loadRom()
    abstract fun loadPRGROM()
    abstract fun loadCHRROM()
    abstract fun loadBatteryRam()
    abstract fun loadRomBank(bank: Int, address: Int)
    abstract fun loadVromBank(bank: Int, address: Int)
    abstract fun load32kRomBank(bank: Int, address: Int)
    abstract fun load8kVromBank(bank4kStart: Int, address:Int)
    abstract fun load1kVromBank(bank1k: Int, address: Int)
    abstract fun load2kVromBank(bank2k: Int, address: Int)
    abstract fun load8kRomBank(bank8k: Int, address: Int)
    abstract fun clockIrqCounter()
    abstract fun latchAccess(address: Int)
}