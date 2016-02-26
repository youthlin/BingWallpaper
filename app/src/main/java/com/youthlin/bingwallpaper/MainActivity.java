package com.youthlin.bingwallpaper;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.ThumbnailUtils;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.File;
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
            }
        });
    }

    @Override
    protected void onDestroy() {
        ArrayList<ImageEntry> list = adapter.list;
        for (ImageEntry entry : list) {
            if (entry.getBitmap() != null)
                if (!entry.getBitmap().isRecycled()) {
                    entry.getBitmap().recycle();
                    entry.setBitmap(null);
                }
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
                return true;
        }
        return super.onOptionsItemSelected(item);
    }//endregion

    private void init() {
        SQLiteDatabase db = SQLiteDatabase.openOrCreateDatabase(
                ConstValues.savePath + ConstValues.dbName, null);
        Cursor c;
        c = db.rawQuery("SELECT * FROM " + ConstValues.tableName + " ORDER BY date DESC", new String[]{});
        String date, urlbase, copyright, link, filepath;
        ArrayList<ImageEntry> list = new ArrayList<>();
        SimpleDateFormat sdf1 = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        SimpleDateFormat sdf2 = new SimpleDateFormat("yyyyMMdd", Locale.getDefault());

        Date tmpDate, today = new Date();
        while (c.moveToNext()) {
            date = c.getString(c.getColumnIndex("date"));
            urlbase = c.getString(c.getColumnIndex("urlbase"));
            copyright = c.getString(c.getColumnIndex("copyright"));
            link = c.getString(c.getColumnIndex("copyrightlink"));
            filepath = c.getString(c.getColumnIndex("filepath"));
            try {
                tmpDate = sdf2.parse(date);
                if (sdf2.format(today).equals(date))
                    date = getResources().getString(R.string.today);
                else
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

        //region GridView异步加载本地图片缩略图
        /**
         * GridView异步加载本地图片缩略图
         * @link http://blog.csdn.net/shouliang52000/article/details/7636232
         *
         * Android 利用 AsyncTask 异步读取网络图片
         * @link http://www.cnblogs.com/_ymw/p/4140418.html
         */
        adapter = new MyGridViewAdapter(this, list);
        mGridView.setAdapter(adapter);
        new ImageLoadAsyncTask(adapter).execute();//endregion
    }

    public class ImageEntry {
        public String mDate, mUrlBase, mCopyright, mLink, mFilePath;
        private Bitmap mbitmap;

        public ImageEntry(String date, String urlbase, String copyright, String link, String path) {
            mDate = date;
            mUrlBase = urlbase;
            mCopyright = copyright;
            mLink = link;
            mFilePath = path;
        }

        public Bitmap getBitmap() {
            return mbitmap;
        }

        public void setBitmap(Bitmap b) {
            mbitmap = b;
        }
    }

    private class ImageLoadAsyncTask extends AsyncTask<Void, Integer, Integer> {
        private MyGridViewAdapter adapter;

        public ImageLoadAsyncTask(MyGridViewAdapter a) {
            super();
            adapter = a;
        }

        @Override
        protected Integer doInBackground(Void... params) {
            ImageEntry entry;
            String path;
            Bitmap bitmap, newbitmap;
            DisplayMetrics dm = getResources().getDisplayMetrics();
            int width;
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
                width = dm.widthPixels / numColumns;
                /*Log.d(ConstValues.TAG, "columns=" + numColumns + "width=" + width + " height="
                        + width * ConstValues.picHeight / ConstValues.picWidth);*/
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
//            Log.d(ConstValues.TAG, "加载图片:" + values[0] + "/" + values[1]);
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
            imageView.setImageBitmap(list.get(position).getBitmap());
            textView.setText(list.get(position).mDate);
            return convertView;
        }
    }
}
