import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;
import jdk.internal.org.objectweb.asm.util.*;

import java.io.*;
import java.nio.file.*;

public class PrintNAAndKVBytecode {
    public static void main(String[] args) throws Exception {
        byte[] bytes = Files.readAllBytes(Paths.get("f:/game/3x116/backup_demo_20260817_115814/tool.jar.original"));
        java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(new ByteArrayInputStream(bytes));
        java.util.zip.ZipEntry ze;
        while ((ze = zis.getNextEntry()) != null) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = zis.read(buf)) != -1) baos.write(buf, 0, n);
            
            if (ze.getName().equals("avt/game/N.class")) {
                ClassReader cr = new ClassReader(baos.toByteArray());
                ClassNode cn = new ClassNode();
                cr.accept(cn, 0);
                for (MethodNode mn : cn.methods) {
                    if (mn.name.equals("A")) {
                        System.out.println("=== Method N.A: " + mn.name + " " + mn.desc + " ===");
                        Textifier t = new Textifier();
                        TraceMethodVisitor tmv = new TraceMethodVisitor(t);
                        mn.accept(tmv);
                        StringWriter sw = new StringWriter();
                        t.print(new PrintWriter(sw));
                        System.out.println(sw.toString());
                    }
                }
            } else if (ze.getName().equals("avt/game/l.class")) {
                ClassReader cr = new ClassReader(baos.toByteArray());
                ClassNode cn = new ClassNode();
                cr.accept(cn, 0);
                for (MethodNode mn : cn.methods) {
                    if (mn.name.equals("I")) {
                        System.out.println("=== Method l.I: " + mn.name + " " + mn.desc + " ===");
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
