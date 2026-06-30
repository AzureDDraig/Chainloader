package net.minecraft.network.codec;

@FunctionalInterface
public interface StreamDecoder<B, V> {
    V decode(B buf);
}
