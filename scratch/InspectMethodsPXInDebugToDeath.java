package avt;

import java.io.*;
import java.util.*;
import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;

public class InspectMethodsPXInDebugToDeath {
    public static void main(String[] args) throws Exception {
        File jarFile = new File("f:/game/3x116/backup_demo_20260817_115814/tool.jar.original");
        byte[] bytes = null;
        try (java.util.zip.ZipFile zf = new java.util.zip.ZipFile(jarFile)) {
            java.util.zip.ZipEntry ze = zf.getEntry("avt/DebugToDeath.class");
            try (InputStream is = zf.getInputStream(ze)) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = is.read(buf)) != -1) baos.write(buf, 0, n);
                bytes = baos.toByteArray();
            }
        }
        
        ClassReader cr = new ClassReader(bytes);
        ClassNode cn = new ClassNode();
        cr.accept(cn, 0);
        
        for (MethodNode mn : cn.methods) {
            if ((mn.name.equals("P") || mn.name.equals("X") || mn.name.equals("J") || mn.name.equals("B")) && mn.desc.equals("([Ljava/lang/Object;)V")) {
                System.out.println("\n=== Method: " + mn.name + " " + mn.desc + " ===");
                for (AbstractInsnNode insn = mn.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                    if (insn instanceof MethodInsnNode) {
                        MethodInsnNode min = (MethodInsnNode) insn;
                        System.out.println("  call " + min.owner + "." + min.name + min.desc);
                    } else if (insn instanceof FieldInsnNode) {
                        FieldInsnNode fin = (FieldInsnNode) insn;
                        System.out.println("  field " + fin.owner + "." + fin.name + " " + fin.desc);
                    } else if (insn instanceof LdcInsnNode) {
                        LdcInsnNode ldc = (LdcInsnNode) insn;
                        System.out.println("  ldc " + ldc.cst);
                    }
                }
            }
        }
    }
}
