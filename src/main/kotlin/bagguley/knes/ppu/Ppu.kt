package bagguley.knes.ppu

import bagguley.knes.IRQ_NMI
import bagguley.knes.Nes
import bagguley.knes.Rom
import kotlin.math.floor

class Ppu(val nes: Nes) {
    // Status flags:
    val STATUS_VRAMWRITE: Int = 4
    val STATUS_SLSPRITECOUNT: Int = 5
    val STATUS_SPRITE0HIT: Int = 6
    val STATUS_VBLANK: Int = 7

    lateinit var vramMem: IntArray
    lateinit var spriteMem: IntArray

    var vramAddress: Int = 0
    var vramTmpAddress: Int = 0
    var vramBufferedReadValue: Int = 0

    var firstWrite: Boolean = false

    var sramAddress: Int = 0
    var currentMirroring: Int = 0

    var requestEndFrame: Boolean = false
    var nmiOk: Boolean = false
    var dummyCycleToggle: Boolean = false
    var validTileData: Boolean = false

    var nmiCounter: Int = 0

    var scanlineAlreadyRendered: Boolean = false

    var f_nmiOnVblank: Int = 0
    var f_spriteSize: Int = 0
    var f_bgPatternTable: Int = 0
    var f_spPatternTable: Int = 0
    var f_addrInc: Int = 0
    var f_nTblAddress: Int = 0
    var f_color: Int = 0
    var f_spVisibility: Int = 0
    var f_bgVisibility: Int = 0
    var f_spClipping: Int = 0
    var f_bgClipping: Int = 0
    var f_dispType: Int = 0
    var cntFV: Int = 0
    var cntV: Int = 0
    var cntH: Int = 0
    var cntVT: Int = 0
    var cntHT: Int = 0
    var regFV: Int = 0
    var regV: Int = 0
    var regH: Int = 0
    var regVT: Int = 0
    var regHT: Int = 0
    var regFH: Int = 0
    var regS: Int = 0
    var curNt: Int = 0

    lateinit var attrib: IntArray
    lateinit var buffer: IntArray
    lateinit var prevBuffer: IntArray
    lateinit var bgbuffer: IntArray
    lateinit var pixrendered: IntArray
    lateinit var scantile: Array<Tile>

    var scanline: Int = 0
    var lastRenderedScanline: Int = 0
    var curX: Int = 0

    lateinit var sprX: IntArray
    lateinit var sprY: IntArray
    lateinit var sprTile: IntArray
    lateinit var sprCol: IntArray

    lateinit var vertFlip: BooleanArray
    lateinit var horiFlip: BooleanArray
    lateinit var bgPriority: BooleanArray

    var spr0HitX: Int = 0
    var spr0HitY: Int = 0

    var hitSpr0: Boolean = false
    lateinit var sprPalette: IntArray
    lateinit var imgPalette: IntArray
    lateinit var ptTile: Array<Tile>
    lateinit var ntable1: IntArray
    lateinit var nameTable: Array<NameTable>
    lateinit var vramMirrorTable: IntArray

    lateinit var palTable: PaletteTable

    // Rendering Options:
    var showSpr0Hit: Boolean = false
    var clipToTvSize: Boolean = true

    init {
        reset()
    }

