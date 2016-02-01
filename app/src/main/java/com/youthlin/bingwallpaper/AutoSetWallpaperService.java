package com.youthlin.bingwallpaper;

import android.app.Service;
import android.app.WallpaperManager;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class AutoSetWallpaperService extends Service {
    WallpaperManager wManager;

    @Override
    public void onCreate() {
        super.onCreate();
        wManager = WallpaperManager.getInstance(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d("info", "onStartCommand服务执行");
        final Handler handler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case 0:
                        Toast.makeText(getApplicationContext(), R.string.set_wallpaper_success, Toast.LENGTH_SHORT).show();
                        break;
                    case 1:
                        Toast.makeText(getApplicationContext(), R.string.down_img_error, Toast.LENGTH_SHORT).show();
                        break;
                }
            }
        };
        new Thread(new Runnable() {
            @Override
            public void run() {
                String sDate = new SimpleDateFormat("yyyyMMdd", Locale.US).format(new Date());
                File img = new File(MainActivity.savePath, sDate + ".jpg");
                if (!img.exists()) {
                    if (MainActivity.jsonData == null) {
                        MainActivity.jsonData = getJson();
                    }
                    JSONObject json;
                    try {
                        json = new JSONObject(MainActivity.jsonData);
                        JSONArray images = json.getJSONArray("images");
                        json = images.getJSONObject(0);
                        URL url = new URL("http://www.bing.com/"
                                + json.getString("urlbase") + "_1080x1920.jpg");
                        InputStream is = url.openStream();
                        OutputStream os = new FileOutputStream(img);
                        byte[] buf = new byte[4096];
                        int hasread;
                        while ((hasread = is.read(buf)) > 0) {
                            os.write(buf, 0, hasread);
                        }
                        is.close();
                        os.close();
                    } catch (JSONException | IOException e) {
                        e.printStackTrace();
                        handler.sendEmptyMessage(1);
                    }
                }
                Bitmap bitmap = BitmapFactory.decodeFile(img.getAbsolutePath());
                if (bitmap != null)
                    try {
                        wManager.setBitmap(bitmap);
                        handler.sendEmptyMessage(0);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                else
                    handler.sendEmptyMessage(1);
                if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
            }
        }).start();

        return START_STICKY;
    }

    public String getJson() {
        String result = null;
        try {
            URL url = new URL(MainActivity.jsonurl);
            InputStream is = url.openStream();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            int length;
            byte buff[] = new byte[4096];
            while ((length = is.read(buff)) != -1) {
                baos.write(buff, 0, length);
            }
            result = new String(baos.toByteArray());
            is.close();
            baos.close();
            return result;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return result;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}