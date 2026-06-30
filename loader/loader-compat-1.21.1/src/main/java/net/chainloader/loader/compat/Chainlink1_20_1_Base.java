package net.chainloader.loader.compat;

import net.chainloader.loader.core.ChainClassLoader;
import net.chainloader.loader.transformer.BytecodeTransformer;
import org.objectweb.asm.*;
import java.util.Collection;
import java.util.List;

public abstract class Chainlink1_20_1_Base extends Chainlink1_21_1_Base {

    @Override
    public String getSupportedVersionRange() {
        return "[1.20, 1.20.1]";
    }

    @Override
    public void onWakeUp(ClassLoader classLoader) {
        System.out.println("[Chainlink 1.20.1 Base] Waking up compat module for loader: " + getSupportedLoaderType());
        super.onWakeUp(classLoader);
    }

    @Override
    public String mapMethod(String owner, String name, String descriptor) {
        BytecodeTransformer bt = BytecodeTransformer.getInstance();
        if (bt != null && bt.isBlockRecursiveCached(owner)) {
            if (descriptor.equals("(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/phys/BlockHitResult;)Lnet/minecraft/world/InteractionResult;") ||
                descriptor.equals("(Ldtc;Ldcw;Ljd;Lcmx;Lbqq;Lewy;)Lbqr;")) {
                if (name.equals("method_9534") || name.equals("m_6227_") || name.equals("use")) {
                    return "use_legacy";
                }
            }
            if (descriptor.equals("(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/BlockGetter;Ljava/util/List;Lnet/minecraft/world/item/TooltipFlag;)V") ||
                descriptor.equals("(Lcuq;Ldcc;Ljava/util/List;Lcwm;)V")) {
                if (name.equals("method_9568") || name.equals("m_5852_") || name.equals("appendHoverText")) {
                    return "appendHoverText_legacy";
                }
            }
        }
        if (bt != null && bt.isItemRecursiveCached(owner)) {
            if (descriptor.equals("(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/Level;Ljava/util/List;Lnet/minecraft/world/item/TooltipFlag;)V") ||
                descriptor.equals("(Lcuq;Ldcw;Ljava/util/List;Lcwm;)V")) {
                if (name.equals("method_7851") || name.equals("m_7373_") || name.equals("appendHoverText")) {
                    return "appendHoverText_legacy";
                }
            }
        }
        return super.mapMethod(owner, name, descriptor);
    }

    @Override
    public String mapClass(String className) {
        if ("qj".equals(className)) {
            return "net/minecraft/resources/ResourceKey";
        }
        return super.mapClass(className);
    }

