package com.example.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.ApkRepository
import com.example.data.GeneratedApk
import com.example.engine.ApkBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

data class ApkCreatorUiState(
    val appName: String = "My Web App",
    val targetUrl: String = "https://en.wikipedia.org",
    val isLocalFile: Boolean = false,
    val localHtmlContent: String = """<!DOCTYPE html>
<html>
<head>
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Offline Web App</title>
    <style>
        body { font-family: sans-serif; padding: 24px; line-height: 1.6; background: #f8fafc; color: #0f172a; }
        .card { background: white; padding: 20px; border-radius: 16px; box-shadow: 0 4px 6px -1px rgba(0,0,0,0.1); margin-bottom: 16px; }
        h1 { color: #2563eb; margin-top: 0; }
        button { background: #2563eb; color: white; border: none; padding: 12px 20px; border-radius: 8px; font-weight: bold; cursor: pointer; }
    </style>
</head>
<body>
    <div class="card">
        <h1>Hello Offline World!</h1>
        <p>This is a standalone local HTML web app compiled directly inside your APK.</p>
        <button onclick="alert('Offline JS Works!')">Test Interactive JS</button>
    </div>
</body>
</html>""",
    val packageName: String = "com.web2apk.myapp",
    val versionName: String = "1.0",
    val selectedPresetId: String = "web",
    val customIconBitmap: Bitmap? = null,
    val themeColorHex: String = "#2563EB",
    val enablePrint: Boolean = true,
    val isBuilding: Boolean = false,
    val buildProgress: Float = 0f,
    val buildStatus: String = "",
    val newlyBuiltApk: GeneratedApk? = null,
    val previewApk: GeneratedApk? = null,
    val apkToInspect: GeneratedApk? = null,
    val errorMessage: String? = null
)

