package com.youthlin.bingwallpaper

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.util.Log
import androidx.work.Configuration
import com.youthlin.bingwallpaper.data.SettingsStore
import com.youthlin.bingwallpaper.work.WorkScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 整个应用的入口（在 AndroidManifest.xml 中声明）。
 * 应用冷启动时最先执行这里：
 * 1. 创建通知渠道（用于壁纸设置进度的通知）
 * 2. 恢复 WorkManager 定时任务（如果用户开启了每日自动更换）
 * 3. 如果用户开启了 Wi-Fi 预取，立刻排一个后台任务去下载大图
 */
class BingApp : Application(), Configuration.Provider {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        // 每次冷启动恢复定时任务。WorkManager 本身跨重启持久化，不需要监听开机广播。
        WorkScheduler.applyFromSettings(this)
        // 如果用户开启了 Wi-Fi 预取，立即排一个后台任务（不阻塞主流程）
        CoroutineScope(Dispatchers.IO).launch {
            if (SettingsStore(this@BingApp).current().prefetchOnWifi) {
                WorkScheduler.enqueuePrefetch(this@BingApp)
            }
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(if (BuildConfig.DEBUG) Log.DEBUG else Log.INFO)
            .build()

    private fun createNotificationChannel() {
        val nm = getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            NOTIF_CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notification_channel_desc)
            setShowBadge(false)
        }
        nm.createNotificationChannel(channel)
    }

    companion object {
        const val NOTIF_CHANNEL_ID = "wallpaper_worker"
        const val NOTIF_ID_WORKING = 1001
    }
}