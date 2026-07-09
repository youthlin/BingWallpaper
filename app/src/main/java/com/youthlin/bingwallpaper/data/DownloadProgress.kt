package com.youthlin.bingwallpaper.data

/**
 * 单张图片的下载进度，按文件名作为 key（如 "20250706_UHD_jpg"）。
 *
 * 每个 key 对应一个共享的 StateFlow，存放在 WallpaperRepository.progressFlows 里。
 * 这样多个观察者（列表页卡片 + 详情页大图）看到的是同一个下载进度，不会重复下载。
 *
 * 初始状态：done = true（表示没有正在下载，UI 不显示进度条）。
 * 真正开始下载时由 ensureVariant() 将 done 设为 false。
 */
data class DownloadProgress(
    val key: String,
    val downloadedBytes: Long,
    val totalBytes: Long,
    val done: Boolean,
    val error: String? = null
) {
    /**
     * 下载百分比：
     * - 100：已完成（done = true）
     * - 0~100：下载中，且服务器返回了 Content-Length（确定进度条）
     * - -1：下载中，但服务器没返回 Content-Length（不确定进度条，显示滚动动画）
     * - 0：刚启动，还没收到字节
     */
    val percent: Int
        get() = when {
            done -> 100
            totalBytes > 0 -> (downloadedBytes * 100 / totalBytes).toInt().coerceIn(0, 100)
            totalBytes < 0 -> -1
            else -> 0
        }
}