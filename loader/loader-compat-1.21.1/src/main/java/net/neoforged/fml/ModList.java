package net.neoforged.fml;

import java.util.Optional;
import java.util.List;
import net.neoforged.neoforgespi.language.ModFileScanData;
import net.chainloader.loader.transformer.ModScanDataHelper;

public class ModList {
    private static final ModList INSTANCE = new ModList();

    public static ModList get() {
        return INSTANCE;
    }

    public boolean isLoaded(String modId) {
        return false;
    }

    public Optional<?> getModContainerById(String modId) {
        return Optional.empty();
    }

    public List<ModFileScanData> getAllScanData() {
        return ModScanDataHelper.getNeoForgeScanData();
    }
}