    fun reset() {

        // Memory
        vramMem = IntArray(0x8000) {0}
        spriteMem = IntArray(0x100) {0}

        // VRAM I/O:
        vramAddress = 0
        vramTmpAddress = 0
        vramBufferedReadValue = 0
        firstWrite = true       // VRAM/Scroll Hi/Lo latch

        // SPR-RAM I/O:
        sramAddress = 0 // 8-bit only.

        currentMirroring = -1
        requestEndFrame = false
        nmiOk = false
        dummyCycleToggle = false
        validTileData = false
        nmiCounter = 0
        scanlineAlreadyRendered = false

        // Control Flags Register 1:
        f_nmiOnVblank = 0    // NMI on VBlank. 0=disable, 1=enable
        f_spriteSize = 0     // Sprite size. 0=8x8, 1=8x16
        f_bgPatternTable = 0 // Background Pattern Table address. 0=0x0000,1=0x1000
        f_spPatternTable = 0 // Sprite Pattern Table address. 0=0x0000,1=0x1000
        f_addrInc = 0        // PPU Address Increment. 0=1,1=32
        f_nTblAddress = 0    // Name Table Address. 0=0x2000,1=0x2400,2=0x2800,3=0x2C00

        // Control Flags Register 2:
        f_color = 0         // Background color. 0=black, 1=blue, 2=green, 4=red
        f_spVisibility = 0   // Sprite visibility. 0=not displayed,1=displayed
        f_bgVisibility = 0   // Background visibility. 0=Not Displayed,1=displayed
        f_spClipping = 0     // Sprite clipping. 0=Sprites invisible in left 8-pixel column,1=No clipping
        f_bgClipping = 0     // Background clipping. 0=BG invisible in left 8-pixel column, 1=No clipping
        f_dispType = 0       // Display type. 0=color, 1=monochrome

        // Counters:
        cntFV = 0
        cntV = 0
        cntH = 0
        cntVT = 0
        cntHT = 0

        // Registers:
        regFV = 0
        regV = 0
        regH = 0
        regVT = 0
        regHT = 0
        regFH = 0
        regS = 0

        // These are temporary variables used in rendering and sound procedures.
        // Their states outside of those procedures can be ignored.
        // TODO: the use of this is a bit weird, investigate
        curNt = 0

        // Variables used when rendering:
        attrib = IntArray(32)
        buffer = IntArray(256*240)
        prevBuffer = IntArray(256*240)
        bgbuffer = IntArray(256*240)
        pixrendered = IntArray(256*240)

        validTileData = false

        scantile = Array(32) { Tile() }

        // Initialize misc vars:
        scanline = 0
        lastRenderedScanline = -1
        curX = 0

        // Sprite data:
        sprX = IntArray(64) // X coordinate
        sprY = IntArray(64) // Y coordinate
        sprTile = IntArray(64) // Tile Index (into pattern table)
        sprCol = IntArray(64) // Upper two bits of color
        vertFlip = BooleanArray(64) // Vertical Flip
        horiFlip = BooleanArray(64) // Horizontal Flip
        bgPriority = BooleanArray(64) // Background priority
        spr0HitX = 0 // Sprite #0 hit X coordinate
        spr0HitY = 0 // Sprite #0 hit Y coordinate
        hitSpr0 = false

        // Palette data:
        sprPalette = IntArray(16)
        imgPalette = IntArray(16)

        // Create pattern table tile buffers:
        ptTile = Array<Tile>(512) {Tile()}

        // Create nametable buffers:
        // Name table data:
        ntable1 = IntArray(4)
        currentMirroring = -1
        nameTable = Array<NameTable>(4) { i -> NameTable(32, 32, "Nt$i") }

        // Initialize mirroring lookup table:
        vramMirrorTable = IntArray(0x8000) { i -> i }

        palTable = PaletteTable()
        //palTable.loadNTSCPalette()
        palTable.loadDefaultPalette()

        updateControlReg1(0)
        updateControlReg2(0)
    }

    fun setMirroring(mirroring: Int) {
        if (mirroring != currentMirroring) {

            currentMirroring = mirroring
            triggerRendering()

            // Remove mirroring:
            if (vramMirrorTable == null) {
                vramMirrorTable = IntArray(0x8000)
            }

            for (i in 0 until 0x8000) {
                vramMirrorTable[i] = i
            }

            // Palette mirroring:
            defineMirrorRegion(0x3f20,0x3f00,0x20)
            defineMirrorRegion(0x3f40,0x3f00,0x20)
            defineMirrorRegion(0x3f80,0x3f00,0x20)
            defineMirrorRegion(0x3fc0,0x3f00,0x20)

            // Additional mirroring:
            defineMirrorRegion(0x3000,0x2000,0xf00)
            defineMirrorRegion(0x4000,0x0000,0x4000)

            when (mirroring) {
                Rom.HORIZONTAL_MIRRORING -> {
                    // Horizontal mirroring.

                    ntable1[0] = 0
                    ntable1[1] = 0
                    ntable1[2] = 1
                    ntable1[3] = 1

                    defineMirrorRegion(0x2400,0x2000,0x400)
                    defineMirrorRegion(0x2c00,0x2800,0x400)
                }
                Rom.VERTICAL_MIRRORING -> {
                    // Vertical mirroring.

                    ntable1[0] = 0
                    ntable1[1] = 1
                    ntable1[2] = 0
                    ntable1[3] = 1

                    defineMirrorRegion(0x2800,0x2000,0x400)
                    defineMirrorRegion(0x2c00,0x2400,0x400)
                }
                Rom.SINGLESCREEN_MIRRORING -> {
                    // Single Screen mirroring

                    ntable1[0] = 0
                    ntable1[1] = 0
                    ntable1[2] = 0
                    ntable1[3] = 0

                    defineMirrorRegion(0x2400,0x2000,0x400)
                    defineMirrorRegion(0x2800,0x2000,0x400)
                    defineMirrorRegion(0x2c00,0x2000,0x400)
                }
                Rom.SINGLESCREEN_MIRRORING2 -> {
                    ntable1[0] = 1
                    ntable1[1] = 1
                    ntable1[2] = 1
                    ntable1[3] = 1

                    defineMirrorRegion(0x2400,0x2400,0x400)
                    defineMirrorRegion(0x2800,0x2400,0x400)
                    defineMirrorRegion(0x2c00,0x2400,0x400)
                }
                else -> {
                    // Assume Four-screen mirroring.
                    ntable1[0] = 0
                    ntable1[1] = 1
                    ntable1[2] = 2
                    ntable1[3] = 3

                }
            }
        }
    }

    // Define a mirrored area in the address lookup table.
    // Assumes the regions don't overlap.
    // The 'to' region is the region that is physically in memory.
    fun defineMirrorRegion(fromStart:Int, toStart:Int, size:Int) {
        for (i in 0 until size) {
            vramMirrorTable[fromStart + i] = toStart + i
        }
    }

