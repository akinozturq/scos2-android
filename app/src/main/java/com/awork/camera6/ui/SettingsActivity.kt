package com.awork.camera6.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.awork.camera6.ui.theme.SCOSTheme
import com.awork.camera6.util.FileManager
import com.awork.camera6.util.PreferencesManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class SettingsActivity : ComponentActivity() {

    @Inject
    lateinit var prefs: PreferencesManager
    @Inject
    lateinit var fileManager: FileManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SCOSTheme {
                SettingsScreen(prefs, fileManager, onBack = { finish() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    prefs: PreferencesManager,
    fileManager: FileManager,
    onBack: () -> Unit = {}
) {
    var cameraMode by remember { mutableStateOf(if (prefs.defaultCamera.equals("front", ignoreCase = true)) "Front" else "Rear") }
    var startMode by remember { mutableStateOf(when(prefs.startMode) { "black" -> "Black Screen"; "minimized" -> "Minimized"; else -> "Normal (Overlay)" }) }
    var burstCount by remember { mutableStateOf(prefs.burstCount.toFloat()) }
    var autoDelay by remember { mutableStateOf(prefs.autoDelay.toFloat()) }
    var disableToast by remember { mutableStateOf(prefs.disableToast) }
    var disableShutter by remember { mutableStateOf(prefs.disableShutter) }
    var disableVibration by remember { mutableStateOf(prefs.disableVibration) }
    var hideFolder by remember { mutableStateOf(prefs.hideFolder) }
    var savePath by remember { mutableStateOf(prefs.savePath) }
    var volumeUpAction by remember { mutableStateOf(prefs.volumeUpAction) }
    var volumeDownAction by remember { mutableStateOf(prefs.volumeDownAction) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            SettingsSection("Camera") {
                SettingsDropdown(
                    label = "Default Camera",
                    value = cameraMode,
                    options = listOf("Rear", "Front"),
                    onSelect = { selected ->
                        cameraMode = selected
                        prefs.defaultCamera = if (selected == "Front") "front" else "back"
                    }
                )
                SettingsDropdown(
                    label = "Start Mode",
                    value = startMode,
                    options = listOf("Normal (Overlay)", "Minimized", "Black Screen"),
                    onSelect = { selected ->
                        startMode = selected
                        prefs.startMode = when (selected) {
                            "Black Screen" -> "black"
                            "Minimized" -> "minimized"
                            else -> "normal"
                        }
                    }
                )
            }

            SettingsSection("Capture") {
                SettingsSlider(
                    label = "Burst Count",
                    value = burstCount,
                    range = 1f..20f,
                    onValueChange = {
                        burstCount = it
                        prefs.burstCount = it.toInt()
                    }
                )
                SettingsSlider(
                    label = "Auto Delay (s)",
                    value = autoDelay,
                    range = 1f..60f,
                    onValueChange = {
                        autoDelay = it
                        prefs.autoDelay = it.toInt()
                    }
                )
            }

            SettingsSection("Stealth") {
                SettingsToggle(
                    label = "Disable Toast Messages",
                    checked = disableToast,
                    onCheckedChange = {
                        disableToast = it
                        prefs.disableToast = it
                    }
                )
                SettingsToggle(
                    label = "Disable Shutter Sound",
                    checked = disableShutter,
                    onCheckedChange = {
                        disableShutter = it
                        prefs.disableShutter = it
                    }
                )
                SettingsToggle(
                    label = "Disable Vibration",
                    checked = disableVibration,
                    onCheckedChange = {
                        disableVibration = it
                        prefs.disableVibration = it
                    }
                )
                SettingsDropdown(
                    label = "Volume UP Button",
                    value = when (volumeUpAction) {
                        "burst" -> "Burst Photo"
                        "auto" -> "Continuous Capture"
                        "video" -> "Record Video"
                        "black" -> "Black Screen"
                        "none" -> "None (System Volume)"
                        else -> "Single Photo"
                    },
                    options = listOf("Single Photo", "Burst Photo", "Continuous Capture", "Record Video", "Black Screen", "None (System Volume)"),
                    onSelect = { selected ->
                        val code = when (selected) {
                            "Burst Photo" -> "burst"
                            "Continuous Capture" -> "auto"
                            "Record Video" -> "video"
                            "Black Screen" -> "black"
                            "None (System Volume)" -> "none"
                            else -> "capture"
                        }
                        volumeUpAction = code
                        prefs.volumeUpAction = code
                    }
                )
                SettingsDropdown(
                    label = "Volume DOWN Button",
                    value = when (volumeDownAction) {
                        "burst" -> "Burst Photo"
                        "auto" -> "Continuous Capture"
                        "video" -> "Record Video"
                        "black" -> "Black Screen"
                        "none" -> "None (System Volume)"
                        else -> "Single Photo"
                    },
                    options = listOf("Single Photo", "Burst Photo", "Continuous Capture", "Record Video", "Black Screen", "None (System Volume)"),
                    onSelect = { selected ->
                        val code = when (selected) {
                            "Burst Photo" -> "burst"
                            "Continuous Capture" -> "auto"
                            "Record Video" -> "video"
                            "Black Screen" -> "black"
                            "None (System Volume)" -> "none"
                            else -> "capture"
                        }
                        volumeDownAction = code
                        prefs.volumeDownAction = code
                    }
                )
            }

            SettingsSection("Storage") {
                SettingsToggle(
                    label = "Hide Folder (.nomedia)",
                    checked = hideFolder,
                    onCheckedChange = {
                        hideFolder = it
                        prefs.hideFolder = it
                        val saveDir = fileManager.getSaveDirectory(savePath)
                        fileManager.toggleHidden(saveDir, it)
                    }
                )
                OutlinedTextField(
                    value = savePath,
                    onValueChange = {
                        savePath = it
                        prefs.savePath = it
                    },
                    label = { Text("Custom Folder Name / Path (optional)") },
                    placeholder = { Text("Default: Pictures/SCOS") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(8.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                content()
            }
        }
    }
}

@Composable
fun SettingsToggle(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun SettingsSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onValueChange: (Float) -> Unit) {
    Column {
        Text("$label: ${value.toInt()}")
        Slider(value = value, onValueChange = onValueChange, valueRange = range)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDropdown(label: String, value: String, options: List<String>, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    }
                )
            }
        }
    }
}
