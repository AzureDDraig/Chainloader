package net.fabricmc.fabric.api.itemgroup.v1;

import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.item.ItemStack;
import java.util.Collection;

public class FabricItemGroupEntries implements CreativeModeTab.Output {
    @Override
    public void accept(ItemStack stack, CreativeModeTab.TabVisibility visibility) {}

    public void accept(ItemLike item) {}
    public void accept(ItemStack stack) {}
    public void accept(ItemLike item, CreativeModeTab.TabVisibility visibility) {}

    public void add(ItemStack stack) {}
    public void add(ItemLike item) {}
    public void add(ItemStack stack, CreativeModeTab.TabVisibility visibility) {}
    public void add(ItemLike item, CreativeModeTab.TabVisibility visibility) {}
    
    public void addBefore(ItemLike helper, ItemStack... stacks) {}
    public void addBefore(ItemLike helper, Collection<ItemStack> stacks) {}
    public void addBefore(ItemLike helper, ItemLike... items) {}
    public void addBefore(ItemLike helper, ItemStack stack, CreativeModeTab.TabVisibility visibility) {}
    public void addBefore(ItemLike helper, Collection<ItemStack> stacks, CreativeModeTab.TabVisibility visibility) {}
    public void addBefore(ItemLike helper, ItemLike item, CreativeModeTab.TabVisibility visibility) {}
    
    public void addAfter(ItemLike helper, ItemStack... stacks) {}
    public void addAfter(ItemLike helper, Collection<ItemStack> stacks) {}
    public void addAfter(ItemLike helper, ItemLike... items) {}
    public void addAfter(ItemLike helper, ItemStack stack, CreativeModeTab.TabVisibility visibility) {}
    public void addAfter(ItemLike helper, Collection<ItemStack> stacks, CreativeModeTab.TabVisibility visibility) {}
    public void addAfter(ItemLike helper, ItemLike item, CreativeModeTab.TabVisibility visibility) {}
}
