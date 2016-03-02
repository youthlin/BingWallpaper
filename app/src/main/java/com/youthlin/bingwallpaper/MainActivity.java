package com.youthlin.bingwallpaper;

import android.content.Context;
import android.content.Intent;
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
import android.widget.PopupMenu;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;

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
                //Log.d(ConstValues.TAG, "点击=" + System.currentTimeMillis());
                Intent i = new Intent(MainActivity.this, DetailActivity.class);
                i.putExtra("current", position);
                startActivity(i);
                overridePendingTransition(R.anim.right_in, R.anim.right_out);
                //Log.d(ConstValues.TAG, "跳转=" + System.currentTimeMillis());
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
                        new SetWallpaper(getApplication(), MainActivity.this,
                                ((ImageEntry) adapter.getItem(position)).mFilePath).start();
                        return true;
                    }
                });
                popupMenu.show();
                return true;
            }
        });
        mGridView.setSelector(R.drawable.selector_gridview_item);
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
                startActivity(new Intent(MainActivity.this, SettingsActivity.class));
                overridePendingTransition(R.anim.right_in, R.anim.right_out);
                break;
            default:
                return super.onOptionsItemSelected(item);
        }
        return true;
    }
    //endregion

    private void init() {
        //region GridView异步加载本地图片缩略图
        /**
         * GridView异步加载本地图片缩略图
         * @link http://blog.csdn.net/shouliang52000/article/details/7636232
         *
         * Android 利用 AsyncTask 异步读取网络图片
         * @link http://www.cnblogs.com/_ymw/p/4140418.html
         */
        adapter = new MyGridViewAdapter(this, ImageEntry.getList(getApplication()));
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
            int height = dm.widthPixels / numColumns;
            for (int i = 0; i < adapter.getCount(); i++) {
                entry = (ImageEntry) adapter.getItem(i);
                path = entry.mFilePath;
                if (!(new File(path)).exists()) {
                    Log.d(ConstValues.TAG, "图片已被删,正在下载" + path);
                    ImageEntry.downImg(getApplicationContext(), entry.mUrlBase, new File(path));
                }
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inSampleSize = 10;
                bitmap = BitmapFactory.decodeFile(path, options);
                newbitmap = ThumbnailUtils.extractThumbnail(bitmap, width, height);
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

    private static Bitmap tempImg = null;

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
                if (tempImg == null) {
                    int w = getResources().getDisplayMetrics().widthPixels;
                    int[] colors = new int[w * w];
                    tempImg = Bitmap.createBitmap(colors, w, w, Bitmap.Config.ALPHA_8);
                }
                img = tempImg;
            }
            imageView.setImageBitmap(img);
            textView.setText(list.get(position).mDate);
            return convertView;
        }
    }

}
