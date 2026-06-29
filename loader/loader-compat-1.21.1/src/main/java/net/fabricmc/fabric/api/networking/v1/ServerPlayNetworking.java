package net.fabricmc.fabric.api.networking.v1;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.network.FriendlyByteBuf;
import java.util.Map;
import java.util.Set;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;

public class ServerPlayNetworking {
    public interface PlayChannelHandler {
        void receive(MinecraftServer server, ServerPlayer player, ServerGamePacketListenerImpl handler, FriendlyByteBuf buf, PacketSender responseSender);
    }

    private static final Map<ResourceLocation, PlayChannelHandler> GLOBAL_RECEIVERS = new ConcurrentHashMap<>();
    private static final Map<Object, Map<ResourceLocation, PlayChannelHandler>> CONNECTION_RECEIVERS = new ConcurrentHashMap<>();

    public static boolean registerGlobalReceiver(ResourceLocation channelName, PlayChannelHandler channelHandler) {
        GLOBAL_RECEIVERS.put(channelName, channelHandler);
        return true;
    }

    public static PlayChannelHandler unregisterGlobalReceiver(ResourceLocation channelName) {
        return GLOBAL_RECEIVERS.remove(channelName);
    }

    public static boolean registerReceiver(Object handler, ResourceLocation channelName, PlayChannelHandler channelHandler) {
        if (handler != null) {
            CONNECTION_RECEIVERS.computeIfAbsent(handler, k -> new ConcurrentHashMap<>()).put(channelName, channelHandler);
        }
        return true;
    }

    public static PlayChannelHandler unregisterReceiver(Object handler, ResourceLocation channelName) {
        if (handler != null) {
            Map<ResourceLocation, PlayChannelHandler> map = CONNECTION_RECEIVERS.get(handler);
            if (map != null) {
                PlayChannelHandler removed = map.remove(channelName);
                if (map.isEmpty()) {
                    CONNECTION_RECEIVERS.remove(handler);
                }
                return removed;
            }
        }
        return null;
    }

    public static void clearConnectionStates(Object handler) {
        if (handler != null) {
            CONNECTION_RECEIVERS.remove(handler);
            System.out.println("[ServerPlayNetworking] Cleared connection states for handler: " + handler);
        }
    }

    public static void send(ServerPlayer player, ResourceLocation channelName, FriendlyByteBuf buf) {
        System.out.println("[ServerPlayNetworking] send packet to player: " + player + " on channel: " + channelName);
    }

    // Accessors for verification/testing
    public static Set<ResourceLocation> getGlobalReceivers() {
        return Collections.unmodifiableSet(GLOBAL_RECEIVERS.keySet());
    }

    public static Map<ResourceLocation, PlayChannelHandler> getActiveReceivers(Object handler) {
        if (handler == null) return Collections.emptyMap();
        Map<ResourceLocation, PlayChannelHandler> map = CONNECTION_RECEIVERS.get(handler);
        return map != null ? Collections.unmodifiableMap(map) : Collections.emptyMap();
    }
}
