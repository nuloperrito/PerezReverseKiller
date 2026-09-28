package com.perez.util;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.preference.PreferenceManager;
import java.io.File;
import java.util.Comparator;

public class RankPrefUtil {
    public static final String PREF_SORT_BY = "pref_key_sort_by";
    public static final String PREF_SORT_REVERSE = "pref_key_sort_reverse";

    public static final Comparator<File> sortByName = (f1, f2) -> {
        if(f1.isDirectory() && !f2.isDirectory()) return -1;
        if(!f1.isDirectory() && f2.isDirectory()) return 1;
        return f1.getName().compareToIgnoreCase(f2.getName());
    };

    public static final Comparator<File> sortByType = (f1, f2) -> {
        if(f1.isDirectory() && !f2.isDirectory()) return -1;
        if(!f1.isDirectory() && f2.isDirectory()) return 1;
        String ext1 = RealFuncUtil.getFileExtension(f1.getName());
        String ext2 = RealFuncUtil.getFileExtension(f2.getName());
        int res = ext1.compareToIgnoreCase(ext2);
        return res != 0 ? res : f1.getName().compareToIgnoreCase(f2.getName());
    };

    public static final Comparator<File> sortByDate = (f1, f2) -> {
        if(f1.isDirectory() && !f2.isDirectory()) return -1;
        if(!f1.isDirectory() && f2.isDirectory()) return 1;
        return Long.compare(f1.lastModified(), f2.lastModified());
    };

    public static final Comparator<File> sortBySize = (f1, f2) -> {
        if(f1.isDirectory() && !f2.isDirectory()) return -1;
        if(!f1.isDirectory() && f2.isDirectory()) return 1;
        return Long.compare(f1.length(), f2.length());
    };

    private final SharedPreferences sp;

    public RankPrefUtil(Context context) {
        this.sp = PreferenceManager.getDefaultSharedPreferences(context);
    }

    public String GetWhich() {
        return sp.getString(PREF_SORT_BY, "0");
    }

    public boolean GetReverse() {
        return sp.getBoolean(PREF_SORT_REVERSE, false);
    }

    public void SetByName() {
        sp.edit().putString(PREF_SORT_BY, "0").apply();
    }

    public void SetByType() {
        sp.edit().putString(PREF_SORT_BY, "1").apply();
    }

    public void SetByDate() {
        sp.edit().putString(PREF_SORT_BY, "2").apply();
    }

    public void SetBySize() {
        sp.edit().putString(PREF_SORT_BY, "3").apply();
    }

    public void SetReverse(boolean reverse) {
        sp.edit().putBoolean(PREF_SORT_REVERSE, reverse).apply();
    }
}