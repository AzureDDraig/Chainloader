package net.chainloader.loader.compat.bridge;

import net.chainloader.api.client.render.ChainEntityRenderer;
import net.chainloader.api.client.render.ChainRenderEvents;
import net.chainloader.api.event.ChainEvents;
import net.chainloader.api.event.PlayerBlockBreakCallback;
import net.chainloader.loader.compat.fabric.FabricLoaderShim;
import net.chainloader.loader.compat.registry.RegistryStager;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ActionResult;
import net.minecraft.entity.EntityType;
import net.minecraft.client.render.entity.EntityRendererFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * FabricApiPort bridges the Fabric API's registry and client-side rendering systems
 * to ChainLoader's unified api/event and staging structures.
 * <p>
 * It emulates the behavior of major Fabric API components like:
 * <ul>
 *   <li>FuelRegistry, FlammableBlockRegistry, CompostingChanceRegistry, StrippableBlockRegistry (Registry Helpers)</li>
 *   <li>BlockEntityRendererRegistry, EntityRendererRegistry, ColorProviderRegistry (Render Porting)</li>
 *   <li>HudRenderCallback, ItemTooltipCallback, PlayerBlockBreakCallback (Event Bridges)</li>
 * </ul>
 * <p>
 * It coordinates with:
 * <ul>
 *   <li>The Event Translator (via loop prevention and bidirectional translation)</li>
 *   <li>The Registry Synchronizer / RegistryStager (via binder wrapping and entry-added event triggering)</li>
 * </ul>
 */
public class FabricApiPort {

    private static final ThreadLocal<Set<Object>> activeEventTranslations = ThreadLocal.withInitial(HashSet::new);
    private static final Map<ResourceLocation, List<RegistryEntryAddedCallback<Object>>> registryCallbacks = new ConcurrentHashMap<>();

