package com.perez.javah.search;

import com.perez.javah.ClassName;
import com.perez.javah.util.Utils;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class ModulePath implements SearchPath {
    private final File path;
    private List<SearchPath.Root> roots;

    public ModulePath(File path) {
        Objects.requireNonNull(path);
        path = path.getAbsoluteFile();
        this.path = path;
        if (!path.exists() || !path.isDirectory()) {
            roots = Collections.emptyList();
        } else {
            File[] files = path.listFiles();
            if (files == null) {
                roots = Collections.emptyList();
            } else {
                List<SearchPath.Root> list = new ArrayList<>();
                for (File p : files) {
                    File abs = p.getAbsoluteFile();
                    if (abs.isFile()) {
                        String n = abs.getName().toLowerCase(Locale.ROOT);
                        if (n.endsWith(".jar") || n.endsWith(".zip") || n.endsWith(".jmod")) {
                            SearchPath.Root r = Utils.classPathRoot(abs);
                            if (r != null) {
                                list.addAll(SearchPath.multiReleaseRoots(r));
                            }
                        }
                    }
                }
                roots = list;
            }
        }
    }

    @Override
    public InputStream search(ClassName name) {
        Objects.requireNonNull(name);
        return SearchPath.searchFromRoots(roots, name);
    }

    @Override
    public String toString() {
        return "ModulePath[" + path + "]";
    }
}