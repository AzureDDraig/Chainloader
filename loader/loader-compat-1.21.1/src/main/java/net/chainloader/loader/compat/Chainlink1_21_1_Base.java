package net.chainloader.loader.compat;

import net.chainloader.loader.compat.asset.AssetPathRelocator;
import net.chainloader.loader.core.ChainClassLoader;
import net.chainloader.loader.core.gui.MainMenuHelper;
import net.chainloader.loader.transformer.BytecodeTransformer;
import org.objectweb.asm.*;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public abstract class Chainlink1_21_1_Base implements Chainlink {

    /** Guard to ensure version-specific mappings are only loaded once across all 1.21.1 Chainlink modules. */
    private static volatile boolean mappingsLoaded = false;
    private static final Object MAPPINGS_LOCK = new Object();

    private boolean transformerRegistered = false;

    @Override
    public String getSupportedVersionRange() {
        return "[1.21, 1.21.1]";
    }

    @Override
    public void onWakeUp(ClassLoader classLoader) {
        System.out.println("[Chainlink 1.21.1 Base] Waking up compat module for loader: " + getSupportedLoaderType());
        
        BytecodeTransformer transformer = BytecodeTransformer.getInstance();

        // Only load version-specific mappings and register transformers once
        // (they're shared across Forge/Fabric/NeoForge for the same MC version)
        synchronized (MAPPINGS_LOCK) {
            if (!mappingsLoaded) {
                if (transformer != null) {
                    loadSharedMappings(transformer);
                }

                // Initialize the bi-directional EventTranslatorBus
                try {
                    net.chainloader.api.event.ChainEventBus hostBus = new net.chainloader.api.event.ChainEventBus();
                    net.minecraftforge.eventbus.api.IEventBus forgeBus = net.minecraftforge.common.MinecraftForge.EVENT_BUS;
                    net.neoforged.bus.api.IEventBus neoforgeBus = net.neoforged.neoforge.common.NeoForge.EVENT_BUS;
                    
                    net.chainloader.loader.compat.bridge.EventTranslatorBus.getInstance().init(hostBus, forgeBus, neoforgeBus);
                    System.out.println("[Chainlink 1.21.1 Base] EventTranslatorBus initialized successfully.");
                } catch (Throwable t) {
                    System.err.println("[Chainlink 1.21.1 Base] Failed to initialize EventTranslatorBus:");
                    t.printStackTrace();
                }

                if (classLoader instanceof ChainClassLoader) {
                    ChainClassLoader ccl = (ChainClassLoader) classLoader;
                    
                    // Register AssetPathRelocator
                    ccl.addTransformer(AssetPathRelocator.getInstance()::transform);
                    
                    // Register a unified delegator transformer that runs the host transformations first,
                    // then delegates to the oldest active legacy compatibility link's transformations.
                    ccl.addTransformer((className, bytes) -> {
                        // 1. Run host (1.21.1) transformations first
                        bytes = this.transform(className, bytes);

                        // 2. Find the oldest active legacy compatibility link
                        Chainlink oldestLegacy = null;
                        for (Chainlink link : net.chainloader.loader.core.ChainLauncher.getActiveLinks()) {
                            if (link.getSupportedVersionRange().contains("1.21")) {
                                continue; // Skip host links
                            }
                            if (oldestLegacy == null) {
                                oldestLegacy = link;
                            } else {
                                if (isOlder(link, oldestLegacy)) {
                                    oldestLegacy = link;
                                }
                            }
                        }

                        if (oldestLegacy != null) {
                            bytes = oldestLegacy.transform(className, bytes);
                        }

                        return bytes;
                    });
                }

                mappingsLoaded = true;
            }
        }
    }

    private static boolean isOlder(Chainlink link1, Chainlink link2) {
        try {
            String r1 = link1.getSupportedVersionRange();
            String r2 = link2.getSupportedVersionRange();
            String v1 = extractFirstVersion(r1);
            String v2 = extractFirstVersion(r2);
            return net.chainloader.loader.core.ChainLauncher.compareVersions(v1, v2) < 0;
        } catch (Throwable t) {
            return false;
        }
    }

    private static String extractFirstVersion(String range) {
        String clean = range.replace("[", "").replace("]", "").replace("(", "").replace(")", "");
        String[] parts = clean.split(",");
        return parts[0].trim();
    }

    /**
     * Loads all version-specific mappings that are shared across all loader types.
     * Called exactly once per version, guarded by {@link #mappingsLoaded}.
     */
    private static void loadSharedMappings(BytecodeTransformer transformer) {
        // Register package prefix mappings
        transformer.addPackageMapping("net/minecraft/block/entity", "net/minecraft/world/level/block/entity");
        transformer.addPackageMapping("net/minecraft/block", "net/minecraft/world/level/block");
        transformer.addPackageMapping("net/minecraft/item", "net/minecraft/world/item");
        transformer.addPackageMapping("net/minecraft/entity", "net/minecraft/world/entity");
        transformer.addPackageMapping("net/minecraft/fluid", "net/minecraft/world/level/material");
        transformer.addPackageMapping("net/minecraft/tileentity", "net/minecraft/world/level/block/entity");
        transformer.addPackageMapping("net/minecraft/world/biome", "net/minecraft/world/level/biome");
        transformer.addPackageMapping("net/minecraft/world/gen", "net/minecraft/world/level/levelgen");
        transformer.addPackageMapping("net/minecraft/world/chunk", "net/minecraft/world/level/chunk");
        transformer.addPackageMapping("net/minecraft/world/dimension", "net/minecraft/world/level/dimension");

        // Register Architectury API Bridges
        transformer.addClassMapping("me/shedaniel/architectury/platform/Platform", "net/chainloader/loader/compat/lib/ArchitecturyApiBridge$Platform");
        transformer.addClassMapping("dev/architectury/platform/Platform", "net/chainloader/loader/compat/lib/ArchitecturyApiBridge$Platform");
        
        transformer.addClassMapping("me/shedaniel/architectury/utils/Env", "net/chainloader/loader/compat/lib/ArchitecturyApiBridge$Env");
        transformer.addClassMapping("dev/architectury/utils/Env", "net/chainloader/loader/compat/lib/ArchitecturyApiBridge$Env");
        
        transformer.addClassMapping("me/shedaniel/architectury/registry/DeferredRegister", "net/chainloader/loader/compat/lib/ArchitecturyApiBridge$DeferredRegister");
        transformer.addClassMapping("dev/architectury/registry/registries/DeferredRegister", "net/chainloader/loader/compat/lib/ArchitecturyApiBridge$DeferredRegister");
        
        transformer.addClassMapping("me/shedaniel/architectury/registry/RegistrySupplier", "net/chainloader/loader/compat/lib/ArchitecturyApiBridge$RegistrySupplier");
        transformer.addClassMapping("dev/architectury/registry/registries/RegistrySupplier", "net/chainloader/loader/compat/lib/ArchitecturyApiBridge$RegistrySupplier");
        
        // ActionResult to InteractionResult mapping redirect (Yarn -> Mojang)
        transformer.addClassMapping("net/minecraft/util/ActionResult", "net/minecraft/world/InteractionResult");

        transformer.addClassMapping("me/shedaniel/architectury/registry/Registries", "net/chainloader/loader/compat/lib/ArchitecturyApiBridge$Registries");
        transformer.addClassMapping("dev/architectury/registry/registries/Registries", "net/chainloader/loader/compat/lib/ArchitecturyApiBridge$Registries");
        
        transformer.addClassMapping("me/shedaniel/architectury/event/Event", "net/chainloader/loader/compat/lib/ArchitecturyApiBridge$Event");
        transformer.addClassMapping("dev/architectury/event/Event", "net/chainloader/loader/compat/lib/ArchitecturyApiBridge$Event");
        
        transformer.addClassMapping("me/shedaniel/architectury/event/EventFactory", "net/chainloader/loader/compat/lib/ArchitecturyApiBridge$EventFactory");
        transformer.addClassMapping("dev/architectury/event/EventFactory", "net/chainloader/loader/compat/lib/ArchitecturyApiBridge$EventFactory");
        
        transformer.addClassMapping("me/shedaniel/architectury/event/events/client/ClientLifecycleEvent", "net/chainloader/loader/compat/lib/ArchitecturyApiBridge$ClientLifecycleEvent");
        transformer.addClassMapping("dev/architectury/event/events/client/ClientLifecycleEvent", "net/chainloader/loader/compat/lib/ArchitecturyApiBridge$ClientLifecycleEvent");
        
        transformer.addClassMapping("me/shedaniel/architectury/event/events/LifecycleEvent", "net/chainloader/loader/compat/lib/ArchitecturyApiBridge$LifecycleEvent");
        transformer.addClassMapping("dev/architectury/event/events/LifecycleEvent", "net/chainloader/loader/compat/lib/ArchitecturyApiBridge$LifecycleEvent");
        
        // GuiRegistry mappings
        transformer.addClassMapping("me/shedaniel/architectury/registry/client/GuiRegistry", "net/chainloader/loader/compat/lib/ArchitecturyApiBridge$GuiRegistry");
        transformer.addClassMapping("dev/architectury/registry/client/GuiRegistry", "net/chainloader/loader/compat/lib/ArchitecturyApiBridge$GuiRegistry");

        // Forge WorldEvent to LevelEvent mapping redirects (for 1.18.2 and older support)
        transformer.addClassMapping("net/minecraftforge/event/world/WorldEvent", "net/minecraftforge/event/level/LevelEvent");
        transformer.addClassMapping("net/minecraftforge/event/world/WorldEvent$Load", "net/minecraftforge/event/level/LevelEvent$Load");
        transformer.addClassMapping("net/minecraftforge/event/world/WorldEvent$Unload", "net/minecraftforge/event/level/LevelEvent$Unload");
        transformer.addClassMapping("net/minecraftforge/event/world/WorldEvent$Save", "net/minecraftforge/event/level/LevelEvent$Save");

        // Forge BlockEvent remapping redirects (for 1.17.1 and older support)
        transformer.addClassMapping("net/minecraftforge/event/world/BlockEvent", "net/minecraftforge/event/level/BlockEvent");
        transformer.addClassMapping("net/minecraftforge/event/world/BlockEvent$BreakEvent", "net/minecraftforge/event/level/BlockEvent$BreakEvent");

        // Minecraft 1.21.1 GUI Class Remappings
        transformer.addClassMapping("net/minecraft/client/gui/screens/Screen", "fod");
        transformer.addClassMapping("net/minecraft/client/gui/screens/TitleScreen", "fof");
        transformer.addClassMapping("net/minecraft/client/gui/screens/options/VideoSettingsScreen", "frl");
        transformer.addClassMapping("net/minecraft/client/gui/screens/options/OptionsSubScreen", "frh");
        transformer.addClassMapping("net/minecraft/client/gui/components/Button", "fim");
        transformer.addClassMapping("net/minecraft/client/gui/components/Button$Builder", "fim$a");
        transformer.addClassMapping("net/minecraft/client/gui/components/Button$OnPress", "fim$c");
        transformer.addClassMapping("net/minecraft/client/gui/components/events/GuiEventListener", "fki");
        transformer.addClassMapping("net/minecraft/network/chat/Component", "wz");
        transformer.addClassMapping("net/minecraft/network/chat/MutableComponent", "xn");
        transformer.addClassMapping("net/minecraft/client/gui/GuiGraphics", "fhz");
        transformer.addClassMapping("net/minecraft/client/gui/Font", "fhx");
        transformer.addClassMapping("net/minecraft/client/Minecraft", "fgo");
        transformer.addClassMapping("net/minecraft/client/Options", "fgs");
        transformer.addClassMapping("net/minecraft/client/OptionInstance", "fgr");
        transformer.addClassMapping("net/minecraft/client/gui/screens/PauseScreen", "fny");
        transformer.addClassMapping("net/minecraft/client/gui/components/AbstractWidget", "fik");

        // Parse and load mapping files (Mojang, Intermediary, Searge)
        transformer.loadMojangMappings("lib/client_mappings.txt");
        transformer.loadTinyMappings("lib/intermediary_mappings.tiny");
        transformer.loadSeargeMappings("lib/joined.tsrg");
    }

    @Override
    public String mapMethod(String owner, String name, String descriptor) {
        if ("getWorld".equals(name) && (owner.contains("LevelEvent") || owner.contains("BlockEvent"))) {
            return "getLevel";
        }
        if ("packId".equals(name) && "()Ljava/lang/String;".equals(descriptor)) {
            return "b";
        }
        if (isClass(owner, "net/minecraft/world/item/ItemStack", "cuq")) {
            if (name.equals("get")) return "c";
            if (name.equals("set")) return "b";
            if (name.equals("parseOptional")) return "a";
            if (name.equals("save")) return descriptor.contains("Tag") ? "b" : "a";
            if (name.equals("getEnchantments")) return "B";
        }
        if (isClass(owner, "net/minecraft/world/item/component/CustomData", "cxh")) {
            if (name.equals("isEmpty")) return "b";
            if (name.equals("copyTag")) return "c";
            if (name.equals("of")) return "a";
        }
        if (isClass(owner, "net/minecraft/world/item/enchantment/ItemEnchantments", "dai")) {
            if (name.equals("entrySet")) return "b";
            if (name.equals("keySet")) return "a";
            if (name.equals("getLevel")) return "a";
        }
        if (isClass(owner, "net/minecraft/core/Holder", "jm")) {
            if (name.equals("value")) return "a";
        }
        if (isClass(owner, "net/minecraft/nbt/CompoundTag", "ub")) {
            if (name.equals("put")) return "a";
            if (name.equals("contains")) return descriptor.endsWith("I)Z") ? "b" : "e";
            if (name.equals("getCompound")) return "p";
        }
        if (name.equals("registryAccess")) {
            if (isClass(owner, "net/minecraft/server/MinecraftServer", "net/minecraft/server/MinecraftServer")) {
                return "bc";
            }
            if (isClass(owner, "net/minecraft/world/level/Level", "dcw") ||
                isClass(owner, "net/minecraft/client/multiplayer/ClientLevel", "fzf") ||
                isClass(owner, "net/minecraft/world/level/LevelReader", "dcz")) {
                return "H_";
            }
        }
        if ("m_6106_".equals(name)) {
            return "A_";
        }
        if ("m_8891_".equals(name)) {
            return "H_";
        }
        if (isClass(owner, "net/minecraft/client/gui/components/events/GuiEventListener", "fki") ||
            isClass(owner, "net/minecraft/client/gui/components/events/ContainerEventHandler", "fkg") ||
            isClass(owner, "net/minecraft/client/gui/screens/Screen", "fod") ||
            isClass(owner, "net/minecraft/client/gui/components/AbstractWidget", "fik")) {
            if (name.equals("mouseClicked")) return "a";
        }
        if (isClass(owner, "net/minecraft/client/gui/screens/Screen", "fod") ||
            isClass(owner, "net/minecraft/client/gui/screens/TitleScreen", "fof") ||
            isClass(owner, "net/minecraft/client/gui/screens/PauseScreen", "fny") ||
            isClass(owner, "net/minecraft/client/gui/screens/options/VideoSettingsScreen", "frl") ||
            isClass(owner, "net/minecraft/client/gui/screens/options/OptionsSubScreen", "frh")) {
            if (name.equals("addRenderableWidget")) return "c";
            if (name.equals("render")) return "a";
            if (name.equals("init")) return "aT_";
            if (name.equals("renderBackground")) return "b";
        }
        if (isClass(owner, "net/minecraft/network/chat/Component", "wz")) {
            if (name.equals("literal")) return "b";
            if (name.equals("translatable")) return "c";
            if ((name.equals("getString") || name.equals("a")) && "()Ljava/lang/String;".equals(descriptor)) return "d";
        }
        if (isClass(owner, "net/minecraft/client/gui/GuiGraphics", "fhz")) {
            if (name.equals("drawString")) {
                return descriptor.endsWith("Z)I") ? "a" : "b";
            }
            if (name.equals("fill")) return "a";
        }
        if (isClass(owner, "net/minecraft/client/Minecraft", "fgo")) {
            if (name.equals("getInstance")) return "Q";
            if (name.equals("setScreen")) return "a";
        }
        if (isClass(owner, "net/minecraft/locale/Language", "tw")) {
            if (name.equals("getInstance")) return "a";
            if (name.equals("getOrDefault")) return "a";
            if (name.equals("has")) return "b";
        }
        if (isClass(owner, "net/minecraft/ChatFormatting", "n")) {
            if (name.equals("getByName")) return "b";
            if (name.equals("getById")) return "a";
            if (name.equals("getByCode")) return "a";
            if (name.equals("stripFormatting")) return "a";
        }
        if (isClass(owner, "net/minecraft/resources/ResourceLocation", "akr")) {
            if (name.equals("fromNamespaceAndPath")) return "a";
            if (name.equals("tryParse")) return "c";
            if (name.equals("parse")) return "a";
            if (name.equals("tryBuild")) return "b";
            if (name.equals("withDefaultNamespace")) return "b";
            if (name.equals("getPath")) return "a";
            if (name.equals("getNamespace")) return "b";
            if (name.equals("withPath")) return descriptor.contains("java/lang/String") ? "e" : "a";
            if (name.equals("withPrefix")) return "f";
            if (name.equals("withSuffix")) return "g";
        }
        if (isClass(owner, "com/mojang/blaze3d/platform/InputConstants$Key", "fae$a")) {
            if (name.equals("getType")) return "a";
            if (name.equals("getValue")) return "b";
            if (name.equals("getName")) return "c";
            if (name.equals("getDisplayName")) return "d";
        }
        if (isClass(owner, "com/mojang/blaze3d/platform/InputConstants$Type", "fae$b")) {
            if (name.equals("getOrCreate")) return "a";
        }
        if (isClass(owner, "com/mojang/blaze3d/platform/InputConstants", "fae")) {
            if (name.equals("getKey")) return "a";
            if (name.equals("isKeyDown")) return "a";
        }
        if (isClass(owner, "net/minecraft/client/OptionInstance", "fgr")) {
            if (name.equals("get")) return "c";
            if (name.equals("set")) return "a";
        }
        if (isClass(owner, "net/minecraft/client/Options", "fgs")) {
            if (name.equals("load")) return "av";
            if (name.equals("save")) return "aw";
        }
        if (isClass(owner, "net/minecraft/client/gui/components/Button", "fim")) {
            if (name.equals("builder")) return "a";
        }
        if (isClass(owner, "net/minecraft/client/gui/components/Button$Builder", "fim$a")) {
            if (name.equals("bounds")) return "a";
            if (name.equals("pos")) return "a";
            if (name.equals("width")) return "a";
            if (name.equals("size")) return "b";
            if (name.equals("build")) return "a";
        }
        if (isClass(owner, "net/minecraft/world/level/block/entity/BlockEntityType$Builder", "dqj$b")) {
            if (name.equals("of")) return "a";
            if (name.equals("build")) return "a";
        }
        if (isClass(owner, "net/minecraft/world/item/CreativeModeTab", "cta")) {
            if (name.equals("builder")) return "a";
        }
        if (isClass(owner, "net/minecraft/world/item/CreativeModeTab$Builder", "cta$a")) {
            if (name.equals("title")) return "a";
            if (name.equals("icon")) return "a";
            if (name.equals("build")) return "d";
        }
        if (isClass(owner, "net/minecraft/client/renderer/entity/EntityRenderers", "gkk")) {
            if (name.equals("register")) return "a";
        }
        if (isClass(owner, "net/minecraft/core/Registry", "jz") ||
            isClass(owner, "net/minecraft/core/WritableRegistry", "ki")) {
            if (name.equals("register")) return "a";
            if (name.equals("key") || name.equals("getResourceKey")) return "d";
            if (name.equals("get")) return "a";
            if (name.equals("getRawId")) return "a";
        }
        if (isClass(owner, "net/minecraft/resources/ResourceKey", "akq")) {
            if (name.equals("location")) return "a";
        }
        return null;
    }

    @Override
    public String mapField(String owner, String name, String descriptor) {
        if (isClass(owner, "net/minecraft/core/component/DataComponents", "kq")) {
            if (name.equals("CUSTOM_DATA")) return "b";
        }
        if (isClass(owner, "net/minecraft/server/packs/PackType", "ass")) {
            if (name.equals("CLIENT_RESOURCES")) return "a";
            if (name.equals("SERVER_DATA")) return "b";
        }
        if (isClass(owner, "net/minecraft/core/RegistryAccess", "ka")) {
            if (name.equals("EMPTY")) return "b";
        }
        if (isClass(owner, "net/minecraft/client/Minecraft", "fgo")) {
            if (name.equals("level")) return "r";
            if (name.equals("options")) return "m";
        }
        if (isClass(owner, "net/minecraft/client/renderer/ItemBlockRenderTypes", "geu")) {
            if (name.equals("TYPE_BY_BLOCK")) return "a";
            if (name.equals("TYPE_BY_FLUID")) return "b";
        }
        if (isClass(owner, "net/minecraft/client/gui/screens/Screen", "fod") ||
            isClass(owner, "net/minecraft/client/gui/screens/TitleScreen", "fof") ||
            isClass(owner, "net/minecraft/client/gui/screens/PauseScreen", "fny") ||
            isClass(owner, "net/minecraft/client/gui/screens/options/VideoSettingsScreen", "frl") ||
            isClass(owner, "net/minecraft/client/gui/screens/options/OptionsSubScreen", "frh")) {
            if (name.equals("minecraft")) return "l";
            if (name.equals("width")) return "m";
            if (name.equals("height")) return "n";
            if (name.equals("font")) return "o";
            if (name.equals("parent") || name.equals("lastScreen")) return "b";
            if (name.equals("options")) return "c";
            if (name.equals("renderables")) return "v";
            if (name.equals("children")) return "r";
        }
        if (isClass(owner, "net/minecraft/client/gui/components/AbstractWidget", "fik") ||
            isClass(owner, "net/minecraft/client/gui/components/Button", "fim") ||
            isClass(owner, "net/minecraft/client/gui/components/Button$Builder", "fim$a")) {
            if (name.equals("x")) return "c";
            if (name.equals("y")) return "d";
            if (name.equals("width")) return "g";
            if (name.equals("height")) return "h";
            if (name.equals("message")) return "e";
        }
        if (isClass(owner, "net/minecraft/client/Options", "fgs")) {
            if (name.equals("keyMappings")) return "W";
            if (name.equals("graphicsMode") || name.equals("graphics")) return "az";
            if (name.equals("ambientOcclusion") || name.equals("ao")) return "aA";
            if (name.equals("renderDistance")) return "aq";
            if (name.equals("simulationDistance")) return "ar";
            if (name.equals("framerateLimit")) return "au";
            if (name.equals("enableVsync") || name.equals("vsync")) return "bk";
            if (name.equals("bobView") || name.equals("viewBobbing")) return "bD";
            if (name.equals("fov")) return "bP";
            if (name.equals("gamma")) return "ce";
            if (name.equals("guiScale")) return "cg";
            if (name.equals("clouds") || name.equals("renderClouds")) return "av";
            if (name.equals("attackIndicator")) return "ba";
            if (name.equals("particles")) return "ch";
            if (name.equals("narrator")) return "ci";
        }
        if (isClass(owner, "net/minecraft/world/item/CreativeModeTab$Row", "cta$f")) {
            if (name.equals("TOP")) return "a";
            if (name.equals("BOTTOM")) return "b";
        }
        if (isClass(owner, "net/minecraft/world/item/CreativeModeTab$Type", "cta$h")) {
            if (name.equals("CATEGORY")) return "a";
            if (name.equals("INVENTORY")) return "b";
            if (name.equals("HOTBAR")) return "c";
            if (name.equals("SEARCH")) return "d";
        }
        if (isClass(owner, "com/mojang/blaze3d/platform/InputConstants$Type", "fae$b")) {
            if (name.equals("KEYSYM")) return "a";
            if (name.equals("SCANCODE")) return "b";
            if (name.equals("MOUSE")) return "c";
        }
        if (isClass(owner, "com/mojang/blaze3d/platform/InputConstants", "fae")) {
            if (name.equals("UNKNOWN")) return "bv";
        }
        if (isClass(owner, "net/minecraft/ChatFormatting", "n")) {
            if (name.equals("BLACK")) return "a";
            if (name.equals("DARK_BLUE")) return "b";
            if (name.equals("DARK_GREEN")) return "c";
            if (name.equals("DARK_AQUA")) return "d";
            if (name.equals("DARK_RED")) return "e";
            if (name.equals("DARK_PURPLE")) return "f";
            if (name.equals("GOLD")) return "g";
            if (name.equals("GRAY")) return "h";
            if (name.equals("DARK_GRAY")) return "i";
            if (name.equals("BLUE")) return "j";
            if (name.equals("GREEN")) return "k";
            if (name.equals("AQUA")) return "l";
            if (name.equals("RED")) return "m";
            if (name.equals("LIGHT_PURPLE")) return "n";
            if (name.equals("YELLOW")) return "o";
            if (name.equals("WHITE")) return "p";
            if (name.equals("OBFUSCATED")) return "q";
            if (name.equals("BOLD")) return "r";
            if (name.equals("STRIKETHROUGH")) return "s";
            if (name.equals("UNDERLINE")) return "t";
            if (name.equals("ITALIC")) return "u";
            if (name.equals("RESET")) return "v";
        }
        if (isClass(owner, "net/minecraft/core/registries/BuiltInRegistries", "lt")) {
            if (name.equals("REGISTRY")) return "aA";
            if (name.equals("WRITABLE_REGISTRY")) return "aD";
            if (name.equals("BLOCK")) return "e";
            if (name.equals("ENTITY_TYPE")) return "f";
            if (name.equals("ITEM")) return "g";
            if (name.equals("BLOCK_ENTITY_TYPE")) return "j";
            if (name.equals("FLUID")) return "c";
            if (name.equals("SOUND_EVENT")) return "b";
            if (name.equals("MOB_EFFECT")) return "d";
            if (name.equals("POTION")) return "h";
            if (name.equals("RECIPE_TYPE")) return "q";
            if (name.equals("RECIPE_SERIALIZER")) return "r";
            if (name.equals("CREATIVE_MODE_TAB")) return "am";
            if (name.equals("DATA_COMPONENT_TYPE")) return "aq";
            if (name.equals("GAME_EVENT")) return "a";
            if (name.equals("MENU")) return "p";
        }
        if (isClass(owner, "net/minecraft/core/registries/Registries", "lu")) {
            if (name.equals("BLOCK")) return "f";
            if (name.equals("BLOCK_ENTITY_TYPE")) return "h";
            if (name.equals("RECIPE_SERIALIZER")) return "ae";
            if (name.equals("RECIPE_TYPE")) return "af";
            if (name.equals("ITEM")) return "K";
            if (name.equals("FLUID")) return "D";
            if (name.equals("ENTITY_TYPE")) return "z";
            if (name.equals("CREATIVE_MODE_TAB")) return "q";
        }
        return null;
    }

    @Override
    public String mapClass(String className) {
        return null;
    }

    @Override
    public Collection<String> getRemapTargetMarkers() {
        // These are string markers that, when found in a class's constant pool,
        // indicate the class needs remapping for 1.21.1
        return List.of(
            // Deobfuscated package prefixes
            "net/minecraft", "net.minecraft",
            "com/mojang", "com.mojang",
            "me/shedaniel", "me.shedaniel",
            "dev/architectury", "dev.architectury",
            // Obfuscated 1.21.1 class names that mods may reference directly
            "eqz", "cti", "dae", "cuq", "ju", "fzc", "arr", "cta", "cta$a"
        );
    }

    @Override
    public boolean isScreenClass(String internalName) {
        if (internalName == null) return false;
        String deobf = resolveDeobf(internalName);
        return deobf.equals("net/minecraft/client/gui/screens/Screen") ||
               deobf.equals("net/minecraft/client/gui/screens/TitleScreen") ||
               deobf.equals("net/minecraft/client/gui/screens/PauseScreen") ||
               deobf.equals("net/minecraft/client/gui/screens/options/VideoSettingsScreen") ||
               deobf.equals("net/minecraft/client/gui/screens/options/OptionsSubScreen") ||
               deobf.equals("net/chainloader/loader/core/gui/ModListScreen") ||
               deobf.equals("net/chainloader/loader/core/gui/SodiumVideoSettingsScreen");
    }

    @Override
    public boolean isWidgetClass(String internalName) {
        if (internalName == null) return false;
        String deobf = resolveDeobf(internalName);
        return deobf.equals("net/minecraft/client/gui/components/AbstractWidget") ||
               deobf.equals("net/minecraft/client/gui/components/Button") ||
               deobf.equals("net/minecraft/client/gui/components/Button$Builder");
    }

    @Override
    public boolean isListenerClass(String internalName) {
        if (internalName == null) return false;
        if (isScreenClass(internalName) || isWidgetClass(internalName)) return true;
        String deobf = resolveDeobf(internalName);
        return deobf.equals("net/minecraft/client/gui/components/events/GuiEventListener") ||
               deobf.equals("net/minecraft/client/gui/components/events/ContainerEventHandler");
    }

    /**
     * Resolves an obfuscated internal name to its deobfuscated form using the
     * BytecodeTransformer's OBF_TO_DEOBF_CLASS_MAPPINGS if available.
     */
    private String resolveDeobf(String internalName) {
        String slash = internalName.replace('.', '/');
        BytecodeTransformer transformer = BytecodeTransformer.getInstance();
        if (transformer != null) {
            String deobf = transformer.getDeobfClassName(slash);
            return deobf != null ? deobf : slash;
        }
        return slash;
    }

    @Override
    public Collection<String> getSelfLoadedPackages() {
        return List.of("net.chainloader.loader.core.gui.");
    }

    @Override
    public Object interceptSetScreen(Object screen) {
        return MainMenuHelper.interceptSetScreen((net.minecraft.client.gui.screens.Screen) screen);
    }

    @Override
    public void onInitTitleScreen(Object titleScreen) {
        MainMenuHelper.onInitTitleScreen((net.minecraft.client.gui.screens.TitleScreen) titleScreen);
    }

    @Override
    public void onRenderTitleScreen(Object titleScreen, Object guiGraphics) {
        MainMenuHelper.onRenderTitleScreen((net.minecraft.client.gui.screens.TitleScreen) titleScreen, (net.minecraft.client.gui.GuiGraphics) guiGraphics);
    }

    @Override
    public void onInitPauseScreen(Object pauseScreen) {
        MainMenuHelper.onInitPauseScreen((net.minecraft.client.gui.screens.PauseScreen) pauseScreen);
    }

    private boolean isClass(String owner, String deobfName, String obfName) {
        if (owner == null) return false;
        String ownerSlash = owner.replace('.', '/');
        String deobfSlash = deobfName.replace('.', '/');
        String obfSlash = obfName != null ? obfName.replace('.', '/') : null;
        
        if (ownerSlash.equals(deobfSlash) || (obfSlash != null && ownerSlash.equals(obfSlash))) {
            return true;
        }
        
        BytecodeTransformer bt = BytecodeTransformer.getInstance();
        if (bt != null) {
            // Check OBF_TO_MOJANG_CLASS_MAPPINGS first
            String deobf = bt.getDeobfClassName(ownerSlash);
            if (deobf != null && deobf.equals(deobfSlash)) {
                return true;
            }
            
            // Fallback to mapped (e.g. intermediary -> obf)
            String mapped = bt.map(ownerSlash);
            if (mapped != null && (mapped.equals(deobfSlash) || (obfSlash != null && mapped.equals(obfSlash)))) {
                return true;
            }
        }
        return false;
    }

    // ============================================================
    // Bytecode transform delegators & implementation
    // ============================================================
    public byte[] transform(String className, byte[] bytes) {
        if (className == null || bytes == null || bytes.length == 0) {
            return bytes;
        }

        // 1. Game class transformations
        if ("fgo".equals(className)) {
            return transformMinecraft(bytes);
        } else if ("fof".equals(className)) {
            return transformTitleScreen(bytes);
        } else if ("fod".equals(className)) {
            return transformScreen(bytes);
        } else if ("frh".equals(className)) {
            return transformOptionsSubScreen(bytes);
        } else if ("fny".equals(className)) {
            return transformPauseScreen(bytes);
        } else if ("net.minecraft.client.ClientBrandRetriever".equals(className)) {
            return transformClientBrandRetriever(bytes);
        } else if ("fik".equals(className)) {
            return transformAbstractWidget(bytes);
        } else if ("fgs".equals(className)) {
            return transformOptions(bytes);
        } else if ("akr".equals(className)) {
            return transformResourceLocation(bytes);
        } else if ("dqj$a".equals(className)) {
            return transformBlockEntityTypeSupplier(bytes);
        } else if ("crc".equals(className)) {
            return transformMenuType(bytes);
        } else if ("crc$a".equals(className)) {
            return transformMenuSupplier(bytes);
        } else if ("cew".equals(className)) {
            return transformPoiTypes(bytes);
        } else if ("net.blay09.mods.balm.mixin.PoiTypesAccessor".equals(className)) {
            return transformPoiTypesAccessor(bytes);
        } else if ("lt".equals(className)) {
            return transformBuiltInRegistries(bytes);
        } else if ("fhy".equals(className)) {
            return transformGui(bytes);
        } else if ("cuq".equals(className)) {
            return transformItemStack(bytes);
        } else if ("cul".equals(className)) {
            return transformItemClass(bytes);
        } else if ("dtb".equals(className)) {
            return transformBlockBehaviourClass(bytes);
        } else if ("dfy".equals(className)) {
            return transformBlockClass(bytes);
        } else if ("atp".equals(className)) {
            return transformPackRepository(bytes);
        } else if ("net.minecraft.server.MinecraftServer".equals(className)) {
            return transformMinecraftServer(bytes);
        } else if ("aqu".equals(className)) {
            return transformServerLevel(bytes);
        } else if ("net.blay09.mods.balm.fabric.client.rendering.FabricBalmModels".equals(className)) {
            return transformFabricBalmModels(bytes);
        } else if ("ghb".equals(className)) {
            return transformBlockEntityRenderers(bytes);
        } else if ("fyg".equals(className)) {
            return transformEntityModelSet(bytes);
        } else if ("gkk".equals(className)) {
            return transformEntityRenderers(bytes);
        } else if ("geu".equals(className)) {
            return transformItemBlockRenderTypes(bytes);
        } else if ("grg".equals(className)) {
            return transformIndexedAssetSource(bytes);
        } else if ("fzc".equals(className)) {
            return transformClientCommonPacketListenerImpl(bytes);
        } else if ("arr".equals(className)) {
            return transformServerCommonPacketListenerImpl(bytes);
        } else if ("fwg".equals(className)) {
            return transformModel(bytes);
        } else if ("fyk".equals(className)) {
            return transformModelPart(bytes);
        } else if ("fih".equals(className) || "fji".equals(className)) {
            return transformSelectionList(className, bytes);
        }

        // 2. Mod class redirections (only transform classes that are not in minecraft packages, except CreativeModeTab)
        boolean isCreativeModeTab = "cta".equals(className) || "net.minecraft.world.item.CreativeModeTab".equals(className);
        if (isCreativeModeTab || (!className.startsWith("net.minecraft.") && !className.startsWith("com.mojang.") && !className.startsWith("net.chainloader.loader.compat.") && className.indexOf('.') != -1)) {
            return applyModRedirections(className, bytes);
        }

        return bytes;
    }

    private byte[] transformItemClass(byte[] bytes) {
        try {
            ClassReader cr = new ClassReader(bytes);
            ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_FRAMES);
            ClassVisitor cv = new ClassVisitor(Opcodes.ASM9, cw) {
                @Override
                public void visitEnd() {
                    // Inject appendHoverText_legacy(ItemStack, Level, List, TooltipFlag)
                    MethodVisitor mv = super.visitMethod(Opcodes.ACC_PUBLIC, "appendHoverText_legacy", "(Lcuq;Ldcw;Ljava/util/List;Lcwm;)V", "Ljava/util/List<Lwz;>;", null);
                    mv.visitCode();
                    mv.visitVarInsn(Opcodes.ALOAD, 0); // this
                    mv.visitVarInsn(Opcodes.ALOAD, 1); // ItemStack
                    
                    // Convert Level to TooltipContext using TooltipContext.of(level) or TooltipContext.EMPTY
                    mv.visitVarInsn(Opcodes.ALOAD, 2); // Level
                    org.objectweb.asm.Label labelEmpty = new org.objectweb.asm.Label();
                    org.objectweb.asm.Label labelCall = new org.objectweb.asm.Label();
                    mv.visitJumpInsn(Opcodes.IFNULL, labelEmpty);
                    mv.visitVarInsn(Opcodes.ALOAD, 2);
                    mv.visitMethodInsn(Opcodes.INVOKESTATIC, "cul$b", "a", "(Ldcw;)Lcul$b;", false);
                    mv.visitJumpInsn(Opcodes.GOTO, labelCall);
                    mv.visitLabel(labelEmpty);
                    mv.visitFieldInsn(Opcodes.GETSTATIC, "cul$b", "a", "Lcul$b;");
                    mv.visitLabel(labelCall);
                    
                    mv.visitVarInsn(Opcodes.ALOAD, 3); // List
                    mv.visitVarInsn(Opcodes.ALOAD, 4); // TooltipFlag
                    // Call the new 1.21.1 appendHoverText method non-virtually: void a(ItemStack, TooltipContext, List, TooltipFlag)
                    mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "cul", "a", "(Lcuq;Lcul$b;Ljava/util/List;Lcwm;)V", false);
                    mv.visitInsn(Opcodes.RETURN);
                    mv.visitMaxs(5, 5);
                    mv.visitEnd();
                    
                    super.visitEnd();
                }
            };
            cr.accept(cv, 0);
            return cw.toByteArray();
        } catch (Exception e) {
            e.printStackTrace();
            return bytes;
        }
    }

    private byte[] transformBlockClass(byte[] bytes) {
        try {
            ClassReader cr = new ClassReader(bytes);
            ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_FRAMES);
            ClassVisitor cv = new ClassVisitor(Opcodes.ASM9, cw) {
                @Override
                public void visitEnd() {
                    // Inject appendHoverText_legacy(ItemStack, BlockGetter, List, TooltipFlag)
                    MethodVisitor mv = super.visitMethod(Opcodes.ACC_PUBLIC, "appendHoverText_legacy", "(Lcuq;Ldcc;Ljava/util/List;Lcwm;)V", "Ljava/util/List<Lwz;>;", null);
                    mv.visitCode();
                    mv.visitVarInsn(Opcodes.ALOAD, 0); // this
                    mv.visitVarInsn(Opcodes.ALOAD, 1); // ItemStack
                    
                    // Check if BlockGetter is an instance of Level
                    mv.visitVarInsn(Opcodes.ALOAD, 2); // BlockGetter
                    org.objectweb.asm.Label labelEmpty = new org.objectweb.asm.Label();
                    org.objectweb.asm.Label labelCall = new org.objectweb.asm.Label();
                    mv.visitTypeInsn(Opcodes.INSTANCEOF, "dcw"); // Level
                    mv.visitJumpInsn(Opcodes.IFEQ, labelEmpty);
                    mv.visitVarInsn(Opcodes.ALOAD, 2);
                    mv.visitTypeInsn(Opcodes.CHECKCAST, "dcw");
                    mv.visitMethodInsn(Opcodes.INVOKESTATIC, "cul$b", "a", "(Ldcw;)Lcul$b;", false);
                    mv.visitJumpInsn(Opcodes.GOTO, labelCall);
                    mv.visitLabel(labelEmpty);
                    mv.visitFieldInsn(Opcodes.GETSTATIC, "cul$b", "a", "Lcul$b;");
                    mv.visitLabel(labelCall);
                    
                    mv.visitVarInsn(Opcodes.ALOAD, 3); // List
                    mv.visitVarInsn(Opcodes.ALOAD, 4); // TooltipFlag
                    // Call the new 1.21.1 appendHoverText method non-virtually: void a(ItemStack, TooltipContext, List, TooltipFlag)
                    mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "dfy", "a", "(Lcuq;Lcul$b;Ljava/util/List;Lcwm;)V", false);
                    mv.visitInsn(Opcodes.RETURN);
                    mv.visitMaxs(5, 5);
                    mv.visitEnd();
                    
                    super.visitEnd();
                }
            };
            cr.accept(cv, 0);
            return cw.toByteArray();
        } catch (Exception e) {
            e.printStackTrace();
            return bytes;
        }
    }

    private byte[] transformBlockBehaviourClass(byte[] bytes) {
        try {
            ClassReader cr = new ClassReader(bytes);
            ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_FRAMES);
            ClassVisitor cv = new ClassVisitor(Opcodes.ASM9, cw) {
                @Override
                public void visitEnd() {
                    // Inject use_legacy(BlockState, Level, BlockPos, Player, InteractionHand, BlockHitResult)
                    MethodVisitor mv = super.visitMethod(Opcodes.ACC_PUBLIC, "use_legacy", "(Ldtc;Ldcw;Ljd;Lcmx;Lbqq;Lewy;)Lbqr;", null, null);
                    mv.visitCode();
                    mv.visitVarInsn(Opcodes.ALOAD, 0); // this
                    mv.visitVarInsn(Opcodes.ALOAD, 1); // BlockState
                    mv.visitVarInsn(Opcodes.ALOAD, 2); // Level
                    mv.visitVarInsn(Opcodes.ALOAD, 3); // BlockPos
                    mv.visitVarInsn(Opcodes.ALOAD, 4); // Player
                    mv.visitVarInsn(Opcodes.ALOAD, 6); // BlockHitResult (index 6!)
                    // Call new useWithoutItem: InteractionResult a(BlockState, Level, BlockPos, Player, BlockHitResult)
                    mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "dtb", "a", "(Ldtc;Ldcw;Ljd;Lcmx;Lewy;)Lbqr;", false);
                    mv.visitInsn(Opcodes.ARETURN);
                    mv.visitMaxs(6, 7);
                    mv.visitEnd();
                    
                    super.visitEnd();
                }
            };
            cr.accept(cv, 0);
            return cw.toByteArray();
        } catch (Exception e) {
            e.printStackTrace();
            return bytes;
        }
    }

    private byte[] transformTitleScreen(byte[] bytes) {
        System.out.println("[Chainlink 1.21.1] Transforming TitleScreen (fof)...");
        try {
            ClassReader cr = new ClassReader(bytes);
            ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_FRAMES);
            ClassVisitor cv = new ClassVisitor(Opcodes.ASM9, cw) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                    if ("aT_".equals(name) && "()V".equals(descriptor)) {
                        return new MethodVisitor(Opcodes.ASM9, mv) {
                            @Override
                            public void visitInsn(int opcode) {
                                if (opcode == Opcodes.RETURN) {
                                    mv.visitVarInsn(Opcodes.ALOAD, 0);
                                    mv.visitMethodInsn(Opcodes.INVOKESTATIC, 
                                        "net/chainloader/loader/core/gui/MainMenuHelper", 
                                        "onInitTitleScreen", 
                                        "(Lfof;)V", 
                                        false);
                                }
                                super.visitInsn(opcode);
                            }
                        };
                    } else if ("a".equals(name) && "(Lfhz;IIF)V".equals(descriptor)) {
                        return new MethodVisitor(Opcodes.ASM9, mv) {
                            @Override
                            public void visitInsn(int opcode) {
                                if (opcode == Opcodes.RETURN) {
                                    mv.visitVarInsn(Opcodes.ALOAD, 0);
                                    mv.visitVarInsn(Opcodes.ALOAD, 1);
                                    mv.visitMethodInsn(Opcodes.INVOKESTATIC, 
                                        "net/chainloader/loader/core/gui/MainMenuHelper", 
                                        "onRenderTitleScreen", 
                                        "(Lfof;Lfhz;)V", 
                                        false);
                                }
                                super.visitInsn(opcode);
                            }
                        };
                    }
                    return mv;
                }
            };
            cr.accept(cv, 0);
            return cw.toByteArray();
        } catch (Exception e) {
            e.printStackTrace();
            return bytes;
        }
    }

    private byte[] transformMinecraft(byte[] bytes) {
        System.out.println("[Chainlink 1.21.1] Transforming Minecraft (fgo)...");
        try {
            ClassReader cr = new ClassReader(bytes);
            ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_FRAMES);
            ClassVisitor cv = new ClassVisitor(Opcodes.ASM9, cw) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                    if ("a".equals(name) && "(Lfod;)V".equals(descriptor)) {
                        return new MethodVisitor(Opcodes.ASM9, mv) {
                            @Override
                            public void visitCode() {
                                super.visitCode();
                                mv.visitVarInsn(Opcodes.ALOAD, 1);
                                mv.visitMethodInsn(Opcodes.INVOKESTATIC, 
                                    "net/chainloader/loader/core/gui/MainMenuHelper", 
                                    "interceptSetScreen", 
                                    "(Lfod;)Lfod;", 
                                    false);
                                mv.visitVarInsn(Opcodes.ASTORE, 1);
                            }
                        };
                    } else if ("<init>".equals(name) && "(Lfua;)V".equals(descriptor)) {
                        return new MethodVisitor(Opcodes.ASM9, mv) {
                            @Override
                            public void visitInsn(int opcode) {
                                if (opcode == Opcodes.RETURN) {
                                    mv.visitVarInsn(Opcodes.ALOAD, 0);
                                    mv.visitMethodInsn(Opcodes.INVOKESTATIC, 
                                        "net/chainloader/loader/compat/bridge/EventBridgeHelper", 
                                        "onMinecraftInit", 
                                        "(Ljava/lang/Object;)V", 
                                        false);
                                }
                                super.visitInsn(opcode);
                            }
                        };
                    }
                    return mv;
                }
            };
            cr.accept(cv, 0);
            return cw.toByteArray();
        } catch (Exception e) {
            e.printStackTrace();
            return bytes;
        }
    }

    private byte[] transformPauseScreen(byte[] bytes) {
        try {
            ClassReader cr = new ClassReader(bytes);
            ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_FRAMES);
            ClassVisitor cv = new ClassVisitor(Opcodes.ASM9, cw) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                    if ("aT_".equals(name) && "()V".equals(descriptor)) {
                        return new MethodVisitor(Opcodes.ASM9, mv) {
                            @Override
                            public void visitInsn(int opcode) {
                                if (opcode == Opcodes.RETURN) {
                                    mv.visitVarInsn(Opcodes.ALOAD, 0);
                                    mv.visitMethodInsn(Opcodes.INVOKESTATIC, 
                                        "net/chainloader/loader/core/gui/MainMenuHelper", 
                                        "onInitPauseScreen", 
                                        "(Lfny;)V", 
                                        false);
                                }
                                super.visitInsn(opcode);
                            }
                        };
                    }
                    return mv;
                }
            };
            cr.accept(cv, 0);
            return cw.toByteArray();
        } catch (Exception e) {
            e.printStackTrace();
            return bytes;
        }
    }

    private byte[] transformClientBrandRetriever(byte[] bytes) {
        try {
            ClassReader cr = new ClassReader(bytes);
            ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_MAXS);
            ClassVisitor cv = new ClassVisitor(Opcodes.ASM9, cw) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    if ("getClientModName".equals(name) && "()Ljava/lang/String;".equals(descriptor)) {
                        MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                        mv.visitCode();
                        mv.visitLdcInsn("ChainLoader");
                        mv.visitInsn(Opcodes.ARETURN);
                        mv.visitMaxs(1, 0);
                        mv.visitEnd();
                        return null;
                    }
                    return super.visitMethod(access, name, descriptor, signature, exceptions);
                }
            };
            cr.accept(cv, 0);
            return cw.toByteArray();
        } catch (Exception e) {
            e.printStackTrace();
            return bytes;
        }
    }

    private byte[] transformBuiltInRegistries(byte[] bytes) {
        try {
            ClassReader cr = new ClassReader(bytes);
            ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_FRAMES);
            ClassVisitor cv = new ClassVisitor(Opcodes.ASM9, cw) {
                @Override
                public org.objectweb.asm.FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
                    if ("aD".equals(name)) {
                        access = (access & ~Opcodes.ACC_PRIVATE & ~Opcodes.ACC_PROTECTED) | Opcodes.ACC_PUBLIC;
                    }
                    return super.visitField(access, name, descriptor, signature, value);
                }

                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                    if ("a".equals(name) && "()V".equals(descriptor)) {
                        return new MethodVisitor(Opcodes.ASM9, mv) {
                            @Override
                            public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
                                if (opcode == Opcodes.INVOKESTATIC && "lt".equals(owner) && "c".equals(name) && "()V".equals(descriptor)) {
                                    mv.visitMethodInsn(Opcodes.INVOKESTATIC, 
                                        "java/lang/Thread", 
                                        "currentThread", 
                                        "()Ljava/lang/Thread;", 
                                        false);
                                    mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, 
                                        "java/lang/Thread", 
                                        "getContextClassLoader", 
                                        "()Ljava/lang/ClassLoader;", 
                                        false);
                                    mv.visitMethodInsn(Opcodes.INVOKESTATIC, 
                                        "net/chainloader/loader/core/ModScanner", 
                                        "initializeMods", 
                                        "(Ljava/lang/ClassLoader;)V", 
                                        false);
                                }
                                super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                            }
                        };
                    }
                    return mv;
                }
            };
            cr.accept(cv, 0);
            return cw.toByteArray();
        } catch (Exception e) {
            e.printStackTrace();
            return bytes;
        }
    }

    private byte[] transformScreen(byte[] bytes) {
        try {
            ClassReader cr = new ClassReader(bytes);
            ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_FRAMES);
            ClassVisitor cv = new ClassVisitor(Opcodes.ASM9, cw) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    if ("c".equals(name) && "(Lfki;)Lfki;".equals(descriptor)) {
                        access = (access & ~Opcodes.ACC_PRIVATE & ~Opcodes.ACC_PROTECTED) | Opcodes.ACC_PUBLIC;
                    }
                    MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                    if ("b".equals(name) && "(Lfgo;II)V".equals(descriptor)) {
                        return new MethodVisitor(Opcodes.ASM9, mv) {
                            @Override
                            public void visitCode() {
                                super.visitCode();
                                mv.visitVarInsn(Opcodes.ALOAD, 0);
                                mv.visitMethodInsn(Opcodes.INVOKESTATIC, 
                                    "net/chainloader/loader/compat/bridge/EventBridgeHelper", 
                                    "onScreenInitPre", 
                                    "(Lfod;)V", 
                                    false);
                            }

                            @Override
                            public void visitInsn(int opcode) {
                                if (opcode == Opcodes.RETURN) {
                                    mv.visitVarInsn(Opcodes.ALOAD, 0);
                                    mv.visitMethodInsn(Opcodes.INVOKESTATIC, 
                                        "net/chainloader/loader/compat/bridge/EventBridgeHelper", 
                                        "onScreenInitPost", 
                                        "(Lfod;)V", 
                                        false);
                                }
                                super.visitInsn(opcode);
                            }
                        };
                    } else if ("a".equals(name) && "(Lfhz;IIF)V".equals(descriptor)) {
                        return new MethodVisitor(Opcodes.ASM9, mv) {
                            @Override
                            public void visitCode() {
                                super.visitCode();
                                mv.visitVarInsn(Opcodes.ALOAD, 0);
                                mv.visitVarInsn(Opcodes.ALOAD, 1);
                                mv.visitVarInsn(Opcodes.ILOAD, 2);
                                mv.visitVarInsn(Opcodes.ILOAD, 3);
                                mv.visitVarInsn(Opcodes.FLOAD, 4);
                                mv.visitMethodInsn(Opcodes.INVOKESTATIC, 
                                    "net/chainloader/loader/compat/bridge/EventBridgeHelper", 
                                    "onScreenRenderPre", 
                                    "(Lfod;Lfhz;IIF)V", 
                                    false);
                            }

                            @Override
                            public void visitInsn(int opcode) {
                                if (opcode == Opcodes.RETURN) {
                                    mv.visitVarInsn(Opcodes.ALOAD, 0);
                                    mv.visitVarInsn(Opcodes.ALOAD, 1);
                                    mv.visitVarInsn(Opcodes.ILOAD, 2);
                                    mv.visitVarInsn(Opcodes.ILOAD, 3);
                                    mv.visitVarInsn(Opcodes.FLOAD, 4);
                                    mv.visitMethodInsn(Opcodes.INVOKESTATIC, 
                                        "net/chainloader/loader/compat/bridge/EventBridgeHelper", 
                                        "onScreenRenderPost", 
                                        "(Lfod;Lfhz;IIF)V", 
                                        false);
                                }
                                super.visitInsn(opcode);
                            }
                        };
                    } else if ("a".equals(name) && "(III)Z".equals(descriptor)) {
                        return new MethodVisitor(Opcodes.ASM9, mv) {
                            @Override
                            public void visitCode() {
                                super.visitCode();
                                org.objectweb.asm.Label label = new org.objectweb.asm.Label();
                                mv.visitVarInsn(Opcodes.ALOAD, 0);
                                mv.visitVarInsn(Opcodes.ILOAD, 1);
                                mv.visitVarInsn(Opcodes.ILOAD, 2);
                                mv.visitVarInsn(Opcodes.ILOAD, 3);
                                mv.visitMethodInsn(Opcodes.INVOKESTATIC, 
                                    "net/chainloader/loader/compat/bridge/EventBridgeHelper", 
                                    "onScreenKeyPressedPre", 
                                    "(Lfod;III)Z", 
                                    false);
                                mv.visitJumpInsn(Opcodes.IFEQ, label);
                                mv.visitInsn(Opcodes.ICONST_1);
                                mv.visitInsn(Opcodes.IRETURN);
                                mv.visitLabel(label);
                            }
                        };
                    } else if ("c".equals(name) && "(III)Z".equals(descriptor)) {
                        return new MethodVisitor(Opcodes.ASM9, mv) {
                            @Override
                            public void visitCode() {
                                super.visitCode();
                                org.objectweb.asm.Label label = new org.objectweb.asm.Label();
                                mv.visitVarInsn(Opcodes.ALOAD, 0);
                                mv.visitVarInsn(Opcodes.ILOAD, 1);
                                mv.visitVarInsn(Opcodes.ILOAD, 2);
                                mv.visitVarInsn(Opcodes.ILOAD, 3);
                                mv.visitMethodInsn(Opcodes.INVOKESTATIC, 
                                    "net/chainloader/loader/compat/bridge/EventBridgeHelper", 
                                    "onScreenKeyReleasedPre", 
                                    "(Lfod;III)Z", 
                                    false);
                                mv.visitJumpInsn(Opcodes.IFEQ, label);
                                mv.visitInsn(Opcodes.ICONST_1);
                                mv.visitInsn(Opcodes.IRETURN);
                                mv.visitLabel(label);
                            }
                        };
                    } else if ("a".equals(name) && "(DDI)Z".equals(descriptor)) {
                        return new MethodVisitor(Opcodes.ASM9, mv) {
                            @Override
                            public void visitCode() {
                                super.visitCode();
                                org.objectweb.asm.Label label = new org.objectweb.asm.Label();
                                mv.visitVarInsn(Opcodes.ALOAD, 0);
                                mv.visitVarInsn(Opcodes.DLOAD, 1);
                                mv.visitVarInsn(Opcodes.DLOAD, 3);
                                mv.visitVarInsn(Opcodes.ILOAD, 5);
                                mv.visitMethodInsn(Opcodes.INVOKESTATIC, 
                                    "net/chainloader/loader/compat/bridge/EventBridgeHelper", 
                                    "onScreenMouseClickedPre", 
                                    "(Lfod;DDI)Z", 
                                    false);
                                mv.visitJumpInsn(Opcodes.IFEQ, label);
                                mv.visitInsn(Opcodes.ICONST_1);
                                mv.visitInsn(Opcodes.IRETURN);
                                mv.visitLabel(label);
                            }
                        };
                    }
                    return mv;
                }

                @Override
                public org.objectweb.asm.FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
                    if ("o".equals(name) || "m".equals(name) || "n".equals(name) || "l".equals(name) || "v".equals(name) || "r".equals(name)) {
                        access = (access & ~Opcodes.ACC_PRIVATE & ~Opcodes.ACC_PROTECTED) | Opcodes.ACC_PUBLIC;
                    }
                    return super.visitField(access, name, descriptor, signature, value);
                }

                @Override
                public void visitEnd() {
                    MethodVisitor mv = super.visitMethod(Opcodes.ACC_PUBLIC, "getMinecraft", "()Lfgo;", null, null);
                    if (mv != null) {
                        mv.visitCode();
                        mv.visitVarInsn(Opcodes.ALOAD, 0);
                        mv.visitFieldInsn(Opcodes.GETFIELD, "fod", "l", "Lfgo;");
                        mv.visitInsn(Opcodes.ARETURN);
                        mv.visitMaxs(1, 1);
                        mv.visitEnd();
                    }
                    super.visitEnd();
                }
            };
            cr.accept(cv, 0);
            return cw.toByteArray();
        } catch (Exception e) {
            e.printStackTrace();
            return bytes;
        }
    }

    private byte[] transformOptionsSubScreen(byte[] bytes) {
        try {
            ClassReader cr = new ClassReader(bytes);
            ClassWriter cw = new ClassWriter(cr, 0);
            ClassVisitor cv = new ClassVisitor(Opcodes.ASM9, cw) {
                @Override
                public org.objectweb.asm.FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
                    if ("b".equals(name) || "c".equals(name)) {
                        access = (access & ~Opcodes.ACC_PRIVATE & ~Opcodes.ACC_PROTECTED) | Opcodes.ACC_PUBLIC;
                    }
                    return super.visitField(access, name, descriptor, signature, value);
                }
            };
            cr.accept(cv, 0);
            return cw.toByteArray();
        } catch (Exception e) {
            e.printStackTrace();
            return bytes;
        }
    }

    private byte[] transformAbstractWidget(byte[] bytes) {
        try {
            ClassReader cr = new ClassReader(bytes);
            ClassWriter cw = new ClassWriter(cr, 0);
            ClassVisitor cv = new ClassVisitor(Opcodes.ASM9, cw) {
                @Override
                public org.objectweb.asm.FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
                    if ("c".equals(name) || "d".equals(name) || "g".equals(name) || "h".equals(name) || "e".equals(name)) {
                        access = (access & ~Opcodes.ACC_PRIVATE & ~Opcodes.ACC_PROTECTED) | Opcodes.ACC_PUBLIC;
                    }
                    return super.visitField(access, name, descriptor, signature, value);
                }

                @Override
                public org.objectweb.asm.MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    if ("a".equals(name) && "(Lfhz;IIF)V".equals(descriptor)) {
                        access = access & ~Opcodes.ACC_FINAL;
                    }
                    return super.visitMethod(access, name, descriptor, signature, exceptions);
                }
            };
            cr.accept(cv, 0);
            return cw.toByteArray();
        } catch (Exception e) {
            e.printStackTrace();
            return bytes;
        }
    }

    private byte[] transformOptions(byte[] bytes) {
        try {
            ClassReader cr = new ClassReader(bytes);
            ClassWriter cw = new ClassWriter(cr, 0);
            ClassVisitor cv = new ClassVisitor(Opcodes.ASM9, cw) {
                @Override
                public org.objectweb.asm.FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
                    access = (access & ~Opcodes.ACC_PRIVATE & ~Opcodes.ACC_PROTECTED) | Opcodes.ACC_PUBLIC;
                    return super.visitField(access, name, descriptor, signature, value);
                }
            };
            cr.accept(cv, 0);
            return cw.toByteArray();
        } catch (Exception e) {
            e.printStackTrace();
            return bytes;
        }
    }

    private byte[] transformResourceLocation(byte[] bytes) {
        try {
            ClassReader cr = new ClassReader(bytes);
            ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_FRAMES);
            ClassVisitor cv = new ClassVisitor(Opcodes.ASM9, cw) {
                private boolean hasSingleStringConstructor = false;

                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    if ("<init>".equals(name)) {
                        access = (access & ~Opcodes.ACC_PRIVATE & ~Opcodes.ACC_PROTECTED) | Opcodes.ACC_PUBLIC;
                        if ("(Ljava/lang/String;)V".equals(descriptor)) {
                            hasSingleStringConstructor = true;
                        }
                    }
                    return super.visitMethod(access, name, descriptor, signature, exceptions);
                }

                @Override
                public void visitEnd() {
                    if (!hasSingleStringConstructor) {
                        MethodVisitor mv = super.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "(Ljava/lang/String;)V", null, null);
                        mv.visitCode();
                        mv.visitVarInsn(Opcodes.ALOAD, 0);
                        mv.visitVarInsn(Opcodes.ALOAD, 1);
                        mv.visitMethodInsn(Opcodes.INVOKESTATIC, "net/chainloader/loader/compat/bridge/EventBridgeHelper", "getResourceLocationNamespace", "(Ljava/lang/String;)Ljava/lang/String;", false);
                        mv.visitVarInsn(Opcodes.ALOAD, 1);
                        mv.visitMethodInsn(Opcodes.INVOKESTATIC, "net/chainloader/loader/compat/bridge/EventBridgeHelper", "getResourceLocationPath", "(Ljava/lang/String;)Ljava/lang/String;", false);
                        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "akr", "<init>", "(Ljava/lang/String;Ljava/lang/String;)V", false);
                        mv.visitInsn(Opcodes.RETURN);
                        mv.visitMaxs(3, 2);
                        mv.visitEnd();
                    }
                    super.visitEnd();
                }
            };
            cr.accept(cv, 0);
            return cw.toByteArray();
        } catch (Exception e) {
            e.printStackTrace();
            return bytes;
        }
    }

    private byte[] transformBlockEntityTypeSupplier(byte[] bytes) {
        try {
            ClassReader cr = new ClassReader(bytes);
            ClassWriter cw = new ClassWriter(cr, 0);
            ClassVisitor cv = new ClassVisitor(Opcodes.ASM9, cw) {
                @Override
                public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
                    access = (access & ~Opcodes.ACC_PRIVATE & ~Opcodes.ACC_PROTECTED) | Opcodes.ACC_PUBLIC;
                    super.visit(version, access, name, signature, superName, interfaces);
                }
            };
            cr.accept(cv, 0);
            return cw.toByteArray();
        } catch (Exception e) {
            e.printStackTrace();
            return bytes;
        }
    }

    private byte[] transformMenuSupplier(byte[] bytes) {
        try {
            ClassReader cr = new ClassReader(bytes);
            ClassWriter cw = new ClassWriter(cr, 0);
            ClassVisitor cv = new ClassVisitor(Opcodes.ASM9, cw) {
                @Override
                public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
                    access = (access & ~Opcodes.ACC_PRIVATE & ~Opcodes.ACC_PROTECTED) | Opcodes.ACC_PUBLIC;
                    super.visit(version, access, name, signature, superName, interfaces);
                }
                @Override
                public void visitInnerClass(String name, String outerName, String innerName, int innerAccess) {
                    if ("crc$a".equals(name)) {
                        innerAccess = (innerAccess & ~Opcodes.ACC_PRIVATE & ~Opcodes.ACC_PROTECTED) | Opcodes.ACC_PUBLIC;
                    }
                    super.visitInnerClass(name, outerName, innerName, innerAccess);
                }
            };
            cr.accept(cv, 0);
            return cw.toByteArray();
        } catch (Exception e) {
            e.printStackTrace();
            return bytes;
        }
    }

    private byte[] transformMenuType(byte[] bytes) {
        try {
            ClassReader cr = new ClassReader(bytes);
            ClassWriter cw = new ClassWriter(cr, 0);
            ClassVisitor cv = new ClassVisitor(Opcodes.ASM9, cw) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    if ("<init>".equals(name)) {
                        access = (access & ~Opcodes.ACC_PRIVATE & ~Opcodes.ACC_PROTECTED) | Opcodes.ACC_PUBLIC;
                    }
                    return super.visitMethod(access, name, descriptor, signature, exceptions);
                }
                @Override
                public void visitInnerClass(String name, String outerName, String innerName, int innerAccess) {
                    if ("crc$a".equals(name)) {
                        innerAccess = (innerAccess & ~Opcodes.ACC_PRIVATE & ~Opcodes.ACC_PROTECTED) | Opcodes.ACC_PUBLIC;
                    }
                    super.visitInnerClass(name, outerName, innerName, innerAccess);
                }
            };
            cr.accept(cv, 0);
            return cw.toByteArray();
        } catch (Exception e) {
            e.printStackTrace();
            return bytes;
        }
    }

    private byte[] transformPoiTypes(byte[] bytes) {
        try {
            ClassReader cr = new ClassReader(bytes);
            ClassWriter cw = new ClassWriter(cr, 0);
            ClassVisitor cv = new ClassVisitor(Opcodes.ASM9, cw) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    if ("a".equals(name) && "(Ljm;Ljava/util/Set;)V".equals(descriptor)) {
                        access = (access & ~Opcodes.ACC_PRIVATE & ~Opcodes.ACC_PROTECTED) | Opcodes.ACC_PUBLIC;
                    }
                    return super.visitMethod(access, name, descriptor, signature, exceptions);
                }
            };
            cr.accept(cv, 0);
            return cw.toByteArray();
        } catch (Exception e) {
            e.printStackTrace();
            return bytes;
        }
    }

    private byte[] transformPoiTypesAccessor(byte[] bytes) {
        try {
            ClassReader cr = new ClassReader(bytes);
            ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
            ClassVisitor cv = new ClassVisitor(Opcodes.ASM9, cw) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    if ("callRegisterBlockStates".equals(name)) {
                        MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                        mv.visitCode();
                        mv.visitVarInsn(Opcodes.ALOAD, 0);
                        mv.visitVarInsn(Opcodes.ALOAD, 1);
                        mv.visitMethodInsn(Opcodes.INVOKESTATIC, "cew", "a", descriptor, false);
                        mv.visitInsn(Opcodes.RETURN);
                        mv.visitMaxs(2, 2);
                        mv.visitEnd();
                        return new MethodVisitor(Opcodes.ASM9) {};
                    }
                    return super.visitMethod(access, name, descriptor, signature, exceptions);
                }
            };
            cr.accept(cv, 0);
            return cw.toByteArray();
        } catch (Exception e) {
            e.printStackTrace();
            return bytes;
        }
    }

    private byte[] transformGui(byte[] bytes) {
        try {
            ClassReader cr = new ClassReader(bytes);
            ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_FRAMES);
            ClassVisitor cv = new ClassVisitor(Opcodes.ASM9, cw) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                    if ("l".equals(name) && "(Lfhz;Lfgf;)V".equals(descriptor)) {
                        return new MethodVisitor(Opcodes.ASM9, mv) {
                            @Override
                            public void visitInsn(int opcode) {
                                if (opcode == Opcodes.RETURN) {
                                    mv.visitVarInsn(Opcodes.ALOAD, 1);
                                    mv.visitVarInsn(Opcodes.ALOAD, 2);
                                    mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, 
                                        "fgf", 
                                        "a", 
                                        "()F", 
                                        true);
                                    mv.visitMethodInsn(Opcodes.INVOKESTATIC, 
                                        "net/chainloader/loader/compat/bridge/EventBridgeHelper", 
                                        "onRenderGuiOverlays", 
                                        "(Lfhz;F)V", 
                                        false);
                                }
                                super.visitInsn(opcode);
                            }
                        };
                    }
                    if ("<init>".equals(name) && "(Lfgo;)V".equals(descriptor)) {
                        return new MethodVisitor(Opcodes.ASM9, mv) {
                            @Override
                            public void visitInsn(int opcode) {
                                if (opcode == Opcodes.RETURN) {
                                    mv.visitVarInsn(Opcodes.ALOAD, 0);
                                    mv.visitIntInsn(Opcodes.BIPUSH, 39);
                                    mv.visitFieldInsn(Opcodes.PUTFIELD, "fhy", "leftHeight", "I");
                                    mv.visitVarInsn(Opcodes.ALOAD, 0);
                                    mv.visitIntInsn(Opcodes.BIPUSH, 39);
                                    mv.visitFieldInsn(Opcodes.PUTFIELD, "fhy", "rightHeight", "I");
                                }
                                super.visitInsn(opcode);
                            }
                        };
                    }
                    return mv;
                }

                @Override
                public void visitEnd() {
                    org.objectweb.asm.FieldVisitor fv1 = cv.visitField(Opcodes.ACC_PUBLIC, "leftHeight", "I", null, null);
                    if (fv1 != null) {
                        fv1.visitEnd();
                    }
                    org.objectweb.asm.FieldVisitor fv2 = cv.visitField(Opcodes.ACC_PUBLIC, "rightHeight", "I", null, null);
                    if (fv2 != null) {
                        fv2.visitEnd();
                    }
                    super.visitEnd();
                }
            };
            cr.accept(cv, 0);
            return cw.toByteArray();
        } catch (Exception e) {
            e.printStackTrace();
            return bytes;
        }
    }

    private byte[] transformItemStack(byte[] bytes) {
        try {
            ClassReader cr = new ClassReader(bytes);
            ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_FRAMES);
            ClassVisitor cv = new ClassVisitor(Opcodes.ASM9, cw) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                    if ("a".equals(name) && "(Lcul$b;Lcmx;Lcwm;)Ljava/util/List;".equals(descriptor)) {
                        return new MethodVisitor(Opcodes.ASM9, mv) {
                            @Override
                            public void visitInsn(int opcode) {
                                if (opcode == Opcodes.ARETURN) {
                                    mv.visitVarInsn(Opcodes.ASTORE, 6);
                                    mv.visitVarInsn(Opcodes.ALOAD, 0);
                                    mv.visitVarInsn(Opcodes.ALOAD, 2);
                                    mv.visitVarInsn(Opcodes.ALOAD, 6);
                                    mv.visitVarInsn(Opcodes.ALOAD, 1);
                                    mv.visitVarInsn(Opcodes.ALOAD, 3);
                                    mv.visitMethodInsn(Opcodes.INVOKESTATIC, 
                                        "net/chainloader/loader/compat/bridge/EventBridgeHelper", 
                                        "onItemTooltip", 
                                        "(Lcuq;Lcmx;Ljava/util/List;Lcul$b;Lcwm;)Ljava/util/List;", 
                                        false);
                                }
                                super.visitInsn(opcode);
                            }
                        };
                    }
                    return mv;
                }

                @Override
                public void visitEnd() {
                    // getNbt
                    {
                        MethodVisitor mv = super.visitMethod(Opcodes.ACC_PUBLIC, "method_7969", "()Lub;", null, null);
                        mv.visitCode();
                        mv.visitVarInsn(Opcodes.ALOAD, 0);
                        mv.visitMethodInsn(Opcodes.INVOKESTATIC, "net/chainloader/loader/compat/bridge/EventBridgeHelper", "getItemStackNbt", "(Ljava/lang/Object;)Ljava/lang/Object;", false);
                        mv.visitTypeInsn(Opcodes.CHECKCAST, "ub");
                        mv.visitInsn(Opcodes.ARETURN);
                        mv.visitMaxs(1, 1);
                        mv.visitEnd();
                    }
                    // hasNbt
                    {
                        MethodVisitor mv = super.visitMethod(Opcodes.ACC_PUBLIC, "method_7980", "()Z", null, null);
                        mv.visitCode();
                        mv.visitVarInsn(Opcodes.ALOAD, 0);
                        mv.visitMethodInsn(Opcodes.INVOKESTATIC, "net/chainloader/loader/compat/bridge/EventBridgeHelper", "hasNbt", "(Ljava/lang/Object;)Z", false);
                        mv.visitInsn(Opcodes.IRETURN);
                        mv.visitMaxs(1, 1);
                        mv.visitEnd();
                    }
                    // getOrCreateSubNbt
                    {
                        MethodVisitor mv = super.visitMethod(Opcodes.ACC_PUBLIC, "method_7947", "(Ljava/lang/String;)Lub;", null, null);
                        mv.visitCode();
                        mv.visitVarInsn(Opcodes.ALOAD, 0);
                        mv.visitVarInsn(Opcodes.ALOAD, 1);
                        mv.visitMethodInsn(Opcodes.INVOKESTATIC, "net/chainloader/loader/compat/bridge/EventBridgeHelper", "getOrCreateSubNbt", "(Ljava/lang/Object;Ljava/lang/String;)Ljava/lang/Object;", false);
                        mv.visitTypeInsn(Opcodes.CHECKCAST, "ub");
                        mv.visitInsn(Opcodes.ARETURN);
                        mv.visitMaxs(2, 2);
                        mv.visitEnd();
                    }
                    // fromNbt
                    {
                        MethodVisitor mv = super.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "method_7915", "(Lub;)Lcuq;", null, null);
                        mv.visitCode();
                        mv.visitVarInsn(Opcodes.ALOAD, 0);
                        mv.visitMethodInsn(Opcodes.INVOKESTATIC, "net/chainloader/loader/compat/bridge/EventBridgeHelper", "fromNbt", "(Ljava/lang/Object;)Ljava/lang/Object;", false);
                        mv.visitTypeInsn(Opcodes.CHECKCAST, "cuq");
                        mv.visitInsn(Opcodes.ARETURN);
                        mv.visitMaxs(1, 1);
                        mv.visitEnd();
                    }
                    // writeNbt
                    {
                        MethodVisitor mv = super.visitMethod(Opcodes.ACC_PUBLIC, "method_7953", "(Lub;)Lub;", null, null);
                        mv.visitCode();
                        mv.visitVarInsn(Opcodes.ALOAD, 0);
                        mv.visitVarInsn(Opcodes.ALOAD, 1);
                        mv.visitMethodInsn(Opcodes.INVOKESTATIC, "net/chainloader/loader/compat/bridge/EventBridgeHelper", "writeNbt", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", false);
                        mv.visitTypeInsn(Opcodes.CHECKCAST, "ub");
                        mv.visitInsn(Opcodes.ARETURN);
                        mv.visitMaxs(2, 2);
                        mv.visitEnd();
                    }
                    // hasTag
                    {
                        MethodVisitor mv = super.visitMethod(Opcodes.ACC_PUBLIC, "m_41782_", "()Z", null, null);
                        mv.visitCode();
                        mv.visitVarInsn(Opcodes.ALOAD, 0);
                        mv.visitMethodInsn(Opcodes.INVOKESTATIC, "net/chainloader/loader/compat/bridge/EventBridgeHelper", "hasNbt", "(Ljava/lang/Object;)Z", false);
                        mv.visitInsn(Opcodes.IRETURN);
                        mv.visitMaxs(1, 1);
                        mv.visitEnd();
                    }
                    // getTag
                    {
                        MethodVisitor mv = super.visitMethod(Opcodes.ACC_PUBLIC, "m_41783_", "()Lub;", null, null);
                        mv.visitCode();
                        mv.visitVarInsn(Opcodes.ALOAD, 0);
                        mv.visitMethodInsn(Opcodes.INVOKESTATIC, "net/chainloader/loader/compat/bridge/EventBridgeHelper", "getItemStackNbt", "(Ljava/lang/Object;)Ljava/lang/Object;", false);
                        mv.visitTypeInsn(Opcodes.CHECKCAST, "ub");
                        mv.visitInsn(Opcodes.ARETURN);
                        mv.visitMaxs(1, 1);
                        mv.visitEnd();
                    }
                    // setTag
                    {
                        MethodVisitor mv = super.visitMethod(Opcodes.ACC_PUBLIC, "m_41751_", "(Lub;)V", null, null);
                        mv.visitCode();
                        mv.visitVarInsn(Opcodes.ALOAD, 0);
                        mv.visitVarInsn(Opcodes.ALOAD, 1);
                        mv.visitMethodInsn(Opcodes.INVOKESTATIC, "net/chainloader/loader/compat/bridge/EventBridgeHelper", "setItemStackNbt", "(Ljava/lang/Object;Ljava/lang/Object;)V", false);
                        mv.visitInsn(Opcodes.RETURN);
                        mv.visitMaxs(2, 2);
                        mv.visitEnd();
                    }
                    // enchant
                    {
                        MethodVisitor mv = super.visitMethod(Opcodes.ACC_PUBLIC, "a", "(Ldac;I)V", null, null);
                        mv.visitCode();
                        mv.visitVarInsn(Opcodes.ALOAD, 0); // this
                        mv.visitVarInsn(Opcodes.ALOAD, 1); // dac (Enchantment)
                        mv.visitVarInsn(Opcodes.ILOAD, 2); // int (level)
                        mv.visitMethodInsn(Opcodes.INVOKESTATIC, "net/chainloader/loader/compat/bridge/EventBridgeHelper", "enchant", "(Lcuq;Ljava/lang/Object;I)V", false);
                        mv.visitInsn(Opcodes.RETURN);
                        mv.visitMaxs(3, 3);
                        mv.visitEnd();
                    }
                    // Inject legacy Fabric getTooltip (method_7950)
                    {
                        MethodVisitor mv = super.visitMethod(Opcodes.ACC_PUBLIC, "method_7950", "(Lcmx;Lcwm;)Ljava/util/List;", "Ljava/util/List<Lwz;>;", null);
                        mv.visitCode();
                        mv.visitVarInsn(Opcodes.ALOAD, 0); // this
                        mv.visitVarInsn(Opcodes.ALOAD, 1); // Player
                        mv.visitVarInsn(Opcodes.ALOAD, 2); // TooltipFlag
                        mv.visitMethodInsn(Opcodes.INVOKESTATIC, "net/chainloader/loader/compat/bridge/EventBridgeHelper", "getTooltipLines", "(Lcuq;Lcmx;Lcwm;)Ljava/util/List;", false);
                        mv.visitInsn(Opcodes.ARETURN);
                        mv.visitMaxs(3, 3);
                        mv.visitEnd();
                    }
                    // Inject legacy Forge getTooltip (m_41786_)
                    {
                        MethodVisitor mv = super.visitMethod(Opcodes.ACC_PUBLIC, "m_41786_", "(Lcmx;Lcwm;)Ljava/util/List;", "Ljava/util/List<Lwz;>;", null);
                        mv.visitCode();
                        mv.visitVarInsn(Opcodes.ALOAD, 0); // this
                        mv.visitVarInsn(Opcodes.ALOAD, 1); // Player
                        mv.visitVarInsn(Opcodes.ALOAD, 2); // TooltipFlag
                        mv.visitMethodInsn(Opcodes.INVOKESTATIC, "net/chainloader/loader/compat/bridge/EventBridgeHelper", "getTooltipLines", "(Lcuq;Lcmx;Lcwm;)Ljava/util/List;", false);
                        mv.visitInsn(Opcodes.ARETURN);
                        mv.visitMaxs(3, 3);
                        mv.visitEnd();
                    }
                    super.visitEnd();
                }
            };
            cr.accept(cv, 0);
            return cw.toByteArray();
        } catch (Exception e) {
            e.printStackTrace();
            return bytes;
        }
    }

    private byte[] transformFabricBalmModels(byte[] bytes) {
        try {
            ClassReader cr = new ClassReader(bytes);
            ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_FRAMES);
            ClassVisitor cv = new ClassVisitor(Opcodes.ASM9, cw) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                    if ("getUnbakedMissingModel".equals(name)) {
                        return new MethodVisitor(Opcodes.ASM9, mv) {
                            @Override
                            public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
                                if (opcode == Opcodes.GETSTATIC && "gss".equals(owner) && "m".equals(name) && "Lgsu;".equals(descriptor)) {
                                    super.visitFieldInsn(opcode, owner, name, "Lakr;");
                                } else {
                                    super.visitFieldInsn(opcode, owner, name, descriptor);
                                }
                            }
                        };
                    }
                    return mv;
                }
            };
            cr.accept(cv, 0);
            return cw.toByteArray();
        } catch (Exception e) {
            e.printStackTrace();
            return bytes;
        }
    }

    private byte[] transformBlockEntityRenderers(byte[] bytes) {
        try {
            ClassReader cr = new ClassReader(bytes);
            ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_FRAMES);
            ClassVisitor cv = new ClassVisitor(Opcodes.ASM9, cw) {
                @Override
                public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
                    int publicAccess = (access & ~Opcodes.ACC_PRIVATE & ~Opcodes.ACC_PROTECTED) | Opcodes.ACC_PUBLIC;
                    super.visit(version, publicAccess, name, signature, superName, interfaces);
                }

                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    if ("a".equals(name) && "(Ldqj;Lgha;)V".equals(descriptor)) {
                        int publicAccess = (access & ~Opcodes.ACC_PRIVATE & ~Opcodes.ACC_PROTECTED) | Opcodes.ACC_PUBLIC;
                        return super.visitMethod(publicAccess, name, descriptor, signature, exceptions);
                    }
                    return super.visitMethod(access, name, descriptor, signature, exceptions);
                }
            };
            cr.accept(cv, 0);
            return cw.toByteArray();
        } catch (Exception e) {
            e.printStackTrace();
            return bytes;
        }
    }

    private byte[] transformEntityModelSet(byte[] bytes) {
        try {
            ClassReader cr = new ClassReader(bytes);
            ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_FRAMES);
            ClassVisitor cv = new ClassVisitor(Opcodes.ASM9, cw) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                    if ("a".equals(name) && "(Laue;)V".equals(descriptor)) {
                        return new MethodVisitor(Opcodes.ASM9, mv) {
                            @Override
                            public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
                                super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                                if (opcode == Opcodes.INVOKESTATIC && "fyh".equals(owner) && "a".equals(name) && "()Ljava/util/Map;".equals(descriptor)) {
                                    mv.visitMethodInsn(Opcodes.INVOKESTATIC,
                                        "net/chainloader/loader/compat/bridge/EventBridgeHelper",
                                        "onEntityModelSetReload",
                                        "(Ljava/util/Map;)Ljava/util/Map;",
                                        false);
                                }
                            }
                        };
                    }
                    return mv;
                }
            };
            cr.accept(cv, 0);
            return cw.toByteArray();
        } catch (Exception e) {
            e.printStackTrace();
            return bytes;
        }
    }

    private byte[] transformEntityRenderers(byte[] bytes) {
        try {
            ClassReader cr = new ClassReader(bytes);
            ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_FRAMES);
            ClassVisitor cv = new ClassVisitor(Opcodes.ASM9, cw) {
                @Override
                public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
                    int publicAccess = (access & ~Opcodes.ACC_PRIVATE & ~Opcodes.ACC_PROTECTED) | Opcodes.ACC_PUBLIC;
                    super.visit(version, publicAccess, name, signature, superName, interfaces);
                }

                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    if ("a".equals(name) && "(Lbsx;Lgkj;)V".equals(descriptor)) {
                        int publicAccess = (access & ~Opcodes.ACC_PRIVATE & ~Opcodes.ACC_PROTECTED) | Opcodes.ACC_PUBLIC;
                        return super.visitMethod(publicAccess, name, descriptor, signature, exceptions);
                    }
                    return super.visitMethod(access, name, descriptor, signature, exceptions);
                }
            };
            cr.accept(cv, 0);
            return cw.toByteArray();
        } catch (Exception e) {
            e.printStackTrace();
            return bytes;
        }
    }

    private byte[] transformItemBlockRenderTypes(byte[] bytes) {
        try {
            ClassReader cr = new ClassReader(bytes);
            ClassWriter cw = new ClassWriter(cr, 0);
            ClassVisitor cv = new ClassVisitor(Opcodes.ASM9, cw) {
                @Override
                public org.objectweb.asm.FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
                    if ("a".equals(name) || "b".equals(name)) {
                        access = (access & ~Opcodes.ACC_PRIVATE & ~Opcodes.ACC_PROTECTED) | Opcodes.ACC_PUBLIC;
                    }
                    return super.visitField(access, name, descriptor, signature, value);
                }
            };
            cr.accept(cv, 0);
            return cw.toByteArray();
        } catch (Exception e) {
            e.printStackTrace();
            return bytes;
        }
    }

    private byte[] transformPackRepository(byte[] bytes) {
        try {
            ClassReader cr = new ClassReader(bytes);
            ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            ClassVisitor cv = new ClassVisitor(Opcodes.ASM9, cw) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                    if ("<init>".equals(name) && "([Latr;)V".equals(descriptor)) {
                        System.out.println("[Chainlink 1.21.1] Intercepting PackRepository constructor sources...");
                        return new MethodVisitor(Opcodes.ASM9, mv) {
                            @Override
                            public void visitVarInsn(int opcode, int varIndex) {
                                super.visitVarInsn(opcode, varIndex);
                                if (opcode == Opcodes.ALOAD && varIndex == 1) {
                                    mv.visitMethodInsn(Opcodes.INVOKESTATIC,
                                        "net/chainloader/loader/compat/bridge/EventBridgeHelper",
                                        "patchPackRepositorySources",
                                        "([Ljava/lang/Object;)[Ljava/lang/Object;",
                                        false);
                                    mv.visitTypeInsn(Opcodes.CHECKCAST, "[Latr;");
                                }
                            }
                        };
                    }
                    return mv;
                }
            };
            cr.accept(cv, 0);
            return cw.toByteArray();
        } catch (Exception e) {
            e.printStackTrace();
            return bytes;
        }
    }

    private byte[] transformMinecraftServer(byte[] bytes) {
        try {
            ClassReader cr = new ClassReader(bytes);
            ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_FRAMES);
            ClassVisitor cv = new ClassVisitor(Opcodes.ASM9, cw) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                    if ("<init>".equals(name)) {
                        return new MethodVisitor(Opcodes.ASM9, mv) {
                            @Override
                            public void visitInsn(int opcode) {
                                if (opcode == Opcodes.RETURN) {
                                    mv.visitVarInsn(Opcodes.ALOAD, 0);
                                    mv.visitMethodInsn(Opcodes.INVOKESTATIC,
                                        "net/chainloader/loader/compat/bridge/EventBridgeHelper",
                                        "setCurrentServer",
                                        "(Ljava/lang/Object;)V",
                                        false);
                                }
                                super.visitInsn(opcode);
                            }
                        };
                    } else if ("e".equals(name) && "()Z".equals(descriptor)) {
                        return new MethodVisitor(Opcodes.ASM9, mv) {
                            @Override
                            public void visitCode() {
                                super.visitCode();
                                mv.visitVarInsn(Opcodes.ALOAD, 0);
                                mv.visitMethodInsn(Opcodes.INVOKESTATIC,
                                    "net/chainloader/loader/compat/bridge/EventBridgeHelper",
                                    "onServerStarting",
                                    "(Ljava/lang/Object;)V",
                                    false);
                            }
                            
                            @Override
                            public void visitInsn(int opcode) {
                                if (opcode == Opcodes.IRETURN) {
                                    mv.visitVarInsn(Opcodes.ALOAD, 0);
                                    mv.visitMethodInsn(Opcodes.INVOKESTATIC,
                                        "net/chainloader/loader/compat/bridge/EventBridgeHelper",
                                        "onServerStarted",
                                        "(Ljava/lang/Object;)V",
                                        false);
                                }
                                super.visitInsn(opcode);
                            }
                        };
                    } else if ("v".equals(name) && "()V".equals(descriptor)) {
                        return new MethodVisitor(Opcodes.ASM9, mv) {
                            @Override
                            public void visitCode() {
                                super.visitCode();
                                mv.visitVarInsn(Opcodes.ALOAD, 0);
                                mv.visitMethodInsn(Opcodes.INVOKESTATIC,
                                    "net/chainloader/loader/compat/bridge/EventBridgeHelper",
                                    "onServerStopping",
                                    "(Ljava/lang/Object;)V",
                                    false);
                            }
                            
                            @Override
                            public void visitInsn(int opcode) {
                                if (opcode == Opcodes.RETURN) {
                                    mv.visitVarInsn(Opcodes.ALOAD, 0);
                                    mv.visitMethodInsn(Opcodes.INVOKESTATIC,
                                        "net/chainloader/loader/compat/bridge/EventBridgeHelper",
                                        "onServerStopped",
                                        "(Ljava/lang/Object;)V",
                                        false);
                                }
                                super.visitInsn(opcode);
                            }
                        };
                    }
                    return mv;
                }
            };
            cr.accept(cv, 0);
            return cw.toByteArray();
        } catch (Exception e) {
            e.printStackTrace();
            return bytes;
        }
    }

    private byte[] transformServerLevel(byte[] bytes) {
        try {
            ClassReader cr = new ClassReader(bytes);
            ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_FRAMES);
            ClassVisitor cv = new ClassVisitor(Opcodes.ASM9, cw) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                    if ("<init>".equals(name)) {
                        return new MethodVisitor(Opcodes.ASM9, mv) {
                            @Override
                            public void visitInsn(int opcode) {
                                if (opcode == Opcodes.RETURN) {
                                    mv.visitVarInsn(Opcodes.ALOAD, 0);
                                    mv.visitMethodInsn(Opcodes.INVOKESTATIC,
                                        "net/chainloader/loader/compat/bridge/EventBridgeHelper",
                                        "onLevelLoad",
                                        "(Ljava/lang/Object;)V",
                                        false);
                                }
                                super.visitInsn(opcode);
                            }
                        };
                    } else if ("close".equals(name) && "()V".equals(descriptor)) {
                        return new MethodVisitor(Opcodes.ASM9, mv) {
                            @Override
                            public void visitCode() {
                                super.visitCode();
                                mv.visitVarInsn(Opcodes.ALOAD, 0);
                                mv.visitMethodInsn(Opcodes.INVOKESTATIC,
                                    "net/chainloader/loader/compat/bridge/EventBridgeHelper",
                                    "onLevelUnload",
                                    "(Ljava/lang/Object;)V",
                                    false);
                            }
                        };
                    } else if ("a".equals(name) && "(Layv;ZZ)V".equals(descriptor)) {
                        return new MethodVisitor(Opcodes.ASM9, mv) {
                            @Override
                            public void visitCode() {
                                super.visitCode();
                                mv.visitVarInsn(Opcodes.ALOAD, 0);
                                mv.visitMethodInsn(Opcodes.INVOKESTATIC,
                                    "net/chainloader/loader/compat/bridge/EventBridgeHelper",
                                    "onLevelSave",
                                    "(Ljava/lang/Object;)V",
                                    false);
                            }
                        };
                    }
                    return mv;
                }
            };
            cr.accept(cv, 0);
            return cw.toByteArray();
        } catch (Exception e) {
            e.printStackTrace();
            return bytes;
        }
    }

    private byte[] transformIndexedAssetSource(byte[] bytes) {
        try {
            ClassReader cr = new ClassReader(bytes);
            ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_FRAMES);
            ClassVisitor cv = new ClassVisitor(Opcodes.ASM9, cw) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                    if ("a".equals(name) && "(Ljava/nio/file/Path;Ljava/lang/String;)Ljava/nio/file/Path;".equals(descriptor)) {
                        return new MethodVisitor(Opcodes.ASM9, mv) {
                            @Override
                            public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
                                if (opcode == Opcodes.INVOKESTATIC && "java/nio/file/Files".equals(owner) && "newBufferedReader".equals(name)) {
                                    super.visitMethodInsn(Opcodes.INVOKESTATIC,
                                        "net/chainloader/loader/compat/asset/VanillaAssetPatcher",
                                        "patchBufferedReader",
                                        "(Ljava/nio/file/Path;Ljava/nio/charset/Charset;)Ljava/io/BufferedReader;",
                                        false);
                                } else {
                                    super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                                }
                            }
                        };
                    }
                    return mv;
                }
            };
            cr.accept(cv, 0);
            return cw.toByteArray();
        } catch (Exception e) {
            e.printStackTrace();
            return bytes;
        }
    }

    private byte[] transformClientCommonPacketListenerImpl(byte[] bytes) {
        try {
            ClassReader cr = new ClassReader(bytes);
            ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_FRAMES);
            ClassVisitor cv = new ClassVisitor(Opcodes.ASM9, cw) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                    if ("a".equals(name) && "(Lvv;)V".equals(descriptor)) {
                        return new MethodVisitor(Opcodes.ASM9, mv) {
                            @Override
                            public void visitCode() {
                                super.visitCode();
                                mv.visitVarInsn(Opcodes.ALOAD, 0);
                                mv.visitVarInsn(Opcodes.ALOAD, 1);
                                mv.visitMethodInsn(Opcodes.INVOKESTATIC,
                                    "net/chainloader/loader/compat/bridge/EventBridgeHelper",
                                    "onClientDisconnect",
                                    "(Ljava/lang/Object;Ljava/lang/Object;)V",
                                    false);
                            }
                        };
                    }
                    return mv;
                }
            };
            cr.accept(cv, 0);
            return cw.toByteArray();
        } catch (Exception e) {
            e.printStackTrace();
            return bytes;
        }
    }

    private byte[] transformServerCommonPacketListenerImpl(byte[] bytes) {
        try {
            ClassReader cr = new ClassReader(bytes);
            ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_FRAMES);
            ClassVisitor cv = new ClassVisitor(Opcodes.ASM9, cw) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                    if ("a".equals(name) && "(Lvv;)V".equals(descriptor)) {
                        return new MethodVisitor(Opcodes.ASM9, mv) {
                            @Override
                            public void visitCode() {
                                super.visitCode();
                                mv.visitVarInsn(Opcodes.ALOAD, 0);
                                mv.visitVarInsn(Opcodes.ALOAD, 1);
                                mv.visitMethodInsn(Opcodes.INVOKESTATIC,
                                    "net/chainloader/loader/compat/bridge/EventBridgeHelper",
                                    "onServerDisconnect",
                                    "(Ljava/lang/Object;Ljava/lang/Object;)V",
                                    false);
                            }
                        };
                    }
                    return mv;
                }

                @Override
                public void visitEnd() {
                    // Inject c()Lvt; getter which returns this.e
                    MethodVisitor mv = super.visitMethod(Opcodes.ACC_PUBLIC, "c", "()Lvt;", null, null);
                    mv.visitCode();
                    mv.visitVarInsn(Opcodes.ALOAD, 0);
                    mv.visitFieldInsn(Opcodes.GETFIELD, "arr", "e", "Lvt;");
                    mv.visitInsn(Opcodes.ARETURN);
                    mv.visitMaxs(1, 1);
                    mv.visitEnd();

                    super.visitEnd();
                }
            };
            cr.accept(cv, 0);
            return cw.toByteArray();
        } catch (Exception e) {
            e.printStackTrace();
            return bytes;
        }
    }

    private byte[] transformModel(byte[] bytes) {
        try {
            ClassReader cr = new ClassReader(bytes);
            ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_FRAMES);
            ClassVisitor cv = new ClassVisitor(Opcodes.ASM9, cw) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    if ("a".equals(name) && "(Lfbi;Lfbm;III)V".equals(descriptor)) {
                        MethodVisitor mv = super.visitMethod(access & ~Opcodes.ACC_ABSTRACT, name, descriptor, signature, exceptions);
                        mv.visitCode();
                        
                        mv.visitVarInsn(Opcodes.ILOAD, 5);
                        mv.visitIntInsn(Opcodes.BIPUSH, 24);
                        mv.visitInsn(Opcodes.IUSHR);
                        mv.visitIntInsn(Opcodes.SIPUSH, 255);
                        mv.visitInsn(Opcodes.IAND);
                        mv.visitInsn(Opcodes.I2F);
                        mv.visitLdcInsn(255.0f);
                        mv.visitInsn(Opcodes.FDIV);
                        mv.visitVarInsn(Opcodes.FSTORE, 6);
                        
                        mv.visitVarInsn(Opcodes.ILOAD, 5);
                        mv.visitIntInsn(Opcodes.BIPUSH, 16);
                        mv.visitInsn(Opcodes.IUSHR);
                        mv.visitIntInsn(Opcodes.SIPUSH, 255);
                        mv.visitInsn(Opcodes.IAND);
                        mv.visitInsn(Opcodes.I2F);
                        mv.visitLdcInsn(255.0f);
                        mv.visitInsn(Opcodes.FDIV);
                        mv.visitVarInsn(Opcodes.FSTORE, 7);
                        
                        mv.visitVarInsn(Opcodes.ILOAD, 5);
                        mv.visitIntInsn(Opcodes.BIPUSH, 8);
                        mv.visitInsn(Opcodes.IUSHR);
                        mv.visitIntInsn(Opcodes.SIPUSH, 255);
                        mv.visitInsn(Opcodes.IAND);
                        mv.visitInsn(Opcodes.I2F);
                        mv.visitLdcInsn(255.0f);
                        mv.visitInsn(Opcodes.FDIV);
                        mv.visitVarInsn(Opcodes.FSTORE, 8);
                        
                        mv.visitVarInsn(Opcodes.ILOAD, 5);
                        mv.visitIntInsn(Opcodes.SIPUSH, 255);
                        mv.visitInsn(Opcodes.IAND);
                        mv.visitInsn(Opcodes.I2F);
                        mv.visitLdcInsn(255.0f);
                        mv.visitInsn(Opcodes.FDIV);
                        mv.visitVarInsn(Opcodes.FSTORE, 9);
                        
                        mv.visitVarInsn(Opcodes.ALOAD, 0);
                        mv.visitVarInsn(Opcodes.ALOAD, 1);
                        mv.visitVarInsn(Opcodes.ALOAD, 2);
                        mv.visitVarInsn(Opcodes.ILOAD, 3);
                        mv.visitVarInsn(Opcodes.ILOAD, 4);
                        mv.visitVarInsn(Opcodes.FLOAD, 7);
                        mv.visitVarInsn(Opcodes.FLOAD, 8);
                        mv.visitVarInsn(Opcodes.FLOAD, 9);
                        mv.visitVarInsn(Opcodes.FLOAD, 6);
                        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "fwg", "a", "(Lfbi;Lfbm;IIFFFF)V", false);
                        
                        mv.visitInsn(Opcodes.RETURN);
                        mv.visitMaxs(0, 0);
                        mv.visitEnd();
                        return null;
                    }
                    return super.visitMethod(access, name, descriptor, signature, exceptions);
                }

                @Override
                public void visitEnd() {
                    MethodVisitor mv = super.visitMethod(Opcodes.ACC_PUBLIC, "a", "(Lfbi;Lfbm;IIFFFF)V", null, null);
                    mv.visitCode();
                    mv.visitInsn(Opcodes.RETURN);
                    mv.visitMaxs(0, 0);
                    mv.visitEnd();
                    super.visitEnd();
                }
            };
            cr.accept(cv, 0);
            return cw.toByteArray();
        } catch (Exception e) {
            e.printStackTrace();
            return bytes;
        }
    }

    private byte[] transformModelPart(byte[] bytes) {
        try {
            ClassReader cr = new ClassReader(bytes);
            ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_FRAMES);
            ClassVisitor cv = new ClassVisitor(Opcodes.ASM9, cw) {
                @Override
                public void visitEnd() {
                    MethodVisitor mv = super.visitMethod(Opcodes.ACC_PUBLIC, "a", "(Lfbi;Lfbm;IIFFFF)V", null, null);
                    mv.visitCode();

                    mv.visitVarInsn(Opcodes.FLOAD, 5);
                    mv.visitLdcInsn(255.0f);
                    mv.visitInsn(Opcodes.FMUL);
                    mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Math", "round", "(F)I", false);
                    mv.visitVarInsn(Opcodes.ISTORE, 9); // r

                    mv.visitVarInsn(Opcodes.FLOAD, 6);
                    mv.visitLdcInsn(255.0f);
                    mv.visitInsn(Opcodes.FMUL);
                    mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Math", "round", "(F)I", false);
                    mv.visitVarInsn(Opcodes.ISTORE, 10); // g

                    mv.visitVarInsn(Opcodes.FLOAD, 7);
                    mv.visitLdcInsn(255.0f);
                    mv.visitInsn(Opcodes.FMUL);
                    mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Math", "round", "(F)I", false);
                    mv.visitVarInsn(Opcodes.ISTORE, 11); // b

                    mv.visitVarInsn(Opcodes.FLOAD, 8);
                    mv.visitLdcInsn(255.0f);
                    mv.visitInsn(Opcodes.FMUL);
                    mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Math", "round", "(F)I", false);
                    mv.visitVarInsn(Opcodes.ISTORE, 12); // a

                    mv.visitVarInsn(Opcodes.ILOAD, 12);
                    mv.visitIntInsn(Opcodes.BIPUSH, 24);
                    mv.visitInsn(Opcodes.ISHL);
                    mv.visitVarInsn(Opcodes.ILOAD, 9);
                    mv.visitIntInsn(Opcodes.BIPUSH, 16);
                    mv.visitInsn(Opcodes.ISHL);
                    mv.visitInsn(Opcodes.IOR);
                    mv.visitVarInsn(Opcodes.ILOAD, 10);
                    mv.visitIntInsn(Opcodes.BIPUSH, 8);
                    mv.visitInsn(Opcodes.ISHL);
                    mv.visitInsn(Opcodes.IOR);
                    mv.visitVarInsn(Opcodes.ILOAD, 11);
                    mv.visitInsn(Opcodes.IOR);
                    mv.visitVarInsn(Opcodes.ISTORE, 13); // packedColor

                    mv.visitVarInsn(Opcodes.ALOAD, 0);
                    mv.visitVarInsn(Opcodes.ALOAD, 1);
                    mv.visitVarInsn(Opcodes.ALOAD, 2);
                    mv.visitVarInsn(Opcodes.ILOAD, 3);
                    mv.visitVarInsn(Opcodes.ILOAD, 4);
                    mv.visitVarInsn(Opcodes.ILOAD, 13);
                    mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "fyk", "a", "(Lfbi;Lfbm;III)V", false);

                    mv.visitInsn(Opcodes.RETURN);
                    mv.visitMaxs(0, 0);
                    mv.visitEnd();
                    super.visitEnd();
                }
            };
            cr.accept(cv, 0);
            return cw.toByteArray();
        } catch (Exception e) {
            e.printStackTrace();
            return bytes;
        }
    }

    private byte[] transformSelectionList(String owner, byte[] bytes) {
        try {
            ClassReader cr = new ClassReader(bytes);
            ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
            ClassVisitor cv = new ClassVisitor(Opcodes.ASM9, cw) {
                private boolean hasSixParamConstructor = false;

                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    if ("<init>".equals(name) && "(Lfgo;IIIII)V".equals(descriptor)) {
                        hasSixParamConstructor = true;
                    }
                    return super.visitMethod(access, name, descriptor, signature, exceptions);
                }

                @Override
                public void visitEnd() {
                    if (!hasSixParamConstructor) {
                        MethodVisitor mv = super.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "(Lfgo;IIIII)V", null, null);
                        mv.visitCode();
                        mv.visitVarInsn(Opcodes.ALOAD, 0);
                        mv.visitVarInsn(Opcodes.ALOAD, 1);
                        mv.visitVarInsn(Opcodes.ILOAD, 2);
                        
                        mv.visitVarInsn(Opcodes.ILOAD, 5);
                        mv.visitVarInsn(Opcodes.ILOAD, 4);
                        mv.visitInsn(Opcodes.ISUB);
                        
                        mv.visitVarInsn(Opcodes.ILOAD, 4);
                        mv.visitVarInsn(Opcodes.ILOAD, 6);
                        
                        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, owner.replace('.', '/'), "<init>", "(Lfgo;IIII)V", false);
                        mv.visitInsn(Opcodes.RETURN);
                        mv.visitMaxs(6, 7);
                        mv.visitEnd();
                    }
                    super.visitEnd();
                }
            };
            cr.accept(cv, 0);
            return cw.toByteArray();
        } catch (Exception e) {
            e.printStackTrace();
            return bytes;
        }
    }

    private byte[] applyModRedirections(String className, byte[] classBytes) {
        try {
            ClassReader classReader = new ClassReader(classBytes);
            ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            
            ClassVisitor interfaceFixer = new ClassVisitor(Opcodes.ASM9, classWriter) {
                private boolean isEntryClass = false;
                private boolean hasNewRender = false;

                private Handle fixHandle(Handle h) {
                    if (h != null && "com/mojang/serialization/DataResult".equals(h.getOwner())) {
                        boolean isInterface = true;
                        int tag = h.getTag();
                        if (tag == Opcodes.H_INVOKEVIRTUAL) {
                            tag = Opcodes.H_INVOKEINTERFACE;
                        }
                        return new Handle(tag, h.getOwner(), h.getName(), h.getDesc(), isInterface);
                    }
                    return h;
                }

                @Override
                public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
                    super.visit(version, access, name, signature, superName, interfaces);
                    if (superName != null) {
                        String deobfSuper = BytecodeTransformer.getInstance() != null ? 
                            BytecodeTransformer.getInstance().getDeobfClassName(superName) : superName;
                        if (deobfSuper.equals("net/minecraft/client/gui/components/AbstractSelectionList$Entry") ||
                            deobfSuper.equals("net/minecraft/client/gui/components/ObjectSelectionList$Entry") ||
                            "fih$a".equals(superName) || "fji$a".equals(superName)) {
                            isEntryClass = true;
                        }
                    }
                }

                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    if ("a".equals(name) && "(Lfhz;IIIIIIIZF)V".equals(descriptor)) {
                        hasNewRender = true;
                    }
                    MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                    return new MethodVisitor(Opcodes.ASM9, mv) {
                        @Override
                        public void visitMethodInsn(int opcode, String owner, String methodName, String methodDesc, boolean isInterface) {
                            boolean redirectedGetConnection = false;
                            boolean isPacketListener = "net/minecraft/client/multiplayer/ClientCommonPacketListenerImpl".equals(owner) || "fzc".equals(owner)
                                    || "net/minecraft/server/network/ServerCommonPacketListenerImpl".equals(owner) || "arr".equals(owner)
                                    || "net/minecraft/network/PacketListener".equals(owner) || "ha".equals(owner)
                                    || "net/minecraft/server/network/ServerGamePacketListenerImpl".equals(owner) || "aru".equals(owner);
                            boolean isGetConnection = "getConnection".equals(methodName) || "c".equals(methodName) || "m_6198_".equals(methodName);
                            if (isPacketListener && isGetConnection) {
                                System.out.println("[Chainlink 1.21.1 DEBUG] Redirecting getConnection call in " + className);
                                opcode = Opcodes.INVOKESTATIC;
                                owner = "net/chainloader/loader/compat/bridge/EventBridgeHelper";
                                methodName = "getConnection";
                                methodDesc = "(Ljava/lang/Object;)Ljava/lang/Object;";
                                isInterface = false;
                                redirectedGetConnection = true;
                            }

                            boolean isTickMethod = "tick".equals(methodName) || "method_2037".equals(methodName) || "method_1684".equals(methodName) || "method_1868".equals(methodName) || "m_94120_".equals(methodName);
                            // We check the deobf class name recursive using helper or simple check
                            if (isTickMethod && "()V".equals(methodDesc) && isWidgetRecursive(owner)) {
                                System.out.println("[Chainlink 1.21.1 DEBUG] Redirecting widget tick in " + className + ": owner=" + owner + ", name=" + methodName);
                                opcode = Opcodes.INVOKESTATIC;
                                owner = "net/chainloader/loader/compat/bridge/EventBridgeHelper";
                                methodName = "tickWidget";
                                methodDesc = "(Ljava/lang/Object;)V";
                                isInterface = false;
                            }
                            
                            boolean isDisplayItemsGenerator = "cta$b".equals(owner) || "net/minecraft/world/item/CreativeModeTab$DisplayItemsGenerator".equals(owner);
                            boolean isAccept = "a".equals(methodName) || "accept".equals(methodName) || "method_47325".equals(methodName);
                            boolean isAcceptDesc = "(Lcta$d;Lcta$e;)V".equals(methodDesc) || "(Lnet/minecraft/world/item/CreativeModeTab$ItemDisplayParameters;Lnet/minecraft/world/item/CreativeModeTab$Output;)V".equals(methodDesc);
                            if (isDisplayItemsGenerator && isAccept && isAcceptDesc) {
                                System.out.println("[Chainlink 1.21.1 DEBUG] Redirecting DisplayItemsGenerator.accept in " + className);
                                opcode = Opcodes.INVOKESTATIC;
                                owner = "net/chainloader/loader/compat/bridge/EventBridgeHelper";
                                methodName = "buildTabContents";
                                methodDesc = "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)V";
                                isInterface = false;
                            }

                            boolean isEnchantmentHelper = "net/minecraft/world/item/enchantment/EnchantmentHelper".equals(owner) || "dae".equals(owner);
                            boolean isGetEnchantments = "method_8222".equals(methodName) || "getEnchantments".equals(methodName);
                            if (isEnchantmentHelper && isGetEnchantments && methodDesc.endsWith(")Ljava/util/Map;")) {
                                opcode = Opcodes.INVOKESTATIC;
                                owner = "net/chainloader/loader/compat/bridge/EventBridgeHelper";
                                methodName = "getEnchantments";
                                methodDesc = "(Lcuq;)Ljava/util/Map;";
                                isInterface = false;
                                System.out.println("[Chainlink 1.21.1 DEBUG] Redirected EnchantmentHelper.getEnchantments to EventBridgeHelper.getEnchantments");
                            } else if ("com/mojang/serialization/DataResult".equals(owner) && "getOrThrow".equals(methodName) && "(ZLjava/util/function/Consumer;)Ljava/lang/Object;".equals(methodDesc)) {
                                opcode = Opcodes.INVOKESTATIC;
                                owner = "net/chainloader/loader/compat/lib/DataResultCompatBridge";
                                methodDesc = "(Lcom/mojang/serialization/DataResult;ZLjava/util/function/Consumer;)Ljava/lang/Object;";
                                isInterface = false;
                            } else if ("com/mojang/serialization/DataResult".equals(owner)) {
                                if (opcode == Opcodes.INVOKEVIRTUAL) {
                                    opcode = Opcodes.INVOKEINTERFACE;
                                }
                                isInterface = true;
                            } else {
                                boolean isDimensionDataStorage = "net/minecraft/world/level/storage/DimensionDataStorage".equals(owner) || "eqz".equals(owner);
                                boolean isComputeIfAbsent = "computeIfAbsent".equals(methodName) || "a".equals(methodName);
                                boolean isOldSignature = methodDesc.startsWith("(Ljava/util/function/Function;Ljava/util/function/Supplier;Ljava/lang/String;)");
                                if (isDimensionDataStorage && isComputeIfAbsent && isOldSignature) {
                                    System.out.println("[Chainlink 1.21.1 DEBUG] Redirecting DimensionDataStorage.computeIfAbsent old signature in " + className);
                                    opcode = Opcodes.INVOKESTATIC;
                                    owner = "net/chainloader/loader/compat/bridge/EventBridgeHelper";
                                    methodName = "computeIfAbsent";
                                    methodDesc = "(Ljava/lang/Object;" + methodDesc.substring(1);
                                    isInterface = false;
                                } else {
                                    boolean isDyeColor = "net/minecraft/world/item/DyeColor".equals(owner) || "cti".equals(owner);
                                    boolean isGetColorComponents = "getColorComponents".equals(methodName) || "d".equals(methodName);
                                    boolean isFloatArrayReturn = "()[F".equals(methodDesc);
                                    if (isDyeColor && isGetColorComponents && isFloatArrayReturn) {
                                        System.out.println("[Chainlink 1.21.1 DEBUG] Redirecting DyeColor.getColorComponents in " + className);
                                        opcode = Opcodes.INVOKESTATIC;
                                        owner = "net/chainloader/loader/compat/bridge/EventBridgeHelper";
                                        methodName = "getColorComponents";
                                        methodDesc = "(Ljava/lang/Object;)[F";
                                        isInterface = false;
                                    } else if (("net/minecraft/network/protocol/game/ClientboundBlockEntityDataPacket".equals(owner) || "acb".equals(owner)) &&
                                            ("create".equals(methodName) || "a".equals(methodName)) &&
                                            ("(Lnet/minecraft/world/level/block/entity/BlockEntity;Ljava/util/function/Function;)Lnet/minecraft/network/protocol/game/ClientboundBlockEntityDataPacket;".equals(methodDesc) ||
                                             "(Ldqh;Ljava/util/function/Function;)Lacb;".equals(methodDesc))) {
                                     System.out.println("[Chainlink 1.21.1 DEBUG] Redirecting ClientboundBlockEntityDataPacket.create legacy signature in " + className);
                                     opcode = Opcodes.INVOKESTATIC;
                                     owner = "net/chainloader/loader/compat/bridge/EventBridgeHelper";
                                     methodName = "createBlockEntityDataPacket";
                                     isInterface = false;
                                 } else if (("net/minecraft/nbt/NbtUtils".equals(owner) || "uq".equals(owner)) &&
                                            ("writeBlockPos".equals(methodName) || "a".equals(methodName)) &&
                                            ("(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/nbt/CompoundTag;".equals(methodDesc) ||
                                             "(Ljd;)Lub;".equals(methodDesc))) {
                                     System.out.println("[Chainlink 1.21.1 DEBUG] Redirecting NbtUtils.writeBlockPos legacy signature in " + className);
                                     opcode = Opcodes.INVOKESTATIC;
                                     owner = "net/chainloader/loader/compat/bridge/EventBridgeHelper";
                                     methodName = "writeBlockPosCompound";
                                     isInterface = false;
                                 } else if (("net/minecraft/world/level/biome/Biome".equals(owner) || "ddw".equals(owner)) &&
                                            ("getPrecipitation".equals(methodName) || "m_47530_".equals(methodName)) &&
                                            ("()Lnet/minecraft/world/level/biome/Biome$Precipitation;".equals(methodDesc) ||
                                             "()Lddw$c;".equals(methodDesc))) {
                                     System.out.println("[Chainlink 1.21.1 DEBUG] Redirecting Biome.getPrecipitation in " + className);
                                     opcode = Opcodes.INVOKESTATIC;
                                     owner = "net/chainloader/loader/compat/bridge/EventBridgeHelper";
                                     methodName = "getPrecipitation";
                                     methodDesc = "()Lddw$c;".equals(methodDesc) ? "(Ljava/lang/Object;)Lddw$c;" : "(Ljava/lang/Object;)Lnet/minecraft/world/level/biome/Biome$Precipitation;";
                                     isInterface = false;
                                 } else {
                                         boolean isItemProperties = "net/minecraft/client/renderer/item/ItemProperties".equals(owner) || "gps".equals(owner);
                                         boolean isRegister = "register".equals(methodName) || "a".equals(methodName);
                                         boolean isOldDescriptor = methodDesc.contains("/ItemPropertyFunction;") || methodDesc.contains("Lgpt;");
                                         if (isItemProperties && isRegister && isOldDescriptor) {
                                             System.out.println("[Chainlink 1.21.1 DEBUG] Redirecting ItemProperties.register old signature in " + className);
                                             opcode = Opcodes.INVOKESTATIC;
                                             owner = "net/chainloader/loader/compat/bridge/EventBridgeHelper";
                                             methodName = "registerItemProperty";
                                             methodDesc = "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)V";
                                             isInterface = false;
                                         }
                                    }
                                }
                            }
                            super.visitMethodInsn(opcode, owner, methodName, methodDesc, isInterface);
                            if (redirectedGetConnection) {
                                String targetConnectionClass = "vt";
                                if (BytecodeTransformer.getInstance() != null) {
                                    String mapped = BytecodeTransformer.getInstance().mapClassName("net.minecraft.network.Connection");
                                    if (mapped != null) {
                                        targetConnectionClass = mapped.replace('.', '/');
                                    }
                                }
                                super.visitTypeInsn(Opcodes.CHECKCAST, targetConnectionClass);
                            }
                        }

                        @Override
                        public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
                            if (opcode == Opcodes.GETSTATIC && "dah".equals(owner) && "Ldac;".equals(descriptor)) {
                                System.out.println("[Chainlink 1.21.1 DEBUG] Redirecting field GETSTATIC for " + owner + "." + name + " in class=" + className);
                                super.visitLdcInsn(name);
                                super.visitMethodInsn(Opcodes.INVOKESTATIC, "net/chainloader/loader/compat/bridge/EventBridgeHelper", "getEnchantment", "(Ljava/lang/String;)Ljava/lang/Object;", false);
                                super.visitTypeInsn(Opcodes.CHECKCAST, "dac");
                            } else if (opcode == Opcodes.GETSTATIC && ("net/minecraft/world/item/CreativeModeTabs".equals(owner) || "ctb".equals(owner)) &&
                                       ("Lnet/minecraft/world/item/CreativeModeTab;".equals(descriptor) || "Lcta;".equals(descriptor))) {
                                System.out.println("[Chainlink 1.21.1 DEBUG] Redirecting field GETSTATIC for CreativeModeTabs." + name + " in class=" + className);
                                String realDesc = "Lnet/minecraft/resources/ResourceKey;";
                                if ("Lcta;".equals(descriptor)) {
                                    realDesc = "Lqj;";
                                }
                                super.visitFieldInsn(opcode, owner, name, realDesc);
                                super.visitMethodInsn(Opcodes.INVOKESTATIC, "net/chainloader/loader/compat/bridge/EventBridgeHelper", "getCreativeModeTab", "(Ljava/lang/Object;)Lnet/minecraft/world/item/CreativeModeTab;", false);
                                if ("Lcta;".equals(descriptor)) {
                                    super.visitTypeInsn(Opcodes.CHECKCAST, "cta");
                                } else {
                                    super.visitTypeInsn(Opcodes.CHECKCAST, "net/minecraft/world/item/CreativeModeTab");
                                }
                            } else {
                                super.visitFieldInsn(opcode, owner, name, descriptor);
                            }
                        }

                        @Override
                        public void visitInvokeDynamicInsn(String name, String descriptor, Handle bootstrapMethodHandle, Object... bootstrapMethodArguments) {
                            Handle fixedBsm = fixHandle(bootstrapMethodHandle);
                            Object[] fixedArgs = new Object[bootstrapMethodArguments.length];
                            for (int i = 0; i < bootstrapMethodArguments.length; i++) {
                                Object arg = bootstrapMethodArguments[i];
                                if (arg instanceof Handle) {
                                    fixedArgs[i] = fixHandle((Handle) arg);
                                } else {
                                    fixedArgs[i] = arg;
                                }
                            }
                            super.visitInvokeDynamicInsn(name, descriptor, fixedBsm, fixedArgs);
                        }

                        @Override
                        public void visitLdcInsn(Object value) {
                            if (value instanceof Handle) {
                                value = fixHandle((Handle) value);
                            }
                            super.visitLdcInsn(value);
                        }
                    };
                }

                @Override
                public void visitEnd() {
                    if (isEntryClass && !hasNewRender) {
                        System.out.println("[Chainlink 1.21.1] Injecting render(GuiGraphics) -> render(PoseStack) bridge in " + className);
                        MethodVisitor mv = super.visitMethod(Opcodes.ACC_PUBLIC, "a", "(Lfhz;IIIIIIIZF)V", null, null);
                        mv.visitCode();
                        mv.visitVarInsn(Opcodes.ALOAD, 0); // this
                        mv.visitVarInsn(Opcodes.ALOAD, 1); // GuiGraphics
                        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "fhz", "c", "()Lfbi;", false); // get PoseStack
                        mv.visitVarInsn(Opcodes.ILOAD, 2);
                        mv.visitVarInsn(Opcodes.ILOAD, 3);
                        mv.visitVarInsn(Opcodes.ILOAD, 4);
                        mv.visitVarInsn(Opcodes.ILOAD, 5);
                        mv.visitVarInsn(Opcodes.ILOAD, 6);
                        mv.visitVarInsn(Opcodes.ILOAD, 7);
                        mv.visitVarInsn(Opcodes.ILOAD, 8);
                        mv.visitVarInsn(Opcodes.ILOAD, 9);
                        mv.visitVarInsn(Opcodes.FLOAD, 10);
                        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, className.replace('.', '/'), "a", "(Lfbi;IIIIIIIZF)V", false);
                        mv.visitInsn(Opcodes.RETURN);
                        mv.visitMaxs(11, 11);
                        mv.visitEnd();
                    }
                    super.visitEnd();
                }
            };
            classReader.accept(interfaceFixer, ClassReader.EXPAND_FRAMES);
            return classWriter.toByteArray();
        } catch (Exception e) {
            e.printStackTrace();
            return classBytes;
        }
    }

    private boolean isWidgetRecursive(String owner) {
        if (owner == null || "java/lang/Object".equals(owner)) {
            return false;
        }
        BytecodeTransformer transformer = BytecodeTransformer.getInstance();
        if (transformer != null) {
            // We can query the core remapper since it caches isWidgetRecursive
            return transformer.isWidgetRecursiveCached(owner);
        }
        return false;
    }
}