    fun startVBlank() {
        // Do NMI:
        nes.cpu.requestIrq(IRQ_NMI)

        // Make sure everything is rendered:
        if (lastRenderedScanline < 239) {
            renderFramePartially(lastRenderedScanline + 1, 240 - lastRenderedScanline)
        }

        // End frame:
        endFrame()

        // Reset scanline counter:
        lastRenderedScanline = -1
    }

    fun endScanline() {
        when (scanline) {
            19 -> {
                // Dummy scanline.
                // May be variable length:
                if (dummyCycleToggle) {

                    // Remove dead cycle at end of scanline,
                    // for next scanline:
                    curX = 1
                    dummyCycleToggle = false
                }
            }

            20 -> {
                // Clear VBlank flag:
                setStatusFlag(STATUS_VBLANK, false)

                // Clear Sprite #0 hit flag:
                setStatusFlag(STATUS_SPRITE0HIT, false)
                hitSpr0 = false
                spr0HitX = -1
                spr0HitY = -1

                if (f_bgVisibility == 1 || f_spVisibility == 1) {
                    // Update counters:
                    cntFV = regFV
                    cntV = regV
                    cntH = regH
                    cntVT = regVT
                    cntHT = regHT

                    if (f_bgVisibility == 1) {
                        // Render dummy scanline:
                        renderBgScanline(false, 0)
                    }
                }

                if (f_bgVisibility == 1 && f_spVisibility == 1) {
                    // Check sprite 0 hit for first scanline:
                    checkSprite0(0)
                }

                if (f_bgVisibility == 1 || f_spVisibility == 1) {
                    // Clock mapper IRQ Counter:
                    nes.mmap.get().clockIrqCounter()
                }
            }

            261 -> {
                // Dead scanline, no rendering.
                // Set VINT:
                setStatusFlag(STATUS_VBLANK, true)
                requestEndFrame = true
                nmiCounter = 9

                // Wrap around:
                scanline = -1 // will be incremented to 0

            }

            else -> {
                if (scanline >= 21 && scanline <= 260) {

                    // Render normally:
                    if (f_bgVisibility == 1) {

                        if (!scanlineAlreadyRendered) {
                            // update scroll:
                            cntHT = regHT
                            cntH = regH
                            renderBgScanline(true, scanline + 1 - 21)
                        }
                        scanlineAlreadyRendered = false

                        // Check for sprite 0 (next scanline):
                        if (!hitSpr0 && f_spVisibility == 1) {
                            if (sprX[0] >= -7 && sprX[0] < 256 && sprY[0] + 1 <= (scanline - 20) && (sprY[0] + 1 + (if (f_spriteSize == 0) 8 else 16)) >= (scanline - 20)) {
                                if (checkSprite0(scanline - 20)) {
                                    hitSpr0 = true
                                }
                            }
                        }

                    }

                    if (f_bgVisibility == 1 || f_spVisibility == 1) {
                        // Clock mapper IRQ Counter:
                        nes.mmap.get().clockIrqCounter()
                    }
                }
            }
        }
        scanline += 1
        regsToAddress()
        cntsToAddress()
    }

    fun startFrame() {
        // Set background color:
        val bgColor: Int

        if (f_dispType == 0) {
            // Color display.
            // f_color determines color emphasis.
            // Use first entry of image palette as BG color.
            bgColor = imgPalette[0]
        }
        else {
            // Monochrome display.
            // f_color determines the bg color.
            when (f_color) {
                0 ->
                // Black
                bgColor = 0x00000
                1 ->
                // Green
                bgColor = 0x00FF00
                2 ->
                // Blue
                bgColor = 0xFF0000
                3 ->
                // Invalid. Use black.
                bgColor = 0x000000
                4 ->
                // Red
                bgColor = 0x0000FF
                else ->
                // Invalid. Use black.
                bgColor = 0x0
            }
        }

        for (i in 0 until 256*240) {
            buffer[i] = bgColor
        }

        for (i in pixrendered.indices) {
            pixrendered[i]=65
        }
    }

    fun endFrame() {
        // Draw spr#0 hit coordinates:
        if (showSpr0Hit) {
            // Spr 0 position:
            if (sprX[0] >= 0 && sprX[0] < 256 && sprY[0] >= 0 && sprY[0] < 240) {
                for (i in 0 until 256) {
                    buffer[(sprY[0] shl 8)+i] = 0xFF5555
                }
                for (i in 0 until 240) {
                    buffer[(i shl 8) + sprX[0]] = 0xFF5555
                }
            }
            // Hit position:
            if (spr0HitX >= 0 && spr0HitX < 256 && spr0HitY >= 0 && spr0HitY < 240) {
                for (i in 0 until 256) {
                    buffer[(spr0HitY shl 8)+i] = 0x55FF55
                }
                for (i in 0 until 240) {
                    buffer[(i shl 8) + spr0HitX] = 0x55FF55
                }
            }
        }

        // This is a bit lazy..
        // if either the sprites or the background should be clipped,
        // both are clipped after rendering is finished.
        if (clipToTvSize || f_bgClipping == 0 || f_spClipping == 0) {
            // Clip left 8-pixels column:
            for (y in 0 until 240) {
                for (x in 0 until 8) {
                buffer[(y shl 8) + x] = 0
            }
            }
        }

        if (clipToTvSize) {
            // Clip right 8-pixels column too:
            for (y in 0 until 240) {
                for (x in 0 until 8) {
                buffer[(y shl 8) + 255 - x] = 0
            }
            }
        }

        // Clip top and bottom 8 pixels:
        if (clipToTvSize) {
            for (y in 0 until 8) {
                for (x in 0 until 256) {
                buffer[(y shl 8) + x] = 0
                buffer[((239 - y) shl 8) + x] = 0
            }
            }
        }

        if (nes.opts.showDisplay) {
            nes.ui.writeFrame(buffer, prevBuffer)
        }
    }

