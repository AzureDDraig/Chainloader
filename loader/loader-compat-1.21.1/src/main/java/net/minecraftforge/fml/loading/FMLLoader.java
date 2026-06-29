package net.minecraftforge.fml.loading;

import net.minecraftforge.api.distmarker.Dist;

public class FMLLoader {
    public static boolean isProduction() {
        return true;
    }

    public static Dist getDist() {
        return Dist.CLIENT;
    }
}
