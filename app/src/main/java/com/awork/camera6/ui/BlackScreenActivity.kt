package com.awork.camera6.ui

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
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
    private lateinit var gestureDetector: android.view.GestureDetector

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
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
        gestureDetector = android.view.GestureDetector(this, object : android.view.GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                com.awork.camera6.command.CaptureDispatcher.dispatch(
                    this@BlackScreenActivity,
                    com.awork.camera6.command.CaptureCommand.SINGLE_CAPTURE
                )
                return true
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
        if (event == null) return super.onTouchEvent(event)
        val scaleHandled = scaleDetector.onTouchEvent(event)
        val gestureHandled = gestureDetector.onTouchEvent(event)
        return scaleHandled || gestureHandled || super.onTouchEvent(event)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val isUp = event.keyCode == KeyEvent.KEYCODE_VOLUME_UP
        val isDown = event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
        if (isUp || isDown) {
            val action = if (isUp) cachedVolumeUpAction else cachedVolumeDownAction
            val command = com.awork.camera6.command.CaptureCommand.fromPreferenceAction(action)
            if (command != null) {
                if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                    com.awork.camera6.command.CaptureDispatcher.dispatch(this, command)
                }
                return true // Consume BOTH ACTION_DOWN and ACTION_UP
            }
        }
        return super.dispatchKeyEvent(event)
    }

}
