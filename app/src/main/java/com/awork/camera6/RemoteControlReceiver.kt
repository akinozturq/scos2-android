package com.awork.camera6

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.view.KeyEvent

class RemoteControlReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (Intent.ACTION_MEDIA_BUTTON == intent.action) {
            val event = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT)
            }
            if (event != null && event.action == KeyEvent.ACTION_DOWN) {
                val action = when (event.keyCode) {
                    KeyEvent.KEYCODE_VOLUME_UP -> SpyCamService.ACTION_CAPTURE_SINGLE
                    KeyEvent.KEYCODE_VOLUME_DOWN -> SpyCamService.ACTION_RECORD_VIDEO
                    KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_HEADSETHOOK -> SpyCamService.ACTION_CAPTURE_SINGLE
                    else -> null
                }
                if (action != null) {
                    context.sendBroadcast(Intent(action).setPackage(context.packageName))
                }
            }
        }
    }
}
