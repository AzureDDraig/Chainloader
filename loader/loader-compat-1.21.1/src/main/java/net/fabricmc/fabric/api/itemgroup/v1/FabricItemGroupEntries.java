package net.fabricmc.fabric.api.itemgroup.v1;

import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.item.ItemStack;
import java.util.Collection;

public interface FabricItemGroupEntries extends CreativeModeTab.Output {
    @Override
    default void accept(ItemStack stack, CreativeModeTab.TabVisibility visibility) {}

    default void accept(ItemLike item) {}
    default void accept(ItemStack stack) {}
    default void accept(ItemLike item, CreativeModeTab.TabVisibility visibility) {}

    default void add(ItemStack stack) {}
    default void add(ItemLike item) {}
    default void add(ItemStack stack, CreativeModeTab.TabVisibility visibility) {}
    default void add(ItemLike item, CreativeModeTab.TabVisibility visibility) {}
    
    default void addBefore(ItemLike helper, ItemStack... stacks) {}
    default void addBefore(ItemLike helper, Collection<ItemStack> stacks) {}
    default void addBefore(ItemLike helper, ItemLike... items) {}
    default void addBefore(ItemLike helper, ItemStack stack, CreativeModeTab.TabVisibility visibility) {}
    default void addBefore(ItemLike helper, Collection<ItemStack> stacks, CreativeModeTab.TabVisibility visibility) {}
    default void addBefore(ItemLike helper, ItemLike item, CreativeModeTab.TabVisibility visibility) {}
    
    default void addAfter(ItemLike helper, ItemStack... stacks) {}
    default void addAfter(ItemLike helper, Collection<ItemStack> stacks) {}
    default void addAfter(ItemLike helper, ItemLike... items) {}
    default void addAfter(ItemLike helper, ItemStack stack, CreativeModeTab.TabVisibility visibility) {}
    default void addAfter(ItemLike helper, Collection<ItemStack> stacks, CreativeModeTab.TabVisibility visibility) {}
    default void addAfter(ItemLike helper, ItemLike item, CreativeModeTab.TabVisibility visibility) {}
}
