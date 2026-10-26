package com.automation.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.text.TextUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    scripts: List<ScriptItem>,
    selectedScriptId: String,
    onSelectScript: (String) -> Unit,
    onAddScript: (String) -> Unit,
    onDeleteScript: (String) -> Unit,
    onScriptContentChange: (String) -> Unit,
    logs: List<String>,
    isServiceRunning: Boolean,
    onStartService: () -> Unit,
    onStopService: () -> Unit,
    onClearLogs: () -> Unit
) {
    val context = LocalContext.current

    var isAccessibilityActive by remember { mutableStateOf(isAccessibilityEnabled(context)) }
    var isNotificationActive by remember { mutableStateOf(isNotificationListenerEnabled(context)) }
    var isBatteryOptIgnored by remember { mutableStateOf(isIgnoringBatteryOptimizations(context)) }

    var showAddDialog by remember { mutableStateOf(false) }
    var newScriptName by remember { mutableStateOf("") }
    var dropdownExpanded by remember { mutableStateOf(false) }

    val currentScript = scripts.find { it.id == selectedScriptId } ?: scripts.firstOrNull()

    val listState = rememberLazyListState()

    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.size - 1)
        }
    }

    DisposableEffect(Unit) {
        isAccessibilityActive = isAccessibilityEnabled(context)
        isNotificationActive = isNotificationListenerEnabled(context)
        isBatteryOptIgnored = isIgnoringBatteryOptimizations(context)
        onDispose { }
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("New Script") },
            text = {
                OutlinedTextField(
                    value = newScriptName,
                    onValueChange = { newScriptName = it },
                    label = { Text("Script Name") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newScriptName.isNotBlank()) {
                            onAddScript(newScriptName.trim())
                            newScriptName = ""
                            showAddDialog = false
                        }
                    }
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Automation Core Engine",
                style = MaterialTheme.typography.headlineMedium
            )

            // Status Indicators & Permission Prompts
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(text = "System Permissions & Services", style = MaterialTheme.typography.titleMedium)

                    StatusRow(
                        label = "Accessibility Service",
                        isActive = isAccessibilityActive,
                        onRequest = {
                            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(intent)
                        }
                    )

                    StatusRow(
                        label = "Notification Listener",
                        isActive = isNotificationActive,
                        onRequest = {
                            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(intent)
                        }
                    )

                    StatusRow(
                        label = "Battery Optimization Exclusion",
                        isActive = isBatteryOptIgnored,
                        onRequest = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                }
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                context.startActivity(intent)
                            }
                        }
                    )
                }
            }

            // Script Selection Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    OutlinedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { dropdownExpanded = true }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = currentScript?.name ?: "Select Script",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Icon(Icons.Default.ArrowDropDown, contentDescription = "Dropdown")
                        }
                    }

                    DropdownMenu(
                        expanded = dropdownExpanded,
                        onDismissRequest = { dropdownExpanded = false }
                    ) {
                        scripts.forEach { script ->
                            DropdownMenuItem(
                                text = { Text(script.name) },
                                onClick = {
                                    onSelectScript(script.id)
                                    dropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                IconButton(onClick = { showAddDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Add Script")
                }

                if (scripts.size > 1 && currentScript != null) {
                    IconButton(onClick = { onDeleteScript(currentScript.id) }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete Script", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }

            // Script Input Field
            OutlinedTextField(
                value = currentScript?.content ?: "",
                onValueChange = onScriptContentChange,
                label = { Text("JavaScript Code (${currentScript?.name ?: ""})") },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 13.sp),
                maxLines = 15
            )

            // Service Control Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        if (isServiceRunning) onStopService() else onStartService()
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isServiceRunning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(if (isServiceRunning) "Stop Service" else "Run Selected Script")
                }

                OutlinedButton(
                    onClick = onClearLogs
                ) {
                    Text("Clear Logs")
                }
            }

            // Log Console
            Text(
                text = "Console Logs",
                style = MaterialTheme.typography.titleMedium
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .background(Color.Black, shape = RoundedCornerShape(8.dp))
                    .padding(8.dp)
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(logs) { log ->
                        Text(
                            text = log,
                            color = Color.Green,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StatusRow(
    label: String,
    isActive: Boolean,
    onRequest: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = if (isActive) "●" else "○",
                color = if (isActive) Color.Green else Color.Red,
                fontSize = 16.sp
            )
            Text(text = label, style = MaterialTheme.typography.bodyMedium)
        }
        if (!isActive) {
            TextButton(onClick = onRequest) {
                Text("Enable", fontSize = 12.sp)
            }
        } else {
            Text("ACTIVE", color = Color.Green, fontSize = 12.sp, modifier = Modifier.padding(end = 8.dp))
        }
    }
}

private fun isAccessibilityEnabled(context: Context): Boolean {
    val expectedService = "${context.packageName}/${AutomationAccessibilityService::class.java.canonicalName}"
    val enabledServices = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ) ?: return false

    val splitter = TextUtils.SimpleStringSplitter(':')
    splitter.setString(enabledServices)
    while (splitter.hasNext()) {
        val service = splitter.next()
        if (service.equals(expectedService, ignoreCase = true)) {
            return true
        }
    }
    return false
}

private fun isNotificationListenerEnabled(context: Context): Boolean {
    val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
    val packageName = context.packageName
    return flat != null && flat.contains(packageName)
}

private fun isIgnoringBatteryOptimizations(context: Context): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }
    return true
}
