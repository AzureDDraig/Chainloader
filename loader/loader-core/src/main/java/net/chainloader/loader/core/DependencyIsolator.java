package net.chainloader.loader.core;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * The DependencyIsolator is responsible for managing classpath isolation and masking rules
 * for individual mods in ChainLoader.
 * 
 * In a modded Minecraft environment, different mods might bundle different versions of the same
 * library (e.g., a specific version of Gson, Guava, or ASM). Without isolation, these libraries
 * conflict on the global classpath, leading to NoSuchMethodError, LinkageError, or class casting issues.
 * 
 * This class provides mechanisms to:
 * 1. Define masking rules (packages that should be isolated or delegated).
 * 2. Create and manage isolated classloaders per mod or mod group.
 * 3. Enforce boundaries so that mod-bundled libraries do not pollute the parent (game) classpath
 *    and vice-versa.
 */
public class DependencyIsolator {
    private static final Logger LOGGER = Logger.getLogger(DependencyIsolator.class.getName());

    // Map of mod ID to its specific masking rules
    private final Map<String, MaskingRules> modRules = new ConcurrentHashMap<>();
    
    public Map<String, MaskingRules> getModRules() {
        return modRules;
    }
    
    // Map of mod ID to its isolated ClassLoader
    private final Map<String, IsolatedClassLoader> modClassLoaders = new ConcurrentHashMap<>();

    /**
     * Rules defining how classes should be loaded for a specific mod.
     */
    public static class MaskingRules {
        private final String modId;
        // Packages that MUST be loaded from the mod's own JAR/libraries, bypassing parent delegation.
        private final Set<String> isolatedPackages = new HashSet<>();
        // Packages that MUST NOT be loaded from the mod (always delegate to parent/Minecraft).
        private final Set<String> sharedPackages = new HashSet<>();

        public MaskingRules(String modId) {
            this.modId = modId;
        }

        /**
         * Add a package prefix to be isolated (e.g., "org.slf4j", "com.google.gson").
         *
         * @param packagePrefix the package prefix to isolate
         * @return this MaskingRules instance for chaining
         */
        public MaskingRules isolatePackage(String packagePrefix) {
            isolatedPackages.add(packagePrefix);
            return this;
        }

        /**
         * Add a package prefix that should always be loaded from the parent/game classloader.
         *
         * @param packagePrefix the package prefix to share
         * @return this MaskingRules instance for chaining
         */
        public MaskingRules sharePackage(String packagePrefix) {
            sharedPackages.add(packagePrefix);
            return this;
        }

        public Set<String> getIsolatedPackages() {
            return Collections.unmodifiableSet(isolatedPackages);
        }

        public Set<String> getSharedPackages() {
            return Collections.unmodifiableSet(sharedPackages);
        }

        /**
         * Determines if a class name matches the isolation rules.
         * 
         * @param className the fully qualified class name
         * @return true if the class must be isolated (loaded directly by the mod's classloader)
         */
        public boolean shouldIsolate(String className) {
            // Shared packages take precedence: if it's explicitly shared, do not isolate.
            for (String shared : sharedPackages) {
                if (className.startsWith(shared)) {
                    return false;
                }
            }
            // Check if it matches any isolated packages
            for (String isolated : isolatedPackages) {
                if (className.startsWith(isolated)) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * Register masking rules for a given mod.
     *
     * @param modId the unique identifier of the mod
     * @param rules the masking rules to register
     */
    public void registerRules(String modId, MaskingRules rules) {
        modRules.put(modId, rules);
        LOGGER.info("Registered dependency isolation rules for mod: " + modId);
    }

    /**
     * Create an isolated ClassLoader for a mod, using its bundled library URLs.
     * 
     * @param modId the unique identifier of the mod
     * @param parent the parent ClassLoader (typically the game or system classloader)
     * @param libraryUrls URLs of the mod's bundled libraries/JARs
     * @return the configured ClassLoader
     */
    public ClassLoader createIsolatedClassLoader(String modId, ClassLoader parent, URL[] libraryUrls) {
        MaskingRules rules = modRules.computeIfAbsent(modId, MaskingRules::new);
        IsolatedClassLoader classLoader = new IsolatedClassLoader(libraryUrls, parent, rules);
        modClassLoaders.put(modId, classLoader);
        return classLoader;
    }

    /**
     * Retrieve an existing isolated ClassLoader for a mod.
     *
     * @param modId the unique identifier of the mod
     * @return an Optional containing the ClassLoader, or empty if not found
     */
    public Optional<ClassLoader> getClassLoader(String modId) {
        return Optional.ofNullable(modClassLoaders.get(modId));
    }

    /**
     * A custom ClassLoader that filters and routes class loading requests based on masking rules.
     */
    public static class IsolatedClassLoader extends URLClassLoader {
        static {
            registerAsParallelCapable();
        }

        private final MaskingRules rules;

        public IsolatedClassLoader(URL[] urls, ClassLoader parent, MaskingRules rules) {
            super(urls, parent);
            this.rules = Objects.requireNonNull(rules, "Masking rules cannot be null");
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            // 1. If not isolated, load parent-first immediately without acquiring lock
            if (!rules.shouldIsolate(name)) {
                return super.loadClass(name, resolve);
            }

            // 2. Check if already loaded (findLoadedClass is thread-safe)
            Class<?> c = findLoadedClass(name);
            if (c != null) {
                if (resolve) {
                    resolveClass(c);
                }
                return c;
            }

            // 3. Try to load local class under synchronization
            synchronized (getClassLoadingLock(name)) {
                c = findLoadedClass(name);
                if (c == null) {
                    try {
                        c = findClass(name);
                    } catch (ClassNotFoundException e) {
                        // Fall through to fallback
                    }
                }
            }

            // 4. Fallback to parent outside the synchronized block
            if (c != null) {
                if (resolve) {
                    resolveClass(c);
                }
                return c;
            }
            return super.loadClass(name, resolve);
        }

        @Override
        public URL getResource(String name) {
            // Convert resource path to class name check format if applicable
            String possibleClassName = resourceNameToClassName(name);
            if (possibleClassName != null && rules.shouldIsolate(possibleClassName)) {
                URL url = findResource(name);
                if (url != null) {
                    return url;
                }
            }
            return super.getResource(name);
        }

        @Override
        public Enumeration<URL> getResources(String name) throws IOException {
            String possibleClassName = resourceNameToClassName(name);
            if (possibleClassName != null && rules.shouldIsolate(possibleClassName)) {
                return findResources(name);
            }
            return super.getResources(name);
        }

        /**
         * Utility to convert resource paths (e.g. "org/example/MyClass.class") to binary class names.
         */
        private String resourceNameToClassName(String resourceName) {
            if (resourceName.endsWith(".class")) {
                String className = resourceName.substring(0, resourceName.length() - 6);
                return className.replace('/', '.');
            }
            return null;
        }
    }
}
