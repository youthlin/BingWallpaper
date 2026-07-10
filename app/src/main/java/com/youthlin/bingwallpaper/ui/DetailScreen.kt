package com.youthlin.bingwallpaper.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Wallpaper
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.youthlin.bingwallpaper.R
import com.youthlin.bingwallpaper.data.WallpaperImage
import com.youthlin.bingwallpaper.data.db.WallpaperEntity
import android.content.Context
import android.content.Intent
import android.widget.Toast

/**
 * 壁纸详情页。
 * 布局：顶部标题栏（返回 + 菜单） + 可缩放大图 + 版权信息 + 日期 + 设为壁纸按钮。
 * 左右滑动可浏览不同日期的壁纸。
 *
 * 菜单项：搜索图片 / 查看原图 / 复制链接 / 分享图片
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    id: String,       // 要展示的壁纸日期
    onBack: () -> Unit, // 返回列表
    vm: BingViewModel = viewModel()
) {
    val context = LocalContext.current
    val list by vm.wallpapers.collectAsStateWithLifecycle()
    val ui by vm.ui.collectAsStateWithLifecycle()

    // 根据传入的 id 找到当前页码
    val startIndex = list.indexOfFirst { it.date == id }.let { if (it < 0) 0 else it }
    val pagerState = rememberPagerState(initialPage = startIndex, pageCount = { list.size })

    // 如果列表变化（如刷新后），重新定位到当前 id
    LaunchedEffect(list.size, id) {
        val idx = list.indexOfFirst { it.date == id }
        if (idx >= 0 && idx != pagerState.currentPage) pagerState.scrollToPage(idx)
    }

    val currentEntry: WallpaperEntity? = list.getOrNull(pagerState.currentPage)

    Scaffold(
        topBar = {
            // 半透明标题栏
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                ),
                title = { Text(text = currentEntry?.title ?: id, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    // 更多菜单
                    var menuExpanded by remember { mutableStateOf(false) }
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.action_more))
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        // 1. 查看原图：在浏览器中打开 UHD 大图
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_open_original)) },
                            leadingIcon = { Icon(Icons.Outlined.OpenInBrowser, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                currentEntry?.let { entry ->
                                    openExternalLink(context, vm.originalUrl(entry))
                                }
                            }
                        )
                        // 2. 查看竖屏壁纸：下载/打开 Bing 提供的手机壁纸版
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_open_portrait)) },
                            leadingIcon = { Icon(Icons.Outlined.PhoneAndroid, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                currentEntry?.let { vm.viewPortrait(it) }
                            }
                        )
                        // 3. 分享图片：先下载到本地/图库，再通过系统分享发送
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_share)) },
                            leadingIcon = { Icon(Icons.Outlined.Share, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                currentEntry?.let { vm.share(it) }
                            }
                        )
                        // 4. 复制链接：复制原图 URL 到剪贴板
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_copy_link)) },
                            leadingIcon = { Icon(Icons.Outlined.ContentCopy, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                currentEntry?.let { vm.copyOriginalUrl(it) }
                            }
                        )
                        // 5. 搜索图片：打开 Bing 版权搜索页
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_open_search)) },
                            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                currentEntry?.let { entry ->
                                    openExternalLink(context, entry.copyrightLink)
                                }
                            }
                        )
                    }
                }
            )
        }
    ) { padding ->
        if (list.isEmpty()) return@Scaffold
        Box(Modifier.padding(padding).fillMaxSize()) {
            // 左右滑动浏览不同日期的壁纸
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                val entry = list[page]
                // 按日期记住进度流，避免重组时重复触发下载
                val progressFlow = remember(entry.date) { vm.heroProgress(entry) }
                DetailPage(
                    entry = entry,
                    previewUrl = vm.previewUrl(entry),
                    uhdImage = vm.uhdImage(entry),
                    progressFlow = progressFlow,
                    onSet = { vm.setAsWallpaper(entry) },
                )
            }
            // 顶部进度条（设为壁纸任务进行中）
            if (ui.setting) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter)
                )
            }
        }
    }
}

/**
 * 单张详情页的内容。
 * 包含：可缩放的大图 + 下载进度遮罩 + 版权信息 + 日期 + 设为壁纸按钮。
 */
