package com.youthlin.bingwallpaper;

import android.graphics.Bitmap;

/**
 * Created by lin on 2016-02-28-028.
 */
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
