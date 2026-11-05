package com.perez.javah.search;

import com.perez.javah.ClassName;

import java.io.InputStream;
import java.util.Objects;

public class RuntimeSearchPath implements SearchPath {
    public static final RuntimeSearchPath INSTANCE = new RuntimeSearchPath();

    private RuntimeSearchPath() {
    }

    @Override
    public InputStream search(ClassName name) {
        Objects.requireNonNull(name);
        try {
            Class<?> cls = Class.forName(name.className());
            InputStream in = cls.getResourceAsStream(name.simpleName() + ".class");
            if (in != null) {
                return in;
            }
        } catch (Throwable ignored) {
        }
        try {
            ClassLoader loader = Thread.currentThread().getContextClassLoader();
            if (loader != null) {
                InputStream in = loader.getResourceAsStream(name.relativePath());
                if (in != null) {
                    return in;
                }
            }
        } catch (Throwable ignored) {
        }
        try {
            ClassLoader loader = ClassLoader.getSystemClassLoader();
            if (loader != null) {
                InputStream in = loader.getResourceAsStream(name.relativePath());
                if (in != null) {
                    return in;
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    public static InputStream searchClass(String name) {
        return INSTANCE.search(name);
    }

    public static InputStream searchClass(ClassName name) {
        return INSTANCE.search(name);
    }
}