package com.youthlin.bingwallpaper.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.youthlin.bingwallpaper.data.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** 设备重启后 AlarmManager 的闹钟会丢失，这里按当前设置补一次任务并恢复下一次每日触发。 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            val pending = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val settings = SettingsStore(context).current()
                    if (settings.autoDaily) {
                        WorkScheduler.runOnce(context, requireUnmetered = settings.onlyWifi)
                    }
                    WorkScheduler.apply(context, settings)
                } finally {
                    pending.finish()
                }
            }
        }
    }
}
