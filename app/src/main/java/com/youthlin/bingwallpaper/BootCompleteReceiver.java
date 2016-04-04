package com.youthlin.bingwallpaper;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

public class BootCompleteReceiver extends BroadcastReceiver {
    public BootCompleteReceiver() {
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        SharedPreferences shp = PreferenceManager.getDefaultSharedPreferences(context);
        boolean autoSetWallpaper = shp.getBoolean(ConstValues.key_auto_set_wallpaper, true);
        if (autoSetWallpaper) {
            Intent i = new Intent(context, SetWallpaperIntentService.class);
            i.setAction(SetWallpaperIntentService.ACTION_SET_NEWEST_WALLPAPER);
            i.putExtra(SetWallpaperIntentService.EXTRA_PARAM_BOOT, "boot");
            context.startService(i);
            Log.d(ConstValues.TAG, "开机自启已打开");
        } else {
            Log.d(ConstValues.TAG, "开机自启已关闭");
        }
    }
}
