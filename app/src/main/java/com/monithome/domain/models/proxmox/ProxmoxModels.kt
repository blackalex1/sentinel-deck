package com.monithome.domain.models.proxmox

data class ProxmoxAlert(
    val id: String = "",
    val timestamp: Double = 0.0,
    val severity: String = "WARNING", // "CRITICAL", "WARNING", "INFO"
    val category: String = "SECURITY", // "IPS", "AUTH", "RESOURCE", "SECURITY"
    val text: String = "",
    val parseMode: String = "HTML",
    val metadata: Map<String, Any> = emptyMap()
)

data class ProxmoxLog(
    val id: String = "",
    val timestamp: Double = 0.0,
    val source: String = "",
    val level: String = "INFO",
    val line: String = ""
)

sealed class ProxmoxConnectionState {
    object Disconnected : ProxmoxConnectionState()
    object Connecting : ProxmoxConnectionState()
    object Connected : ProxmoxConnectionState()
    data class Error(val message: String) : ProxmoxConnectionState()
}
