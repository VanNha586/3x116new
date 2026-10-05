package avt;

import java.io.*;
import java.util.*;
import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;

public class InspectFullFishingFlow {
    public static void main(String[] args) throws Exception {
        File jarFile = new File("f:/game/3x116/backup_demo_20260817_115814/tool.jar.original");
        try (java.util.zip.ZipFile zf = new java.util.zip.ZipFile(jarFile)) {
            // Check all methods in avt.game.k
            java.util.zip.ZipEntry zeK = zf.getEntry("avt/game/k.class");
            try (InputStream is = zf.getInputStream(zeK)) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = is.read(buf)) != -1) baos.write(buf, 0, n);
                ClassReader cr = new ClassReader(baos.toByteArray());
                ClassNode cn = new ClassNode();
                cr.accept(cn, 0);
                System.out.println("=== FISHING METHODS IN avt.game.k ===");
                for (MethodNode mn : cn.methods) {
                    for (AbstractInsnNode insn = mn.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                        if (insn instanceof IntInsnNode) {
                            int val = ((IntInsnNode) insn).operand;
                            if (val == 41 || val == 45) {
                                System.out.println("k." + mn.name + " " + mn.desc + " sends packet " + val);
                            }
                        }
                    }
                }
            }
            
            // Check all methods in avt.game.N
            java.util.zip.ZipEntry zeN = zf.getEntry("avt/game/N.class");
            try (InputStream is = zf.getInputStream(zeN)) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = is.read(buf)) != -1) baos.write(buf, 0, n);
                ClassReader cr = new ClassReader(baos.toByteArray());
                ClassNode cn = new ClassNode();
                cr.accept(cn, 0);
                System.out.println("=== FISHING METHODS IN avt.game.N ===");
                for (MethodNode mn : cn.methods) {
                    for (AbstractInsnNode insn = mn.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                        if (insn instanceof IntInsnNode) {
                            int val = ((IntInsnNode) insn).operand;
                            if (val == 41 || val == 45) {
                                System.out.println("N." + mn.name + " " + mn.desc + " sends packet " + val);
                            }
                        }
                    }
                }
            }
        }
    }
}
