package com.example.player

/**
 * Low-level JPEG helper for MJPEG frame validation and Huffman table injection.
 * Many cheap USB HDMI capture cards (e.g. MacroSilicon MS2109 / generic USB 2.0 capture dongles)
 * stream MJPEG without DHT (Define Huffman Table) markers to save bandwidth.
 * This class ensures standard baseline Huffman tables are present when needed with zero allocations.
 */
object JpegUtils {
    private const val SOI: Short = 0xD8FF.toShort() // Little-endian check for 0xFF, 0xD8
    private const val EOI: Short = 0xD9FF.toShort()

    // Standard baseline luminance and chrominance DHT segment (JPEG spec ISO/IEC 10918-1)
    private val STANDARD_DHT = byteArrayOf(
        0xFF.toByte(), 0xC4.toByte(), 0x01.toByte(), 0xA2.toByte(),
        0x00.toByte(), 0x00.toByte(), 0x01.toByte(), 0x05.toByte(), 0x01.toByte(), 0x01.toByte(),
        0x01.toByte(), 0x01.toByte(), 0x01.toByte(), 0x01.toByte(), 0x00.toByte(), 0x00.toByte(),
        0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
        0x01.toByte(), 0x02.toByte(), 0x03.toByte(), 0x04.toByte(), 0x05.toByte(), 0x06.toByte(),
        0x07.toByte(), 0x08.toByte(), 0x09.toByte(), 0x0A.toByte(), 0x0B.toByte(), 0x01.toByte(),
        0x00.toByte(), 0x03.toByte(), 0x01.toByte(), 0x01.toByte(), 0x01.toByte(), 0x01.toByte(),
        0x01.toByte(), 0x01.toByte(), 0x01.toByte(), 0x01.toByte(), 0x01.toByte(), 0x00.toByte(),
        0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x01.toByte(),
        0x02.toByte(), 0x03.toByte(), 0x04.toByte(), 0x05.toByte(), 0x06.toByte(), 0x07.toByte(),
        0x08.toByte(), 0x09.toByte(), 0x0A.toByte(), 0x0B.toByte(), 0x10.toByte(), 0x00.toByte(),
        0x02.toByte(), 0x01.toByte(), 0x03.toByte(), 0x03.toByte(), 0x02.toByte(), 0x04.toByte(),
        0x03.toByte(), 0x05.toByte(), 0x05.toByte(), 0x04.toByte(), 0x04.toByte(), 0x00.toByte(),
        0x00.toByte(), 0x01.toByte(), 0x7D.toByte(), 0x01.toByte(), 0x02.toByte(), 0x03.toByte(),
        0x00.toByte(), 0x04.toByte(), 0x11.toByte(), 0x05.toByte(), 0x12.toByte(), 0x21.toByte(),
        0x31.toByte(), 0x41.toByte(), 0x06.toByte(), 0x13.toByte(), 0x51.toByte(), 0x61.toByte(),
        0x07.toByte(), 0x22.toByte(), 0x71.toByte(), 0x14.toByte(), 0x32.toByte(), 0x81.toByte(),
        0x91.toByte(), 0xA1.toByte(), 0x08.toByte(), 0x23.toByte(), 0x42.toByte(), 0xB1.toByte(),
        0xC1.toByte(), 0x15.toByte(), 0x52.toByte(), 0xD1.toByte(), 0xF0.toByte(), 0x24.toByte(),
        0x33.toByte(), 0x62.toByte(), 0x72.toByte(), 0x82.toByte(), 0x09.toByte(), 0x0A.toByte(),
        0x16.toByte(), 0x17.toByte(), 0x18.toByte(), 0x19.toByte(), 0x1A.toByte(), 0x25.toByte(),
        0x26.toByte(), 0x27.toByte(), 0x28.toByte(), 0x29.toByte(), 0x2A.toByte(), 0x34.toByte(),
        0x35.toByte(), 0x36.toByte(), 0x37.toByte(), 0x38.toByte(), 0x39.toByte(), 0x3A.toByte(),
        0x43.toByte(), 0x44.toByte(), 0x45.toByte(), 0x46.toByte(), 0x47.toByte(), 0x48.toByte(),
        0x49.toByte(), 0x4A.toByte(), 0x53.toByte(), 0x54.toByte(), 0x55.toByte(), 0x56.toByte(),
        0x57.toByte(), 0x58.toByte(), 0x59.toByte(), 0x5A.toByte(), 0x63.toByte(), 0x64.toByte(),
        0x65.toByte(), 0x66.toByte(), 0x67.toByte(), 0x68.toByte(), 0x69.toByte(), 0x6A.toByte(),
        0x73.toByte(), 0x74.toByte(), 0x75.toByte(), 0x76.toByte(), 0x77.toByte(), 0x78.toByte(),
        0x79.toByte(), 0x7A.toByte(), 0x83.toByte(), 0x84.toByte(), 0x85.toByte(), 0x86.toByte(),
        0x87.toByte(), 0x88.toByte(), 0x89.toByte(), 0x8A.toByte(), 0x92.toByte(), 0x93.toByte(),
        0x94.toByte(), 0x95.toByte(), 0x96.toByte(), 0x97.toByte(), 0x98.toByte(), 0x99.toByte(),
        0x9A.toByte(), 0xA2.toByte(), 0xA3.toByte(), 0xA4.toByte(), 0xA5.toByte(), 0xA6.toByte(),
        0xA7.toByte(), 0xA8.toByte(), 0xA9.toByte(), 0xAA.toByte(), 0xB2.toByte(), 0xB3.toByte(),
        0xB4.toByte(), 0xB5.toByte(), 0xB6.toByte(), 0xB7.toByte(), 0xB8.toByte(), 0xB9.toByte(),
        0xBA.toByte(), 0xC2.toByte(), 0xC3.toByte(), 0xC4.toByte(), 0xC5.toByte(), 0xC6.toByte(),
        0xC7.toByte(), 0xC8.toByte(), 0xC9.toByte(), 0xCA.toByte(), 0xD2.toByte(), 0xD3.toByte(),
        0xD4.toByte(), 0xD5.toByte(), 0xD6.toByte(), 0xD7.toByte(), 0xD8.toByte(), 0xD9.toByte(),
        0xDA.toByte(), 0xE1.toByte(), 0xE2.toByte(), 0xE3.toByte(), 0xE4.toByte(), 0xE5.toByte(),
        0xE6.toByte(), 0xE7.toByte(), 0xE8.toByte(), 0xE9.toByte(), 0xEA.toByte(), 0xF1.toByte(),
        0xF2.toByte(), 0xF3.toByte(), 0xF4.toByte(), 0xF5.toByte(), 0xF6.toByte(), 0xF7.toByte(),
        0xF8.toByte(), 0xF9.toByte(), 0xFA.toByte(), 0x11.toByte(), 0x00.toByte(), 0x02.toByte(),
        0x01.toByte(), 0x02.toByte(), 0x04.toByte(), 0x04.toByte(), 0x03.toByte(), 0x04.toByte(),
        0x07.toByte(), 0x05.toByte(), 0x04.toByte(), 0x04.toByte(), 0x00.toByte(), 0x01.toByte(),
        0x02.toByte(), 0x77.toByte(), 0x00.toByte(), 0x01.toByte(), 0x02.toByte(), 0x03.toByte(),
        0x11.toByte(), 0x04.toByte(), 0x05.toByte(), 0x21.toByte(), 0x31.toByte(), 0x06.toByte(),
        0x12.toByte(), 0x41.toByte(), 0x51.toByte(), 0x07.toByte(), 0x61.toByte(), 0x71.toByte(),
        0x13.toByte(), 0x22.toByte(), 0x32.toByte(), 0x81.toByte(), 0x08.toByte(), 0x14.toByte(),
        0x42.toByte(), 0x91.toByte(), 0xA1.toByte(), 0xB1.toByte(), 0xC1.toByte(), 0x09.toByte(),
        0x23.toByte(), 0x33.toByte(), 0x52.toByte(), 0xF0.toByte(), 0x15.toByte(), 0x62.toByte(),
        0x72.toByte(), 0xD1.toByte(), 0x0A.toByte(), 0x16.toByte(), 0x24.toByte(), 0x34.toByte(),
        0xE1.toByte(), 0x25.toByte(), 0xF1.toByte(), 0x17.toByte(), 0x18.toByte(), 0x19.toByte(),
        0x1A.toByte(), 0x26.toByte(), 0x27.toByte(), 0x28.toByte(), 0x29.toByte(), 0x2A.toByte(),
        0x35.toByte(), 0x36.toByte(), 0x37.toByte(), 0x38.toByte(), 0x39.toByte(), 0x3A.toByte(),
        0x43.toByte(), 0x44.toByte(), 0x45.toByte(), 0x46.toByte(), 0x47.toByte(), 0x48.toByte(),
        0x49.toByte(), 0x4A.toByte(), 0x53.toByte(), 0x54.toByte(), 0x55.toByte(), 0x56.toByte(),
        0x57.toByte(), 0x58.toByte(), 0x59.toByte(), 0x5A.toByte(), 0x63.toByte(), 0x64.toByte(),
        0x65.toByte(), 0x66.toByte(), 0x67.toByte(), 0x68.toByte(), 0x69.toByte(), 0x6A.toByte(),
        0x73.toByte(), 0x74.toByte(), 0x75.toByte(), 0x76.toByte(), 0x77.toByte(), 0x78.toByte(),
        0x79.toByte(), 0x7A.toByte(), 0x82.toByte(), 0x83.toByte(), 0x84.toByte(), 0x85.toByte(),
        0x86.toByte(), 0x87.toByte(), 0x88.toByte(), 0x89.toByte(), 0x8A.toByte(), 0x92.toByte(),
        0x93.toByte(), 0x94.toByte(), 0x95.toByte(), 0x96.toByte(), 0x97.toByte(), 0x98.toByte(),
        0x99.toByte(), 0x9A.toByte(), 0xA2.toByte(), 0xA3.toByte(), 0xA4.toByte(), 0xA5.toByte(),
        0xA6.toByte(), 0xA7.toByte(), 0xA8.toByte(), 0xA9.toByte(), 0xAA.toByte(), 0xB2.toByte(),
        0xB3.toByte(), 0xB4.toByte(), 0xB5.toByte(), 0xB6.toByte(), 0xB7.toByte(), 0xB8.toByte(),
        0xB9.toByte(), 0xBA.toByte(), 0xC2.toByte(), 0xC3.toByte(), 0xC4.toByte(), 0xC5.toByte(),
        0xC6.toByte(), 0xC7.toByte(), 0xC8.toByte(), 0xC9.toByte(), 0xCA.toByte(), 0xD2.toByte(),
        0xD3.toByte(), 0xD4.toByte(), 0xD5.toByte(), 0xD6.toByte(), 0xD7.toByte(), 0xD8.toByte(),
        0xD9.toByte(), 0xDA.toByte(), 0xE2.toByte(), 0xE3.toByte(), 0xE4.toByte(), 0xE5.toByte(),
        0xE6.toByte(), 0xE7.toByte(), 0xE8.toByte(), 0xE9.toByte(), 0xEA.toByte(), 0xF2.toByte(),
        0xF3.toByte(), 0xF4.toByte(), 0xF5.toByte(), 0xF6.toByte(), 0xF7.toByte(), 0xF8.toByte(),
        0xF9.toByte(), 0xFA.toByte()
    )

