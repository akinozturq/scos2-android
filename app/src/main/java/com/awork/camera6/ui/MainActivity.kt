package com.awork.camera6.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.awork.camera6.SCOSApplication
import com.awork.camera6.SpyCamService
import com.awork.camera6.ui.theme.SCOSTheme
import com.awork.camera6.util.PreferencesManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var prefs: PreferencesManager

    private var cachedVolumeUpAction: String = "capture"
    private var cachedVolumeDownAction: String = "video"

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            requestOverlayPermission()
        } else {
            Toast.makeText(this, "Permissions required", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (checkPermissions()) {
            requestOverlayPermission()
        }
        setContent {
            SCOSTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(
                        onStartService = { startSpyService() },
                        onOpenSettings = { openSettings() }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        cachedVolumeUpAction = prefs.volumeUpAction
        cachedVolumeDownAction = prefs.volumeDownAction
    }

    private fun checkPermissions(): Boolean {
        val permissions = mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        return if (missing.isEmpty()) {
            true
        } else {
            requestPermissionLauncher.launch(missing.toTypedArray())
            false
        }
    }

    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            !Settings.canDrawOverlays(this)
        ) {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
        }
    }

    private fun startSpyService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Please grant Overlay permission first", Toast.LENGTH_LONG).show()
            requestOverlayPermission()
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(Intent(this, SpyCamService::class.java))
        } else {
            startService(Intent(this, SpyCamService::class.java))
        }
        moveTaskToBack(true)
    }

    private fun openSettings() {
        startActivity(Intent(this, SettingsActivity::class.java))
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val isUp = event.keyCode == KeyEvent.KEYCODE_VOLUME_UP
        val isDown = event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
        if (isUp || isDown) {
            val action = if (isUp) cachedVolumeUpAction else cachedVolumeDownAction
            if (action != "none") {
                if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                    if (action == "black") {
                        startActivity(Intent(this, BlackScreenActivity::class.java))
                    } else {
                        val intentAction = when (action) {
                            "burst" -> SpyCamService.ACTION_CAPTURE_BURST
                            "auto" -> SpyCamService.ACTION_CAPTURE_AUTO
                            "video" -> SpyCamService.ACTION_RECORD_VIDEO
                            else -> SpyCamService.ACTION_CAPTURE_SINGLE
                        }
                        sendBroadcast(Intent(intentAction).setPackage(packageName))
                    }
                }
                return true // Consume BOTH ACTION_DOWN and ACTION_UP
            }
        }
        return super.dispatchKeyEvent(event)
    }
}
