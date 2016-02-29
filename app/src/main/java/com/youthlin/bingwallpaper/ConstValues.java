package com.youthlin.bingwallpaper;

import android.os.Environment;

/**
 * Created by lin on 2016-02-26-026.
 */
public class ConstValues {
    public static final String savePath = Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_PICTURES).getAbsolutePath() + "/BingWallpaper/";
    public static final String dbName = "img.db";
    public static final String tableName = "ImageInfo";
    public static final String jsonUrl = "http://cn.bing.com/HPImageArchive.aspx?format=js&idx=15&n=15";
    public static final String TAG = "MyTag";
    public static final int FAILURE = 0; // 失败
    public static final int SUCCESS = 1; // 成功
    public static final int OFFLINE = 2; // 如果支持离线阅读，进入离线模式
    public static final int SHOW_TIME_MIN = 800;// 最小显示时间(毫秒)
    public static final int picWidth = 1080, picHeight = 1920;
    public static final int SET_WALLPAPER_SUCCESS = 0x0;
    public static final int IO_EXCEPTION = 0x1;
    public static final int FILE_NOT_FOUND = 0x2;

}
