package net.minecraft.world.level.biome;

public class Biome {
    public boolean hasPrecipitation() {
        return false;
    }

    public float getBaseTemperature() {
        return 0.0f;
    }

    public enum Precipitation {
        NONE,
        RAIN,
        SNOW
    }
}
