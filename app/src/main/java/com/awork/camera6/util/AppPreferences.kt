package com.awork.camera6.util

/**
 * Immutable preferences model for SCOS.
 */
data class AppPreferences(
    val burstCount: Int = 5,
    val autoDelay: Int = 3,
    val startMode: String = "normal",
    val savePath: String = "",
    val hideFolder: Boolean = false,
    val disableToast: Boolean = false,
    val disableShutter: Boolean = true,
    val disableVibration: Boolean = false,
    val volumeUpAction: String = "capture",
    val volumeDownAction: String = "video",
    val defaultCamera: String = "back"
)
