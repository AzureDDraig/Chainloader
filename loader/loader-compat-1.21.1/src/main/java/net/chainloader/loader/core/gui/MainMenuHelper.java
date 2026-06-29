package net.chainloader.loader.core.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Helper class that intercepts TitleScreen rendering/initialization and Minecraft screen transitions.
 * All public entry points accept {@code Object} parameters so that callers (like Chainlink modules)
 * don't need direct compile-time references to Minecraft classes.
 */
public class MainMenuHelper {

    /**
     * Called at the end of TitleScreen.init() to add the "Mods" button.
     * @param titleScreen an instance of TitleScreen
     */
    public static void onInitTitleScreen(TitleScreen titleScreen) {
        // Close the early loading screen since Minecraft's main menu is now initialized
        net.chainloader.loader.core.gui.EarlyLoadingScreen.getInstance().close();

        int targetY = titleScreen.height / 4 + 48 + 72;
        
        // Shift lower buttons (Options, Quit Game, Accessibility, Language, etc.) down by 24 pixels
        for (Object r : titleScreen.renderables) {
            if (r instanceof AbstractWidget) {
                AbstractWidget w = (AbstractWidget) r;
                if (w.y >= targetY) {
                    w.y += 24;
                }
            }
        }
        
        // Insert "Mods" button where Options and Quit Game originally were
        Button modsButton = Button.builder(Component.literal("Mods"), button -> {
            Minecraft.getInstance().setScreen(new ModListScreen(titleScreen));
        }).bounds(titleScreen.width / 2 - 100, targetY, 200, 20).build();
        
        titleScreen.addRenderableWidget(modsButton);
    }

    /**
     * Called at the end of PauseScreen.init() to add the "Mods" button in-game.
     * @param pauseScreen an instance of PauseScreen
     */
    public static void onInitPauseScreen(PauseScreen pauseScreen) {
        int targetY = pauseScreen.height / 4 + 80;
        
        // Shift lower buttons (Save and Quit to Title, etc.) down by 24 pixels
        for (Object r : pauseScreen.renderables) {
            if (r instanceof AbstractWidget) {
                AbstractWidget w = (AbstractWidget) r;
                if (w.y >= targetY) {
                    w.y += 24;
                }
            }
        }
        
        // Insert "Mods" button (width 204)
        Button modsButton = Button.builder(Component.literal("Mods"), button -> {
            Minecraft.getInstance().setScreen(new ModListScreen(pauseScreen));
        }).bounds(pauseScreen.width / 2 - 102, targetY, 204, 20).build();
        
        pauseScreen.addRenderableWidget(modsButton);
    }

    /**
     * Called at the end of TitleScreen.render() to draw modloader info branding.
     * @param titleScreen an instance of TitleScreen
     * @param guiGraphics an instance of GuiGraphics
     */
    public static void onRenderTitleScreen(TitleScreen titleScreen, GuiGraphics guiGraphics) {
        // Draw 22 pixels from the bottom (above the Minecraft version brand) to prevent overlap
        guiGraphics.drawString(titleScreen.font, "ChainLoader 1.0.0-SNAPSHOT", 2, titleScreen.height - 22, 0xFFFFFF, false);
    }

    /**
     * Intercepts screen changes in Minecraft.setScreen() to redirect vanilla VideoSettingsScreen to our custom SodiumVideoSettingsScreen.
     * @param screen an instance of Screen
     * @return the (possibly intercepted) Screen
     */
    public static Screen interceptSetScreen(Screen screen) {
        if (screen instanceof net.minecraft.client.gui.screens.options.VideoSettingsScreen) {
            net.minecraft.client.gui.screens.options.VideoSettingsScreen vss = (net.minecraft.client.gui.screens.options.VideoSettingsScreen) screen;
            return new SodiumVideoSettingsScreen(vss.parent, vss.options);
        }
        return screen;
    }
}
