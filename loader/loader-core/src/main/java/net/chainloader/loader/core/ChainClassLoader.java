package net.chainloader.loader.core;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLConnection;
import java.security.CodeSigner;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Custom ClassLoader for ChainLoader that manages mod loading,
 * class transformation, and isolated delegation logic.
 */
public class ChainClassLoader extends URLClassLoader {

    private static final boolean DEBUG = Boolean.getBoolean("chainloader.debug");

    static {
        registerAsParallelCapable();
    }

    /**
     * Interface for class transformers that can modify class bytes at runtime.
     */
    @FunctionalInterface
    public interface ClassTransformer {
        /**
         * Transforms the given class bytes.
         *
         * @param className The binary name of the class (e.g. "net.minecraft.client.main.Main")
         * @param classBytes The original class bytes
         * @return The transformed class bytes, or the original bytes if no transformation was applied
         */
        byte[] transform(String className, byte[] classBytes);
    }

    private final List<ClassTransformer> transformers = new CopyOnWriteArrayList<>();
    private final Set<String> parentDelegatedPrefixes = ConcurrentHashMap.newKeySet();
    private final Set<String> selfLoadedPrefixes = ConcurrentHashMap.newKeySet();

    public ChainClassLoader(URL[] urls, ClassLoader parent) {
        super(urls, parent);
        initializeDefaultDelegationRules();
        net.chainloader.loader.transformer.BytecodeTransformer.setClassLoader(this);
    }

    private void initializeDefaultDelegationRules() {
        // Packages that MUST be delegated to the parent classloader
        parentDelegatedPrefixes.add("java.");
        parentDelegatedPrefixes.add("javax.");
        parentDelegatedPrefixes.add("sun.");
        parentDelegatedPrefixes.add("com.sun.");
        parentDelegatedPrefixes.add("oracle.");
        parentDelegatedPrefixes.add("jdk.");
        parentDelegatedPrefixes.add("org.xml.");
        parentDelegatedPrefixes.add("org.w3c.");
        
        // Loader bootstrap classes must be loaded by the parent classloader
        parentDelegatedPrefixes.add("net.chainloader.loader.core.");
        parentDelegatedPrefixes.add("net.chainloader.loader.transformer.");
        parentDelegatedPrefixes.add("net.chainloader.loader.shim.");
        
        // Custom GUI classes should bypass parent delegation so they are loaded by this ClassLoader and can reference obfuscated game types
        selfLoadedPrefixes.add("net.chainloader.loader.core.gui.");
        
        // ASM libraries used by loader core should also be delegated
        parentDelegatedPrefixes.add("org.objectweb.asm.");
        parentDelegatedPrefixes.add("org.ow2.asm.");

        // Shared library dependencies from system classpath to prevent Parent <-> Child lock inversions
        parentDelegatedPrefixes.add("com.google.common.");
        parentDelegatedPrefixes.add("com.google.gson.");
        parentDelegatedPrefixes.add("com.google.thirdparty.");
        parentDelegatedPrefixes.add("io.netty.");
        parentDelegatedPrefixes.add("org.lwjgl.");
        parentDelegatedPrefixes.add("org.apache.logging.log4j.");
        parentDelegatedPrefixes.add("org.apache.commons.");
        parentDelegatedPrefixes.add("org.slf4j.");
        parentDelegatedPrefixes.add("com.mojang.authlib.");
        parentDelegatedPrefixes.add("com.mojang.brigadier.");
        parentDelegatedPrefixes.add("com.mojang.datafixers.");
        parentDelegatedPrefixes.add("com.mojang.serialization.");
        parentDelegatedPrefixes.add("com.mojang.text2speech.");
        parentDelegatedPrefixes.add("org.joml.");
        parentDelegatedPrefixes.add("it.unimi.dsi.fastutil.");
        parentDelegatedPrefixes.add("com.ibm.icu.");
        parentDelegatedPrefixes.add("com.sun.jna.");
        parentDelegatedPrefixes.add("net.java.dev.jna.");
        parentDelegatedPrefixes.add("oshi.");

        // Delegate SPI packages to parent loader
        parentDelegatedPrefixes.add("net.neoforged.neoforgespi.");
        parentDelegatedPrefixes.add("net.minecraftforge.forgespi.");
        parentDelegatedPrefixes.add("net.chainloader.api.environment.");
    }

