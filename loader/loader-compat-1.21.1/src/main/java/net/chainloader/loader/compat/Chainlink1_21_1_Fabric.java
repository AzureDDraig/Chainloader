package net.chainloader.loader.compat;

import net.chainloader.loader.core.ChainLauncher;
import net.chainloader.loader.core.ModScanner;
import java.util.ArrayList;
import java.util.List;

public class Chainlink1_21_1_Fabric extends Chainlink1_21_1_Base {

    @Override
    public String getSupportedLoaderType() {
        return "fabric";
    }

    @Override
    public void onWakeUp(ClassLoader classLoader) {
        System.out.println("[Chainlink 1.21.1 Fabric] Initializing main platform module...");
        super.onWakeUp(classLoader);

        // Scan registered mods for backwards compatibility modules matching other Minecraft versions
        List<ModScanner.DiscoveredMod> mods = ModScanner.getDiscoveredMods();
        List<String> targetVersions = new ArrayList<>();

        for (ModScanner.DiscoveredMod mod : mods) {
            String originalLoader = mod.metadata.getOriginalLoaderType();
            if ("fabric".equalsIgnoreCase(originalLoader)) {
                // Get target mc version
                String modVersion = ChainLauncher.getModTargetMinecraftVersion(mod.metadata, "1.21.1");
                if (modVersion != null && !modVersion.isEmpty()) {
                    targetVersions.add(modVersion);
                }
            }
        }

        // Instantiated adapters registry
        List<String> instantiatedClasses = new ArrayList<>();

        // Match versions and dynamically load corresponding adapters
        tryLoadAdapter("[1.20, 1.20.1]", "net.chainloader.loader.compat.Chainlink1_20_1_Fabric", targetVersions, classLoader, instantiatedClasses);
        tryLoadAdapter("[1.19.3, 1.19.4]", "net.chainloader.loader.compat.Chainlink1_19_3_Fabric", targetVersions, classLoader, instantiatedClasses);
        tryLoadAdapter("[1.19, 1.19.2]", "net.chainloader.loader.compat.Chainlink1_19_Fabric", targetVersions, classLoader, instantiatedClasses);
        tryLoadAdapter("[1.18, 1.18.2]", "net.chainloader.loader.compat.Chainlink1_18_Fabric", targetVersions, classLoader, instantiatedClasses);
        tryLoadAdapter("[1.17, 1.17.1]", "net.chainloader.loader.compat.Chainlink1_17_Fabric", targetVersions, classLoader, instantiatedClasses);
        tryLoadAdapter("[1.16, 1.16.5]", "net.chainloader.loader.compat.Chainlink1_16_Fabric", targetVersions, classLoader, instantiatedClasses);
    }

    private void tryLoadAdapter(String range, String className, List<String> targetVersions, ClassLoader classLoader, List<String> instantiatedClasses) {
        boolean match = false;
        for (String targetVer : targetVersions) {
            if (ChainLauncher.versionMatches(range, targetVer)) {
                match = true;
                break;
            }
        }
        if (match && !instantiatedClasses.contains(className)) {
            System.out.println("[Chainlink 1.21.1 Fabric] Mod version match detected. Dynamically instantiating backwards compat module: " + className);
            try {
                Class<?> adapterClass = Class.forName(className, true, classLoader);
                Chainlink linkInstance = (Chainlink) adapterClass.getDeclaredConstructor().newInstance();
                linkInstance.onWakeUp(classLoader);
                ChainLauncher.getActiveLinks().add(linkInstance);
                instantiatedClasses.add(className);
                System.out.println("[Chainlink 1.21.1 Fabric] Successfully registered: " + className);
            } catch (Throwable t) {
                System.err.println("[Chainlink 1.21.1 Fabric] Failed to dynamically load adapter " + className + ":");
                t.printStackTrace();
            }
        }
    }
}
