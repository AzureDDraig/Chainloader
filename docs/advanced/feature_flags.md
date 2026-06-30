# Boot Options, settings, & Feature Flags

ChainLoader's startup behavior, mod directories, classloading targets, and debugging options are configured using standard Java system properties (arguments passed via `-D`).

These options are parsed early in the boot sequence by `ChainLauncher.java` before the classloader, logger, or game components are initialized.

---

## 1. Complete System Properties Reference

The following properties configure the ChainLoader runtime:

### `chainloader.gameVersion`
*   **Description**: Overrides the target game version reported to mods during dependency checks and metadata normalization.
*   **Example**: `-Dchainloader.gameVersion=1.21.1`
*   **Default**: Automatically detected from the Minecraft jar metadata.

### `chainloader.target`
*   **Description**: Specifies the main class of the game to hand execution to after bootstrapping completes.
*   **Example**: `-Dchainloader.target=net.minecraft.client.main.Main`
*   **Default**: `net.minecraft.client.main.Main`

### `chainloader.gameDir`
*   **Description**: Configures the root working directory of the game. Useful for running sandbox profiles.
*   **Example**: `-Dchainloader.gameDir=.minecraft-test`
*   **Default**: Current working directory (`.`).

### `chainloader.modsDir`
*   **Description**: Defines the folder path where ChainLoader scans for mod jar files.
*   **Example**: `-Dchainloader.modsDir=mods/1.21.1`
*   **Default**: `mods`

### `chainloader.additionalClasspath`
*   **Description**: Appends extra library paths or folders to the `ChainClassLoader` classpath.
*   **Example**: `-Dchainloader.additionalClasspath=lib/jei-compat.jar;libs/dev-deps/`
*   **Default**: None.

### `chainloader.debug`
*   **Description**: Enables verbose console logging and outputs step-by-step traces for bytecode transformations, class remappings, and event posting.
*   **Example**: `-Dchainloader.debug=true`
*   **Default**: `false`

---

## 2. Boot Arguments Parsing Cycle

When `ChainLauncher.main(String[] args)` is executed:
1.  **System Settings Query**: Properties are retrieved using `System.getProperty()`.
2.  **Early Logging Setup**: If `chainloader.debug` is enabled, the console logger outputs verbose traces immediately.
3.  **Workspace Directory Resolution**: If `chainloader.gameDir` is specified, ChainLoader verifies that the folder exists or creates it, establishing it as the working space.
4.  **Classpath Scan**: The launcher loads mod jars from `chainloader.modsDir` and libraries from `chainloader.additionalClasspath`, dynamically building the classloader classpath.
5.  **Progress Update**: The launcher updates the early bootloader GUI with mod scan statistics.
6.  **Hand-off**: The target class (`chainloader.target`) is loaded and its `main(String[])` method is invoked.

---

## 3. Mod Metadata Normalization Effects

When mods are scanned:
*   ChainLoader reads mod configurations (`fabric.mod.json`, `META-INF/neoforge.mods.toml`, `META-INF/mods.toml`).
*   The scanner checks dependencies against `chainloader.gameVersion`. If a mod declares it requires `1.20.1` but `chainloader.gameVersion` is set to `1.21.1`, the dependency parser normalizes the mod constraints so it can load under the 1.21.1 workspace, mapping older dependencies (like `fabric-api`) to ChainLoader's compat shims.
