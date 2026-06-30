package net.minecraftforge.client.event;

import net.minecraftforge.eventbus.api.Event;
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

    public static class InitScreenEvent extends ScreenEvent {
        public InitScreenEvent(Screen screen) {
            super(screen);
        }

        public static class Pre extends InitScreenEvent {
            public Pre(Screen screen) {
                super(screen);
            }
        }

        public static class Post extends InitScreenEvent {
            public Post(Screen screen) {
                super(screen);
            }
        }
    }

    public static class Render extends ScreenEvent {
        private final GuiGraphics guiGraphics;
        private final com.mojang.blaze3d.vertex.PoseStack poseStack;
        private final int mouseX;
        private final int mouseY;
        private final float partialTick;

        public Render(Screen screen, GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
            super(screen);
            this.guiGraphics = guiGraphics;
            this.poseStack = guiGraphics != null ? guiGraphics.pose() : null;
            this.mouseX = mouseX;
            this.mouseY = mouseY;
            this.partialTick = partialTick;
        }

        public Render(Screen screen, com.mojang.blaze3d.vertex.PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
            super(screen);
            this.guiGraphics = null;
            this.poseStack = poseStack;
            this.mouseX = mouseX;
            this.mouseY = mouseY;
            this.partialTick = partialTick;
        }

        public GuiGraphics getGuiGraphics() { return guiGraphics; }
        public com.mojang.blaze3d.vertex.PoseStack getPoseStack() { return poseStack; }
        public int getMouseX() { return mouseX; }
        public int getMouseY() { return mouseY; }
        public float getPartialTick() { return partialTick; }

        public static class Pre extends Render {
            public Pre(Screen screen, GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
                super(screen, guiGraphics, mouseX, mouseY, partialTick);
            }
            public Pre(Screen screen, com.mojang.blaze3d.vertex.PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
                super(screen, poseStack, mouseX, mouseY, partialTick);
            }
        }

        public static class Post extends Render {
            public Post(Screen screen, GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
                super(screen, guiGraphics, mouseX, mouseY, partialTick);
            }
            public Post(Screen screen, com.mojang.blaze3d.vertex.PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
                super(screen, poseStack, mouseX, mouseY, partialTick);
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
}
