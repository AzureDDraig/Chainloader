package net.chainloader.loader.compat;

import net.chainloader.loader.core.ChainClassLoader;
import net.chainloader.loader.transformer.BytecodeTransformer;
import org.objectweb.asm.*;

import java.util.Collection;
import java.util.List;

public abstract class Chainlink1_19_3_Base extends Chainlink1_20_1_Base {

    @Override
    public String getSupportedVersionRange() {
        return "[1.19.3, 1.19.4]";
    }

    @Override
    public void onWakeUp(ClassLoader classLoader) {
        System.out.println("[Chainlink 1.19.3 Base] Waking up compat module for loader: " + getSupportedLoaderType());
        super.onWakeUp(classLoader);
    }

    @Override
    public String mapMethod(String owner, String name, String descriptor) {
        return super.mapMethod(owner, name, descriptor);
    }

    @Override
    public String mapField(String owner, String name, String descriptor) {
        return super.mapField(owner, name, descriptor);
    }

    @Override
    public String mapClass(String className) {
        return super.mapClass(className);
    }

    @Override
    public byte[] transform(String className, byte[] bytes) {
        bytes = super.transform(className, bytes);
        if (className == null || bytes == null || bytes.length == 0) {
            return bytes;
        }

        String editBoxClass = "net.minecraft.client.gui.components.EditBox";
        BytecodeTransformer bt = BytecodeTransformer.getInstance();
        if (bt != null) {
            editBoxClass = bt.mapClassName(editBoxClass);
        }

        if (editBoxClass.equals(className)) {
            return addTickMethod(bytes);
        }

        return bytes;
    }

    private byte[] addTickMethod(byte[] bytes) {
        System.out.println("[Chainlink 1.19.3] Injecting stub tick() method (tick, m_94120_, method_1865) into EditBox...");
        try {
            ClassReader cr = new ClassReader(bytes);
            ClassWriter cw = new ClassWriter(cr, 0);
            ClassVisitor cv = new ClassVisitor(Opcodes.ASM9, cw) {
                private boolean hasSrgTick = false;
                private boolean hasIntermediaryTick = false;
                private boolean hasMojmapTick = false;

                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    if ("()V".equals(descriptor)) {
                        if ("m_94120_".equals(name)) hasSrgTick = true;
                        else if ("method_1865".equals(name)) hasIntermediaryTick = true;
                        else if ("tick".equals(name)) hasMojmapTick = true;
                    }
                    return super.visitMethod(access, name, descriptor, signature, exceptions);
                }

                @Override
                public void visitEnd() {
                    if (!hasSrgTick) {
                        MethodVisitor mv = super.visitMethod(Opcodes.ACC_PUBLIC, "m_94120_", "()V", null, null);
                        mv.visitCode();
                        mv.visitInsn(Opcodes.RETURN);
                        mv.visitMaxs(0, 1);
                        mv.visitEnd();
                    }
                    if (!hasIntermediaryTick) {
                        MethodVisitor mv = super.visitMethod(Opcodes.ACC_PUBLIC, "method_1865", "()V", null, null);
                        mv.visitCode();
                        mv.visitInsn(Opcodes.RETURN);
                        mv.visitMaxs(0, 1);
                        mv.visitEnd();
                    }
                    if (!hasMojmapTick) {
                        MethodVisitor mv = super.visitMethod(Opcodes.ACC_PUBLIC, "tick", "()V", null, null);
                        mv.visitCode();
                        mv.visitInsn(Opcodes.RETURN);
                        mv.visitMaxs(0, 1);
                        mv.visitEnd();
                    }
                    super.visitEnd();
                }
            };
            cr.accept(cv, 0);
            return cw.toByteArray();
        } catch (Exception e) {
            e.printStackTrace();
            return bytes;
        }
    }

    @Override
    public Collection<String> getSelfLoadedPackages() {
        return List.of();
    }

    @Override
    public Collection<String> getRemapTargetMarkers() {
        return List.of();
    }

    @Override
    public boolean isScreenClass(String internalName) {
        return super.isScreenClass(internalName);
    }

    @Override
    public boolean isWidgetClass(String internalName) {
        return super.isWidgetClass(internalName);
    }

    @Override
    public boolean isListenerClass(String internalName) {
        return super.isListenerClass(internalName);
    }

    @Override
    public Object interceptSetScreen(Object screen) {
        return screen;
    }

    @Override
    public void onInitTitleScreen(Object titleScreen) {}

    @Override
    public void onRenderTitleScreen(Object titleScreen, Object guiGraphics) {}

    @Override
    public void onInitPauseScreen(Object pauseScreen) {}
}
