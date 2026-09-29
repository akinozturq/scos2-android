package com.awork.camera6.camera

sealed interface CameraResult {
    data class PhotoSuccess(val fileUri: String, val filePath: String? = null) : CameraResult
    data class VideoSuccess(val fileUri: String) : CameraResult
    data class Failure(val error: CameraError) : CameraResult
}

sealed interface CameraError {
    data class CaptureFailed(val message: String, val cause: Throwable? = null) : CameraError
    data class RecordingFailed(val message: String, val cause: Throwable? = null) : CameraError
    data class InitializationFailed(val message: String, val cause: Throwable? = null) : CameraError
    data class StateConflict(val current: CameraState, val attemptedAction: String) : CameraError
}
