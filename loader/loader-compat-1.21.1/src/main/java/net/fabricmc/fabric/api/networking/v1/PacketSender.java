package net.fabricmc.fabric.api.networking.v1;

public interface PacketSender {
    void sendPacket(Object packet);
}
