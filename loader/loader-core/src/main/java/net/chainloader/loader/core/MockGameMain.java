package net.chainloader.loader.core;

import java.util.Arrays;

/**
 * A mockup target game main class that can be executed to test the full ChainLoader
 * bootstrap sequence without requiring the real Minecraft client on the classpath.
 */
public class MockGameMain {
    public static void main(String[] args) {
        System.out.println("==================================================");
        System.out.println("  ChainLoader Mock Game Engine Initialized!");
        System.out.println("  Active environment: CLIENT");
        System.out.println("  Arguments passed: " + Arrays.toString(args));
        System.out.println("  Testing end-to-end launch sequence: SUCCESS");
        System.out.println("==================================================");

        // Close early loading screen if active
        net.chainloader.loader.core.gui.EarlyLoadingScreen.getInstance().close();
    }
}
