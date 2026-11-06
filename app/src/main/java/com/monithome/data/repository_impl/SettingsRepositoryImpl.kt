package com.monithome.data.repository_impl

import android.content.Context
import android.content.SharedPreferences
import com.monithome.domain.repository.SettingsRepository

class SettingsRepositoryImpl(context: Context) : SettingsRepository {
    private val prefs: SharedPreferences = context.getSharedPreferences("monithome_prefs", Context.MODE_PRIVATE)

    override fun getWidgetOrder(): List<String>? {
        val orderString = prefs.getString("widget_order", null)
        return orderString?.split(",")?.filter { it.isNotEmpty() }
    }

    override fun saveWidgetOrder(order: List<String>) {
        val orderString = order.joinToString(",")
        prefs.edit().putString("widget_order", orderString).apply()
    }

    override fun getThemeColor(): Long {
        return prefs.getLong("theme_color", 0xFF22C55E) // Default Green
    }

    override fun saveThemeColor(color: Long) {
        prefs.edit().putLong("theme_color", color).apply()
    }

    override fun getString(key: String, default: String?): String? {
        return prefs.getString(key, default)
    }

    override fun saveString(key: String, value: String?) {
        prefs.edit().putString(key, value).apply()
    }

    override fun getDeviceId(): String {
        var id = prefs.getString("device_unique_id", null)
        if (id == null) {
            id = java.util.UUID.randomUUID().toString()
            prefs.edit().putString("device_unique_id", id).apply()
        }
        return id!!
    }

    override fun getProxmoxConfig(): Triple<String, String, Boolean> {
        val url = prefs.getString("proxmox_server_url", "https://192.168.1.89:8443") ?: "https://192.168.1.89:8443"
        val token = prefs.getString("proxmox_bearer_token", "") ?: ""
        val enabled = prefs.getBoolean("proxmox_enabled", false)
        return Triple(url, token, enabled)
    }

    override fun saveProxmoxConfig(url: String, token: String, enabled: Boolean) {
        prefs.edit()
            .putString("proxmox_server_url", url.trim())
            .putString("proxmox_bearer_token", token.trim())
            .putBoolean("proxmox_enabled", enabled)
            .apply()
    }
}
