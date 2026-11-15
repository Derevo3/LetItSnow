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
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Foreground Service, обеспечивающий отображение снегопада и сугроба внизу экрана
 * поверх всех приложений и функциональных кнопок с помощью WindowManager (TYPE_APPLICATION_OVERLAY).
 *
 * Оверлей активен только тогда, когда устройство разблокировано.
 * Работает в режиме сквозных касаний (FLAG_NOT_TOUCHABLE), благодаря чему функциональные
 * кнопки (Назад, Домой, Меню / полоса жестов) нажимаются без каких-либо задержек и помех.
 * При выключенном экране или экране блокировки анимация скрывается и не расходует батарею.
 */
class SnowOverlayService : Service() {

    companion object {
        const val ACTION_START = "com.letitsnow.myapp.ACTION_START"
        const val ACTION_STOP = "com.letitsnow.myapp.ACTION_STOP"

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
    private var unlockCheckJob: Job? = null

    private fun isDeviceLocked(): Boolean {
        val km = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        return km?.isKeyguardLocked == true
    }

    private fun showOverlay() {
        unlockCheckJob?.cancel()
        unlockCheckJob = null
        if (::snowView.isInitialized) {
            snowView.visibility = View.VISIBLE
            snowView.start()
        }
    }

    private fun hideOverlay() {
        unlockCheckJob?.cancel()
        unlockCheckJob = null
        if (::snowView.isInitialized) {
            snowView.visibility = View.GONE
            snowView.pause()
        }
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    // Экран погас: скрываем оверлей для экономии батареи
                    hideOverlay()
                }

                Intent.ACTION_SCREEN_ON -> {
                    // Экран загорелся: если заблокирован, скрываем и ждем разблокировки
                    if (isDeviceLocked()) {
                        hideOverlay()
                        unlockCheckJob?.cancel()
                        unlockCheckJob = serviceScope.launch {
                            for (step in 0..75) { // Проверяем до 30 секунд каждые 400мс
                                delay(400)
                                if (!isActive) break
                                if (!isDeviceLocked()) {
                                    showOverlay()
                                    break
                                }
                            }
                        }
                    } else {
                        // Экран включился уже разблокированным (отпечаток на кнопке и т.д.)
                        showOverlay()
                    }
                }

                Intent.ACTION_USER_PRESENT -> {
                    // Пользователь разблокировал экран
                    showOverlay()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }
        isRunning = true
        settingsRepository = SnowSettingsRepository(this)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        createNotificationChannel()
        startAsForeground()

        initOverlayView()
        registerScreenReceiver()
        observeSettings()
    }

    private fun getRealScreenSize(): Pair<Int, Int> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager.maximumWindowMetrics.bounds
            Pair(bounds.width(), bounds.height())
        } else {
            val dm = android.util.DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(dm)
            Pair(dm.widthPixels, dm.heightPixels)
        }
    }

    private fun initOverlayView() {
        snowView = SnowView(this).apply {
            isOverlayMode = true
        }

        val (realW, realH) = getRealScreenSize()
        snowView.updateCanvasSize(realW.toFloat(), realH.toFloat())

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
                WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS

        layoutParams = WindowManager.LayoutParams(
            realW,
            realH,
            windowType,
            flags,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0

            // Разрешаем отрисовку во всю площадь экрана без отступов под системные панели
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                fitInsetsTypes = 0
                fitInsetsSides = 0
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                @Suppress("DEPRECATION")
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        try {
            windowManager.addView(snowView, layoutParams)
            if (isDeviceLocked()) {
                hideOverlay()
            } else {
                showOverlay()
            }
        } catch (_: Exception) {
            stopSelf()
        }
    }

    private fun observeSettings() {
        serviceScope.launch {
            settingsRepository.settingsFlow.collectLatest { settings ->
                if (!settings.isOverlayEnabled) {
                    stopSelf()
                    return@collectLatest
                }
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
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (_: Exception) {
            // Безопасная обработка на случай непредвиденных системных ограничений
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

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(R.drawable.ic_snowflake_notification)
            .setContentIntent(mainPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(0, getString(R.string.notification_action_stop), stopPendingIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_description)
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

            ACTION_START -> {
                if (::snowView.isInitialized) {
                    if (isDeviceLocked()) {
                        hideOverlay()
                    } else {
                        showOverlay()
                    }
                }
            }
        }
        return START_STICKY
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        try {
            if (::snowView.isInitialized && snowView.isAttachedToWindow) {
                val (realW, realH) = getRealScreenSize()
                layoutParams.width = realW
                layoutParams.height = realH
                windowManager.updateViewLayout(snowView, layoutParams)
                snowView.updateCanvasSize(realW.toFloat(), realH.toFloat())
            }
        } catch (_: Exception) { }
    }

    override fun onDestroy() {
        isRunning = false
        unlockCheckJob?.cancel()
        unlockCheckJob = null

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
