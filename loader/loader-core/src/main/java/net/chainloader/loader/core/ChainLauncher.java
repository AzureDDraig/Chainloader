package net.chainloader.loader.core;

import net.chainloader.loader.compat.Chainlink;
import net.chainloader.loader.core.gui.EarlyLoadingScreen;

import java.io.File;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.ServiceLoader;
import java.util.stream.Stream;

/**
 * The main bootstrapper for ChainLoader.
 * Responsible for initializing the custom ClassLoader, detecting and waking up
 * the appropriate Chainlink modules, loading configuration, and handing off
 * execution to the target game entrypoint.
 */
public class ChainLauncher {

    private static final String VERSION = "1.0.0-SNAPSHOT";
    private static final String DEFAULT_TARGET = "net.minecraft.client.main.Main";

    private static final List<Chainlink> activeLinks = new ArrayList<>();

    public static List<Chainlink> getActiveLinks() {
        return activeLinks;
    }

    public static void main(String[] args) {
        Logging.initialize();

        System.out.println("==================================================");
        System.out.println("  ChainLoader Modloader Core - Version " + VERSION);
        System.out.println("  Bootstrapping game initialization sequence...");
        System.out.println("==================================================");

        // 1. Initialize Early Loading GUI
        EarlyLoadingScreen loadingScreen = EarlyLoadingScreen.getInstance();
        loadingScreen.show();
        loadingScreen.updateProgress(10, "Booting ChainLoader Engine...");

        try {
            // 2. Parsing and extracting launcher arguments
            loadingScreen.updateProgress(20, "Parsing command line arguments...");
            LauncherArgs launcherArgs = parseArgs(args);
            System.out.println("Game Directory: " + launcherArgs.gameDir.toAbsolutePath());
            System.out.println("Mods Directory: " + launcherArgs.modsDir.toAbsolutePath());
            System.out.println("Target Main Class: " + launcherArgs.targetMainClass);

            // Detect side (CLIENT vs SERVER) based on target main class
            final net.chainloader.api.environment.EnvType side;
            if (launcherArgs.targetMainClass.toLowerCase().contains("server") || 
                launcherArgs.targetMainClass.toLowerCase().contains("dedicated")) {
                side = net.chainloader.api.environment.EnvType.SERVER;
            } else {
                side = net.chainloader.api.environment.EnvType.CLIENT;
            }
            net.chainloader.api.environment.ChainLoaderEnv.registerBackend(() -> side);
            System.out.println("Environment Side: " + side);

            // Store game directory in system properties for path resolvers (like config managers)
            System.setProperty("chainloader.gameDir", launcherArgs.gameDir.toAbsolutePath().toString());

            // 3. Ensure directories exist
            if (!Files.exists(launcherArgs.gameDir)) {
                Files.createDirectories(launcherArgs.gameDir);
            }
            if (!Files.exists(launcherArgs.modsDir)) {
                Files.createDirectories(launcherArgs.modsDir);
            }

            // 4. Discover classpaths and jars (Libraries, Game JARs, Mods)
            loadingScreen.updateProgress(30, "Scanning directories for candidate mod JARs...");
            List<URL> classpathUrls = new ArrayList<>();
            
            // Add standard Java classpaths
            discoverAdditionalClasspaths(classpathUrls);

            // Add mod files to classpath next
            discoverMods(launcherArgs.modsDir, classpathUrls);
            
            // Scan and register mod metadata
            ModScanner.scanAndRegisterMods(launcherArgs.modsDir);

            // 5. Construct the custom ChainClassLoader
            loadingScreen.updateProgress(45, "Constructing isolated ClassLoader pipeline...");
            ClassLoader parentLoader = ChainLauncher.class.getClassLoader();
            ChainClassLoader classLoader = new ChainClassLoader(classpathUrls.toArray(new URL[0]), parentLoader);

            // 6. Register core class transformers
            loadingScreen.updateProgress(60, "Registering ASM remapping & access widening transformers...");
            registerTransformers(classLoader);

            // 7. Set Thread Context ClassLoader so ServiceLoader finds services in the same context
            Thread.currentThread().setContextClassLoader(classLoader);

            // 8. Discover available Chainlink modules via ServiceLoader
            loadingScreen.updateProgress(70, "Discovering compatibility modules...");
            ServiceLoader<Chainlink> compatLoader = ServiceLoader.load(Chainlink.class, classLoader);
            List<Chainlink> availableLinks = new ArrayList<>();
            for (Chainlink link : compatLoader) {
                availableLinks.add(link);
                System.out.println("[ChainLoader] Discovered compat module: " + link.getClass().getName());
            }

            // Detect current game version
            String gameVersion = detectGameVersion(launcherArgs);
            System.out.println("[ChainLoader] Detected Game Version: " + gameVersion);

            // Wake up matching Chainlinks based on discovered mods
            List<ModScanner.DiscoveredMod> mods = ModScanner.getDiscoveredMods();
            int totalLinks = availableLinks.size();
            int linkIndex = 0;
            for (Chainlink link : availableLinks) {
                linkIndex++;
                boolean shouldWakeUp = false;
                
                if (link.getSupportedLoaderType().equalsIgnoreCase("chainloader") ||
                    link.getSupportedLoaderType().equalsIgnoreCase("fabric") ||
                    link.getSupportedLoaderType().equalsIgnoreCase("forge")) {
                    if (versionMatches(link.getSupportedVersionRange(), gameVersion)) {
                        shouldWakeUp = true;
                    }
                }
                
                if (!shouldWakeUp) {
                    for (ModScanner.DiscoveredMod mod : mods) {
                        String modLoader = mod.metadata.getOriginalLoaderType();
                        if (link.getSupportedLoaderType().equalsIgnoreCase(modLoader)) {
                            String modVersion = getModTargetMinecraftVersion(mod.metadata, gameVersion);
                            if (versionMatches(link.getSupportedVersionRange(), modVersion)) {
                                shouldWakeUp = true;
                                break;
                            }
                        }
                    }
                }
                
                if (shouldWakeUp) {
                    String compatName = link.getClass().getSimpleName();
                    String loaderType = link.getSupportedLoaderType();
                    String versionRange = link.getSupportedVersionRange();
                    int progressStep = 70 + (int) ((double) linkIndex / Math.max(1, totalLinks) * 5.0);
                    
                    loadingScreen.updateProgress(progressStep, "Loading compatibility layer: " + compatName + " (" + loaderType + " " + versionRange + ")...");
                    loadingScreen.log("Bootstrap", "Waking up compatibility module " + compatName + "...");
                    
                    link.onWakeUp(classLoader);
                    activeLinks.add(link);
                }
            }

            // 9. Mod initialization is deferred until after game registry bootstrap
            loadingScreen.updateProgress(75, "Mod initialization deferred until game bootstrap...");

            // 10. Finalize and Invoke target game entrypoint
            loadingScreen.updateProgress(90, "Finalizing context handoff...");
            System.out.println("Handing off control to target main class: " + launcherArgs.targetMainClass);
            System.out.println("Remaining arguments: " + Arrays.toString(launcherArgs.remainingArgs));
            System.out.println("--------------------------------------------------");

            // Close loading GUI just before launching Minecraft if on server side,
            // otherwise keep it open and let Minecraft client initialization close it.
            if (side == net.chainloader.api.environment.EnvType.SERVER) {
                loadingScreen.updateProgress(100, "Launching Minecraft Game Engine...");
                loadingScreen.close();
            } else {
                loadingScreen.updateProgress(80, "Launching Minecraft Game Engine...");
            }

            Class<?> targetClass;
            try {
                targetClass = Class.forName(launcherArgs.targetMainClass, true, classLoader);
            } catch (ClassNotFoundException | LinkageError e) {
                if (launcherArgs.targetMainClass.equals(DEFAULT_TARGET) || 
                    launcherArgs.targetMainClass.equals("net.minecraft.server.Main")) {
                    System.out.println("  [Warning] Target main class '" + launcherArgs.targetMainClass + "' failed to load (" + e.getClass().getSimpleName() + ": " + e.getMessage() + ").");
                    System.out.println("  [Info] Falling back to net.chainloader.loader.core.MockGameMain for testing...");
                    targetClass = Class.forName("net.chainloader.loader.core.MockGameMain", true, classLoader);
                } else {
                    throw e;
                }
            }
            Method mainMethod = targetClass.getMethod("main", String[].class);
            
            // Invoke the target main method
            mainMethod.invoke(null, (Object) launcherArgs.remainingArgs);

        } catch (Throwable t) {
            loadingScreen.log("[FATAL ERROR] " + t.getMessage());
            loadingScreen.updateProgress(0, "Error: Boot failed.");
            System.err.println("Fatal error during ChainLoader bootstrap sequence:");
            t.printStackTrace();
            System.exit(1);
        }
    }

