package net.neoforged.fml;

public class ModLoadingContext {
    private static final ModLoadingContext INSTANCE = new ModLoadingContext();

    public static ModLoadingContext get() {
        return INSTANCE;
    }

    public ModContainer getActiveContainer() {
        return new MockModContainer("dummy");
    }

    public void registerConfig(net.neoforged.fml.config.ModConfig.Type type, net.neoforged.fml.config.IConfigSpec spec) {
        System.out.println("[ChainLoader] Mock NeoForge ModLoadingContext registered config.");
    }

    public void registerConfig(net.neoforged.fml.config.ModConfig.Type type, net.neoforged.fml.config.IConfigSpec spec, String fileName) {
        System.out.println("[ChainLoader] Mock NeoForge ModLoadingContext registered config: " + fileName);
    }
}
