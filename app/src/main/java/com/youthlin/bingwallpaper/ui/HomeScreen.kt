package com.youthlin.bingwallpaper.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.youthlin.bingwallpaper.R
import com.youthlin.bingwallpaper.data.DownloadProgress
import com.youthlin.bingwallpaper.data.db.WallpaperEntity

/**
 * 首页壁纸列表（网格视图）。
 * 布局：顶部标题栏 + 自适应列网格 + 刷新按钮。
 * 每张卡片显示缩略图 + 标题，如果有 Wi-Fi 预取下载任务会显示下载进度条。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenDetail: (String) -> Unit,
    vm: BingViewModel = viewModel()
) {
    val list by vm.wallpapers.collectAsStateWithLifecycle()
    val ui by vm.ui.collectAsStateWithLifecycle()
    val settings by vm.userSettings.collectAsStateWithLifecycle()

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    LaunchedEffect(list, settings.prefetchOnWifi) {
        vm.startWifiPrefetchIfNeeded(list)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection)
    ) {
        // 顶部标题栏（滚动时自动收起）
        TopAppBar(
            title = { Text(stringResource(R.string.app_name)) },
            actions = {
                IconButton(onClick = { vm.refresh() }) {
                    Icon(Icons.Outlined.Refresh, contentDescription = stringResource(R.string.action_refresh))
                }
            },
            scrollBehavior = scrollBehavior,
            windowInsets = WindowInsets(0), // 避免重复添加状态栏内边距
        )
        Box(Modifier.fillMaxSize()) {
            if (list.isEmpty() && !ui.loading) {
                // 空状态提示
                Text(
                    text = stringResource(R.string.msg_no_data),
                    modifier = Modifier.align(Alignment.Center),
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                // 自适应列网格（最小列宽 160dp）。
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 160.dp),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(list, key = { it.date }) { entry ->
                        // 观察下载进度（不触发下载，只是读取共享进度流）
                        val progress by remember(entry.date) { vm.progressOf(entry) }
                            .collectAsStateWithLifecycle()
                        val hasLocalUhd = vm.hasLocalUhd(entry)
                        val uhdReady = hasLocalUhd && progress.done
                        WallpaperCard(
                            entry = entry,
                            previewUrl = vm.previewUrl(entry),
                            progress = progress,
                            uhdReady = uhdReady,
                            onClick = { onOpenDetail(entry.date) }
                        )
                    }
                }
            }
            // 刷新中显示加载动画
            if (ui.loading) {
                CircularProgressIndicator(Modifier.align(Alignment.TopCenter).padding(top = 16.dp))
            }
        }
    }
}

/**
 * 单张壁纸卡片。
 * 点击进入详情页。如果 Wi-Fi 预取正在下载此图，底部显示下载进度条。
 */
@Composable
private fun WallpaperCard(
    entry: WallpaperEntity,
    previewUrl: String,
    progress: DownloadProgress?,
    uhdReady: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
    ) {
        Box(Modifier.fillMaxWidth()) {
            // 缩略图（640x360，Coil 自动缓存）
            AsyncImage(
                model = previewUrl,
                contentDescription = entry.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)
            )
            // 下载进度条（只在真正下载中时显示）
            if (progress != null && !progress.done) {
                val indeterminate = progress.percent < 0
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.5f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (indeterminate)
                            stringResource(R.string.hint_downloading)
                        else
                            stringResource(R.string.hint_downloading_pct, progress.percent),
                        color = Color.White,
                        fontSize = 11.sp
                    )
                    if (indeterminate) {
                        // 不确定进度（服务器没返回文件大小）
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth().padding(top = 3.dp),
                            color = Color.White,
                            trackColor = Color.White.copy(alpha = 0.3f)
                        )
                    } else {
                        // 确定进度（有百分比）
                        LinearProgressIndicator(
                            progress = { progress.percent / 100f },
                            modifier = Modifier.fillMaxWidth().padding(top = 3.dp),
                            color = Color.White,
                            trackColor = Color.White.copy(alpha = 0.3f)
                        )
                    }
                }
            }
        }
        // 标题区域固定单行高度，避免长标题让网格 item 高度不一致。
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                text = "${entry.date} ${entry.title}",
                style = MaterialTheme.typography.bodyMedium,
                color = if (uhdReady) {
                    Color.Unspecified
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.62f)
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
