import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

public class FindAllVPackets {
    public static void main(String[] args) throws Exception {
        String jar = args.length > 0 ? args[0] : "f:/game/3x116/backup_demo_20260817_115814/tool.jar.original";
        byte[] bytes = Files.readAllBytes(Paths.get(jar));
        ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(bytes));
        ZipEntry ze;
        while ((ze = zis.getNextEntry()) != null) {
            String name = ze.getName();
            if (!name.endsWith(".class")) continue;
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = zis.read(buf)) != -1) baos.write(buf, 0, n);
            try {
                ClassReader cr = new ClassReader(baos.toByteArray());
                ClassNode cn = new ClassNode();
                cr.accept(cn, 0);
                for (MethodNode mn : cn.methods) {
                    if (mn.instructions == null) continue;
                    AbstractInsnNode[] insns = mn.instructions.toArray();
                    for (int i = 0; i < insns.length; i++) {
                        AbstractInsnNode insn = insns[i];
                        if (insn.getOpcode() == Opcodes.INVOKESPECIAL) {
                            MethodInsnNode mi = (MethodInsnNode) insn;
                            if (mi.owner.equals("avt/network/v") && mi.name.equals("<init>")) {
                                // walk backwards for int constant (may be pushed via a(int,long))
                                Integer val = findIntBefore(insns, i);
                                String cls = name.replace('/', '.').replaceAll("\\.class$", "");
                                System.out.println(cls + " :: " + mn.name + mn.desc + " -> new avt/network/v(" + (val == null ? "?" : val) + ")");
                            }
                        }
                    }
                }
            } catch (Throwable t) {}
        }
    }

    static Integer findIntBefore(AbstractInsnNode[] insns, int idx) {
        // Pattern 1: SIPUSH/LDC int directly before NEW/v init
        for (int j = idx - 1; j >= Math.max(0, idx - 12); j--) {
            AbstractInsnNode a = insns[j];
            int op = a.getOpcode();
            if (op == Opcodes.SIPUSH) return ((IntInsnNode) a).operand;
            if (op == Opcodes.BIPUSH) return ((IntInsnNode) a).operand;
            if (op == Opcodes.LDC && ((LdcInsnNode) a).cst instanceof Integer) return (Integer) ((LdcInsnNode) a).cst;
            if (op >= Opcodes.ICONST_M1 && op <= Opcodes.ICONST_5) return op - Opcodes.ICONST_0;
        }
        return null;
    }
}
