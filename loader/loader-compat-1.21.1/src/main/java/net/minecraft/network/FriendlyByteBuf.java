package net.minecraft.network;

import io.netty.buffer.ByteBuf;

public class FriendlyByteBuf {
    public FriendlyByteBuf(ByteBuf parent) {}
    public int readableBytes() { return 0; }
    public FriendlyByteBuf readBytes(byte[] dst) { return null; }
    public FriendlyByteBuf writeByteArray(byte[] array) { return null; }
    public byte[] readByteArray() { return null; }
}
