package com.automation.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import org.json.JSONObject

class SystemEventListener : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return

        when (intent.action) {
            Intent.ACTION_BATTERY_CHANGED -> {
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                val batteryPct = if (level >= 0 && scale > 0) {
                    (level * 100 / scale.toFloat()).toInt()
                } else {
                    -1
                }
                val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                        status == BatteryManager.BATTERY_STATUS_FULL

                val json = JSONObject().apply {
                    put("level", batteryPct)
                    put("isCharging", isCharging)
                    put("action", intent.action)
                }.toString()

                AutomationCoreService.instance?.scriptEngineManager?.emitJsEvent("BATTERY_CHANGED", json)
            }
            Intent.ACTION_POWER_CONNECTED,
            Intent.ACTION_POWER_DISCONNECTED -> {
                val isConnected = intent.action == Intent.ACTION_POWER_CONNECTED
                val json = JSONObject().apply {
                    put("isCharging", isConnected)
                    put("action", intent.action)
                }.toString()

                AutomationCoreService.instance?.scriptEngineManager?.emitJsEvent("BATTERY_CHANGED", json)
            }
            Intent.ACTION_SCREEN_ON,
            Intent.ACTION_SCREEN_OFF -> {
                val isScreenOn = intent.action == Intent.ACTION_SCREEN_ON
                val json = JSONObject().apply {
                    put("isScreenOn", isScreenOn)
                    put("action", intent.action)
                }.toString()

                AutomationCoreService.instance?.scriptEngineManager?.emitJsEvent("SCREEN_CHANGED", json)
            }
            Intent.ACTION_BOOT_COMPLETED -> {
                AutomationCoreService.startService(context)
            }
        }
    }
}
