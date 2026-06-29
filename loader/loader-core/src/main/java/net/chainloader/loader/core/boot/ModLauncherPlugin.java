package net.chainloader.loader.core.boot;

import cpw.mods.modlauncher.serviceapi.ILaunchPluginService;
import net.chainloader.loader.access.AccessWidener;
import net.chainloader.loader.core.ChainModMetadata;
import net.chainloader.loader.core.DependencyIsolator;
import net.chainloader.loader.transformer.BytecodeTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;

import java.nio.file.Path;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * ModLauncherPlugin implements CPW's {@link ILaunchPluginService} to integrate ChainLoader
 * with Forge/NeoForge's ModLauncher startup sequence.
 * 
 * This plugin hooks into the early launch cycle, registers ChainLoader's core transformation
 * pipeline (BytecodeTransformer for remapping, AccessWidener for access modifications),
 * and coordinates with:
 * 1. The Knot adapter ({@link KnotClassLoaderAdapter}) to bridge Forge and Fabric classloading.
 * 2. The dependency resolver ({@link UnifiedDependencyResolver}) to propagate resolved mod
 *    loading orders and establish boundaries.
 */
public class ModLauncherPlugin implements ILaunchPluginService {

    private static final Logger LOGGER = Logger.getLogger(ModLauncherPlugin.class.getName());
    private static final String PLUGIN_NAME = "chainloader-modlauncher-plugin";

    private final BytecodeTransformer bytecodeTransformer;
    private final AccessWidener accessWidener;
    private final DependencyIsolator dependencyIsolator;

    /**
     * Default constructor initializing core ChainLoader subsystems.
     */
    public ModLauncherPlugin() {
        this.bytecodeTransformer = new BytecodeTransformer();
        this.accessWidener = new AccessWidener();
        this.dependencyIsolator = new DependencyIsolator();
        LOGGER.info("[ModLauncherPlugin] ChainLoader launch plugin instance created.");
    }

    /**
     * Constructor allowing injection of specific subsystem instances.
     *
     * @param bytecodeTransformer the bytecode transformer to use
     * @param accessWidener       the access widener to use
     * @param dependencyIsolator   the dependency isolator to use
     */
    public ModLauncherPlugin(BytecodeTransformer bytecodeTransformer,
                              AccessWidener accessWidener,
                              DependencyIsolator dependencyIsolator) {
        this.bytecodeTransformer = bytecodeTransformer;
        this.accessWidener = accessWidener;
        this.dependencyIsolator = dependencyIsolator;
    }

    @Override
    public String name() {
        return PLUGIN_NAME;
    }

    /**
     * Initializes the launch sequence. Registers hooks with the UnifiedDependencyResolver
     * and KnotClassLoaderAdapter to enable cross-loader compatibility.
     */
    @Override
    public void initializeLaunch(ITransformerLoader transformerLoader, Map<String, List<Path>> specialPaths) {
        LOGGER.info("[ModLauncherPlugin] Initializing ModLauncher Integration Cycle...");

        // 1. Coordinate with the Knot adapter if it resides in the thread context classloader
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        if (classLoader instanceof KnotClassLoaderAdapter) {
            KnotClassLoaderAdapter adapter = (KnotClassLoaderAdapter) classLoader;
            LOGGER.info("[ModLauncherPlugin] KnotClassLoaderAdapter detected in context! Registering transformer bridge...");
            adapter.registerModLauncherTransformer(PLUGIN_NAME, (className, bytes) -> {
                LOGGER.fine("[ModLauncherPlugin] Transforming bytes for Knot via ModLauncher bridge: " + className);
                return transformBytes(className, bytes);
            });
        } else {
            LOGGER.info("[ModLauncherPlugin] Standalone ModLauncher environment (KnotClassLoaderAdapter not active).");
        }

        // 2. Coordinate with the UnifiedDependencyResolver
        LOGGER.info("[ModLauncherPlugin] Connecting to UnifiedDependencyResolver...");
        UnifiedDependencyResolver resolver = new UnifiedDependencyResolver();
        resolver.registerModLauncherHook(result -> {
            LOGGER.info("[ModLauncherPlugin] UnifiedDependencyResolver finished resolution!");
            for (ChainModMetadata mod : result.getResolvedMods()) {
                LOGGER.info(String.format("  - [Resolved] %s (v%s) [%s]",
                        mod.getId(), mod.getVersion(), mod.getOriginalLoaderType()));
                
                // Establish dependency isolation masking rules based on resolved metadata
                DependencyIsolator.MaskingRules rules = new DependencyIsolator.MaskingRules(mod.getId());
                // Mask the mod package prefix from parent delegation to ensure isolation
                rules.isolatePackage(mod.getId() + ".");
                dependencyIsolator.registerRules(mod.getId(), rules);
            }
        });

        // Resolve dependencies (bootstrapping with an empty mod list for the launcher plugin start)
        resolver.resolve(Collections.emptyList());
    }

