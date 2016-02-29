package com.youthlin.bingwallpaper;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.design.widget.AppBarLayout;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.TranslateAnimation;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewFlipper;

import java.util.ArrayList;
import java.util.Locale;

public class DetailActivity extends AppCompatActivity {
    private static boolean isFullScreen = false;
    private int current;
    private ArrayList<ImageEntry> list;
    private float startX;
    private int statusHeight = 0;
    private TextView description;
    private AppBarLayout mAppBarLayout;
    private ViewFlipper flipper;
    private FloatingActionButton mFab;
    private long lastTouchTime = 0;
    private boolean ready = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //Log.d(ConstValues.TAG, "创建=" + System.currentTimeMillis());
        Intent i = getIntent();
        current = i.getIntExtra("current", 0);
        statusHeight = getStatusHeight(this);
        //region 当 Activity 以全屏模式运行时，如何允许 Android 系统状态栏在顶层出现，
        // 而不迫使 Activity 重新布局让出空间？ - 余炜的回答 - 知乎
        // https://www.zhihu.com/question/19760889/answer/19534818
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);//endregion

        new AsyncTask<Integer, Integer, Bitmap[]>() {
            @Override
            protected void onPreExecute() {
                initView();
                //Log.d(ConstValues.TAG, "布局=" + System.currentTimeMillis());
            }

            @Override
            protected Bitmap[] doInBackground(Integer... params) {
                list = ImageEntry.getList();
                int current = params[0];
                Bitmap bitmap0 = null, bitmap1 = null, bitmap2 = null;
                try {
                    if (current <= 0)
                        bitmap0 = (BitmapFactory.decodeFile(list.get(list.size() - 1).mFilePath));
                    else bitmap0 = (BitmapFactory.decodeFile(list.get(current - 1).mFilePath));
                    //publishProgress(1, 3);
                    bitmap1 = BitmapFactory.decodeFile(list.get(current).mFilePath);
                    //publishProgress(2, 3);
                    if (current >= list.size() - 1)
                        bitmap2 = (BitmapFactory.decodeFile(list.get(0).mFilePath));
                    else bitmap2 = (BitmapFactory.decodeFile(list.get(current + 1).mFilePath));
                    //publishProgress(3, 3);
                } catch (IndexOutOfBoundsException e) {
                    Log.d(ConstValues.TAG, "list为空,数组越界");
                }
                return new Bitmap[]{bitmap0, bitmap1, bitmap2};
            }

            @Override
            protected void onProgressUpdate(Integer... values) {
                //Log.d(ConstValues.TAG, "updated:" + values[0] + "/" + values[1]);
            }

            @Override
            protected void onPostExecute(Bitmap[] result) {
                if (result[0] == null || result[1] == null || result[2] == null) {
                    Toast.makeText(getApplicationContext(),
                            R.string.set_wallpaper_file_not_found, Toast.LENGTH_SHORT).show();
                }
                ImageView imageView = new ImageView(getApplicationContext());
                imageView.setImageBitmap(result[0]);
                flipper.addView(imageView);
                imageView = new ImageView(getApplicationContext());
                imageView.setImageBitmap(result[1]);
                flipper.addView(imageView);
                imageView = new ImageView(getApplicationContext());
                imageView.setImageBitmap(result[2]);
                flipper.addView(imageView);
                flipper.showNext();
                ready = true;
                showInfo();
                mFab.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        try {
                            new SetWallpaper(getApplication(),
                                    DetailActivity.this, list.get(current).mFilePath).start();
                        } catch (IndexOutOfBoundsException e) {
                            e.printStackTrace();
                        }
                    }
                });
                //Log.d(ConstValues.TAG, "完成=" + System.currentTimeMillis());
            }
        }.execute(current);
    }

    public void recycleView(int i) {
        ImageView view = (ImageView) flipper.getChildAt(i);
        if (view == null) return;
        Drawable drawable = view.getDrawable();
        if (drawable != null && drawable instanceof BitmapDrawable) {
            BitmapDrawable bitmapDrawable = (BitmapDrawable) drawable;
            Bitmap bitmap = bitmapDrawable.getBitmap();
            if (bitmap != null && !bitmap.isRecycled()) {
                bitmap.recycle();
                //Log.d(ConstValues.TAG, "回收Bitmap");
            }
        }
    }

    @Override
    protected void onDestroy() {
        //[Android Memory] 手动回收ImageVIew的图片资源
        //http://www.cnblogs.com/0616--ataozhijia/p/3954402.html
        for (int i = 0; i < flipper.getChildCount(); i++) {
            recycleView(i);
        }
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        overridePendingTransition(R.anim.left_in, R.anim.left_out);
    }

    private void initView() {
        setContentView(R.layout.activity_detail);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar1);
        setSupportActionBar(toolbar);
        toolbar.setNavigationIcon(R.drawable.back);
        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onBackPressed();
            }
        });
        mAppBarLayout = (AppBarLayout) findViewById(R.id.toolbarWrap);
        description = (TextView) findViewById(R.id.description);
        //region  设置ToolBar位置刚好在状态栏之下
        //android如何设置控件位置？
        //http://bbs.csdn.net/topics/370225532#post-381091149
        AppBarLayout.LayoutParams params = (AppBarLayout.LayoutParams) toolbar.getLayoutParams();
        params.setMargins(0, statusHeight, 0, 0);// 通过自定义坐标来放置你的控件
        toolbar.setLayoutParams(params);//endregion
        flipper = (ViewFlipper) findViewById(R.id.flipper);
        mFab = (FloatingActionButton) findViewById(R.id.fab);
    }

    public void showInfo() {
        TranslateAnimation animation = new TranslateAnimation(0, 0, 0, description.getHeight());
        animation.setDuration(200);
        animation.setFillAfter(true);
        description.startAnimation(animation);
        try {
            description.setText(String.format(Locale.getDefault(),
                    getResources().getString(R.string.description),
                    list.get(current).mDate, current + 1, list.size(), list.get(current).mCopyright));
            description.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(list.get(current).mLink)));
                    return true;
                }
            });
        } catch (IndexOutOfBoundsException e) {
            description.setText(R.string.set_wallpaper_file_not_found);
        }
        animation = new TranslateAnimation(0, 0, description.getHeight(), 0);
        animation.setDuration(200);
        animation.setFillAfter(true);
        description.startAnimation(animation);
    }

    public void next() {
        //Log.d(ConstValues.TAG, "last=" + lastTouchTime);
        flipper.setInAnimation(this, R.anim.right_in);
        flipper.setOutAnimation(this, R.anim.right_out);
        flipper.showNext();
        current++;
        if (current > list.size() - 1) {
            current = 0;
        }
        recycleView(0);
        flipper.removeViewAt(0);
        ImageView imageView = new ImageView(this);
        Bitmap bitmap = null;
        try {
            if (current >= list.size() - 1) {
                bitmap = BitmapFactory.decodeFile(list.get(0).mFilePath);
            } else bitmap = BitmapFactory.decodeFile(list.get(current + 1).mFilePath);
        } catch (IndexOutOfBoundsException e) {
            e.printStackTrace();
        }
        imageView.setImageBitmap(bitmap);
        flipper.addView(imageView);
        showInfo();
    }

    public void prev() {
        flipper.setInAnimation(this, R.anim.left_in);
        flipper.setOutAnimation(this, R.anim.left_out);
        flipper.showPrevious();
        current--;
        if (current < 0) current = list.size() - 1;
        recycleView(flipper.getChildCount() - 1);
        flipper.removeViewAt(flipper.getChildCount() - 1);
        ImageView imageView = new ImageView(this);
        Bitmap bitmap = null;
        try {
            if (current <= 0)
                bitmap = BitmapFactory.decodeFile(list.get(list.size() - 1).mFilePath);
            else bitmap = BitmapFactory.decodeFile(list.get(current - 1).mFilePath);
        } catch (IndexOutOfBoundsException e) {
            e.printStackTrace();
        }
        imageView.setImageBitmap(bitmap);
        flipper.addView(imageView, 0);
        showInfo();
    }

    //切换全屏
    private void toggleFullScreen() {
        //Android 动画之TranslateAnimation应用详解
        //http://www.jb51.net/article/32339.htm
        //如何正确使用平移动画（关于fillBefore和fillAfter的一点说明）
        //http://blog.csdn.net/fengkuanghun/article/details/7878862
        TranslateAnimation animation;
        if (!isFullScreen) {
            WindowManager.LayoutParams attrs = getWindow().getAttributes();
            attrs.flags |= WindowManager.LayoutParams.FLAG_FULLSCREEN;
            getWindow().setAttributes(attrs);

            animation = new TranslateAnimation(0, 0, 0,
                    -statusHeight - mAppBarLayout.getHeight());
            animation.setDuration(200);//持续时间
            animation.setFillAfter(true);//使控件停留在动画结束的位置
            mAppBarLayout.startAnimation(animation);
            animation = new TranslateAnimation(0, 0, 0, description.getHeight());
            animation.setDuration(200);
            animation.setFillAfter(true);
            description.startAnimation(animation);
            animation = new TranslateAnimation(0, mFab.getWidth()
                    + ((RelativeLayout.LayoutParams) mFab.getLayoutParams()).rightMargin, 0, 0);
            animation.setDuration(200);
            animation.setFillAfter(true);
            mFab.startAnimation(animation);
            description.setLongClickable(false);
            mFab.setClickable(false);
        } else {
            WindowManager.LayoutParams attrs = getWindow().getAttributes();
            attrs.flags &= ~WindowManager.LayoutParams.FLAG_FULLSCREEN;
            getWindow().setAttributes(attrs);

            animation = new TranslateAnimation(0, 0,
                    -statusHeight - mAppBarLayout.getHeight(), 0);
            animation.setDuration(200);
            animation.setFillAfter(true);
            mAppBarLayout.startAnimation(animation);
            animation = new TranslateAnimation(0, 0, description.getHeight(), 0);
            animation.setDuration(200);
            animation.setFillAfter(true);
            description.startAnimation(animation);
            animation = new TranslateAnimation(mFab.getWidth()
                    + ((RelativeLayout.LayoutParams) mFab.getLayoutParams()).rightMargin, 0, 0, 0);
            animation.setDuration(200);
            animation.setFillAfter(true);
            mFab.startAnimation(animation);
            description.setLongClickable(true);
            mFab.setClickable(true);
        }
        isFullScreen = !isFullScreen;
    }

    //region //菜单
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_settings:
                break;
            default:
                return super.onOptionsItemSelected(item);
        }
        return true;
    }//endregion

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!ready) return super.onTouchEvent(event);
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN: {
                startX = event.getX();
                break;
            }
            case MotionEvent.ACTION_MOVE:
                break;
            case MotionEvent.ACTION_UP: {
                if (event.getX() - startX > 50) {
                    if (System.currentTimeMillis() - lastTouchTime < 350) break;
                    lastTouchTime = System.currentTimeMillis();
                    prev();
                } else if (startX - event.getX() > 50) {
                    if (System.currentTimeMillis() - lastTouchTime < 350) break;
                    lastTouchTime = System.currentTimeMillis();
                    next();
                } else {
                    //点击
                    toggleFullScreen();
                }
                break;
            }
        }
        return super.onTouchEvent(event);
    }

    //region 获取状态栏高度，使用反射机制读取com.android.internal.R$dimen.id.status_bar_height

    /**
     * 获取状态栏高度
     *
     * @link http://www.cnblogs.com/mengshu-lbq/archive/2012/12/06/2804559.html
     */
    public static int getStatusHeight(AppCompatActivity activity) {
        int statusHeight;
        Rect localRect = new Rect();
        activity.getWindow().getDecorView().getWindowVisibleDisplayFrame(localRect);
        statusHeight = localRect.top;
        if (0 == statusHeight) {
            Class<?> localClass;
            try {
                localClass = Class.forName("com.android.internal.R$dimen");
                Object localObject = localClass.newInstance();
                int i5 = Integer.parseInt(localClass.getField("status_bar_height")
                        .get(localObject).toString());
                statusHeight = activity.getResources().getDimensionPixelSize(i5);
            } catch (Exception e) {
                e.printStackTrace();
                statusHeight = 38;
            }
        }
        return statusHeight;
    }//endregion

}
