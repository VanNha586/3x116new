package avt;

import java.io.*;
import java.util.*;
import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;

public class FindCallersOfKM {
    public static void main(String[] args) throws Exception {
        File jarFile = new File("f:/game/3x116/backup_demo_20260817_115814/tool.jar.original");
        try (java.util.zip.ZipFile zf = new java.util.zip.ZipFile(jarFile)) {
            Enumeration<? extends java.util.zip.ZipEntry> entries = zf.entries();
            while (entries.hasMoreElements()) {
                java.util.zip.ZipEntry ze = entries.nextElement();
                if (ze.getName().endsWith(".class")) {
                    try (InputStream is = zf.getInputStream(ze)) {
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        byte[] buf = new byte[8192];
                        int n;
                        while ((n = is.read(buf)) != -1) baos.write(buf, 0, n);
                        ClassReader cr = new ClassReader(baos.toByteArray());
                        ClassNode cn = new ClassNode();
                        cr.accept(cn, 0);
                        for (MethodNode mn : cn.methods) {
                            for (AbstractInsnNode insn = mn.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                                if (insn instanceof MethodInsnNode) {
                                    MethodInsnNode min = (MethodInsnNode) insn;
                                    if (min.owner.equals("avt/game/k") && min.name.equals("m") && min.desc.equals("([Ljava/lang/Object;)V")) {
                                        System.out.println("Caller of k.m: " + ze.getName() + " -> " + mn.name + " " + mn.desc);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
