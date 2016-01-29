package com.youthlin.bingwallpaper;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.MotionEvent;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ViewFlipper;


public class MainActivity extends AppCompatActivity {
    private ViewFlipper viewFlipper;
    private float startX;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        viewFlipper = (ViewFlipper) findViewById(R.id.images);
        //TODO 下载壁纸图片
        //http://www.bing.com/HPImageArchive.aspx?format=js&n=1
        //http://s.cn.bing.net/az/hprichbg/rb/AxiaVillage_ZH-CN10956625064_1920x1080.jpg
    }

    public void prev(View source) {
        viewFlipper.setInAnimation(this, R.anim.left_in);
        viewFlipper.setOutAnimation(this, R.anim.left_out);
        viewFlipper.showPrevious();
    }

    public void next(View source) {
        viewFlipper.setInAnimation(this, R.anim.right_in);
        viewFlipper.setOutAnimation(this, R.anim.right_out);
        viewFlipper.showNext();
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
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        switch (id) {
            case R.id.action_settings:
                Intent intent = new Intent(this, SettingsActivity.class);
                startActivity(intent);
                return true;
            case R.id.action_set_wallpaper:
                //next(findViewById(id));
                //TODO 设置壁纸
                return true;
            case R.id.action_save:
                //prev(findViewById(id));
                //TODO 保存壁纸
                return true;
            default:
        }
        return super.onOptionsItemSelected(item);
    }
}