    fun updateControlReg1(value:Int) {
        triggerRendering()

        f_nmiOnVblank =    (value shr 7) and 1
        f_spriteSize =     (value shr 5) and 1
        f_bgPatternTable = (value shr 4) and 1
        f_spPatternTable = (value shr 3) and 1
        f_addrInc =        (value shr 2) and 1
        f_nTblAddress =     value and 3

        regV = (value shr 1) and 1
        regH = value and 1
        regS = (value shr 4) and 1
    }

    fun updateControlReg2(value: Int) {
        triggerRendering()

        f_color =       (value shr 5) and 7
        f_spVisibility = (value shr 4) and 1
        f_bgVisibility = (value shr 3) and 1
        f_spClipping =   (value shr 2) and 1
        f_bgClipping =   (value shr 1) and 1
        f_dispType =      value and 1

        if (f_dispType == 0) {
            palTable.setEmphasis(f_color)
        }
        updatePalettes()
    }

    fun setStatusFlag(flag:Int, value:Boolean) {
        val n = 1 shl flag
        nes.cpu.mem[0x2002] = ((nes.cpu.mem[0x2002] and (255-n)) or (if (value) n else 0))
    }

    // CPU Register $2002:
    // Read the Status Register.
    fun readStatusRegister():Int {
        val tmp:Int = nes.cpu.mem[0x2002]

        // Reset scroll & VRAM Address toggle:
        firstWrite = true

        // Clear VBlank flag:
        setStatusFlag(STATUS_VBLANK, false)

        // Fetch status data:
        return tmp
    }

    // CPU Register $2003:
    // Write the SPR-RAM address that is used for sramWrite (Register 0x2004 in CPU memory map)
    fun writeSRAMAddress(address: Int) {
        sramAddress = address
    }

    // CPU Register $2004 (R):
    // Read from SPR-RAM (Sprite RAM).
    // The address should be set first.
    fun sramLoad(): Int {
        /*short tmp = sprMem.load(sramAddress)
        sramAddress++ // Increment address
        sramAddress%=0x100
        return tmp*/
        return spriteMem[sramAddress]
    }

    // CPU Register $2004 (W):
    // Write to SPR-RAM (Sprite RAM).
    // The address should be set first.
    fun sramWrite(value:Int) {
        spriteMem[sramAddress] = value
        spriteRamWriteUpdate(sramAddress, value)
        sramAddress += 1 // Increment address
        sramAddress %= 0x100
    }

    // CPU Register $2005:
    // Write to scroll registers.
    // The first write is the vertical offset, the second is the
    // horizontal offset:
    fun scrollWrite(value:Int) {
        triggerRendering()

        if (firstWrite) {
            // First write, horizontal scroll:
            regHT = (value shr 3) and 31
            regFH = value and 7
        } else {
            // Second write, vertical scroll:
            regFV = value and 7
            regVT = (value shr 3) and 31
        }
        firstWrite = !firstWrite
    }

    // CPU Register $2006:
    // Sets the adress used when reading/writing from/to VRAM.
    // The first write sets the high byte, the second the low byte.
    fun writeVRAMAddress(address: Int) {
        if (firstWrite) {
            regFV = (address shr 4) and 3
            regV = (address shr 3) and 1
            regH = (address shr 2) and 1
            regVT = (regVT and 7) or ((address and 3) shl 3)
        } else {
            triggerRendering()

            regVT = (regVT and 24) or ((address shr 5) and 7)
            regHT = address and 31

            cntFV = regFV
            cntV = regV
            cntH = regH
            cntVT = regVT
            cntHT = regHT

            checkSprite0(scanline-20)
        }

        firstWrite = !firstWrite

        // Invoke mapper latch:
        cntsToAddress()
        if (vramAddress < 0x2000) nes.mmap.get().latchAccess(vramAddress)
    }

