package com.automation.app

import android.content.Context
import androidx.javascriptengine.JavaScriptConsoleCallback
import androidx.javascriptengine.JavaScriptIsolate
import androidx.javascriptengine.JavaScriptSandbox
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.concurrent.Executors

class ScriptEngineManager(
    private val context: Context,
    val bridge: AndroidSystemBridge
) {
    private var sandbox: JavaScriptSandbox? = null
    private var isolate: JavaScriptIsolate? = null
    private val executor = Executors.newSingleThreadExecutor()
    private val isInitializedDeferred = CompletableDeferred<Boolean>()

    init {
        bridge.scriptEngineManager = this
    }

    suspend fun initialize() = withContext(Dispatchers.IO) {
        if (!JavaScriptSandbox.isSupported()) {
            bridge.log("JavaScriptSandbox is NOT supported on this device!")
            isInitializedDeferred.complete(false)
            return@withContext
        }
        try {
            val sandboxFuture: ListenableFuture<JavaScriptSandbox> = JavaScriptSandbox.createConnectedInstanceAsync(context)
            val sb = sandboxFuture.await()
            sandbox = sb
            val iso = sb.createIsolate()
            isolate = iso

            // Set console message callback to handle console.log or custom messages
            iso.setConsoleCallback(executor, object : JavaScriptConsoleCallback {
                override fun onConsoleMessage(message: JavaScriptConsoleCallback.ConsoleMessage) {
                    val msgText = message.message
                    if (msgText.startsWith("__BRIDGE_CMD__:")) {
                        handleBridgeCommand(msgText.removePrefix("__BRIDGE_CMD__:"))
                    } else {
                        bridge.log("[JS Console] $msgText")
                    }
                }
            })

            // Inject Core Event System, Timers, and JS AndroidSystemBridge proxy
            val coreJs = """
                const AndroidSystemBridge = {
                    log(msg) {
                        console.log("__BRIDGE_CMD__:" + JSON.stringify({ action: "log", msg: String(msg) }));
                    },
                    vibrate(ms) {
                        console.log("__BRIDGE_CMD__:" + JSON.stringify({ action: "vibrate", ms: Number(ms) }));
                    },
                    launchApp(pkg) {
                        console.log("__BRIDGE_CMD__:" + JSON.stringify({ action: "launchApp", pkg: String(pkg) }));
                        return true;
                    },
                    injectGlobalAction(id) {
                        console.log("__BRIDGE_CMD__:" + JSON.stringify({ action: "injectGlobalAction", id: Number(id) }));
                        return true;
                    },
                    createTimer(id, ms, isInterval) {
                        console.log("__BRIDGE_CMD__:" + JSON.stringify({ action: "createTimer", id: String(id), ms: Number(ms), isInterval: Boolean(isInterval) }));
                    },
                    clearTimer(id) {
                        console.log("__BRIDGE_CMD__:" + JSON.stringify({ action: "clearTimer", id: String(id) }));
                    }
                };

                const EventSystem = {
                    listeners: {},
                    on(e, cb) { (this.listeners[e] = this.listeners[e] || []).push(cb); },
                    emit(e, d) { if(this.listeners[e]) this.listeners[e].forEach(cb => { try{ cb(JSON.parse(d)); }catch(err){ AndroidSystemBridge.log(err.message); } }); }
                };
                function __triggerNativeAndroidEvent(e, d) { EventSystem.emit(e, d); }
                let __tid = 0, __timers = {};
                function setTimeout(cb, ms) { let id = "t_" + (__tid++); __timers[id] = cb; AndroidSystemBridge.createTimer(id, Number(ms), false); return id; }
                function setInterval(cb, ms) { let id = "t_" + (__tid++); __timers[id] = cb; AndroidSystemBridge.createTimer(id, Number(ms), true); return id; }
                function clearTimeout(id) { delete __timers[id]; AndroidSystemBridge.clearTimer(id); }
                let clearInterval = clearTimeout;
                EventSystem.on("TIMER_TICK", (d) => { if(__timers[d.id]) __timers[d.id](); });
            """.trimIndent()

            iso.evaluateJavaScriptAsync(coreJs).await()
            bridge.log("Script Engine initialized successfully.")
            isInitializedDeferred.complete(true)
        } catch (e: Exception) {
            bridge.log("Script Engine initialization error: ${e.message}")
            isInitializedDeferred.complete(false)
        }
    }

    suspend fun awaitInitialization(): Boolean {
        return isInitializedDeferred.await()
    }

    private fun handleBridgeCommand(jsonStr: String) {
        try {
            val json = JSONObject(jsonStr)
            when (json.optString("action")) {
                "log" -> bridge.log(json.optString("msg"))
                "vibrate" -> bridge.vibrate(json.optLong("ms"))
                "launchApp" -> bridge.launchApp(json.optString("pkg"))
                "injectGlobalAction" -> bridge.injectGlobalAction(json.optInt("id"))
                "createTimer" -> bridge.createTimer(
                    json.optString("id"),
                    json.optLong("ms"),
                    json.optBoolean("isInterval")
                )
                "clearTimer" -> bridge.clearTimer(json.optString("id"))
            }
        } catch (e: Exception) {
            bridge.log("Bridge command error: ${e.message}")
        }
    }

    suspend fun evaluateUserScript(script: String) = withContext(Dispatchers.IO) {
        if (!awaitInitialization()) {
            bridge.log("Error: Script Engine failed to initialize")
            return@withContext
        }
        val iso = isolate ?: run {
            bridge.log("Error: Isolate not available")
            return@withContext
        }
        try {
            iso.evaluateJavaScriptAsync(script).await()
            bridge.log("Script executed successfully.")
        } catch (e: Exception) {
            bridge.log("Script execution error: ${e.message}")
        }
    }

    fun emitJsEvent(eventName: String, jsonPayload: String) {
        val iso = isolate ?: return
        val escapedPayload = jsonPayload
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
        val jsCall = "__triggerNativeAndroidEvent(\"$eventName\", \"$escapedPayload\");"
        iso.evaluateJavaScriptAsync(jsCall)
    }

    fun close() {
        bridge.destroy()
        isolate?.close()
        isolate = null
        sandbox?.close()
        sandbox = null
        executor.shutdown()
    }
}
