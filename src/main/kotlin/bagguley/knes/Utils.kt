package bagguley.knes

import bagguley.knes.ppu.Tile

object Utils {
    fun copyArrayElements(src: IntArray, srcPos: Int, dest: IntArray, destPos: Int, length: Int) {
        for (i in 0 until length) {
            dest[destPos + i] = src[srcPos + i]
        }
    }

    fun copyArrayElements(src: Array<Tile>, srcPos: Int, dest: Array<Tile>, destPos: Int, length: Int) {
        for (i in 0 until length) {
            dest[destPos + i] = src[srcPos + i]
        }
    }
}