    // CPU Register $2007(R):
    // Read from PPU memory. The address should be set first.
    fun vramLoad():Int {
        val tmp: Int

        cntsToAddress()
        regsToAddress()

        // If address is in range 0x0000-0x3EFF, return buffered values:
        if (vramAddress <= 0x3EFF) {
            tmp = vramBufferedReadValue

            // Update buffered value:
            vramBufferedReadValue = if (vramAddress < 0x2000) {
                vramMem[vramAddress]
            } else {
                mirroredLoad(vramAddress)
            }

            // Mapper latch access:
            if (vramAddress < 0x2000) {
                nes.mmap.get().latchAccess(vramAddress)
            }

            // Increment by either 1 or 32, depending on d2 of Control Register 1:
            vramAddress += (if (f_addrInc == 1) 32 else 1)

            cntsFromAddress()
            regsFromAddress()

            return tmp // Return the previous buffered value.
        } else {
            // No buffering in this mem range. Read normally.
            tmp = mirroredLoad(vramAddress)

            // Increment by either 1 or 32, depending on d2 of Control Register 1:
            vramAddress += (if (f_addrInc == 1) 32 else 1)

            cntsFromAddress()
            regsFromAddress()

            return tmp
        }
    }

    // CPU Register $2007(W):
    // Write to PPU memory. The address should be set first.
    fun vramWrite(value: Int) {
        triggerRendering()
        cntsToAddress()
        regsToAddress()

        if (vramAddress >= 0x2000) {
            // Mirroring is used.
            mirroredWrite(vramAddress, value)
        } else {
            // Write normally.
            writeMem(vramAddress, value)

            // Invoke mapper latch:
            nes.mmap.get().latchAccess(vramAddress)
        }

        // Increment by either 1 or 32, depending on d2 of Control Register 1:
        vramAddress += (if (f_addrInc == 1) 32 else 1)
        regsFromAddress()
        cntsFromAddress()
    }

    // CPU Register $4014:
    // Write 256 bytes of main memory
    // into Sprite RAM.
    fun sramDMA(value: Int) {
        val baseAddress = value * 0x100
        var data:Int
        for (i in sramAddress until 256) {
            data = nes.cpu.mem[baseAddress + i]
            spriteMem[i] = data
            spriteRamWriteUpdate(i, data)
        }

        nes.cpu.haltCycles(513)
    }

    // Updates the scroll registers from a new VRAM address.
    fun regsFromAddress() {
        var address = (vramTmpAddress shr 8) and 0xFF
        regFV = (address shr 4) and 7
        regV = (address shr 3) and 1
        regH = (address shr 2) and 1
        regVT = (regVT and 7) or ((address and 3) shl 3)

        address = vramTmpAddress and 0xFF
        regVT = (regVT and 24) or ((address shr 5) and 7)
        regHT = address and 31
    }

    // Updates the scroll registers from a new VRAM address.
    fun cntsFromAddress() {
        var address = (vramAddress shr 8) and 0xFF
        cntFV = (address shr 4) and 3
        cntV = (address shr 3) and 1
        cntH = (address shr 2) and 1
        cntVT = (cntVT and 7) or ((address and 3) shl 3)

        address = vramAddress and 0xFF
        cntVT = (cntVT and 24) or ((address shr 5) and 7)
        cntHT = address and 31
    }

    fun regsToAddress() {
        var b1  = (regFV and 7) shl 4
        b1 = b1 or ((regV and 1) shl 3)
        b1 = b1 or ((regH and 1) shl 2)
        b1 = b1 or ((regVT shr 3) and 3)

        var b2  = (regVT and 7) shl 5
        b2 = b2 or (regHT and 31)

        vramTmpAddress = ((b1 shl 8) or b2) and 0x7FFF
    }

    fun cntsToAddress() {
        var b1  = (cntFV and 7) shl 4
        b1 = b1 or ((cntV and 1) shl 3)
        b1 = b1 or ((cntH and 1) shl 2)
        b1 = b1 or ((cntVT shr 3) and 3)

        var b2  = (cntVT and 7) shl 5
        b2 = b2 or (cntHT and 31)

        vramAddress = ((b1 shl 8) or b2) and 0x7FFF
    }

    fun incTileCounter(count: Int) {
        for (i in count downTo 1 step -1) {
            cntHT += 1
            if (cntHT == 32) {
                cntHT = 0
                cntVT += 1
                if (cntVT >= 30) {
                    cntH += 1
                    if (cntH == 2) {
                        cntH = 0
                        cntV += 1
                        if (cntV == 2) {
                            cntV = 0
                            cntFV += 1
                            cntFV = cntFV and 0x7
                        }
                    }
                }
            }
        }
    }

    // Reads from memory, taking into account
    // mirroring/mapping of address ranges.
    fun mirroredLoad(address: Int) = vramMem[vramMirrorTable[address]]

    // Writes to memory, taking into account
    // mirroring/mapping of address ranges.
    fun mirroredWrite(address: Int, value: Int) {
        if (address in 0x3f00..0x3f1f) {
            // Palette write mirroring.
            when (address) {
                0x3F00, 0x3F10 -> {
                    writeMem(0x3F00,value)
                    writeMem(0x3F10,value)
                }
                0x3F04, 0x3F14 -> {
                    writeMem(0x3F04,value)
                    writeMem(0x3F14,value)
                }
                0x3F08, 0x3F18 -> {
                    writeMem(0x3F08,value)
                    writeMem(0x3F18,value)
                }
                0x3F0C, 0x3F1C -> {
                    writeMem(0x3F0C,value)
                    writeMem(0x3F1C,value)
                }
                else -> {
                    writeMem(address,value)
                }
            }
        } else {
            // Use lookup table for mirrored address:
            if (address < vramMirrorTable.size) {
                writeMem(vramMirrorTable[address], value)
            } else {
                // FIXME
                nes.ui.alert("Invalid VRAM address: " + Integer.toHexString(address))
            }
        }
    }

