package net.chainloader.loader.core.boot;

import net.chainloader.loader.access.AccessWidener;
import net.chainloader.loader.core.ChainClassLoader;
import net.chainloader.loader.core.DependencyIsolator;
import net.chainloader.loader.mixin.MixinPatcher;
import net.chainloader.loader.transformer.BytecodeTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Opcodes;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.security.CodeSigner;
import java.security.CodeSource;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * KnotClassLoaderAdapter acts as a bridge and compatibility adapter for Fabric's Knot classloader.
 * It integrates with ChainLoader's transformation pipeline, including package remapping (BytecodeTransformer),
 * access widening (AccessWidener), Mixin injection target patching (MixinPatcher), and dependency isolation (DependencyIsolator).
 *
 * This adapter intercepts Fabric's classloading process, mimicking Knot's internal structure while
 * delegating actual bytecode modifications and resolution logic to the respective subsystems.
 */
public class KnotClassLoaderAdapter extends ChainClassLoader {
    
    static {
        registerAsParallelCapable();
    }
    
    private static final Logger LOGGER = Logger.getLogger(KnotClassLoaderAdapter.class.getName());
    
    private final DependencyIsolator dependencyIsolator;
    private final BytecodeTransformer bytecodeTransformer;
    private final MixinPatcher mixinPatcher;
    private final AccessWidener accessWidener;
    
    // Support for ModLauncher coordination
    private final List<ModLauncherTransformer> modLauncherTransformers = new ArrayList<>();
    
    // Cache for post-transformation class bytes to optimize load times and support re-entrance
    private final Map<String, byte[]> postTransformationBytesCache = new ConcurrentHashMap<>();
    
    // Shadow copy of masking rules for isolation check if reflection fails
    private final Map<String, DependencyIsolator.MaskingRules> shadowMaskingRules = new ConcurrentHashMap<>();

    /**
     * Functional interface for ModLauncher transformers to coordinate with the Forge/NeoForge subagent.
     */
    @FunctionalInterface
    public interface ModLauncherTransformer {
        /**
         * Transforms the class bytes using ModLauncher's pipeline.
         *
         * @param className the class name (e.g. "net.minecraft.client.Minecraft")
         * @param classBytes the original bytecode
         * @return the transformed bytecode, or the original bytes if unchanged
         */
        byte[] transform(String className, byte[] classBytes);
    }

    /**
     * Constructs a new KnotClassLoaderAdapter.
     *
     * @param urls               the initial classpath URLs
     * @param parent             the parent classloader
     * @param dependencyIsolator the dependency isolator instance
     * @param bytecodeTransformer the bytecode package remapper
     * @param mixinPatcher        the mixin injection target patcher
     * @param accessWidener      the access widener visitor factory
     */
    public KnotClassLoaderAdapter(URL[] urls, ClassLoader parent,
                                 DependencyIsolator dependencyIsolator,
                                 BytecodeTransformer bytecodeTransformer,
                                 MixinPatcher mixinPatcher,
                                 AccessWidener accessWidener) {
        super(urls, parent);
        this.dependencyIsolator = dependencyIsolator != null ? dependencyIsolator : new DependencyIsolator();
        this.bytecodeTransformer = bytecodeTransformer != null ? bytecodeTransformer : new BytecodeTransformer();
        this.mixinPatcher = mixinPatcher != null ? mixinPatcher : new MixinPatcher();
        this.accessWidener = accessWidener != null ? accessWidener : new AccessWidener();
        
        initializeAdapter();
    }

    /**
     * Convenience constructor with default instances of the subsystems.
     */
    public KnotClassLoaderAdapter(URL[] urls, ClassLoader parent) {
        this(urls, parent, new DependencyIsolator(), new BytecodeTransformer(), new MixinPatcher(), new AccessWidener());
    }

