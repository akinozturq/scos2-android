package com.awork.camera6.ui

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.addCallback
import com.awork.camera6.SpyCamService
import com.awork.camera6.util.PreferencesManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class BlackScreenActivity : ComponentActivity() {

    @Inject
    lateinit var prefs: PreferencesManager

    private var cachedVolumeUpAction: String = "capture"
    private var cachedVolumeDownAction: String = "video"
    private lateinit var scaleDetector: ScaleGestureDetector

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let { controller ->
                controller.hide(WindowInsets.Type.systemBars())
                controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_GESTURE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_FULLSCREEN
            )
        }
        scaleDetector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                if (detector.scaleFactor < 0.8f) {
                    finish()
                    return true
                }
                return false
            }
        })
        setContentView(View(this).apply { setBackgroundColor(0xFF000000.toInt()) })
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                moveTaskToBack(true)
            }
        })
    }

    override fun onResume() {
        super.onResume()
        cachedVolumeUpAction = prefs.volumeUpAction
        cachedVolumeDownAction = prefs.volumeDownAction
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        event?.let { scaleDetector.onTouchEvent(it) }
        if (event?.action == MotionEvent.ACTION_UP) {
            sendBroadcast(Intent(SpyCamService.ACTION_CAPTURE_SINGLE).setPackage(packageName))
        }
        return true
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val isUp = event.keyCode == KeyEvent.KEYCODE_VOLUME_UP
        val isDown = event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
        if (isUp || isDown) {
            val action = if (isUp) cachedVolumeUpAction else cachedVolumeDownAction
            if (action != "none") {
                if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                    val intentAction = when (action) {
                        "burst" -> SpyCamService.ACTION_CAPTURE_BURST
                        "auto" -> SpyCamService.ACTION_CAPTURE_AUTO
                        "video" -> SpyCamService.ACTION_RECORD_VIDEO
                        else -> SpyCamService.ACTION_CAPTURE_SINGLE
                    }
                    sendBroadcast(Intent(intentAction).setPackage(packageName))
                }
                return true // Consume BOTH ACTION_DOWN and ACTION_UP
            }
        }
        return super.dispatchKeyEvent(event)
    }

}
