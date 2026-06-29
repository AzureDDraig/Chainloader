package net.chainloader.loader.core.transform;

import net.chainloader.api.environment.EnvType;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import static org.junit.jupiter.api.Assertions.*;

public class SideAnnotationStripperTest {

    @Test
    public void testClassLevelStripping() {
        // 1. Generate a class annotated with CLIENT environment
        ClassWriter cw = new ClassWriter(0);
        cw.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, "net/example/ClientOnlyClass", null, "java/lang/Object", null);
        
        AnnotationVisitor av = cw.visitAnnotation("Lnet/fabricmc/api/Environment;", true);
        av.visitEnum("value", "Lnet/fabricmc/api/EnvType;", "CLIENT");
        av.visitEnd();
        
        cw.visitEnd();
        byte[] classBytes = cw.toByteArray();

        // 2. Running on CLIENT environment should NOT throw
        SideAnnotationStripper clientStripper = new SideAnnotationStripper(EnvType.CLIENT);
        assertDoesNotThrow(() -> {
            byte[] result = clientStripper.transform("net.example.ClientOnlyClass", classBytes);
            assertNotNull(result);
        });

        // 3. Running on SERVER environment SHOULD throw SideStrippedException
        SideAnnotationStripper serverStripper = new SideAnnotationStripper(EnvType.SERVER);
        SideAnnotationStripper.SideStrippedException exception = assertThrows(
            SideAnnotationStripper.SideStrippedException.class,
            () -> serverStripper.transform("net.example.ClientOnlyClass", classBytes)
        );

        assertEquals("net.example.ClientOnlyClass", exception.getClassName());
        assertEquals(EnvType.CLIENT, exception.getRequiredEnv());
        assertEquals(EnvType.SERVER, exception.getCurrentEnv());

