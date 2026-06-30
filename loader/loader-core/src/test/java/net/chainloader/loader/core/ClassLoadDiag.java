package net.chainloader.loader.core;

import java.net.URL;

public class ClassLoadDiag {
    public static void main(String[] args) {
        System.out.println("URLs in AppClassLoader:");
        ClassLoader cl = ClassLoadDiag.class.getClassLoader();
        if (cl instanceof java.net.URLClassLoader) {
            for (URL url : ((java.net.URLClassLoader) cl).getURLs()) {
                System.out.println("  " + url);
            }
        } else {
            System.out.println("AppClassLoader is not URLClassLoader: " + cl.getClass().getName());
            System.out.println("java.class.path = " + System.getProperty("java.class.path"));
        }
        try {
            Class<?> cls = Class.forName("net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent");
            System.out.println("Successfully loaded RegisterCapabilitiesEvent: " + cls);
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }
}