    private void initializeAdapter() {
        LOGGER.info("[KnotClassLoaderAdapter] Initializing Fabric Knot ClassLoader Adapter Hooks...");
        
        // 1. Bytecode Relocation (Remapper)
        this.addTransformer((className, bytes) -> {
            if (bytecodeTransformer != null) {
                return bytecodeTransformer.transform(className, bytes);
            }
            return bytes;
        });

        // 2. Access Widener
        this.addTransformer((className, bytes) -> {
            if (accessWidener != null) {
                try {
                    ClassReader reader = new ClassReader(bytes);
                    ClassWriter writer = new ClassWriter(reader, 0);
                    ClassVisitor visitor = accessWidener.createVisitor(writer);
                    reader.accept(visitor, 0);
                    return writer.toByteArray();
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Failed to apply AccessWidener to class " + className, e);
                }
            }
            return bytes;
        });

        // 3. Mixin Injection Target Patching
        this.addTransformer((className, bytes) -> {
            if (mixinPatcher != null && (className.contains("Mixin") || className.contains(".mixin."))) {
                try {
                    return patchMixinBytecode(className, bytes);
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Failed to patch Mixin injector targets in " + className, e);
                }
            }
            return bytes;
        });

        // 4. ModLauncher Integration Pipeline
        this.addTransformer((className, bytes) -> {
            byte[] current = bytes;
            synchronized (modLauncherTransformers) {
                for (ModLauncherTransformer transformer : modLauncherTransformers) {
                    byte[] transformed = transformer.transform(className, current);
                    if (transformed != null) {
                        current = transformed;
                    }
                }
            }
            return current;
        });
        
        // 5. Cache final post-transformation bytes for Fabric compatibility queries
        this.addTransformer((className, bytes) -> {
            postTransformationBytesCache.put(className, bytes);
            return bytes;
        });
    }

