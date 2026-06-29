package net.chainloader.loader.core.gui;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.GuiGraphics;

/**
 * A custom video settings screen modeled after Sodium's tabbed layout.
 * Links configuration selections directly back to Minecraft's OptionInstance options.
 */
public class SodiumVideoSettingsScreen extends Screen {
    private final Screen parent;
    private final Options options;
    private int currentTab = 0; // 0 = General, 1 = Quality, 2 = Performance, 3 = Advanced

    public SodiumVideoSettingsScreen(Screen parent, Options options) {
        super(Component.literal("Video Settings"));
        this.parent = parent;
        this.options = options;
    }

    @Override
    public void init() {
        //Done / Apply Button
        Button doneButton = Button.builder(Component.literal("Apply & Done"), button -> {
            Minecraft.getInstance().setScreen(parent);
        }).bounds(width / 2 - 100, height - 30, 200, 20).build();
        addRenderableWidget(doneButton);
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // Do nothing so that super.render doesn't render the blur on top of our text.
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // Render standard background (dirt/blur) first
        super.renderBackground(guiGraphics, mouseX, mouseY, partialTick);

        // Draw deep dark background overlay
        guiGraphics.fill(0, 0, width, height, 0xDF101015);

        // Render Title
        guiGraphics.drawString(font, "Video Settings", 20, 15, 0x8A2BE2, false);

        // Draw Tabs
        String[] tabs = {"General", "Quality", "Performance", "Advanced"};
        int tabX = 20;
        for (int i = 0; i < tabs.length; i++) {
            int color = (i == currentTab) ? 0x8A2BE2 : 0x888888;
            guiGraphics.drawString(font, tabs[i], tabX, 40, color, false);
            if (i == currentTab) {
                // Underline selected tab
                guiGraphics.fill(tabX, 51, tabX + fontWidth(tabs[i]), 52, 0x8A2BE2);
            }
            tabX += 80;
        }

        // Draw a separator line below tabs
        guiGraphics.fill(20, 53, width - 20, 54, 0x22FFFFFF);

        // Render Tab content options
        int contentY = 70;
        if (currentTab == 0) {
            // General Tab options
            renderOption(guiGraphics, "Render Distance", options.renderDistance.get() + " chunks", 20, contentY, mouseX, mouseY);
            renderOption(guiGraphics, "Simulation Distance", options.simulationDistance.get() + " chunks", 20, contentY + 25, mouseX, mouseY);
            renderOption(guiGraphics, "View Bobbing", options.bobView.get() ? "ON" : "OFF", 20, contentY + 50, mouseX, mouseY);
        } else if (currentTab == 1) {
            // Quality Tab
            renderOption(guiGraphics, "Graphics Quality", "FANCY", 20, contentY, mouseX, mouseY);
            renderOption(guiGraphics, "Clouds", "FAST", 20, contentY + 25, mouseX, mouseY);
            renderOption(guiGraphics, "Smooth Lighting", "ON", 20, contentY + 50, mouseX, mouseY);
        } else if (currentTab == 2) {
            // Performance Tab
            renderOption(guiGraphics, "Max Framerate", options.framerateLimit.get() + " fps", 20, contentY, mouseX, mouseY);
            renderOption(guiGraphics, "Chunk Builder Threads", "4", 20, contentY + 25, mouseX, mouseY);
            renderOption(guiGraphics, "Always Upload Chunks", "ON", 20, contentY + 50, mouseX, mouseY);
        } else if (currentTab == 3) {
            // Advanced Tab
            renderOption(guiGraphics, "Use Block Face Culling", "ON", 20, contentY, mouseX, mouseY);
            renderOption(guiGraphics, "Use Compact Vertex Format", "ON", 20, contentY + 25, mouseX, mouseY);
            renderOption(guiGraphics, "Debug Options", "OFF", 20, contentY + 50, mouseX, mouseY);
        }

        // Let standard rendering draw screen widgets (buttons)
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    private void renderOption(GuiGraphics guiGraphics, String name, String value, int x, int y, int mouseX, int mouseY) {
        boolean hovered = mouseX >= x && mouseX <= x + 300 && mouseY >= y - 2 && mouseY < y + 16;
        int bgColor = hovered ? 0x22FFFFFF : 0x11FFFFFF;
        guiGraphics.fill(x, y - 2, x + 300, y + 16, bgColor);
        
        guiGraphics.drawString(font, name, x + 10, y + 3, 0xCCCCCC, false);
        guiGraphics.drawString(font, value, x + 200, y + 3, 0x8A2BE2, false);
    }

    private int fontWidth(String text) {
        return text != null ? text.length() * 6 : 0;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Tab switching
        int tabX = 20;
        for (int i = 0; i < 4; i++) {
            String tabName = i == 0 ? "General" : i == 1 ? "Quality" : i == 2 ? "Performance" : "Advanced";
            int width = fontWidth(tabName);
            if (mouseX >= tabX && mouseX <= tabX + width && mouseY >= 35 && mouseY <= 52) {
                currentTab = i;
                return true;
            }
            tabX += 80;
        }

        // Option interactions (toggling values)
        int contentY = 70;
        if (currentTab == 0) {
            if (isOptionHovered(20, contentY, mouseX, mouseY)) {
                int val = options.renderDistance.get();
                if (val == 8) options.renderDistance.set(12);
                else if (val == 12) options.renderDistance.set(16);
                else if (val == 16) options.renderDistance.set(24);
                else if (val == 24) options.renderDistance.set(32);
                else options.renderDistance.set(8);
                return true;
            }
            if (isOptionHovered(20, contentY + 25, mouseX, mouseY)) {
                int val = options.simulationDistance.get();
                if (val == 8) options.simulationDistance.set(10);
                else if (val == 10) options.simulationDistance.set(12);
                else options.simulationDistance.set(8);
                return true;
            }
            if (isOptionHovered(20, contentY + 50, mouseX, mouseY)) {
                options.bobView.set(!options.bobView.get());
                return true;
            }
        } else if (currentTab == 2) {
            if (isOptionHovered(20, contentY, mouseX, mouseY)) {
                int val = options.framerateLimit.get();
                if (val == 60) options.framerateLimit.set(120);
                else if (val == 120) options.framerateLimit.set(250);
                else options.framerateLimit.set(60);
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean isOptionHovered(int x, int y, double mouseX, double mouseY) {
        return mouseX >= x && mouseX <= x + 300 && mouseY >= y - 2 && mouseY < y + 16;
    }
}
