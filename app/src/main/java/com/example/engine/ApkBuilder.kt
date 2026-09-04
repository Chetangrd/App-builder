package com.example.engine

import android.content.Context
import android.graphics.Bitmap
import com.example.data.GeneratedApk
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.FilterOutputStream
import java.io.OutputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object ApkBuilder {

    private class CountingOutputStream(out: OutputStream) : FilterOutputStream(out) {
        var count: Long = 0L
            private set

        override fun write(b: Int) {
            out.write(b)
            count++
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            out.write(b, off, len)
            count += len
        }
    }

    suspend fun buildApk(
        context: Context,
        name: String,
        packageName: String,
        targetUrl: String,
        isLocalFile: Boolean,
        localHtmlContent: String,
        versionName: String,
        themeColorHex: String,
        iconPresetName: String,
        customIconBitmap: Bitmap?,
        enablePrint: Boolean,
        onProgress: (Float, String) -> Unit = { _, _ -> }
    ): GeneratedApk {
        onProgress(0.1f, "Preparing template files...")

        val apksDir = File(context.filesDir, "apks").apply { mkdirs() }
        val outputApkFile = File(apksDir, "${packageName}.apk")
        val unsignedZipFile = File(context.cacheDir, "unsigned_${System.currentTimeMillis()}.zip")

        // 1. Read base binary AndroidManifest
        onProgress(0.25f, "Configuring package identity...")
        val baseManifestBytes = context.assets.open("base_manifest.bin").use { it.readBytes() }
        val modifiedManifestBytes = AxmlModifier.modifyManifest(
            baseAxmlBytes = baseManifestBytes,
            newPackageName = packageName,
            versionName = versionName
        )

        // Read and configure resources.arsc table
        val baseArscBytes = context.assets.open("resources.arsc").use { it.readBytes() }
        val modifiedArscBytes = ArscModifier.modifyArsc(
            baseArscBytes = baseArscBytes,
            newAppName = name,
            newPackageName = packageName
        )

        // 2. Read classes.dex
        onProgress(0.4f, "Loading runtime DEX engine...")
        val dexBytes = context.assets.open("template_classes.dex").use { it.readBytes() }

        // 3. Render icon PNG
        onProgress(0.55f, "Generating application icons...")
        val iconBitmap = if (customIconBitmap != null) {
            IconHelper.renderCustomBitmap(customIconBitmap, 192)
        } else {
            val tintColor = try {
                android.graphics.Color.parseColor(themeColorHex)
            } catch (e: Exception) {
                null
            }
            IconHelper.renderIconBitmap(iconPresetName, 192, tintColor)
        }
        val iconPngBytes = IconHelper.bitmapToPngBytes(iconBitmap)

        // Save custom icon preview file if present
        var customIconFilePath: String? = null
        if (customIconBitmap != null) {
            val iconCacheFile = File(apksDir, "${packageName}_icon.png")
            FileOutputStream(iconCacheFile).use { it.write(iconPngBytes) }
            customIconFilePath = iconCacheFile.absolutePath
        }

        // 4. Build app_config.json
        onProgress(0.65f, "Embedding kiosk configuration...")
        val configJson = JSONObject().apply {
            put("appName", name)
            put("url", if (isLocalFile) "file:///android_asset/local.html" else targetUrl)
            put("isLocal", isLocalFile)
            put("themeColor", themeColorHex)
            put("enablePrint", enablePrint)
            put("versionName", versionName)
        }.toString().toByteArray(Charsets.UTF_8)

        // 5. Assemble unsigned ZIP archive with proper alignment & uncompressed resources.arsc
        onProgress(0.75f, "Packaging aligned APK container...")
        val cos = CountingOutputStream(FileOutputStream(unsignedZipFile))
        ZipOutputStream(cos).use { zout ->
            // AndroidManifest.xml
            writeZipEntry(zout, cos, "AndroidManifest.xml", modifiedManifestBytes, stored = false)

            // resources.arsc (MUST BE STORED AND 4-BYTE ALIGNED FOR MMAP)
            writeZipEntry(zout, cos, "resources.arsc", modifiedArscBytes, stored = true)

            // classes.dex
            writeZipEntry(zout, cos, "classes.dex", dexBytes, stored = false)

            // Launcher icons (STORED and 4-byte aligned)
            writeZipEntry(zout, cos, "res/drawable/ic_launcher.png", iconPngBytes, stored = true)
            writeZipEntry(zout, cos, "res/drawable-xxhdpi/ic_launcher.png", iconPngBytes, stored = true)

            // assets/app_config.json
            writeZipEntry(zout, cos, "assets/app_config.json", configJson, stored = false)

            // assets/local.html (if local)
            if (isLocalFile) {
                val htmlBytes = localHtmlContent.toByteArray(Charsets.UTF_8)
                writeZipEntry(zout, cos, "assets/local.html", htmlBytes, stored = false)
            }
        }

        // 6. Sign APK with multi-scheme (v1, v2, v3) signature
        onProgress(0.9f, "Signing APK package...")
        ApkSignerHelper.signApk(
            context = context,
            unsignedZipFile = unsignedZipFile,
            outputSignedApkFile = outputApkFile
        )

        // Clean up unsigned temporary file
        unsignedZipFile.delete()

        onProgress(1.0f, "APK successfully built!")

        return GeneratedApk(
            name = name,
            packageName = packageName,
            targetUrl = targetUrl,
            isLocalFile = isLocalFile,
            localHtmlContent = localHtmlContent,
            filePath = outputApkFile.absolutePath,
            fileSizeBytes = outputApkFile.length(),
            versionName = versionName,
            themeColorHex = themeColorHex,
            iconPresetName = iconPresetName,
            customIconPath = customIconFilePath,
            enablePrint = enablePrint,
            createdAt = System.currentTimeMillis()
        )
    }

    private fun writeZipEntry(
        zout: ZipOutputStream,
        countingOut: CountingOutputStream,
        name: String,
        bytes: ByteArray,
        stored: Boolean = false
    ) {
        val entry = ZipEntry(name)
        if (stored) {
            entry.method = ZipEntry.STORED
            entry.size = bytes.size.toLong()
            entry.compressedSize = bytes.size.toLong()
            val crc = CRC32()
            crc.update(bytes)
            entry.crc = crc.value

            // 4-byte alignment: data offset = currentOffset + 30 + nameBytes + extraBytes
            val currentOffset = countingOut.count
            val nameBytesLength = name.toByteArray(Charsets.UTF_8).size
            val dataOffsetWithoutExtra = currentOffset + 30 + nameBytesLength
            val pad = ((4 - (dataOffsetWithoutExtra % 4)) % 4).toInt()
            if (pad > 0) {
                entry.extra = ByteArray(pad)
            }
        } else {
            entry.method = ZipEntry.DEFLATED
        }
        zout.putNextEntry(entry)
        zout.write(bytes)
        zout.closeEntry()
    }
}
