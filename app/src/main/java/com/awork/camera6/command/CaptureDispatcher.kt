package com.awork.camera6.command

import android.content.Context
import android.content.Intent

/**
 * Single dispatcher for sending commands to SpyCamService.
 */
object CaptureDispatcher {

    fun dispatch(context: Context, command: CaptureCommand) {
        val intent = Intent(command.toAction()).apply {
            setPackage(context.packageName)
        }
        context.sendBroadcast(intent)
    }
}
