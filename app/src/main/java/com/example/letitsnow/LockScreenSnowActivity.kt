package com.example.letitsnow

import android.annotation.SuppressLint
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Прозрачная активность, отображающая эффект снегопада поверх системного экрана блокировки.
 * Активируется при включении экрана, если включена опция в настройках.
 * При первом же касании или свайпе открывает системный экран разблокировки (PIN/отпечаток).
 */
class LockScreenSnowActivity : ComponentActivity() {

    private lateinit var snowView: SnowView
    private lateinit var settingsRepository: SnowSettingsRepository
    private var hasNotifiedUnlocked = false

    private val screenEventsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    // Экран погас — закрываем активность без вызова разблокировки
                    finish()
                }
                Intent.ACTION_USER_PRESENT -> {
                    // Телефон разблокирован — закрываемся и уведомляем оверлей о засыпании снегом
                    notifyUnlocked()
                    dismissSmoothly()
                }
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Отображение поверх экрана блокировки
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }

        enableEdgeToEdge()

        settingsRepository = SnowSettingsRepository(this)

        snowView = SnowView(this).apply {
            isOverlayMode = true // Прозрачный фон
            isInteractive = false
        }

        // Любое касание или свайп мягко убирает снег и передает управление вводу пароля/отпечатка
        val gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                dismissAndUnlock()
                return true
            }

            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                dismissAndUnlock()
                return true
            }

            override fun onScroll(
                e1: MotionEvent?,
                e2: MotionEvent,
                distanceX: Float,
                distanceY: Float
            ): Boolean {
                dismissAndUnlock()
                return true
            }
        })

        snowView.setOnTouchListener { v, event ->
            gestureDetector.onTouchEvent(event)
            if (event.actionMasked == MotionEvent.ACTION_UP) {
                v.performClick()
                dismissAndUnlock()
            }
            true
        }

        setContentView(snowView)

        // Подписываемся на события блокировки/разблокировки
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        ContextCompat.registerReceiver(
            this,
            screenEventsReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        // Подгружаем актуальные настройки скорости, плотности и сугроба
        lifecycleScope.launch {
            settingsRepository.settingsFlow.collectLatest { settings ->
                snowView.applySettings(settings)
            }
        }
    }

    private fun notifyUnlocked() {
        if (!hasNotifiedUnlocked) {
            hasNotifiedUnlocked = true
            SnowOverlayService.onUnlocked(this)
        }
    }

    private fun dismissAndUnlock() {
        if (isFinishing || isDestroyed) return
        val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            keyguardManager?.requestDismissKeyguard(this, null)
        }
        notifyUnlocked()
        dismissSmoothly()
    }

    private fun dismissSmoothly() {
        if (isFinishing || isDestroyed) return
        finish()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, android.R.anim.fade_out)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, android.R.anim.fade_out)
        }
    }

    override fun onResume() {
        super.onResume()
        snowView.start()
    }

    override fun onPause() {
        super.onPause()
        snowView.pause()
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(screenEventsReceiver)
        } catch (_: Exception) { }
        super.onDestroy()
    }
}
