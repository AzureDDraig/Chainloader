package net.chainloader.api.client.render;

import java.util.List;
import net.chainloader.api.event.ChainEvents;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

/**
 * Platform-agnostic client-side rendering events.
 * <p>
 * This class provides Unified events for HUD drawings, custom entity overlays,
 * and item tooltip modifications.
 * </p>
 *
 * <h3>Under-the-Hood Mappings:</h3>
 * <ul>
 *   <li><b>HUD Render:</b>
 *       <ul>
 *         <li><b>Fabric:</b> Maps to {@code net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback}.</li>
 *         <li><b>NeoForge:</b> Maps to {@code RenderGuiOverlayEvent.Post} or {@code RegisterGuiOverlaysEvent}.</li>
 *       </ul>
 *   </li>
 *   <li><b>Entity Overlay Render:</b>
 *       <ul>
 *         <li><b>Fabric:</b> Intercepted via {@code WorldRenderEvents.AFTER_ENTITIES} or Mixins in {@code EntityRenderDispatcher}.</li>
 *         <li><b>NeoForge:</b> Maps to {@code RenderLivingEvent.Post} or custom model render layers.</li>
 *       </ul>
 *   </li>
 *   <li><b>Tooltip Modify:</b>
 *       <ul>
 *         <li><b>Fabric:</b> Maps to {@code net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback}.</li>
 *         <li><b>NeoForge:</b> Maps to {@code net.neoforged.neoforge.event.entity.player.ItemTooltipEvent}.</li>
 *       </ul>
 *   </li>
 * </ul>
 */
public final class ChainRenderEvents {

    private ChainRenderEvents() {} // Prevent instantiation

    /**
     * Event fired when the client is drawing the HUD.
     * Use this to render crosshairs, hotbars, status displays, or other screens overlay elements.
     */
    public static final ChainEvents.Event<HudRender> HUD_RENDER = new ChainEvents.Event<>(HudRender.class, (listeners) -> (drawContext, tickDelta) -> {
        for (HudRender listener : listeners) {
            listener.onHudRender(drawContext, tickDelta);
        }
    });

    /**
     * Event fired when rendering overlays/effects on top of an entity (e.g., custom shield, boss shield, glow).
     */
    public static final ChainEvents.Event<EntityOverlayRender> ENTITY_OVERLAY_RENDER = new ChainEvents.Event<>(EntityOverlayRender.class, (listeners) -> (entity, tickDelta, matrixStack, vertexConsumers, light) -> {
        for (EntityOverlayRender listener : listeners) {
            listener.onEntityOverlayRender(entity, tickDelta, matrixStack, vertexConsumers, light);
        }
    });

    /**
     * Event fired when item tooltips are constructed, allowing adding, replacing, or removing lines of tooltip text.
     */
    public static final ChainEvents.Event<TooltipModify> TOOLTIP_MODIFY = new ChainEvents.Event<>(TooltipModify.class, (listeners) -> (stack, tooltipLines, context) -> {
        for (TooltipModify listener : listeners) {
            listener.onTooltipModify(stack, tooltipLines, context);
        }
    });

    // --- Listener Interfaces ---

    @FunctionalInterface
    public interface HudRender {
        /**
         * Called when rendering the HUD overlay.
         *
         * @param drawContext The graphics drawing context.
         * @param tickDelta   The rendering interpolation partial ticks.
         */
        void onHudRender(DrawContext drawContext, float tickDelta);
    }

    @FunctionalInterface
    public interface EntityOverlayRender {
        /**
         * Called when drawing overlays on top of an entity.
         *
         * @param entity          The entity being rendered.
         * @param tickDelta       The rendering interpolation partial ticks.
         * @param matrixStack     The visual transformations stack.
         * @param vertexConsumers The drawing buffer provider.
         * @param light           The lighting level at the entity's position.
         */
        void onEntityOverlayRender(Entity entity, float tickDelta, MatrixStack matrixStack, VertexConsumerProvider vertexConsumers, int light);
    }

    @FunctionalInterface
    public interface TooltipModify {
        /**
         * Called when a tooltip is being generated for an item.
         *
         * @param stack        The item stack instance.
         * @param tooltipLines The mutable list of text lines to be displayed.
         * @param context      The tooltip rendering configuration context.
         */
        void onTooltipModify(ItemStack stack, List<Text> tooltipLines, TooltipContext context);
    }

    // --- Hook Methods (for Internal Loader / Mixins) ---

    public static void hudRender(DrawContext drawContext, float tickDelta) {
        HUD_RENDER.invoker().onHudRender(drawContext, tickDelta);
    }

    public static void entityOverlayRender(Entity entity, float tickDelta, MatrixStack matrixStack, VertexConsumerProvider vertexConsumers, int light) {
        ENTITY_OVERLAY_RENDER.invoker().onEntityOverlayRender(entity, tickDelta, matrixStack, vertexConsumers, light);
    }

    public static void tooltipModify(ItemStack stack, List<Text> tooltipLines, TooltipContext context) {
        TOOLTIP_MODIFY.invoker().onTooltipModify(stack, tooltipLines, context);
    }
}
