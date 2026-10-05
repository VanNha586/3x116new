import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;

import java.io.*;
import java.nio.file.*;

public class Find1499 {
    public static void main(String[] args) throws Exception {
        byte[] bytes = Files.readAllBytes(Paths.get("f:/game/3x116/backup_demo_20260817_115814/tool.jar.original"));
        java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(new ByteArrayInputStream(bytes));
        java.util.zip.ZipEntry ze;
        while ((ze = zis.getNextEntry()) != null) {
            if (ze.getName().equals("avt/Q.class")) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = zis.read(buf)) != -1) baos.write(buf, 0, n);
                
                ClassReader cr = new ClassReader(baos.toByteArray());
                ClassNode cn = new ClassNode();
                cr.accept(cn, 0);
                
                for (MethodNode mn : cn.methods) {
                    if (mn.name.equals("Xy")) {
                        for (AbstractInsnNode insn = mn.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                            if (insn.getOpcode() == Opcodes.INVOKESTATIC) {
                                MethodInsnNode minsn = (MethodInsnNode) insn;
                                System.out.println("Xy calls: " + minsn.owner + "." + minsn.name + minsn.desc);
                            }
                        }
                    }
                }
            }
        }
    }
}
