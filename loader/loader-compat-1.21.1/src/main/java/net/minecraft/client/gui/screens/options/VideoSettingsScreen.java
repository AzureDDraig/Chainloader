package net.minecraft.client.gui.screens.options;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.Options;

public class VideoSettingsScreen extends Screen {
    public final Screen parent;
    public final Options options;

    public VideoSettingsScreen(Screen parent, Options options) {
        super(null);
        this.parent = parent;
        this.options = options;
    }
}
