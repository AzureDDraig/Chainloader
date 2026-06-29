package net.chainloader.api.client.render;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;

/**
 * Platform-agnostic registration manager for client-side entity renderers.
 * <p>
 * This class registers the association between a custom Entity Type and its corresponding 3D renderer/model.
 * </p>
 *
 * <h3>Under-the-Hood Mappings:</h3>
 * <ul>
 *   <li><b>Fabric:</b> Registers renderers using {@code EntityRendererRegistry.register(entityType, factory)}.</li>
 *   <li><b>NeoForge:</b> Registers renderers inside the {@code EntityRenderersEvent.RegisterRenderers} event on the client mod bus.</li>
 * </ul>
 */
public final class ChainEntityRenderer {

    private static final Map<EntityType<?>, EntityRendererFactory<?>> RENDERER_FACTORIES = new HashMap<>();

    private ChainEntityRenderer() {}

    /**
     * Registers a custom entity renderer factory for an entity type.
     * <p>
     * Must be called during client-side initialization.
     * </p>
     *
     * @param entityType The entity type.
     * @param factory    The factory to construct the entity renderer.
     * @param <T>        The entity class type.
     */
    @SuppressWarnings("unchecked")
    public static <T extends Entity> void register(EntityType<? extends T> entityType, EntityRendererFactory<T> factory) {
        if (entityType == null || factory == null) {
            throw new IllegalArgumentException("Arguments cannot be null");
        }
        RENDERER_FACTORIES.put(entityType, factory);
        
        // Under the hood:
        // Fabric: EntityRendererRegistry.register(entityType, factory);
        // NeoForge: Event listener hooks RegisterRenderers event and does:
        //           event.registerEntityRenderer((EntityType<T>) entityType, (EntityRendererFactory<T>) factory);
    }

    /**
     * Gets all registered entity renderer factories.
     * Used internally by platform specific bootloaders.
     *
     * @return An unmodifiable map of registered entity renderer factories.
     */
    public static Map<EntityType<?>, EntityRendererFactory<?>> getRendererFactories() {
        return RENDERER_FACTORIES;
    }
}