    /**
     * Initializes and registers the bi-directional event bridges and compatibility mappings.
     */
    @SuppressWarnings("unchecked")
    public static void registerBridges() {
        System.out.println("[Fabric API Port] Registering event and registry bridges...");

        // 1. Bridge: ChainLoader HUD Render -> Fabric HudRenderCallback
        ChainRenderEvents.HUD_RENDER.register((drawContext, tickDelta) -> {
            Object eventMarker = new Object();
            if (activeEventTranslations.get().contains(eventMarker)) return;
            
            activeEventTranslations.get().add(eventMarker);
            try {
                FabricLoaderShim.getInstance().dispatchEvent("fabric:hud_render", (Consumer<Object>) listener -> {
                    try {
                        Class<?> drawContextClass = Class.forName("net.minecraft.client.gui.DrawContext");
                        java.lang.reflect.Method method = listener.getClass().getMethod("onHudRender", drawContextClass, float.class);
                        method.setAccessible(true);
                        method.invoke(listener, drawContext, tickDelta);
                    } catch (ClassNotFoundException | NoSuchMethodException e) {
                        try {
                            Class<?> matrixStackClass = Class.forName("net.minecraft.client.util.math.MatrixStack");
                            java.lang.reflect.Method fallbackMethod = listener.getClass().getMethod("onHudRender", matrixStackClass, float.class);
                            fallbackMethod.setAccessible(true);
                            Object matrices = null;
                            try {
                                if (drawContext != null) {
                                    java.lang.reflect.Method getMatrices = drawContext.getClass().getMethod("getMatrices");
                                    getMatrices.setAccessible(true);
                                    matrices = getMatrices.invoke(drawContext);
                                }
                            } catch (Exception ex) {
                                // Ignore
                            }
                            fallbackMethod.invoke(listener, matrices, tickDelta);
                        } catch (Exception ex) {
                            System.err.println("Failed to invoke legacy Fabric HudRenderCallback: " + ex.getMessage());
                        }
                    } catch (Exception e) {
                        System.err.println("Error translating HUD Render event: " + e.getMessage());
                    }
                });
            } finally {
                activeEventTranslations.get().remove(eventMarker);
            }
        });

        // 2. Bridge: ChainLoader Tooltip Modify -> Fabric ItemTooltipCallback
        ChainRenderEvents.TOOLTIP_MODIFY.register((stack, tooltipLines, context) -> {
            Object eventMarker = new Object();
            if (activeEventTranslations.get().contains(eventMarker)) return;

            activeEventTranslations.get().add(eventMarker);
            try {
                FabricLoaderShim.getInstance().dispatchEvent("fabric:tooltip_modify", (Consumer<Object>) listener -> {
                    try {
                        Class<?> itemStackClass = Class.forName("net.minecraft.item.ItemStack");
                        Class<?> tooltipContextClass = Class.forName("net.minecraft.client.item.TooltipContext");
                        java.lang.reflect.Method method = listener.getClass().getMethod("getTooltip", itemStackClass, tooltipContextClass, List.class);
                        method.setAccessible(true);
                        method.invoke(listener, stack, context, tooltipLines);
                    } catch (Exception e) {
                        System.err.println("Error translating Tooltip event: " + e.getMessage());
                    }
                });
            } finally {
                activeEventTranslations.get().remove(eventMarker);
            }
        });

        // 3. Bridge: ChainLoader PlayerBlockBreakCallback -> Fabric PlayerBlockBreakEvents.BEFORE
        PlayerBlockBreakCallback.EVENT.register((player, world, pos, state, blockEntity) -> {
            Object eventMarker = new Object();
            if (activeEventTranslations.get().contains(eventMarker)) return ActionResult.PASS;

            activeEventTranslations.get().add(eventMarker);
            final boolean[] allowed = {true};
            try {
                FabricLoaderShim.getInstance().dispatchEvent("fabric:before_block_break", (Consumer<Object>) listener -> {
                    try {
                        Class<?> worldClass = Class.forName("net.minecraft.world.World");
                        Class<?> playerClass = Class.forName("net.minecraft.entity.player.PlayerEntity");
                        Class<?> posClass = Class.forName("net.minecraft.util.math.BlockPos");
                        Class<?> stateClass = Class.forName("net.minecraft.block.BlockState");
                        Class<?> blockEntityClass = Class.forName("net.minecraft.block.entity.BlockEntity");
                        
                        java.lang.reflect.Method method = listener.getClass().getMethod("beforeBlockBreak", 
                                worldClass, playerClass, posClass, stateClass, blockEntityClass);
                        method.setAccessible(true);
                        boolean result = (boolean) method.invoke(listener, world, player, pos, state, blockEntity);
                        if (!result) {
                            allowed[0] = false;
                        }
                    } catch (Exception e) {
                        System.err.println("Error translating PlayerBlockBreakCallback: " + e.getMessage());
                    }
                });
            } finally {
                activeEventTranslations.get().remove(eventMarker);
            }

            return allowed[0] ? ActionResult.PASS : ActionResult.FAIL;
        });

        // 4. Bridge: RegistrySynchronizer's fabric:registry_entry_added -> Fabric's RegistryEntryAddedCallback
        FabricLoaderShim.getInstance().registerEventCallback("fabric:registry_entry_added", (RegistrySynchronizer.FabricRegistryEntryCallback) (registryId, entryId, value) -> {
            try {
                ResourceLocation regLoc = new ResourceLocation(registryId);
                ResourceLocation entLoc = new ResourceLocation(entryId);
                triggerEntryAdded(regLoc, entLoc, value);
            } catch (Exception e) {
                System.err.println("[Fabric API Port] Failed to propagate registry entry added event: " + e.getMessage());
            }
        });
    }

    // --- Emulated Registry Helper Backends ---

    /**
     * Emulates Fabric's FuelRegistry.
     */
    public static class FuelRegistry {
        public static final FuelRegistry INSTANCE = new FuelRegistry();
        private final Map<Object, Integer> fuelTimes = new ConcurrentHashMap<>();

        public void add(Object item, int cookTime) {
            fuelTimes.put(item, cookTime);
            System.out.println("[Fabric API Port] Registered fuel: " + item + " with cook time: " + cookTime);
        }