        // 4. Verify coordination registries
        assertTrue(SideAnnotationStripper.isClassStripped("net/example/ClientOnlyClass"));
        assertTrue(SideAnnotationStripper.isClassStripped("net.example.ClientOnlyClass"));
        assertTrue(serverStripper.isInstanceClassStripped("net/example/ClientOnlyClass"));
    }

    @Test
    public void testFieldAndMethodStripping() {
        // 1. Generate a class with some members to keep and some to strip
        ClassWriter cw = new ClassWriter(0);
        cw.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, "net/example/MixedClass", null, "java/lang/Object", null);

        // Field to keep
        FieldVisitor fvKeep = cw.visitField(Opcodes.ACC_PRIVATE, "keepField", "I", null, null);
        fvKeep.visitEnd();

        // Field to strip (CLIENT annotated, running on SERVER)
        FieldVisitor fvStrip = cw.visitField(Opcodes.ACC_PRIVATE, "stripField", "Ljava/lang/String;", null, null);
        AnnotationVisitor avField = fvStrip.visitAnnotation("Lnet/neoforged/api/distmarker/OnlyIn;", true);
        avField.visitEnum("value", "Lnet/neoforged/api/distmarker/Dist;", "CLIENT");
        avField.visitEnd();
        fvStrip.visitEnd();

        // Method to keep
        MethodVisitor mvKeep = cw.visitMethod(Opcodes.ACC_PUBLIC, "keepMethod", "()V", null, null);
        mvKeep.visitCode();
        mvKeep.visitInsn(Opcodes.RETURN);
        mvKeep.visitMaxs(0, 1);
        mvKeep.visitEnd();

        // Method to strip (SERVER annotated, running on CLIENT)
        MethodVisitor mvStrip = cw.visitMethod(Opcodes.ACC_PUBLIC, "stripMethod", "(I)Z", null, null);
        AnnotationVisitor avMethod = mvStrip.visitAnnotation("Lnet/minecraftforge/api/distmarker/OnlyIn;", true);
        avMethod.visitEnum("value", "Lnet/minecraftforge/api/distmarker/Dist;", "DEDICATED_SERVER");
        avMethod.visitEnd();
        mvStrip.visitCode();
        mvStrip.visitInsn(Opcodes.ICONST_0);
        mvStrip.visitInsn(Opcodes.IRETURN);
        mvStrip.visitMaxs(1, 2);
        mvStrip.visitEnd();

        cw.visitEnd();
        byte[] classBytes = cw.toByteArray();

        // 2. Instantiate stripper for CLIENT environment
        SideAnnotationStripper clientStripper = new SideAnnotationStripper(EnvType.CLIENT);
        byte[] transformedBytes = clientStripper.transform("net.example.MixedClass", classBytes);

        assertNotNull(transformedBytes);
        assertNotEquals(classBytes.length, transformedBytes.length);

        // 3. Inspect transformed bytecode
        ClassReader reader = new ClassReader(transformedBytes);
        ClassNode classNode = new ClassNode();
        reader.accept(classNode, 0);

        // Field checks
        boolean hasKeepField = false;
        boolean hasStripField = false;
        for (FieldNode field : classNode.fields) {
            if ("keepField".equals(field.name)) hasKeepField = true;
            if ("stripField".equals(field.name)) hasStripField = true;
        }
        assertTrue(hasKeepField, "keepField should be preserved");
        assertFalse(hasStripField, "stripField should be stripped");

        // Method checks
        boolean hasKeepMethod = false;
        boolean hasStripMethod = false;
        for (MethodNode method : classNode.methods) {
            if ("keepMethod".equals(method.name)) hasKeepMethod = true;
            if ("stripMethod".equals(method.name)) hasStripMethod = true;
        }
        assertTrue(hasKeepMethod, "keepMethod should be preserved");
        assertFalse(hasStripMethod, "stripMethod should be stripped");

        // 4. Verify coordination registries (stripField required CLIENT, we ran clientStripper -> not stripped)
        assertFalse(SideAnnotationStripper.isFieldStripped("net/example/MixedClass", "stripField", "Ljava/lang/String;"));

        // stripMethod required DEDICATED_SERVER, we ran on CLIENT -> stripped
        assertTrue(SideAnnotationStripper.isMethodStripped("net/example/MixedClass", "stripMethod", "(I)Z"));
        assertTrue(clientStripper.isInstanceMethodStripped("net/example/MixedClass", "stripMethod", "(I)Z"));

        // 5. Test another stripper running on SERVER environment
        SideAnnotationStripper serverStripper = new SideAnnotationStripper(EnvType.SERVER);
        byte[] serverTransformed = serverStripper.transform("net.example.MixedClass", classBytes);

        ClassReader serverReader = new ClassReader(serverTransformed);
        ClassNode serverClassNode = new ClassNode();
        serverReader.accept(serverClassNode, 0);

        // For SERVER environment, stripField (requires CLIENT) should be stripped, and stripMethod (requires SERVER) kept
        boolean serverHasKeepField = false;
        boolean serverHasStripField = false;
        for (FieldNode field : serverClassNode.fields) {
            if ("keepField".equals(field.name)) serverHasKeepField = true;
            if ("stripField".equals(field.name)) serverHasStripField = true;
        }
        assertTrue(serverHasKeepField);
        assertFalse(serverHasStripField);

        boolean serverHasKeepMethod = false;
        boolean serverHasStripMethod = false;
        for (MethodNode method : serverClassNode.methods) {
            if ("keepMethod".equals(method.name)) serverHasKeepMethod = true;
            if ("stripMethod".equals(method.name)) serverHasStripMethod = true;
        }
        assertTrue(serverHasKeepMethod);
        assertTrue(serverHasStripMethod);

        // Verification of registries for SERVER execution
        assertTrue(SideAnnotationStripper.isFieldStripped("net/example/MixedClass", "stripField", "Ljava/lang/String;"));
        assertTrue(serverStripper.isInstanceFieldStripped("net/example/MixedClass", "stripField", "Ljava/lang/String;"));
    }
}
