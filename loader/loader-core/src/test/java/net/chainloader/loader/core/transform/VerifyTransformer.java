package net.chainloader.loader.core.transform;

import net.chainloader.loader.core.ChainLauncher;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.lang.reflect.Method;
import java.util.ListIterator;

public class VerifyTransformer {

    public static void main(String[] args) {
        System.out.println("Starting Task 3B ASM Transformer Verification...");
        try {
            verifyTitleScreenTransform();
            verifyMinecraftTransform();
            verifyEntityModelSetTransform();
            System.out.println("==================================================");
            System.out.println("[SUCCESS] Task 3B Verification Completed Successfully!");
            System.out.println("  - 'fof' (TitleScreen) class matches and transforms 'aT_' (init) and 'a' (render)");
            System.out.println("  - 'fgo' (Minecraft) class matches and transforms 'a' (setScreen)");
            System.out.println("  - 'fyg' (EntityModelSet) class matches and transforms 'a' (onResourceManagerReload)");
            System.out.println("  - Correct MainMenuHelper static hooks and EventBridgeHelper model mergers are injected");
            System.out.println("==================================================");
        } catch (Throwable t) {
            System.err.println("==================================================");
            System.err.println("[FAILURE] Task 3B Verification Failed!");
            t.printStackTrace();
            System.err.println("==================================================");
            System.exit(1);
        }
    }

    private static void verifyTitleScreenTransform() throws Exception {
        System.out.println("Verifying TitleScreen (fof) transformation...");
        // 1. Generate dummy fof class bytes
        ClassWriter cw = new ClassWriter(0);
        cw.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, "fof", null, "java/lang/Object", null);

        // aT_()V (init)
        MethodVisitor mvInit = cw.visitMethod(Opcodes.ACC_PUBLIC, "aT_", "()V", null, null);
        mvInit.visitCode();
        mvInit.visitInsn(Opcodes.RETURN);
        mvInit.visitMaxs(1, 1);
        mvInit.visitEnd();

        // a(Lfhz;IIF)V (render)
        MethodVisitor mvRender = cw.visitMethod(Opcodes.ACC_PUBLIC, "a", "(Lfhz;IIF)V", null, null);
        mvRender.visitCode();
        mvRender.visitInsn(Opcodes.RETURN);
        mvRender.visitMaxs(1, 5);
        mvRender.visitEnd();

        cw.visitEnd();
        byte[] originalBytes = cw.toByteArray();

        // 2. Invoke private transformTitleScreen method via reflection
        Method transformTitleScreen = ChainLauncher.class.getDeclaredMethod("transformTitleScreen", byte[].class);
        transformTitleScreen.setAccessible(true);
        byte[] transformedBytes = (byte[]) transformTitleScreen.invoke(null, (Object) originalBytes);

        if (transformedBytes == null) {
            throw new RuntimeException("transformTitleScreen returned null!");
        }
        if (transformedBytes.length == originalBytes.length) {
            throw new RuntimeException("transformTitleScreen did not modify class bytes!");
        }

        // 3. Inspect transformed bytecode
        ClassReader reader = new ClassReader(transformedBytes);
        ClassNode classNode = new ClassNode();
        reader.accept(classNode, 0);

        boolean verifiedInit = false;
        boolean verifiedRender = false;

