package com.youthlin.bingwallpaper.work

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.youthlin.bingwallpaper.data.SettingsStore
import com.youthlin.bingwallpaper.data.WallpaperRepository

/**
 * 设置壁纸的后台任务（由 WorkManager 调度）。
 * 两种模式：
 * 1. 有 KEY_DATE 输入 → 下载并设置指定日期的壁纸（用户点击"设为壁纸"）
 * 2. 无 KEY_DATE 输入 → 刷新 API 获取最新壁纸并设置（定时任务 / 立即执行）
 */
class SetWallpaperWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val settings = SettingsStore(applicationContext).current()
        val repo = WallpaperRepository(applicationContext)
        val date = inputData.getString(KEY_DATE)
        val requireWifi = inputData.getBoolean(KEY_REQUIRE_WIFI, settings.onlyWifi)
        if (requireWifi && !isWifiOrEthernetConnected()) {
            Log.i(TAG, "Wi-Fi only is enabled, waiting for Wi-Fi or Ethernet")
            return Result.retry()
        }
        return try {
            val savedNew = if (date != null) {
                // 模式 1：设置指定日期的壁纸
                val entry = repo.findByDate(date) ?: return Result.failure()
                val file = repo.ensureDownloaded(entry)
                repo.applyBestFit(entry, settings.target)
                Log.i(TAG, "Applied wallpaper for ${entry.date}: ${entry.title}")
                if (settings.saveToGallery) {
                    try { repo.saveToGallery(entry, file) } catch (_: Exception) { false }
                } else {
                    false
                }
            } else {
                // 模式 2：从 API 获取最新壁纸并设置
                val entry = repo.refresh(count = 1)
                val file = repo.ensureDownloaded(entry)
                repo.applyBestFit(entry, settings.target)
                Log.i(TAG, "Applied wallpaper for ${entry.date}: ${entry.title}")
                if (settings.saveToGallery) {
                    try { repo.saveToGallery(entry, file) } catch (_: Exception) { false }
                } else {
                    false
                }
            }
            Result.success(workDataOf(KEY_SAVED_NEW to savedNew))
        } catch (t: Throwable) {
            Log.w(TAG, "Wallpaper worker failed", t)
            if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
        }
    }

    companion object {
        const val KEY_DATE = "date"
        const val KEY_SAVED_NEW = "saved_new"
        const val KEY_REQUIRE_WIFI = "require_wifi"
        private const val TAG = "SetWallpaperWorker"
        private const val MAX_ATTEMPTS = 5 // 最多重试 5 次
    }

    private fun isWifiOrEthernetConnected(): Boolean {
        val cm = applicationContext.getSystemService(ConnectivityManager::class.java) ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                        caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))
    }
}
