package net.chainloader.loader.core.transform;

import net.chainloader.loader.transformer.BytecodeTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;

public class BytecodeTransformerTest {

    public static void main(String[] args) {
        try {
            BytecodeTransformerTest test = new BytecodeTransformerTest();
            
            System.out.println("Running testGuiClassRemapping...");
            test.testGuiClassRemapping();
            
            System.out.println("Running testMethodAndFieldRemappingWithOwnerChecks...");
            test.testMethodAndFieldRemappingWithOwnerChecks();
            
            System.out.println("Running testBoundarySafeSuffixMatching...");
            test.testBoundarySafeSuffixMatching();
            
            System.out.println("Running testComputeIfAbsentRedirection...");
            test.testComputeIfAbsentRedirection();
            
            System.out.println("Running testDyeColorComponentsRedirection...");
            test.testDyeColorComponentsRedirection();
            
            System.out.println("Running testRecursiveMethodMapping...");
            test.testRecursiveMethodMapping();
            
            System.out.println("Running testWidgetTickRedirection...");
            test.testWidgetTickRedirection();
            
            System.out.println("BytecodeTransformerTest: ALL TESTS PASSED SUCCESSFULLY!");
        } catch (Throwable t) {
            t.printStackTrace();
            System.exit(1);
        }
    }

    public void testGuiClassRemapping() {
        BytecodeTransformer transformer = new BytecodeTransformer();

        // 1. Verify basic type mapping
        byte[] originalBytes = createTestClass("net/example/TestGuiClass", "net/minecraft/client/gui/screens/Screen");
        byte[] transformedBytes = transformer.transform("net.example.TestGuiClass", originalBytes);
        assertNotNull(transformedBytes);

        ClassNode node = readClass(transformedBytes);
        assertEquals("fod", node.superName, "Screen should remap to fod");
    }

    public void testMethodAndFieldRemappingWithOwnerChecks() {
        BytecodeTransformer transformer = new BytecodeTransformer();

        // Create a class that invokes methods on Screen and Minecraft, and accesses fields on Options
        ClassWriter cw = new ClassWriter(0);
        cw.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, "net/example/TestClass", null, "java/lang/Object", null);

