package net.minecraft.client.resources.model;

import net.minecraft.resources.ResourceLocation;

public class ModelResourceLocation extends ResourceLocation {
    private final String variant;

    public ModelResourceLocation(String namespace, String path, String variant) {
        super(namespace, path);
        this.variant = variant;
    }

    public ModelResourceLocation(String id) {
        super(id);
        this.variant = "inventory";
    }

    public String getVariant() {
        return variant;
    }
}
