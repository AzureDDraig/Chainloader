# Mod Files

ChainLoader functions as a translation and runtime compatibility layer for legacy Forge and Fabric mods on NeoForge. Finding, reading, and loading these mod binaries is the first phase of the boot process.

## 1. Mod Discovery (`ModScanner`)
Mod discovery is coordinated by `net.chainloader.loader.core.ModScanner`. The scanner runs during the early boot phase and performs the following tasks:
* **Scanning Directory**: Inspects the configured `mods` directory (and any nested folders or command-line targets) for `.jar` files.
* **Zip/Jar Verification**: Opens each candidate zip file to verify whether it contains a valid mod descriptor:
  - `fabric.mod.json` for Fabric mods.
  - `META-INF/mods.toml` for Forge/NeoForge mods.
* **Extracting Metadata**: If a descriptor is found, the scanner extracts the raw file bytes for normalization.

## 2. Metadata Normalization (`MetadataNormalizer`)
Because Forge and Fabric mods use entirely different formats and schemas to declare metadata (dependencies, entrypoints, name, version, authors, etc.), ChainLoader normalizes both formats into a unified metadata structure: `net.chainloader.loader.core.ChainModMetadata`.

The normalizer (`net.chainloader.loader.core.MetadataNormalizer`) converts fields as follows:

| Field / Feature | Fabric (`fabric.mod.json`) | Forge (`mods.toml`) | Normalized Internal Format |
| :--- | :--- | :--- | :--- |
| **Mod ID** | `id` | `modId` | `id` |
| **Version** | `version` | `version` | `version` (standardized via SemVer) |
| **Display Name** | `name` | `displayName` | `name` |
| **Dependencies** | `depends` | `[[dependencies.<modId>]]` | Unified dependency range matching rules |
| **Entrypoints** | `entrypoints` | `modLoader` / `loaderVersion` | Custom entrypoint registry (mapped by lifecycle stage) |

This normalization allows the modloader's core dependency resolver and classloading controller to treat Fabric and Forge mods uniformly.

## 3. The Boot & Launch Sequence (`ChainLauncher`)
ChainLoader hooks into the Java launch environment via **ModLauncher** using the `ILaunchPluginService` interface.

1. **Launch Hook**: `net.chainloader.loader.core.ChainLauncher` is invoked as the main entrypoint or classloader delegate.
2. **Knot Adapter**: The classloader uses `KnotClassLoaderAdapter` to mimic the Fabric environment, satisfying mods that check for or rely on Fabric's Knot classloader structure.
3. **Dependency Resolution**: Once all metadata is normalized, the dependency chain is verified. If any required libraries or core mods are missing, the early loading GUI (`EarlyLoadingScreen`) displays a diagnostic error.
4. **Bootstrapping**: Mods are loaded into isolated or shared classloader paths depending on their target environments, and their initializers are run.
