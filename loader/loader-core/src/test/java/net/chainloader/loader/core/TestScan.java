package net.chainloader.loader.core;

import java.io.File;
import java.net.URL;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class TestScan {
    public static void main(String[] args) {
        System.out.println("==================================================");
        System.out.println("  ChainLoader Mod Initialization Test Runner");
        System.out.println("==================================================");

        // 1. Scan mods
        ModScanner.scanAndRegisterMods(Paths.get("mods"));
        System.out.println("Discovered Mods Count: " + ModScanner.getDiscoveredMods().size());
        for (ModScanner.DiscoveredMod mod : ModScanner.getDiscoveredMods()) {
            System.out.println("- Mod ID: " + mod.metadata.getId());
            System.out.println("  Name: " + mod.metadata.getName());
            System.out.println("  Version: " + mod.metadata.getVersion());
            System.out.println("  Loader: " + mod.metadata.getOriginalLoaderType());
            System.out.println("  Main Class: " + mod.mainClassName);
        }

        // 2. Build classpath URLs
        List<URL> urls = new ArrayList<>();
        // Add current JVM classpath entries (like bin/ and lib/*) to the ChainClassLoader first (so stubs take precedence)
        String classPath = System.getProperty("java.class.path");
        if (classPath != null) {
            for (String entry : classPath.split(java.io.File.pathSeparator)) {
                try {
                    urls.add(new java.io.File(entry).toURI().toURL());
                } catch (Exception e) {
                    // Ignore
                }
            }
        }

        for (ModScanner.DiscoveredMod mod : ModScanner.getDiscoveredMods()) {
            if (mod.jarFile != null) {
                try {
                    urls.add(mod.jarFile.toURI().toURL());
                } catch (Exception e) {
                    System.err.println("Failed to convert jar to URL: " + e.getMessage());
                }
            }
        }

        // 3. Create ClassLoader
        ClassLoader parent = TestScan.class.getClassLoader();
        ChainClassLoader classLoader = new ChainClassLoader(urls.toArray(new URL[0]), parent);

        // 4. Register transformers (remapper & stripping)
        classLoader.addTransformer(new net.chainloader.loader.transformer.BytecodeTransformer()::transform);

        // ASM ClassTransformer to make ResourceLocation constructor public and BlockEntityType$BlockEntitySupplier public
        classLoader.addTransformer((className, bytes) -> {
            if ("akr".equals(className)) {
                try {
                    org.objectweb.asm.ClassReader cr = new org.objectweb.asm.ClassReader(bytes);
                    org.objectweb.asm.ClassWriter cw = new org.objectweb.asm.ClassWriter(cr, 0);
                    org.objectweb.asm.ClassVisitor cv = new org.objectweb.asm.ClassVisitor(org.objectweb.asm.Opcodes.ASM9, cw) {
                        @Override
                        public org.objectweb.asm.MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                            if ("<init>".equals(name)) {
                                access = (access & ~org.objectweb.asm.Opcodes.ACC_PRIVATE & ~org.objectweb.asm.Opcodes.ACC_PROTECTED) | org.objectweb.asm.Opcodes.ACC_PUBLIC;
                            }
                            return super.visitMethod(access, name, descriptor, signature, exceptions);
                        }
                    };
                    cr.accept(cv, 0);
                    return cw.toByteArray();
                } catch (Exception e) {
                    e.printStackTrace();
                    return bytes;
                }
            } else if ("dqj$a".equals(className)) {
                try {
                    org.objectweb.asm.ClassReader cr = new org.objectweb.asm.ClassReader(bytes);
                    org.objectweb.asm.ClassWriter cw = new org.objectweb.asm.ClassWriter(cr, 0);
                    org.objectweb.asm.ClassVisitor cv = new org.objectweb.asm.ClassVisitor(org.objectweb.asm.Opcodes.ASM9, cw) {
                        @Override
                        public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
                            access = (access & ~org.objectweb.asm.Opcodes.ACC_PRIVATE & ~org.objectweb.asm.Opcodes.ACC_PROTECTED) | org.objectweb.asm.Opcodes.ACC_PUBLIC;
                            super.visit(version, access, name, signature, superName, interfaces);
                        }
                    };
                    cr.accept(cv, 0);
                    return cw.toByteArray();
                } catch (Exception e) {
                    e.printStackTrace();
                    return bytes;
                }
            } else if ("crc".equals(className)) {
                try {
                    org.objectweb.asm.ClassReader cr = new org.objectweb.asm.ClassReader(bytes);
                    org.objectweb.asm.ClassWriter cw = new org.objectweb.asm.ClassWriter(cr, 0);
                    org.objectweb.asm.ClassVisitor cv = new org.objectweb.asm.ClassVisitor(org.objectweb.asm.Opcodes.ASM9, cw) {
                        @Override
                        public org.objectweb.asm.MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                            if ("<init>".equals(name)) {
                                access = (access & ~org.objectweb.asm.Opcodes.ACC_PRIVATE & ~org.objectweb.asm.Opcodes.ACC_PROTECTED) | org.objectweb.asm.Opcodes.ACC_PUBLIC;
                            }
                            return super.visitMethod(access, name, descriptor, signature, exceptions);
                        }
                        @Override
                        public void visitInnerClass(String name, String outerName, String innerName, int innerAccess) {
                            if ("crc$a".equals(name)) {
                                innerAccess = (innerAccess & ~org.objectweb.asm.Opcodes.ACC_PRIVATE & ~org.objectweb.asm.Opcodes.ACC_PROTECTED) | org.objectweb.asm.Opcodes.ACC_PUBLIC;
                            }
                            super.visitInnerClass(name, outerName, innerName, innerAccess);
                        }
                    };
                    cr.accept(cv, 0);
                    return cw.toByteArray();
                } catch (Exception e) {
                    e.printStackTrace();
                    return bytes;
                }
            } else if ("crc$a".equals(className)) {
                try {
                    org.objectweb.asm.ClassReader cr = new org.objectweb.asm.ClassReader(bytes);
                    org.objectweb.asm.ClassWriter cw = new org.objectweb.asm.ClassWriter(cr, 0);
                    org.objectweb.asm.ClassVisitor cv = new org.objectweb.asm.ClassVisitor(org.objectweb.asm.Opcodes.ASM9, cw) {
                        @Override
                        public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
                            access = (access & ~org.objectweb.asm.Opcodes.ACC_PRIVATE & ~org.objectweb.asm.Opcodes.ACC_PROTECTED) | org.objectweb.asm.Opcodes.ACC_PUBLIC;
                            super.visit(version, access, name, signature, superName, interfaces);
                        }
                        @Override
                        public void visitInnerClass(String name, String outerName, String innerName, int innerAccess) {
                            if ("crc$a".equals(name)) {
                                innerAccess = (innerAccess & ~org.objectweb.asm.Opcodes.ACC_PRIVATE & ~org.objectweb.asm.Opcodes.ACC_PROTECTED) | org.objectweb.asm.Opcodes.ACC_PUBLIC;
                            }
                            super.visitInnerClass(name, outerName, innerName, innerAccess);
                        }
                    };
                    cr.accept(cv, 0);
                    return cw.toByteArray();
                } catch (Exception e) {
                    e.printStackTrace();
                    return bytes;
                }
            } else if ("cew".equals(className)) {
                try {
                    org.objectweb.asm.ClassReader cr = new org.objectweb.asm.ClassReader(bytes);
                    org.objectweb.asm.ClassWriter cw = new org.objectweb.asm.ClassWriter(cr, 0);
                    org.objectweb.asm.ClassVisitor cv = new org.objectweb.asm.ClassVisitor(org.objectweb.asm.Opcodes.ASM9, cw) {
                        @Override
                        public org.objectweb.asm.MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                            if ("a".equals(name) && "(Ljm;Ljava/util/Set;)V".equals(descriptor)) {
                                access = (access & ~org.objectweb.asm.Opcodes.ACC_PRIVATE & ~org.objectweb.asm.Opcodes.ACC_PROTECTED) | org.objectweb.asm.Opcodes.ACC_PUBLIC;
                            }
                            return super.visitMethod(access, name, descriptor, signature, exceptions);
                        }
                    };
                    cr.accept(cv, 0);
                    return cw.toByteArray();
                } catch (Exception e) {
                    e.printStackTrace();
                    return bytes;
                }
            } else if ("net.blay09.mods.balm.mixin.PoiTypesAccessor".equals(className)) {
                try {
                    org.objectweb.asm.ClassReader cr = new org.objectweb.asm.ClassReader(bytes);
                    org.objectweb.asm.ClassWriter cw = new org.objectweb.asm.ClassWriter(cr, org.objectweb.asm.ClassWriter.COMPUTE_FRAMES | org.objectweb.asm.ClassWriter.COMPUTE_MAXS);
                    org.objectweb.asm.ClassVisitor cv = new org.objectweb.asm.ClassVisitor(org.objectweb.asm.Opcodes.ASM9, cw) {
                        @Override
                        public org.objectweb.asm.MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                            if ("callRegisterBlockStates".equals(name)) {
                                org.objectweb.asm.MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                                mv.visitCode();
                                mv.visitVarInsn(org.objectweb.asm.Opcodes.ALOAD, 0);
                                mv.visitVarInsn(org.objectweb.asm.Opcodes.ALOAD, 1);
                                mv.visitMethodInsn(org.objectweb.asm.Opcodes.INVOKESTATIC, "cew", "a", descriptor, false);
                                mv.visitInsn(org.objectweb.asm.Opcodes.RETURN);
                                mv.visitMaxs(2, 2);
                                mv.visitEnd();
                                return new org.objectweb.asm.MethodVisitor(org.objectweb.asm.Opcodes.ASM9) {};
                            }
                            return super.visitMethod(access, name, descriptor, signature, exceptions);
                        }
                    };
                    cr.accept(cv, 0);
                    return cw.toByteArray();
                } catch (Exception e) {
                    e.printStackTrace();
                    return bytes;
                }
            }
            return bytes;
        });

        // Set context classloader so ServiceLoader and resources can be resolved correctly
        Thread.currentThread().setContextClassLoader(classLoader);

        // Bootstrap game registries so they are not empty/unbootstrapped
        try {
            System.out.println("Setting Minecraft SharedConstants game version...");
            Class<?> sharedConstants = classLoader.loadClass("ab");
            Class<?> detectedVersion = classLoader.loadClass("t");
            java.lang.reflect.Field builtInField = detectedVersion.getDeclaredField("a");
            builtInField.setAccessible(true);
            Object builtInVersion = builtInField.get(null);
            java.lang.reflect.Field currentVersionField = sharedConstants.getDeclaredField("bn");
            currentVersionField.setAccessible(true);
            currentVersionField.set(null, builtInVersion);
            System.out.println("Minecraft SharedConstants game version set successfully.");
        } catch (Exception e) {
            System.err.println("Failed to set game version: " + e.getMessage());
            e.printStackTrace();
        }

        try {
            System.out.println("Bootstrapping Minecraft registries...");
            Class<?> bootstrapClass = classLoader.loadClass("net.minecraft.server.Bootstrap");
            java.lang.reflect.Method bootStrapMethod = bootstrapClass.getMethod("a");
            bootStrapMethod.invoke(null);
            System.out.println("Minecraft registries bootstrapped successfully.");

            // Unfreeze registries after bootstrap so mods can register custom entries
            try {
                System.out.println("Unfreezing Minecraft registries...");
                Class<?> registriesClass = classLoader.loadClass("lt");
                Class<?> mappedRegistryClass = classLoader.loadClass("ju");
                java.lang.reflect.Field frozenField = mappedRegistryClass.getDeclaredField("l");
                frozenField.setAccessible(true);
                java.lang.reflect.Field intrusiveHoldersField = mappedRegistryClass.getDeclaredField("m");
                intrusiveHoldersField.setAccessible(true);

                int processedCount = 0;
                int totalFields = 0;
                for (java.lang.reflect.Field field : registriesClass.getDeclaredFields()) {
                    if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                        totalFields++;
                        try {
                            field.setAccessible(true);
                            Object registryObj = field.get(null);
                            if (registryObj != null) {
                                boolean isMappedRegistry = mappedRegistryClass.isInstance(registryObj);
                                if (isMappedRegistry) {
                                    // Get registry key
                                    Object registryKey = mappedRegistryClass.getMethod("d").invoke(registryObj);
                                    Object resourceLocation = registryKey.getClass().getMethod("a").invoke(registryKey);
                                    String registryName = resourceLocation.toString();
                                    System.out.println("Field: " + field.getName() + ", key: " + registryName + ", class: " + registryObj.getClass().getName());
                                    
                                    frozenField.set(registryObj, false);
                                    Object existingHolders = intrusiveHoldersField.get(registryObj);
                                    if (existingHolders == null) {
                                        // Only set IdentityHashMap for registries that require intrusive holders
                                        boolean requiresIntrusive = registryName.equals("minecraft:block") || 
                                                                    registryName.equals("minecraft:item") || 
                                                                    registryName.equals("minecraft:fluid") || 
                                                                    registryName.equals("minecraft:entity_type") || 
                                                                    registryName.equals("minecraft:block_entity_type") || 
                                                                    registryName.equals("minecraft:game_event");
                                        if (requiresIntrusive) {
                                            intrusiveHoldersField.set(registryObj, new java.util.IdentityHashMap<>());
                                            System.out.println("  - Set new IdentityHashMap for intrusive holders (required).");
                                        }
                                    }
                                    processedCount++;
                                }
                            } else {
                                System.out.println("Field: " + field.getName() + " is null");
                            }
                        } catch (Exception e) {
                            System.out.println("Error processing field " + field.getName() + ": " + e.toString());
                        }
                    }
                }
                System.out.println("Minecraft registries unfrozen successfully. Processed " + processedCount + " of " + totalFields + " static fields.");
            } catch (Exception e) {
                System.err.println("Failed to unfreeze registries: " + e.getMessage());
                e.printStackTrace();
            }
        } catch (Exception e) {
            System.err.println("Failed to bootstrap registries: " + e.getMessage());
            e.printStackTrace();
        }

        // 5. Initialize mods
        System.out.println("\nInitializing mods...");
        ModScanner.initializeMods(classLoader);
        System.out.println("==================================================");
        System.out.println("  Test Run Completed!");
        System.out.println("==================================================");
    }
}