class ApkCreatorViewModel(
    private val repository: ApkRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ApkCreatorUiState())
    val uiState: StateFlow<ApkCreatorUiState> = _uiState.asStateFlow()

    val allApks: StateFlow<List<GeneratedApk>> = repository.allApks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setAppName(name: String) {
        _uiState.update { state ->
            val cleanSlug = name.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]"), "")
            val defaultPkg = if (cleanSlug.isNotBlank()) "com.web2apk.$cleanSlug" else "com.web2apk.myapp"
            state.copy(
                appName = name,
                packageName = if (state.packageName.startsWith("com.web2apk.")) defaultPkg else state.packageName
            )
        }
    }

    fun setTargetUrl(url: String) {
        _uiState.update { it.copy(targetUrl = url) }
    }

    fun setIsLocalFile(isLocal: Boolean) {
        _uiState.update { it.copy(isLocalFile = isLocal) }
    }

    fun setLocalHtmlContent(content: String) {
        _uiState.update { it.copy(localHtmlContent = content) }
    }

    fun setThemeColor(colorHex: String) {
        _uiState.update { it.copy(themeColorHex = colorHex) }
    }

    fun selectPreset(presetId: String) {
        _uiState.update { it.copy(selectedPresetId = presetId, customIconBitmap = null) }
    }

    fun setCustomIcon(bitmap: Bitmap?) {
        _uiState.update { it.copy(customIconBitmap = bitmap) }
    }

    fun setEnablePrint(enable: Boolean) {
        _uiState.update { it.copy(enablePrint = enable) }
    }

    fun setPackageName(pkg: String) {
        _uiState.update { it.copy(packageName = pkg) }
    }

    fun setVersionName(version: String) {
        _uiState.update { it.copy(versionName = version) }
    }

    fun applyPresetSample(name: String, url: String, icon: String, color: String) {
        setAppName(name)
        setTargetUrl(url)
        setIsLocalFile(false)
        selectPreset(icon)
        setThemeColor(color)
    }

    fun openPreviewCurrent() {
        val state = _uiState.value
        val tempApk = GeneratedApk(
            name = state.appName,
            packageName = state.packageName,
            targetUrl = state.targetUrl,
            isLocalFile = state.isLocalFile,
            localHtmlContent = state.localHtmlContent,
            filePath = "",
            fileSizeBytes = 0,
            themeColorHex = state.themeColorHex,
            enablePrint = state.enablePrint
        )
        _uiState.update { it.copy(previewApk = tempApk) }
    }

    fun openPreviewForApk(apk: GeneratedApk) {
        _uiState.update { it.copy(previewApk = apk) }
    }

    fun dismissPreview() {
        _uiState.update { it.copy(previewApk = null) }
    }

    fun openInspect(apk: GeneratedApk) {
        _uiState.update { it.copy(apkToInspect = apk) }
    }

    fun dismissInspect() {
        _uiState.update { it.copy(apkToInspect = null) }
    }

    fun dismissNewlyBuiltDialog() {
        _uiState.update { it.copy(newlyBuiltApk = null) }
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun buildApk(context: Context) {
        val state = _uiState.value
        if (state.appName.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Please enter an application name.") }
            return
        }
        if (!state.isLocalFile && (state.targetUrl.isBlank() || !state.targetUrl.contains("."))) {
            _uiState.update { it.copy(errorMessage = "Please enter a valid website URL (e.g. https://example.com).") }
            return
        }

        var normalizedUrl = state.targetUrl.trim()
        if (!state.isLocalFile && !normalizedUrl.startsWith("http://") && !normalizedUrl.startsWith("https://")) {
            normalizedUrl = "https://$normalizedUrl"
        }

        _uiState.update {
            it.copy(
                isBuilding = true,
                buildProgress = 0.05f,
                buildStatus = "Starting build pipeline..."
            )
        }

        viewModelScope.launch {
            try {
                val apk = withContext(Dispatchers.IO) {
                    ApkBuilder.buildApk(
                        context = context.applicationContext,
                        name = state.appName,
                        packageName = state.packageName,
                        targetUrl = normalizedUrl,
                        isLocalFile = state.isLocalFile,
                        localHtmlContent = state.localHtmlContent,
                        versionName = state.versionName,
                        themeColorHex = state.themeColorHex,
                        iconPresetName = state.selectedPresetId,
                        customIconBitmap = state.customIconBitmap,
                        enablePrint = state.enablePrint,
                        onProgress = { prog, status ->
                            _uiState.update {
                                it.copy(buildProgress = prog, buildStatus = status)
                            }
                        }
                    )
                }

                val savedId = repository.insert(apk)
                val fullApk = apk.copy(id = savedId)

                _uiState.update {
                    it.copy(
                        isBuilding = false,
                        newlyBuiltApk = fullApk
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isBuilding = false,
                        errorMessage = "Build failed: ${e.localizedMessage ?: e.message}"
                    )
                }
            }
        }
    }

    fun installApk(context: Context, apk: GeneratedApk) {
        try {
            val file = File(apk.filePath)
            if (!file.exists()) {
                Toast.makeText(context, "APK file not found on storage.", Toast.LENGTH_SHORT).show()
                return
            }

            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            context.startActivity(installIntent)
        } catch (e: Exception) {
            Toast.makeText(context, "Cannot launch installer: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    fun shareApk(context: Context, apk: GeneratedApk) {
        try {
            val file = File(apk.filePath)
            if (!file.exists()) {
                Toast.makeText(context, "APK file not found on storage.", Toast.LENGTH_SHORT).show()
                return
            }

            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/vnd.android.package-archive"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "${apk.name} APK")
                putExtra(Intent.EXTRA_TEXT, "Here is the standalone APK for ${apk.name}")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Share ${apk.name} APK"))
        } catch (e: Exception) {
            Toast.makeText(context, "Cannot share APK: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    fun deleteApk(apk: GeneratedApk) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val file = File(apk.filePath)
                if (file.exists()) file.delete()
                apk.customIconPath?.let {
                    val iconFile = File(it)
                    if (iconFile.exists()) iconFile.delete()
                }
                repository.delete(apk)
            } catch (ignored: Exception) {}
        }
    }

    companion object {
        fun provideFactory(activity: ComponentActivity): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val db = AppDatabase.getDatabase(activity)
                    val repo = ApkRepository(db.generatedApkDao())
                    return ApkCreatorViewModel(repo) as T
                }
            }
        }
    }
}
