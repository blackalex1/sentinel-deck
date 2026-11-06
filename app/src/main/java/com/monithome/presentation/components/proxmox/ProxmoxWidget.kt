package com.monithome.presentation.components.proxmox

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.monithome.domain.models.proxmox.ProxmoxAlert
import com.monithome.presentation.dashboard.DashboardIntent

@Composable
fun ProxmoxWidget(
    connected: Boolean,
    enabled: Boolean,
    error: String?,
    alerts: List<ProxmoxAlert>,
    themeColor: Long,
    onIntent: (DashboardIntent) -> Unit,
    modifier: Modifier = Modifier
) {
    val accentColor = Color(themeColor)

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
                    if (alerts.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .background(Color(0xFFEF4444), CircleShape)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
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

            // Список алертов
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
                        .heightIn(min = 140.dp, max = 340.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(alerts, key = { it.id }) { alert ->
                        SwipeableAlertItem(
                            alert = alert,
                            onDismiss = { onIntent(DashboardIntent.DismissProxmoxAlert(alert.id)) }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableAlertItem(
    alert: ProxmoxAlert,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dismissState = rememberSwipeToDismissBoxState()

    LaunchedEffect(dismissState.currentValue) {
        if (dismissState.currentValue != SwipeToDismissBoxValue.Settled) {
            onDismiss()
        }
    }

    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier,
        backgroundContent = {
            val direction = dismissState.dismissDirection
            val isSwiping = direction != SwipeToDismissBoxValue.Settled &&
                    (dismissState.targetValue != SwipeToDismissBoxValue.Settled || dismissState.progress != 0f)
            if (isSwiping) {
                val alignment = when (direction) {
                    SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
                    SwipeToDismissBoxValue.EndToStart,
                    SwipeToDismissBoxValue.Settled -> Alignment.CenterEnd
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFFDC2626), RoundedCornerShape(10.dp))
                        .padding(horizontal = 16.dp),
                    contentAlignment = alignment
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Удалить",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Удалить",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            } else {
                Box(modifier = Modifier.fillMaxSize())
            }
        },
        content = {
            AlertCardItem(alert = alert)
        }
    )
}

@Composable
private fun AlertCardItem(alert: ProxmoxAlert) {
    var isExpanded by remember { mutableStateOf(false) }
    val isCritical = alert.severity == "CRITICAL"
    val badgeColor = if (isCritical) Color(0xFFEF4444) else Color(0xFFF59E0B)
    val timeStr = rememberFormattedTime(alert.timestamp)
    val rotationAngle by animateFloatAsState(targetValue = if (isExpanded) 180f else 0f, label = "alertChevron")

    val blocks = remember(alert.text) { ProxmoxMarkdownParser.parse(alert.text) }

    // Извлекаем заголовок и подзаголовок для краткого свернутого вида
    val mainHeader = remember(blocks) {
        blocks.filterIsInstance<ProxmoxBlock.Header>().firstOrNull { it.level == 1 }?.text
            ?: blocks.filterIsInstance<ProxmoxBlock.Header>().firstOrNull()?.text
            ?: blocks.firstOrNull { it is ProxmoxBlock.Paragraph }?.let { (it as ProxmoxBlock.Paragraph).text }
            ?: alert.text.lines().firstOrNull()?.trim()
            ?: ""
    }

    val subHeader = remember(blocks) {
        val h1 = blocks.filterIsInstance<ProxmoxBlock.Header>().firstOrNull { it.level == 1 }
        if (h1 != null) {
            blocks.filterIsInstance<ProxmoxBlock.Header>().firstOrNull { it.level > 1 }?.text
        } else null
    }

    // Блоки для детального раскрытого отображения (без дублирования верхних заголовков)
    val detailBlocks = remember(blocks) {
        val hasHeaders = blocks.any { it is ProxmoxBlock.Header }
        if (hasHeaders) {
            blocks.filterNot { it is ProxmoxBlock.Header }
        } else {
            blocks
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF13171F), RoundedCornerShape(10.dp))
            .background(badgeColor.copy(alpha = if (isExpanded) 0.12f else 0.07f), RoundedCornerShape(10.dp))
            .border(1.dp, badgeColor.copy(alpha = if (isExpanded) 0.5f else 0.25f), RoundedCornerShape(10.dp))
            .clickable { isExpanded = !isExpanded }
            .padding(10.dp)
    ) {
        Column {
            // Верхняя строка: бейдж важности, категория, время и стрелка раскрытия
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

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = timeStr,
                        color = Color.Gray,
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.ExpandMore,
                        contentDescription = if (isExpanded) "Свернуть" else "Развернуть",
                        tint = Color.LightGray,
                        modifier = Modifier
                            .size(18.dp)
                            .rotate(rotationAngle)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Основной заголовок (виден всегда)
            if (mainHeader.isNotEmpty()) {
                Text(
                    text = parseInlineMarkdown(mainHeader),
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = if (isExpanded) 3 else 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Подзаголовок (если есть, виден всегда в свернутом и развернутом виде)
            if (!subHeader.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = parseInlineMarkdown(subHeader),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF38BDF8),
                    maxLines = if (isExpanded) 3 else 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Раскрывающаяся детальная часть (таблицы, логи, подробности)
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    if (detailBlocks.isNotEmpty()) {
                        ProxmoxMarkdownBlocksContent(blocks = detailBlocks)
                    } else {
                        ProxmoxMarkdownContent(text = alert.text)
                    }
                }
            }
        }
    }
}
