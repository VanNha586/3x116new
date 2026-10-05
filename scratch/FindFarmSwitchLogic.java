import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;

import java.io.*;
import java.nio.file.*;

public class FindFarmSwitchLogic {
    public static void main(String[] args) throws Exception {
        byte[] bytes = Files.readAllBytes(Paths.get("f:/game/3x116/backup_demo_20260817_115814/tool.jar.original"));
        java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(new ByteArrayInputStream(bytes));
        java.util.zip.ZipEntry ze;
        while ((ze = zis.getNextEntry()) != null) {
            if (ze.getName().equals("avt/game/N.class")) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = zis.read(buf)) != -1) baos.write(buf, 0, n);
                
                ClassReader cr = new ClassReader(baos.toByteArray());
                ClassNode cn = new ClassNode();
                cr.accept(cn, 0);
                
                for (MethodNode mn : cn.methods) {
                    for (AbstractInsnNode insn = mn.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                        if (insn.getOpcode() == Opcodes.INVOKEVIRTUAL) {
                            MethodInsnNode minsn = (MethodInsnNode) insn;
                            if (minsn.name.equals("W") && minsn.owner.equals("avt/network/x")) {
                                System.out.println("N." + mn.name + " calls disconnect/close network!");
                            }
                        }
                    }
                }
            }
        }
    }
}
