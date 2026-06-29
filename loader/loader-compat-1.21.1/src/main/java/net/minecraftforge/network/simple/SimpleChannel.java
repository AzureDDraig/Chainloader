package net.minecraftforge.network.simple;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Function;
import net.minecraft.network.Connection;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;

public class SimpleChannel {
    private final Map<Class<?>, BiConsumer<?, ?>> handlers = new ConcurrentHashMap<>();

    @SuppressWarnings("unchecked")
    public <MSG> IndexedMessageCodec.MessageHandler<MSG> registerMessage(
        int index, 
        Class<MSG> messageType, 
        BiConsumer<MSG, ?> encoder, 
        Function<?, MSG> decoder, 
        BiConsumer<MSG, ?> messageConsumer
    ) {
        handlers.put(messageType, messageConsumer);
        return new IndexedMessageCodec.MessageHandler<MSG>() {};
    }

    public void sendToServer(Object message) {
        loopback(message);
    }

    public void sendTo(Object message, Connection connection, NetworkDirection direction) {
        loopback(message);
    }

    @SuppressWarnings("unchecked")
    private void loopback(Object message) {
        if (message == null) return;
        BiConsumer handler = handlers.get(message.getClass());
        if (handler != null) {
            java.util.function.Supplier<NetworkEvent.Context> contextSupplier = () -> {
                net.minecraft.world.entity.player.Player player = null;
                try {
                    player = (net.minecraft.world.entity.player.Player) (Object) net.minecraft.client.Minecraft.getInstance().player;
                } catch (Throwable t) {
                    // Ignore
                }
                return new NetworkEvent.Context(player);
            };
            try {
                handler.accept(message, contextSupplier);
            } catch (Throwable t) {
                System.err.println("[SimpleChannel] Error handling loopback message: " + message.getClass().getName());
                t.printStackTrace();
            }
        }
    }
}
