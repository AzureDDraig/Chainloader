package net.fabricmc.fabric.api.itemgroup.v1;

import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.item.ItemStack;
import java.util.Collection;

public interface FabricItemGroupEntries extends CreativeModeTab.Output {
    void add(ItemStack stack);
    void add(ItemLike item);
    void add(ItemStack stack, CreativeModeTab.TabVisibility visibility);
    void add(ItemLike item, CreativeModeTab.TabVisibility visibility);
    
    void addBefore(ItemLike helper, ItemStack... stacks);
    void addBefore(ItemLike helper, Collection<ItemStack> stacks);
    void addBefore(ItemLike helper, ItemLike... items);
    void addBefore(ItemLike helper, ItemStack stack, CreativeModeTab.TabVisibility visibility);
    void addBefore(ItemLike helper, Collection<ItemStack> stacks, CreativeModeTab.TabVisibility visibility);
    void addBefore(ItemLike helper, ItemLike item, CreativeModeTab.TabVisibility visibility);
    
    void addAfter(ItemLike helper, ItemStack... stacks);
    void addAfter(ItemLike helper, Collection<ItemStack> stacks);
    void addAfter(ItemLike helper, ItemLike... items);
    void addAfter(ItemLike helper, ItemStack stack, CreativeModeTab.TabVisibility visibility);
    void addAfter(ItemLike helper, Collection<ItemStack> stacks, CreativeModeTab.TabVisibility visibility);
    void addAfter(ItemLike helper, ItemLike item, CreativeModeTab.TabVisibility visibility);
}
