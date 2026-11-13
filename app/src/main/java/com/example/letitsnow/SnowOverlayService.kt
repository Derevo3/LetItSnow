package com.example.letitsnow

import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Foreground Service, обеспечивающий отображение снегопада и сугробов
 * поверх всех приложений с помощью WindowManager (TYPE_APPLICATION_OVERLAY).
 *
 * Во время активного использования телефона оверлей работает в режиме сквозных касаний
 * (FLAG_NOT_TOUCHABLE). Во время блокировки экран засыпается снегом без расхода батареи.
 * При разблокировке оверлей становится интерактивным для расчистки сугроба пальцем.
 */
class SnowOverlayService : Service() {

    companion object {
        const val ACTION_START = "com.example.letitsnow.ACTION_START"
        const val ACTION_STOP = "com.example.letitsnow.ACTION_STOP"
        const val ACTION_WIPE = "com.example.letitsnow.ACTION_WIPE"

        private const val NOTIFICATION_CHANNEL_ID = "snow_overlay_channel"
        private const val NOTIFICATION_ID = 1001

        var isRunning: Boolean = false
            private set

        fun start(context: Context) {
            val intent = Intent(context, SnowOverlayService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, SnowOverlayService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var windowManager: WindowManager
    private lateinit var layoutParams: WindowManager.LayoutParams
    private lateinit var snowView: SnowView
    private lateinit var settingsRepository: SnowSettingsRepository

    private var lastScreenOffTime: Long = 0L

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    // Телефон заблокирован / экран погас: фиксируем время и останавливаем анимацию для 0% нагрузки
                    lastScreenOffTime = SystemClock.elapsedRealtime()
                    if (::snowView.isInitialized) {
                        snowView.pause()
                    }
                }

                Intent.ACTION_SCREEN_ON -> {
                    // Экран загорелся (показан экран блокировки или рабочий стол)
                    if (::snowView.isInitialized) {
                        snowView.start()
                    }
                    // Если экрана блокировки нет (или он не заблокирован), сразу создаем сугроб
                    if (keyguardManager?.isKeyguardLocked != true && lastScreenOffTime > 0L) {
                        val elapsedSeconds = (SystemClock.elapsedRealtime() - lastScreenOffTime) / 1000f
                        lastScreenOffTime = 0L
                        triggerSnowAccumulation(elapsedSeconds)
                    }
                }

                Intent.ACTION_USER_PRESENT -> {
                    // Пользователь снял устройство с блокировки
                    if (::snowView.isInitialized) {
                        snowView.start()
                    }
                    if (lastScreenOffTime > 0L) {
                        val elapsedSeconds = (SystemClock.elapsedRealtime() - lastScreenOffTime) / 1000f
                        lastScreenOffTime = 0L
                        triggerSnowAccumulation(elapsedSeconds)
                    }
                }
            }
        }
    }

    private fun triggerSnowAccumulation(elapsedSeconds: Float) {
        if (!::snowView.isInitialized) return
        val effectiveSeconds = maxOf(elapsedSeconds, 5f)
        snowView.accumulation.accumulateOfflineSnow(
            elapsedSeconds = effectiveSeconds,
            rateMultiplier = snowView.accumulationRateMultiplier
        )
        setInteractiveMode(true)
        snowView.invalidate()
    }

    override fun onCreate() {
        super.onCreate()
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }
        isRunning = true
        settingsRepository = SnowSettingsRepository(this)
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        createNotificationChannel()
        startAsForeground()

        initOverlayView()
        registerScreenReceiver()
        observeSettings()
    }

    private fun initOverlayView() {
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }

        snowView = SnowView(this).apply {
            isOverlayMode = true
            isInteractive = false
            onSnowClearedListener = {
                // Когда пользователь расчистил снег, возвращаем сквозной режим
                setInteractiveMode(false)
            }
        }

        val windowType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        @Suppress("DEPRECATION")
        val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED

        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            windowType,
            flags,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        try {
            windowManager.addView(snowView, layoutParams)
            snowView.start()
        } catch (_: Exception) {
            stopSelf()
        }
    }

    private fun setInteractiveMode(interactive: Boolean) {
        if (!::snowView.isInitialized) return
        snowView.isInteractive = interactive

        if (interactive) {
            // Разрешаем касания для очистки снега пальцем
            layoutParams.flags = layoutParams.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        } else {
            // Возвращаем полностью сквозной режим (все нажатия идут приложениям под снегом)
            layoutParams.flags = layoutParams.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        }

        try {
            if (snowView.isAttachedToWindow) {
                windowManager.updateViewLayout(snowView, layoutParams)
            }
        } catch (_: Exception) { }
    }

    private fun observeSettings() {
        serviceScope.launch {
            settingsRepository.settingsFlow.collectLatest { settings ->
                if (::snowView.isInitialized) {
                    snowView.applySettings(settings)
                }
            }
        }
    }

    private fun registerScreenReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        ContextCompat.registerReceiver(
            this,
            screenReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    private fun startAsForeground() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(): Notification {
        val mainActivityIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val mainPendingIntent = PendingIntent.getActivity(
            this,
            0,
            mainActivityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, SnowOverlayService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val wipeIntent = Intent(this, SnowOverlayService::class.java).apply {
            action = ACTION_WIPE
        }
        val wipePendingIntent = PendingIntent.getService(
            this,
            2,
            wipeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("SnowOverlay активен")
            .setContentText("Снегопад поверх всех приложений")
            .setSmallIcon(R.drawable.ic_snowflake_notification)
            .setContentIntent(mainPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(0, "Почистить снег", wipePendingIntent)
            .addAction(0, "Остановить", stopPendingIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Снегопад поверх приложений",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Постоянное уведомление о работе снегопада поверх всех окон"
                setShowBadge(false)
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                serviceScope.launch {
                    settingsRepository.saveOverlayEnabled(false)
                }
                stopSelf()
                return START_NOT_STICKY
            }

            ACTION_WIPE -> {
                if (::snowView.isInitialized) {
                    triggerSnowAccumulation(25f)
                }
            }

            ACTION_START -> {
                if (::snowView.isInitialized) {
                    snowView.start()
                }
            }
        }
        return START_STICKY
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        try {
            if (::snowView.isInitialized && snowView.isAttachedToWindow) {
                windowManager.updateViewLayout(snowView, layoutParams)
            }
        } catch (_: Exception) { }
    }

    override fun onDestroy() {
        isRunning = false
        try {
            unregisterReceiver(screenReceiver)
        } catch (_: Exception) { }

        serviceScope.cancel()

        if (::snowView.isInitialized) {
            snowView.pause()
            try {
                if (snowView.isAttachedToWindow) {
                    windowManager.removeView(snowView)
                }
            } catch (_: Exception) { }
        }

        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