    fun triggerRendering() {
        if (scanline in 21..260) {
            // Render sprites, and combine:
            renderFramePartially(lastRenderedScanline + 1, scanline - 21 - lastRenderedScanline)

            // Set last rendered scanline:
            lastRenderedScanline = scanline - 21
        }
    }

    fun renderFramePartially(startScan:Int, scanCount: Int) {
        if (f_spVisibility == 1) {
            renderSpritesPartially(startScan, scanCount, true)
        }

        if (f_bgVisibility == 1) {
            val si = startScan shl 8
            var ei = (startScan+scanCount) shl 8
            if (ei > 0xF000) {
                ei = 0xF000
            }

            for (destIndex in si until ei) {
                if (pixrendered[destIndex] > 0xFF) {
                    buffer[destIndex] = bgbuffer[destIndex]
                }
            }
        }

        if (f_spVisibility == 1) {
            renderSpritesPartially(startScan, scanCount, false)
        }

        validTileData = false
    }

    fun renderBgScanline(useBgBuffer: Boolean, scan: Int) {
        val baseTile:Int = if (regS == 0) 0 else 256
        var destIndex = (scan shl 8) - regFH

        curNt = ntable1[cntV + cntV + cntH]

        cntHT = regHT
        cntH = regH

        curNt = ntable1[cntV + cntV + cntH]

        if (scan < 240 && (scan - cntFV) >= 0){

            val tscanoffset = cntFV shl 3
            val targetBuffer = if (useBgBuffer) bgbuffer else buffer

            var t: Tile
            var tpix: IntArray
            var att = 0
            var col = 0

            for (tile in 0 until 32) {
                if (scan >= 0) {
                    // Fetch tile & attrib data:
                    if (validTileData) {
                        // Get data from array:
                        t = scantile[tile]
                        tpix = t.pix
                        att = attrib[tile]
                    } else {
                        // Fetch data:
                        t = ptTile[baseTile + nameTable[curNt].getTileIndex(cntHT,cntVT)]
                        tpix = t.pix
                        att = nameTable[curNt].getAttrib(cntHT,cntVT)
                        scantile[tile] = t
                        attrib[tile] = att
                    }

                    // Render tile scanline:
                    var sx = 0
                    val x = (tile shl 3) - regFH

                    if (x > -8) {
                        if (x < 0) {
                            destIndex -= x
                            sx = -x
                        }
                        if (t.opaque[cntFV]) {
                            for (sxI in sx until 8) {
                                targetBuffer[destIndex] = imgPalette[tpix[tscanoffset + sxI] + att]
                                pixrendered[destIndex] = pixrendered[destIndex] or 256
                                destIndex += 1
                            }
                        } else {
                            for (sxI in sx until 8) {
                                col = tpix[tscanoffset + sxI]
                                if (col != 0) {
                                    targetBuffer[destIndex] = imgPalette[col + att]
                                    pixrendered[destIndex] = pixrendered[destIndex] or 256
                                }
                                destIndex += 1
                            }
                        }
                    }

                }

                // Increase Horizontal Tile Counter:
                cntHT += 1
                if (cntHT == 32) {
                    cntHT = 0
                    cntH += 1
                    cntH %= 2
                    curNt = ntable1[(cntV shl 1) + cntH]
                }


            }

            // Tile data for one row should now have been fetched,
            // so the data in the array is valid.
            validTileData = true
        }

        // update vertical scroll:
        cntFV += 1
        if (cntFV == 8) {
            cntFV = 0
            cntVT += 1
            if (cntVT == 30) {
                cntVT = 0
                cntV += 1
                cntV %= 2
                curNt = ntable1[(cntV shl 1) + cntH]
            } else if (cntVT==32) {
                cntVT = 0
            }

            // Invalidate fetched data:
            validTileData = false
        }
    }

