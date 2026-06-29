package net.chainloader.loader.access;

import net.chainloader.loader.access.AccessWidener;
import net.chainloader.loader.access.AccessWidener.AccessType;
import net.chainloader.loader.access.AccessWidener.EntryKey;
import net.chainloader.loader.core.transform.AccessWidenerCompiler;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

import java.io.StringReader;
import java.util.*;

public class AccessWidenerTest {

    public static void main(String[] args) {
        try {
            AccessWidenerTest test = new AccessWidenerTest();
            test.testParsingDirectives();
            test.testBytecodeTransformation();
            System.out.println("AccessWidenerTest: ALL TESTS PASSED SUCCESSFULLY!");
        } catch (Throwable t) {
            t.printStackTrace();
            System.exit(1);
        }
    }

    public void testParsingDirectives() throws Exception {
        System.out.println("Running testParsingDirectives...");
        AccessWidener aw = new AccessWidener();
        String config = "accessWidener v1 named\n" +
                "accessible class net/example/TestClass\n" +
                "transitive-accessible class net/example/TestTransitiveClass\n" +
                "extendable method net/example/TestClass testMethod ()V\n" +
                "transitive-extendable method net/example/TestClass testTransitiveMethod ()V\n" +
                "mutable field net/example/TestClass testField I\n" +
                "transitive-mutable field net/example/TestClass testTransitiveField I\n";

        aw.parse(new StringReader(config));

        // Verify header
        assertEquals("v1", aw.getHeaderVersion());
        assertEquals("named", aw.getHeaderNamespace());

        // Verify class rules
        Map<String, Set<AccessType>> classRules = aw.getClassRules();
        assertTrue(classRules.containsKey("net/example/TestClass"));
        assertTrue(classRules.get("net/example/TestClass").contains(AccessType.ACCESSIBLE));
        assertTrue(classRules.containsKey("net/example/TestTransitiveClass"));
        assertTrue(classRules.get("net/example/TestTransitiveClass").contains(AccessType.ACCESSIBLE));

        // Verify method rules
        Map<EntryKey, Set<AccessType>> methodRules = aw.getMethodRules();
        EntryKey methodKey1 = new EntryKey("net/example/TestClass", "testMethod", "()V");
        assertTrue(methodRules.containsKey(methodKey1));
        assertTrue(methodRules.get(methodKey1).contains(AccessType.EXTENDABLE));

        EntryKey methodKey2 = new EntryKey("net/example/TestClass", "testTransitiveMethod", "()V");
        assertTrue(methodRules.containsKey(methodKey2));
        assertTrue(methodRules.get(methodKey2).contains(AccessType.EXTENDABLE));

        // Verify field rules
        Map<EntryKey, Set<AccessType>> fieldRules = aw.getFieldRules();
        EntryKey fieldKey1 = new EntryKey("net/example/TestClass", "testField", "I");
        assertTrue(fieldRules.containsKey(fieldKey1));
        assertTrue(fieldRules.get(fieldKey1).contains(AccessType.MUTABLE));

        EntryKey fieldKey2 = new EntryKey("net/example/TestClass", "testTransitiveField", "I");
        assertTrue(fieldRules.containsKey(fieldKey2));
        assertTrue(fieldRules.get(fieldKey2).contains(AccessType.MUTABLE));

        System.out.println("  Parsing directives verified successfully.");
    }

    public void testBytecodeTransformation() throws Exception {
        System.out.println("Running testBytecodeTransformation...");
        AccessWidener aw = new AccessWidener();
        String config = "accessWidener v1 named\n" +
                "accessible class net/example/MyClass\n" +
                "extendable method net/example/MyClass myMethod ()V\n" +
                "mutable field net/example/MyClass myField I\n";
        aw.parse(new StringReader(config));

        AccessWidenerCompiler compiler = new AccessWidenerCompiler();
        compiler.compile(aw);

        // 1. Create a dummy class net/example/MyClass with private/protected modifiers and final
        ClassWriter cw = new ClassWriter(0);
        cw.visit(Opcodes.V1_8, Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL, "net/example/MyClass", null, "java/lang/Object", null);

        // Add a private final field
        FieldVisitor fv = cw.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL, "myField", "I", null, null);
        fv.visitEnd();

        // Add a private final method
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL, "myMethod", "()V", null, null);
        mv.visitCode();
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(1, 1);
        mv.visitEnd();

        cw.visitEnd();
        byte[] originalBytes = cw.toByteArray();

        // 2. Transform the class bytes
        ClassReader reader = new ClassReader(originalBytes);
        ClassWriter writer = new ClassWriter(reader, 0);
        ClassVisitor visitor = compiler.compileVisitor(Opcodes.ASM9, writer, "net/example/MyClass");
        reader.accept(visitor, 0);
        byte[] transformedBytes = writer.toByteArray();

        // 3. Inspect transformed bytecode
        ClassReader trReader = new ClassReader(transformedBytes);
        ClassNode classNode = new ClassNode();
        trReader.accept(classNode, 0);

        // Class should be public and non-final
        assertFalse((classNode.access & Opcodes.ACC_PRIVATE) != 0, "Class should not be private");
        assertTrue((classNode.access & Opcodes.ACC_PUBLIC) != 0, "Class should be public");
        assertFalse((classNode.access & Opcodes.ACC_FINAL) != 0, "Class should not be final");

        // Field should be public and non-final
        boolean fieldChecked = false;
        for (FieldNode field : classNode.fields) {
            if ("myField".equals(field.name)) {
                assertFalse((field.access & Opcodes.ACC_PRIVATE) != 0, "Field should not be private");
                assertTrue((field.access & Opcodes.ACC_PUBLIC) != 0, "Field should be public");
                assertFalse((field.access & Opcodes.ACC_FINAL) != 0, "Field should not be final");
                fieldChecked = true;
            }
        }
        assertTrue(fieldChecked, "Field 'myField' not found");

        // Method should be protected/public and non-final
        boolean methodChecked = false;
        for (MethodNode method : classNode.methods) {
            if ("myMethod".equals(method.name) && "()V".equals(method.desc)) {
                assertFalse((method.access & Opcodes.ACC_PRIVATE) != 0, "Method should not be private");
                assertTrue((method.access & Opcodes.ACC_PROTECTED) != 0 || (method.access & Opcodes.ACC_PUBLIC) != 0, "Method should be public or protected");
                assertFalse((method.access & Opcodes.ACC_FINAL) != 0, "Method should not be final");
                methodChecked = true;
            }
        }
        assertTrue(methodChecked, "Method 'myMethod' not found");

        System.out.println("  Bytecode transformation verified successfully.");
    }

    private void assertEquals(Object expected, Object actual) {
        if (expected == null && actual == null) return;
        if (expected != null && expected.equals(actual)) return;
        throw new AssertionError("Expected: " + expected + ", Actual: " + actual);
    }

    private void assertTrue(boolean condition) {
        if (!condition) throw new AssertionError("Expected true, got false");
    }

    private void assertTrue(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private void assertFalse(boolean condition, String message) {
        if (condition) throw new AssertionError(message);
    }
}
