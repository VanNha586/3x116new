import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;
import jdk.internal.org.objectweb.asm.util.*;

import java.io.*;
import java.nio.file.*;

public class DumpXandKOverloads {
    public static void main(String[] args) throws Exception {
        String jar = "f:/game/3x116/backup_demo_20260817_115814/tool.jar.original";
        dump(jar, "avt/network/x.class", new String[]{"X"});
        listMethods(jar, "avt/game/k.class");
    }

    static void listMethods(String jarPath, String entry) throws Exception {
        byte[] bytes = readEntry(jarPath, entry);
        ClassReader cr = new ClassReader(bytes);
        ClassNode cn = new ClassNode();
        cr.accept(cn, 0);
        System.out.println("=== methods of " + entry + " named V or Sr or Q ===");
        for (MethodNode mn : cn.methods) {
            if (mn.name.equals("V") || mn.name.equals("Sr") || mn.name.equals("Q")) {
                System.out.println("  " + mn.name + " " + mn.desc);
            }
        }
    }

    static void dump(String jarPath, String entry, String[] methods) throws Exception {
        byte[] bytes = readEntry(jarPath, entry);
        ClassReader cr = new ClassReader(bytes);
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

    static byte[] readEntry(String jarPath, String entryName) throws Exception {
        byte[] bytes = Files.readAllBytes(Paths.get(jarPath));
        java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(new ByteArrayInputStream(bytes));
        java.util.zip.ZipEntry ze;
        while ((ze = zis.getNextEntry()) != null) {
            if (ze.getName().equals(entryName)) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = zis.read(buf)) != -1) baos.write(buf, 0, n);
                return baos.toByteArray();
            }
        }
        throw new FileNotFoundException(entryName);
    }
}
