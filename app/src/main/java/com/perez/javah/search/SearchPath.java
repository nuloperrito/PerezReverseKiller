package com.perez.javah.search;

import com.perez.javah.ClassName;

import java.io.*;
import java.util.*;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static com.perez.javah.util.Utils.*;

public interface SearchPath {
    InputStream search(ClassName name);

    default InputStream search(String fullName) {
        Objects.requireNonNull(fullName);
        return search(ClassName.ofFullName(fullName));
    }

    static InputStream searchFrom(Iterable<SearchPath> searchPaths, ClassName name) {
        Objects.requireNonNull(searchPaths);
        Objects.requireNonNull(name);
        for (SearchPath searchPath : searchPaths) {
            if (searchPath == null) {
                continue;
            }
            InputStream in = searchPath.search(name);
            if (in != null) {
                return in;
            }
        }
        return null;
    }

    static InputStream searchFromRoots(Iterable<Root> roots, ClassName name) {
        Objects.requireNonNull(roots);
        Objects.requireNonNull(name);
        for (Root root : roots) {
            if (root == null) {
                continue;
            }
            InputStream in = root.search(name);
            if (in != null) {
                return in;
            }
        }
        return null;
    }

    static List<Root> multiReleaseRoots(Root root) {
        Objects.requireNonNull(root);
        return root.multiReleaseRoots();
    }

    interface Root {
        InputStream search(ClassName name);
        List<Root> multiReleaseRoots();
    }

    class ClassFileRoot implements Root {
        private final File file;

        public ClassFileRoot(File file) {
            this.file = Objects.requireNonNull(file);
        }

        public File getFile() {
            return file;
        }

        @Override
        public InputStream search(ClassName name) {
            Objects.requireNonNull(name);
            if (!file.isFile()) {
                return null;
            }
            String fileName = file.getName();
            if (fileName.equals(name.simpleName() + ".class") || fileName.equals(name.className() + ".class")) {
                try {
                    return new FileInputStream(file);
                } catch (IOException ignored) {
                    return null;
                }
            }
            return null;
        }

