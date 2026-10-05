import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;
import jdk.internal.org.objectweb.asm.util.*;

import java.io.*;
import java.nio.file.*;

public class DumpFishingHandlers {
    public static void main(String[] args) throws Exception {
        String jar = "f:/game/3x116/backup_demo_20260817_115814/tool.jar.original";
        dump(jar, "avt/network/x.class", new String[]{"X", "a", "r"});
        dump(jar, "avt/game/k.class", new String[]{"o", "g", "k"});
    }

    static void scanAbUsage(String jarPath, String entry, int idx, String arrName) throws Exception {
        byte[] bytes = Files.readAllBytes(Paths.get(jarPath));
        java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(new ByteArrayInputStream(bytes));
        java.util.zip.ZipEntry ze;
        while ((ze = zis.getNextEntry()) != null) {
            if (ze.getName().equals(entry)) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = zis.read(buf)) != -1) baos.write(buf, 0, n);
                ClassReader cr = new ClassReader(baos.toByteArray());
                ClassNode cn = new ClassNode();
                cr.accept(cn, 0);
                for (MethodNode mn : cn.methods) {
                    if (mn.instructions == null) continue;
                    for (AbstractInsnNode insn : mn.instructions.toArray()) {
                        if (insn.getOpcode() == Opcodes.BIPUSH && ((IntInsnNode) insn).operand == idx
                                || insn.getOpcode() == Opcodes.SIPUSH && ((IntInsnNode) insn).operand == idx) {
                            // verify next refs the string array
                            System.out.println(">>> " + entry + " :: " + mn.name + mn.desc + " uses " + arrName + "[" + idx + "]");
                            break;
                        }
                    }
                }
            }
        }
    }

    static void dump(String jarPath, String entry, String[] methods) throws Exception {
        byte[] bytes = Files.readAllBytes(Paths.get(jarPath));
        java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(new ByteArrayInputStream(bytes));
        java.util.zip.ZipEntry ze;
        while ((ze = zis.getNextEntry()) != null) {
            if (ze.getName().equals(entry)) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = zis.read(buf)) != -1) baos.write(buf, 0, n);
                ClassReader cr = new ClassReader(baos.toByteArray());
                ClassNode cn = new ClassNode();
                cr.accept(cn, 0);
                for (MethodNode mn : cn.methods) {
                    for (String m : methods) {
                        if (mn.name.equals(m)) {
                            System.out.println("=== " + entry + " :: " + mn.name + " " + mn.desc + " ===");
                            Textifier t = new Textifier();
                            TraceMethodVisitor tmv = new TraceMethodVisitor(t);
                            mn.accept(tmv);
                            StringWriter sw = new StringWriter();
                            t.print(new PrintWriter(sw));
                            System.out.println(sw.toString());
                        }
                    }
                }
            }
        }
    }
}
