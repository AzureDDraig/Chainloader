package net.neoforged.fml.loading;

import java.nio.file.Path;
import java.nio.file.Paths;

public enum FMLPaths {
    GAMEDIR(Paths.get(".")),
    MODSDIR(Paths.get("mods")),
    CONFIGDIR(Paths.get("config")),
    FMLCONFIG(Paths.get("config/fml.toml"));

    private final Path path;

    FMLPaths(Path path) {
        this.path = path.toAbsolutePath().normalize();
    }

    public Path get() {
        return this.path;
    }
}
