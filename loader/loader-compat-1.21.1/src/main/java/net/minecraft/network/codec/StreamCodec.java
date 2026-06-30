package net.minecraft.network.codec;

public interface StreamCodec<B, V> {
    V decode(B buf);
    void encode(B buf, V value);

    static <B, V> StreamCodec<B, V> of(StreamEncoder<B, V> encoder, StreamDecoder<B, V> decoder) {
        return new StreamCodec<B, V>() {
            @Override
            public V decode(B buf) {
                return decoder.decode(buf);
            }
            @Override
            public void encode(B buf, V value) {
                encoder.encode(buf, value);
            }
        };
    }
}
