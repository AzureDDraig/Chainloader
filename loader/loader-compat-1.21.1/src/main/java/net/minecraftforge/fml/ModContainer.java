package net.minecraftforge.fml;

public abstract class ModContainer {
    public abstract String getModId();
    public void registerConfig(net.minecraftforge.fml.config.ModConfig.Type type, net.minecraftforge.fml.config.IConfigSpec spec) {}
    public void registerConfig(net.minecraftforge.fml.config.ModConfig.Type type, net.minecraftforge.common.ForgeConfigSpec spec) {}
}
