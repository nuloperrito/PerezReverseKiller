package com.perez.javah.search;

import com.perez.javah.ClassName;

import java.io.File;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import static com.perez.javah.util.Utils.*;

public class ClassPath implements SearchPath {
    private final File path;
    private final List<SearchPath.Root> roots;

    public ClassPath(File path) {
        Objects.requireNonNull(path);
        this.path = path.getAbsoluteFile();
        SearchPath.Root root = classPathRoot(this.path);
        roots = root == null ? Collections.emptyList() : SearchPath.multiReleaseRoots(root);
    }

    @Override
    public InputStream search(ClassName name) {
        Objects.requireNonNull(name);
        return SearchPath.searchFromRoots(roots, name);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ClassPath classPath = (ClassPath) o;
        return Objects.equals(path, classPath.path);
    }

    @Override
    public int hashCode() {
        return path.hashCode();
    }

    @Override
    public String toString() {
        return "ClassPath[" + path + "]";
    }
}