    /**
     * Intercepts Mixin class loading to rewrite method descriptors on the fly using ASM.
     */
    private byte[] patchMixinBytecode(String className, byte[] bytes) {
        ClassReader reader = new ClassReader(bytes);
        ClassWriter writer = new ClassWriter(reader, 0);
        ClassVisitor visitor = new ClassVisitor(Opcodes.ASM9, writer) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                return new MethodVisitor(Opcodes.ASM9, mv) {
                    @Override
                    public AnnotationVisitor visitAnnotation(String annotationDescriptor, boolean visible) {
                        AnnotationVisitor av = super.visitAnnotation(annotationDescriptor, visible);
                        if (isMixinInjector(annotationDescriptor)) {
                            return new AnnotationVisitor(Opcodes.ASM9, av) {
                                @Override
                                public void visit(String key, Object value) {
                                    if ("method".equals(key) && value instanceof String) {
                                        String original = (String) value;
                                        String patched = mixinPatcher.patchInjectionTarget(original);
                                        super.visit(key, patched);
                                    } else {
                                        super.visit(key, value);
                                    }
                                }

                                @Override
                                public AnnotationVisitor visitArray(String key) {
                                    AnnotationVisitor arrayAv = super.visitArray(key);
                                    if ("method".equals(key)) {
                                        return new AnnotationVisitor(Opcodes.ASM9, arrayAv) {
                                            @Override
                                            public void visit(String name, Object value) {
                                                if (value instanceof String) {
                                                    String original = (String) value;
                                                    String patched = mixinPatcher.patchInjectionTarget(original);
                                                    super.visit(name, patched);
                                                } else {
                                                    super.visit(name, value);
                                                }
                                            }
                                        };
                                    }
                                    return arrayAv;
                                }
                            };
                        }
                        return av;
                    }
                };
            }
        };
        reader.accept(visitor, 0);
        return writer.toByteArray();
    }

    private boolean isMixinInjector(String desc) {
        return desc.equals("Lorg/spongepowered/asm/mixin/injection/Inject;")
            || desc.equals("Lorg/spongepowered/asm/mixin/injection/Redirect;")
            || desc.equals("Lorg/spongepowered/asm/mixin/injection/ModifyArg;")
            || desc.equals("Lorg/spongepowered/asm/mixin/injection/ModifyArgs;")
            || desc.equals("Lorg/spongepowered/asm/mixin/injection/ModifyConstant;")
            || desc.equals("Lorg/spongepowered/asm/mixin/injection/ModifyVariable;");
    }

    /**
     * Registers shadow masking rules for a mod to ensure correct delegation boundaries
     * without relying solely on reflection.
     */
    public void registerShadowMaskingRules(String modId, DependencyIsolator.MaskingRules rules) {
        shadowMaskingRules.put(modId, rules);
        if (dependencyIsolator != null) {
            dependencyIsolator.registerRules(modId, rules);
        }
    }

    /**
     * Integrates with DependencyIsolator to enforce mod library isolation rules.
     * Overrides ChainClassLoader's parent delegation decision.
     */
    @Override
    protected boolean shouldDelegateToParent(String name) {
        // Retrieve masking rules mapping from dependencyIsolator
        Map<String, DependencyIsolator.MaskingRules> rulesMap = getActiveMaskingRules();
        
        for (DependencyIsolator.MaskingRules rules : rulesMap.values()) {
            // If the dependency resolver subagent has flagged this class package as isolated,
            // we bypass parent delegation so it can be loaded directly within the mod context.
            if (rules.shouldIsolate(name)) {
                LOGGER.log(Level.FINE, "[KnotClassLoaderAdapter] Bypassing parent delegation for isolated class: {0}", name);
                return false;
            }
            // If explicitly shared, force parent loading.
            if (rules.getSharedPackages().stream().anyMatch(name::startsWith)) {
                LOGGER.log(Level.FINE, "[KnotClassLoaderAdapter] Delegating to parent for shared class: {0}", name);
                return true;
            }
        }
        
        return super.shouldDelegateToParent(name);
    }

    private Map<String, DependencyIsolator.MaskingRules> getActiveMaskingRules() {
        Map<String, DependencyIsolator.MaskingRules> rulesMap = new HashMap<>(shadowMaskingRules);
        if (dependencyIsolator != null) {
            Map<String, DependencyIsolator.MaskingRules> mainMap = dependencyIsolator.getModRules();
            if (mainMap != null) {
                rulesMap.putAll(mainMap);
            }
        }
        return rulesMap;
    }

    // ==========================================
    // ModLauncher (Forge/NeoForge) Coordination API
    // ==========================================

    /**
     * Registers a ModLauncher transformer. Called by the ModLauncher subagent.
     */
    public void registerModLauncherTransformer(String transformerId, ModLauncherTransformer transformer) {
        synchronized (modLauncherTransformers) {
            modLauncherTransformers.add(transformer);
            LOGGER.info("[KnotClassLoaderAdapter] Registered ModLauncher transformer: " + transformerId);
        }
    }

    // ==========================================
    // Fabric KnotClassLoader Compatibility Hooks (Reflective API)
    // ==========================================

    /**
     * Mockup of Fabric's getDelegate() API.
     * Fabric Loader often calls this to obtain the actual loader delegate.
     *
     * @return this adapter instance acting as the loader delegate.
     */
    public ClassLoader getDelegate() {
        return this;
    }

    /**
     * Mockup of Fabric's getRawClassBytes(String) API.
     * Retrieves the original untransformed bytecode for a class resource.
     *
     * @param name Binary name of the class (dot format)
     * @return the raw bytes of the class, or null if not found
     * @throws IOException if class cannot be read
     */
    public byte[] getRawClassBytes(String name) throws IOException {
        String resourcePath = name.replace('.', '/') + ".class";
        URL resourceUrl = findResource(resourcePath);
        if (resourceUrl == null) {
            resourceUrl = getResource(resourcePath);
        }
        if (resourceUrl == null) {
            return null;
        }
        
        try (InputStream is = resourceUrl.openStream();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = is.read(buffer)) != -1) {
                baos.write(buffer, 0, read);
            }
            return baos.toByteArray();
        }
    }

    /**
     * Retrieves the post-transformation bytecode.
     * Useful for debugging, mixin exporting, or verification by other compatibility shims.
     *
     * @param name Binary name of the class
     * @return the transformed class bytes, or null if not loaded yet
     */
    public byte[] getPostMixinClassBytes(String name) {
        return postTransformationBytesCache.get(name);
    }

    /**
     * Mockup of Fabric's loadIntoKnot(String) API.
     * Directly loads a class into this class loader's context.
     *
     * @param name Binary name of the class
     * @return the loaded Class object
     * @throws ClassNotFoundException if class not found
     */
    public Class<?> loadIntoKnot(String name) throws ClassNotFoundException {
        return loadClass(name);
    }

    /**
     * Dynamic URL extension. Overridden to log classloader additions.
     */
    @Override
    public void addURL(URL url) {
        super.addURL(url);
        LOGGER.log(Level.INFO, "[KnotClassLoaderAdapter] Classpath extended with URL: {0}", url);
    }

    /**
     * Checks if a class has already been loaded by this ClassLoader.
     *
     * @param name Binary name of the class
     * @return true if loaded, false otherwise
     */
    public boolean isClassLoaded(String name) {
        return findLoadedClass(name) != null;
    }
}
