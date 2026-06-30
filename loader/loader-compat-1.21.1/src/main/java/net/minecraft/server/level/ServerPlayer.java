package net.minecraft.server.level;

public class ServerPlayer extends net.minecraft.world.entity.player.Player {
    public net.minecraft.server.network.ServerGamePacketListenerImpl connection;
    public net.minecraft.server.MinecraftServer server;
}
