package net.chainloader.api.network;

import net.minecraft.network.FriendlyByteBuf;

/**
 * Represents a network packet that can be serialized and sent over a
 * {@link ChainPacketChannel} in ChainLoader.
 * <p>
 * Custom packets must implement this interface to define how their payload
 * data is written to a byte buffer.
 * </p>
 */
public interface ChainPacket {

    /**
     * Serializes this packet's data into the provided {@link FriendlyByteBuf}.
     *
     * @param buf The byte buffer to write data to.
     */
    void write(FriendlyByteBuf buf);

    /**
     * Functional interface for decoding/deserializing a packet from a {@link FriendlyByteBuf}.
     *
     * @param <T> The exact type of the packet being decoded.
     */
    @FunctionalInterface
    interface Decoder<T extends ChainPacket> {
        /**
         * Decodes/reads packet data from the byte buffer and constructs the packet instance.
         *
         * @param buf The byte buffer to read data from.
         * @return The constructed packet instance.
         */
        T decode(FriendlyByteBuf buf);
    }
}
