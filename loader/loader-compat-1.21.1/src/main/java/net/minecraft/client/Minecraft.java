package net.minecraft.client;

/**
 * Compile-time stub for net.minecraft.client.Minecraft.
 */
public class Minecraft {
    private static Minecraft instance = new Minecraft();

    public net.minecraft.client.multiplayer.ClientLevel level;
    public net.minecraft.client.player.LocalPlayer player;
    public Options options = new Options();

    public static Minecraft getInstance() {
        return instance;
    }

    public net.minecraft.server.MinecraftServer getSingleplayerServer() {
        return null;
    }

    public void setScreen(net.minecraft.client.gui.screens.Screen screen) {
    }

    public net.minecraft.server.packs.resources.ResourceManager getResourceManager() {
        return null;
    }

    public net.minecraft.client.renderer.RenderBuffers renderBuffers() {
        return null;
    }

    public net.minecraft.client.multiplayer.ClientPacketListener getConnection() {
        return null;
    }
}
