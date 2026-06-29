package net.chainloader.loader.core;

import java.io.File;
import java.io.PrintWriter;
import java.net.URL;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class HeadlessSimulator {
    public static void main(String[] args) {
        System.out.println("==================================================");
        System.out.println("  ChainLoader Headless Interaction Simulator");
        System.out.println("==================================================");
        System.out.println("[DEBUG] java.class.path = " + System.getProperty("java.class.path"));
        System.out.println("[DEBUG] HeadlessSimulator ClassLoader = " + HeadlessSimulator.class.getClassLoader());

        try {
            // 1. Scan mods
            ModScanner.scanAndRegisterMods(Paths.get("mods"));

            // Register Example NeoForge Mod programmatically for testing BEFORE registry freeze/bootstrap
            ChainModMetadata metadataObj = new ChainModMetadata.Builder()
                .id("exampleneoforge")
                .name("Example NeoForge Mod")
                .version("1.0.0")
                .originalLoaderType("neoforge")
                .build();
            ModScanner.getDiscoveredMods().add(new ModScanner.DiscoveredMod(
                metadataObj,
                null,
                "net.example.ExampleNeoForgeMod"
            ));

            System.out.println("Discovered Mods: " + ModScanner.getDiscoveredMods().size());

            // 2. Build classpath URLs
            List<URL> urls = new ArrayList<>();
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
            ClassLoader parent = HeadlessSimulator.class.getClassLoader();
            ChainClassLoader classLoader = new ChainClassLoader(urls.toArray(new URL[0]), parent);

            // 4. Register transformers
            net.chainloader.loader.core.ChainLauncher.registerTransformers(classLoader);

            Thread.currentThread().setContextClassLoader(classLoader);

            // 4b. Discover and wake up Chainlink compat modules (mirrors ChainLauncher.main logic)
            System.out.println("Discovering Chainlink compat modules...");
            java.util.ServiceLoader<net.chainloader.loader.compat.Chainlink> compatLoader =
                java.util.ServiceLoader.load(net.chainloader.loader.compat.Chainlink.class, classLoader);
            java.util.List<net.chainloader.loader.compat.Chainlink> availableLinks = new java.util.ArrayList<>();
            for (net.chainloader.loader.compat.Chainlink link : compatLoader) {
                availableLinks.add(link);
                System.out.println("[HeadlessSimulator] Discovered compat module: " + link.getClass().getName());
            }
            String gameVersion = "1.21.1";
            java.util.List<ModScanner.DiscoveredMod> discoveredMods = ModScanner.getDiscoveredMods();
            for (net.chainloader.loader.compat.Chainlink link : availableLinks) {
                boolean shouldWakeUp = false;
                
                if (link.getSupportedLoaderType().equalsIgnoreCase("chainloader") ||
                    link.getSupportedLoaderType().equalsIgnoreCase("fabric") ||
                    link.getSupportedLoaderType().equalsIgnoreCase("forge")) {
                    if (ChainLauncher.versionMatches(link.getSupportedVersionRange(), gameVersion)) {
                        shouldWakeUp = true;
                    }
                }
                
                if (!shouldWakeUp) {
                    for (ModScanner.DiscoveredMod mod : discoveredMods) {
                        String modLoader = mod.metadata.getOriginalLoaderType();
                        if (link.getSupportedLoaderType().equalsIgnoreCase(modLoader)) {
                            String modVersion = ChainLauncher.getModTargetMinecraftVersion(mod.metadata, gameVersion);
                            boolean matches = ChainLauncher.versionMatches(link.getSupportedVersionRange(), modVersion);
                            System.out.println("[HeadlessSimulator] Debug: checking link " + link.getClass().getSimpleName() + " against mod " + mod.metadata.getId() + " (loader: " + modLoader + ", version req: " + modVersion + ") -> matches: " + matches);
                            if (matches) {
                                shouldWakeUp = true;
                                break;
                            }
                        }
                    }
                }
                
                if (shouldWakeUp) {
                    System.out.println("[HeadlessSimulator] Waking up compat module: " + link.getClass().getSimpleName() + " (ClassLoader: " + link.getClass().getClassLoader() + ")");
                    link.onWakeUp(classLoader);
                    ChainLauncher.getActiveLinks().add(link);
                }
            }
            System.out.println("[HeadlessSimulator] Active Chainlink modules: " + ChainLauncher.getActiveLinks().size());

            // 5. Bootstrap registries
            System.out.println("Setting game version...");
            Class<?> sharedConstants = classLoader.loadClass("ab");
            Class<?> detectedVersion = classLoader.loadClass("t");
            java.lang.reflect.Field builtInField = detectedVersion.getDeclaredField("a");
            builtInField.setAccessible(true);
            Object builtInVersion = builtInField.get(null);
            java.lang.reflect.Field currentVersionField = sharedConstants.getDeclaredField("bn");
            currentVersionField.setAccessible(true);
            currentVersionField.set(null, builtInVersion);

            System.out.println("Bootstrapping Minecraft registries...");
            Class<?> bootstrapClass = classLoader.loadClass("net.minecraft.server.Bootstrap");
            java.lang.reflect.Method bootStrapMethod = bootstrapClass.getMethod("a");
            bootStrapMethod.invoke(null);

            // 6. Unfreeze registries
            System.out.println("Unfreezing registries...");
            Class<?> registriesClass = classLoader.loadClass("lt");
            Class<?> mappedRegistryClass = classLoader.loadClass("ju");
            java.lang.reflect.Field frozenField = mappedRegistryClass.getDeclaredField("l");
            frozenField.setAccessible(true);
            java.lang.reflect.Field intrusiveHoldersField = mappedRegistryClass.getDeclaredField("m");
            intrusiveHoldersField.setAccessible(true);

            for (java.lang.reflect.Field field : registriesClass.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                    try {
                        field.setAccessible(true);
                        Object registryObj = field.get(null);
                        if (registryObj != null && mappedRegistryClass.isInstance(registryObj)) {
                            Object registryKey = mappedRegistryClass.getMethod("d").invoke(registryObj);
                            Object resourceLocation = registryKey.getClass().getMethod("a").invoke(registryKey);
                            String registryName = resourceLocation.toString();
                            
                            frozenField.set(registryObj, false);
                            Object existingHolders = intrusiveHoldersField.get(registryObj);
                            if (existingHolders == null) {
                                boolean requiresIntrusive = registryName.equals("minecraft:block") || 
                                                            registryName.equals("minecraft:item") || 
                                                            registryName.equals("minecraft:fluid") || 
                                                            registryName.equals("minecraft:entity_type") || 
                                                            registryName.equals("minecraft:block_entity_type") || 
                                                            registryName.equals("minecraft:game_event");
                                if (requiresIntrusive) {
                                    intrusiveHoldersField.set(registryObj, new java.util.IdentityHashMap<>());
                                }
                            }
                        }
                    } catch (Exception e) {
                        // Ignore
                    }
                }
            }

            // 7. Simulating Minecraft client initialization to test EntityRenderersEvent firing and registration capture

            // Simulate Minecraft client initialization to test EntityRenderersEvent firing and registration capture
            System.out.println("\n--- SIMULATING MINECRAFT CLIENT INITIALIZATION (TESTING ENTITY RENDERERS EVENTS) ---");
            Class<?> minecraftClass = classLoader.loadClass("fgo");
            java.lang.reflect.Field theUnsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            theUnsafeField.setAccessible(true);
            sun.misc.Unsafe unsafe = (sun.misc.Unsafe) theUnsafeField.get(null);
            Object mockMinecraft = unsafe.allocateInstance(minecraftClass);
            
            Class<?> eventBridgeHelperClass = classLoader.loadClass("net.chainloader.loader.compat.bridge.EventBridgeHelper");
            java.lang.reflect.Method onMinecraftInitMethod = eventBridgeHelperClass.getMethod("onMinecraftInit", Object.class);
            onMinecraftInitMethod.invoke(null, mockMinecraft);
            System.out.println("--- MINECRAFT CLIENT INITIALIZATION SIMULATION END ---");

            // 8. Run simulated check tasks
            System.out.println("\n--- RUNNING HEADLESS SIMULATED INTERACTIONS ---");
            runSimulatedActions(classLoader);
            System.out.println("--- SIMULATED INTERACTIONS SUCCESS ---");

            System.out.println("==================================================");
            System.out.println("  Simulator Completed Successfully!");
            System.out.println("==================================================");

        } catch (Throwable t) {
            System.err.println("\n[ERROR] Headless Simulation Failed!");
            writeCrashReport(t);
            System.exit(1);
        }
    }

    private static void runSimulatedActions(ClassLoader classLoader) throws Exception {
        Class<?> registriesClass = classLoader.loadClass("lt");
        Class<?> resourceLocationClass = classLoader.loadClass("akr");

        // Simulating block lookup: waystones:blue_sharestone
        // BuiltInRegistries.BLOCK is obfuscated to field "e" in class "lt"
        java.lang.reflect.Field blockRegistryField = registriesClass.getDeclaredField("e");
        blockRegistryField.setAccessible(true);
        Object blockRegistry = blockRegistryField.get(null);

        // ResourceLocation.tryParse is obfuscated to "c"
        Object key = resourceLocationClass.getMethod("c", String.class).invoke(null, "waystones:blue_sharestone");

        // Registry.get(ResourceLocation) — find by parameter type
        java.lang.reflect.Method getMethod = null;
        for (java.lang.reflect.Method m : blockRegistry.getClass().getMethods()) {
            if (m.getParameterCount() == 1 && m.getParameterTypes()[0].isAssignableFrom(resourceLocationClass)) {
                if (m.getReturnType() != void.class && !m.getReturnType().equals(boolean.class)) {
                    getMethod = m;
                    break;
                }
            }
        }
        if (getMethod == null) {
            throw new RuntimeException("Could not find registry get(ResourceLocation) method on " + blockRegistry.getClass().getName());
        }
        Object block = getMethod.invoke(blockRegistry, key);
        
        if (block == null) {
            throw new RuntimeException("Simulation failed: waystones:blue_sharestone block is not registered!");
        }
        System.out.println("[SIMULATOR] Successfully verified block registry lookup for: " + key + " -> " + block.getClass().getName());

        // Simulating item lookup: waystones:warp_dust
        // BuiltInRegistries.ITEM is obfuscated to field "g" in class "lt"
        java.lang.reflect.Field itemRegistryField = registriesClass.getDeclaredField("g");
        itemRegistryField.setAccessible(true);
        Object itemRegistry = itemRegistryField.get(null);
        Object itemKey = resourceLocationClass.getMethod("c", String.class).invoke(null, "waystones:warp_dust");

        java.lang.reflect.Method getItemMethod = null;
        for (java.lang.reflect.Method m : itemRegistry.getClass().getMethods()) {
            if (m.getParameterCount() == 1 && m.getParameterTypes()[0].isAssignableFrom(resourceLocationClass)) {
                if (m.getReturnType() != void.class && !m.getReturnType().equals(boolean.class)) {
                    getItemMethod = m;
                    break;
                }
            }
        }
        if (getItemMethod == null) {
            throw new RuntimeException("Could not find registry get(ResourceLocation) method on " + itemRegistry.getClass().getName());
        }
        Object item = getItemMethod.invoke(itemRegistry, itemKey);

        if (item == null) {
            throw new RuntimeException("Simulation failed: waystones:warp_dust item is not registered!");
        }
        System.out.println("[SIMULATOR] Successfully verified item registry lookup for: " + itemKey + " -> " + item.getClass().getName());

        // Simulating DyeColor color extraction for DyeColor.BLUE
        // DyeColor is an enum (cti) — BLUE is at ordinal 11. Fields are obfuscated, so use values()[11].
        Class<?> dyeColorClass = classLoader.loadClass("net.minecraft.world.item.DyeColor");
        Object[] dyeColors = (Object[]) dyeColorClass.getMethod("values").invoke(null);
        Object blueDyeColor = dyeColors[11]; // BLUE = ordinal 11
        
        Class<?> bridgeHelperClass = classLoader.loadClass("net.chainloader.loader.compat.bridge.EventBridgeHelper");
        java.lang.reflect.Method getRGBMethod = bridgeHelperClass.getMethod("getColorComponents", Object.class);
        float[] rgb = (float[]) getRGBMethod.invoke(null, blueDyeColor);
        System.out.printf("[SIMULATOR] Successfully simulated DyeColor components conversion: R=%.2f, G=%.2f, B=%.2f%n", rgb[0], rgb[1], rgb[2]);

        // Verify Model (fwg) class transformations
        Class<?> modelClass = classLoader.loadClass("fwg");
        Class<?> poseStackClass = classLoader.loadClass("fbi");
        Class<?> vertexConsumerClass = classLoader.loadClass("fbm");
        
        java.lang.reflect.Method legacyModelRenderMethod = null;
        try {
            legacyModelRenderMethod = modelClass.getMethod("a", poseStackClass, vertexConsumerClass, int.class, int.class, float.class, float.class, float.class, float.class);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException("Simulation failed: fwg (Model) does not have legacy float-color render method 'a'!");
        }
        System.out.println("[SIMULATOR] Successfully verified Model (fwg) legacy render method: " + legacyModelRenderMethod);

        // Verify ModelPart (fyk) class transformations
        Class<?> modelPartClass = classLoader.loadClass("fyk");
        java.lang.reflect.Method legacyModelPartRenderMethod = null;
        try {
            legacyModelPartRenderMethod = modelPartClass.getMethod("a", poseStackClass, vertexConsumerClass, int.class, int.class, float.class, float.class, float.class, float.class);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException("Simulation failed: fyk (ModelPart) does not have legacy float-color render method 'a'!");
        }
        System.out.println("[SIMULATOR] Successfully verified ModelPart (fyk) legacy render method: " + legacyModelPartRenderMethod);

        // Verify MinecraftServer (net.minecraft.server.MinecraftServer) and ServerLevel (aqu) class transformations load cleanly
        System.out.println("[SIMULATOR] Verifying ASM transformations on MinecraftServer...");
        Class<?> serverClass = classLoader.loadClass("net.minecraft.server.MinecraftServer");
        System.out.println("[SIMULATOR] Loaded transformed MinecraftServer successfully.");

        System.out.println("[SIMULATOR] Verifying ASM transformations on ServerLevel (aqu)...");
        Class<?> serverLevelClass = classLoader.loadClass("aqu");
        System.out.println("[SIMULATOR] Loaded transformed ServerLevel (aqu) successfully.");

        // Simulate recipe and worldgen registry loading
        simulateWorldAndRecipeLoad(classLoader);

        // Simulate and verify lifecycle events
        System.out.println("[SIMULATOR] Starting simulated server and world lifecycle event tests...");
        Class<?> mockServerClass = classLoader.loadClass("net.chainloader.loader.core.MockMinecraftServer");
        Class<?> mockLevelClass = classLoader.loadClass("net.chainloader.loader.core.MockServerLevel");

        java.lang.reflect.Field theUnsafe = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        theUnsafe.setAccessible(true);
        sun.misc.Unsafe unsafe = (sun.misc.Unsafe) theUnsafe.get(null);
        Object mockServer = unsafe.allocateInstance(mockServerClass);
        Object mockLevel = unsafe.allocateInstance(mockLevelClass);

        mockLevelClass.getMethod("setServer", classLoader.loadClass("net.minecraft.server.MinecraftServer")).invoke(mockLevel, mockServer);

        Class<?> eventBusClass = classLoader.loadClass("net.neoforged.bus.api.IEventBus");
        Class<?> neoForgeClass = classLoader.loadClass("net.neoforged.neoforge.common.NeoForge");
        Object neoForgeEventBus = neoForgeClass.getField("EVENT_BUS").get(null);

        final java.util.concurrent.atomic.AtomicInteger neoforgeStartingCount = new java.util.concurrent.atomic.AtomicInteger(0);
        final java.util.concurrent.atomic.AtomicInteger neoforgeStartedCount = new java.util.concurrent.atomic.AtomicInteger(0);
        final java.util.concurrent.atomic.AtomicInteger neoforgeStoppingCount = new java.util.concurrent.atomic.AtomicInteger(0);
        final java.util.concurrent.atomic.AtomicInteger neoforgeStoppedCount = new java.util.concurrent.atomic.AtomicInteger(0);
        final java.util.concurrent.atomic.AtomicInteger neoforgeLoadCount = new java.util.concurrent.atomic.AtomicInteger(0);
        final java.util.concurrent.atomic.AtomicInteger neoforgeUnloadCount = new java.util.concurrent.atomic.AtomicInteger(0);
        final java.util.concurrent.atomic.AtomicInteger neoforgeSaveCount = new java.util.concurrent.atomic.AtomicInteger(0);

        java.util.function.Consumer<Object> neoforgeListener = event -> {
            String name = event.getClass().getName();
            if (name.contains("ServerStartingEvent")) {
                neoforgeStartingCount.incrementAndGet();
            } else if (name.contains("ServerStartedEvent")) {
                neoforgeStartedCount.incrementAndGet();
            } else if (name.contains("ServerStoppingEvent")) {
                neoforgeStoppingCount.incrementAndGet();
            } else if (name.contains("ServerStoppedEvent")) {
                neoforgeStoppedCount.incrementAndGet();
            } else if (name.contains("LevelEvent$Load")) {
                neoforgeLoadCount.incrementAndGet();
            } else if (name.contains("LevelEvent$Unload")) {
                neoforgeUnloadCount.incrementAndGet();
            } else if (name.contains("LevelEvent$Save")) {
                neoforgeSaveCount.incrementAndGet();
            }
        };
        eventBusClass.getMethod("addListener", java.util.function.Consumer.class).invoke(neoForgeEventBus, neoforgeListener);

        Class<?> serverLifecycleEventsClass = classLoader.loadClass("net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents");
        Class<?> serverStartingIface = classLoader.loadClass("net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents$ServerStarting");
        Class<?> serverStartedIface = classLoader.loadClass("net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents$ServerStarted");
        Class<?> serverStoppingIface = classLoader.loadClass("net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents$ServerStopping");
        Class<?> serverStoppedIface = classLoader.loadClass("net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents$ServerStopped");

        Class<?> serverLevelEventsClass = classLoader.loadClass("net.fabricmc.fabric.api.event.lifecycle.v1.ServerLevelEvents");
        Class<?> levelLoadIface = classLoader.loadClass("net.fabricmc.fabric.api.event.lifecycle.v1.ServerLevelEvents$Load");
        Class<?> levelUnloadIface = classLoader.loadClass("net.fabricmc.fabric.api.event.lifecycle.v1.ServerLevelEvents$Unload");
        Class<?> levelSaveIface = classLoader.loadClass("net.fabricmc.fabric.api.event.lifecycle.v1.ServerLevelEvents$Save");

        final java.util.concurrent.atomic.AtomicInteger fabricStartingCount = new java.util.concurrent.atomic.AtomicInteger(0);
        final java.util.concurrent.atomic.AtomicInteger fabricStartedCount = new java.util.concurrent.atomic.AtomicInteger(0);
        final java.util.concurrent.atomic.AtomicInteger fabricStoppingCount = new java.util.concurrent.atomic.AtomicInteger(0);
        final java.util.concurrent.atomic.AtomicInteger fabricStoppedCount = new java.util.concurrent.atomic.AtomicInteger(0);
        final java.util.concurrent.atomic.AtomicInteger fabricLoadCount = new java.util.concurrent.atomic.AtomicInteger(0);
        final java.util.concurrent.atomic.AtomicInteger fabricUnloadCount = new java.util.concurrent.atomic.AtomicInteger(0);
        final java.util.concurrent.atomic.AtomicInteger fabricSaveCount = new java.util.concurrent.atomic.AtomicInteger(0);

        Object fabricStartingProxy = java.lang.reflect.Proxy.newProxyInstance(
            classLoader,
            new Class<?>[]{ serverStartingIface },
            (proxy, method, args) -> {
                fabricStartingCount.incrementAndGet();
                return null;
            }
        );
        Object serverStartingEvent = serverLifecycleEventsClass.getField("SERVER_STARTING").get(null);
        serverStartingEvent.getClass().getMethod("register", Object.class).invoke(serverStartingEvent, fabricStartingProxy);

        Object fabricStartedProxy = java.lang.reflect.Proxy.newProxyInstance(
            classLoader,
            new Class<?>[]{ serverStartedIface },
            (proxy, method, args) -> {
                fabricStartedCount.incrementAndGet();
                return null;
            }
        );
        Object serverStartedEvent = serverLifecycleEventsClass.getField("SERVER_STARTED").get(null);
        serverStartedEvent.getClass().getMethod("register", Object.class).invoke(serverStartedEvent, fabricStartedProxy);

        Object fabricStoppingProxy = java.lang.reflect.Proxy.newProxyInstance(
            classLoader,
            new Class<?>[]{ serverStoppingIface },
            (proxy, method, args) -> {
                fabricStoppingCount.incrementAndGet();
                return null;
            }
        );
        Object serverStoppingEvent = serverLifecycleEventsClass.getField("SERVER_STOPPING").get(null);
        serverStoppingEvent.getClass().getMethod("register", Object.class).invoke(serverStoppingEvent, fabricStoppingProxy);

        Object fabricStoppedProxy = java.lang.reflect.Proxy.newProxyInstance(
            classLoader,
            new Class<?>[]{ serverStoppedIface },
            (proxy, method, args) -> {
                fabricStoppedCount.incrementAndGet();
                return null;
            }
        );
        Object serverStoppedEvent = serverLifecycleEventsClass.getField("SERVER_STOPPED").get(null);
        serverStoppedEvent.getClass().getMethod("register", Object.class).invoke(serverStoppedEvent, fabricStoppedProxy);

        Object fabricLoadProxy = java.lang.reflect.Proxy.newProxyInstance(
            classLoader,
            new Class<?>[]{ levelLoadIface },
            (proxy, method, args) -> {
                fabricLoadCount.incrementAndGet();
                return null;
            }
        );
        Object levelLoadEvent = serverLevelEventsClass.getField("LOAD").get(null);
        levelLoadEvent.getClass().getMethod("register", Object.class).invoke(levelLoadEvent, fabricLoadProxy);

        Object fabricUnloadProxy = java.lang.reflect.Proxy.newProxyInstance(
            classLoader,
            new Class<?>[]{ levelUnloadIface },
            (proxy, method, args) -> {
                fabricUnloadCount.incrementAndGet();
                return null;
            }
        );
        Object levelUnloadEvent = serverLevelEventsClass.getField("UNLOAD").get(null);
        levelUnloadEvent.getClass().getMethod("register", Object.class).invoke(levelUnloadEvent, fabricUnloadProxy);

        Object fabricSaveProxy = java.lang.reflect.Proxy.newProxyInstance(
            classLoader,
            new Class<?>[]{ levelSaveIface },
            (proxy, method, args) -> {
                fabricSaveCount.incrementAndGet();
                return null;
            }
        );
        Object levelSaveEvent = serverLevelEventsClass.getField("SAVE").get(null);
        levelSaveEvent.getClass().getMethod("register", Object.class).invoke(levelSaveEvent, fabricSaveProxy);

        // Call the EventBridgeHelper methods to simulate the events being intercepted and fired
        bridgeHelperClass.getMethod("onServerStarting", Object.class).invoke(null, mockServer);
        bridgeHelperClass.getMethod("onServerStarted", Object.class).invoke(null, mockServer);
        bridgeHelperClass.getMethod("onServerStopping", Object.class).invoke(null, mockServer);
        bridgeHelperClass.getMethod("onServerStopped", Object.class).invoke(null, mockServer);

        bridgeHelperClass.getMethod("onLevelLoad", Object.class).invoke(null, mockLevel);
        bridgeHelperClass.getMethod("onLevelUnload", Object.class).invoke(null, mockLevel);
        bridgeHelperClass.getMethod("onLevelSave", Object.class).invoke(null, mockLevel);

        System.out.println("[SIMULATOR] Verifying NeoForge event counters...");
        if (neoforgeStartingCount.get() != 1) throw new RuntimeException("NeoForge ServerStartingEvent was not triggered correctly!");
        if (neoforgeStartedCount.get() != 1) throw new RuntimeException("NeoForge ServerStartedEvent was not triggered correctly!");
        if (neoforgeStoppingCount.get() != 1) throw new RuntimeException("NeoForge ServerStoppingEvent was not triggered correctly!");
        if (neoforgeStoppedCount.get() != 1) throw new RuntimeException("NeoForge ServerStoppedEvent was not triggered correctly!");
        if (neoforgeLoadCount.get() != 1) throw new RuntimeException("NeoForge LevelEvent.Load was not triggered correctly!");
        if (neoforgeUnloadCount.get() != 1) throw new RuntimeException("NeoForge LevelEvent.Unload was not triggered correctly!");
        if (neoforgeSaveCount.get() != 1) throw new RuntimeException("NeoForge LevelEvent.Save was not triggered correctly!");

        System.out.println("[SIMULATOR] Verifying Fabric event counters...");
        if (fabricStartingCount.get() != 1) throw new RuntimeException("Fabric SERVER_STARTING event was not triggered correctly!");
        if (fabricStartedCount.get() != 1) throw new RuntimeException("Fabric SERVER_STARTED event was not triggered correctly!");
        if (fabricStoppingCount.get() != 1) throw new RuntimeException("Fabric SERVER_STOPPING event was not triggered correctly!");
        if (fabricStoppedCount.get() != 1) throw new RuntimeException("Fabric SERVER_STOPPED event was not triggered correctly!");
        if (fabricLoadCount.get() != 1) throw new RuntimeException("Fabric ServerLevelEvents.LOAD was not triggered correctly!");
        if (fabricUnloadCount.get() != 1) throw new RuntimeException("Fabric ServerLevelEvents.UNLOAD was not triggered correctly!");
        if (fabricSaveCount.get() != 1) throw new RuntimeException("Fabric ServerLevelEvents.SAVE was not triggered correctly!");

        System.out.println("[SIMULATOR] All simulated Server/World Lifecycle events verified successfully!");

        // Verify Fabric Model Loading API stubs
        System.out.println("\nTesting Fabric Model Loading API stubs...");
        Class<?> modelLoadingPluginClass = classLoader.loadClass("net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin");
        Class<?> contextClass = classLoader.loadClass("net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin$Context");
        Class<?> modelModifierOnLoadClass = classLoader.loadClass("net.fabricmc.fabric.api.client.model.loading.v1.ModelModifier$OnLoad");
        Class<?> modelModifierOnLoadContextClass = classLoader.loadClass("net.fabricmc.fabric.api.client.model.loading.v1.ModelModifier$OnLoad$Context");
        Class<?> unbakedModelClass = classLoader.loadClass("net.minecraft.client.resources.model.UnbakedModel");
        Class<?> resourceLocationClassReal = classLoader.loadClass("akr");
        Class<?> eventClass = classLoader.loadClass("net.fabricmc.fabric.api.event.Event");

        // Simulate registering a plugin via the static ModelLoadingPlugin.register(ModelLoadingPlugin) method
        Object pluginProxy = java.lang.reflect.Proxy.newProxyInstance(
            classLoader,
            new Class<?>[] { modelLoadingPluginClass },
            new java.lang.reflect.InvocationHandler() {
                @Override
                public Object invoke(Object proxy, java.lang.reflect.Method method, Object[] args) throws Throwable {
                    if (method.getName().equals("onInitializeModelLoader")) {
                        System.out.println("[SIMULATOR] ModelLoadingPlugin.onInitializeModelLoader callback invoked!");
                        Object context = args[0];
                        
                        // 1. Invoke addModels(ResourceLocation... ids)
                        java.lang.reflect.Method tryParseMethod = resourceLocationClassReal.getMethod("c", String.class);
                        Object testLoc = tryParseMethod.invoke(null, "minecraft:block/stone");
                        
                        Object resourceLocationArray = java.lang.reflect.Array.newInstance(resourceLocationClassReal, 1);
                        java.lang.reflect.Array.set(resourceLocationArray, 0, testLoc);
                        
                        java.lang.reflect.Method addModelsMethod = contextClass.getMethod("addModels", resourceLocationArray.getClass());
                        addModelsMethod.invoke(context, (Object) resourceLocationArray);
                        System.out.println("[SIMULATOR] Successfully called context.addModels with: " + testLoc);
                        
                        // 2. Invoke modifyModelOnLoad() to get the Event
                        java.lang.reflect.Method modifyModelOnLoadMethod = contextClass.getMethod("modifyModelOnLoad");
                        Object eventObj = modifyModelOnLoadMethod.invoke(context);
                        System.out.println("[SIMULATOR] Successfully obtained modifyModelOnLoad Event: " + eventObj);
                        
                        // 3. Register a modifier
                        Object onLoadProxy = java.lang.reflect.Proxy.newProxyInstance(
                            classLoader,
                            new Class<?>[] { modelModifierOnLoadClass },
                            new java.lang.reflect.InvocationHandler() {
                                @Override
                                public Object invoke(Object proxy, java.lang.reflect.Method method, Object[] args) throws Throwable {
                                    if (method.getName().equals("modifyModelOnLoad")) {
                                        Object model = args[0];
                                        Object ctx = args[1];
                                        java.lang.reflect.Method idMethod = modelModifierOnLoadContextClass.getMethod("id");
                                        Object id = idMethod.invoke(ctx);
                                        System.out.println("[SIMULATOR] modifyModelOnLoad called for model ID: " + id);
                                        return model; // Return original model
                                    }
                                    return null;
                                }
                            }
                        );
                        
                        java.lang.reflect.Method registerMethod = eventClass.getMethod("register", Object.class);
                        registerMethod.invoke(eventObj, onLoadProxy);
                        System.out.println("[SIMULATOR] Successfully registered ModelModifier.OnLoad listener.");
                    } else if (method.getName().equals("toString")) {
                        return "SimulatedModelLoadingPlugin";
                    }
                    return null;
                }
            }
        );

        // Register the plugin
        java.lang.reflect.Method registerMethod = modelLoadingPluginClass.getMethod("register", modelLoadingPluginClass);
        registerMethod.invoke(null, pluginProxy);
        System.out.println("[SIMULATOR] Successfully invoked ModelLoadingPlugin.register");

        // Construct a mock Context to invoke the plugin's callback and verify the Event invoker behavior
        final Object[] registeredListenerHolder = new Object[1];
        Object contextProxy = java.lang.reflect.Proxy.newProxyInstance(
            classLoader,
            new Class<?>[] { contextClass },
            new java.lang.reflect.InvocationHandler() {
                @Override
                public Object invoke(Object proxy, java.lang.reflect.Method method, Object[] args) throws Throwable {
                    if (method.getName().equals("addModels")) {
                        Object[] ids = (Object[]) args[0];
                        System.out.println("[SIMULATOR] MockContext.addModels invoked with: " + java.util.Arrays.toString(ids));
                        return null;
                    } else if (method.getName().equals("modifyModelOnLoad")) {
                        System.out.println("[SIMULATOR] MockContext.modifyModelOnLoad invoked.");
                        java.lang.reflect.Constructor<?> eventCtor = eventClass.getConstructor(Class.class);
                        Object event = eventCtor.newInstance(modelModifierOnLoadClass);
                        registeredListenerHolder[0] = event; // Keep reference to trigger it later
                        return event;
                    }
                    return null;
                }
            }
        );

        // Manually invoke onInitializeModelLoader with our context proxy to trigger registration
        java.lang.reflect.Method onInitializeMethod = modelLoadingPluginClass.getMethod("onInitializeModelLoader", contextClass);
        onInitializeMethod.invoke(pluginProxy, contextProxy);
        System.out.println("[SIMULATOR] Successfully invoked onInitializeModelLoader.");

        // Trigger the registered ModelModifier.OnLoad invoker to verify the whole pipeline end-to-end
        Object event = registeredListenerHolder[0];
        if (event != null) {
            java.lang.reflect.Method invokerMethod = eventClass.getMethod("invoker");
            Object invoker = invokerMethod.invoke(event);
            
            // Create dummy UnbakedModel instance using dynamic proxy
            Object dummyModel = java.lang.reflect.Proxy.newProxyInstance(
                classLoader,
                new Class<?>[] { unbakedModelClass },
                (proxy, method, args) -> null
            );
            
            // Create dummy ModelModifier.OnLoad.Context
            Object dummyContext = java.lang.reflect.Proxy.newProxyInstance(
                classLoader,
                new Class<?>[] { modelModifierOnLoadContextClass },
                new java.lang.reflect.InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, java.lang.reflect.Method method, Object[] args) throws Throwable {
                        if (method.getName().equals("id")) {
                            java.lang.reflect.Method tryParseMethod = resourceLocationClassReal.getMethod("c", String.class);
                            return tryParseMethod.invoke(null, "waystones:waystone");
                        }
                        return null;
                    }
                }
            );
            
            // Invoke the invoker: modifyModelOnLoad(UnbakedModel model, Context context)
            java.lang.reflect.Method modifyModelMethod = modelModifierOnLoadClass.getMethod("modifyModelOnLoad", unbakedModelClass, modelModifierOnLoadContextClass);
            modifyModelMethod.invoke(invoker, dummyModel, dummyContext);
            System.out.println("[SIMULATOR] End-to-end event invoker test succeeded!");
        }

        System.out.println("[SIMULATOR] Successfully verified ModelLoadingPlugin execution flow.");

        // Simulate ModelEvent firing and verification
        System.out.println("\n--- SIMULATING MODEL EVENTS (TESTING MODEL REGISTRATION & BAKING EVENTS) ---");
        java.util.Set<Object> buses = (java.util.Set<Object>) classLoader.loadClass("net.chainloader.loader.core.ModScanner")
            .getMethod("getModEventBuses").invoke(null);
        
        boolean neoForgeBusFound = false;
        boolean foundModWithListener = false;
        for (Object bus : buses) {
            if (bus.getClass().getName().contains("neoforged")) {
                neoForgeBusFound = true;
                System.out.println("[SIMULATOR] Found NeoForge mod event bus: " + bus.getClass().getName() + ", posting ModelEvents...");
                
                // Check if this bus has any listener registered for ModelEvent.RegisterAdditional
                java.lang.reflect.Field listenersField = bus.getClass().getDeclaredField("listeners");
                listenersField.setAccessible(true);
                java.util.Map<?, ?> listenersMap = (java.util.Map<?, ?>) listenersField.get(bus);
                boolean hasRegisterAdditionalListener = false;
                for (Object keyClass : listenersMap.keySet()) {
                    if (keyClass instanceof Class && ((Class<?>) keyClass).getName().contains("ModelEvent$RegisterAdditional")) {
                        hasRegisterAdditionalListener = true;
                        break;
                    }
                }

                // 1. Post ModelEvent.RegisterAdditional
                Class<?> registerAdditionalClass = classLoader.loadClass("net.neoforged.neoforge.client.event.ModelEvent$RegisterAdditional");
                Object registerAdditionalEvent = registerAdditionalClass.getDeclaredConstructor().newInstance();
                bus.getClass().getMethod("post", Object.class).invoke(bus, registerAdditionalEvent);
                
                // Verify the model was captured
                java.util.Set<?> registeredModels = (java.util.Set<?>) registerAdditionalClass.getMethod("getModels").invoke(registerAdditionalEvent);
                System.out.println("[SIMULATOR] Captured registered additional models: " + registeredModels);
                if (hasRegisterAdditionalListener) {
                    foundModWithListener = true;
                    boolean foundTargetModel = false;
                    for (Object m : registeredModels) {
                        if (m.toString().equals("example:test_model")) {
                            foundTargetModel = true;
                            break;
                        }
                    }
                    if (!foundTargetModel) {
                        throw new RuntimeException("ModelEvent.RegisterAdditional failed to capture model registration!");
                    }
                }
                
                // 2. Post ModelEvent.BakingCompleted
                Class<?> bakingCompletedClass = classLoader.loadClass("net.neoforged.neoforge.client.event.ModelEvent$BakingCompleted");
                Object bakingCompletedEvent = bakingCompletedClass.getDeclaredConstructor(java.util.Map.class)
                    .newInstance(new java.util.HashMap<>());
                bus.getClass().getMethod("post", Object.class).invoke(bus, bakingCompletedEvent);
                
                // 3. Post ModelEvent.ModifyBakingResult
                Class<?> modifyBakingResultClass = classLoader.loadClass("net.neoforged.neoforge.client.event.ModelEvent$ModifyBakingResult");
                Object modifyBakingResultEvent = modifyBakingResultClass.getDeclaredConstructor(java.util.Map.class)
                    .newInstance(new java.util.HashMap<>());
                bus.getClass().getMethod("post", Object.class).invoke(bus, modifyBakingResultEvent);
            }
        }
        if (!neoForgeBusFound) {
            throw new RuntimeException("Simulation failed: Example NeoForge mod event bus was not registered/found!");
        }
        if (!foundModWithListener) {
            throw new RuntimeException("Simulation failed: Expected to find at least one NeoForge mod event bus with a ModelEvent.RegisterAdditional listener!");
        }
        System.out.println("--- MODEL EVENTS SIMULATION END ---");
        System.out.println("[SIMULATOR] Loading SharestoneRenderer to trigger bytecode transformation...");
        classLoader.loadClass("net.blay09.mods.waystones.client.render.SharestoneRenderer");
        System.out.println("[SIMULATOR] Loaded SharestoneRenderer successfully.");

        System.out.println("[SIMULATOR] Loading NaturesCompassItem to trigger bytecode transformation...");
        classLoader.loadClass("com.chaosthedude.naturescompass.items.NaturesCompassItem");
        System.out.println("[SIMULATOR] Loaded NaturesCompassItem successfully.");

        // Simulate opening the player inventory (instantiating InventoryScreen)
        System.out.println("\n[SIMULATOR] Simulating opening player inventory...");
        try {
            Class<?> playerClass = classLoader.loadClass("cmx");
            Class<?> localPlayerClass = classLoader.loadClass("geb");
            Class<?> inventoryClass = classLoader.loadClass("cmw");
            Class<?> inventoryMenuClass = classLoader.loadClass("cqw");
            Class<?> inventoryScreenClass = classLoader.loadClass("fpt");

            java.lang.reflect.Field theUnsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            theUnsafeField.setAccessible(true);
            sun.misc.Unsafe localUnsafe = (sun.misc.Unsafe) theUnsafeField.get(null);

            Object mockPlayer = localUnsafe.allocateInstance(localPlayerClass);

            // Instantiate Inventory using its constructor
            java.lang.reflect.Constructor<?> invCtor = null;
            for (java.lang.reflect.Constructor<?> c : inventoryClass.getDeclaredConstructors()) {
                if (c.getParameterCount() == 1 && c.getParameterTypes()[0].equals(playerClass)) {
                    invCtor = c;
                    break;
                }
            }
            Object mockInventory;
            if (invCtor != null) {
                invCtor.setAccessible(true);
                mockInventory = invCtor.newInstance(mockPlayer);
            } else {
                mockInventory = localUnsafe.allocateInstance(inventoryClass);
            }

            // Instantiate InventoryMenu using its constructor
            java.lang.reflect.Constructor<?> menuCtor = null;
            for (java.lang.reflect.Constructor<?> c : inventoryMenuClass.getDeclaredConstructors()) {
                Class<?>[] params = c.getParameterTypes();
                if (params.length == 3 && params[0].equals(inventoryClass) && params[1].equals(boolean.class) && params[2].equals(playerClass)) {
                    menuCtor = c;
                    break;
                }
            }
            Object mockInventoryMenu;
            if (menuCtor != null) {
                menuCtor.setAccessible(true);
                mockInventoryMenu = menuCtor.newInstance(mockInventory, true, mockPlayer);
            } else {
                mockInventoryMenu = localUnsafe.allocateInstance(inventoryMenuClass);
            }

            // Find and set inventory field on player
            for (java.lang.reflect.Field f : playerClass.getDeclaredFields()) {
                if (f.getType().equals(inventoryClass)) {
                    f.setAccessible(true);
                    f.set(mockPlayer, mockInventory);
                }
            }

            // Find and set inventoryMenu/containerMenu field on player
            for (java.lang.reflect.Field f : playerClass.getDeclaredFields()) {
                if (f.getType().equals(inventoryMenuClass) || f.getType().getName().contains("cqw")) {
                    f.setAccessible(true);
                    f.set(mockPlayer, mockInventoryMenu);
                }
            }

            // Instantiate InventoryScreen
            java.lang.reflect.Constructor<?> ctor = inventoryScreenClass.getConstructor(playerClass);
            ctor.setAccessible(true);
            Object inventoryScreen = ctor.newInstance(mockPlayer);
            System.out.println("[SIMULATOR] Successfully instantiated InventoryScreen: " + inventoryScreen.getClass().getName());
        } catch (Throwable t) {
            System.err.println("[SIMULATOR] Failed to simulate opening inventory:");
            t.printStackTrace();
            throw new RuntimeException(t);
        }
    }

    private static void writeCrashReport(Throwable t) {
        File crashDir = new File("crash-reports");
        if (!crashDir.exists()) {
            crashDir.mkdirs();
        }
        
        File crashFile = new File(crashDir, "crash-simulation-" + System.currentTimeMillis() + ".txt");
        try (PrintWriter writer = new PrintWriter(crashFile)) {
            writer.println("---- Minecraft Headless Simulator Crash Report ----");
            writer.println("Time: " + new Date());
            writer.println("Description: Headless Simulation Assertion or Linkage Exception\n");
            
            writer.println("Exception Stacktrace:");
            t.printStackTrace(writer);
            
            System.err.println("[SIMULATOR] Standard crash report saved to: " + crashFile.getAbsolutePath());
        } catch (Exception e) {
            System.err.println("Failed to write crash report file:");
            e.printStackTrace();
        }
        t.printStackTrace();
    }

    private static void simulateWorldAndRecipeLoad(ClassLoader classLoader) {
        System.out.println("\n--- SIMULATING WORLD/DATAPACK RECIPE LOADING ---");
        try {
            Class<?> registriesClass = classLoader.loadClass("lt");
            Class<?> mappedRegistryClass = classLoader.loadClass("ju");
            
            java.lang.reflect.Field recipeSerializerRegistryField = null;
            java.lang.reflect.Field placementModifierRegistryField = null;
            
            for (java.lang.reflect.Field f : registriesClass.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) {
                    try {
                        f.setAccessible(true);
                        Object reg = f.get(null);
                        if (reg != null && mappedRegistryClass.isInstance(reg)) {
                            Object registryKey = mappedRegistryClass.getMethod("d").invoke(reg);
                            Object resourceLocation = registryKey.getClass().getMethod("a").invoke(registryKey);
                            String regName = resourceLocation.toString();
                            if ("minecraft:recipe_serializer".equals(regName)) {
                                recipeSerializerRegistryField = f;
                            } else if ("minecraft:placement_modifier_type".equals(regName)) {
                                placementModifierRegistryField = f;
                            }
                        }
                    } catch (Exception e) {
                        // Ignore fields that cannot be read
                    }
                }
            }
            
            if (recipeSerializerRegistryField == null) {
                System.out.println("[SIMULATOR] Could not locate recipe serializer registry!");
                return;
            }
            
            Object registry = recipeSerializerRegistryField.get(null);
            Iterable<?> iterable = (Iterable<?>) registry;
            java.util.List<Object> serializers = new ArrayList<>();
            for (Object serializer : iterable) {
                serializers.add(serializer);
            }
            System.out.println("[SIMULATOR] Found " + serializers.size() + " registered recipe serializers.");
                     for (Object serializer : serializers) {
                System.out.println("[SIMULATOR] Testing recipe serializer: " + serializer.getClass().getName());
                
                // Get codec
                java.lang.reflect.Method codecMethod = null;
                for (java.lang.reflect.Method m : serializer.getClass().getMethods()) {
                    if ((m.getName().equals("codec") || m.getName().equals("a")) && m.getParameterCount() == 0 && m.getReturnType().getName().contains("MapCodec")) {
                        codecMethod = m;
                        break;
                    }
                }
                if (codecMethod == null) {
                    throw new RuntimeException("Could not find codec() or a() method returning MapCodec on " + serializer.getClass().getName());
                }
                codecMethod.setAccessible(true);
                Object codec = codecMethod.invoke(serializer);
                System.out.println("  - codec() returned: " + codec);
                
                // Get streamCodec
                java.lang.reflect.Method streamCodecMethod = null;
                for (java.lang.reflect.Method m : serializer.getClass().getMethods()) {
                    if ((m.getName().equals("streamCodec") || m.getName().equals("b")) && m.getParameterCount() == 0 && (m.getReturnType().getName().contains("StreamCodec") || m.getReturnType().getName().equals("yx"))) {
                        streamCodecMethod = m;
                        break;
                    }
                }
                if (streamCodecMethod == null) {
                    throw new RuntimeException("Could not find streamCodec() or b() method returning StreamCodec on " + serializer.getClass().getName());
                }
                streamCodecMethod.setAccessible(true);
                Object streamCodec = streamCodecMethod.invoke(serializer);
                System.out.println("  - streamCodec() returned: " + streamCodec);
                
                if (codec == null) {
                    throw new RuntimeException("codec() returned null for " + serializer.getClass().getName());
                }
                if (streamCodec == null) {
                    throw new RuntimeException("streamCodec() returned null for " + serializer.getClass().getName());
                }
            }
            System.out.println("[SIMULATOR] Successfully verified all recipe serializers and their codec/streamCodec getters!");
 
            if (placementModifierRegistryField != null) {
                Object placementRegistry = placementModifierRegistryField.get(null);
                Iterable<?> placementIterable = (Iterable<?>) placementRegistry;
                java.util.List<Object> placementTypes = new ArrayList<>();
                for (Object type : placementIterable) {
                    placementTypes.add(type);
                }
                System.out.println("[SIMULATOR] Found " + placementTypes.size() + " registered placement modifier types.");
                for (Object modifierType : placementTypes) {
                    System.out.println("[SIMULATOR] Testing placement modifier type: " + modifierType.getClass().getName());
                    java.lang.reflect.Method codecMethod = null;
                    for (java.lang.reflect.Method m : modifierType.getClass().getMethods()) {
                        if ((m.getName().equals("codec") || m.getName().equals("a")) && m.getParameterCount() == 0 && m.getReturnType().getName().contains("MapCodec")) {
                            codecMethod = m;
                            break;
                        }
                    }
                    if (codecMethod == null) {
                        throw new RuntimeException("Could not find codec() or a() method returning MapCodec on placement modifier " + modifierType.getClass().getName());
                    }
                    codecMethod.setAccessible(true);
                    Object codec = codecMethod.invoke(modifierType);
                    System.out.println("  - codec() returned: " + codec);
                    if (codec == null) {
                        throw new RuntimeException("codec() returned null for placement modifier " + modifierType.getClass().getName());
                    }
                }
                System.out.println("[SIMULATOR] Successfully verified all placement modifier types and their codec getters!");
            } else {
                System.out.println("[SIMULATOR] Could not locate placement modifier registry!");
            }
        } catch (Throwable t) {
            System.err.println("[SIMULATOR] Failed to simulate recipe loading:");
            t.printStackTrace();
            throw new RuntimeException(t);
        }
    }
}
