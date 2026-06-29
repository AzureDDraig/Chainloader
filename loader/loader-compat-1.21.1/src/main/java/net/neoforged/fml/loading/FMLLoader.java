package net.neoforged.fml.loading;

import net.neoforged.api.distmarker.Dist;

public class FMLLoader {
    public static boolean isProduction() {
        return true;
    }

    public static Dist getDist() {
        return Dist.CLIENT;
    }
}