@Composable
private fun DetailPage(
    entry: WallpaperEntity,
    previewUrl: String,
    uhdImage: WallpaperImage?,
    progressFlow: kotlinx.coroutines.flow.StateFlow<com.youthlin.bingwallpaper.data.DownloadProgress>,
    onSet: () -> Unit,
) {
    val progress by progressFlow.collectAsStateWithLifecycle()
    val downloading = !progress.done && progress.percent < 100
    val imageModel: Any = uhdImage?.model ?: previewUrl
    val copyrightLines = remember(entry.copyright) { splitCopyright(entry.copyright) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 大图区域（16:9 宽高比）
        Box(Modifier.fillMaxWidth()) {
            ZoomableImage(
                model = imageModel,
                contentDescription = entry.title,
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)
            )
            // 下载进度遮罩（大图正在下载时显示）
            if (!progress.done && progress.percent < 100) {
                DownloadOverlay(
                    percent = progress.percent,
                    downloaded = progress.downloadedBytes,
                    total = progress.totalBytes,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        }

        // 版权信息
        Column(Modifier.padding(horizontal = 16.dp)) {
            Text(text = entry.title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = copyrightLines.description, style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp)
            )
            if (copyrightLines.credit.isNotBlank()) {
                Text(
                    text = copyrightLines.credit,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            Text(
                text = entry.date, style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        // 设为壁纸按钮（下载中时禁用）
        Button(
            onClick = onSet,
            enabled = !downloading,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
        ) {
            Icon(Icons.Outlined.Wallpaper, contentDescription = null)
            Text(
                text = "  " + stringResource(
                    if (downloading) R.string.hint_downloading else R.string.action_set_wallpaper
                ),
            )
        }
        Box(Modifier.fillMaxWidth().padding(bottom = 24.dp))
    }
}

/**
 * 可缩放的大图组件。
 * 双指缩放（1x~5x），单指拖拽平移。
 * 图片由 Coil 加载，自动缓存。
 */
@Composable
private fun ZoomableImage(model: Any, contentDescription: String?, modifier: Modifier = Modifier) {
    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }

    Box(
        modifier = modifier
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val newScale = (scale * zoom).coerceIn(1f, 5f)
                    scale = newScale
                    if (newScale > 1f) {
                        offsetX += pan.x
                        offsetY += pan.y
                    } else {
                        offsetX = 0f; offsetY = 0f
                    }
                }
            }
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current).data(model).crossfade(true).build(),
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale, scaleY = scale,
                    translationX = offsetX, translationY = offsetY
                )
        )
    }
}

/**
 * 下载进度遮罩（半透明黑色背景）。
 * 根据服务器是否返回 Content-Length 显示两种模式：
 * - 有 Content-Length：显示 "下载中 42%  1.2MB / 3.5MB" + 确定进度条
 * - 无 Content-Length：显示 "下载中…" + 滚动不确定进度条
 */
@Composable
private fun DownloadOverlay(
    percent: Int,
    downloaded: Long,
    total: Long,
    modifier: Modifier = Modifier
) {
    val ctx = LocalContext.current
    val indeterminate = percent < 0 || total <= 0
    val text = if (indeterminate) {
        ctx.getString(R.string.msg_download_indeterminate)
    } else {
        ctx.getString(
            R.string.msg_download_progress,
            percent, formatBytes(downloaded), formatBytes(total)
        )
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.5f))
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Text(text = text, color = Color.White, fontSize = 12.sp)
        if (indeterminate) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                color = Color.White,
                trackColor = Color.White.copy(alpha = 0.3f)
            )
        } else {
            LinearProgressIndicator(
                progress = { percent / 100f },
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                color = Color.White,
                trackColor = Color.White.copy(alpha = 0.3f)
            )
        }
    }
}

/** 格式化字节数：B → KB → MB */
private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return "%.0fKB".format(kb)
    val mb = kb / 1024.0
    return "%.1fMB".format(mb)
}

private data class CopyrightLines(
    val description: String,
    val credit: String
)

private fun splitCopyright(copyright: String): CopyrightLines {
    val left = listOf('(', '（').map { copyright.indexOf(it) }.filter { it >= 0 }.minOrNull()
    if (left == null) return CopyrightLines(description = copyright.trim(), credit = "")

    val right = copyright.indexOfAny(charArrayOf(')', '）'), startIndex = left + 1)
        .let { if (it >= 0) it else copyright.length }
    val description = copyright.substring(0, left).trim()
    val credit = copyright.substring(left + 1, right).trim()
    return CopyrightLines(description = description, credit = credit)
}

private fun openExternalLink(context: Context, url: String) {
    runCatching {
        val intent = Intent(Intent.ACTION_VIEW, url.toUri())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }.onFailure {
        Toast.makeText(context, context.getString(R.string.msg_open_link_failed), Toast.LENGTH_SHORT).show()
    }
}
