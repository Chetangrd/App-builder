package com.example.engine

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

object AxmlModifier {

    fun modifyManifest(
        baseAxmlBytes: ByteArray,
        newPackageName: String,
        versionName: String = "1.0"
    ): ByteArray {
        val buffer = ByteBuffer.wrap(baseAxmlBytes).order(ByteOrder.LITTLE_ENDIAN)

        // ResXMLTree_header: type (0x0003), headerSize (8), size (4 bytes)
        val fileType = buffer.short.toInt() and 0xFFFF
        val fileHeaderSize = buffer.short.toInt() and 0xFFFF
        val totalFileSize = buffer.int

        // StringPool Chunk at offset 8
        val poolType = buffer.short.toInt() and 0xFFFF
        val poolHeaderSize = buffer.short.toInt() and 0xFFFF
        val poolChunkSize = buffer.int
        val stringCount = buffer.int
        val styleCount = buffer.int
        val flags = buffer.int // 0 = UTF-16, 0x100 = UTF-8
        val stringsStart = buffer.int
        val stylesStart = buffer.int

        val offsets = IntArray(stringCount)
        for (i in 0 until stringCount) {
            offsets[i] = buffer.int
        }

        val stringsAbsoluteStart = 8 + stringsStart
        val strings = ArrayList<String>(stringCount)

        for (i in 0 until stringCount) {
            val strPos = stringsAbsoluteStart + offsets[i]
            buffer.position(strPos)
            if ((flags and 0x100) != 0) {
                // UTF-8
                val len1 = buffer.get().toInt() and 0xFF
                val len = if ((len1 and 0x80) != 0) {
                    ((len1 and 0x7F) shl 8) or (buffer.get().toInt() and 0xFF)
                } else len1
                val bytes = ByteArray(len)
                buffer.get(bytes)
                strings.add(String(bytes, Charsets.UTF_8))
            } else {
                // UTF-16LE
                val len1 = buffer.short.toInt() and 0xFFFF
                val len = if ((len1 and 0x8000) != 0) {
                    ((len1 and 0x7FFF) shl 16) or (buffer.short.toInt() and 0xFFFF)
                } else len1
                val bytes = ByteArray(len * 2)
                buffer.get(bytes)
                strings.add(String(bytes, Charsets.UTF_16LE))
            }
        }

        // Replace package names and version strings
        for (i in 0 until strings.size) {
            val s = strings[i]
            when {
                s == "com.example" -> strings[i] = newPackageName
                s.startsWith("com.example.") -> strings[i] = s.replace("com.example", newPackageName)
                s == "1.0" && versionName.isNotBlank() -> strings[i] = versionName
            }
        }

        // Rebuild StringPool
        val stringDataOut = ByteArrayOutputStream()
        val newOffsets = IntArray(stringCount)

        for (i in 0 until stringCount) {
            val s = strings[i]
            newOffsets[i] = stringDataOut.size()
            val encoded = s.toByteArray(Charsets.UTF_16LE)
            val charLen = s.length

            // Write length (uint16)
            stringDataOut.write(charLen and 0xFF)
            stringDataOut.write((charLen shr 8) and 0xFF)
            stringDataOut.write(encoded)
            // 2-byte null terminator
            stringDataOut.write(0)
            stringDataOut.write(0)
        }

        // 4-byte padding for string data
        while (stringDataOut.size() % 4 != 0) {
            stringDataOut.write(0)
        }

        val newStringData = stringDataOut.toByteArray()
        val newStringsStartRelative = 28 + stringCount * 4
        val newPoolChunkSize = newStringsStartRelative + newStringData.size

        // Build new pool chunk
        val poolChunkOut = ByteArrayOutputStream()
        val poolHeaderBuf = ByteBuffer.allocate(28).order(ByteOrder.LITTLE_ENDIAN)
        poolHeaderBuf.putShort(0x0001.toShort()) // type
        poolHeaderBuf.putShort(28.toShort())     // headerSize
        poolHeaderBuf.putInt(newPoolChunkSize)   // chunkSize
        poolHeaderBuf.putInt(stringCount)
        poolHeaderBuf.putInt(0)                  // styleCount
        poolHeaderBuf.putInt(0)                  // flags = UTF-16
        poolHeaderBuf.putInt(newStringsStartRelative)
        poolHeaderBuf.putInt(0)                  // stylesStart
        poolChunkOut.write(poolHeaderBuf.array())

        val offsetsBuf = ByteBuffer.allocate(stringCount * 4).order(ByteOrder.LITTLE_ENDIAN)
        for (off in newOffsets) {
            offsetsBuf.putInt(off)
        }
        poolChunkOut.write(offsetsBuf.array())
        poolChunkOut.write(newStringData)

        // Remainder of file (resource IDs chunk + XML nodes)
        val restOffset = 8 + poolChunkSize
        val restBytes = ByteArray(baseAxmlBytes.size - restOffset)
        System.arraycopy(baseAxmlBytes, restOffset, restBytes, 0, restBytes.size)

        // Assemble final AXML
        val newTotalSize = 8 + poolChunkOut.size() + restBytes.size
        val finalOut = ByteArrayOutputStream(newTotalSize)
        val fileHeaderBuf = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
        fileHeaderBuf.putShort(0x0003.toShort())
        fileHeaderBuf.putShort(8.toShort())
        fileHeaderBuf.putInt(newTotalSize)
        finalOut.write(fileHeaderBuf.array())
        finalOut.write(poolChunkOut.toByteArray())
        finalOut.write(restBytes)

        return finalOut.toByteArray()
    }
}
