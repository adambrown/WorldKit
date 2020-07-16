package wk.utils.hdr

import java.io.*

private const val VALID_PROGRAM_TYPE = 0x01
private const val VALID_GAMMA = 0x02
private const val VALID_EXPOSURE = 0x04
private const val GAMMA_STRING = "GAMMA="
private const val EXPOSURE_STRING = "EXPOSURE="
private val widthHeightPattern = "-Y (\\d+) \\+X (\\d+)".toRegex()

class HdrImage(val width: Int, val height: Int, private var data: FloatArray) {

    operator fun get(x: Int, y: Int, b: Int) = data[(y * width + x) * 3 + b]

    operator fun set(x: Int, y: Int, b: Int, value: Float) {
        data[(y * width + x) * 3 + b] = value
    }
}

fun readHdr(file: File): HdrImage {
    DataInputStream(file.inputStream().buffered()).use { stream ->
        val (width, height) = readHeader(stream)
        val pixel = ByteArray(4)
        val scanLine = ByteArray(width * 4)
        val scanBufferTmp = ByteArray(width * 4)
        val imageData = FloatArray(width * height * 3)
        var floatOffset = 0
        for (h in 0 until height) {
            readRunLengthEncodedPixels(stream, scanLine, scanBufferTmp, pixel, width)
            floatOffset = scanlineToFloatRgb(scanLine, imageData, floatOffset)
        }
        return HdrImage(width, height, imageData)
    }
}

private fun readHeader(input: DataInput): Pair<Int, Int> {
    var valid = 0
    var width = 0
    var height = 0
    var line = input.readLine() ?: throw IOException("Unexpected EOF reading magic token")
    if (line[0] == '#' && line[1] == '?') {
        valid = valid or VALID_PROGRAM_TYPE
        line = input.readLine() ?: throw IOException("Unexpected EOF reading line after magic token")
    }
    var foundFormat = false
    var done = false
    while (!done) {
        if (line == "FORMAT=32-bit_rle_rgbe") {
            foundFormat = true
        } else if (line.startsWith(GAMMA_STRING)) {
            valid = valid or VALID_GAMMA
        } else if (line.startsWith(EXPOSURE_STRING)) {
            valid = valid or VALID_EXPOSURE
        } else {
            val match = widthHeightPattern.matchEntire(line)
            if (match != null) {
                width = match.groups[2]!!.value.toInt()
                height = match.groups[1]!!.value.toInt()
                done = true
            }
        }
        if (!done) {
            line = input.readLine() ?: throw IOException("Unexpected EOF reading header")
        }
    }
    if (!foundFormat) {
        throw IOException("No FORMAT specifier found")
    }
    return width to height
}

private fun readRunLengthEncodedPixels(input: DataInput, scanLine: ByteArray, scanBufferTmp: ByteArray, pixel: ByteArray, width: Int) {
    var cursor = 0
    if (width < 8 || width > 0x7fff) {
        input.readFully(scanLine, cursor, width * 4)
    }
    input.readFully(pixel)
    if (pixel[0] != 2.toByte() || pixel[1] != 2.toByte() || pixel[2].toInt() and 0x80 != 0) {
        scanLine[cursor++] = pixel[0]
        scanLine[cursor++] = pixel[1]
        scanLine[cursor++] = pixel[2]
        scanLine[cursor++] = pixel[3]
        input.readFully(scanLine, cursor, (width - 1) * 4)
    }
    var pointer = 0
    for (i in 1..4) {
        val end = i * width
        while (pointer < end) {
            input.readFully(pixel, 0, 2)
            if (pixel[0].toInt() and 0xFF > 128) {
                var count = (pixel[0].toInt() and 0xFF) - 128
                if (count == 0 || count > end - pointer) {
                    throw IOException("Invalid run length.")
                }
                while (count-- > 0) {
                    scanBufferTmp[pointer++] = pixel[1]
                }
            } else {
                var count = pixel[0].toInt() and 0xFF
                if (count == 0 || count > end - pointer) {
                    throw IOException("Invalid run length.")
                }
                scanBufferTmp[pointer++] = pixel[1]
                if (--count > 0) {
                    input.readFully(scanBufferTmp, pointer, count)
                    pointer += count
                }
            }
        }
    }
    val width2 = width * 2
    val width3 = width * 3
    for (i in 0 until width) {
        scanLine[cursor++] = scanBufferTmp[i]
        scanLine[cursor++] = scanBufferTmp[i + width]
        scanLine[cursor++] = scanBufferTmp[i + width2]
        scanLine[cursor++] = scanBufferTmp[i + width3]
    }
}

private fun scanlineToFloatRgb(scanline: ByteArray, dest: FloatArray, destOffset: Int): Int {
    var off = destOffset
    for (i in scanline.indices step 4) {
        rgbeToFloatRgb(dest, scanline, i, off)
        off += 3
    }
    return off
}

private fun rgbeToFloatRgb(rgb: FloatArray, rgbe: ByteArray, inOffset: Int, outOffset: Int) {
    val e = rgbe[inOffset + 3].toInt()
    if (e == 0) {
        rgb[outOffset] = 0.0f
        rgb[outOffset + 1] = 0.0f
        rgb[outOffset + 2] = 0.0f
    } else {
        val exp = (e and 0xFF) - 136
        val scale = when {
            exp == 0 -> 1.0f
            exp > 0 -> (1 shl exp).toFloat()
            else -> (1.0f / (1 shl -exp))
        }
        rgb[outOffset] = (rgbe[inOffset + 0].toInt() and 0xFF) * scale
        rgb[outOffset + 1] = (rgbe[inOffset + 1].toInt() and 0xFF) * scale
        rgb[outOffset + 2] = (rgbe[inOffset + 2].toInt() and 0xFF) * scale
    }
}
