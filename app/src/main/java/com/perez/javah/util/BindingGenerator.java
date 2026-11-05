package com.perez.javah.util;

import com.perez.javah.ClassName;
import com.perez.javah.TargetLanguage;
import com.perez.javah.search.RuntimeSearchPath;
import com.perez.javah.search.SearchPath;
import org.objectweb.asm.ClassReader;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Objects;

import static com.perez.javah.util.Utils.NOOP_WRITER;
import static com.perez.javah.util.Utils.superClassOf;

public abstract class BindingGenerator {
    protected final PrintWriter errorHandle;
    protected final Iterable<SearchPath> searchPaths;
    protected final File outputDir;

    public BindingGenerator(File outputDir) {
        this(outputDir, null, null);
    }

    public BindingGenerator(File outputDir, Iterable<SearchPath> searchPaths) {
        this(outputDir, searchPaths, null);
    }

    public BindingGenerator(File outputDir, Iterable<SearchPath> searchPaths, PrintWriter errorHandle) {
        if (searchPaths == null) {
            searchPaths = Collections.singleton(RuntimeSearchPath.INSTANCE);
        }
        if (errorHandle == null) {
            errorHandle = NOOP_WRITER;
        }
        this.errorHandle = errorHandle;
        this.searchPaths = searchPaths;
        this.outputDir = outputDir;
    }

    public static BindingGenerator create(TargetLanguage language, File outputDir, Iterable<SearchPath> searchPaths, PrintWriter errorHandle) {
        Objects.requireNonNull(language);
        switch (language) {
            case RUST:
                return new RustGenerator(outputDir, searchPaths, errorHandle);
            case GO:
                return new GoGenerator(outputDir, searchPaths, errorHandle);
            case C:
            default:
                return new JNIGenerator(outputDir, searchPaths, errorHandle);
        }
    }

    public abstract String getFileExtension();

    protected abstract void writeBindings(ClassMetaInfo meta, ClassName name, PrintWriter out);

    public void generate(ClassName name) {
        Objects.requireNonNull(name);
        if (outputDir == null) {
            throw new IllegalStateException("outputDir is null");
        }
        if (outputDir.exists() && !outputDir.isDirectory()) {
            throw new IllegalArgumentException(outputDir + " is not a directory");
        }
        if (!outputDir.exists() && !outputDir.mkdirs() && !outputDir.isDirectory()) {
            errorHandle.println("error: cannot create directory " + outputDir);
            return;
        }
        StringWriter sw = new StringWriter();
        boolean generated;
        try {
            generated = generateTo(name, sw);
        } catch (Exception ex) {
            errorHandle.println("error: cannot generate bindings for " + name);
            ex.printStackTrace(errorHandle);
            return;
        }
        if (!generated) {
            return;
        }
        File op = new File(outputDir, name.mangledName() + "." + getFileExtension());
        try (OutputStreamWriter out = new OutputStreamWriter(new FileOutputStream(op), StandardCharsets.UTF_8)) {
            out.write(sw.toString());
        } catch (Exception ex) {
            errorHandle.println("error: cannot write to " + op);
            ex.printStackTrace(errorHandle);
            if (op.exists()) {
                op.delete();
            }
        }
    }

    public boolean generateTo(ClassName name, Writer writer) throws IOException {
        Objects.requireNonNull(name);
        Objects.requireNonNull(writer);
        ClassMetaInfo meta = new ClassMetaInfo();
        InputStream in = search(name);
        if (in == null) {
            errorHandle.println("Not found class " + name);
            throw new FileNotFoundException("Cannot find class byte stream for: " + name);
        }
        try {
            ClassReader reader = new ClassReader(in);
            reader.accept(meta, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        } catch (IOException e) {
            errorHandle.println("error: cannot open class file of " + name);
            e.printStackTrace(errorHandle);
            errorHandle.flush();
            throw e;
        } finally {
            try {
                in.close();
            } catch (IOException ignored) {
            }
        }

        if (meta.methods.isEmpty() && meta.constants.isEmpty()) {
            return false;
        }

        PrintWriter out = writer instanceof PrintWriter ? (PrintWriter) writer : new PrintWriter(writer);
        writeBindings(meta, name, out);
        out.flush();
        return true;
    }

    protected InputStream search(ClassName name) {
        return SearchPath.searchFrom(searchPaths, name);
    }

    protected boolean isThrowable(ClassName name) {
        if (name == null) {
            return false;
        }
        try {
            Class<?> clazz = Class.forName(name.className(), false, Thread.currentThread().getContextClassLoader());
            return Throwable.class.isAssignableFrom(clazz);
        } catch (Throwable ignored) {
        }
        switch (name.className()) {
            case "java.lang.Throwable":
            case "java.lang.Error":
            case "java.lang.Exception":
                return true;
            case "java.lang.Object":
                return false;
        }
        InputStream in = search(name);
        if (in == null) {
            return false;
        }
        try {
            return isThrowable(superClassOf(new ClassReader(in)));
        } catch (Exception ignored) {
            return false;
        } finally {
            try {
                in.close();
            } catch (IOException ignored) {
            }
        }
    }
}