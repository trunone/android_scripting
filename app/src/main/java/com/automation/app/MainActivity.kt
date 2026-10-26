package com.automation.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val logs = mutableStateListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycleScope.launch {
            AutomationCoreService.logFlow.collectLatest { log ->
                logs.add(log)
            }
        }

        val defaultScript = """
            AndroidSystemBridge.log("Engine running!");
            setInterval(() => { AndroidSystemBridge.log("Tick"); }, 5000);
            EventSystem.on("NOTIFICATION_RECEIVED", (n) => {
                if (n.packageName === "com.whatsapp" && n.text.includes("ALERT")) {
                    AndroidSystemBridge.vibrate(500);
                    AndroidSystemBridge.launchApp("com.whatsapp");
                }
            });
            EventSystem.on("UI_CHANGED", (ui) => {
                if (ui.packageName === "com.android.settings" && ui.textItems.includes("Storage")) {
                    AndroidSystemBridge.injectGlobalAction(1); // BACK
                }
            });
        """.trimIndent()

        setContent {
            var scriptText by remember { mutableStateOf(defaultScript) }
            val isRunning by AutomationCoreService.isRunning.collectAsState()

            MainScreen(
                scriptText = scriptText,
                onScriptTextChange = { scriptText = it },
                logs = logs,
                isServiceRunning = isRunning,
                onStartService = {
                    AutomationCoreService.startService(this@MainActivity, scriptText)
                },
                onStopService = {
                    AutomationCoreService.stopService(this@MainActivity)
                },
                onClearLogs = {
                    logs.clear()
                }
            )
        }
    }
}