    @Override
    public byte[] transform(String className, byte[] bytes) {
        bytes = super.transform(className, bytes);
        if (className == null || bytes == null || bytes.length == 0) {
            return bytes;
        }

        BytecodeTransformer bt = BytecodeTransformer.getInstance();
        if (bt == null) return bytes;

        try {
            boolean isBlock = bt.isBlockRecursiveCached(className);
            boolean isItem = bt.isItemRecursiveCached(className);

            boolean isVanilla = className.startsWith("net.minecraft.") || className.startsWith("com.mojang.") || className.indexOf('.') == -1;
            if (isVanilla || "dfy".equals(className) || "cul".equals(className) || "dtb".equals(className)) {
                isBlock = false;
                isItem = false;
            }

            if (isBlock || isItem) {
                ClassReader cr = new ClassReader(bytes);
                ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_MAXS);
                
                class CompatClassVisitor extends ClassVisitor {
                    private boolean hasLegacyUse = false;
                    private boolean hasLegacyAppendHoverText = false;
                    private boolean alreadyHasNewUse = false;
                    private boolean alreadyHasNewAppendHoverText = false;
                    private String appendHoverTextDesc = null;
                    private final String internalClassName;

                    public CompatClassVisitor(ClassVisitor cv, String internalClassName) {
                        super(Opcodes.ASM9, cv);
                        this.internalClassName = internalClassName;
                    }

                    @Override
                    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                        if ("use_legacy".equals(name) && "(Ldtc;Ldcw;Ljd;Lcmx;Lbqq;Lewy;)Lbqr;".equals(descriptor)) {
                            hasLegacyUse = true;
                        }
                        if ("appendHoverText_legacy".equals(name)) {
                            if ("(Lcuq;Ldcc;Ljava/util/List;Lcwm;)V".equals(descriptor) ||
                                "(Lcuq;Ldcw;Ljava/util/List;Lcwm;)V".equals(descriptor)) {
                                hasLegacyAppendHoverText = true;
                                appendHoverTextDesc = descriptor;
                            }
                        }

                        if (("a".equals(name) || "useWithoutItem".equals(name)) && 
                            ("(Ldtc;Ldcw;Ljd;Lcmx;Lewy;)Lbqr;".equals(descriptor) ||
                             descriptor.contains("Player;Lnet/minecraft/world/phys/BlockHitResult;)Lnet/minecraft/world/InteractionResult;"))) {
                            alreadyHasNewUse = true;
                        }
                        if (("a".equals(name) || "appendHoverText".equals(name)) && 
                            ("(Lcuq;Lcul$b;Ljava/util/List;Lcwm;)V".equals(descriptor) ||
                             descriptor.contains("Item$TooltipContext;Ljava/util/List;Lnet/minecraft/world/item/TooltipFlag;)V"))) {
                            alreadyHasNewAppendHoverText = true;
                        }

                        MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                        if (mv != null) {
                            return new MethodVisitor(Opcodes.ASM9, mv) {
                                @Override
                                public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
                                    if (opcode == Opcodes.INVOKESPECIAL) {
                                        if ("use_legacy".equals(name)) {
                                            if (owner.equals("net/minecraft/world/level/block/Block") || owner.equals("dfy") ||
                                                owner.equals("net/minecraft/world/level/block/state/BlockBehaviour") || owner.equals("dtb")) {
                                                super.visitMethodInsn(Opcodes.INVOKESTATIC, "net/chainloader/loader/compat/bridge/EventBridgeHelper", "superUse", "(Ldtb;Ldtc;Ldcw;Ljd;Lcmx;Lbqq;Lewy;)Lbqr;", false);
                                                return;
                                            }
                                        }
                                        if ("appendHoverText_legacy".equals(name)) {
                                            if (owner.equals("net/minecraft/world/item/Item") || owner.equals("cul")) {
                                                super.visitMethodInsn(Opcodes.INVOKESTATIC, "net/chainloader/loader/compat/bridge/EventBridgeHelper", "superAppendHoverText", "(Lcul;Lcuq;Ldcw;Ljava/util/List;Lcwm;)V", false);
                                                return;
                                            }
                                            if (owner.equals("net/minecraft/world/level/block/Block") || owner.equals("dfy")) {
                                                super.visitMethodInsn(Opcodes.INVOKESTATIC, "net/chainloader/loader/compat/bridge/EventBridgeHelper", "superAppendHoverText", "(Ldfy;Lcuq;Ldcc;Ljava/util/List;Lcwm;)V", false);
                                                return;
                                            }
                                        }
                                    }
                                    super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                                }
                            };
                        }
                        return null;
                    }

                    @Override
                    public void visitEnd() {
                        if (hasLegacyUse && !alreadyHasNewUse) {
                            System.out.println("[Compat 1.20.1] Injecting useWithoutItem bridge into block class: " + internalClassName);
                            MethodVisitor mv = super.visitMethod(Opcodes.ACC_PUBLIC, "a", "(Ldtc;Ldcw;Ljd;Lcmx;Lewy;)Lbqr;", null, null);
                            mv.visitCode();
                            mv.visitVarInsn(Opcodes.ALOAD, 0); // this
                            mv.visitVarInsn(Opcodes.ALOAD, 1); // BlockState
                            mv.visitVarInsn(Opcodes.ALOAD, 2); // Level
                            mv.visitVarInsn(Opcodes.ALOAD, 3); // BlockPos
                            mv.visitVarInsn(Opcodes.ALOAD, 4); // Player
                            mv.visitFieldInsn(Opcodes.GETSTATIC, "bqq", "a", "Lbqq;"); // InteractionHand.MAIN_HAND
                            mv.visitVarInsn(Opcodes.ALOAD, 5); // BlockHitResult
                            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, internalClassName, "use_legacy", "(Ldtc;Ldcw;Ljd;Lcmx;Lbqq;Lewy;)Lbqr;", false);
                            mv.visitInsn(Opcodes.ARETURN);
                            mv.visitMaxs(7, 6);
                            mv.visitEnd();
                        }
                        if (hasLegacyAppendHoverText && !alreadyHasNewAppendHoverText) {
                            System.out.println("[Compat 1.20.1] Injecting appendHoverText bridge into class: " + internalClassName);
                            MethodVisitor mv = super.visitMethod(Opcodes.ACC_PUBLIC, "a", "(Lcuq;Lcul$b;Ljava/util/List;Lcwm;)V", "Ljava/util/List<Lwz;>;", null);
                            mv.visitCode();
                            mv.visitVarInsn(Opcodes.ALOAD, 0); // this
                            mv.visitVarInsn(Opcodes.ALOAD, 1); // ItemStack
                            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "net/chainloader/loader/compat/bridge/EventBridgeHelper", "getClientLevel", "()Ldcw;", false);
                            mv.visitVarInsn(Opcodes.ALOAD, 3); // List
                            mv.visitVarInsn(Opcodes.ALOAD, 4); // TooltipFlag
                            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, internalClassName, "appendHoverText_legacy", appendHoverTextDesc, false);
                            mv.visitInsn(Opcodes.RETURN);
                            mv.visitMaxs(5, 5);
                            mv.visitEnd();
                        }
                        super.visitEnd();
                    }
                }
                
                String internalClassName = className.replace('.', '/');
                cr.accept(new CompatClassVisitor(cw, internalClassName), 0);
                return cw.toByteArray();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return bytes;
    }
}