        for (MethodNode method : classNode.methods) {
            if ("aT_".equals(method.name) && "()V".equals(method.desc)) {
                verifiedInit = true;
                int returnCount = 0;
                int invokeCount = 0;
                ListIterator<AbstractInsnNode> iterator = method.instructions.iterator();
                while (iterator.hasNext()) {
                    AbstractInsnNode insn = iterator.next();
                    if (insn.getOpcode() == Opcodes.INVOKESTATIC) {
                        MethodInsnNode min = (MethodInsnNode) insn;
                        if (!"net/chainloader/loader/core/gui/MainMenuHelper".equals(min.owner)) {
                            throw new RuntimeException("Expected call to MainMenuHelper but got " + min.owner);
                        }
                        if (!"onInitTitleScreen".equals(min.name)) {
                            throw new RuntimeException("Expected call to onInitTitleScreen but got " + min.name);
                        }
                        if (!"(Lfof;)V".equals(min.desc)) {
                            throw new RuntimeException("Expected descriptor (Lfof;)V but got " + min.desc);
                        }
                        invokeCount++;
                    } else if (insn.getOpcode() == Opcodes.RETURN) {
                        returnCount++;
                    }
                }
                if (invokeCount != 1) {
                    throw new RuntimeException("Expected exactly 1 hook invocation in aT_ but found " + invokeCount);
                }
                if (returnCount != 1) {
                    throw new RuntimeException("Expected exactly 1 RETURN instruction in aT_ but found " + returnCount);
                }
                System.out.println("  - TitleScreen.aT_()V successfully verified (added MainMenuHelper.onInitTitleScreen(this)).");
            } else if ("a".equals(method.name) && "(Lfhz;IIF)V".equals(method.desc)) {
                verifiedRender = true;
                int returnCount = 0;
                int invokeCount = 0;
                ListIterator<AbstractInsnNode> iterator = method.instructions.iterator();
                while (iterator.hasNext()) {
                    AbstractInsnNode insn = iterator.next();
                    if (insn.getOpcode() == Opcodes.INVOKESTATIC) {
                        MethodInsnNode min = (MethodInsnNode) insn;
                        if (!"net/chainloader/loader/core/gui/MainMenuHelper".equals(min.owner)) {
                            throw new RuntimeException("Expected call to MainMenuHelper but got " + min.owner);
                        }
                        if (!"onRenderTitleScreen".equals(min.name)) {
                            throw new RuntimeException("Expected call to onRenderTitleScreen but got " + min.name);
                        }
                        if (!"(Lfof;Lfhz;)V".equals(min.desc)) {
                            throw new RuntimeException("Expected descriptor (Lfof;Lfhz;)V but got " + min.desc);
                        }
                        invokeCount++;
                    } else if (insn.getOpcode() == Opcodes.RETURN) {
                        returnCount++;
                    }
                }
                if (invokeCount != 1) {
                    throw new RuntimeException("Expected exactly 1 hook invocation in render (a) but found " + invokeCount);
                }
                if (returnCount != 1) {
                    throw new RuntimeException("Expected exactly 1 RETURN instruction in render (a) but found " + returnCount);
                }
                System.out.println("  - TitleScreen.a(Lfhz;IIF)V successfully verified (added MainMenuHelper.onRenderTitleScreen(this, graphics)).");
            }
        }

