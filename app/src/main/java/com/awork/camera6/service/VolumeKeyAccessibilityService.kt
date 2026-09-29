package com.awork.camera6.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import com.awork.camera6.SpyCamService
import com.awork.camera6.util.PreferencesManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class VolumeKeyAccessibilityService : AccessibilityService() {

    @Inject
    lateinit var preferences: PreferencesManager

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val isUp = event.keyCode == KeyEvent.KEYCODE_VOLUME_UP
        val isDown = event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN

        if (isUp || isDown) {
            val action = if (isUp) preferences.volumeUpAction else preferences.volumeDownAction
            if (action != "none") {
                if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                    val intentAction = when (action) {
                        "burst" -> SpyCamService.ACTION_CAPTURE_BURST
                        "auto" -> SpyCamService.ACTION_CAPTURE_AUTO
                        "video" -> SpyCamService.ACTION_RECORD_VIDEO
                        else -> SpyCamService.ACTION_CAPTURE_SINGLE
                    }
                    sendBroadcast(Intent(intentAction).setPackage(packageName))
                }
                return true // Consume BOTH ACTION_DOWN and ACTION_UP! Stops system volume change!
            }
        }
        return super.onKeyEvent(event)
    }
}
