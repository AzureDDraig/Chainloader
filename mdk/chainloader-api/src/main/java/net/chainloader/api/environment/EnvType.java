package net.chainloader.api.environment;

/**
 * Represents the physical environment or distribution type on which the mod is running.
 */
public enum EnvType {
    /**
     * The physical client environment (including singleplayer and multiplayer client).
     */
    CLIENT,

    /**
     * The dedicated server environment.
     */
    SERVER
}