    fun renderSpritesPartially(startscan: Int, scancount: Int, bgPri: Boolean) {
        if (f_spVisibility == 1) {

            for (i in 0 until 64) {
                if (bgPriority[i] == bgPri && sprX[i] >= 0 && sprX[i] < 256 && sprY[i] + 8 >= startscan && sprY[i] < startscan+scancount) {
                    // Show sprite.
                    if (f_spriteSize == 0) {
                        // 8x8 sprites

                        var srcy1 = 0
                        var srcy2 = 8

                        if (sprY[i] < startscan) {
                            srcy1 = startscan - sprY[i] - 1
                        }

                        if (sprY[i]+8 > startscan+scancount) {
                            srcy2 = startscan+scancount-sprY[i]+1
                        }

                        if (f_spPatternTable == 0) {
                            ptTile[sprTile[i]].render(buffer, 0, srcy1, 8, srcy2, sprX[i], sprY[i]+1, sprCol[i],
                                sprPalette, horiFlip[i], vertFlip[i], i, pixrendered)
                        } else {
                            ptTile[sprTile[i]+256].render(buffer, 0, srcy1, 8, srcy2, sprX[i], sprY[i]+1, sprCol[i],
                                sprPalette, horiFlip[i], vertFlip[i], i, pixrendered)
                        }
                    } else {
                        // 8x16 sprites
                        var top = sprTile[i]
                        if ((top and 1) != 0) {
                            top = sprTile[i]-1+256
                        }

                        var srcy1 = 0
                        var srcy2 = 8

                        if (sprY[i]<startscan) {
                            srcy1 = startscan - sprY[i]-1
                        }

                        if (sprY[i]+8 > startscan+scancount) {
                            srcy2 = startscan+scancount-sprY[i]
                        }

                        ptTile[top + (if(vertFlip[i]) 1 else 0)].render(
                            buffer,
                            0,
                            srcy1,
                            8,
                            srcy2,
                            sprX[i],
                            sprY[i] + 1,
                            sprCol[i],
                            sprPalette,
                            horiFlip[i],
                            vertFlip[i],
                            i,
                            pixrendered
                        )

                        srcy1 = 0
                        srcy2 = 8

                        if (sprY[i]+8<startscan) {
                            srcy1 = startscan - (sprY[i]+8+1)
                        }

                        if (sprY[i]+16 > startscan+scancount) {
                            srcy2 = startscan+scancount-(sprY[i]+8)
                        }

                        ptTile[top + (if (vertFlip[i]) 0 else 1)].render(
                            buffer,
                            0,
                            srcy1,
                            8,
                            srcy2,
                            sprX[i],
                            sprY[i]+1+8,
                            sprCol[i],
                            sprPalette,
                            horiFlip[i],
                            vertFlip[i],
                            i,
                            pixrendered
                        )
                    }
                }
            }
        }
    }

    fun checkSprite0(scan: Int) :Boolean {
        spr0HitX = -1
        spr0HitY = -1

        var toffset: Int
        var tIndexAdd: Int = (if (f_spPatternTable == 0) 0 else 256)
        var x: Int
        val y: Int
        val t: Tile
        var bufferIndex: Int
        val col: Int
        val bgPri: Boolean

        x = sprX[0]
        y = sprY[0] + 1

        if (f_spriteSize == 0) {
            // 8x8 sprites.

            // Check range:
            if (y <= scan && y + 8 > scan && x >= -7 && x < 256) {

                // Sprite is in range.
                // Draw scanline:
                t = ptTile[sprTile[0] + tIndexAdd]
                col = sprCol[0]
                bgPri = bgPriority[0]

                if (vertFlip[0]) {
                    toffset = 7 - (scan -y)
                }
                else {
                    toffset = scan - y
                }
                toffset *= 8

                bufferIndex = scan * 256 + x
                if (horiFlip[0]) {
                    for (i in 7 downTo 0 step -1) {
                        if (x in 0..255) {
                            if (bufferIndex in 0..61439 && pixrendered[bufferIndex] != 0 ) {
                                if (t.pix[toffset + i] != 0) {
                                    spr0HitX = bufferIndex % 256
                                    spr0HitY = scan
                                    return true
                                }
                            }
                        }
                        x += 1
                        bufferIndex += 1
                    }
                }
                else {
                    for (i in 0 until 8) {
                        if (x in 0..255) {
                            if (bufferIndex in 0..61439 && pixrendered[bufferIndex] != 0) {
                                if (t.pix[toffset+i] != 0) {
                                    spr0HitX = bufferIndex % 256
                                    spr0HitY = scan
                                    return true
                                }
                            }
                        }
                        x += 1
                        bufferIndex += 1
                    }
                }
            }
        }
        else {
            // 8x16 sprites:

            // Check range:
            if (y <= scan && y + 16 > scan && x >= -7 && x < 256) {
                // Sprite is in range.
                // Draw scanline:

                if (vertFlip[0]) {
                    toffset = 15 - (scan - y)
                } else {
                    toffset = scan - y
                }

                if (toffset<8) {
                    // first half of sprite.
                    t = ptTile[sprTile[0] + (if (vertFlip[0]) 1 else 0) + (if ((sprTile[0] and 1) != 0) 255 else 0)]
                } else {
                    // second half of sprite.
                    t = ptTile[sprTile[0] + (if (vertFlip[0]) 0 else 1) + (if ((sprTile[0] and 1) != 0) 255 else 0)]
                    if (vertFlip[0]) {
                        toffset = 15 - toffset
                    }
                    else {
                        toffset -= 8
                    }
                }
                toffset *= 8
                col = sprCol[0]
                bgPri = bgPriority[0]

                bufferIndex = scan*256+x
                if (horiFlip[0]) {
                    for (i in 7 downTo  1 step -1) { // WB - was 7 until 0
                        if (x in 0..255) {
                            if (bufferIndex in 0..61439 && pixrendered[bufferIndex]!=0) {
                                if (t.pix[toffset+i] != 0) {
                                    spr0HitX = bufferIndex % 256
                                    spr0HitY = scan
                                    return true
                                }
                            }
                        }
                        x += 1
                        bufferIndex += 1
                    }
                }
                else {
                    for (i in 0 until 8) {
                        if (x in 0..255) {
                            if (bufferIndex in 0..61439 && pixrendered[bufferIndex] !=0) {
                                if (t.pix[toffset + i] != 0) {
                                    spr0HitX = bufferIndex % 256
                                    spr0HitY = scan
                                    return true
                                }
                            }
                        }
                        x += 1
                        bufferIndex += 1
                    }

                }

            }

        }

        return false
    }

