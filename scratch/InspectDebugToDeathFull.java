package avt;

import java.io.*;
import java.util.*;
import java.util.zip.*;
import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;

public class InspectDebugToDeathFull {
    public static void main(String[] args) throws Exception {
        File jarFile = new File("f:/game/3x116/backup_demo_20260817_115814/tool.jar.original");
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(jarFile))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().equals("avt/DebugToDeath.class")) {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = zis.read(buf)) != -1) {
                        baos.write(buf, 0, n);
                    }
                    byte[] bytes = baos.toByteArray();
                    ClassReader cr = new ClassReader(bytes);
                    ClassNode cn = new ClassNode();
                    cr.accept(cn, 0);
                    
                    System.out.println("=== FIELDS of DebugToDeath ===");
                    for (FieldNode fn : cn.fields) {
                        System.out.println(fn.name + " : " + fn.desc);
                    }
                    
                    System.out.println("=== METHODS of DebugToDeath ===");
                    for (MethodNode mn : cn.methods) {
                        System.out.println("Method: " + mn.name + " " + mn.desc);
                    }
                }
            }
        }
    }
}
