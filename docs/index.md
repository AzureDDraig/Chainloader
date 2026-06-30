# ChainLoader Documentation Index

Welcome to the ChainLoader documentation. This documentation details how ChainLoader bridges legacy Forge and Fabric mods onto Minecraft 1.21.1 (NeoForge). It is structured in the same way as the NeoForge documentation to make it familiar to developers.

## Sidebar / Table of Contents

### 1. [Getting Started](gettingstarted/mod_files.md)
* [Mod Files](gettingstarted/mod_files.md) — Mod discovery, metadata normalization, and boot sequence
* [Structuring Your Mod](gettingstarted/structuring.md) — Folder layouts, source sets, and dependency resolution
* [Versioning](gettingstarted/versioning.md) — Semver translation and version range shimming

### 2. [Concepts](concepts/registries.md)
* [Registries](concepts/registries.md) — Intercepting and remapping mod registries
* [Sides](concepts/sides.md) — Client vs Server isolation and side annotation stripping
* [Events](concepts/events.md) — Event translation, translation bus, and bridge helpers
* [Chainlinks & Subloaders](concepts/chainlinks_subloaders.md) — Modular compatibility design and SPI loading
* [Creating a Custom Chainlink](concepts/custom_chainlink.md) — Developer guide to writing custom mod patches

### 3. [Blocks](blocks/blockstates.md)
* [Blockstates](blocks/blockstates.md) — Block registries and blockstate property compatibility shims

### 4. [Items](items/interactions.md)
* [Interactions](items/interactions.md) — Remapping item use and interaction hooks
* [Data Components](items/data_components.md) — NBT-to-Component bridging and `TrackedCompoundTag`
* [Consumables](items/consumables.md) — Eating and drinking interaction translations
* [Tools](items/tools.md) — Tool tiers, mining speed, and tool action shimming
* [Armor](items/armor.md) — Armor material properties and trim translations
* [Mob Effects & Potions](items/effects_potions.md) — Custom effects and brewing recipe shims

### 5. [Entities](entities/data_networking.md)
* [Data and Networking](entities/data_networking.md) — Entity data attachments and level query overrides
* [Living Entities, Mobs & Players](entities/living_entities_mobs_players.md) — Attributes, ticks, and entity lifetime events
* [Attributes](entities/attributes.md) — Legacy attribute registry and getter redirects
* [Entity Renderers](entities/renderers.md) — Render layers, registration bridges, and model adapters

### 6. [Block Entities](blockentities/renderer.md)
* [BlockEntityRenderer](blockentities/renderer.md) — Rendering block entities and data synchronization shims

### 7. [Resources](resources/metadata.md)
* [Resource Metadata](resources/metadata.md) — Pack metadata translation and validation
* Client Resources:
  * [I18n and L10n](resources/client/i18n.md)
  * [Models](resources/client/models.md)
  * [Particles](resources/client/particles.md)
  * [Sounds](resources/client/sounds.md)
  * [Textures](resources/client/textures.md)
* Server Resources:
  * [Advancements](resources/server/advancements.md)
  * [Data Load Conditions](resources/server/data_load_conditions.md)
  * [Damage Types & Damage Sources](resources/server/damage_types.md)
  * [Data Maps](resources/server/data_maps.md)
  * [Enchantments](resources/server/enchantments.md)
  * [Loot Tables](resources/server/loot_tables.md)
  * [Recipes](resources/server/recipes.md)
  * [Tags](resources/server/tags.md)

### 8. [Inventories & Transfers](inventories_transfers/containers.md)
* [Containers](inventories_transfers/containers.md) — Menu provider overrides and screen handler shims
* [Capabilities](inventories_transfers/capabilities.md) — Item, Fluid, and Energy transfer translation (Forge vs Fabric vs NeoForge)
* [Menus](inventories_transfers/menus.md) — GUI menu registration and custom menu type bridges
* [Transactions](inventories_transfers/transactions.md) — Inventory transactions, item insertion, and slot changes

### 9. [Data Storage](datastorage/nbt.md)
* [Named Binary Tag (NBT)](datastorage/nbt.md) — NBT serialization/deserialization and component synchronization
* [Codecs](datastorage/codecs.md) — Serialization adapters and JSON/NBT codecs
* [Value I/O](datastorage/value_io.md) — Custom stream and packet buffer readers/writers
* [Data Attachments](datastorage/attachments.md) — Attaching arbitrary data structures to levels, entities, and chunks
* [Saved Data](datastorage/saved_data.md) — Map storage and level saved data shims

### 10. [Worldgen](worldgen/biome_modifiers.md)
* [Biome Modifiers](worldgen/biome_modifiers.md) — Precipitation, climate parameters, and biome feature translation

### 11. [Networking](networking/registering_payloads.md)
* [Registering Payloads](networking/registering_payloads.md) — Registry routing and channel namespace allocation
* [Stream Codecs](networking/stream_codecs.md) — Legacy payload adapters and StreamEncoder/StreamDecoder stubs
* [Using Configuration Tasks](networking/configuration_tasks.md) — Config stage packets and custom handshake shims

### 12. [Rendering](rendering/features.md)
* [Features](rendering/features.md) — Shader registry and render types
* [Client Particles](rendering/particles.md) — Custom particle registry bridges and factories
* [Screens](rendering/screens.md) — Removing `final` from `renderWidget`, checkbox backports, and super constructor redirects

### 13. [Advanced Topics](advanced/access_transformers.md)
* [Access Transformers](advanced/access_transformers.md) — Wildcard access wideners and member visibility compilation
* [Extensible Enums](advanced/extensible_enums.md) — Dynamic enum injection and class-load interception
* [Feature Flags](advanced/feature_flags.md) — Custom feature toggles and boot phase options

### 14. [Miscellaneous](miscellaneous/configuration.md)
* [Configuration](miscellaneous/configuration.md) — Forge ConfigSpec to NeoForge config system mapping
* [Debug Profiler](miscellaneous/debug_profiler.md) — Performance profiling and bytecode overhead analysis
* [Game Tests](miscellaneous/game_tests.md) — Automated game tests and simulation structures
* [Identifiers](miscellaneous/identifiers.md) — ResourceLocation redirects and Fabric ID shims
* [Key Mappings](miscellaneous/key_mappings.md) — Key binding registry bridges and client registration triggers