    private static String detectGameVersion(LauncherArgs args) {
        String sysProp = System.getProperty("chainloader.gameVersion");
        if (sysProp != null && !sysProp.isEmpty()) {
            return sysProp;
        }
        if (args != null && args.remainingArgs != null) {
            for (int i = 0; i < args.remainingArgs.length - 1; i++) {
                if (args.remainingArgs[i].equals("--version")) {
                    return args.remainingArgs[i + 1];
                }
            }
        }
        return "1.21.1";
    }

    public static boolean versionMatches(String range, String version) {
        if (range == null || range.isEmpty() || range.equals("*")) return true;
        if (version == null || version.isEmpty()) return true;
        
        if (range.startsWith("[") && range.endsWith("]")) {
            String[] parts = range.substring(1, range.length() - 1).split(",");
            if (parts.length == 1) {
                return version.trim().equals(parts[0].trim());
            } else if (parts.length == 2) {
                String min = parts[0].trim();
                String max = parts[1].trim();
                return compareVersions(version, min) >= 0 && compareVersions(version, max) <= 0;
            }
        }
        return range.contains(version) || version.contains(range);
    }

    public static int compareVersions(String v1, String v2) {
        String[] p1 = v1.split("\\.");
        String[] p2 = v2.split("\\.");
        int length = Math.max(p1.length, p2.length);
        for (int i = 0; i < length; i++) {
            String s1 = i < p1.length ? p1[i].replaceAll("[^0-9]", "") : "";
            String s2 = i < p2.length ? p2[i].replaceAll("[^0-9]", "") : "";
            int num1 = s1.isEmpty() ? 0 : Integer.parseInt(s1);
            int num2 = s2.isEmpty() ? 0 : Integer.parseInt(s2);
            if (num1 != num2) {
                return num1 - num2;
            }
        }
        return 0;
    }

