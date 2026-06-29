package net.chainloader.api.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Represents a logical network channel in ChainLoader for registering, sending, and receiving custom packets.
 * <p>
 * This class abstracts the differences between Fabric's PlayNetworking and Forge/NeoForge's SimpleChannel.
 * Each channel is identified by a unique {@link ResourceLocation}.
 * </p>
 * 
 * <h3>Usage Example:</h3>
 * <pre>{@code
 * // 1. Create/Retrieve a channel
 * ResourceLocation channelId = new ResourceLocation("mymod", "main_channel");
 * ChainPacketChannel channel = ChainPacketChannel.create(channelId);
 * 
 * // 2. Register packet types dynamically with discriminators
 * channel.registerPacket(1, MyCustomPacket.class, MyCustomPacket::new);
 * 
 * // 3. Register client and server listeners
 * channel.registerServerListener(MyCustomPacket.class, (packet, context) -> {
 *     context.queue(() -> {
 *         // Executed on the main server thread
 *         ServerPlayerEntity sender = context.getServerPlayer();
 *         System.out.println("Received from " + sender.getName().getString() + ": " + packet.getMessage());
 *     });
 * });
 * 
 * channel.registerClientListener(MyCustomPacket.class, (packet, context) -> {
 *     context.queue(() -> {
 *         // Executed on the main client thread
 *         System.out.println("Received from server: " + packet.getMessage());
 *     });
 * });
 * 
 * // 4. Send packets
 * // Client -> Server
 * channel.sendToServer(new MyCustomPacket("Hello Server!"));
 * 
 * // Server -> Client
 * channel.sendToPlayer(playerEntity, new MyCustomPacket("Hello Player!"));
 * }</pre>
 */
public class ChainPacketChannel {

    private static final Map<ResourceLocation, ChainPacketChannel> CHANNELS = new ConcurrentHashMap<>();
    private static final INetworkBackend DEFAULT_BACKEND = NetworkBackendFactory.get();

    private final ResourceLocation channelId;
    private final Map<Class<? extends ChainPacket>, PacketInfo<?>> packetRegistry = new ConcurrentHashMap<>();
    private final Map<Integer, Class<? extends ChainPacket>> discriminatorToClass = new ConcurrentHashMap<>();
    private final Map<Class<? extends ChainPacket>, Integer> classToDiscriminator = new ConcurrentHashMap<>();

    private ChainPacketChannel(ResourceLocation channelId) {
        this.channelId = Objects.requireNonNull(channelId, "Channel ID cannot be null");
        // Register this channel to the active network backend
        NetworkBackendFactory.get().registerChannel(this);
    }

    /**
     * Gets or creates a networking channel.
     *
     * @param channelId The unique identifier of the channel.
     * @return The channel instance.
     */
    public static ChainPacketChannel create(ResourceLocation channelId) {
        return CHANNELS.computeIfAbsent(channelId, ChainPacketChannel::new);
    }

    /**
     * Dynamically registers a packet type to this channel.
     *
     * @param discriminator A unique identifier (byte/int) for this packet type within this channel.
     * @param packetClass   The class of the packet.
     * @param decoder       The decoder used to deserialize the packet from raw bytes.
     * @param <T>           The packet type.
     */
    public <T extends ChainPacket> void registerPacket(
            int discriminator,
            Class<T> packetClass,
            ChainPacket.Decoder<T> decoder
    ) {
        Objects.requireNonNull(packetClass, "Packet class cannot be null");
        Objects.requireNonNull(decoder, "Decoder cannot be null");

        if (discriminatorToClass.containsKey(discriminator)) {
            throw new IllegalArgumentException("Discriminator " + discriminator + " is already registered on channel " + channelId);
        }
        if (classToDiscriminator.containsKey(packetClass)) {
            throw new IllegalArgumentException("Packet class " + packetClass.getName() + " is already registered on channel " + channelId);
        }

        discriminatorToClass.put(discriminator, packetClass);
        classToDiscriminator.put(packetClass, discriminator);
        packetRegistry.put(packetClass, new PacketInfo<>(packetClass, decoder));
    }

