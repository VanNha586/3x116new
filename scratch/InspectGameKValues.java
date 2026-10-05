package avt;

import java.io.*;
import java.util.*;
import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;

public class InspectGameKValues {
    public static void main(String[] args) throws Exception {
        File jarFile = new File("f:/game/3x116/backup_demo_20260817_115814/tool.jar.original");
        try (java.util.zip.ZipFile zf = new java.util.zip.ZipFile(jarFile)) {
            java.util.zip.ZipEntry ze = zf.getEntry("avt/game/k.class");
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
                        if (insn instanceof LdcInsnNode) {
                            LdcInsnNode ldc = (LdcInsnNode) insn;
                            if (ldc.cst != null && ldc.cst.toString().contains("Nhân vật") || ldc.cst != null && ldc.cst.toString().contains("Cấp độ chính") || ldc.cst != null && ldc.cst.toString().contains("Cập nhật xu")) {
                                System.out.println("Log in method: " + mn.name + " " + mn.desc + " -> " + ldc.cst);
                            }
                        }
                    }
                }
            }
        }
    }
}
