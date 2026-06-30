package net.chainloader.loader.core;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class ModScanner {

    public static final class DiscoveredMod {
        public final ChainModMetadata metadata;
        public final File jarFile;
        public final String mainClassName;

        public DiscoveredMod(ChainModMetadata metadata, File jarFile, String mainClassName) {
            this.metadata = metadata;
            this.jarFile = jarFile;
            this.mainClassName = mainClassName;
        }
    }

    public static final class EventSubscriberInfo {
        public final String className;
        public final String modId;
        public final String bus;
        public final List<String> dists;

        public EventSubscriberInfo(String className, String modId, String bus, List<String> dists) {
            this.className = className;
            this.modId = modId;
            this.bus = bus;
            this.dists = dists;
        }
    }

    private static final class TempSubscriber {
        final String className;
        final String modIdField;
        final String bus;
        final List<String> dists;

        TempSubscriber(String className, String modIdField, String bus, List<String> dists) {
            this.className = className;
            this.modIdField = modIdField;
            this.bus = bus;
            this.dists = dists;
        }
    }

    private static final List<EventSubscriberInfo> EVENT_SUBSCRIBERS = new ArrayList<>();
    private static final Map<String, Object> MOD_EVENT_BUSES = new java.util.concurrent.ConcurrentHashMap<>();

    public static java.util.Set<Object> getModEventBuses() {
        return new java.util.HashSet<>(MOD_EVENT_BUSES.values());
    }

    private static final List<DiscoveredMod> DISCOVERED_MODS = new ArrayList<>();
    private static final java.util.Set<String> discoveredPacketChannels = new java.util.concurrent.ConcurrentHashMap<String, Boolean>().keySet(true);
    private static boolean initialized = false;

    public static java.util.Set<String> getDiscoveredPacketChannels() {
        return discoveredPacketChannels;
    }

    static {
        // Register ChainLoader Core
        ChainModMetadata.Builder cl = new ChainModMetadata.Builder();
        cl.id("chainloader");
        cl.name("ChainLoader Core");
        cl.version("1.0.0-SNAPSHOT");
        cl.description("The core bootstrap engine and unified modloader for Minecraft.");
        cl.originalLoaderType("chainloader");
        cl.addAuthor("ChainLoader Team");
        cl.license("MIT");
        DISCOVERED_MODS.add(new DiscoveredMod(cl.build(), null, null));

        // Register Example Mod
        ChainModMetadata.Builder ex = new ChainModMetadata.Builder();
        ex.id("example");
        ex.name("Example Mod");
        ex.version("1.0.0");
        ex.description("A sample mod demonstrating ChainLoader's cross-platform APIs.");
        ex.originalLoaderType("fabric");
        ex.addAuthor("Example Author");
        ex.license("MIT");
        ex.addEntrypoint("main", "net.example.ExampleMod");
        DISCOVERED_MODS.add(new DiscoveredMod(ex.build(), null, "net.example.ExampleMod"));
    }

    public static List<DiscoveredMod> getDiscoveredMods() {
        return DISCOVERED_MODS;
    }

    public static void scanAndRegisterMods(Path modsDir) {
        Logging.info("Starting dynamic mod scanning in %s", modsDir);
        if (!Files.exists(modsDir)) {
            Logging.warn("Mods directory %s does not exist. Skipping scan.", modsDir);
            return;
        }
        try {
            Files.list(modsDir).forEach(path -> {
                if (path.toString().endsWith(".jar")) {
                    scanJar(path);
                }
            });
        } catch (Exception e) {
            Logging.error("Failed to list mods directory", e);
        }
    }

    private static void scanJar(Path jarPath) {
        File file = jarPath.toFile();
        Logging.debug("Scanning", "Scanning JAR file: %s", file.getName());
        try (ZipFile zipFile = new ZipFile(file)) {
            ZipEntry fabricEntry = zipFile.getEntry("fabric.mod.json");
            ZipEntry forgeEntry = zipFile.getEntry("META-INF/mods.toml");
            ChainModMetadata metadata = null;

            if (fabricEntry != null) {
                Logging.debug("Scanning", "  Detected Fabric mod structure in %s", file.getName());
                String content = readEntryText(zipFile, fabricEntry);
                metadata = MetadataNormalizer.parseFabric(content);
                Logging.debug("Scanning", "  Parsed fabric.mod.json metadata for mod ID: %s", metadata.getId());
            } else if (forgeEntry != null) {
                Logging.debug("Scanning", "  Detected Forge/NeoForge mod structure in %s", file.getName());
                String content = readEntryText(zipFile, forgeEntry);
                metadata = MetadataNormalizer.parseForge(content);
                Logging.debug("Scanning", "  Parsed mods.toml metadata for mod ID: %s", metadata.getId());
            }

            // Scan classes for Forge/NeoForge @Mod and @EventBusSubscriber annotations
            String modClass = null;
            List<TempSubscriber> tempSubscribers = new ArrayList<>();
            java.util.Set<String> modStrings = new java.util.HashSet<>();
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            int scannedClassesCount = 0;
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.getName().endsWith(".class")) {
                    scannedClassesCount++;
                    try (InputStream is = zipFile.getInputStream(entry)) {
                        ClassReader cr = new ClassReader(is);
                        TempSubscriber sub = scanClassAnnotations(cr);
                        if (sub != null) {
                            Logging.debug("Scanning", "  Found @EventBusSubscriber: class %s, mod ID field %s, bus %s", sub.className, sub.modIdField, sub.bus);
                            tempSubscribers.add(sub);
                        }
                        String foundClass = scanForModAnnotation(cr);
                        if (foundClass != null) {
                            Logging.debug("Scanning", "  Found @Mod class: %s", foundClass);
                            modClass = foundClass;
                        }
                        scanClassStringConstants(cr, modStrings);
                    } catch (Exception e) {
                        // Skip corrupted/unreadable classes
                    }
                }
            }
            Logging.debug("Scanning", "  Scanned %d class files in JAR: %s", scannedClassesCount, file.getName());

            if (metadata != null || modClass != null) {
                String loaderType = "forge";
                String realModClass = null;
                if (modClass != null) {
                    if (modClass.startsWith("neoforge:")) {
                        loaderType = "neoforge";
                        realModClass = modClass.substring(9);
                    } else if (modClass.startsWith("forge:")) {
                        loaderType = "forge";
                        realModClass = modClass.substring(6);
                    } else {
                        realModClass = modClass;
                    }
                }

                if (metadata == null) {
                    // Synthesize metadata if we found a @Mod class but no mods.toml
                    String modId = realModClass.substring(realModClass.lastIndexOf('.') + 1).toLowerCase();
                    ChainModMetadata.Builder b = new ChainModMetadata.Builder();
                    b.id(modId);
                    b.name(modId);
                    b.version("1.0.0");
                    b.originalLoaderType(loaderType);
                    metadata = b.build();
                    Logging.debug("Scanning", "  Synthesized metadata for mod ID: %s", modId);
                } else if (loaderType.equals("neoforge") && metadata.getOriginalLoaderType().equals("forge")) {
                    // Reconstruct metadata if we found a NeoForge class but it was loaded as forge
                    ChainModMetadata.Builder b = new ChainModMetadata.Builder();
                    b.id(metadata.getId());
                    b.name(metadata.getName());
                    b.version(metadata.getVersion());
                    b.description(metadata.getDescription());
                    b.license(metadata.getLicense());
                    for (String author : metadata.getAuthors()) b.addAuthor(author);
                    for (Map.Entry<String, String> entry : metadata.getContactLinks().entrySet()) b.addContactLink(entry.getKey(), entry.getValue());
                    for (ChainModMetadata.ModDependency dep : metadata.getDependencies()) b.addDependency(dep.getModId(), dep.getVersionRequirement(), dep.isOptional());
                    if (metadata.getEntrypoints() != null) {
                        for (Map.Entry<String, List<String>> entry : metadata.getEntrypoints().entrySet()) {
                            for (String cName : entry.getValue()) b.addEntrypoint(entry.getKey(), cName);
                        }
                    }
                    for (String mixin : metadata.getMixins()) b.addMixin(mixin);
                    b.originalLoaderType("neoforge");
                    metadata = b.build();
                    Logging.debug("Scanning", "  Reconstructed NeoForge metadata for mod ID: %s", metadata.getId());
                }

                // If we have metadata, check if we can associate a mainClassName
                if (realModClass == null && metadata.getEntrypoints() != null) {
                    List<String> mainEPs = metadata.getEntrypoints().get("main");
                    if (mainEPs != null && !mainEPs.isEmpty()) {
                        realModClass = mainEPs.get(0);
                    }
                }

                // Deduplicate with built-in mods
                boolean duplicate = false;
                for (DiscoveredMod m : DISCOVERED_MODS) {
                    if (m.metadata.getId().equals(metadata.getId())) {
                        duplicate = true;
                        break;
                    }
                }

                if (!duplicate) {
                    DISCOVERED_MODS.add(new DiscoveredMod(metadata, file, realModClass));
                    Logging.info("Registered discovered mod: %s (%s) version %s (loader: %s, main: %s)",
                            metadata.getId(), metadata.getName(), metadata.getVersion(),
                            metadata.getOriginalLoaderType(), realModClass);

                    String resolvedModId = metadata.getId();
                    
                    // Register potential packet channels for this mod
                    for (String s : modStrings) {
                        if (s.contains(":") && s.length() < 128) {
                            int colonIdx = s.indexOf(':');
                            if (colonIdx > 0 && colonIdx < s.length() - 1) {
                                String namespace = s.substring(0, colonIdx);
                                String path = s.substring(colonIdx + 1);
                                if (namespace.matches("^[a-z0-9_.-]+$") && path.matches("^[a-z0-9_.-/]+$")) {
                                    if (!namespace.equals("minecraft") && !namespace.equals("neoforge") && 
                                        !namespace.equals("forge") && !namespace.equals("fabric") && !namespace.equals("c")) {
                                        Logging.debug("Scanning", "  Discovered potential packet channel: %s", s);
                                        discoveredPacketChannels.add(s);
                                    }
                                }
                            }
                        } else if (s.length() >= 2 && s.length() <= 64 && s.matches("^[a-z0-9_.-/]+$")) {
                            // Synthesize potential channel for this mod ID
                            if (!s.equals("minecraft") && !s.equals("neoforge") && !s.equals("forge") && !s.equals("fabric")) {
                                String synthesized = resolvedModId + ":" + s;
                                Logging.debug("Scanning", "  Synthesized potential packet channel: %s", synthesized);
                                discoveredPacketChannels.add(synthesized);
                            }
                        }
                    }

                    for (TempSubscriber temp : tempSubscribers) {
                        String modId = temp.modIdField.isEmpty() ? resolvedModId : temp.modIdField;
                        synchronized (EVENT_SUBSCRIBERS) {
                            EVENT_SUBSCRIBERS.add(new EventSubscriberInfo(temp.className, modId, temp.bus, temp.dists));
                        }
                        Logging.debug("Scanning", "  Added event subscriber %s for mod %s on bus %s", temp.className, modId, temp.bus);
                    }
                } else {
                    Logging.debug("Scanning", "  Skipped duplicate mod: %s", metadata.getId());
                }
            }
        } catch (Exception e) {
            Logging.error("Failed to scan JAR " + jarPath.getFileName(), e);
        }
    }

    private static String readEntryText(ZipFile zipFile, ZipEntry entry) throws Exception {
        try (InputStream is = zipFile.getInputStream(entry);
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[1024];
            int length;
            while ((length = is.read(buffer)) != -1) {
                baos.write(buffer, 0, length);
            }
            return baos.toString(StandardCharsets.UTF_8.name());
        }
    }

    private static String scanForModAnnotation(ClassReader cr) {
        final String[] modClassName = new String[2];
        cr.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                if (descriptor.equals("Lnet/minecraftforge/fml/common/Mod;")) {
                    modClassName[0] = cr.getClassName();
                } else if (descriptor.equals("Lnet/neoforged/fml/common/Mod;")) {
                    modClassName[1] = cr.getClassName();
                }
                return null;
            }
        }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        if (modClassName[1] != null) {
            return "neoforge:" + modClassName[1].replace('/', '.');
        }
        if (modClassName[0] != null) {
            return "forge:" + modClassName[0].replace('/', '.');
        }
        return null;
    }

    public static void initializeMods(ClassLoader classLoader) {
        System.out.println("[DEBUG MODSCANNER] ModScanner loaded by: " + ModScanner.class.getClassLoader());
        System.out.println("[DEBUG MODSCANNER] DISCOVERED_MODS size: " + DISCOVERED_MODS.size());
        for (DiscoveredMod m : DISCOVERED_MODS) {
            System.out.println("[DEBUG MODSCANNER]   - Mod ID: " + (m.metadata != null ? m.metadata.getId() : "null") + ", Class: " + m.getClass().getName() + ", Loader: " + m.getClass().getClassLoader());
        }
        if (initialized) {
            return;
        }
        initialized = true;
        Logging.info("Initializing mods...");
        net.chainloader.loader.core.gui.EarlyLoadingScreen.getInstance().updateProgress(85, "Initializing mods...");


        // Fetch Forge Mod Context Bus if present
        try {
            Class<?> ctxClass = Class.forName("net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext", true, classLoader);
            Method getMethod = ctxClass.getMethod("get");
            Object ctx = getMethod.invoke(null);
            Method getBusMethod = ctxClass.getMethod("getModEventBus");
            Object forgeBus = getBusMethod.invoke(ctx);
            if (forgeBus != null) {
                MOD_EVENT_BUSES.put("forge_context_bus", forgeBus);
                Logging.info("Registered Forge FMLJavaModLoadingContext mod event bus.");
            }
        } catch (Throwable t) {
            // Forge not present
        }

        for (DiscoveredMod mod : DISCOVERED_MODS) {
            // Built-in core loader has no initialization entrypoint
            if ("chainloader".equals(mod.metadata.getId())) {
                continue;
            }

            if (mod.mainClassName != null) {
                try {
                    String statusMsg = "Initializing mod: " + mod.metadata.getName() + " (" + mod.metadata.getId() + ")...";
                    Logging.info("  " + statusMsg);
                    net.chainloader.loader.core.gui.EarlyLoadingScreen.getInstance().updateProgress(85, statusMsg);
                    
                    // Log that we are searching for constructors
                    net.chainloader.loader.core.gui.EarlyLoadingScreen.getInstance().log(mod.metadata.getId(), "searching constructors");
                    Class<?> clazz = Class.forName(mod.mainClassName, true, classLoader);
                    
                    // Call constructor (handling dependency injection for EventBus, ModContainer, and Dist)
                    Constructor<?> ctor = null;
                    Object[] ctorArgs = null;

                    Constructor<?>[] ctors = clazz.getDeclaredConstructors();
                    java.util.Arrays.sort(ctors, (c1, c2) -> Integer.compare(c2.getParameterCount(), c1.getParameterCount()));

                    for (Constructor<?> c : ctors) {
                        try {
                            Class<?>[] paramTypes = c.getParameterTypes();
                            Object[] args = new Object[paramTypes.length];
                            boolean match = true;
                            for (int i = 0; i < paramTypes.length; i++) {
                                Class<?> type = paramTypes[i];
                                Object resolved = resolveConstructorArg(type, mod.metadata.getId(), classLoader);
                                if (resolved == null && type.isPrimitive()) {
                                    if (type == boolean.class) resolved = false;
                                    else if (type == int.class) resolved = 0;
                                    else if (type == float.class) resolved = 0.0f;
                                    else if (type == double.class) resolved = 0.0d;
                                    else if (type == long.class) resolved = 0L;
                                    else if (type == char.class) resolved = '\0';
                                    else if (type == short.class) resolved = (short)0;
                                    else if (type == byte.class) resolved = (byte)0;
                                }
                                args[i] = resolved;
                            }
                            c.setAccessible(true);
                            ctor = c;
                            ctorArgs = args;
                            break;
                        } catch (Exception e) {
                            // Try next constructor
                        }
                    }

                    if (ctor == null) {
                        Logging.error("  Could not find any invokable constructor for mod %s. Available constructors:", mod.metadata.getId());
                        for (Constructor<?> c : clazz.getDeclaredConstructors()) {
                            Logging.error("    %s", c.toGenericString());
                        }
                        throw new NoSuchMethodException("No matching constructor resolved");
                    }

                    // Log that we are instantiating
                    net.chainloader.loader.core.gui.EarlyLoadingScreen.getInstance().log(mod.metadata.getId(), "instantiating class");
                    Object instance = ctor.newInstance(ctorArgs);

                    // If it has onInitialize (Fabric), call it
                    if ("fabric".equals(mod.metadata.getOriginalLoaderType())) {
                        try {
                            Method initMethod = clazz.getMethod("onInitialize");
                            // Log that we are running onInitialize
                            net.chainloader.loader.core.gui.EarlyLoadingScreen.getInstance().log(mod.metadata.getId(), "running onInitialize");
                            initMethod.invoke(instance);
                            Logging.info("  Successfully invoked Fabric onInitialize() for %s", mod.metadata.getId());
                        } catch (NoSuchMethodException e) {
                            // Some Fabric mods might not have onInitialize directly or use another entrypoint type
                        }
                    } else {
                        Logging.info("  Successfully instantiated Forge mod class: %s", clazz.getName());
                    }
                } catch (ClassNotFoundException e) {
                    Logging.error("  Class not found for mod " + mod.metadata.getId() + ": " + mod.mainClassName);
                    net.chainloader.loader.core.gui.EarlyLoadingScreen.getInstance().logError(mod.metadata.getId(), "Class not found for mod " + mod.metadata.getId() + ": " + mod.mainClassName, e);
                } catch (Throwable t) {
                    Logging.error("  Failed to initialize mod " + mod.metadata.getId(), t);
                    net.chainloader.loader.core.gui.EarlyLoadingScreen.getInstance().logError(mod.metadata.getId(), "Failed to initialize mod " + mod.metadata.getId(), t);
                }
            }
        }

        // Load and register all scanned @EventBusSubscriber classes
        Logging.info("Registering @EventBusSubscriber classes...");
        for (EventSubscriberInfo sub : EVENT_SUBSCRIBERS) {
            if (sub.dists != null && !sub.dists.isEmpty() && !sub.dists.contains("CLIENT")) {
                Logging.debug("Scanning", "  Skipping event subscriber class %s because it is not for CLIENT dist", sub.className);
                continue;
            }

            try {
                // Log that we are registering event subscribers
                net.chainloader.loader.core.gui.EarlyLoadingScreen.getInstance().log(sub.modId, "registering event subscribers");
                Class<?> clazz = Class.forName(sub.className, true, classLoader);
                Logging.info("  Registering subscriber: %s for mod: %s on bus: %s", sub.className, sub.modId, sub.bus);
                
                if ("MOD".equalsIgnoreCase(sub.bus)) {
                    Object modBus = MOD_EVENT_BUSES.get(sub.modId);
                    if (modBus != null) {
                        registerSubscriberToBus(modBus, clazz, sub.modId);
                    }
                    Object forgeContextBus = MOD_EVENT_BUSES.get("forge_context_bus");
                    if (forgeContextBus != null && forgeContextBus != modBus) {
                        registerSubscriberToBus(forgeContextBus, clazz, sub.modId);
                    }
                } else if ("FORGE".equalsIgnoreCase(sub.bus) || "GAME".equalsIgnoreCase(sub.bus)) {
                    try {
                        Class<?> neoforgeClass = Class.forName("net.neoforged.neoforge.common.NeoForge", true, classLoader);
                        Object neoforgeBus = neoforgeClass.getField("EVENT_BUS").get(null);
                        registerSubscriberToBus(neoforgeBus, clazz, sub.modId);
                    } catch (Throwable t) {}
                    try {
                        Class<?> forgeClass = Class.forName("net.minecraftforge.common.MinecraftForge", true, classLoader);
                        Object forgeBus = forgeClass.getField("EVENT_BUS").get(null);
                        registerSubscriberToBus(forgeBus, clazz, sub.modId);
                    } catch (Throwable t) {}
                }
            } catch (Throwable t) {
                Logging.error("  Failed to register event subscriber " + sub.className, t);
                net.chainloader.loader.core.gui.EarlyLoadingScreen.getInstance().logError(sub.modId, "Failed to register event subscriber: " + sub.className, t);
            }
        }

        // Fire RegisterEvent to all mod buses BEFORE this method returns.
        // This is critical: initializeMods is called from BuiltInRegistries.a()V BEFORE lt.c()V (freeze).
        // Mods that register blocks/items via @EventBusSubscriber + RegisterEvent need writable registries.
        try {
            Class<?> bridgeClass = Class.forName("net.chainloader.loader.compat.bridge.EventBridgeHelper", true, classLoader);
            java.lang.reflect.Method fireMethod = bridgeClass.getMethod("fireRegisterEvents");
            fireMethod.invoke(null);
        } catch (Throwable t) {
            Logging.error("Failed to fire RegisterEvents from ModScanner.initializeMods", t);
        }
    }

    private static Object resolveConstructorArg(Class<?> type, String modId, ClassLoader classLoader) {
        String name = type.getName();
        if ("net.neoforged.bus.api.IEventBus".equals(name)) {
            try {
                Class<?> neoforgeBusImpl = Class.forName("net.neoforged.bus.EventBus", true, classLoader);
                Object bus = neoforgeBusImpl.getDeclaredConstructor().newInstance();
                MOD_EVENT_BUSES.put(modId, bus);
                return bus;
            } catch (Exception e) {
                return null;
            }
        }
        if ("net.minecraftforge.eventbus.api.IEventBus".equals(name)) {
            try {
                Class<?> forgeBusImpl = Class.forName("net.minecraftforge.eventbus.api.EventBus", true, classLoader);
                Object bus = forgeBusImpl.getDeclaredConstructor().newInstance();
                MOD_EVENT_BUSES.put(modId, bus);
                return bus;
            } catch (Exception e) {
                return null;
            }
        }
        if ("net.neoforged.fml.ModContainer".equals(name)) {
            try {
                Class<?> mockContainerClass = Class.forName("net.neoforged.fml.MockModContainer", true, classLoader);
                return mockContainerClass.getDeclaredConstructor(String.class).newInstance(modId);
            } catch (Exception e) {
                return null;
            }
        }
        if ("net.minecraftforge.fml.ModContainer".equals(name)) {
            try {
                Class<?> mockContainerClass = Class.forName("net.minecraftforge.fml.MockModContainer", true, classLoader);
                return mockContainerClass.getDeclaredConstructor(String.class).newInstance(modId);
            } catch (Exception e) {
                return null;
            }
        }
        if ("net.neoforged.api.distmarker.Dist".equals(name)) {
            try {
                Class<?> distClass = Class.forName("net.neoforged.api.distmarker.Dist", true, classLoader);
                return Enum.valueOf((Class<Enum>) distClass, "CLIENT");
            } catch (Exception e) {
                return null;
            }
        }
        if ("net.minecraftforge.api.distmarker.Dist".equals(name)) {
            try {
                Class<?> distClass = Class.forName("net.minecraftforge.api.distmarker.Dist", true, classLoader);
                return Enum.valueOf((Class<Enum>) distClass, "CLIENT");
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    private static TempSubscriber scanClassAnnotations(ClassReader cr) {
        final TempSubscriber[] result = new TempSubscriber[1];
        String className = cr.getClassName().replace('/', '.');
        cr.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                if (descriptor.equals("Lnet/neoforged/fml/common/Mod$EventBusSubscriber;") ||
                    descriptor.equals("Lnet/minecraftforge/fml/common/Mod$EventBusSubscriber;")) {
                    
                    final String[] modId = {""};
                    final String[] bus = {"MOD"};
                    final List<String> dists = new ArrayList<>();

                    return new AnnotationVisitor(Opcodes.ASM9) {
                        @Override
                        public void visit(String name, Object value) {
                            if ("modid".equals(name)) {
                                modId[0] = (String) value;
                            }
                        }

                        @Override
                        public void visitEnum(String name, String descriptor, String value) {
                            if ("bus".equals(name)) {
                                bus[0] = value;
                            }
                        }

                        @Override
                        public AnnotationVisitor visitArray(String name) {
                            if ("value".equals(name)) {
                                return new AnnotationVisitor(Opcodes.ASM9) {
                                    @Override
                                    public void visitEnum(String name, String descriptor, String value) {
                                        dists.add(value);
                                    }
                                };
                            }
                            return null;
                        }

                        @Override
                        public void visitEnd() {
                            result[0] = new TempSubscriber(className, modId[0], bus[0], dists);
                        }
                    };
                }
                return null;
            }
        }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return result[0];
    }

    private static void registerSubscriberToBus(Object bus, Class<?> clazz, String modId) {
        try {
            Method registerMethod = bus.getClass().getMethod("register", Object.class);
            registerMethod.invoke(bus, clazz);
            Logging.debug("Scanning", "    Successfully registered to bus: %s", bus.getClass().getName());
        } catch (Throwable t) {
            Logging.error("    Failed to register " + clazz.getName() + " to bus " + bus.getClass().getName(), t);
            net.chainloader.loader.core.gui.EarlyLoadingScreen.getInstance().logError(modId, "Failed to register " + clazz.getName() + " to bus " + bus.getClass().getName(), t);
        }
    }

    private static void scanClassStringConstants(ClassReader cr, java.util.Set<String> constants) {
        cr.accept(new org.objectweb.asm.ClassVisitor(org.objectweb.asm.Opcodes.ASM9) {
            @Override
            public org.objectweb.asm.FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
                if (value instanceof String) {
                    constants.add((String) value);
                }
                return null;
            }
            
            @Override
            public org.objectweb.asm.MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                org.objectweb.asm.MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                return new org.objectweb.asm.MethodVisitor(org.objectweb.asm.Opcodes.ASM9, mv) {
                    @Override
                    public void visitLdcInsn(Object value) {
                        if (value instanceof String) {
                            constants.add((String) value);
                        }
                        super.visitLdcInsn(value);
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
    }
}

