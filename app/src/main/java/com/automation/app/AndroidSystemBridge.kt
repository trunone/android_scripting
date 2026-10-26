package com.automation.app

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap

class AndroidSystemBridge(
    private val context: Context,
    private val onLog: (String) -> Unit
) {
    var scriptEngineManager: ScriptEngineManager? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val timerJobs = ConcurrentHashMap<String, Job>()

    fun log(msg: String) {
        onLog(msg)
    }

    fun vibrate(ms: Long) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                val vibrator = vibratorManager.defaultVibrator
                vibrator.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(ms)
                }
            }
        } catch (e: Exception) {
            log("Vibrate error: ${e.message}")
        }
    }

    fun launchApp(pkg: String): Boolean {
        return try {
            val intent = context.packageManager.getLaunchIntentForPackage(pkg)
            if (intent != null) {
                intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                true
            } else {
                log("App not found: $pkg")
                false
            }
        } catch (e: Exception) {
            log("LaunchApp error: ${e.message}")
            false
        }
    }

    fun injectGlobalAction(id: Int): Boolean {
        return AutomationAccessibilityService.instance?.performGlobalAction(id) ?: run {
            log("Accessibility Service not running")
            false
        }
    }

    fun createTimer(id: String, ms: Long, isInterval: Boolean) {
        clearTimer(id)
        val job = scope.launch {
            if (isInterval) {
                while (isActive) {
                    delay(ms)
                    if (isActive) {
                        scriptEngineManager?.emitJsEvent("TIMER_TICK", "{\"id\":\"$id\"}")
                    }
                }
            } else {
                delay(ms)
                if (isActive) {
                    scriptEngineManager?.emitJsEvent("TIMER_TICK", "{\"id\":\"$id\"}")
                }
            }
        }
        timerJobs[id] = job
    }

    fun clearTimer(id: String) {
        timerJobs.remove(id)?.cancel()
    }

    fun destroy() {
        scope.cancel()
        timerJobs.clear()
    }
}
