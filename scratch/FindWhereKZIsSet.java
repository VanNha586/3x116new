package avt;

import java.io.*;
import java.util.*;
import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;

public class FindWhereKZIsSet {
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
                                if (insn instanceof FieldInsnNode) {
                                    FieldInsnNode fin = (FieldInsnNode) insn;
                                    if (fin.owner.equals("avt/game/k") && fin.name.equals("z") && fin.getOpcode() == Opcodes.PUTFIELD) {
                                        System.out.println("k.z set in " + ze.getName() + " -> " + mn.name + " " + mn.desc);
                                    }
                                    if (fin.owner.equals("avt/N") && fin.name.equals("i") && fin.getOpcode() == Opcodes.PUTFIELD) {
                                        System.out.println("N.i set in " + ze.getName() + " -> " + mn.name + " " + mn.desc);
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
