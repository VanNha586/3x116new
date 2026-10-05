package avt;

import java.io.*;
import java.util.*;
import java.util.zip.*;
import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;

public class FindControllerByAsm {
    public static void main(String[] args) throws Exception {
        File jarFile = new File("f:/game/3x116/backup_demo_20260817_115814/tool.jar.original");
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(jarFile))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();
                if (name.endsWith(".class")) {
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
                    
                    boolean hasGridPane = false;
                    for (FieldNode fn : cn.fields) {
                        if (fn.desc.contains("GridPane")) {
                            System.out.println("Class " + name + " has field " + fn.name + " of type " + fn.desc);
                            hasGridPane = true;
                        }
                    }
                }
            }
        }
    }
}
