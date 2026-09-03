package com.example.engine

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

object ArscModifier {

    /**
     * Modifies the compiled resources.arsc table:
     * 1. Updates String 0 in the global string pool with [newAppName].
     * 2. Updates the package name in the package chunk with [newPackageName].
     * 3. Adjusts all chunk headers and file lengths.
     */
    fun modifyArsc(
        baseArscBytes: ByteArray,
        newAppName: String,
        newPackageName: String
    ): ByteArray {
        val buffer = ByteBuffer.wrap(baseArscBytes).order(ByteOrder.LITTLE_ENDIAN)

        // 1. Read ResTable_header (12 bytes)
        val tableType = buffer.short.toInt() and 0xFFFF
        val tableHdrSize = buffer.short.toInt() and 0xFFFF
        val oldTotalSize = buffer.int
        val packageCount = buffer.int

        // 2. Read ResStringPool_header (28 bytes) at offset 12
        val poolType = buffer.short.toInt() and 0xFFFF
        val poolHdrSize = buffer.short.toInt() and 0xFFFF
        val oldPoolSize = buffer.int
        val stringCount = buffer.int
        val styleCount = buffer.int
        val flags = buffer.int
        val stringsStart = buffer.int
        val stylesStart = buffer.int

        // Strings to embed: String 0 = App Name, String 1 = Launcher Icon path
        val strings = listOf(newAppName, "res/drawable/ic_launcher.png")
        val actualCount = strings.size

        // Build new UTF-8 String Pool data
        val stringDataOut = ByteArrayOutputStream()
        val newOffsets = IntArray(actualCount)

        for (i in 0 until actualCount) {
            val s = strings[i]
            newOffsets[i] = stringDataOut.size()
            val utf8Bytes = s.toByteArray(Charsets.UTF_8)
            val charLen = s.length
            val byteLen = utf8Bytes.size

            // Character length
            if (charLen > 127) {
                stringDataOut.write(((charLen shr 8) and 0x7F) or 0x80)
                stringDataOut.write(charLen and 0xFF)
            } else {
                stringDataOut.write(charLen and 0xFF)
            }

            // Byte length
            if (byteLen > 127) {
                stringDataOut.write(((byteLen shr 8) and 0x7F) or 0x80)
                stringDataOut.write(byteLen and 0xFF)
            } else {
                stringDataOut.write(byteLen and 0xFF)
            }

            stringDataOut.write(utf8Bytes)
            stringDataOut.write(0) // Null terminator
        }

        // 4-byte padding for string data
        while (stringDataOut.size() % 4 != 0) {
            stringDataOut.write(0)
        }

        val newStringData = stringDataOut.toByteArray()
        val newStringsStartRelative = 28 + actualCount * 4
        val newPoolSize = newStringsStartRelative + newStringData.size

        // Build new Pool Header + Offsets + String Data
        val poolOut = ByteArrayOutputStream(newPoolSize)
        val poolHdrBuf = ByteBuffer.allocate(28).order(ByteOrder.LITTLE_ENDIAN)
        poolHdrBuf.putShort(0x0001.toShort()) // RES_STRING_POOL_TYPE
        poolHdrBuf.putShort(28.toShort())     // headerSize
        poolHdrBuf.putInt(newPoolSize)
        poolHdrBuf.putInt(actualCount)
        poolHdrBuf.putInt(0)                  // styleCount
        poolHdrBuf.putInt(0x0100)             // flags: UTF-8
        poolHdrBuf.putInt(newStringsStartRelative)
        poolHdrBuf.putInt(0)                  // stylesStart
        poolOut.write(poolHdrBuf.array())

        val offsetsBuf = ByteBuffer.allocate(actualCount * 4).order(ByteOrder.LITTLE_ENDIAN)
        for (off in newOffsets) {
            offsetsBuf.putInt(off)
        }
        poolOut.write(offsetsBuf.array())
        poolOut.write(newStringData)

        // Read rest of the ARSC file (package chunk and type chunks)
        val restOffset = 12 + oldPoolSize
        val restBytes = ByteArray(baseArscBytes.size - restOffset)
        System.arraycopy(baseArscBytes, restOffset, restBytes, 0, restBytes.size)

        // Update package name in the package chunk (starts at restBytes[0])
        // Package header: type (2 bytes), headerSize (2 bytes), size (4 bytes), id (4 bytes)
        // Name is a 128-character UTF-16 array (256 bytes) starting at offset 12
        if (restBytes.size >= 12 + 256) {
            val pkgBytes = newPackageName.toByteArray(Charsets.UTF_16LE)
            val maxBytes = minOf(pkgBytes.size, 256)
            // Zero out original name buffer
            java.util.Arrays.fill(restBytes, 12, 12 + 256, 0.toByte())
            System.arraycopy(pkgBytes, 0, restBytes, 12, maxBytes)
        }

        val newTotalSize = 12 + poolOut.size() + restBytes.size
        val finalOut = ByteArrayOutputStream(newTotalSize)
        val tableHdrBuf = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN)
        tableHdrBuf.putShort(0x0002.toShort()) // RES_TABLE_TYPE
        tableHdrBuf.putShort(12.toShort())     // headerSize
        tableHdrBuf.putInt(newTotalSize)
        tableHdrBuf.putInt(packageCount)
        finalOut.write(tableHdrBuf.array())
        finalOut.write(poolOut.toByteArray())
        finalOut.write(restBytes)

        return finalOut.toByteArray()
    }
}
