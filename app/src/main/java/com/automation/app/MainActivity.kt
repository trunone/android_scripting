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
    private lateinit var scriptRepository: ScriptRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        scriptRepository = ScriptRepository(this)

        lifecycleScope.launch {
            AutomationCoreService.logFlow.collectLatest { log ->
                logs.add(log)
            }
        }

        setContent {
            var scripts by remember { mutableStateOf(scriptRepository.getScripts()) }
            var selectedScriptId by remember { mutableStateOf(scripts.firstOrNull()?.id ?: "") }
            val isRunning by AutomationCoreService.isRunning.collectAsState()

            val selectedScript = scripts.find { it.id == selectedScriptId } ?: scripts.firstOrNull()

            MainScreen(
                scripts = scripts,
                selectedScriptId = selectedScriptId,
                onSelectScript = { selectedScriptId = it },
                onAddScript = { name ->
                    val newScript = ScriptItem(
                        name = name,
                        content = "// $name\nAndroidSystemBridge.log(\"Executing $name\");\n"
                    )
                    val updated = scripts + newScript
                    scripts = updated
                    selectedScriptId = newScript.id
                    scriptRepository.saveScripts(updated)
                },
                onDeleteScript = { id ->
                    if (scripts.size > 1) {
                        val updated = scripts.filter { it.id != id }
                        scripts = updated
                        selectedScriptId = updated.first().id
                        scriptRepository.saveScripts(updated)
                    }
                },
                onScriptContentChange = { newContent ->
                    if (selectedScript != null) {
                        val updated = scripts.map {
                            if (it.id == selectedScript.id) it.copy(content = newContent) else it
                        }
                        scripts = updated
                        scriptRepository.saveScripts(updated)
                    }
                },
                logs = logs,
                isServiceRunning = isRunning,
                onStartService = {
                    selectedScript?.let {
                        AutomationCoreService.startService(this@MainActivity, it.content)
                    }
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