    // This will write to PPU memory, and
    // update internally buffered data
    // appropriately.
    fun writeMem(address:Int, value:Int) {
        vramMem[address] = value

        // Update internally buffered data:
        if (address < 0x2000) {
            vramMem[address] = value
            patternWrite(address,value)
        }
        else if (address in 0x2000..0x23bf) {
            nameTableWrite(ntable1[0], address - 0x2000, value)
        }
        else if (address in 0x23c0..0x23ff) {
            attribTableWrite(ntable1[0],address-0x23c0,value)
        }
        else if (address in 0x2400..0x27bf) {
            nameTableWrite(ntable1[1],address-0x2400,value)
        }
        else if (address in 0x27c0..0x27ff) {
            attribTableWrite(ntable1[1],address-0x27c0,value)
        }
        else if (address in 0x2800..0x2bbf) {
            nameTableWrite(ntable1[2],address-0x2800,value)
        }
        else if (address in 0x2bc0..0x2bff) {
            attribTableWrite(ntable1[2],address-0x2bc0,value)
        }
        else if (address in 0x2c00..0x2fbf) {
            nameTableWrite(ntable1[3], address - 0x2c00, value)
        }
        else if (address in 0x2fc0..0x2fff) {
            attribTableWrite(ntable1[3], address - 0x2fc0, value)
        }
        else if (address in 0x3f00..0x3f1f) {
            updatePalettes()
        }
    }

    // Reads data from $3f00 to $f20
    // into the two buffered palettes.
    fun updatePalettes() {
        for (i in 0 until 16) {
            if (f_dispType == 0) {
                imgPalette[i] = palTable.getEntry(vramMem[0x3f00 + i] and 63)
            }
            else {
                imgPalette[i] = palTable.getEntry(vramMem[0x3f00 + i] and 32)
            }
        }
        for (i in 0 until 16) {
            if (f_dispType == 0) {
                sprPalette[i] = palTable.getEntry(vramMem[0x3f10 + i] and 63)
            }
            else {
                sprPalette[i] = palTable.getEntry(vramMem[0x3f10 + i] and 32)
            }
        }
    }

    // Updates the internal pattern
    // table buffers with this new byte.
    // In vNES, there is a version of this with 4 arguments which isn't used.
    fun patternWrite(address:Int, value:Int) {
        var tileIndex:Int = floor(address.toDouble() / 16).toInt()
        var leftOver = address%16
        if (leftOver < 8) {
            ptTile[tileIndex].setScanline(leftOver, value, vramMem[address+8])
        }
        else {
            ptTile[tileIndex].setScanline(leftOver-8, vramMem[address-8], value)
        }
    }

    // Updates the internal name table buffers
    // with this new byte.
    fun nameTableWrite(index: Int, address:Int, value:Int) {
        nameTable[index].tile[address] = value

        // Update Sprite #0 hit:
        //updateSpr0Hit()
        checkSprite0(scanline-20)
    }

    // Updates the internal pattern
    // table buffers with this new attribute
    // table byte.
    fun attribTableWrite(index: Int, address: Int, value: Int) = nameTable[index].writeAttrib(address,value)

    // Updates the internally buffered sprite
    // data with this new byte of info.
    fun spriteRamWriteUpdate(address:Int, value:Int) {
        var tIndex:Int = floor(address.toDouble() / 4).toInt()

        if (tIndex == 0) {
            //updateSpr0Hit()
            checkSprite0(scanline - 20)
        }

        if (address % 4 == 0) {
            // Y coordinate
            sprY[tIndex] = value
        }
        else if (address % 4 == 1) {
            // Tile index
            sprTile[tIndex] = value
        }
        else if (address % 4 == 2) {
            // Attributes
            vertFlip[tIndex] = ((value and 0x80) != 0)
            horiFlip[tIndex] = ((value and 0x40) != 0 )
            bgPriority[tIndex] = ((value and 0x20) != 0)
            sprCol[tIndex] = (value and 3) shl 2

        }
        else if (address % 4 == 3) {
            // X coordinate
            sprX[tIndex] = value
        }
    }

    fun doNMI() {
        // Set VBlank flag:
        setStatusFlag(STATUS_VBLANK, true)
        //nes.getCpu().doNonMaskableInterrupt();
        nes.cpu.requestIrq(IRQ_NMI)
    }
}