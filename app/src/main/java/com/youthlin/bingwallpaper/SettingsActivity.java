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
import android.app.AlertDialog;
import android.app.FragmentManager;
import android.os.Build;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceScreen;
import android.support.v7.app.AppCompatActivity;
import android.widget.Toast;

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
    public static class SettingsFragment extends PreferenceFragment {

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.preference);
        }

        @Override
        public boolean onPreferenceTreeClick(PreferenceScreen ps, Preference p) {
            System.out.println("Click" + p.getKey());
            if (p.getKey().equals("autoSetWallpaer")) {
                Toast.makeText(getActivity(), R.string.auto_set_not_available, Toast.LENGTH_SHORT).show();
            } else if (p.getKey().equals("aboutApp")) {
                new AlertDialog.Builder(getActivity())
                        .setIcon(R.mipmap.ic_launcher)
                        .setTitle(R.string.setting_about_app)
//                        .setView(R.layout.settings_about)
                        .setMessage(R.string.setting_about_app_msg)
                        .show();
            } else if (p.getKey().equals("aboutNotice")) {
                new AlertDialog.Builder(getActivity())
                        .setIcon(R.mipmap.ic_launcher)
                        .setTitle(R.string.setting_about_notice)
                        .setMessage(R.string.setting_about_notice_msg)
                        .show();
            }
            return true;
        }
    }
}
