package com.perez.javah;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

import static com.perez.javah.util.Utils.*;

public final class ClassName {
    private final String moduleName;
    private final String className;
    private final String simpleName;
    private final String mangledName;

    private static final int MAGIC_NUMBER = 0xCAFEBABE;

    public static String parseClassName(File classFile) throws IOException {
        return parseClassName(classFile, true);
    }

    public static String parseClassName(File classFile, boolean completeClazz) throws IOException {
        try (InputStream is = new FileInputStream(classFile);
             BufferedInputStream bis = new BufferedInputStream(is);
             DataInputStream dis = new DataInputStream(bis)) {

            int magic = dis.readInt();
            if (magic != MAGIC_NUMBER) {
                throw new IllegalArgumentException("Invalid class file: magic number mismatch");
            }

            dis.readUnsignedShort(); // minor_version
            dis.readUnsignedShort(); // major_version

            int cpCount = dis.readUnsignedShort();
            int[] classIndices = new int[cpCount];
            String[] utf8Strings = new String[cpCount];

            for (int i = 1; i < cpCount; i++) {
                int tag = dis.readUnsignedByte();
                switch (tag) {
                    case 1: // CONSTANT_Utf8
                        utf8Strings[i] = dis.readUTF();
                        break;
                    case 3:  // CONSTANT_Integer
                    case 4:  // CONSTANT_Float
                    case 9:  // CONSTANT_Fieldref
                    case 10: // CONSTANT_Methodref
                    case 11: // CONSTANT_InterfaceMethodref
                    case 12: // CONSTANT_NameAndType
                    case 17: // CONSTANT_Dynamic
                    case 18: // CONSTANT_InvokeDynamic
                        dis.readInt();
                        break;
                    case 5: // CONSTANT_Long
                    case 6: // CONSTANT_Double
                        dis.readLong();
                        i++;
                        break;
                    case 7: // CONSTANT_Class
                        classIndices[i] = dis.readUnsignedShort();
                        break;
                    case 8:  // CONSTANT_String
                    case 16: // CONSTANT_MethodType
                    case 19: // CONSTANT_Module
                    case 20: // CONSTANT_Package
                        dis.readUnsignedShort();
                        break;
                    case 15: // CONSTANT_MethodHandle
                        dis.readByte();
                        dis.readUnsignedShort();
                        break;
                    default:
                        throw new IllegalArgumentException("Unsupported constant pool tag: " + tag + " at index " + i);
                }
            }

            dis.readUnsignedShort(); // access_flags
            int thisClassIndex = dis.readUnsignedShort();
            if (thisClassIndex <= 0 || thisClassIndex >= cpCount) {
                throw new IllegalStateException("Corrupted class file: invalid this_class index " + thisClassIndex);
            }

            int nameIndex = classIndices[thisClassIndex];
            if (nameIndex <= 0 || nameIndex >= cpCount || utf8Strings[nameIndex] == null) {
                throw new IllegalStateException("Corrupted class file: invalid name_index in CONSTANT_Class");
            }

            String clazzName = utf8Strings[nameIndex].replace('/', '.');
            return completeClazz ? clazzName : (clazzName.substring(clazzName.lastIndexOf('.') + 1));
        }
    }

    public static ClassName of(String moduleName, String className) {
        Objects.requireNonNull(className, "Class name is null");
        if (moduleName != null && !FULL_NAME_PATTERN.matcher(moduleName).matches()) {
            throw new IllegalArgumentException("Illegal module name: " + moduleName);
        }
        if (!FULL_NAME_PATTERN.matcher(className).matches()) {
            throw new IllegalArgumentException("Illegal class name: " + className);
        }
        return new ClassName(moduleName, className);
    }

    public static ClassName ofFullName(String fullName) {
        Objects.requireNonNull(fullName, "class name is null");
        int idx = fullName.indexOf('/');
        if (idx == -1) {
            return ClassName.of(null, fullName);
        }
        return ClassName.of(fullName.substring(0, idx), fullName.substring(idx + 1));
    }

    public static ClassName ofInternalName(String name) {
        return of(null, name.replace('/', '.'));
    }

    private ClassName(String moduleName, String className) {
        this.moduleName = moduleName;
        this.className = className;
        this.simpleName = className.substring(className.lastIndexOf('.') + 1);
        this.mangledName = mangleName(className);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ClassName)) return false;
        ClassName className1 = (ClassName) o;
        return Objects.equals(moduleName, className1.moduleName) && className.equals(className1.className);
    }

    @Override
    public int hashCode() {
        return Objects.hash(moduleName, className);
    }

    @Override
    public String toString() {
        return moduleName == null ? className : moduleName + '/' + className;
    }

    public String moduleName() {
        return moduleName;
    }

    public String className() {
        return className;
    }

    public String simpleName() {
        return simpleName;
    }

    public String mangledName() {
        return mangledName;
    }

    public String relativePath() {
        return className.replace('.', '/') + ".class";
    }
}