package net.chainloader.loader.core.transform;

import net.chainloader.loader.core.ChainLauncher;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.lang.reflect.Method;
import java.util.ListIterator;

import static org.junit.jupiter.api.Assertions.*;

public class ChainLauncherTransformerTest {

    @Test
    public void testTransformTitleScreen() throws Exception {
        // 1. Generate a dummy fof (TitleScreen) class bytes
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

        assertNotNull(transformedBytes);
        assertNotEquals(originalBytes.length, transformedBytes.length);

        // 3. Inspect transformed bytecode
        ClassReader reader = new ClassReader(transformedBytes);
        ClassNode classNode = new ClassNode();
        reader.accept(classNode, 0);

        boolean verifiedInit = false;
        boolean verifiedRender = false;

        for (MethodNode method : classNode.methods) {
            if ("aT_".equals(method.name) && "()V".equals(method.desc)) {
                verifiedInit = true;
                // Verify instructions in aT_()V:
                // Expecting: ALOAD 0, INVOKESTATIC net/chainloader/loader/core/gui/MainMenuHelper.onInitTitleScreen (Lfof;)V, RETURN
                int returnCount = 0;
                int invokeCount = 0;
                ListIterator<AbstractInsnNode> iterator = method.instructions.iterator();
                while (iterator.hasNext()) {
                    AbstractInsnNode insn = iterator.next();
                    if (insn.getOpcode() == Opcodes.INVOKESTATIC) {
                        MethodInsnNode min = (MethodInsnNode) insn;
                        assertEquals("net/chainloader/loader/core/gui/MainMenuHelper", min.owner);
                        assertEquals("onInitTitleScreen", min.name);
                        assertEquals("(Lfof;)V", min.desc);
                        invokeCount++;
                    } else if (insn.getOpcode() == Opcodes.RETURN) {
                        returnCount++;
                    }
                }
                assertEquals(1, invokeCount, "Should have exactly one call to MainMenuHelper.onInitTitleScreen");
                assertEquals(1, returnCount, "Should have exactly one RETURN instruction");
            } else if ("a".equals(method.name) && "(Lfhz;IIF)V".equals(method.desc)) {
                verifiedRender = true;
                // Verify instructions in a(Lfhz;IIF)V:
                // Expecting: ALOAD 0, ALOAD 1, INVOKESTATIC net/chainloader/loader/core/gui/MainMenuHelper.onRenderTitleScreen (Lfof;Lfhz;)V, RETURN
                int returnCount = 0;
                int invokeCount = 0;
                ListIterator<AbstractInsnNode> iterator = method.instructions.iterator();
                while (iterator.hasNext()) {
                    AbstractInsnNode insn = iterator.next();
                    if (insn.getOpcode() == Opcodes.INVOKESTATIC) {
                        MethodInsnNode min = (MethodInsnNode) insn;
                        assertEquals("net/chainloader/loader/core/gui/MainMenuHelper", min.owner);
                        assertEquals("onRenderTitleScreen", min.name);
                        assertEquals("(Lfof;Lfhz;)V", min.desc);
                        invokeCount++;
                    } else if (insn.getOpcode() == Opcodes.RETURN) {
                        returnCount++;
                    }
                }
                assertEquals(1, invokeCount, "Should have exactly one call to MainMenuHelper.onRenderTitleScreen");
                assertEquals(1, returnCount, "Should have exactly one RETURN instruction");
            }
        }

        assertTrue(verifiedInit, "aT_ method should have been transformed and verified");
        assertTrue(verifiedRender, "a method should have been transformed and verified");
    }

    @Test
    public void testTransformMinecraft() throws Exception {
        // 1. Generate a dummy fgo (Minecraft) class bytes
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

        assertNotNull(transformedBytes);
        assertNotEquals(originalBytes.length, transformedBytes.length);

        // 3. Inspect transformed bytecode
        ClassReader reader = new ClassReader(transformedBytes);
        ClassNode classNode = new ClassNode();
        reader.accept(classNode, 0);

        boolean verifiedSetScreen = false;

        for (MethodNode method : classNode.methods) {
            if ("a".equals(method.name) && "(Lfod;)V".equals(method.desc)) {
                verifiedSetScreen = true;
                // Verify instructions in a(Lfod;)V:
                // Expecting: ALOAD 1, INVOKESTATIC net/chainloader/loader/core/gui/MainMenuHelper.interceptSetScreen (Lfod;)Lfod;, ASTORE 1, ...
                int invokeCount = 0;
                int astoreCount = 0;
                ListIterator<AbstractInsnNode> iterator = method.instructions.iterator();
                while (iterator.hasNext()) {
                    AbstractInsnNode insn = iterator.next();
                    if (insn.getOpcode() == Opcodes.INVOKESTATIC) {
                        MethodInsnNode min = (MethodInsnNode) insn;
                        assertEquals("net/chainloader/loader/core/gui/MainMenuHelper", min.owner);
                        assertEquals("interceptSetScreen", min.name);
                        assertEquals("(Lfod;)Lfod;", min.desc);
                        invokeCount++;
                    } else if (insn.getOpcode() == Opcodes.ASTORE) {
                        VarInsnNode vin = (VarInsnNode) insn;
                        assertEquals(1, vin.var);
                        astoreCount++;
                    }
                }
                assertEquals(1, invokeCount, "Should have exactly one call to MainMenuHelper.interceptSetScreen");
                assertEquals(1, astoreCount, "Should have exactly one ASTORE 1 instruction");
            }
        }

        assertTrue(verifiedSetScreen, "a(Lfod;)V method should have been transformed and verified");
    }

