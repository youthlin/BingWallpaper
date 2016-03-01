package com.youthlin.bingwallpaper;

import android.app.IntentService;
import android.app.WallpaperManager;
import android.content.Intent;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;

public class SetWallpaperIntentService extends IntentService {
    public static final String ACTION_SET_NEWEST_WALLPAPER
            = "com.youthlin.bingwallpaper.action.SET_NEWEST_WALLPAPER";

    public SetWallpaperIntentService() {
        super("SetWallpaperIntentService");
    }

    public static void startActionSetNewestWallpaper(Context context) {
        Intent intent = new Intent(context, SetWallpaperIntentService.class);
        intent.setAction(ACTION_SET_NEWEST_WALLPAPER);
//        intent.putExtra(EXTRA_PARAM1, param1);
        context.startService(intent);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent != null) {
            final String action = intent.getAction();
            if (ACTION_SET_NEWEST_WALLPAPER.equals(action)) {
//                final String param1 = intent.getStringExtra(EXTRA_PARAM1);
                handleActionSetNewestWallpaper();
            } else {
                Log.d(ConstValues.TAG, "未知action");
            }
        }
    }

    private void handleActionSetNewestWallpaper() {
        Log.d(ConstValues.TAG, "调用了设置壁纸服务");
        //region 如有网络则下载最新图片
        if (SplashActivity.isNetworkConnected(this)) {
            String jsonData = ImageEntry.getJson();
            if (jsonData != null) {
                try {
                    JSONObject json = new JSONObject(jsonData);
                    JSONArray images = json.getJSONArray("images");
                    json = images.getJSONObject(0);
                    String filename = ConstValues.savePath + json.getString("enddate") + ".jpg";
                    ImageEntry entry = new ImageEntry(json.getString("enddate"),
                            json.getString("urlbase"), json.getString("copyright"),
                            json.getString("copyrightlink"), filename);
                    File img = new File(filename);
                    if (!img.exists()) {
                        ImageEntry.downImg(this, entry.mUrlBase, img);
                    }
                    ImageEntry.insertIfNotExists(this, entry);
                } catch (JSONException e) {
                    e.printStackTrace();
                    Log.d(ConstValues.TAG, "解析json出错");
                }
            } else {
                Log.d(ConstValues.TAG, "获取json出错");
            }
        }
        //endregion
        //region 从数据库中获取最新图片并设为壁纸
        SQLiteDatabase db = ImageEntry.openOrCreateDatabase(this);
        Cursor c;
        c = db.rawQuery("SELECT * FROM " + ConstValues.tableName + " ORDER BY date DESC",
                new String[]{});
        if (c.moveToFirst()) {
            String filePath = c.getString(c.getColumnIndex("filepath"));
            WallpaperManager wm = WallpaperManager.getInstance(this);
            Bitmap bitmap = BitmapFactory.decodeFile(filePath);
            if (bitmap == null) {
                Log.d(ConstValues.TAG, "bitmap==null!!!解析图片出错");
                return;
            }
            try {
                wm.setBitmap(bitmap);
                Log.d(ConstValues.TAG, "设置壁纸成功");
            } catch (IOException e) {
                e.printStackTrace();
            }
            if (!bitmap.isRecycled()) bitmap.recycle();
        } else {
            Log.d(ConstValues.TAG, "数据库中无记录");
        }
        c.close();
        db.close();//endregion
    }
}
