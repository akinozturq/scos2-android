package com.awork.camera6

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
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
import com.awork.camera6.command.CaptureCommand
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

    private val mainHandler = Handler(Looper.getMainLooper())

    private val commandReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val command = CaptureCommand.fromAction(intent.action) ?: return
            executeCommand(command)
        }
    }

    override fun onCreate() {
        super.onCreate()

        val filter = IntentFilter().apply {
            addAction(CaptureCommand.ACTION_CAPTURE_SINGLE)
            addAction(CaptureCommand.ACTION_CAPTURE_BURST)
            addAction(CaptureCommand.ACTION_CAPTURE_AUTO)
            addAction(CaptureCommand.ACTION_STOP_AUTO)
            addAction(CaptureCommand.ACTION_RECORD_VIDEO)
            addAction(CaptureCommand.ACTION_STOP_RECORDING)
            addAction(CaptureCommand.ACTION_SWITCH_CAMERA)
            addAction(CaptureCommand.ACTION_SHOW_OVERLAY)
            addAction(CaptureCommand.ACTION_HIDE_OVERLAY)
            addAction(CaptureCommand.ACTION_TOGGLE_OVERLAY)
            addAction(CaptureCommand.ACTION_BLACK_MODE)
            addAction(CaptureCommand.ACTION_EXIT)
        }
        registerReceiver(
            commandReceiver, 
            filter, 
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Context.RECEIVER_NOT_EXPORTED else 0
        )

        startSilentPlayer()
        setupVolumeKeyListener()
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
        cameraController.onStateChangedListener = { state ->
            val statusText = when (state) {
                com.awork.camera6.camera.CameraState.Idle -> if (isOverlayVisible) "Camera active" else "Hidden mode"
                com.awork.camera6.camera.CameraState.Capturing -> "Capturing photo..."
                is com.awork.camera6.camera.CameraState.Burst -> "Burst (${state.current}/${state.total})"
                com.awork.camera6.camera.CameraState.AutoCapturing -> "Auto capture active"
                com.awork.camera6.camera.CameraState.Recording -> "Recording video..."
            }
            updateNotification(statusText)
        }

        when (preferences.startMode) {
            "black" -> startBlackMode()
            "minimized" -> { /* Keep foreground service only */ }
            else -> showOverlay()
        }

        return START_NOT_STICKY
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

                        val command = action?.let { CaptureCommand.fromPreferenceAction(it) }
                        if (command != null) {
                            executeCommand(command)
                        }
                    }
                })
                isActive = true
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun executeCommand(command: CaptureCommand) {
        mainHandler.post {
            if (!preferences.disableToast) {
                val label = when (command) {
                    CaptureCommand.SINGLE_CAPTURE -> "Photo Captured"
                    CaptureCommand.BURST_CAPTURE -> "Burst Shot Started"
                    CaptureCommand.AUTO_CAPTURE -> if (cameraController.isAutoCapturing) "Continuous Shot Stopped" else "Continuous Shot Started"
                    CaptureCommand.STOP_AUTO_CAPTURE -> "Continuous Shot Stopped"
                    CaptureCommand.RECORD_VIDEO -> if (cameraController.isRecording) "Video Stopped" else "Video Recording Started"
                    CaptureCommand.STOP_RECORDING -> "Video Stopped"
                    CaptureCommand.SWITCH_CAMERA -> "Camera Switched"
                    CaptureCommand.SHOW_OVERLAY -> "Overlay Shown"
                    CaptureCommand.HIDE_OVERLAY -> "Overlay Hidden"
                    CaptureCommand.TOGGLE_OVERLAY -> "Overlay Toggled"
                    CaptureCommand.BLACK_MODE -> "Black Screen Mode"
                    CaptureCommand.EXIT -> "Exiting SCOS"
                }
                Toast.makeText(applicationContext, label, Toast.LENGTH_SHORT).show()
            }
        }

        when (command) {
            CaptureCommand.SINGLE_CAPTURE -> {
                if (!cameraController.isRecording && !cameraController.isAutoCapturing) {
                    cameraController.captureSingle()
                }
            }
            CaptureCommand.BURST_CAPTURE -> {
                if (!cameraController.isRecording && !cameraController.isAutoCapturing) {
                    cameraController.captureBurst(preferences.burstCount)
                }
            }
            CaptureCommand.AUTO_CAPTURE -> {
                if (!cameraController.isRecording) {
                    cameraController.startAutoCapture(preferences.autoDelay)
                }
            }
            CaptureCommand.STOP_AUTO_CAPTURE -> {
                cameraController.stopAutoCapture()
            }
            CaptureCommand.RECORD_VIDEO -> {
                if (!cameraController.isAutoCapturing) {
                    if (cameraController.isRecording) {
                        cameraController.stopRecording()
                    } else {
                        cameraController.startRecording()
                    }
                }
            }
            CaptureCommand.STOP_RECORDING -> {
                cameraController.stopRecording()
            }
            CaptureCommand.SWITCH_CAMERA -> {
                cameraController.switchCamera()
            }
            CaptureCommand.SHOW_OVERLAY -> showOverlay()
            CaptureCommand.HIDE_OVERLAY -> hideOverlay()
            CaptureCommand.TOGGLE_OVERLAY -> toggleOverlay()
            CaptureCommand.BLACK_MODE -> startBlackMode()
            CaptureCommand.EXIT -> stopSelf()
        }
    }

    private fun createNotification(statusText: String? = null) = NotificationCompat.Builder(this, SCOSApplication.CHANNEL_ID)
        .setContentTitle("SCOS")
        .setContentText(statusText ?: if (isOverlayVisible) "Camera active" else "Hidden mode")
        .setSmallIcon(android.R.drawable.ic_menu_camera)
        .setOngoing(true)
        .setContentIntent(PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        ))
        .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Exit",
            PendingIntent.getBroadcast(this, 1,
                Intent(CaptureCommand.ACTION_EXIT).apply { setPackage(packageName) }, PendingIntent.FLAG_IMMUTABLE))
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

            findViewById<ImageButton>(R.id.btn_capture)?.setOnClickListener {
                executeCommand(CaptureCommand.SINGLE_CAPTURE)
            }
            findViewById<ImageButton>(R.id.btn_burst)?.setOnClickListener {
                executeCommand(CaptureCommand.BURST_CAPTURE)
            }
            findViewById<ImageButton>(R.id.btn_video)?.setOnClickListener {
                executeCommand(CaptureCommand.RECORD_VIDEO)
            }
            findViewById<ImageButton>(R.id.btn_switch)?.setOnClickListener {
                executeCommand(CaptureCommand.SWITCH_CAMERA)
            }
            findViewById<ImageButton>(R.id.btn_black)?.setOnClickListener {
                executeCommand(CaptureCommand.BLACK_MODE)
            }
            findViewById<ImageButton>(R.id.btn_hide)?.setOnClickListener {
                executeCommand(CaptureCommand.HIDE_OVERLAY)
            }
            findViewById<ImageButton>(R.id.btn_exit)?.setOnClickListener {
                executeCommand(CaptureCommand.EXIT)
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

    private fun updateNotification(statusText: String? = null) {
        val manager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        manager.notify(NOTIFICATION_ID, createNotification(statusText))
    }

    companion object {
        const val NOTIFICATION_ID = 1001
        const val ACTION_CAPTURE_SINGLE = CaptureCommand.ACTION_CAPTURE_SINGLE
        const val ACTION_CAPTURE_BURST = CaptureCommand.ACTION_CAPTURE_BURST
        const val ACTION_CAPTURE_AUTO = CaptureCommand.ACTION_CAPTURE_AUTO
        const val ACTION_STOP_AUTO = CaptureCommand.ACTION_STOP_AUTO
        const val ACTION_RECORD_VIDEO = CaptureCommand.ACTION_RECORD_VIDEO
        const val ACTION_STOP_RECORDING = CaptureCommand.ACTION_STOP_RECORDING
        const val ACTION_SWITCH_CAMERA = CaptureCommand.ACTION_SWITCH_CAMERA
        const val ACTION_SHOW_OVERLAY = CaptureCommand.ACTION_SHOW_OVERLAY
        const val ACTION_HIDE_OVERLAY = CaptureCommand.ACTION_HIDE_OVERLAY
        const val ACTION_TOGGLE_OVERLAY = CaptureCommand.ACTION_TOGGLE_OVERLAY
        const val ACTION_BLACK_MODE = CaptureCommand.ACTION_BLACK_MODE
        const val ACTION_EXIT = CaptureCommand.ACTION_EXIT
    }
}
