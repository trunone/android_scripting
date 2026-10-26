package com.automation.app

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

class ScriptRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("automation_scripts_prefs", Context.MODE_PRIVATE)

    fun getScripts(): List<ScriptItem> {
        val jsonStr = prefs.getString(KEY_SCRIPTS, null) ?: return getDefaultScripts()
        val list = mutableListOf<ScriptItem>()
        try {
            val jsonArray = JSONArray(jsonStr)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    ScriptItem(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        content = obj.getString("content"),
                        isEnabled = obj.optBoolean("isEnabled", true)
                    )
                )
            }
        } catch (e: Exception) {
            return getDefaultScripts()
        }
        return if (list.isEmpty()) getDefaultScripts() else list
    }

    fun saveScripts(scripts: List<ScriptItem>) {
        val jsonArray = JSONArray()
        for (script in scripts) {
            val obj = JSONObject().apply {
                put("id", script.id)
                put("name", script.name)
                put("content", script.content)
                put("isEnabled", script.isEnabled)
            }
            jsonArray.put(obj)
        }
        prefs.edit().putString(KEY_SCRIPTS, jsonArray.toString()).apply()
    }

    private fun getDefaultScripts(): List<ScriptItem> {
        val defaultScriptContent = """
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

        return listOf(
            ScriptItem(
                name = "Default Automation",
                content = defaultScriptContent,
                isEnabled = true
            )
        )
    }

    companion object {
        private const val KEY_SCRIPTS = "saved_scripts"
    }
}