    /**
     * Registers a client-side listener for a specific packet type.
     *
     * @param packetClass The class of the packet.
     * @param listener    The listener instance.
     * @param <T>         The packet type.
     */
    @SuppressWarnings("unchecked")
    public <T extends ChainPacket> void registerClientListener(
            Class<T> packetClass,
            PacketListener<T> listener
    ) {
        PacketInfo<T> info = (PacketInfo<T>) packetRegistry.get(packetClass);
        if (info == null) {
            throw new IllegalStateException("Packet type " + packetClass.getName() + " must be registered before adding listeners");
        }
        info.addClientListener(listener);
    }

    /**
     * Registers a server-side listener for a specific packet type.
     *
     * @param packetClass The class of the packet.
     * @param listener    The listener instance.
     * @param <T>         The packet type.
     */
    @SuppressWarnings("unchecked")
    public <T extends ChainPacket> void registerServerListener(
            Class<T> packetClass,
            PacketListener<T> listener
    ) {
        PacketInfo<T> info = (PacketInfo<T>) packetRegistry.get(packetClass);
        if (info == null) {
            throw new IllegalStateException("Packet type " + packetClass.getName() + " must be registered before adding listeners");
        }
        info.addServerListener(listener);
    }

    /**
     * Sends a packet from the client to the server.
     *
     * @param packet The packet to send.
     */
    public void sendToServer(ChainPacket packet) {
        NetworkBackendFactory.get().sendToServer(this, packet);
    }

    /**
     * Sends a packet from the server to a specific player.
     *
     * @param player The recipient player.
     * @param packet The packet to send.
     */
    public void sendToPlayer(ServerPlayerEntity player, ChainPacket packet) {
        NetworkBackendFactory.get().sendToPlayer(this, player, packet);
    }

    /**
     * Sends a packet from the server to all players currently online.
     *
     * @param packet The packet to send.
     */
    public void sendToAll(ChainPacket packet) {
        NetworkBackendFactory.get().sendToAll(this, packet);
    }

    /**
     * Encodes a packet into a buffer, prefixing it with its registered discriminator.
     *
     * @param packet The packet to encode.
     * @param buf    The target byte buffer.
     */
    public void encode(ChainPacket packet, FriendlyByteBuf buf) {
        Integer discriminator = classToDiscriminator.get(packet.getClass());
        if (discriminator == null) {
            throw new IllegalArgumentException("Packet type " + packet.getClass().getName() + " is not registered on channel " + channelId);
        }
        buf.writeVarInt(discriminator);
        packet.write(buf);
    }

    /**
     * Decodes and dispatches an incoming packet to registered listeners.
     * Primarily called internally by the platform-specific backend when receiving network data.
     *
     * @param buf     The received buffer.
     * @param context The packet handling context.
     */
    public void handleIncoming(FriendlyByteBuf buf, PacketContext context) {
        if (!buf.isReadable()) {
            throw new IllegalStateException("Received empty network packet on channel " + channelId);
        }

        int discriminator = buf.readVarInt();
        Class<? extends ChainPacket> packetClass = discriminatorToClass.get(discriminator);
        if (packetClass == null) {
            throw new IllegalArgumentException("Unknown packet discriminator " + discriminator + " on channel " + channelId);
        }

        PacketInfo<?> info = packetRegistry.get(packetClass);
        if (info == null) {
            throw new IllegalStateException("No registration info for packet class: " + packetClass.getName());
        }

        ChainPacket packet = info.decoder().decode(buf);
        if (context.getSide() == PacketContext.EnvSide.CLIENT) {
            info.dispatchClient(packet, context);
        } else {
            info.dispatchServer(packet, context);
        }
    }

    /**
     * Gets the unique channel ID.
     *
     * @return The channel resource location.
     */
    public ResourceLocation getChannelId() {
        return channelId;
    }

    // --- Inner Interfaces & Helper Classes ---

    /**
     * Handles incoming packets of a specific type.
     *
     * @param <T> The packet type.
     */
    @FunctionalInterface
    public interface PacketListener<T extends ChainPacket> {
        /**
         * Invoked when a packet is received.
         *
         * @param packet  The received packet.
         * @param context The packet context containing information about the sender and threads.
         */
        void handle(T packet, PacketContext context);
    }

    /**
     * Context provided when handling a packet, allowing operations such as threading synchronization
     * and retrieval of the sender/receiver player.
     */
    public interface PacketContext {
        /**
         * Gets the player associated with this networking packet.
         * For C2S packets (Client to Server), this is the client player who sent it.
         * For S2C packets (Server to Client), this is the local client player.
         *
         * @return The player entity.
         */
        Object getPlayer();

