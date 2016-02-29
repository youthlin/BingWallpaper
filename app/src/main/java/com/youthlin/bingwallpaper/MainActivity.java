package com.youthlin.bingwallpaper;

import android.app.WallpaperManager;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.ThumbnailUtils;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {
    private GridView mGridView;
    private int numColumns;
    private MyGridViewAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        mGridView = (GridView) findViewById(R.id.gridView);
        //numColumns = mGridView.getNumColumns();//-1 ???
        numColumns = 3;
        init();
        mGridView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Log.d(ConstValues.TAG, "点击position=" + position + " id=" + id + " view=" + view);
                Intent i = new Intent(MainActivity.this, DetailActivity.class);
                i.putExtra("current", position);
                startActivity(i);
                overridePendingTransition(R.anim.right_in, R.anim.right_out);
            }
        });
        mGridView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, final int position, long id) {
                final PopupMenu popupMenu = new PopupMenu(getApplication(), view);
                getMenuInflater().inflate(R.menu.popup_menu, popupMenu.getMenu());
                popupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                    @Override
                    public boolean onMenuItemClick(MenuItem item) {
                        Snackbar.make(findViewById(R.id.fab),
                                R.string.setting_wallpaper, Snackbar.LENGTH_LONG).show();
                        new SetWallpaper(getApplicationContext(),
                                ((ImageEntry) adapter.getItem(position)).mFilePath).start();
                        return true;
                    }
                });
                popupMenu.show();
                return true;
            }
        });
    }

    @Override
    protected void onDestroy() {
        ArrayList<ImageEntry> list = adapter.list;
        for (ImageEntry entry : list) {
            Bitmap bitmap = entry.getBitmap();
            if (bitmap != null && !bitmap.isRecycled()) {
                bitmap.recycle();
            }
            entry.setBitmap(null);
        }
        super.onDestroy();
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
    }
    //endregion

    public static ArrayList<ImageEntry> getList() {
        SQLiteDatabase db = SQLiteDatabase.openOrCreateDatabase(
                ConstValues.savePath + ConstValues.dbName, null);
        Cursor c;
        c = db.rawQuery("SELECT * FROM " + ConstValues.tableName + " ORDER BY date DESC",
                new String[]{});
        String date, urlbase, copyright, link, filepath;
        ArrayList<ImageEntry> list = new ArrayList<>();
        SimpleDateFormat sdf1 = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        SimpleDateFormat sdf2 = new SimpleDateFormat("yyyyMMdd", Locale.getDefault());
        Date tmpDate;
        while (c.moveToNext()) {
            date = c.getString(c.getColumnIndex("date"));
            urlbase = c.getString(c.getColumnIndex("urlbase"));
            copyright = c.getString(c.getColumnIndex("copyright"));
            link = c.getString(c.getColumnIndex("copyrightlink"));
            filepath = c.getString(c.getColumnIndex("filepath"));
            try {
                tmpDate = sdf2.parse(date);
                date = sdf1.format(tmpDate);
            } catch (ParseException e) {
                e.printStackTrace();
                Log.d(ConstValues.TAG, "格式化日期出错,使用默认格式");
                date = c.getString(c.getColumnIndex("date"));
            }
            list.add(new ImageEntry(date, urlbase, copyright, link, filepath));
        }
        c.close();
        db.close();
        return list;
    }

    private void init() {
        //region GridView异步加载本地图片缩略图
        /**
         * GridView异步加载本地图片缩略图
         * @link http://blog.csdn.net/shouliang52000/article/details/7636232
         *
         * Android 利用 AsyncTask 异步读取网络图片
         * @link http://www.cnblogs.com/_ymw/p/4140418.html
         */
        adapter = new MyGridViewAdapter(this, getList());
        mGridView.setAdapter(adapter);
        new ImageLoadAsyncTask(adapter).execute();//endregion
    }

    private class ImageLoadAsyncTask extends AsyncTask<Void, Integer, Void> {
        private MyGridViewAdapter adapter;

        public ImageLoadAsyncTask(MyGridViewAdapter a) {
            super();
            adapter = a;
        }

        @Override
        protected Void doInBackground(Void... params) {
            ImageEntry entry;
            String path;
            Bitmap bitmap, newbitmap;
            DisplayMetrics dm = getResources().getDisplayMetrics();
            int width = dm.widthPixels / numColumns;
            for (int i = 0; i < adapter.getCount(); i++) {
                entry = (ImageEntry) adapter.getItem(i);
                path = entry.mFilePath;
                if (!(new File(path)).exists()) {
                    Log.d(ConstValues.TAG, "图片已被删,正在下载" + path);
                    SplashActivity.downImg(entry.mUrlBase, new File(path));
                }
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inSampleSize = 10;
                bitmap = BitmapFactory.decodeFile(path, options);
                newbitmap = ThumbnailUtils.extractThumbnail(bitmap, width, width);
                if (bitmap != null && !bitmap.isRecycled())
                    bitmap.recycle();
                entry.setBitmap(newbitmap);
                if (newbitmap != null) {
                    publishProgress(i, adapter.getCount());
                } else {
                    Log.d(ConstValues.TAG, "获取本地图片缩略图出错" + path);
                }
            }
            return null;
        }

        @Override
        public void onProgressUpdate(Integer... values) {
            adapter.notifyDataSetChanged();
        }
    }

    private class MyGridViewAdapter extends BaseAdapter {
        private Context context;
        private ArrayList<ImageEntry> list;

        public MyGridViewAdapter(Context c, ArrayList<ImageEntry> _list) {
            super();
            context = c;
            list = _list;
        }

        @Override
        public int getCount() {
            return list.size();
        }

        @Override
        public Object getItem(int position) {
            return list.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null)
                convertView = LayoutInflater.from(context).inflate(R.layout.gridview_item, null);
            ImageView imageView = (ImageView) convertView.findViewById(R.id.gridViewItemImg);
            TextView textView = (TextView) convertView.findViewById(R.id.gridViewItemText);
            Bitmap img = list.get(position).getBitmap();
            if (img == null) {
                int width = getResources().getDisplayMetrics().widthPixels;
                int[] colors = new int[width * width];
                img = Bitmap.createBitmap(colors, width, width, Bitmap.Config.ALPHA_8);
            }
            imageView.setImageBitmap(img);
            textView.setText(list.get(position).mDate);
            //registerForContextMenu(convertView);
            return convertView;
        }
    }

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
                case ConstValues.SET_WALLPAPER_SUCCESS:
                    Snackbar.make(activity.findViewById(R.id.fab),
                            R.string.set_wallpaper_succ, Snackbar.LENGTH_LONG).show();
                    break;
                case ConstValues.FILE_NOT_FOUND:
                    Snackbar.make(activity.findViewById(R.id.fab),
                            R.string.set_wallpaper_file_not_found, Snackbar.LENGTH_LONG).show();
                    break;
                case ConstValues.IO_EXCEPTION:
                    Snackbar.make(activity.findViewById(R.id.fab),
                            R.string.set_wallpaper_io_exception, Snackbar.LENGTH_LONG).show();
                    break;
            }
        }
    }

    public class SetWallpaper extends Thread {
        Context ctx;
        String filename;
        MyHandler handler;

        public SetWallpaper(Context ctx, String filename) {
            this.ctx = ctx;
            this.filename = filename;
            handler = new MyHandler(MainActivity.this);
        }

        @Override
        public void run() {
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
            //记得回收图片防止OutOfMemory
            if (!bitmap.isRecycled())
                bitmap.recycle();
        }
    }
}
