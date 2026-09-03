package com.example.ui.screens

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.GeneratedApk
import com.example.engine.IconHelper
import com.example.ui.ApkCreatorUiState
import com.example.ui.ApkCreatorViewModel
import com.example.ui.components.ApkDetailsDialog
import com.example.ui.components.WebKioskPreviewDialog
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: ApkCreatorViewModel) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val allApks by viewModel.allApks.collectAsState()

    var selectedTabIndex by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("📦", style = MaterialTheme.typography.titleMedium)
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "APK Creator",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Web to Minimal Standalone APK",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            SecondaryTabRow(
                selectedTabIndex = selectedTabIndex,
                modifier = Modifier.fillMaxWidth()
            ) {
                Tab(
                    selected = selectedTabIndex == 0,
                    onClick = { selectedTabIndex = 0 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Create APK", fontWeight = FontWeight.SemiBold)
                        }
                    },
                    modifier = Modifier.testTag("tab_create_apk")
                )
                Tab(
                    selected = selectedTabIndex == 1,
                    onClick = { selectedTabIndex = 1 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            BadgedBox(
                                badge = {
                                    if (allApks.isNotEmpty()) {
                                        Badge { Text("${allApks.size}") }
                                    }
                                }
                            ) {
                                Icon(imageVector = Icons.Default.Build, contentDescription = null)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("My APKs", fontWeight = FontWeight.SemiBold)
                        }
                    },
                    modifier = Modifier.testTag("tab_my_apks")
                )
            }

            Box(modifier = Modifier.weight(1f)) {
                if (selectedTabIndex == 0) {
                    CreateApkContent(
                        uiState = uiState,
                        viewModel = viewModel,
                        onSwitchToMyApks = { selectedTabIndex = 1 }
                    )
                } else {
                    MyApksContent(
                        apks = allApks,
                        viewModel = viewModel,
                        onSwitchToCreate = { selectedTabIndex = 0 }
                    )
                }
            }
        }
    }

    // Live Preview Dialog
    uiState.previewApk?.let { apk ->
        WebKioskPreviewDialog(
            apk = apk,
            onDismiss = { viewModel.dismissPreview() }
        )
    }

    // Inspect Details Dialog
    uiState.apkToInspect?.let { apk ->
        ApkDetailsDialog(
            apk = apk,
            onDismiss = { viewModel.dismissInspect() },
            onInstall = { viewModel.installApk(context, apk) },
            onShare = { viewModel.shareApk(context, apk) },
            onPreview = {
                viewModel.dismissInspect()
                viewModel.openPreviewForApk(apk)
            }
        )
    }

    // Build Progress Dialog
    if (uiState.isBuilding) {
        AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            title = {
                Text("Building APK Package...", fontWeight = FontWeight.Bold)
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    LinearProgressIndicator(
                        progress = { uiState.buildProgress },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = uiState.buildStatus,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        )
    }

    // Newly Built APK Success Dialog
    uiState.newlyBuiltApk?.let { apk ->
        val sizeKb = apk.fileSizeBytes / 1024.0
        AlertDialog(
            onDismissRequest = { viewModel.dismissNewlyBuiltDialog() },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("APK Created Successfully!", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column {
                    Text(
                        text = "Your minimal standalone installable APK is ready.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = apk.name,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = apk.packageName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Size: %.1f KB • Minimal Standalone".format(sizeKb),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            if (apk.enablePrint) {
                                Text(
                                    text = "✓ Print to PDF Enabled",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.installApk(context, apk)
                        viewModel.dismissNewlyBuiltDialog()
                    },
                    modifier = Modifier.testTag("success_install_button")
                ) {
                    Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Install Now")
                }
            },
            dismissButton = {
                Row {
                    OutlinedButton(
                        onClick = {
                            viewModel.shareApk(context, apk)
                        },
                        modifier = Modifier.testTag("success_share_button")
                    ) {
                        Icon(imageVector = Icons.Default.Share, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Share")
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    TextButton(onClick = { viewModel.dismissNewlyBuiltDialog() }) {
                        Text("Done")
                    }
                }
            }
        )
    }

    // Error Dialog
    uiState.errorMessage?.let { error ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissError() },
            title = { Text("Error") },
            text = { Text(error) },
            confirmButton = {
                Button(onClick = { viewModel.dismissError() }) {
                    Text("OK")
                }
            }
        )
    }
}

@Composable
fun CreateApkContent(
    uiState: ApkCreatorUiState,
    viewModel: ApkCreatorViewModel,
    onSwitchToMyApks: () -> Unit
) {
    val context = LocalContext.current
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val bitmap = BitmapFactory.decodeStream(stream)
                    viewModel.setCustomIcon(bitmap)
                }
            } catch (ignored: Exception) {}
        }
    }

    var showAdvanced by remember { mutableStateOf(false) }

    val themeColors = listOf(
        "#2563EB" to "Blue",
        "#4F46E5" to "Indigo",
        "#0D9488" to "Teal",
        "#10B981" to "Emerald",
        "#D97706" to "Amber",
        "#E11D48" to "Rose",
        "#7C3AED" to "Purple",
        "#334155" to "Slate"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Hero Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
            ),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Convert Any Webpage into an APK",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Generates a minimal, ultra-compact Android app with hidden URL bar, full offline/online capabilities, and built-in Print as PDF support.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                )

                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Quick Presets:",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        PresetChip("Wikipedia", "https://en.wikipedia.org", "web", "#2563EB") {
                            viewModel.applyPresetSample("Wikipedia", "https://en.wikipedia.org", "web", "#2563EB")
                        }
                    }
                    item {
                        PresetChip("Dev Docs", "https://devdocs.io", "code", "#EA580C") {
                            viewModel.applyPresetSample("Dev Docs", "https://devdocs.io", "code", "#EA580C")
                        }
                    }
                    item {
                        PresetChip("Hacker News", "https://news.ycombinator.com", "news", "#D97706") {
                            viewModel.applyPresetSample("Hacker News", "https://news.ycombinator.com", "news", "#D97706")
                        }
                    }
                    item {
                        PresetChip("W3Schools", "https://www.w3schools.com", "book", "#0D9488") {
                            viewModel.applyPresetSample("W3Schools", "https://www.w3schools.com", "book", "#0D9488")
                        }
                    }
                }
            }
        }

        // Section 1: Target Destination
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "1. Target Address & Source",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(12.dp))

                // Toggle URL vs Local File
                Row(modifier = Modifier.fillMaxWidth()) {
                    FilterChip(
                        selected = !uiState.isLocalFile,
                        onClick = { viewModel.setIsLocalFile(false) },
                        label = { Text("Web URL / Address") },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("source_web_url_chip")
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    FilterChip(
                        selected = uiState.isLocalFile,
                        onClick = { viewModel.setIsLocalFile(true) },
                        label = { Text("Local HTML / File") },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("source_local_html_chip")
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (!uiState.isLocalFile) {
                    OutlinedTextField(
                        value = uiState.targetUrl,
                        onValueChange = { viewModel.setTargetUrl(it) },
                        label = { Text("Website URL or Address") },
                        placeholder = { Text("https://yourwebsite.com") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("target_url_input"),
                        shape = RoundedCornerShape(12.dp)
                    )
                } else {
                    OutlinedTextField(
                        value = uiState.localHtmlContent,
                        onValueChange = { viewModel.setLocalHtmlContent(it) },
                        label = { Text("Embedded Local HTML Code") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .testTag("local_html_input"),
                        shape = RoundedCornerShape(12.dp),
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                    )
                }
            }
        }

        // Section 2: App Name & Identity
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "2. App Name & Icon",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = uiState.appName,
                    onValueChange = { viewModel.setAppName(it) },
                    label = { Text("Application Name") },
                    placeholder = { Text("My Web App") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("app_name_input"),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Icon Preview & Selection
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Live Icon Preview Box
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(18.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (uiState.customIconBitmap != null) {
                            Image(
                                bitmap = uiState.customIconBitmap.asImageBitmap(),
                                contentDescription = "Custom Icon Preview",
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            val preset = IconHelper.getPreset(uiState.selectedPresetId)
                            val tintColor = try {
                                Color(android.graphics.Color.parseColor(uiState.themeColorHex))
                            } catch (e: Exception) {
                                Color(preset.defaultBgColor)
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(tintColor),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = preset.symbolChar,
                                    style = MaterialTheme.typography.headlineMedium
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (uiState.customIconBitmap != null) "Custom Image Selected" else "Preset Icon: ${IconHelper.getPreset(uiState.selectedPresetId).name}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedButton(
                            onClick = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            modifier = Modifier.testTag("upload_custom_icon_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Photo,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Upload Image")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Choose Preset Icon:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(IconHelper.presets) { preset ->
                        val isSelected = uiState.selectedPresetId == preset.id && uiState.customIconBitmap == null
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { viewModel.selectPreset(preset.id) }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(preset.symbolChar, style = MaterialTheme.typography.titleMedium)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = preset.name,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }
            }
        }

        // Section 3: Theme Accent Color
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "3. Theme Accent Color",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(10.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(themeColors) { (hex, name) ->
                        val isSelected = uiState.themeColorHex.equals(hex, ignoreCase = true)
                        val color = Color(android.graphics.Color.parseColor(hex))
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(color)
                                .border(
                                    width = if (isSelected) 3.dp else 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.4f),
                                    shape = CircleShape
                                )
                                .clickable { viewModel.setThemeColor(hex) },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = name,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Section 4: Print to PDF Option
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Print,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Add Option to Print as PDF",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Embeds a floating action button in the generated APK to print pages to PDF",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = uiState.enablePrint,
                    onCheckedChange = { viewModel.setEnablePrint(it) },
                    modifier = Modifier.testTag("enable_print_switch")
                )
            }
        }

        // Advanced Package Settings (Collapsible)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showAdvanced = !showAdvanced },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Advanced Package Settings",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Icon(
                        imageVector = if (showAdvanced) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null
                    )
                }

                AnimatedVisibility(visible = showAdvanced) {
                    Column(modifier = Modifier.padding(top = 12.dp)) {
                        OutlinedTextField(
                            value = uiState.packageName,
                            onValueChange = { viewModel.setPackageName(it) },
                            label = { Text("Application Package ID") },
                            placeholder = { Text("com.web2apk.myapp") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("package_name_input"),
                            shape = RoundedCornerShape(12.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = uiState.versionName,
                            onValueChange = { viewModel.setVersionName(it) },
                            label = { Text("Version Name") },
                            placeholder = { Text("1.0") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("version_name_input"),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }
            }
        }

        // Action Buttons Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = { viewModel.openPreviewCurrent() },
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
                    .testTag("preview_current_button")
            ) {
                Icon(imageVector = Icons.Default.Visibility, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Live Preview", fontWeight = FontWeight.Bold)
            }

            Button(
                onClick = { viewModel.buildApk(context) },
                modifier = Modifier
                    .weight(1.4f)
                    .height(52.dp)
                    .testTag("build_apk_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(imageVector = Icons.Default.Build, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Build Minimal APK", fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun MyApksContent(
    apks: List<GeneratedApk>,
    viewModel: ApkCreatorViewModel,
    onSwitchToCreate: () -> Unit
) {
    val context = LocalContext.current
    var apkToDelete by remember { mutableStateOf<GeneratedApk?>(null) }

    if (apks.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.size(80.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("📱", style = MaterialTheme.typography.displaySmall)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "No Generated APKs Yet",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Create your first minimal web kiosk APK to install or share with others.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = onSwitchToCreate,
                    modifier = Modifier.testTag("create_first_apk_button")
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Create New APK")
                }
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Built Applications (${apks.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Stored locally on device",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            items(apks, key = { it.id }) { apk ->
                ApkItemCard(
                    apk = apk,
                    onInstall = { viewModel.installApk(context, apk) },
                    onShare = { viewModel.shareApk(context, apk) },
                    onPreview = { viewModel.openPreviewForApk(apk) },
                    onInspect = { viewModel.openInspect(apk) },
                    onDelete = { apkToDelete = apk }
                )
            }
        }
    }

    // Delete Confirmation Dialog
    apkToDelete?.let { apk ->
        AlertDialog(
            onDismissRequest = { apkToDelete = null },
            title = { Text("Delete APK?") },
            text = { Text("This will permanently delete the APK file '${apk.name}.apk' from your device.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteApk(apk)
                        apkToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { apkToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun ApkItemCard(
    apk: GeneratedApk,
    onInstall: () -> Unit,
    onShare: () -> Unit,
    onPreview: () -> Unit,
    onInspect: () -> Unit,
    onDelete: () -> Unit
) {
    val sizeKb = apk.fileSizeBytes / 1024.0
    val formattedDate = remember(apk.createdAt) {
        val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        sdf.format(Date(apk.createdAt))
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("apk_item_card_${apk.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Icon Avatar
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            try {
                                Color(android.graphics.Color.parseColor(apk.themeColorHex))
                            } catch (e: Exception) {
                                MaterialTheme.colorScheme.primary
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    val preset = IconHelper.getPreset(apk.iconPresetName)
                    Text(preset.symbolChar, style = MaterialTheme.typography.titleLarge)
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = apk.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = apk.packageName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (apk.isLocalFile) "Embedded Local HTML" else apk.targetUrl,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(onClick = onInspect) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Inspect Details",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Chips Row: Size & Print to PDF
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    ) {
                        Text(
                            text = "%.1f KB • Minimal".format(sizeKb),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    if (apk.enablePrint) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
                        ) {
                            Text(
                                text = "PDF Print",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                Text(
                    text = formattedDate,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onInstall,
                    modifier = Modifier.weight(1.2f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Install", fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = onShare,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Share")
                }

                OutlinedButton(
                    onClick = onPreview,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Visibility,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Preview")
                }

                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete APK",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun PresetChip(
    title: String,
    url: String,
    icon: String,
    colorHex: String,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val preset = IconHelper.getPreset(icon)
            Text(preset.symbolChar, style = MaterialTheme.typography.bodySmall)
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
