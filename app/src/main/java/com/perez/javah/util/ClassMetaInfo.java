package com.perez.javah.util;

import com.perez.javah.ClassName;
import org.objectweb.asm.*;

import java.util.*;

public class ClassMetaInfo extends ClassVisitor {
    public final List<Constant> constants = new LinkedList<>();
    public final List<NativeMethod> methods = new LinkedList<>();
    public final Map<String, Integer> counts = new HashMap<>();
    ClassName superClassName;
    ClassName name;

    public ClassMetaInfo() {
        super(Opcodes.ASM5);
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        this.superClassName = superName == null ? null : ClassName.ofInternalName(superName);
        this.name = ClassName.ofInternalName(name);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        if ((access & Opcodes.ACC_NATIVE) != 0) {
            Integer currentCount = counts.get(name);
            counts.put(name, currentCount == null ? 1 : currentCount + 1);
            this.methods.add(NativeMethod.of(access, name, descriptor));
        }
        return null;
    }

    @Override
    public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
        if (value != null && !(value instanceof String)) {
            constants.add(Constant.of(name, value));
        }
        return null;
    }

    public boolean isOverloadMethod(NativeMethod method) {
        Objects.requireNonNull(method);
        Integer count = counts.get(method.name());
        return (count == null ? 1 : count) > 1;
    }
}