    /**
     * Checks if data starts with JPEG Start Of Image (0xFF, 0xD8).
     */
    fun isJpeg(data: ByteArray, length: Int): Boolean {
        return length >= 4 &&
                (data[0].toInt() and 0xFF) == 0xFF &&
                (data[1].toInt() and 0xFF) == 0xD8
    }

    /**
     * Quick scan to determine if the JPEG data contains a DHT marker (0xFF, 0xC4).
     */
    fun hasHuffmanTable(data: ByteArray, length: Int): Boolean {
        var i = 2
        val max = length - 2
        while (i < max) {
            if ((data[i].toInt() and 0xFF) == 0xFF) {
                val marker = data[i + 1].toInt() and 0xFF
                if (marker == 0xC4) {
                    return true
                }
                if (marker == 0xDA) { // SOS (Start of Scan) - headers finished
                    return false
                }
                if (marker in 0xD0..0xD7 || marker == 0x00 || marker == 0xFF) {
                    i++
                } else if (i + 3 < length) {
                    val segLen = ((data[i + 2].toInt() and 0xFF) shl 8) or (data[i + 3].toInt() and 0xFF)
                    i += 2 + segLen
                } else {
                    break
                }
            } else {
                i++
            }
        }
        return false
    }

    /**
     * Injects baseline Huffman table directly after SOI into the destination buffer
     * without generating heap allocations.
     * @return resulting total length in dest
     */
    fun injectHuffmanTable(
        src: ByteArray,
        srcLen: Int,
        dest: ByteArray
    ): Int {
        if (srcLen < 2) return 0
        // Copy SOI (2 bytes)
        dest[0] = src[0]
        dest[1] = src[1]

        // Copy standard DHT
        val dhtLen = STANDARD_DHT.size
        System.arraycopy(STANDARD_DHT, 0, dest, 2, dhtLen)

        // Copy remaining JPEG bytes
        val remaining = srcLen - 2
        System.arraycopy(src, 2, dest, 2 + dhtLen, remaining)
        return 2 + dhtLen + remaining
    }
}