    @Override
    public EnumSet<Phase> handlesClass(Type classType, boolean isEmpty) {
        String className = classType.getClassName();

        // Intercept Minecraft, Forge, NeoForge, and remapped packages in the BEFORE phase
        boolean shouldHandle = className.startsWith("net.minecraft.") ||
                               className.startsWith("net.minecraftforge.") ||
                               className.startsWith("net.neoforged.");

        if (!shouldHandle && bytecodeTransformer != null) {
            String internalName = classType.getInternalName();
            for (String mappedPrefix : bytecodeTransformer.getMappings().keySet()) {
                if (internalName.startsWith(mappedPrefix)) {
                    shouldHandle = true;
                    break;
                }
            }
        }

        if (shouldHandle) {
            return EnumSet.of(Phase.BEFORE);
        }

        return EnumSet.noneOf(Phase.class);
    }

    @Override
    public EnumSet<Phase> handlesClass(Type classType, boolean isEmpty, String reason) {
        return handlesClass(classType, isEmpty);
    }

    @Override
    public boolean processClass(Phase phase, ClassNode classNode, Type classType) {
        if (phase != Phase.BEFORE) {
            return false;
        }

        String className = classType.getClassName();
        try {
            // Write current ClassNode structure to byte array
            ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            classNode.accept(cw);
            byte[] originalBytes = cw.toByteArray();

            // Apply ChainLoader transformation pipeline
            byte[] transformedBytes = transformBytes(className, originalBytes);

            if (transformedBytes != originalBytes && transformedBytes.length > 0) {
                // Re-parse the transformed bytes back into a temporary ClassNode
                ClassReader cr = new ClassReader(transformedBytes);
                ClassNode newNode = new ClassNode();
                cr.accept(newNode, 0);

                // Replace target ClassNode contents in-place
                replaceClassNodeContents(classNode, newNode);
                LOGGER.fine("[ModLauncherPlugin] In-place transformed ClassNode: " + className);
                return true;
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to apply transformation pipeline to ClassNode: " + className, e);
        }

        return false;
    }

    @Override
    public int processClassWithFlags(Phase phase, ClassNode classNode, Type classType, String reason) {
        // Return 1 if modified, 0 otherwise
        return processClass(phase, classNode, classType) ? 1 : 0;
    }

    /**
     * Transforms class bytecode using the remapper and access widener.
     *
     * @param className  Fully qualified binary name of the class (dot format)
     * @param classBytes Original bytecode
     * @return the transformed bytecode, or original if unmodified
     */
    private byte[] transformBytes(String className, byte[] classBytes) {
        byte[] current = classBytes;

        // 1. Remap package layout to match target Minecraft versions
        if (bytecodeTransformer != null) {
            current = bytecodeTransformer.transform(className, current);
        }

        // 2. Widen member and class access modifiers
        if (accessWidener != null && current != null) {
            try {
                ClassReader reader = new ClassReader(current);
                ClassWriter writer = new ClassWriter(reader, 0);
                org.objectweb.asm.ClassVisitor visitor = accessWidener.createVisitor(writer);
                reader.accept(visitor, 0);
                current = writer.toByteArray();
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Failed to apply AccessWidener to class " + className, e);
            }
        }

        return current;
    }

    /**
     * Helper to replace the fields of the target ClassNode with the source ClassNode in-place.
     */
    private void replaceClassNodeContents(ClassNode target, ClassNode source) {
        target.version = source.version;
        target.access = source.access;
        target.name = source.name;
        target.signature = source.signature;
        target.superName = source.superName;
        target.interfaces = source.interfaces;
        target.sourceFile = source.sourceFile;
        target.sourceDebug = source.sourceDebug;
        target.module = source.module;
        target.outerClass = source.outerClass;
        target.outerMethod = source.outerMethod;
        target.outerMethodDesc = source.outerMethodDesc;
        target.visibleAnnotations = source.visibleAnnotations;
        target.invisibleAnnotations = source.invisibleAnnotations;
        target.visibleTypeAnnotations = source.visibleTypeAnnotations;
        target.invisibleTypeAnnotations = source.invisibleTypeAnnotations;
        target.attrs = source.attrs;
        target.innerClasses = source.innerClasses;
        target.nestHostClass = source.nestHostClass;
        target.nestMembers = source.nestMembers;
        target.permittedSubclasses = source.permittedSubclasses;
        target.recordComponents = source.recordComponents;
        target.fields = source.fields;
        target.methods = source.methods;
    }
}
