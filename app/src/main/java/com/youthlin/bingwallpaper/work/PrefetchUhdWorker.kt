package com.youthlin.bingwallpaper.work

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.youthlin.bingwallpaper.data.SettingsStore
import com.youthlin.bingwallpaper.data.WallpaperRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Wi-Fi 下静默预下载大图的后台任务。
 * 当用户开启"Wi-Fi 环境自动下载大图"时触发。
 * 遍历所有已知壁纸，下载 UHD 大图，如果开启了"保存到图库"则一并归档。
 * 单张失败不影响其他图片的下载。
 */
class PrefetchUhdWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val settings = SettingsStore(applicationContext).current()
        if (!settings.prefetchOnWifi) return@withContext Result.success()

        val repo = WallpaperRepository(applicationContext)
        val entries = repo.observeAll().first().sortedByDescending { it.date }
        for (e in entries) {
            try {
                val f = repo.ensureVariant(e, "_UHD.jpg")
                if (settings.saveToGallery) repo.saveToGallery(e, f)
            } catch (t: Throwable) {
                // 单张图片下载失败不中断整批
                Log.w(TAG, "Prefetch failed for ${e.date}", t)
            }
        }
        Result.success()
    }

    private companion object {
        const val TAG = "PrefetchUhdWorker"
    }
}