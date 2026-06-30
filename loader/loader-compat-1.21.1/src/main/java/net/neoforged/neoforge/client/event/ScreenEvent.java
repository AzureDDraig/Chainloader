package net.neoforged.neoforge.client.event;

import net.neoforged.bus.api.Event;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.GuiGraphics;

public class ScreenEvent extends Event {
    private final Screen screen;

    public ScreenEvent(Screen screen) {
        this.screen = screen;
    }

    public Screen getScreen() {
        return screen;
    }

    public static class Init extends ScreenEvent {
        public Init(Screen screen) {
            super(screen);
        }

        public static class Pre extends Init {
            public Pre(Screen screen) {
                super(screen);
            }
        }

        public static class Post extends Init {
            public Post(Screen screen) {
                super(screen);
            }
        }
    }

    public static class Render extends ScreenEvent {
        private final GuiGraphics guiGraphics;
        private final int mouseX;
        private final int mouseY;
        private final float partialTick;

        public Render(Screen screen, GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
            super(screen);
            this.guiGraphics = guiGraphics;
            this.mouseX = mouseX;
            this.mouseY = mouseY;
            this.partialTick = partialTick;
        }

        public GuiGraphics getGuiGraphics() { return guiGraphics; }
        public int getMouseX() { return mouseX; }
        public int getMouseY() { return mouseY; }
        public float getPartialTick() { return partialTick; }

        public static class Pre extends Render {
            public Pre(Screen screen, GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
                super(screen, guiGraphics, mouseX, mouseY, partialTick);
            }
        }

        public static class Post extends Render {
            public Post(Screen screen, GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
                super(screen, guiGraphics, mouseX, mouseY, partialTick);
            }
        }
    }

    public static class MouseButtonPressed extends ScreenEvent {
        private final double mouseX;
        private final double mouseY;
        private final int button;

        public MouseButtonPressed(Screen screen, double mouseX, double mouseY, int button) {
            super(screen);
            this.mouseX = mouseX;
            this.mouseY = mouseY;
            this.button = button;
        }

        public double getMouseX() { return mouseX; }
        public double getMouseY() { return mouseY; }
        public int getButton() { return button; }

        public static class Pre extends MouseButtonPressed {
            public Pre(Screen screen, double mouseX, double mouseY, int button) {
                super(screen, mouseX, mouseY, button);
            }
            @Override
            public boolean isCancelable() { return true; }
        }

        public static class Post extends MouseButtonPressed {
            public Post(Screen screen, double mouseX, double mouseY, int button) {
                super(screen, mouseX, mouseY, button);
            }
        }
    }

    public static class KeyPressed extends ScreenEvent {
        private final int keyCode;
        private final int scanCode;
        private final int modifiers;

        public KeyPressed(Screen screen, int keyCode, int scanCode, int modifiers) {
            super(screen);
            this.keyCode = keyCode;
            this.scanCode = scanCode;
            this.modifiers = modifiers;
        }

        public int getKeyCode() { return keyCode; }
        public int getScanCode() { return scanCode; }
        public int getModifiers() { return modifiers; }

        public static class Pre extends KeyPressed {
            public Pre(Screen screen, int keyCode, int scanCode, int modifiers) {
                super(screen, keyCode, scanCode, modifiers);
            }
            @Override
            public boolean isCancelable() { return true; }
        }

        public static class Post extends KeyPressed {
            public Post(Screen screen, int keyCode, int scanCode, int modifiers) {
                super(screen, keyCode, scanCode, modifiers);
            }
        }
    }

    public static class KeyReleased extends ScreenEvent {
        private final int keyCode;
        private final int scanCode;
        private final int modifiers;

        public KeyReleased(Screen screen, int keyCode, int scanCode, int modifiers) {
            super(screen);
            this.keyCode = keyCode;
            this.scanCode = scanCode;
            this.modifiers = modifiers;
        }

        public int getKeyCode() { return keyCode; }
        public int getScanCode() { return scanCode; }
        public int getModifiers() { return modifiers; }

        public static class Pre extends KeyReleased {
            public Pre(Screen screen, int keyCode, int scanCode, int modifiers) {
                super(screen, keyCode, scanCode, modifiers);
            }
            @Override
            public boolean isCancelable() { return true; }
        }

        public static class Post extends KeyReleased {
            public Post(Screen screen, int keyCode, int scanCode, int modifiers) {
                super(screen, keyCode, scanCode, modifiers);
            }
        }
    }

    public static class Opening extends ScreenEvent {
        private final Screen currentScreen;
        private Screen newScreen;

        public Opening(Screen currentScreen, Screen newScreen) {
            super(newScreen);
            this.currentScreen = currentScreen;
            this.newScreen = newScreen;
        }

        public Screen getCurrentScreen() {
            return currentScreen;
        }

        public Screen getNewScreen() {
            return newScreen;
        }

        public void setNewScreen(Screen newScreen) {
            this.newScreen = newScreen;
        }

        @Override
        public boolean isCancelable() {
            return true;
        }
    }
}

