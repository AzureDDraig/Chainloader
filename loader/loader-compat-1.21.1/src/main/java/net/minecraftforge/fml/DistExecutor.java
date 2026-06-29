package net.minecraftforge.fml;

import java.util.function.Supplier;

public class DistExecutor {
    public static <T> T safeRunForDist(Supplier<Supplier<T>> clientTarget, Supplier<Supplier<T>> serverTarget) {
        return clientTarget.get().get();
    }
    public static void unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist dist, Supplier<Runnable> toRun) {
        if (dist == net.minecraftforge.api.distmarker.Dist.CLIENT) {
            toRun.get().run();
        }
    }
}
