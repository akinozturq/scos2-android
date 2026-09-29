package com.awork.camera6.camera

/**
 * Explicit state machine for Camera operations.
 * Prevents race conditions and boolean flag synchronization issues.
 */
sealed interface CameraState {
    data object Idle : CameraState
    data object Capturing : CameraState
    data class Burst(val current: Int, val total: Int) : CameraState
    data object AutoCapturing : CameraState
    data object Recording : CameraState

    val isRecording: Boolean
        get() = this is Recording

    val isAutoCapturing: Boolean
        get() = this is AutoCapturing

    val isBusy: Boolean
        get() = this !is Idle
}