        public void remove(Object item) {
            fuelTimes.remove(item);
        }

        public Integer timeOf(Object item) {
            return fuelTimes.getOrDefault(item, 0);
        }

        public Map<Object, Integer> getFuelTimes() {
            return Collections.unmodifiableMap(fuelTimes);
        }
    }

    /**
     * Emulates Fabric's FlammableBlockRegistry.
     */
    public static class FlammableBlockRegistry {
        private static final FlammableBlockRegistry INSTANCE = new FlammableBlockRegistry();
        private final Map<Object, Entry> entries = new ConcurrentHashMap<>();

        public static FlammableBlockRegistry getDefaultInstance() {
            return INSTANCE;
        }

        public void add(Object block, int burnChance, int spreadChance) {
            entries.put(block, new Entry(burnChance, spreadChance));
            System.out.println("[Fabric API Port] Registered flammable block: " + block + " (burn=" + burnChance + ", spread=" + spreadChance + ")");
        }

        public void remove(Object block) {
            entries.remove(block);
        }

        public Entry get(Object block) {
            return entries.getOrDefault(block, new Entry(0, 0));
        }

        public Map<Object, Entry> getEntries() {
            return Collections.unmodifiableMap(entries);
        }

        public record Entry(int burnChance, int spreadChance) {}
    }

    /**
     * Emulates Fabric's CompostingChanceRegistry.
     */
    public static class CompostingChanceRegistry {
        public static final CompostingChanceRegistry INSTANCE = new CompostingChanceRegistry();
        private final Map<Object, Float> chances = new ConcurrentHashMap<>();

        public void add(Object item, float chance) {
            chances.put(item, chance);
            System.out.println("[Fabric API Port] Registered composting chance: " + item + " -> " + chance);
        }

        public void remove(Object item) {
            chances.remove(item);
        }

        public Float get(Object item) {
            return chances.getOrDefault(item, 0.0f);
        }

        public Map<Object, Float> getChances() {
            return Collections.unmodifiableMap(chances);
        }
    }

    /**
     * Emulates Fabric's StrippableBlockRegistry.
     */
    public static class StrippableBlockRegistry {
        private static final Map<Object, Object> STRIPPABLES = new ConcurrentHashMap<>();

        public static void register(Object log, Object strippedLog) {
            STRIPPABLES.put(log, strippedLog);
            System.out.println("[Fabric API Port] Registered strippable block: " + log + " -> " + strippedLog);
        }

        public static Map<Object, Object> getStrippables() {
            return Collections.unmodifiableMap(STRIPPABLES);
        }
    }

    // --- Emulated Render System Backends ---

    /**
     * Emulates Fabric's BlockEntityRendererRegistry.
     */
    public static class BlockEntityRendererRegistry {
        private static final Map<Object, Object> RENDERERS = new ConcurrentHashMap<>();

        public static void register(Object type, Object factory) {
            RENDERERS.put(type, factory);
            System.out.println("[Fabric API Port] Registered block entity renderer for type: " + type);
        }

        public static Map<Object, Object> getRenderers() {
            return Collections.unmodifiableMap(RENDERERS);
        }
    }

    /**
     * Emulates Fabric's EntityRendererRegistry by bridging directly to ChainLoader's renderer.
     */
    public static class EntityRendererRegistry {
        @SuppressWarnings("unchecked")
        public static void register(Object type, Object factory) {
            try {
                ChainEntityRenderer.register((EntityType) type, (EntityRendererFactory) factory);
                System.out.println("[Fabric API Port] Bridged EntityRenderer for type: " + type);
            } catch (Exception e) {
                System.err.println("[Fabric API Port] Failed to bridge EntityRenderer: " + e.getMessage());
            }
        }
    }

    /**
     * Emulates Fabric's ColorProviderRegistry.
     */
    public static class ColorProviderRegistry<P, T> {
        public static final ColorProviderRegistry<Object, Object> BLOCK = new ColorProviderRegistry<>();
        public static final ColorProviderRegistry<Object, Object> ITEM = new ColorProviderRegistry<>();

