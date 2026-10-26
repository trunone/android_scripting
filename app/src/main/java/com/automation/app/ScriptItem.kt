package com.automation.app

import java.util.UUID

data class ScriptItem(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val content: String,
    val isEnabled: Boolean = true
)
