package net.chainloader.loader.core;

import net.minecraft.server.MinecraftServer;
import net.minecraft.core.RegistryAccess;

public class MockMinecraftServer extends MinecraftServer {
    @Override
    public net.minecraft.core.RegistryAccess.Frozen registryAccess() {
        return net.minecraft.core.RegistryAccess.EMPTY;
    }
}
