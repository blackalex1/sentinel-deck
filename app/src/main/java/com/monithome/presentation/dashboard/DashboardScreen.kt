package com.monithome.presentation.dashboard

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.staggeredgrid.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.monithome.presentation.components.lyrics.LyricsWidget
import com.monithome.presentation.components.stats.StatsChartsWidget
import com.monithome.presentation.components.proxmox.ProxmoxSettingsDialog
import com.monithome.presentation.dashboard.components.*
import org.koin.androidx.compose.koinViewModel
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import kotlin.math.roundToInt

@Suppress("UNCHECKED_CAST")
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()
    val density = LocalDensity.current

    val onToggleLyrics = remember(viewModel) { { viewModel.processIntent(DashboardIntent.ToggleLyricsFullScreen) } }
    val onToggleStats = remember(viewModel) { { viewModel.processIntent(DashboardIntent.ToggleStatsExpanded) } }
    val onStopReordering = remember(viewModel) { { viewModel.processIntent(DashboardIntent.StopReordering) } }
    val onChangeThemeColor = remember(viewModel) { { color: Long -> viewModel.processIntent(DashboardIntent.ChangeThemeColor(color)) } }

    LaunchedEffect(Unit) {
        viewModel.processIntent(DashboardIntent.Connect())
    }

    if (state.isLoading) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        return
    }

    Scaffold(
        containerColor = Color.Black
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Панель выбора цвета (появляется при перетаскивании)
                AnimatedVisibility(
                    visible = state.isReordering,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    ThemeColorPicker(
                        state = state,
                        selectedColor = state.themeColor,
                        onColorSelect = onChangeThemeColor,
                        onClose = onStopReordering,
                        suggestedColor = state.serverSuggestedColor
                    )
                }

                Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    if (!state.isConnected) {
                        ConnectionScreen(state, viewModel)
                    } else {
                        val gridState = rememberLazyStaggeredGridState()
                        // Фильтруем только активные виджеты для отображения
                        val visibleWidgets = remember(state.widgetOrder, state.activePlugins, state.mediaState.isLyricsActive, state.proxmoxEnabled) {
                            state.widgetOrder.filter { id ->
                                when (id) {
                                    "proxmox", "proxmox_sentinel" -> state.activePlugins.any { it.id == "proxmox_sentinel" && it.active } || state.proxmoxEnabled
                                    "media" -> state.activePlugins.any { it.type == "media_source" && it.active }
                                    "pc_system" -> state.activePlugins.any { it.id == "system_control" && it.active }
                                    "virtual_camera" -> state.activePlugins.any { it.id == "virtual_camera" && it.active }
                                    "yandex_lyrics" -> state.mediaState.isLyricsActive
                                    "system_stats" -> state.activePlugins.any { it.id == "system_stats" && it.active }
                                    "pc_disks" -> state.activePlugins.any { it.id == "system_stats" && it.active }
                                    "keenetic_mihomo" -> state.activePlugins.any { (it.id == "keenetic_proxy" || it.id == "mihomo_service") && it.active }
                                    else -> true
                                }
                            }
                        }

                        LazyVerticalStaggeredGrid(
                            columns = StaggeredGridCells.Fixed(3),
                            state = gridState,
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalItemSpacing = 16.dp
                        ) {
                            itemsIndexed(
                                items = visibleWidgets,
                                key = { _, widgetId -> widgetId }
                            ) { index, widgetId ->
                                var offsetX by remember { mutableStateOf(0f) }
                                var offsetY by remember { mutableStateOf(0f) }
                                var isDragging by remember { mutableStateOf(false) }

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .animateItem()
                                        .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                                        .graphicsLayer {
                                            scaleX = if (isDragging) 1.05f else 1f
                                            scaleY = if (isDragging) 1.05f else 1f
                                            shadowElevation = if (isDragging) with(density) { 16.dp.toPx() } else 0f
                                            alpha = if (isDragging) 0.8f else 1f
                                        }
                                        .pointerInput(state.isReordering) {
                                            if (!state.isReordering) return@pointerInput
                                            detectDragGesturesAfterLongPress(
                                                onDragStart = { isDragging = true },
                                                onDragEnd = {
                                                    isDragging = false
                                                    offsetX = 0f
                                                    offsetY = 0f
                                                },
                                                onDragCancel = {
                                                    isDragging = false
                                                    offsetX = 0f
                                                    offsetY = 0f
                                                },
                                                onDrag = { change, dragAmount ->
                                                    change.consume()
                                                    offsetX += dragAmount.x
                                                    offsetY += dragAmount.y

                                                    val currentItem = gridState.layoutInfo.visibleItemsInfo.find { it.key == widgetId }
                                                    if (currentItem != null) {
                                                        val centerX = currentItem.offset.x + offsetX + (currentItem.size.width / 2)
                                                        val centerY = currentItem.offset.y + offsetY + (currentItem.size.height / 2)

                                                        val targetItem = gridState.layoutInfo.visibleItemsInfo.find { target ->
                                                            target.key != widgetId &&
                                                            centerX > target.offset.x && centerX < (target.offset.x + target.size.width) &&
                                                            centerY > target.offset.y && centerY < (target.offset.y + target.size.height)
                                                        }
                                                        
                                                        if (targetItem != null) {
                                                            val targetIndex = visibleWidgets.indexOf(targetItem.key)
                                                            if (targetIndex != -1) {
                                                                viewModel.processIntent(DashboardIntent.MoveWidget(index, targetIndex))
                                                                offsetX = 0f
                                                                offsetY = 0f
                                                            }
                                                        }
                                                    }
                                                }
                                            )
                                        }
                                ) {
                                    WidgetContent(widgetId, state, viewModel)
                                }
                            }
                        }
                    }
                }
            }

            // ПОЛНОЭКРАННЫЙ ТЕКСТ
            AnimatedVisibility(
                visible = state.isLyricsFullScreen,
                enter = fadeIn() + expandIn(),
                exit = fadeOut() + shrinkOut()
            ) {
                LyricsWidget(
                    lyrics = state.lyrics,
                    baseProgressMs = (state.mediaState.baseProgress * 1000).toLong(),
                    lastUpdateUnixTime = state.mediaState.lastUpdateUnixTime,
                    isPlaying = state.mediaState.isPlaying,
                    isFullScreen = true,
                    coverUrl = state.mediaState.coverUrl,
                    onClick = onToggleLyrics,
                    modifier = Modifier.fillMaxSize()
                )
            }
            
            // ГРАФИКИ СТАТИСТИКИ
            AnimatedVisibility(
                visible = state.isStatsExpanded,
                enter = fadeIn() + expandIn(),
                exit = fadeOut() + shrinkOut()
            ) {
                val systemStats = state.stats["system_stats"] ?: emptyMap()
                StatsChartsWidget(
                    cpuHistory = state.cpuHistory,
                    cpuTempHistory = state.cpuTempHistory,
                    gpuHistory = state.gpuLoadHistory,
                    gpuTempHistory = state.gpuTempHistory,
                    diskTemps = (systemStats["disk_temps"] as? List<Map<String, Any>>) ?: emptyList(),
                    currentStats = systemStats,
                    translations = state.translations,
                    onClose = onToggleStats,
                    modifier = Modifier.fillMaxSize().wrapContentHeight(Alignment.CenterVertically)
                )
            }
        }
        
        // Диалог авторизации (поверх всего)
        if (state.isAuthRequired) {
            AuthDialog(state, viewModel)
        }

        // Диалог подтверждения включения камеры (поверх всего)
        if (state.showCameraConfirmDialog) {
            CameraConfirmDialog(state, viewModel)
        }

        // Диалог настроек Proxmox Sentinel (поверх всего)
        if (state.showProxmoxSettingsDialog) {
            ProxmoxSettingsDialog(
                initialUrl = state.proxmoxUrl,
                initialToken = state.proxmoxToken,
                initialEnabled = state.proxmoxEnabled,
                onSave = { url, token, enabled ->
                    viewModel.processIntent(DashboardIntent.SaveProxmoxConfig(url, token, enabled))
                },
                onDismiss = {
                    viewModel.processIntent(DashboardIntent.SetProxmoxSettingsDialog(false))
                }
            )
        }
    }

    androidx.activity.compose.BackHandler(enabled = state.isLyricsFullScreen || state.isStatsExpanded) {
        if (state.isLyricsFullScreen) viewModel.processIntent(DashboardIntent.ToggleLyricsFullScreen)
        if (state.isStatsExpanded) viewModel.processIntent(DashboardIntent.ToggleStatsExpanded)
    }
}
