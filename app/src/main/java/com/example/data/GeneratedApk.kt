package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "generated_apks")
data class GeneratedApk(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val packageName: String,
    val targetUrl: String,
    val isLocalFile: Boolean = false,
    val localHtmlContent: String = "",
    val filePath: String,
    val fileSizeBytes: Long,
    val versionName: String = "1.0",
    val themeColorHex: String = "#2563EB",
    val iconPresetName: String = "web",
    val customIconPath: String? = null,
    val enablePrint: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)
