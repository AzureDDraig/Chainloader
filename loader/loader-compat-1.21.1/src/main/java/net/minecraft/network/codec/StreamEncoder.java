package net.minecraft.network.codec;

@FunctionalInterface
public interface StreamEncoder<B, V> {
    void encode(B buf, V value);
}
