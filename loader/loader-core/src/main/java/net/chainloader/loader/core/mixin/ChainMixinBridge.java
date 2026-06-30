package net.chainloader.loader.core.mixin;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.List;

public class ChainMixinBridge {
    private static final List<String> dynamicMixinConfigs = new CopyOnWriteArrayList<>();

    public static void registerDynamicMixinConfig(String configJsonPath) {
        dynamicMixinConfigs.add(configJsonPath);
    }

    public static List<String> getDynamicMixinConfigs() {
        return dynamicMixinConfigs;
    }
}
