package net.chainloader.loader.core.gui;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.chainloader.loader.core.ModScanner;
import java.util.ArrayList;
import java.util.List;

/**
 * Screen that displays active and installed mods with their metadata details.
 */
public class ModListScreen extends Screen {
    private final Screen parent;
    private final List<ModEntry> mods = new ArrayList<>();
    private int selectedIndex = 0;

    public ModListScreen(Screen parent) {
        super(Component.literal("Mods"));
        this.parent = parent;
        
        // Populate mods list dynamically from ModScanner
        for (ModScanner.DiscoveredMod mod : ModScanner.getDiscoveredMods()) {
            String authorList = String.join(", ", mod.metadata.getAuthors());
            
            // 1. Resolve target MC version
            String mcVersion = "Any";
            for (net.chainloader.loader.core.ChainModMetadata.ModDependency dep : mod.metadata.getDependencies()) {
                if ("minecraft".equals(dep.getModId())) {
                    mcVersion = dep.getVersionRequirement();
                    break;
                }
            }
            if ("Any".equals(mcVersion) && mod.jarFile != null) {
                String filename = mod.jarFile.getName();
                java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("(?i)(?:mc)?(1\\.\\d{2}(?:\\.\\d+)?)");
                java.util.regex.Matcher matcher = pattern.matcher(filename);
                if (matcher.find()) {
                    mcVersion = matcher.group(1);
                }
            }
            
            // 2. Resolve loader type and version
            String loaderVersion = "";
            for (net.chainloader.loader.core.ChainModMetadata.ModDependency dep : mod.metadata.getDependencies()) {
                String depId = dep.getModId();
                if ("fabricloader".equals(depId) || "forge".equals(depId) || "neoforge".equals(depId)) {
                    loaderVersion = dep.getVersionRequirement();
                    break;
                }
            }
            
            String loaderName = mod.metadata.getOriginalLoaderType();
            if (loaderName != null && loaderName.length() > 0) {
                if (loaderName.equalsIgnoreCase("neoforge")) {
                    loaderName = "NeoForge";
                } else if (loaderName.equalsIgnoreCase("chainloader")) {
                    loaderName = "ChainLoader";
                } else if (loaderName.equalsIgnoreCase("fabric")) {
                    loaderName = "Fabric";
                } else if (loaderName.equalsIgnoreCase("forge")) {
                    loaderName = "Forge";
                } else {
                    loaderName = loaderName.substring(0, 1).toUpperCase() + loaderName.substring(1);
                }
            } else {
                loaderName = "Unknown";
            }
            
            if (!loaderVersion.isEmpty() && !"*".equals(loaderVersion)) {
                loaderName = loaderName + " (" + loaderVersion + ")";
            }
            
            mods.add(new ModEntry(
                mod.metadata.getId(),
                (mod.metadata.getName() != null && !mod.metadata.getName().isEmpty()) ? mod.metadata.getName() : mod.metadata.getId(),
                mod.metadata.getVersion(),
                authorList.isEmpty() ? "Unknown" : authorList,
                (mod.metadata.getLicense() != null && !mod.metadata.getLicense().isEmpty()) ? mod.metadata.getLicense() : "Unknown",
                mod.metadata.getDescription() != null ? mod.metadata.getDescription() : "",
                mcVersion,
                loaderName
            ));
        }
    }

    @Override
    public void init() {
        // Add "Done" button to return to the previous screen
        Button doneButton = Button.builder(Component.literal("Done"), button -> {
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
        guiGraphics.drawString(font, "Installed Mods", 20, 20, 0x8A2BE2, false);

        // Render mod list on the left side (x: 20 to 180)
        int listY = 45;
        for (int i = 0; i < mods.size(); i++) {
            ModEntry mod = mods.get(i);
            int color = (i == selectedIndex) ? 0x8A2BE2 : 0xAAAAAA;
            guiGraphics.drawString(font, mod.name, 30, listY, color, false);
            
            if (i == selectedIndex) {
                guiGraphics.drawString(font, ">", 20, listY, 0x8A2BE2, false);
            }
            listY += 15;
        }

        // Render selected mod details on the right side (x: 200 to width - 20)
        if (selectedIndex >= 0 && selectedIndex < mods.size()) {
            ModEntry mod = mods.get(selectedIndex);
            int detailsX = 200;
            
            // Draw a subtle vertical separator line
            guiGraphics.fill(detailsX - 10, 40, detailsX - 9, height - 45, 0x33FFFFFF);

            guiGraphics.drawString(font, mod.name, detailsX, 45, 0xFFFFFF, false);
            guiGraphics.drawString(font, "Version: " + mod.version, detailsX, 60, 0x888888, false);
            guiGraphics.drawString(font, "Built for Loader: " + mod.loader, detailsX, 72, 0x888888, false);
            guiGraphics.drawString(font, "Built for Minecraft: " + mod.mcVersion, detailsX, 84, 0x888888, false);
            guiGraphics.drawString(font, "Author: " + mod.author, detailsX, 96, 0x888888, false);
            guiGraphics.drawString(font, "License: " + mod.license, detailsX, 108, 0x888888, false);
            
            guiGraphics.drawString(font, "Description:", detailsX, 125, 0xAAAAAA, false);
            List<String> wrapped = wrapText(mod.description, 45);
            int descY = 137;
            for (String descLine : wrapped) {
                guiGraphics.drawString(font, descLine, detailsX, descY, 0xCCCCCC, false);
                descY += 12;
            }
        }

        // Let standard rendering draw screen widgets (buttons)
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Handle selecting mods in the scroll list
        int listY = 45;
        for (int i = 0; i < mods.size(); i++) {
            if (mouseX >= 20 && mouseX <= 180 && mouseY >= listY && mouseY < listY + 15) {
                selectedIndex = i;
                return true;
            }
            listY += 15;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private List<String> wrapText(String text, int maxChars) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return lines;
        }
        String[] words = text.split(" ");
        StringBuilder currentLine = new StringBuilder();
        for (String word : words) {
            if (currentLine.length() + word.length() + 1 > maxChars) {
                lines.add(currentLine.toString());
                currentLine = new StringBuilder(word);
            } else {
                if (currentLine.length() > 0) {
                    currentLine.append(" ");
                }
                currentLine.append(word);
            }
        }
        if (currentLine.length() > 0) {
            lines.add(currentLine.toString());
        }
        return lines;
    }

    private static class ModEntry {
        final String id;
        final String name;
        final String version;
        final String author;
        final String license;
        final String description;
        final String mcVersion;
        final String loader;

        ModEntry(String id, String name, String version, String author, String license, String description, String mcVersion, String loader) {
            this.id = id;
            this.name = name;
            this.version = version;
            this.author = author;
            this.license = license;
            this.description = description;
            this.mcVersion = mcVersion;
            this.loader = loader;
        }
    }
}
