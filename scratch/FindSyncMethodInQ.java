package avt;

import java.io.*;
import java.util.*;
import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;
import jdk.internal.org.objectweb.asm.util.*;

public class FindSyncMethodInQ {
    public static void main(String[] args) throws Exception {
        File jarFile = new File("f:/game/3x116/backup_demo_20260817_115814/tool.jar.original");
        try (java.util.zip.ZipFile zf = new java.util.zip.ZipFile(jarFile)) {
            java.util.zip.ZipEntry ze = zf.getEntry("avt/Q.class");
            try (InputStream is = zf.getInputStream(ze)) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = is.read(buf)) != -1) baos.write(buf, 0, n);
                ClassReader cr = new ClassReader(baos.toByteArray());
                ClassNode cn = new ClassNode();
                cr.accept(cn, 0);
                for (MethodNode mn : cn.methods) {
                    boolean usesBoth = false;
                    boolean usesK = false;
                    boolean usesN = false;
                    for (AbstractInsnNode insn = mn.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                        if (insn instanceof TypeInsnNode) {
                            TypeInsnNode tin = (TypeInsnNode) insn;
                            if (tin.desc.equals("avt/game/k")) usesK = true;
                            if (tin.desc.equals("avt/N")) usesN = true;
                        }
                        if (insn instanceof FieldInsnNode) {
                            FieldInsnNode fin = (FieldInsnNode) insn;
                            if (fin.owner.equals("avt/game/k")) usesK = true;
                            if (fin.owner.equals("avt/N")) usesN = true;
                        }
                    }
                    if (usesK && usesN) {
                        System.out.println("=== SYNC METHOD: " + mn.name + " " + mn.desc + " ===");
                        Textifier textifier = new Textifier();
                        TraceMethodVisitor tmv = new TraceMethodVisitor(textifier);
                        mn.accept(tmv);
                        StringWriter sw = new StringWriter();
                        PrintWriter pw = new PrintWriter(sw);
                        textifier.print(pw);
                        pw.flush();
                        System.out.println(sw.toString());
                    }
                }
            }
        }
    }
}
