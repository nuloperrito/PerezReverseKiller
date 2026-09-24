package com.perez.util;

import org.jetbrains.java.decompiler.main.Fernflower;
import org.jetbrains.java.decompiler.main.decompiler.BaseDecompiler;
import org.jetbrains.java.decompiler.main.decompiler.PrintStreamLogger;
import org.jetbrains.java.decompiler.main.extern.IBytecodeProvider;
import org.jetbrains.java.decompiler.main.extern.IFernflowerLogger;
import org.jetbrains.java.decompiler.main.extern.IFernflowerPreferences;
import org.jetbrains.java.decompiler.main.extern.IResultSaver;
import org.jetbrains.java.decompiler.struct.StructClass;
import org.jetbrains.java.decompiler.struct.StructContext;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public class VineflowerJarDecompiler {

    // 51-byte valid minimal Java 8 classfile bytecode (class A extends java.lang.Object)
    private static final byte[] FALLBACK_MINIMAL_CLASS = new byte[]{
            (byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE,
            0x00, 0x00, 0x00, 0x34,
            0x00, 0x05,
            0x07, 0x00, 0x02,
            0x01, 0x00, 0x01, 0x41,
            0x07, 0x00, 0x04,
            0x01, 0x00, 0x10, 0x6A, 0x61, 0x76, 0x61, 0x2F, 0x6C, 0x61, 0x6E, 0x67, 0x2F, 0x4F, 0x62, 0x6A, 0x65, 0x63, 0x74,
            0x00, 0x21,
            0x00, 0x01,
            0x00, 0x03,
            0x00, 0x00,
            0x00, 0x00,
            0x00, 0x00,
            0x00, 0x00
    };

    public static void decompile(File inputJar, File outputZip) throws IOException {
        decompile(inputJar, outputZip, getDefaultOptions(), new PrintStreamLogger(System.out));
    }

    public static void decompile(File inputJar, File outputZip, Map<String, Object> customOptions, IFernflowerLogger logger) throws IOException {
        if (!inputJar.exists() || !inputJar.isFile()) {
            throw new IOException("Input JAR file does not exist: " + inputJar.getAbsolutePath());
        }

        IBytecodeProvider bytecodeProvider = new FileBytecodeProvider();

        try (ZipArchiveResultSaver resultSaver = new ZipArchiveResultSaver(outputZip)) {
            BaseDecompiler decompiler = new BaseDecompiler(bytecodeProvider, resultSaver, customOptions, logger);

            // Inject dummy StructClass sentinel before parsing context to prevent NPE on Android
            patchAndroidSentinel(decompiler, inputJar);

            decompiler.addSource(inputJar);
            decompiler.decompileContext();
        }
    }

    public static Map<String, Object> getDefaultOptions() {
        Map<String, Object> options = new HashMap<>();
        options.put(IFernflowerPreferences.DECOMPILE_INNER, "1");
        options.put(IFernflowerPreferences.BYTECODE_SOURCE_MAPPING, "1");
        options.put(IFernflowerPreferences.REMOVE_BRIDGE, "1");
        options.put(IFernflowerPreferences.REMOVE_SYNTHETIC, "1");
        options.put(IFernflowerPreferences.DECOMPILE_GENERIC_SIGNATURES, "1");
        options.put(IFernflowerPreferences.THREADS, String.valueOf(Math.max(1, Runtime.getRuntime().availableProcessors())));
        return options;
    }

    /**
     * Injects a valid StructClass sentinel into StructContext to bypass Android's lack of .class files.
     */
    private static void patchAndroidSentinel(BaseDecompiler decompiler, File inputJar) {
        try {
            // Resolve Fernflower engine from BaseDecompiler
            Field engineField = null;
            Class<?> decompilerClass = decompiler.getClass();
            while (decompilerClass != null && engineField == null) {
                try {
                    engineField = decompilerClass.getDeclaredField("engine");
                } catch (NoSuchFieldException ignored) {
                    decompilerClass = decompilerClass.getSuperclass();
                }
            }
            if (engineField == null) return;
            engineField.setAccessible(true);
            Fernflower engine = (Fernflower) engineField.get(decompiler);

            // Resolve StructContext from Fernflower
            Field contextField = null;
            Class<?> engineClass = engine.getClass();
            while (engineClass != null && contextField == null) {
                try {
                    contextField = engineClass.getDeclaredField("structContext");
                } catch (NoSuchFieldException ignored) {
                    engineClass = engineClass.getSuperclass();
                }
            }
            if (contextField == null) return;
            contextField.setAccessible(true);
            StructContext structContext = (StructContext) contextField.get(engine);

            // Locate the StructClass sentinel field in StructContext
            Field sentinelField = null;
            for (Field f : StructContext.class.getDeclaredFields()) {
                if (StructClass.class.isAssignableFrom(f.getType())) {
                    sentinelField = f;
                    sentinelField.setAccessible(true);
                    break;
                }
            }
            if (sentinelField == null) return;

            Object existingSentinel = Modifier.isStatic(sentinelField.getModifiers())
                    ? sentinelField.get(null)
                    : sentinelField.get(structContext);
            if (existingSentinel != null) return;

            // Obtain valid bytecode bytes
            byte[] rawBytecode = extractClassBytecode(inputJar);
            if (rawBytecode == null) {
                rawBytecode = FALLBACK_MINIMAL_CLASS;
            }

            // Instantiate dummy StructClass
            StructClass sentinelInstance = createDummyStructClass(rawBytecode, structContext);

            // Assign back to sentinel field
            if (Modifier.isStatic(sentinelField.getModifiers())) {
                sentinelField.set(null, sentinelInstance);
            } else {
                sentinelField.set(structContext, sentinelInstance);
            }
        } catch (Throwable t) {
            System.err.println("[VineflowerJarDecompiler] Failed to patch Android sentinel: " + t.getMessage());
        }
    }

    private static byte[] extractClassBytecode(File jarFile) {
        try (ZipFile zip = new ZipFile(jarFile)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (!entry.isDirectory() && entry.getName().endsWith(".class")) {
                    try (InputStream is = zip.getInputStream(entry)) {
                        return toByteArray(is);
                    }
                }
            }
        } catch (IOException ignored) {
        }
        return null;
    }

    private static StructClass createDummyStructClass(byte[] classBytes, StructContext context) throws Exception {
        Class<?> streamClass = Class.forName("org.jetbrains.java.decompiler.util.DataInputFullStream");
        Constructor<?> streamCtor = streamClass.getConstructor(byte[].class);
        Object stream = streamCtor.newInstance((Object) classBytes);

        // Attempt static factory methods: StructClass.create(...)
        for (Method m : StructClass.class.getDeclaredMethods()) {
            if (m.getName().equals("create") && Modifier.isStatic(m.getModifiers())) {
                m.setAccessible(true);
                Class<?>[] params = m.getParameterTypes();
                if (params.length == 2 && params[0] == streamClass && params[1] == boolean.class) {
                    return (StructClass) m.invoke(null, stream, false);
                } else if (params.length == 3 && params[0] == streamClass && params[1] == boolean.class && params[2] == StructContext.class) {
                    return (StructClass) m.invoke(null, stream, false, context);
                }
            }
        }

        // Fallback to constructors
        for (Constructor<?> ctor : StructClass.class.getDeclaredConstructors()) {
            ctor.setAccessible(true);
            Class<?>[] params = ctor.getParameterTypes();
            if (params.length == 2 && params[0] == streamClass && params[1] == boolean.class) {
                return (StructClass) ctor.newInstance(stream, false);
            } else if (params.length == 3 && params[0] == streamClass && params[1] == boolean.class && params[2] == StructContext.class) {
                return (StructClass) ctor.newInstance(stream, false, context);
            }
        }

        throw new IllegalStateException("Failed to construct StructClass instance");
    }

    private static byte[] toByteArray(InputStream in) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] data = new byte[8192];
        int bytesRead;
        while ((bytesRead = in.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, bytesRead);
        }
        return buffer.toByteArray();
    }

    private static class FileBytecodeProvider implements IBytecodeProvider {
        @Override
        public byte[] getBytecode(String externalPath, String internalPath) throws IOException {
            File file = new File(externalPath);
            if (internalPath == null) {
                try (InputStream in = new FileInputStream(file)) {
                    return toByteArray(in);
                }
            }

            try (ZipFile zipFile = new ZipFile(file)) {
                ZipEntry entry = zipFile.getEntry(internalPath);
                if (entry == null) {
                    throw new IOException("Entry not found: " + internalPath + " in " + externalPath);
                }
                try (InputStream in = zipFile.getInputStream(entry)) {
                    return toByteArray(in);
                }
            }
        }
    }

    private static class ZipArchiveResultSaver implements IResultSaver, AutoCloseable {
        private ZipOutputStream zos;
        private final Set<String> writtenEntries = Collections.synchronizedSet(new HashSet<String>());
        private boolean isClosed = false;

        public ZipArchiveResultSaver(File targetZipFile) throws IOException {
            File parent = targetZipFile.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            this.zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(targetZipFile)));
        }

        private synchronized void writeEntry(String entryName, byte[] data, long time) {
            if (isClosed || zos == null || !writtenEntries.add(entryName)) {
                return;
            }
            try {
                ZipEntry entry = new ZipEntry(entryName);
                if (time > 0) {
                    entry.setTime(time);
                }
                zos.putNextEntry(entry);
                if (data != null && data.length > 0) {
                    zos.write(data);
                }
                zos.closeEntry();
            } catch (IOException e) {
                throw new RuntimeException("Failed writing entry: " + entryName, e);
            }
        }

        @Override
        public void saveFolder(String path) {}

        @Override
        public void copyFile(String source, String path, String entryName) {}

        @Override
        public void saveClassFile(String path, String qualifiedName, String entryName, String content, int[] mapping) {
            byte[] data = content != null ? content.getBytes(StandardCharsets.UTF_8) : new byte[0];
            writeEntry(entryName, data, -1);
        }

        @Override
        public synchronized void createArchive(String path, String archiveName, Manifest manifest) {
            if (manifest != null) {
                try {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    manifest.write(baos);
                    writeEntry("META-INF/MANIFEST.MF", baos.toByteArray(), -1);
                } catch (IOException e) {
                    throw new RuntimeException("Failed to serialize Manifest", e);
                }
            }
        }

        @Override
        public void saveDirEntry(String path, String archiveName, String entryName) {
            if (entryName != null && !entryName.isEmpty()) {
                String dirPath = entryName.endsWith("/") ? entryName : entryName + "/";
                writeEntry(dirPath, null, -1);
            }
        }

        @Override
        public void copyEntry(String sourceArchiveAddress, String path, String archiveName, String entryName) {
            if (isClosed || writtenEntries.contains(entryName)) {
                return;
            }

            try (ZipFile srcZip = new ZipFile(sourceArchiveAddress)) {
                ZipEntry srcEntry = srcZip.getEntry(entryName);
                if (srcEntry != null && !srcEntry.isDirectory()) {
                    try (InputStream in = srcZip.getInputStream(srcEntry)) {
                        writeEntry(entryName, toByteArray(in), srcEntry.getTime());
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException("Failed to copy non-class resource entry: " + entryName, e);
            }
        }

        @Override
        public void saveClassEntry(String path, String archiveName, String qualifiedName, String entryName, String content) {
            byte[] data = content != null ? content.getBytes(StandardCharsets.UTF_8) : new byte[0];
            writeEntry(entryName, data, -1);
        }

        @Override
        public synchronized void closeArchive(String path, String archiveName) {
            try {
                close();
            } catch (IOException e) {
                throw new RuntimeException("Failed to close archive: " + archiveName, e);
            }
        }

        @Override
        public synchronized void close() throws IOException {
            if (isClosed) {
                return;
            }
            isClosed = true;

            if (zos != null) {
                try {
                    zos.finish();
                } finally {
                    try {
                        zos.close();
                    } finally {
                        zos = null;
                    }
                }
            }
        }
    }
}