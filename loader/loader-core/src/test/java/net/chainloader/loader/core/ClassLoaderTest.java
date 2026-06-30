package net.chainloader.loader.core;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class ClassLoaderTest {
    public static void main(String[] args) {
        try {
            List<URL> urls = new ArrayList<>();
            String classPath = System.getProperty("java.class.path");
            if (classPath != null) {
                for (String entry : classPath.split(File.pathSeparator)) {
                    urls.add(new File(entry).toURI().toURL());
                }
            }
            
            ChainClassLoader loader = new ChainClassLoader(urls.toArray(new URL[0]), ClassLoaderTest.class.getClassLoader());
            loader.addSelfLoadedPrefix("net.neoforged.");
            
            System.out.println("URLs in ChainClassLoader:");
            for (URL u : loader.getURLs()) {
                System.out.println("  " + u);
            }
            
            String target = "net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent";
            String resourcePath = target.replace('.', '/') + ".class";
            System.out.println("findResource result: " + loader.findResource(resourcePath));
            
            Class<?> cls = loader.loadClass(target);
            System.out.println("Successfully loaded via ChainClassLoader: " + cls + " (ClassLoader: " + cls.getClassLoader() + ")");
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }
}
