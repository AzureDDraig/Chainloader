package net.example;

/**
 * A mock mod entry point used during simulation and testing
 * to verify that ChainLoader can dynamically resolve and initialize
 * Fabric/ChainLoader entry points on the classpath.
 */
public class ExampleMod {
    public void onInitialize() {
        System.out.println("==================================================");
        System.out.println("  Mock ExampleMod Initialized Successfully!");
        System.out.println("==================================================");
    }
}
