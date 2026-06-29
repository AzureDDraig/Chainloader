package net.neoforged.fml;

public abstract class ModContainer {
    public abstract String getModId();
    public void registerConfig(net.neoforged.fml.config.ModConfig.Type type, net.neoforged.fml.config.IConfigSpec spec) {}
    public void registerExtensionPoint(Class<?> extensionPointClass, net.neoforged.fml.IExtensionPoint extensionPoint) {}
}
