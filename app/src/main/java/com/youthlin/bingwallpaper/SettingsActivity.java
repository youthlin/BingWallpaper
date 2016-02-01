package com.youthlin.bingwallpaper;

//package me.drakeet.materialsettingsactivitydemo;
/**
 * Material Design 风格的设置页面
 * 有ActionBar的设置页面。
 * 自定义一个Fragment继承PreferenceFragment，然后在Activity里设置这个Fragment
 *
 * @see http://drakeet.me/material-design-settings-activity
 */

import android.annotation.TargetApi;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.FragmentManager;
import android.app.PendingIntent;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceScreen;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.Toast;

import java.net.URI;
import java.net.URISyntaxException;

//原文使用ActionBarActivity提示已过时
public class SettingsActivity extends AppCompatActivity {
    private SettingsFragment mSettingsFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        if (savedInstanceState == null) {
            mSettingsFragment = new SettingsFragment();
            //settings_container是这个activity的布局文件的fragment的id
            replaceFragment(R.id.settings_container, mSettingsFragment);
        }
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public void replaceFragment(int viewId, android.app.Fragment fragment) {
        FragmentManager fragmentManager = getFragmentManager();
        fragmentManager.beginTransaction().replace(viewId, fragment).commit();
    }

    /**
     * A placeholder fragment containing a settings view.
     */
    public static class SettingsFragment extends PreferenceFragment
            implements Preference.OnPreferenceChangeListener, Preference.OnPreferenceClickListener {

        ListPreference list;
        Preference about, notice;

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.preference);
            list = (ListPreference) findPreference("autoSet");
            about = findPreference("aboutApp");
            notice = findPreference("aboutNotice");

            list.setOnPreferenceChangeListener(this);
            about.setOnPreferenceClickListener(this);
            notice.setOnPreferenceClickListener(this);
            list.setSummary(list.getEntry());
        }

        //Android中Preference的使用以及监听事件分析
        //http://blog.csdn.net/qinjuning/article/details/6710003/
        @Override
        public boolean onPreferenceChange(Preference preference, Object newValue) {
            if (preference == list) {
                if (list.getValue() == newValue) return false;
                int value = Integer.parseInt((String) newValue);
                AlarmManager manager = (AlarmManager) getActivity().getSystemService(ALARM_SERVICE);
                Intent intent = new Intent(getActivity(), AutoSetWallpaperService.class);
                PendingIntent pi = PendingIntent.getService(getActivity(), 0, intent, 0);
                String[] entries = getResources().getStringArray(R.array.select_frq);
                list.setSummary(entries[value]);
                switch (value) {
                    case 0:
                        manager.cancel(pi);
                        return true;
                    case 1:
                        value = 2;
                        break;
                    case 2:
                        value = 4;
                        break;
                    case 3:
                        value = 8;
                        break;
                    case 4:
                        value = 16;
                        break;
                    case 5:
                        value = 24;
                        break;
                }
                long t = value * 60 * 60 * 1000;
                manager.setInexactRepeating(AlarmManager.ELAPSED_REALTIME,
                        0, t, pi);
            }
            return true;
        }

        @Override
        public boolean onPreferenceClick(Preference preference) {
            if (preference == about) {
                new AlertDialog.Builder(getActivity())
                        .setIcon(R.mipmap.ic_launcher)
                        .setTitle(R.string.setting_about_app)
                        .setMessage(R.string.setting_about_app_msg)
                        .setPositiveButton(R.string.open_github, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                Uri uri = Uri.parse("https://github.com/YouthLin/BingWallpaper");
                                Intent i = new Intent(Intent.ACTION_VIEW, uri);
                                startActivity(i);
                            }
                        })
                        .setNegativeButton(R.string.open_blog, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                Uri uri = Uri.parse("http://youthlin.com");
                                Intent i = new Intent(Intent.ACTION_VIEW, uri);
                                startActivity(i);
                            }
                        })
                        .show();
                return true;
            } else if (preference == notice) {
                new AlertDialog.Builder(getActivity())
                        .setIcon(R.mipmap.ic_launcher)
                        .setTitle(R.string.setting_about_notice)
                        .setMessage(R.string.setting_about_notice_msg)
                        .show();
                return true;
            }
            return false;
        }
    }
}