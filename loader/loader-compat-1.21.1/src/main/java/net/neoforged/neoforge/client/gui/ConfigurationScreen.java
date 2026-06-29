package net.neoforged.neoforge.client.gui;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class ConfigurationScreen extends Screen {
    public ConfigurationScreen(net.neoforged.fml.ModContainer container, Screen parent) {
        super(null);
    }

    protected ConfigurationScreen(Component title) {
        super(title);
    }
}
