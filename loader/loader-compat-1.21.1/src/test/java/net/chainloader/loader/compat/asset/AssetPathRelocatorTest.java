package net.chainloader.loader.compat.asset;

import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Unit tests for {@link AssetPathRelocator} validating:
 * 1. String-based path relocations (exact, prefix, and namespace remappings)
 * 2. ResourceLocation remappings
 * 3. Bytecode transformation of LDC string constants using ASM
 * 4. Integration with VirtualAssetPack via the PathRelocator interface
 */
public class AssetPathRelocatorTest {

    private AssetPathRelocator relocator;

    @BeforeEach
    public void setUp() {
        relocator = AssetPathRelocator.getInstance();
        // Register test specific mappings
        relocator.registerNamespaceMapping("old_mod", "new_mod");
        relocator.registerPathMapping("old_mod:textures/entity/skin.png", "new_mod:textures/entity/super_skin.png");
        relocator.registerPrefixMapping("old_mod:models/item/legacy_", "new_mod:models/item/modern_");
    }

    @Test
    public void testStringRelocation() {
        // Test exact mapping
        assertEquals("new_mod:textures/entity/super_skin.png", 
                relocator.relocateString("old_mod:textures/entity/skin.png"));

        // Test prefix mapping
        assertEquals("new_mod:models/item/modern_sword.json", 
                relocator.relocateString("old_mod:models/item/legacy_sword.json"));

        // Test namespace remapping
        assertEquals("new_mod:textures/block/stone.png", 
                relocator.relocateString("old_mod:textures/block/stone.png"));

        // Test unmapped string remains untouched
        assertEquals("another_mod:textures/block/dirt.png", 
                relocator.relocateString("another_mod:textures/block/dirt.png"));
    }

    @Test
    public void testResourceLocationRelocation() {
        ResourceLocation original = new ResourceLocation("old_mod", "textures/block/stone.png");
        ResourceLocation relocated = relocator.relocate(original);
        
        assertEquals("new_mod", relocated.getNamespace());
        assertEquals("textures/block/stone.png", relocated.getPath());
    }

    @Test
    public void testVirtualAssetPackIntegration() {
        VirtualAssetPack pack = new VirtualAssetPack("test-pack", false);
        relocator.registerTo(pack);

        // Prepopulate with a mock blockstate in "old_mod"
        pack.registerBlockstate("new_mod", "custom_block", "{}");

        // Look up using old namespace, relocator should transparently bridge
        ResourceLocation oldLoc = new ResourceLocation("old_mod", "blockstates/custom_block.json");
        assertNotNull(pack.getResource(PackType.CLIENT_RESOURCES, oldLoc), 
                "VirtualAssetPack should resolve old_mod request after AssetPathRelocator registration");
    }

    @Test
    public void testBytecodeRemapping() {
        // 1. Generate a mock class with ASM containing a string constant to remap
        ClassWriter cw = new ClassWriter(0);
        cw.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, "net/chainloader/loader/compat/asset/MockClass", null, "java/lang/Object", null);

        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC + Opcodes.ACC_STATIC, "getAsset", "()Ljava/lang/String;", null, null);
        mv.visitCode();
        // LDC with target old namespace/path
        mv.visitLdcInsn("old_mod:textures/block/stone.png");
        mv.visitInsn(Opcodes.ARETURN);
        mv.visitMaxs(1, 0);
        mv.visitEnd();

        cw.visitEnd();
        byte[] originalBytes = cw.toByteArray();

        // Verify the constant pool scanner detects it
        assertTrue(relocator.containsRelocationTargets(originalBytes), "Scanner should detect 'old_mod' in constant pool");

        // 2. Transform the class bytes
        byte[] transformedBytes = relocator.transform("net.chainloader.loader.compat.asset.MockClass", originalBytes);
        assertNotNull(transformedBytes);
        assertNotEquals(originalBytes.length, 0);

        // 3. Inspect transformed bytes to verify LDC is updated
        ClassReader cr = new ClassReader(transformedBytes);
        AtomicBoolean verified = new AtomicBoolean(false);
        cr.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitLdcInsn(Object value) {
                        if (value instanceof String) {
                            assertEquals("new_mod:textures/block/stone.png", value, "Bytecode LDC constant should be relocated");
                            verified.set(true);
                        }
                        super.visitLdcInsn(value);
                    }
                };
            }
        }, 0);

        assertTrue(verified.get(), "LDC verification visitor should have been invoked and validated");
    }
}
