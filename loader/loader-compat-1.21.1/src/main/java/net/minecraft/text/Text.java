package net.minecraft.text;

/**
 * Stub interface for Minecraft's Yarn Text component to allow compilation of shims.
 */
public interface Text {
    
    static Text literal(String text) {
        return new Literal(text);
    }

    static Text translatable(String key) {
        return new Translatable(key);
    }

    String getString();

    class Literal implements Text {
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

    class Translatable implements Text {
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
