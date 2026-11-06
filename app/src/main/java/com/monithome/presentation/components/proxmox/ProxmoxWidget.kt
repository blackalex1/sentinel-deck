package com.monithome.presentation.components.proxmox

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.monithome.domain.models.proxmox.ProxmoxAlert
import com.monithome.domain.models.proxmox.ProxmoxLog
import com.monithome.presentation.dashboard.DashboardIntent

@Composable
fun ProxmoxWidget(
    connected: Boolean,
    enabled: Boolean,
    error: String?,
    alerts: List<ProxmoxAlert>,
    logs: List<ProxmoxLog>,
    themeColor: Long,
    onIntent: (DashboardIntent) -> Unit,
    modifier: Modifier = Modifier
) {
    val accentColor = Color(themeColor)
    var selectedTab by remember { mutableStateOf(0) } // 0: Алерты, 1: Логи
    val logListState = rememberLazyListState()

    // Автопрокрутка логов к последней записи
    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty() && selectedTab == 1) {
            logListState.animateScrollToItem(logs.size - 1)
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            // Заголовок виджета
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = "Proxmox Sentinel",
                        tint = accentColor,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Proxmox Sentinel",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Индикатор статуса
                    val (statusColor, statusText) = when {
                        !enabled -> Color.Gray to "Откл."
                        connected -> Color(0xFF22C55E) to "WSS Онлайн"
                        error != null -> Color(0xFFEF4444) to "Сбой"
                        else -> Color(0xFFF59E0B) to "Подключение..."
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .background(statusColor.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .background(statusColor, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = statusText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = statusColor
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(
                        onClick = { onIntent(DashboardIntent.SetProxmoxSettingsDialog(true)) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = Color.LightGray,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Переключатель вкладок: Алерты / Консоль логов
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1E232A), RoundedCornerShape(10.dp))
                    .padding(3.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(
                            if (selectedTab == 0) accentColor.copy(alpha = 0.25f) else Color.Transparent,
                            RoundedCornerShape(8.dp)
                        )
                        .clickable { selectedTab = 0 }
                        .padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Алерты",
                            fontSize = 13.sp,
                            fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal,
                            color = if (selectedTab == 0) Color.White else Color.Gray
                        )
                        if (alerts.isNotEmpty()) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .background(Color(0xFFEF4444), CircleShape)
                                    .padding(horizontal = 6.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    text = alerts.size.toString(),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(
                            if (selectedTab == 1) accentColor.copy(alpha = 0.25f) else Color.Transparent,
                            RoundedCornerShape(8.dp)
                        )
                        .clickable { selectedTab = 1 }
                        .padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Консоль логов (${logs.size})",
                        fontSize = 13.sp,
                        fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal,
                        color = if (selectedTab == 1) Color.White else Color.Gray
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Контент вкладки
            if (selectedTab == 0) {
                // Вкладка Алертов
                if (alerts.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "OK",
                                tint = Color(0xFF22C55E),
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Угроз безопасности не обнаружено",
                                fontSize = 13.sp,
                                color = Color.Gray
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(alerts, key = { it.id }) { alert ->
                            AlertCardItem(alert)
                        }
                    }
                }
            } else {
                // Вкладка Консоли логов
                Column(modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .background(Color(0xFF0D1117), RoundedCornerShape(10.dp))
                            .border(1.dp, Color(0xFF30363D), RoundedCornerShape(10.dp))
                            .padding(8.dp)
                    ) {
                        if (logs.isEmpty()) {
                            Text(
                                text = if (!enabled) "Мониторинг выключен" else "Ожидание потока логов по WSS...",
                                color = Color.Gray,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.align(Alignment.Center)
                            )
                        } else {
                            LazyColumn(
                                state = logListState,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(logs, key = { it.id }) { logItem ->
                                    LogLineItem(logItem)
                                }
                            }
                        }
                    }

                    if (logs.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(
                                onClick = { onIntent(DashboardIntent.ClearProxmoxLogs) },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Clear",
                                    tint = Color.Gray,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Очистить", fontSize = 11.sp, color = Color.Gray)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AlertCardItem(alert: ProxmoxAlert) {
    val isCritical = alert.severity == "CRITICAL"
    val badgeColor = if (isCritical) Color(0xFFEF4444) else Color(0xFFF59E0B)
    val timeStr = rememberFormattedTime(alert.timestamp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(badgeColor.copy(alpha = 0.08f), RoundedCornerShape(10.dp))
            .border(1.dp, badgeColor.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
            .padding(10.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .background(badgeColor, RoundedCornerShape(4.dp))
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = alert.severity,
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = alert.category,
                        color = Color.LightGray,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Text(
                    text = timeStr,
                    color = Color.Gray,
                    fontSize = 11.sp
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = alert.text,
                fontSize = 13.sp,
                color = Color.White,
                lineHeight = 17.sp
            )
        }
    }
}

@Composable
private fun LogLineItem(log: ProxmoxLog) {
    val tagColor = when {
        log.line.contains("kill", ignoreCase = true) || log.level == "CRITICAL" -> Color(0xFFEF4444)
        log.source.contains("auth", ignoreCase = true) || log.level == "WARN" -> Color(0xFFFBBF24)
        log.source.contains("ips", ignoreCase = true) -> Color(0xFFF87171)
        else -> Color(0xFF38BDF8)
    }

    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
        Text(
            text = "[${log.source}] ",
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            color = tagColor,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = log.line,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            color = Color(0xFFE6EDF3),
            lineHeight = 14.sp
        )
    }
}