    private static LauncherArgs parseArgs(String[] args) {
        Path gameDir = Paths.get(".");
        Path modsDir = Paths.get("mods");
        String targetMainClass = System.getProperty("chainloader.target", DEFAULT_TARGET);
        List<String> remainingArgsList = new ArrayList<>();

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg.equals("--gameDir") && i + 1 < args.length) {
                gameDir = Paths.get(args[++i]);
            } else if (arg.equals("--modsDir") && i + 1 < args.length) {
                modsDir = Paths.get(args[++i]);
            } else if (arg.equals("--loaderTarget") && i + 1 < args.length) {
                targetMainClass = args[++i];
            } else {
                remainingArgsList.add(arg);
            }
        }

        String sysGameDir = System.getProperty("chainloader.gameDir");
        if (sysGameDir != null) {
            gameDir = Paths.get(sysGameDir);
        }
        String sysModsDir = System.getProperty("chainloader.modsDir");
        if (sysModsDir != null) {
            modsDir = Paths.get(sysModsDir);
        }

        if (!modsDir.isAbsolute()) {
            modsDir = gameDir.resolve(modsDir);
        }

        return new LauncherArgs(gameDir, modsDir, targetMainClass, remainingArgsList.toArray(new String[0]));
    }

    private static void discoverMods(Path modsDir, List<URL> urls) {
        System.out.println("Scanning mods directory for candidate JAR files...");
        try (Stream<Path> walk = Files.walk(modsDir, 1)) {
            walk.filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".jar"))
                .forEach(path -> {
                    try {
                        URL url = path.toUri().toURL();
                        urls.add(url);
                        System.out.println("  Discovered mod JAR: " + path.getFileName() + " -> Adding to classpath.");
                    } catch (Exception e) {
                        System.err.println("  Failed to convert path " + path + " to URL: " + e.getMessage());
                    }
                });
        } catch (Exception e) {
            System.err.println("Error reading mods directory: " + e.getMessage());
        }
    }

    private static void discoverAdditionalClasspaths(List<URL> urls) {
        String extraClasspaths = System.getProperty("chainloader.additionalClasspath");
        if (extraClasspaths != null) {
            for (String pathStr : extraClasspaths.split(File.pathSeparator)) {
                try {
                    URL url = Paths.get(pathStr).toUri().toURL();
                    urls.add(url);
                    System.out.println("  Added extra classpath resource: " + pathStr);
                } catch (Exception e) {
                    System.err.println("  Failed to add extra classpath " + pathStr + ": " + e.getMessage());
                }
            }
        }
    }

    public static void registerTransformers(ChainClassLoader classLoader) {
        // Register the SideAnnotationStripper first in the pipeline
        net.chainloader.api.environment.EnvType currentEnv = net.chainloader.api.environment.ChainLoaderEnv.getEnvType();
        classLoader.addTransformer(new net.chainloader.loader.core.transform.SideAnnotationStripper(currentEnv));

        // Register the BytecodeTransformer to remap references at runtime
        classLoader.addTransformer(new net.chainloader.loader.transformer.BytecodeTransformer()::transform);

        // Register the AccessWidener compiler
        net.chainloader.loader.access.AccessWidener accessWidener = new net.chainloader.loader.access.AccessWidener();
        net.chainloader.loader.core.transform.AccessWidenerCompiler accessCompiler = new net.chainloader.loader.core.transform.AccessWidenerCompiler();
        accessCompiler.compile(accessWidener);
        classLoader.addTransformer(accessCompiler.createClassTransformer());
    }

    private static class LauncherArgs {
        final Path gameDir;
        final Path modsDir;
        final String targetMainClass;
        final String[] remainingArgs;

        LauncherArgs(Path gameDir, Path modsDir, String targetMainClass, String[] remainingArgs) {
            this.gameDir = gameDir;
            this.modsDir = modsDir;
            this.targetMainClass = targetMainClass;
            this.remainingArgs = remainingArgs;
        }
    }

    public static String getModTargetMinecraftVersion(net.chainloader.loader.core.ChainModMetadata metadata, String fallbackVersion) {
        if (metadata == null) return fallbackVersion;
        for (net.chainloader.loader.core.ChainModMetadata.ModDependency dep : metadata.getDependencies()) {
            if ("minecraft".equalsIgnoreCase(dep.getModId())) {
                String req = dep.getVersionRequirement();
                if (req == null || req.isEmpty()) continue;
                if (req.startsWith("[") || req.startsWith("(")) {
                    String stripped = req.substring(1, req.length() - 1);
                    String[] parts = stripped.split(",");
                    if (parts.length > 0) {
                        return parts[parts.length - 1].trim();
                    }
                }
                return req.replaceAll("[>=<^~=]", "").trim();
            }
        }
        return fallbackVersion;
    }
}