        // Add method calling addRenderableWidget
        org.objectweb.asm.MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "test", "()V", null, null);
        mv.visitCode();
        // Invoke net/minecraft/client/gui/screens/Screen.addRenderableWidget(Lnet/minecraft/client/gui/components/events/GuiEventListener;)Lnet/minecraft/client/gui/components/events/GuiEventListener;
        mv.visitVarInsn(Opcodes.ALOAD, 0); // Screen object
        mv.visitVarInsn(Opcodes.ALOAD, 1); // listener
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "net/minecraft/client/gui/screens/Screen", "addRenderableWidget", 
                "(Lnet/minecraft/client/gui/components/events/GuiEventListener;)Lnet/minecraft/client/gui/components/events/GuiEventListener;", false);
        
        // Access net/minecraft/client/Minecraft.options
        mv.visitVarInsn(Opcodes.ALOAD, 2); // Minecraft object
        mv.visitFieldInsn(Opcodes.GETFIELD, "net/minecraft/client/Minecraft", "options", "Lnet/minecraft/client/Options;");

        // Access net/minecraft/client/Options.graphicsMode
        mv.visitVarInsn(Opcodes.ALOAD, 3); // Options object
        mv.visitFieldInsn(Opcodes.GETFIELD, "net/minecraft/client/Options", "graphicsMode", "Lnet/minecraft/client/OptionInstance;");

        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(3, 4);
        mv.visitEnd();
        cw.visitEnd();

        byte[] originalBytes = cw.toByteArray();
        byte[] transformedBytes = transformer.transform("net.example.TestClass", originalBytes);
        assertNotNull(transformedBytes);

        ClassNode node = readClass(transformedBytes);
        MethodInsnNode methodInsn = null;
        FieldInsnNode optionsFieldInsn = null;
        FieldInsnNode graphicsModeFieldInsn = null;

        for (org.objectweb.asm.tree.MethodNode mn : node.methods) {
            if ("test".equals(mn.name)) {
                for (AbstractInsnNode insn : mn.instructions) {
                    if (insn instanceof MethodInsnNode) {
                        methodInsn = (MethodInsnNode) insn;
                    } else if (insn instanceof FieldInsnNode) {
                        FieldInsnNode f = (FieldInsnNode) insn;
                        if ("fgo".equals(f.owner)) {
                            optionsFieldInsn = f;
                        } else if ("fgs".equals(f.owner)) {
                            graphicsModeFieldInsn = f;
                        }
                    }
                }
            }
        }

        assertNotNull(methodInsn);
        assertEquals("fod", methodInsn.owner, "Method owner Screen should map to fod");
        assertEquals("c", methodInsn.name, "Method name addRenderableWidget should map to c");

        assertNotNull(optionsFieldInsn);
        assertEquals("m", optionsFieldInsn.name, "Minecraft.options should map to m");

        assertNotNull(graphicsModeFieldInsn);
        assertEquals("az", graphicsModeFieldInsn.name, "Options.graphicsMode should map to az");
    }

    public void testBoundarySafeSuffixMatching() {
        BytecodeTransformer transformer = new BytecodeTransformer();

        // 1. Verify block entity remapping (nested/prefix path)
        byte[] origBlockEntity = createTestClass("net/example/Test", "net/minecraft/block/entity/BlockEntity");
        byte[] transBlockEntity = transformer.transform("net.example.Test", origBlockEntity);
        assertEquals("net/minecraft/world/level/block/entity/BlockEntity", readClass(transBlockEntity).superName, "Block entity should map correctly");

        // 2. Verify regular block remapping (prefix path)
        byte[] origBlock = createTestClass("net/example/Test", "net/minecraft/block/Block");
        byte[] transBlock = transformer.transform("net.example.Test", origBlock);
        assertEquals("net/minecraft/world/level/block/Block", readClass(transBlock).superName, "Block should map correctly");

        // 3. Verify boundary safety: net/minecraft/blockfield should NOT match net/minecraft/block prefix
        byte[] origBoundary = createTestClass("net/example/Test", "net/minecraft/blockfield/Something");
        byte[] transBoundary = transformer.transform("net.example.Test", origBoundary);
        assertEquals("net/minecraft/blockfield/Something", readClass(transBoundary).superName, 
                "Boundary safety: net/minecraft/blockfield should not be remapped to world/level/blockfield");
    }

    private byte[] createTestClass(String name, String superName) {
        ClassWriter cw = new ClassWriter(0);
        cw.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, name, null, superName, null);
        cw.visitEnd();
        return cw.toByteArray();
    }

    private ClassNode readClass(byte[] bytes) {
        ClassReader reader = new ClassReader(bytes);
        ClassNode node = new ClassNode();
        reader.accept(node, 0);
        return node;
    }

    private void assertNotNull(Object obj) {
        if (obj == null) throw new AssertionError("Expected object to not be null");
    }

    private void assertEquals(Object expected, Object actual) {
        assertEquals(expected, actual, "Expected " + expected + " but got " + actual);
    }

    private void assertEquals(Object expected, Object actual, String message) {
        if (expected == null && actual == null) return;
        if (expected != null && expected.equals(actual)) return;
        throw new AssertionError(message + " (Expected: " + expected + ", Actual: " + actual + ")");
    }

    public void testComputeIfAbsentRedirection() {
        BytecodeTransformer transformer = new BytecodeTransformer();

        ClassWriter cw = new ClassWriter(0);
        cw.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, "net/example/TestClass", null, "java/lang/Object", null);

        org.objectweb.asm.MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "test", "()V", null, null);
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 0); // DimensionDataStorage object
        mv.visitVarInsn(Opcodes.ALOAD, 1); // function
        mv.visitVarInsn(Opcodes.ALOAD, 2); // supplier
        mv.visitLdcInsn("test"); // name
        
        // This is the instruction we want to redirect:
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "net/minecraft/class_26", "method_17924", 
                "(Ljava/util/function/Function;Ljava/util/function/Supplier;Ljava/lang/String;)Lnet/minecraft/class_18;", false);

        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(4, 3);
        mv.visitEnd();
        cw.visitEnd();

        byte[] originalBytes = cw.toByteArray();
        byte[] transformedBytes = transformer.transform("net.example.TestClass", originalBytes);
        assertNotNull(transformedBytes);

        ClassNode node = readClass(transformedBytes);
        MethodInsnNode methodInsn = null;

        for (org.objectweb.asm.tree.MethodNode mn : node.methods) {
            if ("test".equals(mn.name)) {
                for (AbstractInsnNode insn : mn.instructions) {
                    if (insn instanceof MethodInsnNode) {
                        methodInsn = (MethodInsnNode) insn;
                    }
                }
            }
        }

        assertNotNull(methodInsn);
        assertEquals(Opcodes.INVOKESTATIC, methodInsn.getOpcode(), "Method should be static redirected");
        assertEquals("net/chainloader/loader/compat/bridge/EventBridgeHelper", methodInsn.owner, "Should redirect to EventBridgeHelper");
        assertEquals("computeIfAbsent", methodInsn.name, "Should redirect to computeIfAbsent");
    }

    public void testDyeColorComponentsRedirection() {
        BytecodeTransformer transformer = new BytecodeTransformer();

        ClassWriter cw = new ClassWriter(0);
        cw.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, "net/example/TestClass", null, "java/lang/Object", null);

        org.objectweb.asm.MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "test", "()V", null, null);
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 0); // DyeColor object
        
        // This is the instruction we want to redirect:
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "net/minecraft/class_1767", "method_7787", 
                "()[F", false);

        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(1, 1);
        mv.visitEnd();
        cw.visitEnd();

        byte[] originalBytes = cw.toByteArray();
        byte[] transformedBytes = transformer.transform("net.example.TestClass", originalBytes);
        assertNotNull(transformedBytes);

        ClassNode node = readClass(transformedBytes);
        MethodInsnNode methodInsn = null;

        for (org.objectweb.asm.tree.MethodNode mn : node.methods) {
            if ("test".equals(mn.name)) {
                for (AbstractInsnNode insn : mn.instructions) {
                    if (insn instanceof MethodInsnNode) {
                        methodInsn = (MethodInsnNode) insn;
                    }
                }
            }
        }

        assertNotNull(methodInsn);
        assertEquals(Opcodes.INVOKESTATIC, methodInsn.getOpcode(), "Method should be static redirected");
        assertEquals("net/chainloader/loader/compat/bridge/EventBridgeHelper", methodInsn.owner, "Should redirect to EventBridgeHelper");
        assertEquals("getColorComponents", methodInsn.name, "Should redirect to getColorComponents");
    }

    public void testRecursiveMethodMapping() {
        BytecodeTransformer transformer = new BytecodeTransformer();

        ClassWriter cw = new ClassWriter(0);
        cw.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, "net/example/TestClass", null, "java/lang/Object", null);

        org.objectweb.asm.MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "test", "()V", null, null);
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 0); // Button object
        
        // Invoke net/minecraft/client/gui/components/Button.getWidth()
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "net/minecraft/client/gui/components/Button", "getWidth", 
                "()I", false);

        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(1, 1);
        mv.visitEnd();
        cw.visitEnd();

        byte[] originalBytes = cw.toByteArray();
        byte[] transformedBytes = transformer.transform("net.example.TestClass", originalBytes);
        assertNotNull(transformedBytes);

        ClassNode node = readClass(transformedBytes);
        MethodInsnNode methodInsn = null;

        for (org.objectweb.asm.tree.MethodNode mn : node.methods) {
            if ("test".equals(mn.name)) {
                for (AbstractInsnNode insn : mn.instructions) {
                    if (insn instanceof MethodInsnNode) {
                        methodInsn = (MethodInsnNode) insn;
                    }
                }
            }
        }

        assertNotNull(methodInsn);
        assertEquals("y", methodInsn.name, "Button.getWidth should recursively remap to y");
    }

    public void testWidgetTickRedirection() {
        BytecodeTransformer transformer = new BytecodeTransformer();

        ClassWriter cw = new ClassWriter(0);
        cw.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, "net/example/TestClass", null, "java/lang/Object", null);

        org.objectweb.asm.MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "test", "()V", null, null);
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 0); // Button object
        
        // Invoke net/minecraft/client/gui/components/Button.tick()
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "net/minecraft/client/gui/components/Button", "tick", 
                "()V", false);

        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(1, 1);
        mv.visitEnd();
        cw.visitEnd();

        byte[] originalBytes = cw.toByteArray();
        byte[] transformedBytes = transformer.transform("net.example.TestClass", originalBytes);
        assertNotNull(transformedBytes);

        ClassNode node = readClass(transformedBytes);
        MethodInsnNode methodInsn = null;

        for (org.objectweb.asm.tree.MethodNode mn : node.methods) {
            if ("test".equals(mn.name)) {
                for (AbstractInsnNode insn : mn.instructions) {
                    if (insn instanceof MethodInsnNode) {
                        methodInsn = (MethodInsnNode) insn;
                    }
                }
            }
        }

        assertNotNull(methodInsn);
        assertEquals(Opcodes.INVOKESTATIC, methodInsn.getOpcode(), "tick should be static redirected");
        assertEquals("net/chainloader/loader/compat/bridge/EventBridgeHelper", methodInsn.owner, "Should redirect to EventBridgeHelper");
        assertEquals("tickWidget", methodInsn.name, "Should redirect to tickWidget");
    }
}
