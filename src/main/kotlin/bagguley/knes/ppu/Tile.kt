package bagguley.knes.ppu

class Tile {
    // Tile data:
    var pix = IntArray(64)
    var opaque = BooleanArray(8)
    var initialized:Boolean = false

    var tIndex:Int = 0
    var w: Int = 0
    var h: Int = 0
    var fbIndex: Int = 0
    var palIndex: Int = 0
    var tpri: Int = 0

    /*
      var x: Int = 0
      var incX: Int = 0
      var incY: Int = 0
      var c: Int = 0*/

    fun setBuffer(scanline: IntArray) {
        for (y in 0 until 8) {
            setScanline(y, scanline[y], scanline[y+8])
        }
    }

    fun setScanline(sline: Int, b1: Int, b2: Int) {
        initialized = true
        tIndex = sline shl 3
        for (x in 0 until 8) {
            pix[tIndex + x] = ((b1 shr (7 - x)) and 1) + (((b2 shr (7 - x)) and 1) shl 1)
            if (pix[tIndex + x] == 0) {
                opaque[sline] = false
            }
        }
    }

    fun render(buffer: IntArray, srcx1IN: Int, srcy1IN: Int, srcx2IN: Int, srcy2IN: Int, dx: Int, dy: Int, palAdd: Int,
    palette: IntArray, flipHorizontal: Boolean, flipVertical: Boolean, pri: Int, priTable: IntArray) {
        if (dx < -7 || dx >= 256 || dy < -7 || dy >= 240) {
            // Do nothing - replaces return statement
        } else {
            var srcx1 = srcx1IN
            var srcy1 = srcy1IN
            var srcx2 = srcx2IN
            var srcy2 = srcy2IN

            w = srcx2 - srcx1
            h = srcy2 - srcy1

            if (dx < 0) {
                srcx1 -= dx
            }
            if (dx + srcx2 >= 256) {
                srcx2 = 256 - dx
            }

            if (dy < 0) {
                srcy1 -= dy
            }
            if (dy + srcy2 >= 240) {
                srcy2 = 240 - dy
            }

            if (!flipHorizontal && !flipVertical) {

                fbIndex = (dy shl 8) + dx
                tIndex = 0
                for (y in 0 until 8) {
                    for (x in 0 until 8) {
                    if (x in srcx1..< srcx2 && y >= srcy1 && y < srcy2) {
                        palIndex = pix[tIndex]
                        tpri = priTable[fbIndex]
                        if (palIndex != 0 && pri <= (tpri and 0xFF)) {
                            //console.log("Rendering upright tile to buffer")
                            buffer[fbIndex] = palette[palIndex + palAdd]
                            tpri = (tpri and 0xF00) or pri
                            priTable[fbIndex] = tpri
                        }
                    }
                    fbIndex += 1
                    tIndex += 1
                }
                    fbIndex -= 8
                    fbIndex += 256
                }
            } else if (flipHorizontal && !flipVertical) {

                fbIndex = (dy shl 8) + dx
                tIndex = 7
                for (y in 0 until 8) {
                    for (x in 0 until 8) {
                    if (x in srcx1..<srcx2 && y >= srcy1 && y < srcy2) {
                        palIndex = pix[tIndex]
                        tpri = priTable[fbIndex]
                        if (palIndex != 0 && pri <= (tpri and 0xFF)) {
                            buffer[fbIndex] = palette[palIndex + palAdd]
                            tpri = (tpri and 0xF00) or pri
                            priTable[fbIndex] = tpri
                        }
                    }
                    fbIndex += 1
                    tIndex -= 1
                }
                    fbIndex -= 8
                    fbIndex += 256
                    tIndex += 16
                }

            } else if (flipVertical && !flipHorizontal) {
                fbIndex = (dy shl 8) + dx
                tIndex = 56
                for (y in 0 until 8) {
                    for (x in 0 until 8) {
                    if (x in srcx1..< srcx2 && y >= srcy1 && y < srcy2) {
                        palIndex = pix[tIndex]
                        tpri = priTable[fbIndex]
                        if (palIndex != 0 && pri <= (tpri and 0xFF)) {
                            buffer[fbIndex] = palette[palIndex + palAdd]
                            tpri = (tpri and 0xF00) or pri
                            priTable[fbIndex] = tpri
                        }
                    }
                    fbIndex += 1
                    tIndex += 1
                }
                    fbIndex -= 8
                    fbIndex += 256
                    tIndex -= 16
                }

            }
            else {
                fbIndex = (dy shl 8) + dx
                tIndex = 63
                for (y in 0 until 8) {
                    for (x in 0 until 8) {
                    if (x in srcx1..<srcx2 && y >= srcy1 && y < srcy2) {
                        palIndex = pix[tIndex]
                        tpri = priTable[fbIndex]
                        if (palIndex !=0 && pri <= (tpri and 0xFF)) {
                            buffer[fbIndex] = palette[palIndex + palAdd]
                            tpri = (tpri and 0xF00) or pri
                            priTable[fbIndex] = tpri
                        }
                    }
                    fbIndex += 1
                    tIndex -= 1
                }
                    fbIndex -= 8
                    fbIndex += 256
                }
            }
        }
    }

    fun isTransparent(x: Int, y:Int):Boolean = pix[(y shl 3) + x] == 0
}