    @Test
    public void testTransformScreenKeyEvents() throws Exception {
        // 1. Generate a dummy fod (Screen) class bytes
        ClassWriter cw = new ClassWriter(0);
        cw.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, "fod", null, "java/lang/Object", null);

        // a(III)Z (keyPressed)
        MethodVisitor mvKeyPressed = cw.visitMethod(Opcodes.ACC_PUBLIC, "a", "(III)Z", null, null);
        mvKeyPressed.visitCode();
        mvKeyPressed.visitInsn(Opcodes.ICONST_0);
        mvKeyPressed.visitInsn(Opcodes.IRETURN);
        mvKeyPressed.visitMaxs(1, 4);
        mvKeyPressed.visitEnd();

        // c(III)Z (keyReleased)
        MethodVisitor mvKeyReleased = cw.visitMethod(Opcodes.ACC_PUBLIC, "c", "(III)Z", null, null);
        mvKeyReleased.visitCode();
        mvKeyReleased.visitInsn(Opcodes.ICONST_0);
        mvKeyReleased.visitInsn(Opcodes.IRETURN);
        mvKeyReleased.visitMaxs(1, 4);
        mvKeyReleased.visitEnd();

        cw.visitEnd();
        byte[] originalBytes = cw.toByteArray();

        // 2. Invoke private transformScreen method via reflection
        Method transformScreen = ChainLauncher.class.getDeclaredMethod("transformScreen", byte[].class);
        transformScreen.setAccessible(true);
        byte[] transformedBytes = (byte[]) transformScreen.invoke(null, (Object) originalBytes);

        assertNotNull(transformedBytes);
        assertNotEquals(originalBytes.length, transformedBytes.length);

        // 3. Inspect transformed bytecode
        ClassReader reader = new ClassReader(transformedBytes);
        ClassNode classNode = new ClassNode();
        reader.accept(classNode, 0);

        boolean verifiedKeyPressed = false;
        boolean verifiedKeyReleased = false;

        for (MethodNode method : classNode.methods) {
            if ("a".equals(method.name) && "(III)Z".equals(method.desc)) {
                verifiedKeyPressed = true;
                int invokeCount = 0;
                ListIterator<AbstractInsnNode> iterator = method.instructions.iterator();
                while (iterator.hasNext()) {
                    AbstractInsnNode insn = iterator.next();
                    if (insn.getOpcode() == Opcodes.INVOKESTATIC) {
                        MethodInsnNode min = (MethodInsnNode) insn;
                        assertEquals("net/chainloader/loader/compat/bridge/EventBridgeHelper", min.owner);
                        assertEquals("onScreenKeyPressedPre", min.name);
                        assertEquals("(Lfod;III)Z", min.desc);
                        invokeCount++;
                    }
                }
                assertEquals(1, invokeCount, "Should have exactly one call to EventBridgeHelper.onScreenKeyPressedPre");
            } else if ("c".equals(method.name) && "(III)Z".equals(method.desc)) {
                verifiedKeyReleased = true;
                int invokeCount = 0;
                ListIterator<AbstractInsnNode> iterator = method.instructions.iterator();
                while (iterator.hasNext()) {
                    AbstractInsnNode insn = iterator.next();
                    if (insn.getOpcode() == Opcodes.INVOKESTATIC) {
                        MethodInsnNode min = (MethodInsnNode) insn;
                        assertEquals("net/chainloader/loader/compat/bridge/EventBridgeHelper", min.owner);
                        assertEquals("onScreenKeyReleasedPre", min.name);
                        assertEquals("(Lfod;III)Z", min.desc);
                        invokeCount++;
                    }
                }
                assertEquals(1, invokeCount, "Should have exactly one call to EventBridgeHelper.onScreenKeyReleasedPre");
            }
        }

        assertTrue(verifiedKeyPressed, "keyPressed method (a) should have been transformed");
        assertTrue(verifiedKeyReleased, "keyReleased method (c) should have been transformed");
    }

    @Test
    public void testTransformEntityModelSet() throws Exception {
        // 1. Generate a dummy fyg (EntityModelSet) class bytes
        ClassWriter cw = new ClassWriter(0);
        cw.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, "fyg", null, "java/lang/Object", null);

        // a(Laue;)V (onResourceManagerReload)
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

        assertNotNull(transformedBytes);
        assertNotEquals(originalBytes.length, transformedBytes.length);

        // 3. Inspect transformed bytecode
        ClassReader reader = new ClassReader(transformedBytes);
        ClassNode classNode = new ClassNode();
        reader.accept(classNode, 0);

        boolean verified = false;

        for (MethodNode method : classNode.methods) {
            if ("a".equals(method.name) && "(Laue;)V".equals(method.desc)) {
                verified = true;
                // Expecting: INVOKESTATIC fyh.a ()Ljava/util/Map;
                // followed by: INVOKESTATIC net/chainloader/loader/compat/bridge/EventBridgeHelper.onEntityModelSetReload (Ljava/util/Map;)Ljava/util/Map;
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
                assertTrue(originalCallIndex != -1, "Should find call to fyh.a");
                assertTrue(bridgeCallIndex != -1, "Should find call to EventBridgeHelper.onEntityModelSetReload");
                assertEquals(originalCallIndex + 1, bridgeCallIndex, "Bridge call should immediately follow fyh.a call");
            }
        }

        assertTrue(verified, "a(Laue;)V method should have been transformed and verified");
    }
}
