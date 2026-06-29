package dev.architectury.networking;

import net.minecraft.resources.ResourceLocation;

public class NetworkManager {
    public enum Side {
        C2S,
        S2C
    }

    public interface PacketContext {
        Object getPlayer();
        void queue(Runnable task);
        Side getSide();
    }

    public interface NetworkReceiver {
        void receive(Object buf, PacketContext context);
    }

    public static Side c2s() {
        return Side.C2S;
    }

    public static Side s2c() {
        return Side.S2C;
    }

    public static void registerReceiver(Side side, ResourceLocation id, NetworkReceiver receiver) {
        System.out.println("[ChainLoader] Mock NetworkManager registered receiver for: " + id);
    }
}
