package net.chainloader.loader.compat;

import java.util.Collection;

public interface Chainlink {
    /**
     * Returns the target Minecraft version range supported by this module (e.g., "[1.21, 1.21.1]").
     */
    String getSupportedVersionRange();

    /**
     * Returns the target modloader type supported (e.g., "forge", "fabric", "neoforge").
     */
    String getSupportedLoaderType();

    /**
     * Called when the Core wakes up this Chainlink module.
     */
    void onWakeUp(ClassLoader classLoader);

    /**
     * Version-and-loader-specific bytecode remappings.
     */
    String mapMethod(String owner, String name, String descriptor);
    String mapField(String owner, String name, String descriptor);
    String mapClass(String className);
    byte[] transform(String className, byte[] bytes);

    /**
     * Returns custom stubs packages that must bypass parent delegation.
     */
    Collection<String> getSelfLoadedPackages();

    /**
     * Returns a set of string markers (e.g., class names, package prefixes) that,
     * when found in a class's constant pool, indicate the class needs remapping.
     * This allows the core's fast-path check to remain version-agnostic.
     */
    Collection<String> getRemapTargetMarkers();

    /**
     * Checks if the given internal class name is a Screen class in this version.
     * @param internalName the class internal name (slash-separated)
     * @return true if it's a screen class, false if unknown/not applicable
     */
    boolean isScreenClass(String internalName);

    /**
     * Checks if the given internal class name is an AbstractWidget class in this version.
     * @param internalName the class internal name (slash-separated)
     * @return true if it's a widget class, false if unknown/not applicable
     */
    boolean isWidgetClass(String internalName);

    /**
     * Checks if the given internal class name is a GUI event listener in this version.
     * @param internalName the class internal name (slash-separated)
     * @return true if it's a listener class, false if unknown/not applicable
     */
    boolean isListenerClass(String internalName);

    /**
     * GUI hooks.
     */
    Object interceptSetScreen(Object screen);
    void onInitTitleScreen(Object titleScreen);
    void onRenderTitleScreen(Object titleScreen, Object guiGraphics);
    void onInitPauseScreen(Object pauseScreen);
}
