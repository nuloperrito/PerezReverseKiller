package com.perez.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import java.io.File;
import java.util.Comparator;

public class RankPrefUtil {

    private SharedPreferences sharedPreferences;

    public RankPrefUtil(Context ctx) {
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(ctx);
    }

    private void SetKeyBool(String key, boolean val) {
        SharedPreferences.Editor edt = sharedPreferences.edit();
        edt.putBoolean(key, val);
        edt.apply();
    }

    public void SetReverse(boolean val) {
        SetKeyBool("revrank", val);
    }

    public boolean GetReverse() {
        return sharedPreferences.getBoolean("revrank", false);
    }

    public void SetByName() {
        SetKeyBool("nombre", true);
        SetKeyBool("tipo", false);
        SetKeyBool("fecha", false);
        SetKeyBool("tamano", false);
    }

    public void SetByType() {
        SetKeyBool("nombre", false);
        SetKeyBool("tipo", true);
        SetKeyBool("fecha", false);
        SetKeyBool("tamano", false);
    }

    public void SetByDate() {
        SetKeyBool("nombre", false);
        SetKeyBool("tipo", false);
        SetKeyBool("fecha", true);
        SetKeyBool("tamano", false);
    }

    public void SetBySize() {
        SetKeyBool("nombre", false);
        SetKeyBool("tipo", false);
        SetKeyBool("fecha", false);
        SetKeyBool("tamano", true);
    }

    public String GetWhich() {
        boolean b1 = sharedPreferences.getBoolean("tipo", false);
        boolean b2 = sharedPreferences.getBoolean("fecha", false);
        boolean b3 = sharedPreferences.getBoolean("tamano", false);
        boolean b4 = sharedPreferences.getBoolean("nombre", false);
        if(b1) return "type";
        else if(b2) return "date";
        else if(b3) return "size";
        else if(b4) return "name";
        else return "null";
    }

    public static Comparator<File> directoryComparator = (f1, f2) -> {
        boolean isFile1Dir = f1.isDirectory();
        boolean isFile2Dir = f2.isDirectory();
        if (isFile1Dir && !isFile2Dir) {
            return -1;
        } else if (!isFile1Dir && isFile2Dir) {
            return 1;
        } else {
            return 0;
        }
    };

    public static Comparator<File> sortByName = (f1, f2) -> {
        int directoryCompare = directoryComparator.compare(f1, f2);
        if (directoryCompare != 0) {
            return directoryCompare;
        }
        return f1.getName().compareToIgnoreCase(f2.getName());
    };

    public static Comparator<File> sortByType = (f1, f2) -> {
        int directoryCompare = directoryComparator.compare(f1, f2);
        if (directoryCompare != 0) {
            return directoryCompare;
        }

        String ext1 = RealFuncUtil.getFileExtension(f1.getName());
        String ext2 = RealFuncUtil.getFileExtension(f2.getName());
        int extCompare = ext1.compareToIgnoreCase(ext2);
        if (extCompare != 0) {
            return extCompare;
        } else {
            return f1.getName().compareToIgnoreCase(f2.getName());
        }
    };

    public static Comparator<File> sortByDate = (f1, f2) -> {
        int directoryCompare = directoryComparator.compare(f1, f2);
        if (directoryCompare != 0) {
            return directoryCompare;
        }

        long lastModified1 = f1.lastModified();
        long lastModified2 = f2.lastModified();
        if (lastModified1 < lastModified2) {
            return -1;
        } else if (lastModified1 > lastModified2) {
            return 1;
        }

        String fn1 = f1.getName();
        String fn2 = f2.getName();
        return fn1.compareToIgnoreCase(fn2);
    };

    public static Comparator<File> sortBySize = (f1, f2) -> {
        int directoryCompare = directoryComparator.compare(f1, f2);
        if (directoryCompare != 0) {
            return directoryCompare;
        }

        long size1 = f1.length();
        long size2 = f2.length();
        if (size1 < size2) {
            return -1;
        } else if (size1 > size2) {
            return 1;
        }

        String fn1 = f1.getName();
        String fn2 = f2.getName();
        return fn1.compareToIgnoreCase(fn2);
    };
}
