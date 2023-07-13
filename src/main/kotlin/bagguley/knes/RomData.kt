package bagguley.knes

import java.io.BufferedInputStream
import java.io.FileInputStream

class RomData(val data: IntArray) {
    fun indexOf(arg: String): Int {
        return 0
    }

    fun charCodeAt(i: Int): Int {
        return data[i]
    }

    fun length(): Int {
        return data.size
    }

    companion object {
        /*fun load(filename:String):RomData {
            val bis = BufferedInputStream(FileInputStream(filename))
            val bArray = bis.readBytes()

            bis.close()

            return RomData(bArray)
        }*/

        fun load(filename:String): RomData {
            val ints = mutableListOf<Int>()
            val bis = BufferedInputStream(FileInputStream(filename))

            var b = bis.read()
            while (b != -1) {
                ints.add(b)
                b = bis.read()
            }
            bis.close()

            return RomData(ints.toIntArray())
        }
    }
}