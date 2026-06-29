package net.minecraft.network.chat;

/**
 * Stub interface for Minecraft's Mojang Component interface to allow compilation of shims.
 */
public interface Component {

    static MutableComponent literal(String text) {
        return new Literal(text);
    }

    static MutableComponent translatable(String key) {
        return new Translatable(key);
    }

    String getString();

    class Literal implements MutableComponent {
        private final String text;

        public Literal(String text) {
            this.text = text;
        }

        @Override
        public String getString() {
            return text;
        }

        @Override
        public String toString() {
            return text;
        }
    }

    class Translatable implements MutableComponent {
        private final String key;

        public Translatable(String key) {
            this.key = key;
        }

        @Override
        public String getString() {
            return key;
        }

        @Override
        public String toString() {
            return key;
        }
    }
}
