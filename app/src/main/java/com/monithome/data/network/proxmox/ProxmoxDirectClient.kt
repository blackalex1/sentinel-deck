package com.monithome.data.network.proxmox

import android.util.Log
import com.monithome.domain.models.proxmox.ProxmoxAlert
import com.monithome.domain.models.proxmox.ProxmoxConnectionState
import com.monithome.domain.models.proxmox.ProxmoxLog
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class ProxmoxDirectClient(baseClient: OkHttpClient) {
    private val TAG = "ProxmoxDirectClient"

    // OkHttpClient с поддержкой TLSv1.2+ и самоподписанных сертификатов (унаследован от базового)
    private val client = baseClient.newBuilder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .connectTimeout(10, TimeUnit.SECONDS)
        .pingInterval(25, TimeUnit.SECONDS)
        .build()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var webSocket: WebSocket? = null
    private var reconnectJob: Job? = null
    private var heartbeatJob: Job? = null

    @Volatile
    private var serverUrl: String = ""
    @Volatile
    private var bearerToken: String = ""
    @Volatile
    private var isEnabled: Boolean = false

    private val _connectionState = MutableStateFlow<ProxmoxConnectionState>(ProxmoxConnectionState.Disconnected)
    val connectionState: StateFlow<ProxmoxConnectionState> = _connectionState.asStateFlow()

    private val _alerts = MutableStateFlow<List<ProxmoxAlert>>(emptyList())
    val alerts: StateFlow<List<ProxmoxAlert>> = _alerts.asStateFlow()

    private val _logs = MutableStateFlow<List<ProxmoxLog>>(emptyList())
    val logs: StateFlow<List<ProxmoxLog>> = _logs.asStateFlow()

    private val _latestAlert = MutableSharedFlow<ProxmoxAlert>(extraBufferCapacity = 32)
    val latestAlert: SharedFlow<ProxmoxAlert> = _latestAlert.asSharedFlow()

    fun configure(url: String, token: String, enabled: Boolean) {
        val changed = (url != serverUrl || token != bearerToken || enabled != isEnabled)
        this.serverUrl = url.trim()
        this.bearerToken = token.trim()
        this.isEnabled = enabled

        if (changed) {
            Log.i(TAG, "Configuration updated: enabled=$enabled, url=$serverUrl")
            disconnect()
            if (isEnabled && serverUrl.isNotEmpty() && bearerToken.isNotEmpty()) {
                connect()
            }
        }
    }

    fun connect() {
        if (!isEnabled || serverUrl.isEmpty() || bearerToken.isEmpty()) {
            _connectionState.value = ProxmoxConnectionState.Disconnected
            return
        }

        reconnectJob?.cancel()
        webSocket?.cancel()

        _connectionState.value = ProxmoxConnectionState.Connecting

        val wsUrl = formatWebSocketUrl(serverUrl)
        Log.i(TAG, "Connecting to Proxmox Sentinel: $wsUrl")

        val request = try {
            Request.Builder()
                .url(wsUrl)
                .addHeader("Authorization", "Bearer $bearerToken")
                .build()
        } catch (e: Exception) {
            Log.e(TAG, "Invalid URL: $wsUrl", e)
            _connectionState.value = ProxmoxConnectionState.Error("Неверный формат URL: ${e.message}")
            return
        }

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.i(TAG, "Connected successfully to Proxmox Sentinel (HTTP ${response.code})")
                _connectionState.value = ProxmoxConnectionState.Connected
                startHeartbeat()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleIncomingMessage(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.w(TAG, "WebSocket closing ($code): $reason")
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.i(TAG, "WebSocket closed ($code): $reason")
                stopHeartbeat()
                _connectionState.value = ProxmoxConnectionState.Disconnected
                scheduleReconnect()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                val errorMsg = if (response != null) "HTTP ${response.code}: ${response.message}" else t.message ?: "Ошибка сети"
                Log.e(TAG, "WebSocket failure: $errorMsg", t)
                stopHeartbeat()
                _connectionState.value = ProxmoxConnectionState.Error(errorMsg)
                scheduleReconnect()
            }
        })
    }

    fun disconnect() {
        reconnectJob?.cancel()
        stopHeartbeat()
        webSocket?.close(1000, "Client disconnect requested")
        webSocket = null
        _connectionState.value = ProxmoxConnectionState.Disconnected
    }

    fun clearAlerts() {
        _alerts.value = emptyList()
    }

    fun clearLogs() {
        _logs.value = emptyList()
    }

    private fun formatWebSocketUrl(rawUrl: String): String {
        var clean = rawUrl.trim()
        if (!clean.contains("://")) {
            clean = "https://$clean"
        }
        val wsUrl = when {
            clean.startsWith("https://", ignoreCase = true) -> "wss://" + clean.substring(8)
            clean.startsWith("http://", ignoreCase = true) -> "ws://" + clean.substring(7)
            clean.startsWith("wss://", ignoreCase = true) -> clean
            clean.startsWith("ws://", ignoreCase = true) -> clean
            else -> "wss://$clean"
        }

        val noSlash = wsUrl.trimEnd('/')
        return if (noSlash.endsWith("/api/v1/ws/stream")) {
            noSlash
        } else {
            "$noSlash/api/v1/ws/stream"
        }
    }

    private fun handleIncomingMessage(text: String) {
        try {
            val json = JSONObject(text)
            when (json.optString("type")) {
                "init" -> {
                    // Парсинг начального снимка алертов и логов
                    val recentAlerts = parseAlertsArray(json.optJSONArray("recent_alerts"))
                    val recentLogs = parseLogsArray(json.optJSONArray("recent_logs"))
                    _alerts.value = recentAlerts
                    _logs.value = recentLogs
                    Log.i(TAG, "Initialized snapshot: ${recentAlerts.size} alerts, ${recentLogs.size} logs")
                }
                "alert" -> {
                    val alert = parseAlert(json)
                    _alerts.update { current ->
                        val updated = (listOf(alert) + current.filter { it.id != alert.id }).take(100)
                        updated
                    }
                    _latestAlert.tryEmit(alert)
                    Log.i(TAG, "New Proxmox Alert: [${alert.severity}] ${alert.text}")
                }
                "log" -> {
                    val logEntry = parseLog(json)
                    _logs.update { current ->
                        (current + logEntry).takeLast(200)
                    }
                }
                "pong" -> {
                    Log.d(TAG, "Heartbeat pong received")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse incoming Proxmox message: ${e.message}")
        }
    }

    private fun parseAlert(obj: JSONObject): ProxmoxAlert {
        return ProxmoxAlert(
            id = obj.optString("id", "alert_${System.currentTimeMillis()}"),
            timestamp = obj.optDouble("timestamp", System.currentTimeMillis() / 1000.0),
            severity = obj.optString("severity", "WARNING").uppercase(),
            category = obj.optString("category", "SECURITY").uppercase(),
            text = obj.optString("text", ""),
            parseMode = obj.optString("parse_mode", "HTML")
        )
    }

    private fun parseLog(obj: JSONObject): ProxmoxLog {
        return ProxmoxLog(
            id = obj.optString("id", "log_${System.currentTimeMillis()}"),
            timestamp = obj.optDouble("timestamp", System.currentTimeMillis() / 1000.0),
            source = obj.optString("source", "system"),
            level = obj.optString("level", "INFO").uppercase(),
            line = obj.optString("line", "")
        )
    }

    private fun parseAlertsArray(arr: JSONArray?): List<ProxmoxAlert> {
        if (arr == null) return emptyList()
        val list = mutableListOf<ProxmoxAlert>()
        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i)
            if (item != null) list.add(parseAlert(item))
        }
        return list
    }

    private fun parseLogsArray(arr: JSONArray?): List<ProxmoxLog> {
        if (arr == null) return emptyList()
        val list = mutableListOf<ProxmoxLog>()
        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i)
            if (item != null) list.add(parseLog(item))
        }
        return list
    }

    private fun startHeartbeat() {
        stopHeartbeat()
        heartbeatJob = scope.launch {
            while (isActive) {
                delay(20000)
                try {
                    webSocket?.send(JSONObject().put("action", "ping").toString())
                } catch (e: Exception) {
                    Log.d(TAG, "Heartbeat ping failed: ${e.message}")
                    break
                }
            }
        }
    }

    private fun stopHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = null
    }

    private fun scheduleReconnect() {
        if (!isEnabled || serverUrl.isEmpty()) return
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            delay(5000)
            if (isEnabled && _connectionState.value !is ProxmoxConnectionState.Connected) {
                Log.i(TAG, "Attempting auto-reconnect to Proxmox Sentinel...")
                connect()
            }
        }
    }
}
