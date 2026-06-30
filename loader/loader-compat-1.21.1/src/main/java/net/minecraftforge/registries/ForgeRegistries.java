package net.minecraftforge.registries;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;

public class ForgeRegistries {
    public static final IForgeRegistry<net.minecraft.world.level.block.Block> BLOCKS = new ForgeRegistryWrapper<>(Registries.BLOCK);
    public static final IForgeRegistry<net.minecraft.world.item.Item> ITEMS = new ForgeRegistryWrapper<>(Registries.ITEM);
    public static final IForgeRegistry<net.minecraft.world.entity.EntityType<?>> ENTITY_TYPES = new ForgeRegistryWrapper<>(Registries.ENTITY_TYPE);
    public static final IForgeRegistry<net.minecraft.world.level.block.entity.BlockEntityType<?>> BLOCK_ENTITY_TYPES = new ForgeRegistryWrapper<>(Registries.BLOCK_ENTITY_TYPE);
    public static final IForgeRegistry<net.minecraft.sounds.SoundEvent> SOUND_EVENTS = new ForgeRegistryWrapper<>(Registries.SOUND_EVENT);

    public static class Keys {
        public static final ResourceKey<?> ITEMS = Registries.ITEM;
        public static final ResourceKey<?> BLOCKS = Registries.BLOCK;
        public static final ResourceKey<?> BLOCK_ENTITY_TYPES = Registries.BLOCK_ENTITY_TYPE;
        public static final ResourceKey<?> BIOMES = Registries.BIOME;
        public static final ResourceKey<?> ENCHANTMENTS = Registries.ENCHANTMENT;
        public static final ResourceKey<?> MOB_EFFECTS = Registries.MOB_EFFECT;
        public static final ResourceKey<?> ENTITY_TYPES = Registries.ENTITY_TYPE;
    }
}
