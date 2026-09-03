package com.example.engine

import android.content.Context
import android.graphics.Bitmap
import com.example.data.GeneratedApk
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object ApkBuilder {

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

        // 5. Assemble unsigned ZIP archive
        onProgress(0.75f, "Packaging APK container...")
        ZipOutputStream(FileOutputStream(unsignedZipFile)).use { zout ->
            // AndroidManifest.xml
            writeZipEntry(zout, "AndroidManifest.xml", modifiedManifestBytes)

            // classes.dex
            writeZipEntry(zout, "classes.dex", dexBytes)

            // res/drawable/ic_launcher.png
            writeZipEntry(zout, "res/drawable/ic_launcher.png", iconPngBytes)

            // assets/app_config.json
            writeZipEntry(zout, "assets/app_config.json", configJson)

            // assets/local.html (if local)
            if (isLocalFile) {
                val htmlBytes = localHtmlContent.toByteArray(Charsets.UTF_8)
                writeZipEntry(zout, "assets/local.html", htmlBytes)
            }
        }

        // 6. Sign APK with v1 signature
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

    private fun writeZipEntry(zout: ZipOutputStream, name: String, bytes: ByteArray) {
        val entry = ZipEntry(name)
        zout.putNextEntry(entry)
        zout.write(bytes)
        zout.closeEntry()
    }
}
