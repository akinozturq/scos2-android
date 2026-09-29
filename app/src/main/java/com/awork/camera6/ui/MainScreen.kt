package com.awork.camera6.ui

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.awork.camera6.SpyCamService

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onStartService: () -> Unit = {},
    onOpenSettings: () -> Unit = {}
) {
    val context = LocalContext.current
    var serviceRunning by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SCOS", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (!serviceRunning) {
                Button(
                    onClick = { serviceRunning = true; onStartService() },
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Start Camera Service", fontSize = 18.sp)
                }
            } else {
                Text(
                    "Camera Active",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                CameraControlGrid()
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        context.sendBroadcast(Intent(SpyCamService.ACTION_TOGGLE_OVERLAY).setPackage(context.packageName))
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Visibility, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Toggle Overlay")
                }
            }

            Spacer(Modifier.height(16.dp))
            OutlinedButton(
                onClick = {
                    context.startActivity(Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS))
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.VolumeUp, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Enable Hardware Volume Keys")
            }
        }
    }
}

@Composable
fun CameraControlGrid() {
    val context = LocalContext.current
    val controls = listOf(
        ControlItem("Single", Icons.Default.CameraAlt, SpyCamService.ACTION_CAPTURE_SINGLE),
        ControlItem("Burst", Icons.Default.BurstMode, SpyCamService.ACTION_CAPTURE_BURST),
        ControlItem("Auto", Icons.Default.Timer, SpyCamService.ACTION_CAPTURE_AUTO),
        ControlItem("Video", Icons.Default.Videocam, SpyCamService.ACTION_RECORD_VIDEO),
        ControlItem("Black", Icons.Default.DarkMode, SpyCamService.ACTION_BLACK_MODE),
    )

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        controls.chunked(3).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                row.forEach { item ->
                    ControlButton(
                        item = item,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            context.sendBroadcast(Intent(item.action).setPackage(context.packageName))
                        }
                    )
                }
                if (row.size < 3) {
                    Spacer(Modifier.weight(3 - row.size.toFloat()))
                }
            }
        }
    }
}

data class ControlItem(val label: String, val icon: ImageVector, val action: String)

@Composable
fun ControlButton(item: ControlItem, modifier: Modifier = Modifier, onClick: () -> Unit = {}) {
    Button(
        onClick = onClick,
        modifier = modifier.height(72.dp),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(item.icon, contentDescription = item.label, modifier = Modifier.size(24.dp))
            Spacer(Modifier.height(4.dp))
            Text(item.label, fontSize = 12.sp)
        }
    }
}
