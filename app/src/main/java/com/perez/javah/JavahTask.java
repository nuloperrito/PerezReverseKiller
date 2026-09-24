package com.perez.javah;

import com.perez.javah.search.ClassPath;
import com.perez.javah.search.ModulePath;
import com.perez.javah.search.RuntimeSearchPath;
import com.perez.javah.search.SearchPath;
import com.perez.javah.util.BindingGenerator;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class JavahTask {
    private final List<SearchPath> searchPaths = new LinkedList<>();
    private File outputDir;
    private File outputFile;
    private PrintWriter errorHandle = new PrintWriter(System.err, true);
    private final List<ClassName> classes = new LinkedList<>();

    // Default to C language only
    private final Set<TargetLanguage> targetLanguages = EnumSet.of(TargetLanguage.C);

    public void run() {
        if (outputDir == null && outputFile == null) {
            throw new AssertionError("Both outputDir and outputFile are null");
        }
        if (targetLanguages.isEmpty()) {
            throw new IllegalStateException("No target language specified");
        }

        // Boundary condition: multiple target languages cannot be concatenated into a single output file
        if (outputFile != null) {
            if (targetLanguages.size() > 1) {
                throw new IllegalArgumentException(
                        "Cannot generate multiple target languages into a single outputFile: " + outputFile);
            }
            File parent = outputFile.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs() && !parent.isDirectory()) {
                errorHandle.println("error: cannot create directory " + parent);
                return;
            }
            TargetLanguage lang = targetLanguages.iterator().next();
            BindingGenerator generator = BindingGenerator.create(lang, outputDir, searchPaths, errorHandle);
            try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(outputFile), StandardCharsets.UTF_8)) {
                for (ClassName cls : classes) {
                    generator.generateTo(cls, writer);
                }
            } catch (Exception ex) {
                ex.printStackTrace(errorHandle);
            }
            return;
        }

        // Generate files in outputDir for each target language
        for (TargetLanguage lang : targetLanguages) {
            BindingGenerator generator = BindingGenerator.create(lang, outputDir, searchPaths, errorHandle);
            for (ClassName cls : classes) {
                try {
                    generator.generate(cls);
                } catch (Exception ex) {
                    ex.printStackTrace(errorHandle);
                }
            }
        }
    }

    public Set<TargetLanguage> getTargetLanguages() {
        return Collections.unmodifiableSet(targetLanguages);
    }

    /**
     * Resets target languages and sets a single target language.
     */
    public void setTargetLanguage(TargetLanguage targetLanguage) {
        Objects.requireNonNull(targetLanguage, "targetLanguage must not be null");
        this.targetLanguages.clear();
        this.targetLanguages.add(targetLanguage);
    }

    /**
     * Resets target languages and sets multiple target languages.
     */
    public void setTargetLanguages(TargetLanguage... languages) {
        Objects.requireNonNull(languages, "languages must not be null");
        if (languages.length == 0) {
            throw new IllegalArgumentException("Target languages must not be empty");
        }
        this.targetLanguages.clear();
        for (TargetLanguage lang : languages) {
            this.targetLanguages.add(Objects.requireNonNull(lang));
        }
    }

    /**
     * Resets target languages and sets multiple target languages from an Iterable.
     */
    public void setTargetLanguages(Iterable<TargetLanguage> languages) {
        Objects.requireNonNull(languages, "languages must not be null");
        this.targetLanguages.clear();
        for (TargetLanguage lang : languages) {
            this.targetLanguages.add(Objects.requireNonNull(lang));
        }
        if (this.targetLanguages.isEmpty()) {
            throw new IllegalArgumentException("Target languages must not be empty");
        }
    }

    /**
     * Appends a target language to the current generation set.
     */
    public void addTargetLanguage(TargetLanguage targetLanguage) {
        Objects.requireNonNull(targetLanguage, "targetLanguage must not be null");
        this.targetLanguages.add(targetLanguage);
    }

    /**
     * Appends multiple target languages to the current generation set.
     */
    public void addTargetLanguages(TargetLanguage... languages) {
        Objects.requireNonNull(languages, "languages must not be null");
        for (TargetLanguage lang : languages) {
            this.targetLanguages.add(Objects.requireNonNull(lang));
        }
    }

    public boolean hasClasses() {
        return !classes.isEmpty();
    }

    public void addClass(File classFile) throws IOException {
        Objects.requireNonNull(classFile);
        String fullName = ClassName.parseClassName(classFile, true);
        addClass(fullName);
        addClassPath(classFile);
    }

    public void addClass(ClassName name) {
        Objects.requireNonNull(name);
        classes.add(name);
    }

    public void addClass(String name) {
        Objects.requireNonNull(name);
        classes.add(ClassName.ofFullName(name));
    }

    public void addClasses(Iterable<String> i) {
        Objects.requireNonNull(i);
        for (String c : i) {
            classes.add(ClassName.ofFullName(c));
        }
    }

    public void addRuntimeSearchPath() {
        searchPaths.add(RuntimeSearchPath.INSTANCE);
    }

    public void addSearchPath(SearchPath searchPath) {
        Objects.requireNonNull(searchPath);
        searchPaths.add(searchPath);
    }

    public void addClassPath(File classPath) {
        Objects.requireNonNull(classPath);
        searchPaths.add(new ClassPath(classPath));
    }

    public void addModulePath(File modulePath) {
        Objects.requireNonNull(modulePath);
        searchPaths.add(new ModulePath(modulePath));
    }

    public File getOutputDir() {
        return outputDir;
    }

    public void setOutputDir(File outputDir) {
        this.outputDir = outputDir;
    }

    public File getOutputFile() {
        return outputFile;
    }

    public void setOutputFile(File outputFile) {
        this.outputFile = outputFile;
    }

    public PrintWriter getErrorHandle() {
        return errorHandle;
    }

    public void setErrorHandle(Writer errorHandle) {
        if (errorHandle instanceof PrintWriter || errorHandle == null) {
            this.errorHandle = (PrintWriter) errorHandle;
        } else {
            this.errorHandle = new PrintWriter(errorHandle);
        }
    }
}