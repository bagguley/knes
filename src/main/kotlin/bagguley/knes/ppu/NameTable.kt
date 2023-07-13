package bagguley.knes.ppu

import kotlin.math.floor

class NameTable(val width: Int, val height: Int, val name: String) {
    var tile = IntArray(width * height)
    var attrib = IntArray(width * height)

    fun getTileIndex(x: Int, y: Int): Int  {
        return tile[y * width + x]
    }

    fun getAttrib(x: Int, y: Int): Int {
        return attrib[y * width + x]
    }

    fun writeAttrib(index: Int, value: Int) {
        val basex:Int = (index % 8) * 4
        val basey:Int = (floor(index.toDouble() / 8) * 4).toInt()

        for (sqy in 0 until 2) {
            for (sqx in 0 until 2) {
                val add = (value shr (2 * (sqy * 2 + sqx))) and 3
                for (y in 0 until 2) {
                    for (x in 0 until 2) {
                        val tx = basex + sqx * 2 + x
                        val ty = basey + sqy * 2 + y
                        val attindex = ty * width + tx
                        attrib[attindex] = (add shl 2) and 12
                    }
                }
            }
        }
    }
}