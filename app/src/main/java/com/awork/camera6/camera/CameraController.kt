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
import androidx.core.content.FileProvider
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
    var isRecording = false
        private set
    @Volatile
    var isAutoCapturing = false
        private set

    init {
        isFrontCamera = preferences.defaultCamera.equals("front", ignoreCase = true)
        cameraSelector = if (isFrontCamera) {
            CameraSelector.DEFAULT_FRONT_CAMERA
        } else {
            CameraSelector.DEFAULT_BACK_CAMERA
        }
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

    fun captureSingle() {
        if (isRecording) return // Mutex: don't take photo while recording video

        val capture = imageCapture ?: run {
            bindUseCases()
            imageCapture ?: return
        }

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmssSSS", Locale.US).format(Date())
        val isHidden = preferences.hideFolder

        triggerHapticAndSound()

        val outputOptions: ImageCapture.OutputFileOptions = if (!isHidden && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "IMG_$timestamp.jpg")
                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                val subDir = if (preferences.savePath.isNotBlank()) preferences.savePath else FileManager.SCOS_DIR
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/$subDir")
            }
            ImageCapture.OutputFileOptions.Builder(
                context.contentResolver,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues
            ).build()
        } else {
            val saveDir = fileManager.getSaveDirectory(preferences.savePath, hidden = isHidden)
            val file = File(saveDir, "IMG_$timestamp.jpg")
            ImageCapture.OutputFileOptions.Builder(file).build()
        }

        capture.takePicture(
            outputOptions,
            cameraExecutor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    if (isHidden || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                        val file = File(fileManager.getSaveDirectory(preferences.savePath, hidden = isHidden), "IMG_$timestamp.jpg")
                        if (file.exists() && !isHidden) {
                            notifyMediaScanner(file)
                        }
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    exception.printStackTrace()
                }
            }
        )
    }

    fun captureBurst(count: Int) {
        if (isRecording || isAutoCapturing) return // Mutex

        var remaining = count
        val burstRunnable = object : Runnable {
            override fun run() {
                if (remaining > 0 && !isRecording) {
                    captureSingle()
                    remaining--
                    mainHandler.postDelayed(this, 400)
                }
            }
        }
        mainHandler.post(burstRunnable)
    }

    fun startAutoCapture(delaySeconds: Int) {
        if (isRecording) return // Mutex: cannot start auto capture during video recording

        if (isAutoCapturing) {
            stopAutoCapture()
            return
        }

        isAutoCapturing = true
        val intervalMs = (delaySeconds.coerceAtLeast(1)) * 1000L
        autoCaptureRunnable = object : Runnable {
            override fun run() {
                if (isAutoCapturing && !isRecording) {
                    captureSingle()
                    mainHandler.postDelayed(this, intervalMs)
                }
            }
        }
        mainHandler.post(autoCaptureRunnable!!)
    }

    fun stopAutoCapture() {
        isAutoCapturing = false
        autoCaptureRunnable?.let { mainHandler.removeCallbacks(it) }
        autoCaptureRunnable = null
    }

    fun startRecording() {
        if (isAutoCapturing) return // Mutex: cannot start video recording during auto capture

        if (isRecording) {
            stopRecording()
            return
        }

        val provider = cameraProvider ?: return
        val owner = lifecycleOwner ?: return

        try {
            provider.unbindAll()

            val recorder = Recorder.Builder()
                .setQualitySelector(QualitySelector.from(Quality.HIGHEST))
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

            val pendingRecording = if (!isHidden && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val videoContentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, "VID_$timestamp.mp4")
                    put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
                    val subDir = if (preferences.savePath.isNotBlank()) preferences.savePath else FileManager.SCOS_DIR
                    put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/$subDir")
                }
                val mediaStoreOutput = MediaStoreOutputOptions.Builder(
                    context.contentResolver,
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                ).setContentValues(videoContentValues).build()
                videoCapture?.output?.prepareRecording(context, mediaStoreOutput)
            } else {
                val videoDir = fileManager.getSaveDirectory(preferences.savePath, hidden = isHidden)
                val videoFile = File(videoDir, "VID_$timestamp.mp4")
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
                        isRecording = false
                        bindUseCases()
                    }
                }
            }

            isRecording = true
            triggerHapticAndSound()
        } catch (e: Exception) {
            e.printStackTrace()
            isRecording = false
            bindUseCases()
        }
    }

    fun stopRecording() {
        if (!isRecording) return
        activeRecording?.stop()
        activeRecording = null
        isRecording = false
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
