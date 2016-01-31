package com.youthlin.bingwallpaper;

import android.app.Activity;
import android.app.WallpaperManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.ConnectivityManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewFlipper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {
    private ProgressBar progressBar;
    private ViewFlipper viewFlipper;
    private TextView textView;
    private String jsonData;
    private float startX;
    private String savePath;
    int screenWidth, screenHeight;
    final static int imgcount = 8;
    final static String jsonurl = "http://www.bing.com/HPImageArchive.aspx?format=js&n=" + imgcount;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        progressBar = (ProgressBar) findViewById(R.id.progressBar);
        viewFlipper = (ViewFlipper) findViewById(R.id.flipper);
        textView = (TextView) findViewById(R.id.textView);
        textView.setAlpha(0.7f);

        DisplayMetrics dm = getResources().getDisplayMetrics();
        screenWidth = dm.widthPixels;
        screenHeight = dm.heightPixels;
        savePath = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES).getAbsolutePath() + "/BingWallpaper/";

        File path = new File(savePath);
        if (!path.exists()) {
            path.mkdirs();
            Toast.makeText(this, R.string.mkdir, Toast.LENGTH_SHORT).show();
        }
        getJson();
        showImage();
        showInfo();
    }

    public void showImage() {
//        Calendar c = Calendar.getInstance();
//        c.add(Calendar.DATE, -imgcount + 1);//沃德天，记得加一……难怪图片一直不存在泪奔::>_<::
//        Date date = c.getTime();
        Date date = new Date();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd", Locale.US);
        String sDate = sdf.format(date);
        Log.d("info", sDate);
        File img = new File(savePath, sDate + ".jpg");

        //判断本地是否已有图片
        if (!img.exists()) {
            progressBar.setVisibility(View.VISIBLE);
            Log.d("info", img.getAbsolutePath() + "不存在,即将下载");
            Toast.makeText(this, "本地图片不存在,即将下载", Toast.LENGTH_SHORT).show();
            downImg();
        } else {
            loadImg();
        }
    }

    public void getJson() {
        final MyHandler handler = new MyHandler(this);
        new Thread() {
            @Override
            public void run() {
                String result = null;
                Message msg = new Message();
                try {
                    URL url = new URL(jsonurl);
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
                    msg.what = 0x12;//下载json
                } catch (Exception e) {
                    e.printStackTrace();
                    msg.what = 0x1;//下载json失败
                }
                Bundle data = new Bundle();
                data.putString("json", result);
                msg.setData(data);
                handler.sendMessage(msg);
            }
        }.start();
    }

    public void downImg() {
        final MyHandler handler = new MyHandler(this);
        new Thread() {
            public void run() {
                try {
                    URL url = new URL(jsonurl);
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
                    handler.sendEmptyMessage(0x1);
                }
                if (jsonData != null) {
                    try {
                        JSONObject json = new JSONObject(jsonData);
                        JSONArray images = json.getJSONArray("images");

                        for (int i = 0; i < images.length(); i++) {
                            json = images.getJSONObject(i);
                            String date = json.getString("enddate");
                            String imageurl = json.getString("url").replace("1920x1080", "1080x1920");
                            //String copyright = json.getString("copyright");
                            URL url = new URL(imageurl);
                            InputStream is = url.openStream();
                            OutputStream os = new FileOutputStream(new File(savePath + date + ".jpg"));
                            byte[] buf = new byte[4096];
                            int hasread;
                            while ((hasread = is.read(buf)) > 0) {
                                os.write(buf, 0, hasread);
                            }
                            is.close();
                            os.close();
                            Log.d("info", date + ".jpg已下载");
                            //region //Android Handler Message总结
                            //http://blog.csdn.net/caesardadi/article/details/8473777
                            //下面这样取出来时竟然是null
                            //Bundle data = new Bundle();
                            //data.putString("filename", date + ".jpg");//region
                            Message msg = Message.obtain(handler, 0x2, date);//图片已下载
                            handler.sendMessage(msg);
                        }
                        //下载完成
                        handler.sendEmptyMessage(0x123);
                    } catch (JSONException | IOException e) {
                        //region //当catch块内的内容相同时，可以写成上述形式
                        //http://stackoverflow.com/questions/25528215/
                        //catch-branch-is-identical-however-still-requires-me-to-catch-it
                        //要不然IDEA太智能会提示: （233）
                        //Reports identical catch sections in try blocks under JDK 7.
                        //A quickfix is available to collapse the sections into
                        //a multi-catch section.//endregion
                        e.printStackTrace();
                    }
                }
            }
        }.start();
    }

    public void loadImg() {
        for (int i = 0; i < imgcount; i++) {
            Calendar c = Calendar.getInstance();
            c.add(Calendar.DATE, -i);
            Date date = c.getTime();
            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd", Locale.US);
            String sDate = sdf.format(date);
            File img = new File(savePath + sDate + ".jpg");
            Log.d("info", img.getName());
            Bitmap bitmap;
            if (img.exists()) {
                ImageView iv = new ImageView(this);
                bitmap = createImageThumbnail(img.getAbsolutePath(), screenWidth, screenWidth * screenHeight);
                iv.setImageBitmap(bitmap);
                iv.setId(Integer.parseInt(sDate));//设置ID，文件名
                iv.setScaleType(ImageView.ScaleType.FIT_XY);//充满父容器
                viewFlipper.addView(iv);
                progressBar.setVisibility(View.INVISIBLE);
            }
        }
        showInfo();
    }

    public void showInfo() {
        ImageView iv = (ImageView) viewFlipper.getCurrentView();
        if (iv == null) {
            textView.setText(R.string.pic_not_available);
            return;
        }
        String date = "" + iv.getId();
        if (date.length() > 0) {
            if (jsonData != null) {
                try {
                    JSONObject json = new JSONObject(jsonData);
                    JSONArray images = json.getJSONArray("images");
                    for (int i = 0; i < images.length(); i++) {
                        json = images.getJSONObject(i);
                        if (json.getString("enddate").equals(date)) {
                            Date d = new Date();
                            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd", Locale.US);
                            if (date.equals(sdf.format(d)))
                                date = date + "(" + getResources().getString(R.string.today) + ")";
                            textView.setText(json.getString("copyright") + " [" + date + "]");
                        }
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public void prev(View source) {
        viewFlipper.setInAnimation(this, R.anim.left_in);
        viewFlipper.setOutAnimation(this, R.anim.left_out);
        viewFlipper.showPrevious();
        showInfo();
        Log.d("info", "上一张");
    }

    public void next(View source) {
        viewFlipper.setInAnimation(this, R.anim.right_in);
        viewFlipper.setOutAnimation(this, R.anim.right_out);
        viewFlipper.showNext();
        showInfo();
        Log.d("info", "下一张");
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN: {
                startX = event.getX();
                break;
            }
            case MotionEvent.ACTION_MOVE:
                break;
            case MotionEvent.ACTION_UP: {
                if (event.getX() - startX > 50) {
                    prev(getCurrentFocus());
                }
                if (startX - event.getX() > 50) {
                    next(getCurrentFocus());
                }
                break;
            }
        }
        return super.onTouchEvent(event);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        switch (id) {
            case R.id.action_settings:
                Intent intent = new Intent(this, SettingsActivity.class);
                startActivity(intent);
                return true;
            case R.id.action_set_wallpaper:
                ImageView iv = (ImageView) viewFlipper.getCurrentView();
                if (iv != null) {
                    String filename = savePath + iv.getId() + ".jpg";
                    setWallpaper(getApplicationContext(), filename);
                } else {
                    Toast.makeText(this, R.string.pic_not_available, Toast.LENGTH_LONG).show();
                }
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public class SetWallpaper extends Thread {
        Context ctx;
        String filename;
        MyHandler handler = new MyHandler(MainActivity.this);

        //为了传参所以继承……
        public SetWallpaper(Context ctx, String filename) {
            this.ctx = ctx;
            this.filename = filename;
        }

        @Override
        public void run() {
            WallpaperManager wm = WallpaperManager.getInstance(ctx);
            //region  //Android单屏壁纸设置
            //http://blog.csdn.net/csxwc/article/details/25290641
            //先设置期望的壁纸尺寸为屏幕尺寸，这样就是单屏壁纸(Apex桌面也是单屏壁纸)
            //但是！！！重启机器或者启动器Luncher或失效，壁纸又会变成可滚动，两边有黑边……
            //测试机型 华为G520-5000 4.1.2
            //endregion
            //region //不再需要设置suggestDesiredDimensions
            //原来是从ImageView getDrawable，
            //但是！！那样得到的Bitmap其实质量不高，因此必须设置期望的壁纸为单屏，才能看起来高清。
            //现在直接设置1920x1080的高清壁纸，就不需要了。
            //wm.suggestDesiredDimensions(screenWidth, screenHeight);
            //endregion
            Bitmap bitmap = BitmapFactory.decodeFile(filename);
            try {
                wm.setBitmap(bitmap);
                handler.sendEmptyMessage(0x1234);
            } catch (IOException e) {
                e.printStackTrace();
                Log.e("error", e.getLocalizedMessage());
                Toast.makeText(ctx, R.string.set_wallpaper_error, Toast.LENGTH_SHORT).show();
            }
            //记得回收图片防止OutOfMemory
            bitmap.recycle();
        }
    }

    public boolean setWallpaper(Context ctx, String filename) {
        Toast.makeText(ctx, "正在设置壁纸", Toast.LENGTH_SHORT).show();
        new SetWallpaper(ctx, filename).start();
        return true;
    }

    //region //This Handler class should be static or leaks might occur Android
    //http://www.cnblogs.com/jevan/p/3168828.html
    static class MyHandler extends Handler {
        WeakReference<Activity> mActivity;

        MyHandler(Activity activity) {
            mActivity = new WeakReference<Activity>(activity);
        }

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            Activity theActivity = mActivity.get();
            switch (msg.what) {
                case 0x1://下载json失败
                    Toast.makeText(theActivity, R.string.get_json_error, Toast.LENGTH_LONG).show();
                    ((MainActivity) theActivity).textView.setText(R.string.get_json_error);
                    break;
                case 0x12: {//下载json
                    Bundle data = msg.getData();
                    ((MainActivity) theActivity).jsonData = data.getString("json");
                    //Android之NetworkOnMainThreadException异常
                    //http://blog.csdn.net/mad1989/article/details/25964495
                    //主线程UI线程不应联网，使用Handler与新线程结合，线程内传递数据给UI
                    Log.d("info", "下载json完成:" + data.getString("json"));
                    ((MainActivity) theActivity).showInfo();
                    break;
                }
                case 0x123://下载图片完成
                    ((MainActivity) theActivity).loadImg();
                    break;
                case 0x1234://设置壁纸成功
                    Toast.makeText(theActivity, R.string.set_wallpaper_success, Toast.LENGTH_SHORT).show();
                    break;
                case 0x2: {//图片已下载
                    Bundle data = msg.getData();
                    ((MainActivity) theActivity).textView.setText(
                            String.format(
                                    theActivity.getResources().getString(R.string.pic_downloaded),
                                    //data.getString("filename")
                                    msg.obj + ".jpg"
                            )
                    );
                    Log.d("info", "msg传递的文件名" + msg.obj);
                    break;
                }
            }
        }
    }
    //endregion

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
    }
    //endregion

    //region 创建合适大小的Bitmap http://www.cnblogs.com/RayLee/archive/2010/11/09/1872856.html
    public static Bitmap createImageThumbnail(String filePath,
                                              int minSideLength,
                                              int maxNumOfPixels) {
        Bitmap bitmap = null;
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(filePath, opts);
        opts.inSampleSize = computeSampleSize(opts, minSideLength, maxNumOfPixels);
        opts.inJustDecodeBounds = false;
        try {
            bitmap = BitmapFactory.decodeFile(filePath, opts);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return bitmap;
    }

    public static int computeSampleSize(BitmapFactory.Options options,
                                        int minSideLength,
                                        int maxNumOfPixels) {
        int initialSize = computeInitialSampleSize(options, minSideLength, maxNumOfPixels);
        int roundedSize;
        if (initialSize <= 8) {
            roundedSize = 1;
            while (roundedSize < initialSize) {
                roundedSize <<= 1;
            }
        } else {
            roundedSize = (initialSize + 7) / 8 * 8;
        }
        return roundedSize;
    }

    private static int computeInitialSampleSize(BitmapFactory.Options options,
                                                int minSideLength,
                                                int maxNumOfPixels) {
        double w = options.outWidth;
        double h = options.outHeight;
        int lowerBound = (maxNumOfPixels == -1)
                ? 1
                : (int) Math.ceil(Math.sqrt(w * h / maxNumOfPixels));
        int upperBound = (minSideLength == -1)
                ? 128
                : (int) Math.min(Math.floor(w / minSideLength), Math.floor(h / minSideLength));
        if (upperBound < lowerBound) {
            // return the larger one when there is no overlapping zone.
            return lowerBound;
        }
        if ((maxNumOfPixels == -1) && (minSideLength == -1)) {
            return 1;
        } else if (minSideLength == -1) {
            return lowerBound;
        } else {
            return upperBound;
        }
    }
    //endregion

}