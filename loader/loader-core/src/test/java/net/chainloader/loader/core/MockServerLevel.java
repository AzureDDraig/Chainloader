package net.chainloader.loader.core;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.MinecraftServer;

public class MockServerLevel extends ServerLevel {
    private MinecraftServer server;

    public void setServer(MinecraftServer server) {
        this.server = server;
    }

    @Override
    public MinecraftServer getServer() {
        return this.server;
    }

    private net.minecraft.core.RegistryAccess registryAccess;

    public void setRegistryAccess(net.minecraft.core.RegistryAccess registryAccess) {
        this.registryAccess = registryAccess;
    }

    @Override
    public net.minecraft.core.RegistryAccess registryAccess() {
        if (this.registryAccess != null) {
            return this.registryAccess;
        }
        return super.registryAccess();
    }
}
