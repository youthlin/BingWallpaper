package com.youthlin.bingwallpaper;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.WallpaperManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.widget.Toast;

import java.io.IOException;
import java.lang.ref.WeakReference;

/**
 * Created by lin on 2016-02-29-029.
 */
public class SetWallpaper extends Thread {
    public static class MyHandler extends Handler {
        WeakReference<AppCompatActivity> mActivity;

        MyHandler(AppCompatActivity activity) {
            mActivity = new WeakReference<>(activity);
        }

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            AppCompatActivity activity = mActivity.get();
            switch (msg.what) {
                case ConstValues.SETTING_WALLPAPER:
                    Toast.makeText(activity, R.string.setting_wallpaper, Toast.LENGTH_SHORT).show();
                    break;
                case ConstValues.SET_WALLPAPER_SUCCESS:
                    Toast.makeText(activity, R.string.set_wallpaper_succ, Toast.LENGTH_SHORT).show();
                    /*NotificationManager manager = (NotificationManager)
                            activity.getSystemService(Context.NOTIFICATION_SERVICE);
                    manager.cancel(0);
                    Notification notification = new Notification.Builder(activity)
                            .setSmallIcon(R.mipmap.ic_launcher)
                            .setTicker(activity.getResources()
                                    .getString(R.string.set_wallpaper_succ))
                            .setContentTitle(activity.getResources()
                                    .getString(R.string.set_wallpaper_succ))
                            .setAutoCancel(true).getNotification();
                    manager.notify(0, notification);*/
                    break;
                case ConstValues.FILE_NOT_FOUND:
                    Toast.makeText(activity, R.string.set_wallpaper_file_not_found, Toast.LENGTH_SHORT).show();
                    break;
                case ConstValues.IO_EXCEPTION:
                    Toast.makeText(activity, R.string.set_wallpaper_io_exception, Toast.LENGTH_SHORT).show();
                    break;
            }
        }
    }

    Context ctx;
    String filename;
    MyHandler handler;

    public SetWallpaper(Context ctx, AppCompatActivity activity, String filename) {
        this.ctx = ctx;
        this.filename = filename;
        handler = new MyHandler(activity);
    }

    @Override
    public void run() {
        handler.sendEmptyMessage(ConstValues.SETTING_WALLPAPER);
        WallpaperManager wm = WallpaperManager.getInstance(ctx);
        Bitmap bitmap = BitmapFactory.decodeFile(filename);
        if (bitmap == null) {
            handler.sendEmptyMessage(ConstValues.FILE_NOT_FOUND);
            return;
        }
        try {
            wm.setBitmap(bitmap);
            handler.sendEmptyMessage(ConstValues.SET_WALLPAPER_SUCCESS);
        } catch (IOException e) {
            e.printStackTrace();
            handler.sendEmptyMessage(ConstValues.IO_EXCEPTION);
        }
        if (!bitmap.isRecycled()) bitmap.recycle();
    }
}