    /**
     * Registers a package prefix to always be delegated to the parent class loader.
     */
    public void addParentDelegatedPrefix(String prefix) {
        parentDelegatedPrefixes.add(prefix);
    }

    /**
     * Registers a package prefix to always be loaded by this class loader, bypassing parent.
     */
    public void addSelfLoadedPrefix(String prefix) {
        selfLoadedPrefixes.add(prefix);
    }

    /**
     * Adds a class transformer to the pipeline.
     */
    public void addTransformer(ClassTransformer transformer) {
        transformers.add(transformer);
    }

    /**
     * Dynamic classpath extension.
     */
    @Override
    public void addURL(URL url) {
        super.addURL(url);
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        String mappedName = name;
        net.chainloader.loader.transformer.BytecodeTransformer transformer = net.chainloader.loader.transformer.BytecodeTransformer.getInstance();
        if (transformer != null) {
            mappedName = transformer.mapClassName(name);
        } else {
            System.out.println("[ChainClassLoader] Warning: BytecodeTransformer instance is null in loadClass for " + name);
        }
        // System.out.println("[ChainClassLoader] loadClass: " + name + " -> " + mappedName);

        // 1. Check delegation rules first, before acquiring lock to avoid deadlocks
        if (shouldDelegateToParent(mappedName)) {
            return super.loadClass(mappedName, resolve);
        }

        // 2. Fast check if already loaded (findLoadedClass is thread-safe)
        Class<?> c = findLoadedClass(mappedName);
        if (c != null) {
            if (resolve) {
                resolveClass(c);
            }
            return c;
        }

        // 3. Try to load from this class loader with synchronization
        synchronized (getClassLoadingLock(mappedName)) {
            c = findLoadedClass(mappedName);
            if (c == null) {
                try {
                    c = findClass(mappedName);
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

        return super.loadClass(mappedName, resolve);
    }

    protected boolean shouldDelegateToParent(String name) {
        if (name.equals("net.chainloader.loader.compat.Chainlink")) {
            return true;
        }
        if (name.equals("net.chainloader.loader.core.gui.EarlyLoadingScreen")) {
            return true;
        }
        if (name.equals("net.chainloader.loader.core.MockServerLevel") || 
            name.equals("net.chainloader.loader.core.MockMinecraftServer")) {
            return false;
        }
        // Explicit self-loaded packages take precedence
        for (String prefix : selfLoadedPrefixes) {
            if (name.startsWith(prefix)) {
                return false;
            }
        }
        for (net.chainloader.loader.compat.Chainlink link : net.chainloader.loader.core.ChainLauncher.getActiveLinks()) {
            for (String prefix : link.getSelfLoadedPackages()) {
                if (name.startsWith(prefix)) {
                    return false;
                }
            }
        }
        // Then check if it is explicitly parent-delegated
        for (String prefix : parentDelegatedPrefixes) {
            if (name.startsWith(prefix)) {
                return true;
            }
        }
        // Default is to try self-loading first (parent-last) for game classes & mods
        return false;
    }

    @Override
    public URL findResource(String name) {
        if (name.startsWith("mezz/jei/")) {
            for (URL url : getURLs()) {
                if (url.getPath().endsWith(".jar")) {
                    URL res = getResourceFromURL(url, name);
                    if (res != null) {
                        return res;
                    }
                }
            }
        }
        return super.findResource(name);
    }

    private URL getResourceFromURL(URL baseUrl, String resourceName) {
        try {
            URL resourceUrl;
            if (baseUrl.getPath().endsWith(".jar")) {
                resourceUrl = new URL("jar:" + baseUrl.toExternalForm() + "!/" + resourceName);
            } else {
                resourceUrl = new URL(baseUrl, resourceName);
            }
            try (InputStream is = resourceUrl.openStream()) {
                if (is != null) {
                    return resourceUrl;
                }
            }
        } catch (Exception e) {
            // Ignore
        }
        return null;
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        String resourcePath = name.replace('.', '/') + ".class";
        URL classResource = findResource(resourcePath);
        if (classResource == null) {
            if (DEBUG) {
                System.err.println("[ChainClassLoader DEBUG] findResource returned null for: " + resourcePath);
                System.err.println("[ChainClassLoader DEBUG] URLs in classloader:");
                for (URL u : getURLs()) {
                    System.err.println("  " + u);
                }
            }
            throw new ClassNotFoundException(name);
        }

        try {
            byte[] rawBytes = readBytes(classResource);
            byte[] transformedBytes;
            try {
                transformedBytes = transformBytes(name, rawBytes);
            } catch (net.chainloader.loader.core.transform.SideAnnotationStripper.SideStrippedException e) {
                throw new ClassNotFoundException("Class " + name + " was stripped because it is not active on this environment side.", e);
            }

            if (name.equals("net.minecraftforge.registries.ForgeRegistries$Keys") || 
                name.equals("net.minecraftforge.registries.ForgeRegistries") ||
                name.equals("net.chainloader.loader.core.gui.MainMenuHelper")) {
                try {
                    java.nio.file.Files.write(java.nio.file.Paths.get("transformed_" + name.replace('$', '_') + ".class"), transformedBytes);
                    System.out.println("[ChainClassLoader DEBUG] Dumped transformed class: " + name);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            
            // Define the package if necessary
            definePackageForClass(name);

            // Construct CodeSource
            CodeSource codeSource = getCodeSource(classResource);

            return defineClass(name, transformedBytes, 0, transformedBytes.length, codeSource);
        } catch (IOException e) {
            throw new ClassNotFoundException("Failed to read class bytes for " + name, e);
        }
    }

    private byte[] transformBytes(String name, byte[] bytes) {
        byte[] current = bytes;
        for (ClassTransformer transformer : transformers) {
            byte[] next = transformer.transform(name, current);
            if (next != null) {
                current = next;
            }
        }
        return current;
    }

    private byte[] readBytes(URL url) throws IOException {
        try (InputStream is = url.openStream();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = is.read(buffer)) != -1) {
                baos.write(buffer, 0, read);
            }
            return baos.toByteArray();
        }
    }

    private void definePackageForClass(String className) {
        int lastDot = className.lastIndexOf('.');
        if (lastDot != -1) {
            String packageName = className.substring(0, lastDot);
            if (getPackage(packageName) == null) {
                try {
                    definePackage(packageName, null, null, null, null, null, null, null);
                } catch (IllegalArgumentException e) {
                    // Package might have been defined concurrently in another thread
                }
            }
        }
    }

    private CodeSource getCodeSource(URL classResource) {
        try {
            URLConnection connection = classResource.openConnection();
            if (connection instanceof JarURLConnection) {
                return new CodeSource(((JarURLConnection) connection).getJarFileURL(), (CodeSigner[]) null);
            }
            // For general URLs, attempt to extract root URL from class resource URL
            String urlStr = classResource.toString();
            if (urlStr.endsWith(".class")) {
                int index = urlStr.lastIndexOf(classResource.getPath());
                if (index != -1) {
                    return new CodeSource(new URL(urlStr.substring(0, index)), (CodeSigner[]) null);
                }
            }
            return new CodeSource(classResource, (CodeSigner[]) null);
        } catch (Exception e) {
            return new CodeSource(classResource, (CodeSigner[]) null);
        }
    }
}
