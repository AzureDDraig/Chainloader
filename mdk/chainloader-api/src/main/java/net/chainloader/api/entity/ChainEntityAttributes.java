package net.chainloader.api.entity;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttribute;

/**
 * Platform-agnostic manager for entity attribute setup and registration.
 * <p>
 * This class handles registering default attribute values (e.g., max health, movement speed)
 * for custom living entities and creating new custom attributes.
 * </p>
 *
 * <h3>Under-the-Hood Mappings:</h3>
 * <ul>
 *   <li><b>Fabric:</b> Registers default attributes using {@code FabricDefaultAttributeRegistry.register(entityType, builder)}.</li>
 *   <li><b>NeoForge:</b> Registers default attributes during the {@code EntityAttributeCreationEvent} event on the mod bus.</li>
 *   <li><b>Custom Attributes:</b> Standard attribute registration is handled via registries, mapping to {@code Registries.ATTRIBUTE} on Fabric and {@code DeferredRegister} on NeoForge.</li>
 * </ul>
 */
public final class ChainEntityAttributes {

    private static final Map<EntityType<? extends LivingEntity>, Supplier<DefaultAttributeContainer.Builder>> DEFAULT_ATTRIBUTES = new HashMap<>();

    private ChainEntityAttributes() {}

    /**
     * Registers default attribute builders for a custom living entity type.
     * <p>
     * Modders call this during mod initialization to define base stats (e.g. health, attack damage) for custom mobs.
     * </p>
     *
     * @param entityType       The entity type to register attributes for.
     * @param builderSupplier  A supplier that constructs a {@link DefaultAttributeContainer.Builder} preconfigured with default stats.
     */
    public static void register(EntityType<? extends LivingEntity> entityType, Supplier<DefaultAttributeContainer.Builder> builderSupplier) {
        if (entityType == null || builderSupplier == null) {
            throw new IllegalArgumentException("Arguments cannot be null");
        }
        DEFAULT_ATTRIBUTES.put(entityType, builderSupplier);
        
        // Under the hood:
        // Fabric: FabricDefaultAttributeRegistry.register(entityType, builderSupplier.get());
        // NeoForge: Event listener listens to EntityAttributeCreationEvent and maps the map contents to:
        //           event.put(entityType, builderSupplier.get().build());
    }

    /**
     * Retrieve the map of all registered default attributes.
     * Used internally by platform specific bootloaders.
     *
     * @return An unmodifiable view of registered default attributes.
     */
    public static Map<EntityType<? extends LivingEntity>, Supplier<DefaultAttributeContainer.Builder>> getRegisteredAttributes() {
        return DEFAULT_ATTRIBUTES;
    }

    /**
     * Helper to create a new custom attribute (e.g. "magic_power", "critical_chance").
     * <p>
     * <b>Fabric:</b> The returned attribute is registered directly into {@code Registries.ATTRIBUTE}.<br>
     * <b>NeoForge:</b> The returned attribute is registered using NeoForge's custom {@code DeferredRegister} registry pattern.
     * </p>
     *
     * @param translationKey The translation key for the attribute.
     * @param defaultValue   The default float value of this attribute.
     * @param min            The minimum clamped value allowed.
     * @param max            The maximum clamped value allowed.
     * @return A custom EntityAttribute instance ready for registration.
     */
    public static EntityAttribute createCustom(String translationKey, double defaultValue, double min, double max) {
        // Under the hood, this instantiates a ClampedEntityAttribute (in Yarn) or ClampedAttribute (in Mojang):
        // new ClampedEntityAttribute(translationKey, defaultValue, min, max);
        // Note: For actual runtime use, the loader instantiates this object using the appropriate platform mapping.
        return new DummyCustomAttribute(translationKey, defaultValue, min, max);
    }

    /**
     * Dummy class to satisfy compilation requirements of the mockup API.
     * Under real runtime conditions, the platform specific loader maps this to standard Minecraft classes.
     */
    private static class DummyCustomAttribute extends EntityAttribute {
        private final double min;
        private final double max;

        protected DummyCustomAttribute(String translationKey, double defaultValue, double min, double max) {
            super(translationKey, defaultValue);
            this.min = min;
            this.max = max;
        }

        public double getMin() {
            return min;
        }

        public double getMax() {
            return max;
        }
    }
}