        @Override
        public List<Root> multiReleaseRoots() {
            return Collections.singletonList(this);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ClassFileRoot)) return false;
            ClassFileRoot that = (ClassFileRoot) o;
            return file.equals(that.file);
        }

        @Override
        public int hashCode() {
            return file.hashCode();
        }

        @Override
        public String toString() {
            return "ClassFileRoot[" + file + "]";
        }
    }

    class DirectoryRoot implements Root {
        private final File dir;

        public DirectoryRoot(File dir) {
            this.dir = Objects.requireNonNull(dir);
        }

        public File getDirectory() {
            return dir;
        }

        @Override
        public InputStream search(ClassName name) {
            Objects.requireNonNull(name);
            if (!dir.isDirectory()) {
                return null;
            }
            File file = new File(dir, name.relativePath().replace('/', File.separatorChar));
            if (file.isFile()) {
                try {
                    return new FileInputStream(file);
                } catch (IOException ignored) {
                    return null;
                }
            }
            File flatFile = new File(dir, name.simpleName() + ".class");
            if (flatFile.isFile()) {
                try {
                    return new FileInputStream(flatFile);
                } catch (IOException ignored) {
                    return null;
                }
            }
            return null;
        }

        @Override
        public List<Root> multiReleaseRoots() {
            if (!dir.isDirectory()) {
                return Collections.emptyList();
            }
            boolean isMultiRelease = false;
            File manifestFile = new File(dir, "META-INF" + File.separator + "MANIFEST.MF");
            if (manifestFile.isFile()) {
                try (InputStream in = new FileInputStream(manifestFile)) {
                    Manifest manifest = new Manifest(in);
                    isMultiRelease = "true".equalsIgnoreCase(manifest.getMainAttributes().getValue("Multi-Release"));
                } catch (IOException | NullPointerException ignored) {
                }
            }
            if (isMultiRelease) {
                File base = new File(dir, "META-INF" + File.separator + "versions");
                if (base.isDirectory()) {
                    File[] versionDirs = base.listFiles(new FileFilter() {
                        @Override
                        public boolean accept(File pathname) {
                            return pathname.isDirectory();
                        }
                    });
                    if (versionDirs != null) {
                        List<File> validDirs = new ArrayList<>();
                        for (File vd : versionDirs) {
                            if (MULTI_RELEASE_VERSIONS.contains(vd.getName())) {
                                validDirs.add(vd);
                            }
                        }
                        Collections.sort(validDirs, new Comparator<File>() {
                            @Override
                            public int compare(File f1, File f2) {
                                return Integer.compare(Integer.parseInt(f2.getName()), Integer.parseInt(f1.getName()));
                            }
                        });
                        List<Root> list = new LinkedList<>();
                        for (File vd : validDirs) {
                            list.add(new DirectoryRoot(vd.getAbsoluteFile()));
                        }
                        list.add(this);
                        return Collections.unmodifiableList(list);
                    }
                }
            }
            return Collections.singletonList(this);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof DirectoryRoot)) return false;
            DirectoryRoot that = (DirectoryRoot) o;
            return dir.equals(that.dir);
        }

        @Override
        public int hashCode() {
            return dir.hashCode();
        }

        @Override
        public String toString() {
            return "DirectoryRoot[" + dir + "]";
        }
    }

    class ArchiveRoot implements Root {
        private final File file;
        private final String prefix;

        public ArchiveRoot(File file, String prefix) {
            this.file = Objects.requireNonNull(file);
            this.prefix = prefix == null ? "" : prefix;
        }

        public File getFile() {
            return file;
        }

        public String getPrefix() {
            return prefix;
        }

        @Override
        public InputStream search(ClassName name) {
            Objects.requireNonNull(name);
            if (!file.isFile()) {
                return null;
            }
            String entryName = prefix + name.relativePath();
            try (ZipFile zf = new ZipFile(file)) {
                ZipEntry entry = zf.getEntry(entryName);
                if (entry != null && !entry.isDirectory()) {
                    try (InputStream in = zf.getInputStream(entry)) {
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        byte[] buf = new byte[4096];
                        int n;
                        while ((n = in.read(buf)) != -1) {
                            baos.write(buf, 0, n);
                        }
                        return new ByteArrayInputStream(baos.toByteArray());
                    }
                }
            } catch (IOException ignored) {
            }
            return null;
        }

        @Override
        public List<Root> multiReleaseRoots() {
            if (!file.isFile()) {
                return Collections.emptyList();
            }
            boolean isMultiRelease = false;
            try (ZipFile zf = new ZipFile(file)) {
                ZipEntry mfEntry = zf.getEntry(prefix + "META-INF/MANIFEST.MF");
                if (mfEntry == null && !prefix.isEmpty()) {
                    mfEntry = zf.getEntry("META-INF/MANIFEST.MF");
                }
                if (mfEntry != null) {
                    try (InputStream in = zf.getInputStream(mfEntry)) {
                        Manifest manifest = new Manifest(in);
                        isMultiRelease = "true".equalsIgnoreCase(manifest.getMainAttributes().getValue("Multi-Release"));
                    }
                }
                if (isMultiRelease) {
                    Set<String> versions = new HashSet<>();
                    Enumeration<? extends ZipEntry> en = zf.entries();
                    String verPrefix = prefix + "META-INF/versions/";
                    while (en.hasMoreElements()) {
                        ZipEntry ze = en.nextElement();
                        String entryName = ze.getName();
                        if (entryName.startsWith(verPrefix)) {
                            int start = verPrefix.length();
                            int slash = entryName.indexOf('/', start);
                            if (slash != -1) {
                                String v = entryName.substring(start, slash);
                                if (MULTI_RELEASE_VERSIONS.contains(v)) {
                                    versions.add(v);
                                }
                            }
                        }
                    }
                    if (!versions.isEmpty()) {
                        List<String> sortedVersions = new ArrayList<>(versions);
                        Collections.sort(sortedVersions, new Comparator<String>() {
                            @Override
                            public int compare(String v1, String v2) {
                                return Integer.compare(Integer.parseInt(v2), Integer.parseInt(v1));
                            }
                        });
                        List<Root> list = new LinkedList<>();
                        for (String v : sortedVersions) {
                            list.add(new ArchiveRoot(file, verPrefix + v + "/"));
                        }
                        list.add(this);
                        return Collections.unmodifiableList(list);
                    }
                }
            } catch (IOException | NullPointerException ignored) {
            }
            return Collections.singletonList(this);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ArchiveRoot)) return false;
            ArchiveRoot that = (ArchiveRoot) o;
            return file.equals(that.file) && prefix.equals(that.prefix);
        }

        @Override
        public int hashCode() {
            return Objects.hash(file, prefix);
        }

        @Override
        public String toString() {
            return "ArchiveRoot[" + file + "!" + prefix + "]";
        }
    }
}