package com.youthlin.bingwallpaper.work

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.youthlin.bingwallpaper.data.SettingsStore
import com.youthlin.bingwallpaper.data.UserSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * WorkManager 调度入口，所有后台任务都从这里排入。
 *
 * 三种任务：
 * 1. apply() — 每日定时更换壁纸（AlarmManager 到点触发，再交给 WorkManager 执行）
 * 2. runOnce() — 一次性执行（手动点击"立即执行"或"设为壁纸"）
 * 3. enqueuePrefetch() — Wi-Fi 下静默预下载大图
 */
object WorkScheduler {

    private const val ONE_SHOT_NAME = "bing_wallpaper_now"
    private const val DAILY_ALARM_REQUEST = 100

    /** 从设置读取当前偏好并（重新）调度定时任务。应用启动时调用。 */
    fun applyFromSettings(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            val store = SettingsStore(context)
            apply(context, store.current())
        }
    }

    /**
     * 根据用户设置调度/取消每日定时更换任务。
     * 如果用户关闭了自动更换，取消已有定时任务。
     */
    fun apply(context: Context, settings: UserSettings) {
        val alarm = context.getSystemService(AlarmManager::class.java) ?: return
        val pending = dailyAlarmIntent(context)
        if (!settings.autoDaily) {
            alarm.cancel(pending)
            return
        }

        val triggerAt = nextTriggerAtMillis(settings.hour, settings.minute)
        alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
    }

    /** Wi-Fi 下静默预下载所有已知壁纸的大图 */
    fun enqueuePrefetch(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.UNMETERED)
            .build()
        val req = OneTimeWorkRequestBuilder<PrefetchUhdWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork("prefetch_uhd", ExistingWorkPolicy.KEEP, req)
    }

    /**
     * 一次性执行设置壁纸。
     * @param date 可选，指定要设置的壁纸日期。为 null 时使用 API 最新壁纸。
     */
    fun runOnce(context: Context, requireUnmetered: Boolean = false, date: String? = null) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(if (requireUnmetered) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .build()
        val builder = OneTimeWorkRequestBuilder<SetWallpaperWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
        if (date != null) {
            builder.setInputData(workDataOf(SetWallpaperWorker.KEY_DATE to date))
        }
        val req = builder.build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(ONE_SHOT_NAME, ExistingWorkPolicy.REPLACE, req)
    }

    /**
     * 计算从「现在」到「下次 hh:mm」的毫秒延迟。
     * 如果今天的目标时间已过，则延迟到明天。
     */
    private fun computeInitialDelayMillis(hour: Int, minute: Int): Long {
        return nextTriggerAtMillis(hour, minute) - System.currentTimeMillis()
    }

    private fun nextTriggerAtMillis(hour: Int, minute: Int): Long {
        val now = Calendar.getInstance()
        val target = (now.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (!after(now)) add(Calendar.DAY_OF_YEAR, 1)
        }
        return target.timeInMillis
    }

    private fun dailyAlarmIntent(context: Context): PendingIntent {
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        return PendingIntent.getBroadcast(
            context,
            DAILY_ALARM_REQUEST,
            Intent(context, DailyWallpaperReceiver::class.java),
            flags
        )
    }
}
