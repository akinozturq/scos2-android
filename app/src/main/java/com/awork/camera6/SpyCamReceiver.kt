package com.awork.camera6

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class SpyCamReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val forwardIntent = Intent(intent.action).apply {
            setPackage(context.packageName)
        }
        context.sendBroadcast(forwardIntent)
    }
}
