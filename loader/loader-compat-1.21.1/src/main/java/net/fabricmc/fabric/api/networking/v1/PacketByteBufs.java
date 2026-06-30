package net.fabricmc.fabric.api.networking.v1;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;

public final class PacketByteBufs {
    private PacketByteBufs() {}

    public static FriendlyByteBuf create() {
        return new FriendlyByteBuf(Unpooled.buffer());
    }

    public static FriendlyByteBuf empty() {
        return new FriendlyByteBuf(Unpooled.EMPTY_BUFFER);
    }

    public static FriendlyByteBuf copy(ByteBuf buf) {
        return new FriendlyByteBuf(Unpooled.copiedBuffer(buf));
    }

    public static FriendlyByteBuf slice(ByteBuf buf) {
        return new FriendlyByteBuf(buf.slice());
    }

    public static FriendlyByteBuf duplicate(ByteBuf buf) {
        return new FriendlyByteBuf(buf.duplicate());
    }
}
