package com.awork.camera6.command

/**
 * Unified capture commands for all triggers (Activity key events, MediaSession volume keys,
 * Widgets, Overlay buttons, and Notification actions).
 */
enum class CaptureCommand {
    SINGLE_CAPTURE,
    BURST_CAPTURE,
    AUTO_CAPTURE,
    STOP_AUTO_CAPTURE,
    RECORD_VIDEO,
    STOP_RECORDING,
    SWITCH_CAMERA,
    SHOW_OVERLAY,
    HIDE_OVERLAY,
    TOGGLE_OVERLAY,
    BLACK_MODE,
    EXIT;

    companion object {
        const val ACTION_CAPTURE_SINGLE = "com.awork.camera6.action.CAPTURE_SINGLE"
        const val ACTION_CAPTURE_BURST = "com.awork.camera6.action.CAPTURE_BURST"
        const val ACTION_CAPTURE_AUTO = "com.awork.camera6.action.CAPTURE_AUTO"
        const val ACTION_STOP_AUTO = "com.awork.camera6.action.STOP_AUTO"
        const val ACTION_RECORD_VIDEO = "com.awork.camera6.action.RECORD_VIDEO"
        const val ACTION_STOP_RECORDING = "com.awork.camera6.action.STOP_RECORDING"
        const val ACTION_SWITCH_CAMERA = "com.awork.camera6.action.SWITCH_CAMERA"
        const val ACTION_SHOW_OVERLAY = "com.awork.camera6.action.SHOW_OVERLAY"
        const val ACTION_HIDE_OVERLAY = "com.awork.camera6.action.HIDE_OVERLAY"
        const val ACTION_TOGGLE_OVERLAY = "com.awork.camera6.action.TOGGLE_OVERLAY"
        const val ACTION_BLACK_MODE = "com.awork.camera6.action.BLACK_MODE"
        const val ACTION_EXIT = "com.awork.camera6.action.EXIT"

        fun fromAction(action: String?): CaptureCommand? = when (action) {
            ACTION_CAPTURE_SINGLE -> SINGLE_CAPTURE
            ACTION_CAPTURE_BURST -> BURST_CAPTURE
            ACTION_CAPTURE_AUTO -> AUTO_CAPTURE
            ACTION_STOP_AUTO -> STOP_AUTO_CAPTURE
            ACTION_RECORD_VIDEO -> RECORD_VIDEO
            ACTION_STOP_RECORDING -> STOP_RECORDING
            ACTION_SWITCH_CAMERA -> SWITCH_CAMERA
            ACTION_SHOW_OVERLAY -> SHOW_OVERLAY
            ACTION_HIDE_OVERLAY -> HIDE_OVERLAY
            ACTION_TOGGLE_OVERLAY -> TOGGLE_OVERLAY
            ACTION_BLACK_MODE -> BLACK_MODE
            ACTION_EXIT -> EXIT
            else -> null
        }

        fun fromPreferenceAction(actionKey: String): CaptureCommand? = when (actionKey) {
            "capture" -> SINGLE_CAPTURE
            "burst" -> BURST_CAPTURE
            "auto" -> AUTO_CAPTURE
            "video" -> RECORD_VIDEO
            "black" -> BLACK_MODE
            else -> null
        }
    }

    fun toAction(): String = when (this) {
        SINGLE_CAPTURE -> ACTION_CAPTURE_SINGLE
        BURST_CAPTURE -> ACTION_CAPTURE_BURST
        AUTO_CAPTURE -> ACTION_CAPTURE_AUTO
        STOP_AUTO_CAPTURE -> ACTION_STOP_AUTO
        RECORD_VIDEO -> ACTION_RECORD_VIDEO
        STOP_RECORDING -> ACTION_STOP_RECORDING
        SWITCH_CAMERA -> ACTION_SWITCH_CAMERA
        SHOW_OVERLAY -> ACTION_SHOW_OVERLAY
        HIDE_OVERLAY -> ACTION_HIDE_OVERLAY
        TOGGLE_OVERLAY -> ACTION_TOGGLE_OVERLAY
        BLACK_MODE -> ACTION_BLACK_MODE
        EXIT -> ACTION_EXIT
    }
}