        private final Map<T, P> providers = new ConcurrentHashMap<>();

        @SuppressWarnings("unchecked")
        public void register(P provider, T... objects) {
            for (T obj : objects) {
                if (obj != null) {
                    providers.put(obj, provider);
                    System.out.println("[Fabric API Port] Registered Color Provider for " + obj);
                }
            }
        }

        public Map<T, P> getProviders() {
            return Collections.unmodifiableMap(providers);
        }
    }

    // --- Emulated Registry Callback System (Coordinating with RegistryStager / Synchronizer) ---

    @FunctionalInterface
    public interface RegistryEntryAddedCallback<T> {
        void onEntryAdded(int rawId, ResourceLocation id, T object);
    }

    /**
     * Registers an entry added callback for a specific registry ID.
     */
    @SuppressWarnings("unchecked")
    public static <T> void registerEntryAdded(ResourceLocation registryId, RegistryEntryAddedCallback<T> callback) {
        registryCallbacks.computeIfAbsent(registryId, k -> Collections.synchronizedList(new ArrayList<>()))
                .add((RegistryEntryAddedCallback<Object>) callback);
    }

    /**
     * Fires the entry-added callback for the specified registry ID.
     */
    public static void triggerEntryAdded(ResourceLocation registryId, int rawId, ResourceLocation entryId, Object value) {
        List<RegistryEntryAddedCallback<Object>> callbacks = registryCallbacks.get(registryId);
        if (callbacks != null) {
            synchronized (callbacks) {
                for (RegistryEntryAddedCallback<Object> callback : callbacks) {
                    try {
                        callback.onEntryAdded(rawId, entryId, value);
                    } catch (Exception e) {
                        System.err.println("[Fabric API Port] Error invoking RegistryEntryAddedCallback: " + e.getMessage());
                    }
                }
            }
        }
    }

    /**
     * Overloaded helper to trigger entry-added callback while dynamically resolving the raw ID.
     */
    @SuppressWarnings("unchecked")
    public static void triggerEntryAdded(ResourceLocation registryId, ResourceLocation entryId, Object value) {
        int rawId = -1;
        try {
            Class<?> registriesClass = null;
            try {
                registriesClass = Class.forName("net.minecraft.registry.Registries");
            } catch (ClassNotFoundException e) {
                registriesClass = Class.forName("net.minecraft.core.registries.BuiltInRegistries");
            }
            
            if (registriesClass != null) {
                Object registryMap = registriesClass.getField("REGISTRY").get(null);
                if (registryMap instanceof net.minecraft.core.Registry) {
                    net.minecraft.core.Registry<?> rootRegistry = (net.minecraft.core.Registry<?>) registryMap;
                    net.minecraft.core.Registry<Object> targetRegistry = (net.minecraft.core.Registry<Object>) rootRegistry.get(registryId);
                    if (targetRegistry != null) {
                        rawId = targetRegistry.getRawId(value);
                    }
                }
            }
        } catch (Throwable t) {
            // Ignore and fallback to -1
        }
        triggerEntryAdded(registryId, rawId, entryId, value);
    }

    /**
     * Wraps a RegistryBinder so that when the entry is injected, the entry added callback is triggered.
     * This coordinates directly with the RegistryStager (Registry Synchronizer).
     */
    public static <T> RegistryStager.RegistryBinder<T> wrapBinder(String registryId, RegistryStager.RegistryBinder<T> originalBinder) {
        return (entryId, value) -> {
            originalBinder.bind(entryId, value);
            try {
                ResourceLocation regLoc = new ResourceLocation(registryId);
                ResourceLocation entLoc = new ResourceLocation(entryId);
                triggerEntryAdded(regLoc, entLoc, value);
            } catch (Exception e) {
                System.err.println("[Fabric API Port] Failed to trigger entry added callback during injection: " + e.getMessage());
            }
        };
    }
}
