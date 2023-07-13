package bagguley.knes

import bagguley.knes.ppu.Tile
import java.util.*

class Rom(val nes: Nes) {
    companion object {
        const val VERTICAL_MIRRORING = 0
        const val HORIZONTAL_MIRRORING = 1
        const val FOURSCREEN_MIRRORING = 2
        const val SINGLESCREEN_MIRRORING = 3
        const val SINGLESCREEN_MIRRORING2 = 4
        const val SINGLESCREEN_MIRRORING3 = 5
        const val SINGLESCREEN_MIRRORING4 = 6
        const val CHRROM_MIRRORING = 7

        const val ROM_SIZE = 16384
        const val VROM_SIZE = 4096
        const val VROM_TILE_SIZE = 256
    }

    val mapperName = MutableList(92) { "Unknown Mapper" }

    lateinit var header:IntArray
    lateinit var rom: Array<IntArray>
    lateinit var vrom: Array<IntArray>
    lateinit var vromTile: Array<Array<Tile>>

    var romCount: Int = 0
    var vromCount: Int = 0
    var mirroring: Int = 0
    var batteryRam: Boolean = false
    var trainer: Boolean = false
    var fourScreen: Boolean = false
    var mapperType: Int = 0
    var valid = false

    init {
        mapperName[0] = "Direct Access"
        mapperName[1] = "Nintendo MMC1"
        mapperName[2] = "UNROM"
        mapperName[3] = "CNROM"
        mapperName[4] = "Nintendo MMC3"
        mapperName[5] = "Nintendo MMC5"
        mapperName[6] = "FFE F4xxx"
        mapperName[7] = "AOROM"
        mapperName[8] = "FFE F3xxx"
        mapperName[9] = "Nintendo MMC2"
        mapperName[10] = "Nintendo MMC4"
        mapperName[11] = "Color Dreams Chip"
        mapperName[12] = "FFE F6xxx"
        mapperName[15] = "100-in-1 switch"
        mapperName[16] = "Bandai chip"
        mapperName[17] = "FFE F8xxx"
        mapperName[18] = "Jaleco SS8806 chip"
        mapperName[19] = "Namcot 106 chip"
        mapperName[20] = "Famicom Disk System"
        mapperName[21] = "Konami VRC4a"
        mapperName[22] = "Konami VRC2a"
        mapperName[23] = "Konami VRC2a"
        mapperName[24] = "Konami VRC6"
        mapperName[25] = "Konami VRC4b"
        mapperName[32] = "Irem G-101 chip"
        mapperName[33] = "Taito TC0190/TC0350"
        mapperName[34] = "32kB ROM switch"

        mapperName[64] = "Tengen RAMBO-1 chip"
        mapperName[65] = "Irem H-3001 chip"
        mapperName[66] = "GNROM switch"
        mapperName[67] = "SunSoft3 chip"
        mapperName[68] = "SunSoft4 chip"
        mapperName[69] = "SunSoft5 FME-7 chip"
        mapperName[71] = "Camerica chip"
        mapperName[78] = "Irem 74HC161/32-based"
        mapperName[91] = "Pirate HK-SF3 chip"
    }

    fun load(data: RomData) {
        if (data.indexOf("NES\u001a") == -1) {
            nes.ui.updateStatus("Not a valid NES ROM.")
            return
        }
        header = IntArray(16)
        for (i in 0 until 16) header[i] = data.charCodeAt(i).toInt() and 0xff

        romCount = header[4]
        vromCount = header[5]*2
        mirroring = if ((header[6] and 1) != 0) 1 else 0
        batteryRam = (header[6] and 2) != 0
        trainer = (header[6] and 4) != 0
        fourScreen = (header[6] and 8) != 0
        mapperType = (header[6] shr 4) or (header[7] and 0xf0)

        // Check whether byte 8 to 15 are zeros
        var foundError = header.slice(8 until 16).any{ it != 0} // WB

        if (foundError) mapperType = mapperType and 0xf // Ignore byte 7

        // ******************
        // Load PRG Rom Banks
        // ******************
        rom = Array(romCount) { IntArray(ROM_SIZE) }
        var offset: Int = 16

        for (i in 0 until romCount) {
            for (j in 0 until Rom.ROM_SIZE) {
                if (offset + j >= data.length()) {
                    //stop
                } else {
                    rom[i][j] = data.charCodeAt(offset + j).toInt() and 0xff
                }
            }
            offset += ROM_SIZE
        }

        /*
        for (i <- 0 until romCount) {
          rom(i) = new Array(16384)
          for (j <- 0 until 16384) {
            if (offset+j >= data.length) {
              break;
            }
            rom(i)(j) = data.charCodeAt(offset + j) & 0xff
          }
          offset += 16384
        }
        */

        // ******************
        // Load CHR-ROM Banks
        // ******************
        vrom = Array(vromCount) { IntArray(VROM_SIZE) }

        for (i in 0 until vromCount) {
            for (j in 0 until VROM_SIZE) {
                if (offset + j >= data.length()) {
                    //stop
                } else {
                    vrom[i][j] = data.charCodeAt(offset + j).toInt() and 0xff
                }
            }
            offset += Rom.VROM_SIZE
        }

        /*
        for (i <- 0 until vromCount) {
          vrom(i) = new Array(4096)
          for (j <- 0 until 4096) {
            if (offset+j >= data.length) {
              break
            }
            vrom(i)(j) = data.charCodeAt(offset + j) & 0xff
          }
          offset += 4096
        }
        */

        // *****************
        // Create VROM tiles
        // *****************
        vromTile = Array(vromCount) { Array(VROM_TILE_SIZE) { Tile() } }
/* OLD - WB
        for (i in 0 until vromCount) {
            vromTile[i] = Array(VROM_TILE_SIZE)
            for (j in 0 until VROM_TILE_SIZE) vromTile[i][j] = Tile()
        }
*/

        /*
        for (i <- 0 until vromCount) {
          vromTile(i) = new Array(256)
          for (j <- 0 until 256) {
            vromTile(i)(j) = new PPU.Tile()
          }
        }-
        */

        // Convert CHR-ROM banks to tiles
        for (v in 0 until vromCount) {
            for (i in 0 until Rom.VROM_SIZE) {
                val tileIndex:Int = i shr 4
                val leftOver:Int = i % 16
                if (leftOver < 8) {
                    vromTile[v][tileIndex].setScanline(leftOver, vrom[v][i], vrom[v][i+8])
                } else {
                    vromTile[v][tileIndex].setScanline(leftOver-8, vrom[v][i-8], vrom[v][i])
                }
            }
        }

        valid = true
    }

    fun getMirroringType(): Int {
        return if (fourScreen) Rom.FOURSCREEN_MIRRORING else if (mirroring == 0) Rom.HORIZONTAL_MIRRORING else Rom.VERTICAL_MIRRORING
    }

    fun getMapperName(): String {
        return if (mapperType >= 0 && mapperType < mapperName.size) mapperName[mapperType] else "Unknown Mapper, " + mapperType
    }

    fun mapperSupported(): Boolean {
        return mapperType < 100 && nes.mappers[mapperType] != null
    }

    fun createMapper(): Optional<Mapper> {
        return if (mapperSupported()) Optional.of(nes.mappers[mapperType]!!.invoke()) else { // TODO
            nes.ui.updateStatus("This ROM uses a mapper not supported by JSNES: " + getMapperName() + "("+mapperType+")")
            Optional.empty()
        }
    }
}