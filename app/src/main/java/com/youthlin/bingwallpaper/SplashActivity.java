package com.youthlin.bingwallpaper;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteCursorDriver;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQuery;
import android.net.ConnectivityManager;
import android.os.AsyncTask;
//import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.awt.font.TextAttribute;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);
        mStartTime = System.currentTimeMillis();//记录开始时间，
        mProgressBar = (ProgressBar) findViewById(R.id.progressBar);
        mTextView1 = (TextView) findViewById(R.id.textView);
        mTextView2 = (TextView) findViewById(R.id.textView2);

        new SplashAsyncTask().execute();
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
            SplashActivity.this.startActivity(new Intent(SplashActivity.this,
                    MainActivity.class));
            finish();
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
        }
    }

    //region //检测网络是否连接
    //@link http://melove.net/lzan13/develops/android-develop/android-wifi-state-348.html
    private boolean isNetworkConnected() {
        boolean result = false;
        //得到网络连接信息
        ConnectivityManager manager =
                (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
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
            Log.d(ConstValues.TAG, "创建目录:" + mkdirs + path.getAbsolutePath());
        }
        //打开数据库(不存在则创建)
        SQLiteDatabase db = SQLiteDatabase.openOrCreateDatabase(ConstValues.savePath
                + ConstValues.dbName, null);
        Log.d(ConstValues.TAG, db.getPath());
        //创建表(已有则不创建)
        db.execSQL("create table if not exists " + ConstValues.tableName
                + "(date primary key,urlbase,copyright,copyrightlink,filepath)");

        //判断网络
        if (isNetworkConnected()) {
            String jsonData = null;
            //region //下载json数据
            try {
                URL url = new URL(ConstValues.jsonUrl);
                InputStream is = url.openStream();
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                int length;
                byte buff[] = new byte[4096];
                while ((length = is.read(buff)) != -1) {
                    baos.write(buff, 0, length);
                }
                jsonData = new String(baos.toByteArray());
                is.close();
                baos.close();
            } catch (Exception e) {
                Log.d(ConstValues.TAG, "获取json数据失败");
            }//endregion

            //region //解析json数据
            if (jsonData != null) {
                try {
                    JSONObject json = new JSONObject(jsonData);
                    JSONArray images = json.getJSONArray("images");
                    Cursor c;
                    boolean hasresult;
                    String imageurl;
                    //region //遍历
                    for (int i = 0; i < images.length(); i++) {
                        a.publish(i, images.length());
                        json = images.getJSONObject(i);
                        String date = json.getString("enddate"),
                                urlbase = json.getString("urlbase"),
                                copyright = json.getString("copyright"),
                                copyrightlink = json.getString("copyrightlink");
                        File img = new File(ConstValues.savePath, date + ".jpg");

                        //region //图片不存在则下载图片
                        if (!img.exists()) {
                            imageurl = "http://www.bing.com/"
                                    + urlbase + "_1080x1920.jpg";
                            URL url = new URL(imageurl);
                            InputStream is = url.openStream();
                            OutputStream os = new FileOutputStream(img);
                            byte[] buf = new byte[4096];
                            int hasread;
                            while ((hasread = is.read(buf)) > 0) {
                                os.write(buf, 0, hasread);
                            }
                            is.close();
                            os.close();
                            Log.d(ConstValues.TAG, date + ".jpg已下载");
                        }
                        //endregion

                        //region //数据库中没有记录则插入
                        c = db.rawQuery("SELECT * FROM " + ConstValues.tableName + " WHERE date = ?",
                                new String[]{date});
                        hasresult = false;
                        while (c.moveToNext()) {
                            if (c.getString(c.getColumnIndex("date")) != null
                                    && c.getString(c.getColumnIndex("filepath")) != null) {
                                hasresult = true;
                            }
                        }
                        c.close();
                        if (!hasresult) {
                            //插入记录到数据库
                            db.execSQL("INSERT INTO " + ConstValues.tableName
                                            + "(date,urlbase,copyright,copyrightlink,filepath) "
                                            + " VALUES(?,?,?,?,?)",
                                    new Object[]{date, urlbase, copyright,
                                            copyrightlink, img.getAbsolutePath()}
                            );
                            Log.d(ConstValues.TAG, "已插入记录" + date);
                        }//endregion

                    }//endregion 遍历
                } catch (JSONException | IOException e) {
                    Log.d(ConstValues.TAG, "解析json出错");
                    db.close();
                    return ConstValues.FAILURE;
                }
                db.close();
                return ConstValues.SUCCESS;
            }//endregion
            db.close();
            return ConstValues.FAILURE;
        }
        db.close();
        return ConstValues.OFFLINE;
    }
}
