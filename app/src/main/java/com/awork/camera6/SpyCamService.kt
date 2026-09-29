package com.awork.camera6

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.database.ContentObserver
import android.graphics.PixelFormat
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.VolumeProvider
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import com.awork.camera6.camera.CameraController
import com.awork.camera6.ui.BlackScreenActivity
import com.awork.camera6.ui.MainActivity
import com.awork.camera6.util.PreferencesManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class SpyCamService : LifecycleService() {

    @Inject
    lateinit var cameraController: CameraController
    @Inject
    lateinit var preferences: PreferencesManager
    private var overlayView: View? = null
    private var isOverlayVisible = false
    private var mediaSession: MediaSession? = null
    private var silentPlayer: MediaPlayer? = null
    private var volumeObserver: ContentObserver? = null

    private val monitoredStreams = intArrayOf(
        AudioManager.STREAM_MUSIC,
        AudioManager.STREAM_RING,
        AudioManager.STREAM_NOTIFICATION,
        AudioManager.STREAM_SYSTEM,
        10 // STREAM_ACCESSIBILITY / Xiaomi stream
    )
    private val previousStreamVolumes = IntArray(monitoredStreams.size) { -1 }

    private val mainHandler = Handler(Looper.getMainLooper())

    private val commandReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ACTION_CAPTURE_SINGLE -> handleVolumeAction("capture")
                ACTION_CAPTURE_BURST -> handleVolumeAction("burst")
                ACTION_CAPTURE_AUTO -> handleVolumeAction("auto")
                ACTION_STOP_AUTO -> cameraController.stopAutoCapture()
                ACTION_CAPTURE_FACE -> {
                    if (cameraController.isFaceDetecting) {
                        cameraController.stopFaceDetection()
                        if (!preferences.disableToast) {
                            Toast.makeText(applicationContext, "Face Detection Stopped", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        cameraController.startFaceDetection()
                        if (!preferences.disableToast) {
                            Toast.makeText(applicationContext, "Face Detection Started", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                ACTION_RECORD_VIDEO -> handleVolumeAction("video")
                ACTION_STOP_RECORDING -> cameraController.stopRecording()
                ACTION_SWITCH_CAMERA -> cameraController.switchCamera()
                ACTION_SHOW_OVERLAY -> showOverlay()
                ACTION_HIDE_OVERLAY -> hideOverlay()
                ACTION_TOGGLE_OVERLAY -> toggleOverlay()
                ACTION_BLACK_MODE -> startBlackMode()
                ACTION_EXIT -> stopSelf()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()

        val filter = IntentFilter().apply {
            addAction(ACTION_CAPTURE_SINGLE)
            addAction(ACTION_CAPTURE_BURST)
            addAction(ACTION_CAPTURE_AUTO)
            addAction(ACTION_STOP_AUTO)
            addAction(ACTION_CAPTURE_FACE)
            addAction(ACTION_RECORD_VIDEO)
            addAction(ACTION_STOP_RECORDING)
            addAction(ACTION_SWITCH_CAMERA)
            addAction(ACTION_SHOW_OVERLAY)
            addAction(ACTION_HIDE_OVERLAY)
            addAction(ACTION_TOGGLE_OVERLAY)
            addAction(ACTION_BLACK_MODE)
            addAction(ACTION_EXIT)
        }
        registerReceiver(
            commandReceiver, 
            filter, 
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Context.RECEIVER_NOT_EXPORTED else 0
        )

        startSilentPlayer()
        setupVolumeKeyListener()
        startVolumeObserver()

        cameraController.onFaceDetectedListener = {
            if (!preferences.disableToast) {
                Toast.makeText(applicationContext, "Face Detected - Photo Captured", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        val notification = createNotification()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA else 0
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        cameraController.startCamera(this)

        when (preferences.startMode) {
            "black" -> startBlackMode()
            "minimized" -> { /* Keep foreground service only */ }
            else -> showOverlay()
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(commandReceiver)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        stopVolumeObserver()
        stopSilentPlayer()
        try {
            mediaSession?.isActive = false
            mediaSession?.release()
            mediaSession = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
        cameraController.release()
        hideOverlay()
        super.onDestroy()
    }

    private fun startSilentPlayer() {
        try {
            silentPlayer = MediaPlayer.create(this, R.raw.silent).apply {
                isLooping = true
                setVolume(0.01f, 0.01f)
                start()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun stopSilentPlayer() {
        try {
            silentPlayer?.stop()
            silentPlayer?.release()
            silentPlayer = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun setupVolumeKeyListener() {
        try {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val componentName = ComponentName(this, RemoteControlReceiver::class.java)
            @Suppress("DEPRECATION")
            audioManager.registerMediaButtonEventReceiver(componentName)

            mediaSession = MediaSession(this, "SCOS_VolumeKey_Session").apply {
                setPlaybackState(
                    PlaybackState.Builder()
                        .setState(PlaybackState.STATE_PLAYING, 0, 1.0f)
                        .setActions(PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE)
                        .build()
                )
                setPlaybackToRemote(object : VolumeProvider(
                    VOLUME_CONTROL_RELATIVE, 100, 50
                ) {
                    override fun onAdjustVolume(direction: Int) {
                        val action = if (direction > 0) {
                            preferences.volumeUpAction
                        } else if (direction < 0) {
                            preferences.volumeDownAction
                        } else null

                        if (action != null && action != "none") {
                            handleVolumeAction(action)
                        }
                    }
                })
                isActive = true
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun startVolumeObserver() {
        try {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            for (i in monitoredStreams.indices) {
                try {
                    val stream = monitoredStreams[i]
                    val max = audioManager.getStreamMaxVolume(stream)
                    var curr = audioManager.getStreamVolume(stream)
                    // Keep volume in middle range so volume up and volume down always produce delta
                    if (curr <= 0) {
                        curr = 2
                        audioManager.setStreamVolume(stream, curr, 0)
                    } else if (curr >= max) {
                        curr = max - 2
                        audioManager.setStreamVolume(stream, curr, 0)
                    }
                    previousStreamVolumes[i] = curr
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            volumeObserver = object : ContentObserver(mainHandler) {
                override fun onChange(selfChange: Boolean) {
                    super.onChange(selfChange)
                    for (i in monitoredStreams.indices) {
                        val stream = monitoredStreams[i]
                        try {
                            val curr = audioManager.getStreamVolume(stream)
                            val prev = previousStreamVolumes[i]
                            if (prev != -1 && curr != prev) {
                                val delta = curr - prev
                                val action = if (delta > 0) preferences.volumeUpAction else preferences.volumeDownAction
                                if (action != "none") {
                                    handleVolumeAction(action)
                                    try {
                                        audioManager.setStreamVolume(stream, prev, 0)
                                    } catch (e: Exception) {
                                        previousStreamVolumes[i] = curr
                                    }
                                } else {
                                    previousStreamVolumes[i] = curr
                                }
                                break
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            }

            contentResolver.registerContentObserver(
                Settings.System.CONTENT_URI,
                true,
                volumeObserver!!
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun stopVolumeObserver() {
        volumeObserver?.let {
            try {
                contentResolver.unregisterContentObserver(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            volumeObserver = null
        }
        try {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val componentName = ComponentName(this, RemoteControlReceiver::class.java)
            @Suppress("DEPRECATION")
            audioManager.unregisterMediaButtonEventReceiver(componentName)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun handleVolumeAction(action: String) {
        mainHandler.post {
            if (!preferences.disableToast) {
                val label = when (action) {
                    "capture" -> "Photo Captured"
                    "burst" -> "Burst Shot Started"
                    "auto" -> if (cameraController.isAutoCapturing) "Continuous Shot Stopped" else "Continuous Shot Started"
                    "video" -> if (cameraController.isRecording) "Video Stopped" else "Video Recording Started"
                    "black" -> "Black Screen Mode"
                    else -> action
                }
                Toast.makeText(applicationContext, label, Toast.LENGTH_SHORT).show()
            }
        }

        when (action) {
            "capture" -> {
                if (!cameraController.isRecording && !cameraController.isAutoCapturing) {
                    cameraController.captureSingle()
                }
            }
            "burst" -> {
                if (!cameraController.isRecording && !cameraController.isAutoCapturing) {
                    cameraController.captureBurst(preferences.burstCount)
                }
            }
            "auto" -> {
                if (!cameraController.isRecording) {
                    cameraController.startAutoCapture(preferences.autoDelay)
                }
            }
            "video" -> {
                if (!cameraController.isAutoCapturing) {
                    if (cameraController.isRecording) {
                        cameraController.stopRecording()
                    } else {
                        cameraController.startRecording()
                    }
                }
            }
            "black" -> startBlackMode()
        }
    }

    private fun createNotification() = NotificationCompat.Builder(this, SCOSApplication.CHANNEL_ID)
        .setContentTitle("SCOS")
        .setContentText(if (isOverlayVisible) "Camera active" else "Hidden mode")
        .setSmallIcon(android.R.drawable.ic_menu_camera)
        .setOngoing(true)
        .setContentIntent(PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        ))
        .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Exit",
            PendingIntent.getBroadcast(this, 1,
                Intent(ACTION_EXIT).apply { setPackage(packageName) }, PendingIntent.FLAG_IMMUTABLE))
        .build()

    private fun showOverlay() {
        if (overlayView != null) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            return
        }

        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val inflater = getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater
        overlayView = inflater.inflate(R.layout.overlay_main, null)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0; y = 100
        }

        overlayView?.apply {
            var initialX = 0
            var initialY = 0
            var initialTouchX = 0f
            var initialTouchY = 0f

            val dragHandle = findViewById<View>(R.id.overlay_drag_handle)
            dragHandle?.setOnTouchListener { _, event ->
                when (event.action) {
                    android.view.MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        true
                    }
                    android.view.MotionEvent.ACTION_MOVE -> {
                        params.x = initialX + (event.rawX - initialTouchX).toInt()
                        params.y = initialY + (event.rawY - initialTouchY).toInt()
                        try {
                            wm.updateViewLayout(overlayView, params)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                        true
                    }
                    else -> false
                }
            }

            val previewView = findViewById<androidx.camera.view.PreviewView>(R.id.overlay_preview)
            findViewById<ImageButton>(R.id.btn_preview)?.setOnClickListener {
                previewView?.let { pv ->
                    if (pv.visibility == View.VISIBLE) {
                        pv.visibility = View.GONE
                        cameraController.setPreviewView(null)
                        if (!preferences.disableToast) {
                            Toast.makeText(applicationContext, "Preview Hidden", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        pv.visibility = View.VISIBLE
                        cameraController.setPreviewView(pv)
                        if (!preferences.disableToast) {
                            Toast.makeText(applicationContext, "Preview Active", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }

            findViewById<ImageButton>(R.id.btn_face)?.setOnClickListener {
                if (cameraController.isFaceDetecting) {
                    cameraController.stopFaceDetection()
                    if (!preferences.disableToast) {
                        Toast.makeText(applicationContext, "Face Detection Stopped", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    cameraController.startFaceDetection()
                    if (!preferences.disableToast) {
                        Toast.makeText(applicationContext, "Face Detection Started", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            findViewById<ImageButton>(R.id.btn_capture)?.setOnClickListener {
                handleVolumeAction("capture")
            }
            findViewById<ImageButton>(R.id.btn_burst)?.setOnClickListener {
                handleVolumeAction("burst")
            }
            findViewById<ImageButton>(R.id.btn_video)?.setOnClickListener {
                handleVolumeAction("video")
            }
            findViewById<ImageButton>(R.id.btn_switch)?.setOnClickListener {
                cameraController.switchCamera()
            }
            findViewById<ImageButton>(R.id.btn_black)?.setOnClickListener {
                startBlackMode()
            }
            findViewById<ImageButton>(R.id.btn_hide)?.setOnClickListener {
                hideOverlay()
            }
            findViewById<ImageButton>(R.id.btn_exit)?.setOnClickListener {
                stopSelf()
            }
        }

        try {
            wm.addView(overlayView, params)
            isOverlayVisible = true
            updateNotification()
        } catch (e: Exception) {
            e.printStackTrace()
            overlayView = null
        }
    }

    private fun hideOverlay() {
        overlayView?.let {
            cameraController.setPreviewView(null)
            try {
                val wm = getSystemService(WINDOW_SERVICE) as WindowManager
                wm.removeView(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            overlayView = null
        }
        isOverlayVisible = false
        updateNotification()
    }

    private fun toggleOverlay() {
        if (isOverlayVisible) hideOverlay() else showOverlay()
    }

    private fun startBlackMode() {
        startActivity(Intent(this, BlackScreenActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    private fun updateNotification() {
        val manager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        manager.notify(NOTIFICATION_ID, createNotification())
    }

    companion object {
        const val NOTIFICATION_ID = 1001
        const val ACTION_CAPTURE_SINGLE = "com.awork.camera6.action.CAPTURE_SINGLE"
        const val ACTION_CAPTURE_BURST = "com.awork.camera6.action.CAPTURE_BURST"
        const val ACTION_CAPTURE_AUTO = "com.awork.camera6.action.CAPTURE_AUTO"
        const val ACTION_STOP_AUTO = "com.awork.camera6.action.STOP_AUTO"
        const val ACTION_CAPTURE_FACE = "com.awork.camera6.action.CAPTURE_FACE"
        const val ACTION_RECORD_VIDEO = "com.awork.camera6.action.RECORD_VIDEO"
        const val ACTION_STOP_RECORDING = "com.awork.camera6.action.STOP_RECORDING"
        const val ACTION_SWITCH_CAMERA = "com.awork.camera6.action.SWITCH_CAMERA"
        const val ACTION_SHOW_OVERLAY = "com.awork.camera6.action.SHOW_OVERLAY"
        const val ACTION_HIDE_OVERLAY = "com.awork.camera6.action.HIDE_OVERLAY"
        const val ACTION_TOGGLE_OVERLAY = "com.awork.camera6.action.TOGGLE_OVERLAY"
        const val ACTION_BLACK_MODE = "com.awork.camera6.action.BLACK_MODE"
        const val ACTION_EXIT = "com.awork.camera6.action.EXIT"
    }
}