        /**
         * Convenience method to retrieve the server player if this packet was received on the server.
         *
         * @return The server player entity, or {@code null} if handled on the client.
         */
        default ServerPlayerEntity getServerPlayer() {
            Object player = getPlayer();
            return player instanceof ServerPlayerEntity ? (ServerPlayerEntity) player : null;
        }

        /**
         * Gets the logical side (CLIENT or SERVER) receiving this packet.
         *
         * @return The receipt side.
         */
        EnvSide getSide();

        /**
         * Schedules a task to be executed on the main game thread.
         * Since netty processes network packets on background threads, any updates to the world
         * or entities must be run on the main game thread.
         *
         * @param task The task to execute.
         */
        void queue(Runnable task);

        enum EnvSide {
            CLIENT,
            SERVER
        }
    }

    /**
     * Internal data structure to map packet types to decoders and active listeners.
     */
    private static class PacketInfo<T extends ChainPacket> {
        private final Class<T> packetClass;
        private final ChainPacket.Decoder<T> decoder;
        private final List<PacketListener<T>> clientListeners = new CopyOnWriteArrayList<>();
        private final List<PacketListener<T>> serverListeners = new CopyOnWriteArrayList<>();

        public PacketInfo(Class<T> packetClass, ChainPacket.Decoder<T> decoder) {
            this.packetClass = packetClass;
            this.decoder = decoder;
        }

        public ChainPacket.Decoder<T> decoder() {
            return decoder;
        }

        public void addClientListener(PacketListener<T> listener) {
            clientListeners.add(listener);
        }

        public void addServerListener(PacketListener<T> listener) {
            serverListeners.add(listener);
        }

        @SuppressWarnings("unchecked")
        public void dispatchClient(ChainPacket packet, PacketContext context) {
            for (PacketListener<T> listener : clientListeners) {
                listener.handle((T) packet, context);
            }
        }

        @SuppressWarnings("unchecked")
        public void dispatchServer(ChainPacket packet, PacketContext context) {
            for (PacketListener<T> listener : serverListeners) {
                listener.handle((T) packet, context);
            }
        }
    }

    // --- Platform Bridging & Backend Factory ---

    /**
     * Platform-specific network implementation backend.
     * Implementing classes (provided by Forge, Fabric, or NeoForge loaders) bridge this channel
     * and its packet serialization/deserialization to standard platform APIs.
     */
    public interface INetworkBackend {
        void registerChannel(ChainPacketChannel channel);
        void sendToServer(ChainPacketChannel channel, ChainPacket packet);
        void sendToPlayer(ChainPacketChannel channel, ServerPlayerEntity player, ChainPacket packet);
        void sendToAll(ChainPacketChannel channel, ChainPacket packet);
    }

    /**
     * Factory class managing the active platform-specific network backend provider.
     */
    public static class NetworkBackendFactory {
        private static INetworkBackend provider;

        /**
         * Sets the network backend provider.
         * Called during mod loader initialization.
         *
         * @param newProvider The network backend implementation.
         */
        public static void registerProvider(INetworkBackend newProvider) {
            provider = newProvider;
        }

        public static INetworkBackend get() {
            if (provider == null) {
                return MockNetworkBackend.INSTANCE;
            }
            return provider;
        }
    }

    /**
     * A mock network backend used when compiling or running in an environment without a loaded platform.
     */
    private static class MockNetworkBackend implements INetworkBackend {
        private static final MockNetworkBackend INSTANCE = new MockNetworkBackend();

        @Override
        public void registerChannel(ChainPacketChannel channel) {
            System.out.println("[ChainLoader-Network] [Mock] Registered network channel: " + channel.getChannelId());
        }

        @Override
        public void sendToServer(ChainPacketChannel channel, ChainPacket packet) {
            System.out.println("[ChainLoader-Network] [Mock Client] Transmitting packet to server: " + packet.getClass().getSimpleName());
        }

        @Override
        public void sendToPlayer(ChainPacketChannel channel, ServerPlayerEntity player, ChainPacket packet) {
            System.out.println("[ChainLoader-Network] [Mock Server] Transmitting packet to player " + player + ": " + packet.getClass().getSimpleName());
        }

        @Override
        public void sendToAll(ChainPacketChannel channel, ChainPacket packet) {
            System.out.println("[ChainLoader-Network] [Mock Server] Broadcasting packet to all players: " + packet.getClass().getSimpleName());
        }
    }
}
