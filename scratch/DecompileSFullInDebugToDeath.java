package avt;

import java.io.*;
import java.util.*;
import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;
import jdk.internal.org.objectweb.asm.util.*;

public class DecompileSFullInDebugToDeath {
    public static void main(String[] args) throws Exception {
        File jarFile = new File("f:/game/3x116/backup_demo_20260817_115814/tool.jar.original");
        try (java.util.zip.ZipFile zf = new java.util.zip.ZipFile(jarFile)) {
            java.util.zip.ZipEntry ze = zf.getEntry("avt/DebugToDeath.class");
            try (InputStream is = zf.getInputStream(ze)) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = is.read(buf)) != -1) baos.write(buf, 0, n);
                ClassReader cr = new ClassReader(baos.toByteArray());
                ClassNode cn = new ClassNode();
                cr.accept(cn, 0);
                for (MethodNode mn : cn.methods) {
                    if (mn.name.equals("s") && mn.desc.contains("Node")) {
                        Textifier textifier = new Textifier();
                        TraceMethodVisitor tmv = new TraceMethodVisitor(textifier);
                        mn.accept(tmv);
                        StringWriter sw = new StringWriter();
                        PrintWriter pw = new PrintWriter(sw);
                        textifier.print(pw);
                        pw.flush();
                        String full = sw.toString();
                        String[] lines = full.split("\n");
                        for (int i = 0; i < Math.min(100, lines.length); i++) {
                            System.out.println(lines[i]);
                        }
                    }
                }
            }
        }
    }
}
