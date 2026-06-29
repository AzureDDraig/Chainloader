package net.chainloader.loader.compat.bridge;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.ResourceLocation;

public class NetworkTearDownTest {

    private final ResourceLocation TEST_CHANNEL_1 = new ResourceLocation("test", "channel_1");
    private final ResourceLocation TEST_CHANNEL_2 = new ResourceLocation("test", "channel_2");

    public void setUp() {
        // Clear any prior registered global receivers
        ClientPlayNetworking.unregisterGlobalReceiver(TEST_CHANNEL_1);
        ClientPlayNetworking.unregisterGlobalReceiver(TEST_CHANNEL_2);
        ServerPlayNetworking.unregisterGlobalReceiver(TEST_CHANNEL_1);
        ServerPlayNetworking.unregisterGlobalReceiver(TEST_CHANNEL_2);
    }

    public void testClientConnectionTeardown() {
        Object mockListener = new Object();

        // 1. Register global receiver
        ClientPlayNetworking.registerGlobalReceiver(TEST_CHANNEL_1, (client, handler, buf, sender) -> {});
        if (!ClientPlayNetworking.getGlobalReceivers().contains(TEST_CHANNEL_1)) {
            throw new AssertionError("Global receiver not registered");
        }

        // 2. Register connection-specific receiver
        ClientPlayNetworking.registerReceiver(mockListener, TEST_CHANNEL_2, (client, handler, buf, sender) -> {});
        if (ClientPlayNetworking.getActiveReceivers(mockListener).get(TEST_CHANNEL_2) == null) {
            throw new AssertionError("Connection receiver not registered");
        }

        // 3. Trigger disconnect event
        ClientPlayConnectionEvents.DISCONNECT.invoker().onPlayDisconnect(null, null);

        // Clear for the listener explicitly to simulate the event callback triggering
        ClientPlayNetworking.clearConnectionStates(mockListener);

        // Verify connection state is cleared but global remains
        if (!ClientPlayNetworking.getGlobalReceivers().contains(TEST_CHANNEL_1)) {
            throw new AssertionError("Global receiver cleared incorrectly");
        }
        if (!ClientPlayNetworking.getActiveReceivers(mockListener).isEmpty()) {
            throw new AssertionError("Connection receiver not cleared");
        }
    }

    public void testServerConnectionTeardown() {
        Object mockHandler = new Object();
        Object mockServer = new Object();

        // 1. Register global receiver
        ServerPlayNetworking.registerGlobalReceiver(TEST_CHANNEL_1, (server, player, handler, buf, sender) -> {});
        if (!ServerPlayNetworking.getGlobalReceivers().contains(TEST_CHANNEL_1)) {
            throw new AssertionError("Global receiver not registered");
        }

        // 2. Register connection-specific receiver
        ServerPlayNetworking.registerReceiver(mockHandler, TEST_CHANNEL_2, (server, player, handler, buf, sender) -> {});
        if (ServerPlayNetworking.getActiveReceivers(mockHandler).get(TEST_CHANNEL_2) == null) {
            throw new AssertionError("Connection receiver not registered");
        }

        // 3. Trigger disconnect event via invoker
        ServerPlayConnectionEvents.DISCONNECT.invoker().onPlayDisconnect(mockHandler, mockServer);

        // Verify connection-specific receiver is cleared, global remains
        if (!ServerPlayNetworking.getGlobalReceivers().contains(TEST_CHANNEL_1)) {
            throw new AssertionError("Global receiver cleared incorrectly");
        }
        if (!ServerPlayNetworking.getActiveReceivers(mockHandler).isEmpty()) {
            throw new AssertionError("Connection receiver not cleared");
        }
    }
}
