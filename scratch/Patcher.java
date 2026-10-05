import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

public class Patcher {
    public static void main(String[] args) throws Exception {
        File jarFile = new File("f:/game/3x116/backup_demo_20260817_115814/tool.jar.original");
        File tempJar = new File("f:/game/3x116/scratch/tool_patched.jar");
        File bypassHelperClass = new File("f:/game/3x116/scratch/avt/BypassHelper.class");

        System.out.println("Reading original jar: " + jarFile.getAbsolutePath());

        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(jarFile))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = zis.read(buf)) != -1) {
                    baos.write(buf, 0, n);
                }
                entries.put(entry.getName(), baos.toByteArray());
            }
        }

        System.out.println("Total entries read: " + entries.size());

        // Add BypassHelper.class and all anonymous inner classes (BypassHelper$1, $2, ...)
        File scratchAvtDir = new File("f:/game/3x116/scratch/avt");
        int bypassAdded = 0;
        if (scratchAvtDir.exists()) {
            for (File f : scratchAvtDir.listFiles()) {
                String name = f.getName();
                if (name.equals("BypassHelper.class") || name.startsWith("BypassHelper$")) {
                    byte[] bytes = Files.readAllBytes(f.toPath());
                    entries.put("avt/" + name, bytes);
                    System.out.println("Added avt/" + name + " to jar entries!");
                    bypassAdded++;
                }
            }
        }
        if (bypassAdded == 0) {
            System.err.println("Error: No BypassHelper*.class files found!");
            return;
        }

        // 1. Patch avt/Q.class
        if (entries.containsKey("avt/Q.class")) {
            System.out.println("Patching avt/Q.class...");
            byte[] originalQ = entries.get("avt/Q.class");
            byte[] patchedQ = patchQ(originalQ);
            entries.put("avt/Q.class", patchedQ);
            System.out.println("avt/Q.class patched successfully!");
        }

        // 2. Patch avt/M.class
        if (entries.containsKey("avt/M.class")) {
            System.out.println("Patching avt/M.class...");
            byte[] originalM = entries.get("avt/M.class");
            byte[] patchedM = patchM(originalM);
            entries.put("avt/M.class", patchedM);
            System.out.println("avt/M.class patched successfully!");
        }

        // 3. Patch avt/DebugToDeath.class
        if (entries.containsKey("avt/DebugToDeath.class")) {
            System.out.println("Patching avt/DebugToDeath.class...");
            byte[] originalD = entries.get("avt/DebugToDeath.class");
            byte[] patchedD = patchDebugToDeath(originalD);
            entries.put("avt/DebugToDeath.class", patchedD);
            System.out.println("avt/DebugToDeath.class patched successfully!");
        }

        // 4. Patch avt/game/O.class (auto reconnect instead of stop)
        if (entries.containsKey("avt/game/O.class")) {
            System.out.println("Patching avt/game/O.class...");
            byte[] originalO = entries.get("avt/game/O.class");
            byte[] patchedO = patchO(originalO);
            entries.put("avt/game/O.class", patchedO);
            System.out.println("avt/game/O.class patched successfully!");
        }

        // 5. Patch avt/game/k.class (doQuangCau & doSr singleton throttle)
        if (entries.containsKey("avt/game/k.class")) {
            System.out.println("Patching avt/game/k.class...");
            byte[] originalK = entries.get("avt/game/k.class");
            byte[] patchedK = patchK(originalK);
            entries.put("avt/game/k.class", patchedK);
            System.out.println("avt/game/k.class patched successfully!");
        }

        // 6. Patch avt/network/x.class (clean enqueuePacket)
        if (entries.containsKey("avt/network/x.class")) {
            System.out.println("Patching avt/network/x.class...");
            byte[] originalX = entries.get("avt/network/x.class");
            byte[] patchedX = patchNetworkX(originalX);
            entries.put("avt/network/x.class", patchedX);
            System.out.println("avt/network/x.class patched successfully!");
        }

        // Write output jar
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(tempJar))) {
            for (Map.Entry<String, byte[]> e : entries.entrySet()) {
                ZipEntry ze = new ZipEntry(e.getKey());
                zos.putNextEntry(ze);
                zos.write(e.getValue());
                zos.closeEntry();
            }
        }

        System.out.println("Patched jar written to: " + tempJar.getAbsolutePath());
    }

    private static byte[] patchQ(byte[] bytes) {
        ClassReader cr = new ClassReader(bytes);
        ClassNode cn = new ClassNode();
        cr.accept(cn, 0);

        for (MethodNode mn : cn.methods) {
            if (mn.name.equals("o") && mn.desc.equals("([Ljava/lang/Object;)Z")) {
                mn.instructions.clear();
                mn.tryCatchBlocks.clear();
                mn.instructions.add(new InsnNode(Opcodes.ICONST_1));
                mn.instructions.add(new InsnNode(Opcodes.IRETURN));
            }
            if (mn.name.equals("r") && mn.desc.equals("([Ljava/lang/Object;)Z")) {
                mn.instructions.clear();
                mn.tryCatchBlocks.clear();
                mn.instructions.add(new InsnNode(Opcodes.ICONST_1));
                mn.instructions.add(new InsnNode(Opcodes.IRETURN));
            }
            if (mn.name.equals("u") && mn.desc.equals("([Ljava/lang/Object;)Z")) {
                mn.instructions.clear();
                mn.tryCatchBlocks.clear();
                mn.instructions.add(new InsnNode(Opcodes.ICONST_1));
                mn.instructions.add(new InsnNode(Opcodes.IRETURN));
            }
            if (mn.name.equals("N") && mn.desc.equals("([Ljava/lang/Object;)Z")) {
                mn.instructions.clear();
                mn.tryCatchBlocks.clear();
                mn.instructions.add(new InsnNode(Opcodes.ICONST_1));
                mn.instructions.add(new InsnNode(Opcodes.IRETURN));
            }
            if (mn.name.equals("v") && mn.desc.equals("([Ljava/lang/Object;)V")) {
                mn.instructions.clear();
                mn.tryCatchBlocks.clear();
                mn.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "avt/BypassHelper", "apply", "()V", false));
                mn.instructions.add(new InsnNode(Opcodes.RETURN));
            }
            if (mn.name.equals("vi") && mn.desc.equals("([Ljava/lang/Object;)V")) {
                mn.instructions.clear();
                mn.tryCatchBlocks.clear();
                mn.instructions.add(new InsnNode(Opcodes.RETURN));
            }
            if (mn.name.equals("v2") && mn.desc.equals("([Ljava/lang/Object;)V")) {
                mn.instructions.clear();
                mn.tryCatchBlocks.clear();
                mn.instructions.add(new InsnNode(Opcodes.RETURN));
            }
            if (mn.name.equals("vU") && mn.desc.equals("([Ljava/lang/Object;)V")) {
                mn.instructions.clear();
                mn.tryCatchBlocks.clear();
                mn.instructions.add(new InsnNode(Opcodes.RETURN));
            }
            if (mn.name.equals("Li") && mn.desc.equals("([Ljava/lang/Object;)V")) {
                mn.instructions.clear();
                mn.tryCatchBlocks.clear();
                mn.instructions.add(new InsnNode(Opcodes.RETURN));
            }
            if (mn.name.equals("S") && mn.desc.equals("([Ljava/lang/Object;)Ljava/lang/String;")) {
                mn.instructions.clear();
                mn.tryCatchBlocks.clear();
                mn.instructions.add(new LdcInsnNode("{\"status\":\"ok\",\"message\":\"Đăng nhập thành công\"}"));
                mn.instructions.add(new InsnNode(Opcodes.ARETURN));
            }
            if (mn.name.equals("vS") && mn.desc.equals("([Ljava/lang/Object;)V")) {
                mn.instructions.clear();
                mn.tryCatchBlocks.clear();
                mn.instructions.add(new InsnNode(Opcodes.RETURN));
            }
            if (mn.name.equals("L") && mn.desc.equals("([Ljava/lang/Object;)V")) {
                mn.instructions.clear();
                mn.tryCatchBlocks.clear();
                mn.instructions.add(new InsnNode(Opcodes.RETURN));
            }
            if (mn.name.equals("X") && mn.desc.equals("([Ljava/lang/Object;)V")) {
                mn.instructions.clear();
                mn.tryCatchBlocks.clear();
                mn.instructions.add(new InsnNode(Opcodes.RETURN));
            }
            if (mn.name.equals("I") && mn.desc.equals("([Ljava/lang/Object;)V")) {
                InsnList inject = new InsnList();
                inject.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "avt/BypassHelper", "apply", "()V", false));
                mn.instructions.insert(inject);

                for (AbstractInsnNode insn = mn.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                    if (insn.getOpcode() == Opcodes.RETURN) {
                        InsnList applyList = new InsnList();
                        applyList.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "avt/BypassHelper", "apply", "()V", false));
                        mn.instructions.insertBefore(insn, applyList);
                    }
                }
            }
            if (mn.name.equals("F") && mn.desc.equals("([Ljava/lang/Object;)Ljava/lang/String;")) {
                mn.instructions.clear();
                mn.tryCatchBlocks.clear();
                mn.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
                mn.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "avt/BypassHelper", "getStateJson", "([Ljava/lang/Object;)Ljava/lang/String;", false));
                mn.instructions.add(new InsnNode(Opcodes.ARETURN));
            }
            if (mn.name.equals("j") && mn.desc.equals("([Ljava/lang/Object;)Ljava/lang/String;")) {
                mn.instructions.clear();
                mn.tryCatchBlocks.clear();
                mn.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
                mn.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "avt/BypassHelper", "getStateJson", "([Ljava/lang/Object;)Ljava/lang/String;", false));
                mn.instructions.add(new InsnNode(Opcodes.ARETURN));
            }
        }

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cn.accept(cw);
        return cw.toByteArray();
    }

    private static byte[] patchM(byte[] bytes) {
        ClassReader cr = new ClassReader(bytes);
        ClassNode cn = new ClassNode();
        cr.accept(cn, 0);

        for (MethodNode mn : cn.methods) {
            if (mn.name.equals("j") && mn.desc.equals("([Ljava/lang/Object;)Lavt/t;")) {
                mn.instructions.clear();
                mn.tryCatchBlocks.clear();
                mn.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "avt/BypassHelper", "createMockT", "()Ljava/lang/Object;", false));
                mn.instructions.add(new TypeInsnNode(Opcodes.CHECKCAST, "avt/t"));
                mn.instructions.add(new InsnNode(Opcodes.ARETURN));
            }
            if (mn.name.equals("W") && mn.desc.equals("([Ljava/lang/Object;)Lavt/t;")) {
                mn.instructions.clear();
                mn.tryCatchBlocks.clear();
                mn.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "avt/BypassHelper", "createMockT", "()Ljava/lang/Object;", false));
                mn.instructions.add(new TypeInsnNode(Opcodes.CHECKCAST, "avt/t"));
                mn.instructions.add(new InsnNode(Opcodes.ARETURN));
            }
            if (mn.name.equals("R") && mn.desc.equals("([Ljava/lang/Object;)Lorg/json/simple/S;")) {
                mn.instructions.clear();
                mn.tryCatchBlocks.clear();
                mn.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "avt/BypassHelper", "createMockR", "()Lorg/json/simple/S;", false));
                mn.instructions.add(new InsnNode(Opcodes.ARETURN));
            }
        }

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cn.accept(cw);
        return cw.toByteArray();
    }

    private static byte[] patchDebugToDeath(byte[] bytes) {
        ClassReader cr = new ClassReader(bytes);
        ClassNode cn = new ClassNode();
        cr.accept(cn, 0);

        for (MethodNode mn : cn.methods) {
            if (mn.name.equals("start") && mn.desc.equals("(Ljavafx/stage/Stage;)V")) {
                InsnList inject = new InsnList();
                inject.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "avt/BypassHelper", "apply", "()V", false));
                mn.instructions.insert(inject);
            }
            if (mn.name.equals("B") && mn.desc.equals("([Ljava/lang/Object;)V")) {
                mn.instructions.clear();
                mn.tryCatchBlocks.clear();
                mn.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
                mn.instructions.add(new InsnNode(Opcodes.ICONST_0));
                mn.instructions.add(new TypeInsnNode(Opcodes.ANEWARRAY, "java/lang/Object"));
                mn.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, "avt/DebugToDeath", "d", "([Ljava/lang/Object;)V", false));
                mn.instructions.add(new InsnNode(Opcodes.RETURN));
            }
        }

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cn.accept(cw);
        return cw.toByteArray();
    }

    private static byte[] patchO(byte[] bytes) {
        ClassReader cr = new ClassReader(bytes);
        ClassNode cn = new ClassNode();
        cr.accept(cn, 0);

        for (MethodNode mn : cn.methods) {
            // In O.P([Ljava/lang/Object;)V (onDisconnected) -> delegate to BypassHelper.handleDisconnected
            if (mn.name.equals("P") && mn.desc.equals("([Ljava/lang/Object;)V")) {
                System.out.println("  Patching O.P (onDisconnected) -> auto reconnect");
                mn.instructions.clear();
                mn.tryCatchBlocks.clear();
                mn.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
                mn.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "avt/BypassHelper", "handleDisconnected", "(Ljava/lang/Object;)V", false));
                mn.instructions.add(new InsnNode(Opcodes.RETURN));
            }

            // In O.N([Ljava/lang/Object;)V (onConnectFail) -> delegate to BypassHelper.handleDisconnected
            if (mn.name.equals("N") && mn.desc.equals("([Ljava/lang/Object;)V")) {
                System.out.println("  Patching O.N (onConnectFail) -> auto reconnect");
                mn.instructions.clear();
                mn.tryCatchBlocks.clear();
                mn.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
                mn.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "avt/BypassHelper", "handleDisconnected", "(Ljava/lang/Object;)V", false));
                mn.instructions.add(new InsnNode(Opcodes.RETURN));
            }
        }

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cn.accept(cw);
        return cw.toByteArray();
    }

    private static byte[] patchK(byte[] bytes) {
        ClassReader cr = new ClassReader(bytes);
        ClassNode cn = new ClassNode();
        cr.accept(cn, 0);

        for (MethodNode mn : cn.methods) {
            // Inject onBotLog into k.o (called on every log output)
            if (mn.name.equals("o") && mn.desc.equals("([Ljava/lang/Object;)V")) {
                System.out.println("  Injecting onBotLog into k.o");
                InsnList inject = new InsnList();
                inject.add(new VarInsnNode(Opcodes.ALOAD, 0));
                inject.add(new VarInsnNode(Opcodes.ALOAD, 1));
                inject.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "avt/BypassHelper", "onBotLog", "(Ljava/lang/Object;[Ljava/lang/Object;)V", false));
                mn.instructions.insert(inject);
            }

            // Inject registerActiveBot at end of k.<init>
            if (mn.name.equals("<init>")) {
                for (AbstractInsnNode insn = mn.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                    if (insn.getOpcode() == Opcodes.RETURN) {
                        InsnList inject = new InsnList();
                        inject.add(new VarInsnNode(Opcodes.ALOAD, 0));
                        inject.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "avt/BypassHelper", "registerActiveBot", "(Ljava/lang/Object;)V", false));
                        mn.instructions.insertBefore(insn, inject);
                    }
                }
            }

            // In k.V([Ljava/lang/Object;)V -> delegate to BypassHelper.doQuangCau
            if (mn.name.equals("V") && mn.desc.equals("([Ljava/lang/Object;)V")) {
                System.out.println("  Patching k.V -> delegate to BypassHelper.doQuangCau");
                mn.instructions.clear();
                mn.tryCatchBlocks.clear();
                mn.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
                mn.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "avt/BypassHelper", "doQuangCau", "(Ljava/lang/Object;)V", false));
                mn.instructions.add(new InsnNode(Opcodes.RETURN));
            }

            // In k.Sr([Ljava/lang/Object;)V -> delegate to BypassHelper.doSr
            if (mn.name.equals("Sr") && mn.desc.equals("([Ljava/lang/Object;)V")) {
                System.out.println("  Patching k.Sr -> delegate to BypassHelper.doSr");
                mn.instructions.clear();
                mn.tryCatchBlocks.clear();
                mn.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
                mn.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "avt/BypassHelper", "doSr", "(Ljava/lang/Object;)V", false));
                mn.instructions.add(new InsnNode(Opcodes.RETURN));
            }
        }

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cn.accept(cw);
        return cw.toByteArray();
    }

    private static byte[] patchNetworkX(byte[] bytes) {
        ClassReader cr = new ClassReader(bytes);
        ClassNode cn = new ClassNode();
        cr.accept(cn, 0);

        for (MethodNode mn : cn.methods) {
            // In x.X([Ljava/lang/Object;)V -> delegate to BypassHelper.enqueuePacket
            if (mn.name.equals("X") && mn.desc.equals("([Ljava/lang/Object;)V")) {
                System.out.println("  Patching x.X (send packet queue) -> filter duplicate fishing packets");
                mn.instructions.clear();
                mn.tryCatchBlocks.clear();
                mn.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
                mn.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
                mn.instructions.add(new InsnNode(Opcodes.ICONST_0));
                mn.instructions.add(new InsnNode(Opcodes.AALOAD));
                mn.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "avt/BypassHelper", "enqueuePacket", "(Ljava/lang/Object;Ljava/lang/Object;)V", false));
                mn.instructions.add(new InsnNode(Opcodes.RETURN));
            }
        }

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cn.accept(cw);
        return cw.toByteArray();
    }
}
