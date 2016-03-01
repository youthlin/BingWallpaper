package com.youthlin.bingwallpaper;

import android.annotation.TargetApi;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.FragmentManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.SwitchPreference;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

//http://drakeet.me/material-design-settings-activity
public class SettingsActivity extends AppCompatActivity {
    private SettingsFragment mSettingsFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        if (savedInstanceState == null) {
            mSettingsFragment = new SettingsFragment();
            replaceFragment(R.id.settings_container, mSettingsFragment);
        }
        Toolbar toolbar = (Toolbar) findViewById(R.id.settigs_toolbar);
        toolbar.setNavigationIcon(R.drawable.back);
        setSupportActionBar(toolbar);
        //要在setSupportActionBar之后设置监听
        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onBackPressed();
            }
        });
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        overridePendingTransition(R.anim.left_in, R.anim.left_out);
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public void replaceFragment(int viewId, android.app.Fragment fragment) {
        FragmentManager fragmentManager = getFragmentManager();
        fragmentManager.beginTransaction().replace(viewId, fragment).commit();
    }


    public static class SettingsFragment extends PreferenceFragment
            implements Preference.OnPreferenceClickListener, Preference.OnPreferenceChangeListener {

        CheckBoxPreference checkBox;
        Preference about, notice;

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.preference);
            about = findPreference("aboutApp");
            notice = findPreference("aboutNotice");
            checkBox = (CheckBoxPreference) findPreference("autoSetWallpaper");
            about.setOnPreferenceClickListener(this);
            notice.setOnPreferenceClickListener(this);
            checkBox.setOnPreferenceChangeListener(this);
        }

        //Android中Preference的使用以及监听事件分析
        //http://blog.csdn.net/qinjuning/article/details/6710003/
        @Override
        public boolean onPreferenceChange(Preference preference, Object newValue) {
            if (preference == checkBox) {
                Intent intent = new Intent(getActivity(), SetWallpaperIntentService.class);
                intent.setAction(SetWallpaperIntentService.ACTION_SET_NEWEST_WALLPAPER);
                PendingIntent pi = PendingIntent.getService(getActivity(), 0, intent, 0);
                AlarmManager am = (AlarmManager) getActivity().getSystemService(Service.ALARM_SERVICE);
                if (newValue.equals(true)) {
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()),
                            sdf2 = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                    Calendar calendar = Calendar.getInstance();
                    calendar.add(Calendar.DATE, 1);//日期加一天
                    long start = System.currentTimeMillis();
                    try {
                        String t = sdf2.format(calendar.getTime());//明天的日期，不带时间//因为时间是当前时间所以舍弃
                        Date tomorrow = sdf.parse(t + " 00:00:00");//明天的日期加上时间凌晨零点
                        start = tomorrow.getTime();
                    } catch (ParseException e) {
                        e.printStackTrace();
                    }
                    long repeat = 86400000;//24 * 3600 * 1000
                    am.setRepeating(AlarmManager.RTC_WAKEUP, start, repeat, pi);
                } else {
                    Log.d(ConstValues.TAG, "cancle");
                    am.cancel(pi);
                }
            }
            return true;
        }

        @Override
        public boolean onPreferenceClick(Preference preference) {
            if (preference == about) {
                //region 对话框
                new AlertDialog.Builder(getActivity())
                        .setIcon(R.mipmap.ic_launcher)
                        .setTitle(R.string.settings_about_app)
                        .setMessage(R.string.setting_about_app_msg)
                        .setPositiveButton(R.string.open_github, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                startActivity(new Intent(Intent.ACTION_VIEW,
                                        Uri.parse("https://github.com/YouthLin/BingWallpaper")));
                            }
                        })
                        .setNegativeButton(R.string.open_blog, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                startActivity(new Intent(Intent.ACTION_VIEW,
                                        Uri.parse("http://youthlin.com")));
                            }
                        })
                        .show();
                return true;//endregion
            } else if (preference == notice) {//region
                new AlertDialog.Builder(getActivity())
                        .setIcon(R.mipmap.ic_launcher)
                        .setTitle(R.string.settings_about_notice)
                        .setMessage(R.string.setting_about_notice_msg)
                        .show();
                return true;//endregion
            }
            return false;
        }
    }
}