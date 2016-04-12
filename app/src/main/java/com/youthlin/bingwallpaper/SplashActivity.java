package com.youthlin.bingwallpaper;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;

/**
 * @link http://blog.csdn.net/lincyang/article/details/42673151
 * AppCompatActivity是android.support.v7.app.AppCompatActivity，
 * 因此他需要在AndroidManifest.xml里设置android:theme为Theme.AppCompat
 * 否则报错
 * java.lang.IllegalStateException: You need to use a Theme.AppCompat theme (or...
 * 但这样就不能全屏了。因此改为继承Activity。
 * <br>
 * Android笔记之:App应用之启动界面SplashActivity的使用
 * @link http://www.jb51.net/article/36190.htm
 */
public class SplashActivity extends Activity {

    private long mStartTime;// 开始时间
    private ProgressBar mProgressBar;
    private TextView mTextView1, mTextView2;
    private int PERMISSION_REQUEST = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);
        mStartTime = System.currentTimeMillis();//记录开始时间，
        mProgressBar = (ProgressBar) findViewById(R.id.progressBar);
        mTextView1 = (TextView) findViewById(R.id.textView);
        mTextView2 = (TextView) findViewById(R.id.textView2);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (Build.VERSION.SDK_INT >= 23) {
            String[] permissions = new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE};
            if (ActivityCompat.checkSelfPermission(this, permissions[0])
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, permissions, PERMISSION_REQUEST);
            }
        }
        new SplashAsyncTask().execute();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode == PERMISSION_REQUEST) {
            if (grantResults.length > 0) {
                boolean storageSuccess = false;
                for (int i = 0; i < permissions.length; i++) {
                    if (permissions[i].equals(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                        if (grantResults.length > i) {
                            if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                                storageSuccess = true;
                            }
                        }
                    }
                }
                if (!storageSuccess) {
                    Toast.makeText(this, R.string.write_external_storage_tip,
                            Toast.LENGTH_LONG).show();
                }
            }
        }
    }

    private class SplashAsyncTask extends AsyncTask<Void, Integer, Integer> {
        public void publish(Integer... values) {
            super.publishProgress(values);
        }

        @Override
        protected Integer doInBackground(Void... params) {
            int result = init(this);

            long loadingTime = System.currentTimeMillis() - mStartTime;
            Log.d(ConstValues.TAG, "用时:" + loadingTime);
            if (loadingTime < ConstValues.SHOW_TIME_MIN) {
                try {
                    Thread.sleep(ConstValues.SHOW_TIME_MIN - loadingTime);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            return result;
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            mProgressBar.setProgress(100 * values[0] / values[1]);
            mTextView1.setText(String.format(getResources().getString(R.string.percent),
                    100 * values[0] / values[1]));
            mTextView2.setText(String.format(getResources().getString(R.string.percent_detail),
                    values[0], values[1]));
            //Log.d(TAG, "进度:" + values[0] + "/" + values[1]);
        }

        @Override
        protected void onPostExecute(Integer result) {
            switch (result) {
                case ConstValues.OFFLINE:
                    Log.d(ConstValues.TAG, "离线启动");
                    mProgressBar.setProgress(0);
                    Toast.makeText(getApplication(), R.string.network_not_available,
                            Toast.LENGTH_SHORT).show();
                    break;
                case ConstValues.FAILURE:
                    Log.d(ConstValues.TAG, "非正常启动(解析数据异常)");
                    Toast.makeText(getApplication(), R.string.error_parsing_data,
                            Toast.LENGTH_SHORT).show();
                    break;
                case ConstValues.SUCCESS:
                    mProgressBar.setProgress(100);
                    mTextView1.setText(String.format(getResources().getString(R.string.percent),
                            100));
                    Log.d(ConstValues.TAG, "正常启动");
                    break;
            }
            //region检查是否开启了自动设置壁纸
            //求大神解答关于PreferenceActivity数据读取问题！ [问题点数：40分，结帖人cndnis]
            //http://bbs.csdn.net/topics/390242793
            SharedPreferences shp = PreferenceManager.getDefaultSharedPreferences(getApplication());
            boolean autoSetWallpaper = shp.getBoolean(ConstValues.key_auto_set_wallpaper, true);
            SettingsActivity.autoSetWallpaper(getApplication(), autoSetWallpaper);
            //endregion
            SplashActivity.this.startActivity(new Intent(SplashActivity.this,
                    MainActivity.class));
            finish();
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
        }
    }

    //region //检测网络是否连接
    //@link http://melove.net/lzan13/develops/android-develop/android-wifi-state-348.html
    public static boolean isNetworkConnected(Context context) {
        boolean result = false;
        //得到网络连接信息
        ConnectivityManager manager =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (manager.getActiveNetworkInfo() != null) {
            result = manager.getActiveNetworkInfo().isAvailable();
        }
        return result;
    }//endregion

    private int init(SplashAsyncTask a) {
        //检查目录是否被删
        File path = new File(ConstValues.savePath);
        if (!path.exists()) {
            boolean mkdirs = path.mkdirs();
            Log.d(ConstValues.TAG, "创建目录成功吗:" + mkdirs + ";path=" + path.getAbsolutePath());
        }
        //判断网络
        if (isNetworkConnected(this)) {
            String jsonData = ImageEntry.getJson();
            //region //解析json数据
            if (jsonData != null) {
                try {
                    JSONObject json = new JSONObject(jsonData);
                    JSONArray images = json.getJSONArray("images");
                    //region //遍历
                    for (int i = images.length() - 1; i >= 0; i--) {
                        a.publish(images.length() - i - 1, images.length());
                        json = images.getJSONObject(i);
                        String date = json.getString("enddate"),
                                urlbase = json.getString("urlbase"),
                                copyright = json.getString("copyright"),
                                copyrightlink = json.getString("copyrightlink");
                        File img = new File(ConstValues.savePath, date + ".jpg");
                        //图片不存在则下载图片
                        if (!img.exists()) {
                            ImageEntry.downImg(getApplication(), urlbase, img);
                        }
                        //数据库中没有记录则插入
                        ImageEntry.insertIfNotExists(getApplicationContext(),
                                new ImageEntry(date, urlbase, copyright, copyrightlink,
                                        img.getAbsolutePath()));
                    }//endregion 遍历
                } catch (JSONException e) {
                    e.printStackTrace();
                    Log.d(ConstValues.TAG, "解析json出错");
                    return ConstValues.FAILURE;
                }
                return ConstValues.SUCCESS;
            }//endregion
            return ConstValues.FAILURE;
        }
        return ConstValues.OFFLINE;
    }
}
