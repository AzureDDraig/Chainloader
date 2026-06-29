package net.minecraftforge.registries;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;

public class ForgeRegistries {
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