        if (!verifiedInit) {
            throw new RuntimeException("Failed to locate/verify transformed aT_()V method!");
        }
        if (!verifiedRender) {
            throw new RuntimeException("Failed to locate/verify transformed a(Lfhz;IIF)V method!");
        }
    }

    private static void verifyMinecraftTransform() throws Exception {
        System.out.println("Verifying Minecraft (fgo) transformation...");
        // 1. Generate dummy fgo class bytes
        ClassWriter cw = new ClassWriter(0);
        cw.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, "fgo", null, "java/lang/Object", null);

        // a(Lfod;)V (setScreen)
        MethodVisitor mvSetScreen = cw.visitMethod(Opcodes.ACC_PUBLIC, "a", "(Lfod;)V", null, null);
        mvSetScreen.visitCode();
        mvSetScreen.visitInsn(Opcodes.RETURN);
        mvSetScreen.visitMaxs(1, 2);
        mvSetScreen.visitEnd();

        cw.visitEnd();
        byte[] originalBytes = cw.toByteArray();

        // 2. Invoke private transformMinecraft method via reflection
        Method transformMinecraft = ChainLauncher.class.getDeclaredMethod("transformMinecraft", byte[].class);
        transformMinecraft.setAccessible(true);
        byte[] transformedBytes = (byte[]) transformMinecraft.invoke(null, (Object) originalBytes);

        if (transformedBytes == null) {
            throw new RuntimeException("transformMinecraft returned null!");
        }
        if (transformedBytes.length == originalBytes.length) {
            throw new RuntimeException("transformMinecraft did not modify class bytes!");
        }

        // 3. Inspect transformed bytecode
        ClassReader reader = new ClassReader(transformedBytes);
        ClassNode classNode = new ClassNode();
        reader.accept(classNode, 0);

        boolean verifiedSetScreen = false;

        for (MethodNode method : classNode.methods) {
            if ("a".equals(method.name) && "(Lfod;)V".equals(method.desc)) {
                verifiedSetScreen = true;
                int invokeCount = 0;
                int astoreCount = 0;
                ListIterator<AbstractInsnNode> iterator = method.instructions.iterator();
                while (iterator.hasNext()) {
                    AbstractInsnNode insn = iterator.next();
                    if (insn.getOpcode() == Opcodes.INVOKESTATIC) {
                        MethodInsnNode min = (MethodInsnNode) insn;
                        if (!"net/chainloader/loader/core/gui/MainMenuHelper".equals(min.owner)) {
                            throw new RuntimeException("Expected call to MainMenuHelper but got " + min.owner);
                        }
                        if (!"interceptSetScreen".equals(min.name)) {
                            throw new RuntimeException("Expected call to interceptSetScreen but got " + min.name);
                        }
                        if (!"(Lfod;)Lfod;".equals(min.desc)) {
                            throw new RuntimeException("Expected descriptor (Lfod;)Lfod; but got " + min.desc);
                        }
                        invokeCount++;
                    } else if (insn.getOpcode() == Opcodes.ASTORE) {
                        VarInsnNode vin = (VarInsnNode) insn;
                        if (vin.var != 1) {
                            throw new RuntimeException("Expected ASTORE to local variable 1 but got " + vin.var);
                        }
                        astoreCount++;
                    }
                }
                if (invokeCount != 1) {
                    throw new RuntimeException("Expected exactly 1 hook invocation in setScreen but found " + invokeCount);
                }
                if (astoreCount != 1) {
                    throw new RuntimeException("Expected exactly 1 ASTORE 1 instruction in setScreen but found " + astoreCount);
                }
                System.out.println("  - Minecraft.a(Lfod;)V successfully verified (injected MainMenuHelper.interceptSetScreen(screen)).");
            }
        }

        if (!verifiedSetScreen) {
            throw new RuntimeException("Failed to locate/verify transformed a(Lfod;)V method!");
        }
    }

    private static void verifyEntityModelSetTransform() throws Exception {
        System.out.println("Verifying EntityModelSet (fyg) transformation...");
        // 1. Generate dummy fyg class bytes
        ClassWriter cw = new ClassWriter(0);
        cw.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, "fyg", null, "java/lang/Object", null);

        // a(Laue;)V
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "a", "(Laue;)V", null, null);
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, "fyh", "a", "()Ljava/util/Map;", false);
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, "com/google/common/collect/ImmutableMap", "copyOf", "(Ljava/util/Map;)Lcom/google/common/collect/ImmutableMap;", false);
        mv.visitFieldInsn(Opcodes.PUTFIELD, "fyg", "a", "Ljava/util/Map;");
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(2, 2);
        mv.visitEnd();

        cw.visitEnd();
        byte[] originalBytes = cw.toByteArray();

        // 2. Invoke private transformEntityModelSet method via reflection
        Method transformEntityModelSet = ChainLauncher.class.getDeclaredMethod("transformEntityModelSet", byte[].class);
        transformEntityModelSet.setAccessible(true);
        byte[] transformedBytes = (byte[]) transformEntityModelSet.invoke(null, (Object) originalBytes);

        if (transformedBytes == null) {
            throw new RuntimeException("transformEntityModelSet returned null!");
        }
        if (transformedBytes.length == originalBytes.length) {
            throw new RuntimeException("transformEntityModelSet did not modify class bytes!");
        }

        // 3. Inspect transformed bytecode
        ClassReader reader = new ClassReader(transformedBytes);
        ClassNode classNode = new ClassNode();
        reader.accept(classNode, 0);

        boolean verified = false;

        for (MethodNode method : classNode.methods) {
            if ("a".equals(method.name) && "(Laue;)V".equals(method.desc)) {
                verified = true;
                int originalCallIndex = -1;
                int bridgeCallIndex = -1;
                int i = 0;
                ListIterator<AbstractInsnNode> iterator = method.instructions.iterator();
                while (iterator.hasNext()) {
                    AbstractInsnNode insn = iterator.next();
                    if (insn.getOpcode() == Opcodes.INVOKESTATIC) {
                        MethodInsnNode min = (MethodInsnNode) insn;
                        if ("fyh".equals(min.owner) && "a".equals(min.name)) {
                            originalCallIndex = i;
                        } else if ("net/chainloader/loader/compat/bridge/EventBridgeHelper".equals(min.owner) && "onEntityModelSetReload".equals(min.name)) {
                            bridgeCallIndex = i;
                        }
                    }
                    i++;
                }
                if (originalCallIndex == -1) {
                    throw new RuntimeException("Failed to find call to fyh.a");
                }
                if (bridgeCallIndex == -1) {
                    throw new RuntimeException("Failed to find call to EventBridgeHelper.onEntityModelSetReload");
                }
                if (bridgeCallIndex != originalCallIndex + 1) {
                    throw new RuntimeException("Bridge call does not immediately follow fyh.a call");
                }
                System.out.println("  - EntityModelSet.a(Laue;)V successfully verified (injected EventBridgeHelper.onEntityModelSetReload).");
            }
        }

        if (!verified) {
            throw new RuntimeException("Failed to locate/verify transformed a(Laue;)V method!");
        }
    }
}
