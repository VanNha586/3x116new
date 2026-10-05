package avt;

import java.io.*;
import java.util.*;
import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;
import jdk.internal.org.objectweb.asm.util.*;

public class DecompileFishingCallers {
    public static void main(String[] args) throws Exception {
        File jarFile = new File("f:/game/3x116/backup_demo_20260817_115814/tool.jar.original");
        try (java.util.zip.ZipFile zf = new java.util.zip.ZipFile(jarFile)) {
            // Find callers in k
            java.util.zip.ZipEntry zeK = zf.getEntry("avt/game/k.class");
            try (InputStream is = zf.getInputStream(zeK)) {
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
                            if (min.owner.equals("avt/game/k") && (min.name.equals("V") || min.name.equals("Sr") || min.name.equals("v") || min.name.equals("qf"))) {
                                System.out.println("k." + mn.name + " " + mn.desc + " calls k." + min.name);
                            }
                        }
                    }
                }
            }
            
            // Find callers in N
            java.util.zip.ZipEntry zeN = zf.getEntry("avt/game/N.class");
            try (InputStream is = zf.getInputStream(zeN)) {
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
                            if (min.owner.equals("avt/game/k") && (min.name.equals("V") || min.name.equals("Sr") || min.name.equals("v") || min.name.equals("qf"))) {
                                System.out.println("N." + mn.name + " " + mn.desc + " calls k." + min.name);
                            }
                        }
                    }
                }
            }
        }
    }
}
