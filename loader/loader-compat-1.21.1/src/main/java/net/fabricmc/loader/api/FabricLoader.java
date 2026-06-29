package net.fabricmc.loader.api;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public interface FabricLoader {
    FabricLoader INSTANCE = new FabricLoader() {
        @Override
        public boolean isModLoaded(String id) {
            try {
                Class<?> scannerClass = Class.forName("net.chainloader.loader.core.ModScanner");
                List<?> mods = (List<?>) scannerClass.getMethod("getDiscoveredMods").invoke(null);
                for (Object mod : mods) {
                    Object metadata = mod.getClass().getField("metadata").get(mod);
                    String modId = (String) metadata.getClass().getMethod("getId").invoke(metadata);
                    if (modId.equalsIgnoreCase(id)) {
                        return true;
                    }
                }
            } catch (Exception e) {
                // Ignore and fallback
            }
            return false;
        }

        @Override
        public Path getConfigDir() {
            String gameDir = System.getProperty("chainloader.gameDir", ".");
            return Paths.get(gameDir).resolve("config");
        }

        @Override
        public Path getGameDir() {
            String gameDir = System.getProperty("chainloader.gameDir", ".");
            return Paths.get(gameDir);
        }

        @Override
        public net.fabricmc.api.EnvType getEnvironmentType() {
            try {
                Class<?> launcherClass = Class.forName("net.chainloader.loader.core.ChainLauncher");
                Object side = launcherClass.getMethod("getSide").invoke(null);
                if (side.toString().equalsIgnoreCase("CLIENT")) {
                    return net.fabricmc.api.EnvType.CLIENT;
                }
            } catch (Exception e) {
                // Ignore
            }
            return net.fabricmc.api.EnvType.SERVER;
        }
    };

    static FabricLoader getInstance() {
        return INSTANCE;
    }

    boolean isModLoaded(String id);
    Path getConfigDir();
    Path getGameDir();
    net.fabricmc.api.EnvType getEnvironmentType();
}
