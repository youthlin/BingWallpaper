package com.youthlin.bingwallpaper.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.youthlin.bingwallpaper.data.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * AlarmManager 到点回调。真正的下载和设置仍交给 WorkManager，避免在广播里做重活。
 */
class DailyWallpaperReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val settings = SettingsStore(context).current()
                if (settings.autoDaily) {
                    WorkScheduler.runOnce(context, requireUnmetered = settings.onlyWifi)
                    WorkScheduler.apply(context, settings)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
