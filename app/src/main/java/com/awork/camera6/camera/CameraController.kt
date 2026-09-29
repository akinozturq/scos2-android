package com.awork.camera6.camera

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaActionSound
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.MediaStore
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.UseCase
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.awork.camera6.util.FileManager
import com.awork.camera6.util.PreferencesManager
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraController(
    private val context: Context,
    private val preferences: PreferencesManager,
    private val fileManager: FileManager
) {
    private var cameraProvider: ProcessCameraProvider? = null
    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var preview: Preview? = null
    private var previewView: PreviewView? = null
    private var activeRecording: Recording? = null
    private var cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
    private var cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var lifecycleOwner: LifecycleOwner? = null
    private var isFrontCamera = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private var autoCaptureRunnable: Runnable? = null
    private var mediaActionSound: MediaActionSound? = null

    @Volatile
    var currentState: CameraState = CameraState.Idle
        private set

    val isRecording: Boolean get() = currentState is CameraState.Recording
    val isAutoCapturing: Boolean get() = currentState is CameraState.AutoCapturing

    var onStateChangedListener: ((CameraState) -> Unit)? = null
    var onResultListener: ((CameraResult) -> Unit)? = null

    init {
        isFrontCamera = preferences.defaultCamera.equals("front", ignoreCase = true)
        cameraSelector = if (isFrontCamera) {
            CameraSelector.DEFAULT_FRONT_CAMERA
        } else {
            CameraSelector.DEFAULT_BACK_CAMERA
        }
    }

    private fun updateState(newState: CameraState) {
        currentState = newState
        mainHandler.post { onStateChangedListener?.invoke(newState) }
    }

    fun setPreviewView(view: PreviewView?) {
        previewView = view
        bindUseCases()
    }

    fun startCamera(owner: LifecycleOwner? = null) {
        lifecycleOwner = owner
        if (cameraExecutor.isShutdown || cameraExecutor.isTerminated) {
            cameraExecutor = Executors.newSingleThreadExecutor()
        }
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                bindUseCases()
            } catch (e: Exception) {
                e.printStackTrace()
                onResultListener?.invoke(
                    CameraResult.Failure(CameraError.InitializationFailed("Failed to initialize camera provider", e))
                )
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun bindUseCases() {
        val provider = cameraProvider ?: return
        val owner = lifecycleOwner ?: return

        try {
            provider.unbindAll()

            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()

            val useCases = mutableListOf<UseCase>()
            imageCapture?.let { useCases.add(it) }

            previewView?.let { pv ->
                val p = Preview.Builder().build().also {
                    it.surfaceProvider = pv.surfaceProvider
                }
                preview = p
                useCases.add(p)
            }

            provider.bindToLifecycle(
                owner,
                cameraSelector,
                *useCases.toTypedArray()
            )
        } catch (e: Exception) {
            e.printStackTrace()
            onResultListener?.invoke(
                CameraResult.Failure(CameraError.InitializationFailed("Failed to bind use cases", e))
            )
        }
    }

    fun switchCamera() {
        if (isRecording) {
            stopRecording()
        }
        if (isAutoCapturing) {
            stopAutoCapture()
        }
        isFrontCamera = !isFrontCamera
        preferences.defaultCamera = if (isFrontCamera) "front" else "back"
        cameraSelector = if (isFrontCamera)
            CameraSelector.DEFAULT_FRONT_CAMERA
        else
            CameraSelector.DEFAULT_BACK_CAMERA
        bindUseCases()
    }

    fun captureSingle(onComplete: ((Boolean) -> Unit)? = null) {
        if (isRecording) {
            onResultListener?.invoke(
                CameraResult.Failure(CameraError.StateConflict(currentState, "captureSingle"))
            )
            onComplete?.invoke(false)
            return
        }

        val capture = imageCapture ?: run {
            bindUseCases()
            imageCapture ?: run {
                onComplete?.invoke(false)
                return
            }
        }

        if (currentState is CameraState.Idle) {
            updateState(CameraState.Capturing)
        }

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmssSSS", Locale.US).format(Date())
        val isHidden = preferences.hideFolder
        val fileName = "IMG_$timestamp.jpg"

        triggerHapticAndSound()

        val outputOptions: ImageCapture.OutputFileOptions = if (!isHidden && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val subDir = fileManager.getCleanFolderName(preferences.savePath)
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/$subDir")
            }
            ImageCapture.OutputFileOptions.Builder(
                context.contentResolver,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues
            ).build()
        } else {
            val saveDir = fileManager.getPhotoSaveDirectory(preferences.savePath, hidden = isHidden)
            val file = File(saveDir, fileName)
            ImageCapture.OutputFileOptions.Builder(file).build()
        }

        capture.takePicture(
            outputOptions,
            cameraExecutor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    if (currentState is CameraState.Capturing) {
                        updateState(CameraState.Idle)
                    }

                    val savedUri = output.savedUri
                    val uriString = savedUri?.toString() ?: ""
                    var filePath: String? = null

                    if (isHidden || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                        val file = File(fileManager.getPhotoSaveDirectory(preferences.savePath, hidden = isHidden), fileName)
                        filePath = file.absolutePath
                        if (file.exists() && !isHidden) {
                            notifyMediaScanner(file)
                        }
                    }

                    mainHandler.post {
                        onResultListener?.invoke(CameraResult.PhotoSuccess(uriString, filePath))
                        onComplete?.invoke(true)
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    if (currentState is CameraState.Capturing) {
                        updateState(CameraState.Idle)
                    }
                    mainHandler.post {
                        onResultListener?.invoke(
                            CameraResult.Failure(CameraError.CaptureFailed(exception.message ?: "Capture failed", exception))
                        )
                        onComplete?.invoke(false)
                    }
                }
            }
        )
    }

    fun captureBurst(count: Int) {
        if (isRecording || isAutoCapturing) return
        val total = count.coerceAtLeast(1)
        updateState(CameraState.Burst(1, total))

        fun executeNext(current: Int) {
            if (currentState !is CameraState.Burst) return
            updateState(CameraState.Burst(current, total))
            captureSingle { _ ->
                if (currentState !is CameraState.Burst) return@captureSingle
                if (current < total) {
                    mainHandler.postDelayed({
                        if (currentState is CameraState.Burst) {
                            executeNext(current + 1)
                        }
                    }, 400L)
                } else {
                    updateState(CameraState.Idle)
                }
            }
        }

        executeNext(1)
    }

    fun startAutoCapture(delaySeconds: Int) {
        if (isRecording) return

        if (isAutoCapturing) {
            stopAutoCapture()
            return
        }

        updateState(CameraState.AutoCapturing)
        val intervalMs = (delaySeconds.coerceAtLeast(1)) * 1000L

        fun scheduleNext() {
            if (currentState !is CameraState.AutoCapturing) return
            captureSingle {
                if (currentState is CameraState.AutoCapturing) {
                    autoCaptureRunnable = Runnable {
                        if (currentState is CameraState.AutoCapturing) {
                            scheduleNext()
                        }
                    }
                    mainHandler.postDelayed(autoCaptureRunnable!!, intervalMs)
                }
            }
        }

        scheduleNext()
    }

    fun stopAutoCapture() {
        autoCaptureRunnable?.let { mainHandler.removeCallbacks(it) }
        autoCaptureRunnable = null
        if (currentState is CameraState.AutoCapturing) {
            updateState(CameraState.Idle)
        }
    }

    fun startRecording() {
        if (isAutoCapturing) return

        if (isRecording) {
            stopRecording()
            return
        }

        val provider = cameraProvider ?: return
        val owner = lifecycleOwner ?: return

        try {
            provider.unbindAll()

            val recorder = Recorder.Builder()
                .setQualitySelector(
                    QualitySelector.fromOrderedList(
                        listOf(Quality.FHD, Quality.HD, Quality.SD),
                        FallbackStrategy.lowerQualityOrHigherThan(Quality.SD)
                    )
                )
                .build()
            videoCapture = VideoCapture.withOutput(recorder)

            val useCases = mutableListOf<UseCase>()
            videoCapture?.let { useCases.add(it) }

            previewView?.let { pv ->
                val p = Preview.Builder().build().also {
                    it.surfaceProvider = pv.surfaceProvider
                }
                preview = p
                useCases.add(p)
            }

            provider.bindToLifecycle(
                owner,
                cameraSelector,
                *useCases.toTypedArray()
            )

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmssSSS", Locale.US).format(Date())
            val isHidden = preferences.hideFolder
            val fileName = "VID_$timestamp.mp4"

            val pendingRecording = if (!isHidden && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val subDir = fileManager.getCleanFolderName(preferences.savePath)
                val videoContentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
                    put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/$subDir")
                }
                val mediaStoreOutput = MediaStoreOutputOptions.Builder(
                    context.contentResolver,
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                ).setContentValues(videoContentValues).build()
                videoCapture?.output?.prepareRecording(context, mediaStoreOutput)
            } else {
                val videoDir = fileManager.getVideoSaveDirectory(preferences.savePath, hidden = isHidden)
                val videoFile = File(videoDir, fileName)
                val fileOutputOptions = FileOutputOptions.Builder(videoFile).build()
                videoCapture?.output?.prepareRecording(context, fileOutputOptions)
            }

            val recordingWithAudio = if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                pendingRecording?.withAudioEnabled()
            } else {
                pendingRecording
            }

            activeRecording = recordingWithAudio?.start(ContextCompat.getMainExecutor(context)) { recordEvent ->
                when (recordEvent) {
                    is VideoRecordEvent.Finalize -> {
                        updateState(CameraState.Idle)
                        if (recordEvent.hasError()) {
                            onResultListener?.invoke(
                                CameraResult.Failure(CameraError.RecordingFailed(
                                    recordEvent.cause?.message ?: "Video recording error ${recordEvent.error}",
                                    recordEvent.cause
                                ))
                            )
                        } else {
                            onResultListener?.invoke(
                                CameraResult.VideoSuccess(recordEvent.outputResults.outputUri.toString())
                            )
                        }
                        bindUseCases()
                    }
                }
            }

            updateState(CameraState.Recording)
            triggerHapticAndSound()
        } catch (e: Exception) {
            e.printStackTrace()
            updateState(CameraState.Idle)
            onResultListener?.invoke(
                CameraResult.Failure(CameraError.RecordingFailed("Failed to start recording", e))
            )
            bindUseCases()
        }
    }

    fun stopRecording() {
        if (currentState !is CameraState.Recording) return
        activeRecording?.stop()
        activeRecording = null
    }

    private fun triggerHapticAndSound() {
        if (!preferences.disableVibration) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                    vibratorManager?.defaultVibrator?.vibrate(
                        VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE)
                    )
                } else {
                    @Suppress("DEPRECATION")
                    val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(50)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        if (!preferences.disableShutter) {
            try {
                if (mediaActionSound == null) {
                    mediaActionSound = MediaActionSound().apply {
                        load(MediaActionSound.SHUTTER_CLICK)
                    }
                }
                mediaActionSound?.play(MediaActionSound.SHUTTER_CLICK)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun release() {
        try {
            if (isAutoCapturing) stopAutoCapture()
            if (isRecording) stopRecording()
            mediaActionSound?.release()
            mediaActionSound = null
            cameraExecutor.shutdown()
            cameraProvider?.unbindAll()
            updateState(CameraState.Idle)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun notifyMediaScanner(file: File) {
        try {
            android.media.MediaScannerConnection.scanFile(
                context,
                arrayOf(file.absolutePath),
                null,
                null
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
