package com.youthlin.bingwallpaper;

/**
 * Created by lin on 2016-01-29-029.
 */
public class Wallpaper {

    private String date, url, copyright;


    public String getDate() {
        return date;
    }

    public String getUrl() {
        return url;
    }

    public String getCopyright() {
        return copyright;
    }

    public Wallpaper(String url, String date, String copyright) {
        this.url = url;
        this.date = date;
        this.copyright = copyright;
    }
}
