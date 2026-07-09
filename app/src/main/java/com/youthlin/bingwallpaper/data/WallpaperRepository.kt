package com.youthlin.bingwallpaper.data

import android.content.ContentUris
import android.app.WallpaperManager
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.DisplayMetrics
import android.view.WindowManager
import com.youthlin.bingwallpaper.data.db.AppDatabase
import com.youthlin.bingwallpaper.data.db.WallpaperEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/**
 * 数据仓库——整个应用的数据中心。
 * 负责：从 API 拉取壁纸元数据、下载图片文件、设置系统壁纸、保存到图库。
 *
 * 数据流向：
 * BingApi (JSON) → WallpaperRepository.refresh() → Room upsert → Flow 自动推送 → UI 自动刷新
 * 用户点击"设为壁纸" → ensureDownloaded() → apply() → WallpaperManager 设置
 */
class WallpaperRepository(
    private val context: Context,
    private val db: AppDatabase = AppDatabase.get(context),
    private val api: BingApi = BingApiFactory.instance
) {

    private val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    // ==================== 数据库操作 ====================

    /** 观察所有壁纸，按日期降序排列。Room 返回 Flow，数据变化时自动通知 UI。 */
    fun observeAll(): Flow<List<WallpaperEntity>> = db.wallpapers().observeAll()

    /** 获取最新一条壁纸 */
    suspend fun latest(): WallpaperEntity? = db.wallpapers().latest()

    /** 按日期查找某条壁纸 */
    suspend fun findByDate(date: String): WallpaperEntity? = db.wallpapers().findByDate(date)

    // ==================== API 刷新 ====================

    /**
     * 从 Bing API 拉取最近 N 天的壁纸元数据，存入数据库。
     * 返回最新的一条壁纸。
     */
    suspend fun refresh(count: Int = 8): WallpaperEntity {
        val market = SettingsStore(context).current().market
        val resp = api.archive(n = count.coerceIn(1, 8), mkt = market)
        val entities = resp.images.map { it.toEntity() }
        if (entities.isEmpty()) error("Bing returned an empty image list")
        db.wallpapers().insertIfAbsent(entities)
        return db.wallpapers().latest()!!
    }

    // ==================== 图片下载 ====================

    /**
     * 下载壁纸的 UHD 大图到应用私有目录，返回本地文件。
     * 如果文件已存在，直接返回缓存。
     * 下载路径：filesDir/wallpapers/{日期}_UHD.jpg
     */
    suspend fun ensureDownloaded(entry: WallpaperEntity): File = ensureHeroFile(entry)

    /** 返回已经存在的本地 UHD 文件，不触发网络下载。 */
    fun localUhdFile(entry: WallpaperEntity): File? {
        return existingFile(entry.filePath)
            ?: existingFile(variantFile(entry.date, "_UHD.jpg"))
            ?: existingFile(legacyDownloadedFile(entry.date))
    }

    // ==================== 设置壁纸 ====================

    /**
     * 把本地图片文件设为系统壁纸。
     * @param target 目标：主屏幕 / 锁屏 / 双屏
     */
    suspend fun apply(file: File, target: WallpaperTarget) {
        applyPreparedBitmap(file, target, centerCrop = true)
    }

    /** 为竖屏手机优先使用 Bing 提供的竖屏构图版本，失败时退回 UHD 居中裁剪。 */
    suspend fun applyBestFit(entry: WallpaperEntity, target: WallpaperTarget) {
        val screenSize = currentDisplaySize()
        val portrait = if (screenSize.second > screenSize.first) {
            runCatching { ensurePortraitWallpaperFile(entry) }.getOrNull()
        } else {
            null
        }
        val file = portrait ?: ensureDownloaded(entry)
        applyPreparedBitmap(file, target, centerCrop = portrait == null)
    }

    private suspend fun applyPreparedBitmap(file: File, target: WallpaperTarget, centerCrop: Boolean) {
        withContext(Dispatchers.IO) {
            val wm = WallpaperManager.getInstance(context)
            val source = BitmapFactory.decodeFile(file.absolutePath)
                ?: throw IOException("Failed to decode ${file.absolutePath}")
            val screenSize = currentDisplaySize()
            val bitmap = if (centerCrop) {
                centerCropToRatio(source, screenSize.first, screenSize.second)
            } else {
                source
            }
            try {
                val flag = when (target) {
                    WallpaperTarget.HOME -> WallpaperManager.FLAG_SYSTEM
                    WallpaperTarget.LOCK -> WallpaperManager.FLAG_LOCK
                    WallpaperTarget.BOTH -> WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK
                }
                wm.setBitmap(bitmap, /* visibleCropHint = */ null, /* allowBackup = */ true, flag)
            } finally {
                if (!bitmap.isRecycled) bitmap.recycle()
                if (bitmap !== source && !source.isRecycled) source.recycle()
            }
        }
    }

    private fun currentDisplaySize(): Pair<Int, Int> {
        val wm = context.getSystemService(WindowManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            wm?.currentWindowMetrics?.bounds?.let { bounds ->
                if (bounds.width() > 0 && bounds.height() > 0) {
                    return bounds.width() to bounds.height()
                }
            }
        }
        @Suppress("DEPRECATION")
        return DisplayMetrics().also { metrics ->
            wm?.defaultDisplay?.getRealMetrics(metrics)
        }.let { metrics ->
            if (metrics.widthPixels > 0 && metrics.heightPixels > 0) {
                metrics.widthPixels to metrics.heightPixels
            } else {
                val fallback = context.resources.displayMetrics
                fallback.widthPixels to fallback.heightPixels
            }
        }
    }

    private fun centerCropToRatio(source: Bitmap, targetWidth: Int, targetHeight: Int): Bitmap {
        if (targetWidth <= 0 || targetHeight <= 0) return source

        val sourceWidth = source.width
        val sourceHeight = source.height
        val targetAspect = targetWidth.toDouble() / targetHeight.toDouble()
        val sourceAspect = sourceWidth.toDouble() / sourceHeight.toDouble()

        val cropWidth: Int
        val cropHeight: Int
        if (sourceAspect > targetAspect) {
            cropHeight = sourceHeight
            cropWidth = (cropHeight * targetAspect).roundToInt().coerceIn(1, sourceWidth)
        } else {
            cropWidth = sourceWidth
            cropHeight = (cropWidth / targetAspect).roundToInt().coerceIn(1, sourceHeight)
        }

        if (cropWidth == sourceWidth && cropHeight == sourceHeight) return source

        val left = (sourceWidth - cropWidth) / 2
        val top = (sourceHeight - cropHeight) / 2
        return Bitmap.createBitmap(source, left, top, cropWidth, cropHeight)
    }

    /** 一步完成：刷新 API → 下载 → 设为壁纸（用于定时任务） */
    suspend fun refreshAndApply(target: WallpaperTarget): WallpaperEntity {
        val latest = refresh(count = 1)
        applyBestFit(latest, target)
        return latest
    }

    // ==================== 下载进度追踪 ====================

    /**
     * 下载进度共享池。每个 key（如 "20250706_UHD_jpg"）对应一个 StateFlow，
     * 多个观察者（列表页卡片 + 详情页）共享同一个进度，不会重复下载。
     */
    /** 按日期生成进度 key */
    fun uhdProgressKey(date: String): String = "${date}_UHD_jpg"

    /** 观察某个 key 的下载进度（只读，不触发下载） */
    fun progressFlow(key: String): StateFlow<DownloadProgress> =
        progressFlows.getOrPut(key) {
            MutableStateFlow(DownloadProgress(key = key, downloadedBytes = 0, totalBytes = 0, done = true))
        }.asStateFlow()

    /** 获取或创建可变进度流 */
    private fun mutableProgressFlow(key: String): MutableStateFlow<DownloadProgress> =
        progressFlows.getOrPut(key) {
            MutableStateFlow(DownloadProgress(key = key, downloadedBytes = 0, totalBytes = 0, done = true))
        }

    /**
     * 下载指定变体（如 "_UHD.jpg"）的图片，并实时更新下载进度。
     * 下载过程中通过 progressFlow 通知 UI 显示"下载中 42%"。
     * 如果文件已存在，直接返回缓存。
     */
    suspend fun ensureVariant(entry: WallpaperEntity, variant: String): File = withContext(Dispatchers.IO) {
        val key = uhdProgressKey(entry.date)
        val progress = mutableProgressFlow(key)
        wallpapersDir(create = true)
        if (variant == "_UHD.jpg") {
            localUhdFile(entry)?.let { cached ->
                progress.value = DownloadProgress(key, cached.length(), cached.length(), done = true)
                return@withContext cached
            }
        }
        val out = variantFile(entry.date, variant)
        existingFile(out)?.let { cached ->
            progress.value = DownloadProgress(key, cached.length(), cached.length(), done = true)
            return@withContext cached
        }
        downloadMutex.withLock {
            // 双重检查，防止并发重复下载
            if (variant == "_UHD.jpg") {
                localUhdFile(entry)?.let { cached ->
                    progress.value = DownloadProgress(key, cached.length(), cached.length(), done = true)
                    return@withLock cached
                }
            }
            existingFile(out)?.let { cached ->
                progress.value = DownloadProgress(key, cached.length(), cached.length(), done = true)
                return@withLock cached
            }
            val url = BingApiFactory.buildImageUrl(entry.urlBase, variant)
            val req = Request.Builder().url(url).build()
            progress.value = DownloadProgress(key, 0, -1, done = false)
            try {
                http.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        val msg = "HTTP ${resp.code}"
                        progress.value = DownloadProgress(key, 0, 0, done = true, error = msg)
                        throw IOException("$msg for $url")
                    }
                    val body = resp.body ?: throw IOException("Empty body for $url")
                    val total = body.contentLength()
                    progress.value = DownloadProgress(key, 0, total, done = false)
                    body.source().use { source ->
                        out.outputStream().use { sink ->
                            var downloaded = 0L
                            val buffer = ByteArray(8192)
                            while (true) {
                                val read = source.read(buffer)
                                if (read == -1) break
                                sink.write(buffer, 0, read)
                                downloaded += read
                                progress.value = DownloadProgress(key, downloaded, total, done = false)
                            }
                        }
                    }
                }
                progress.value = DownloadProgress(key, out.length(), out.length(), done = true)
                out
            } catch (e: Exception) {
                // 下载失败时也要标记 done = true，否则进度条会一直显示
                progress.value = DownloadProgress(key, 0, 0, done = true, error = e.message)
                throw e
            }
        }
    }

    /**
     * 下载 UHD 大图（供详情页预览和分享使用）。
     * 与 ensureVariant 不同，下载完成后会把本地路径存回数据库。
     */
    suspend fun ensureHeroFile(entry: WallpaperEntity): File = withContext(Dispatchers.IO) {
        val file = localUhdFile(entry) ?: ensureVariant(entry, "_UHD.jpg")
        // 下载成功后把路径写回数据库，后续直接读缓存
        if (entry.filePath != file.absolutePath) {
            db.wallpapers().upsert(entry.copy(filePath = file.absolutePath))
        }
        file
    }

    private suspend fun ensurePortraitWallpaperFile(entry: WallpaperEntity): File =
        ensureVariant(entry, "_768x1366.jpg")

    // ==================== 保存到图库 ====================

    /** 把下载好的图片保存到系统相册（Pictures/BingWallpaper/）。已存在则跳过。 */
    suspend fun saveToGallery(entry: WallpaperEntity, file: File): Boolean {
        return withContext(Dispatchers.IO) {
            val displayName = "${entry.date}_Bing_${entry.title.take(50)}.jpg"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+ 使用 MediaStore API
                val relativePath = Environment.DIRECTORY_PICTURES + "/BingWallpaper/"
                val resolver = context.contentResolver
                if (findExistingGalleryUri(displayName, relativePath) != null) {
                    return@withContext false
                }
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(MediaStore.Images.Media.RELATIVE_PATH, relativePath)
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    ?: throw IOException("Failed to insert into MediaStore")
                try {
                    resolver.openOutputStream(uri)?.use { out ->
                        file.inputStream().use { it.copyTo(out) }
                    }
                    values.clear()
                    values.put(MediaStore.Images.Media.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                    true
                } catch (e: Exception) {
                    resolver.delete(uri, null, null)
                    throw e
                }
            } else {
                // Android 9 及以下直接写文件
                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                val dest = File(dir, "BingWallpaper/$displayName")
                if (dest.exists() && dest.length() > 0) {
                    return@withContext false
                }
                dest.parentFile?.mkdirs()
                file.copyTo(dest, overwrite = true)
                true
            }
        }
    }

    private fun findExistingGalleryUri(displayName: String, relativePath: String): android.net.Uri? {
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(MediaStore.Images.Media._ID)
        val pathWithoutTrailingSlash = relativePath.trimEnd('/')
        val selection = "${MediaStore.Images.Media.DISPLAY_NAME} = ? AND " +
                "(${MediaStore.Images.Media.RELATIVE_PATH} = ? OR ${MediaStore.Images.Media.RELATIVE_PATH} = ?)"
        val args = arrayOf(displayName, relativePath, pathWithoutTrailingSlash)
        context.contentResolver.query(collection, projection, selection, args, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID))
                return ContentUris.withAppendedId(collection, id)
            }
        }
        return null
    }

    private fun wallpapersDir(create: Boolean = false): File {
        val dir = File(context.filesDir, "wallpapers")
        if (create) dir.mkdirs()
        return dir
    }

    private fun variantFile(date: String, variant: String): File =
        File(wallpapersDir(), "${date}${variant.replace('/', '_')}")

    private fun legacyDownloadedFile(date: String): File =
        File(wallpapersDir(), "$date.jpg")

    private fun existingFile(path: String?): File? =
        path?.let(::File)?.let(::existingFile)

    private fun existingFile(file: File): File? =
        file.takeIf { it.exists() && it.length() > 0 }

    /** 把 API 返回的 BingImage 转成数据库实体 */
    private fun BingImage.toEntity() = WallpaperEntity(
        date = endDate,
        urlBase = urlBase,
        title = title.ifBlank { copyright.substringBefore('(').trim() },
        copyright = copyright,
        copyrightLink = copyrightLink,
        filePath = null
    )

    private companion object {
        private val progressFlows = ConcurrentHashMap<String, MutableStateFlow<DownloadProgress>>()
        private val downloadMutex = Mutex()
    }
}
