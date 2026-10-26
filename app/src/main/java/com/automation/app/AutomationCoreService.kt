package com.automation.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AutomationCoreService : LifecycleService() {

    private var wakeLock: PowerManager.WakeLock? = null
    private var systemEventListener: SystemEventListener? = null
    var scriptEngineManager: ScriptEngineManager? = null
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        _isRunning.value = true
        startForegroundServiceWithNotification()
        acquireWakeLock()
        registerSystemEventListener()
        initScriptEngine()
    }

    private fun startForegroundServiceWithNotification() {
        val channelId = "automation_core_channel"
        val channelName = "Automation Core Background Service"

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_LOW
            )
            manager.createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Automation Core Running")
            .setContentText("Persistent JavaScript sandbox active in background")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "AutomationApp::AutomationCoreServiceWakeLock"
        ).apply {
            acquire()
        }
    }

    private fun registerSystemEventListener() {
        val listener = SystemEventListener()
        systemEventListener = listener
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        registerReceiver(listener, filter)
    }

    private fun unregisterSystemEventListener() {
        systemEventListener?.let {
            try {
                unregisterReceiver(it)
            } catch (e: Exception) {
                // Ignore if not registered
            }
        }
        systemEventListener = null
    }

    private fun initScriptEngine() {
        val bridge = AndroidSystemBridge(this) { logMsg ->
            lifecycleScope.launch {
                _logFlow.emit(logMsg)
            }
        }
        val engineManager = ScriptEngineManager(this, bridge)
        scriptEngineManager = engineManager

        lifecycleScope.launch {
            engineManager.initialize()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        val script = intent?.getStringExtra(EXTRA_SCRIPT)
        if (!script.isNullOrEmpty()) {
            lifecycleScope.launch {
                scriptEngineManager?.evaluateUserScript(script)
            }
        }

        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        // Ensure service remains alive and sticky if user swipes app away from recents
        val restartServiceIntent = Intent(applicationContext, AutomationCoreService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            applicationContext.startForegroundService(restartServiceIntent)
        } else {
            applicationContext.startService(restartServiceIntent)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterSystemEventListener()
        scriptEngineManager?.close()
        scriptEngineManager = null
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
        _isRunning.value = false
        instance = null
    }

    companion object {
        const val NOTIFICATION_ID = 1001
        const val EXTRA_SCRIPT = "extra_user_script"

        @Volatile
        var instance: AutomationCoreService? = null
            private set

        private val _logFlow = MutableSharedFlow<String>(extraBufferCapacity = 100)
        val logFlow: SharedFlow<String> = _logFlow.asSharedFlow()

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

        fun startService(context: Context, script: String? = null) {
            val intent = Intent(context, AutomationCoreService::class.java).apply {
                if (script != null) {
                    putExtra(EXTRA_SCRIPT, script)
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, AutomationCoreService::class.java)
            context.stopService(intent)
        }
    }
}
