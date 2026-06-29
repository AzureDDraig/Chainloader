package net.minecraft.client.gui.components;

import net.minecraft.network.chat.Component;

public class Button extends AbstractWidget {
    public interface OnPress {
        void onPress(Button button);
    }

    public static Builder builder(Component message, OnPress onPress) {
        return new Builder(message, onPress);
    }

    public static class Builder {
        private final Component message;
        private final OnPress onPress;
        private int x, y, width, height;

        public Builder(Component message, OnPress onPress) {
            this.message = message;
            this.onPress = onPress;
        }

        public Builder bounds(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            return this;
        }

        public Button build() {
            return new Button(message, onPress, x, y, width, height);
        }
    }

    private final Component message;
    private final OnPress onPress;

    protected Button(Component message, OnPress onPress, int x, int y, int width, int height) {
        this.message = message;
        this.onPress = onPress;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }
}
