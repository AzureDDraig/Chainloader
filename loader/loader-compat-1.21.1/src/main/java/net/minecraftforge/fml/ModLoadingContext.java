package net.minecraftforge.fml;

public class ModLoadingContext {
    private static final ModLoadingContext INSTANCE = new ModLoadingContext();

    public static ModLoadingContext get() {
        return INSTANCE;
    }

    public ModContainer getActiveContainer() {
        return new MockModContainer("dummy");
    }

    public void registerConfig(net.minecraftforge.fml.config.ModConfig.Type type, net.minecraftforge.fml.config.IConfigSpec spec) {
        System.out.println("[ChainLoader] Mock ModLoadingContext registered config.");
    }

    public void registerConfig(net.minecraftforge.fml.config.ModConfig.Type type, net.minecraftforge.fml.config.IConfigSpec spec, String fileName) {
        System.out.println("[ChainLoader] Mock ModLoadingContext registered config: " + fileName);
    }

    public void registerConfig(net.minecraftforge.fml.config.ModConfig.Type type, net.minecraftforge.common.ForgeConfigSpec spec) {
        System.out.println("[ChainLoader] Mock ModLoadingContext registered config.");
    }

    public void registerConfig(net.minecraftforge.fml.config.ModConfig.Type type, net.minecraftforge.common.ForgeConfigSpec spec, String fileName) {
        System.out.println("[ChainLoader] Mock ModLoadingContext registered config: " + fileName);
    }

    public void registerExtensionPoint(Class<?> extensionPoint, java.util.function.Supplier<?> extension) {
        System.out.println("[ChainLoader] Mock ModLoadingContext registered extension point: " + extensionPoint.getName());
    }
}
