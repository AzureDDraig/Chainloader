package net.chainloader.loader.compat.bridge;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ChainloaderPayload(ResourceLocation id, byte[] data) implements CustomPacketPayload {
    public static StreamCodec<FriendlyByteBuf, ChainloaderPayload> STREAM_CODEC(ResourceLocation channelId) {
        return StreamCodec.of(
            (buf, payload) -> {
                buf.writeByteArray(payload.data());
            },
            buf -> {
                byte[] bytes = buf.readByteArray();
                return new ChainloaderPayload(channelId, bytes);
            }
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return new Type<>(id);
    }
}
