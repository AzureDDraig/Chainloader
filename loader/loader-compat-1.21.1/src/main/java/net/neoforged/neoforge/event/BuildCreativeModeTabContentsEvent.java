package net.neoforged.neoforge.event;

import net.neoforged.bus.api.Event;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraft.resources.ResourceKey;

public class BuildCreativeModeTabContentsEvent extends Event implements net.minecraft.world.item.CreativeModeTab.Output {
    private final CreativeModeTab tab;
    private final ResourceKey<CreativeModeTab> tabKey;
    private final CreativeModeTab.ItemDisplayParameters parameters;
    private final CreativeModeTab.Output output;

    public BuildCreativeModeTabContentsEvent(CreativeModeTab tab, ResourceKey<CreativeModeTab> tabKey, CreativeModeTab.ItemDisplayParameters parameters, CreativeModeTab.Output output) {
        this.tab = tab;
        this.tabKey = tabKey;
        this.parameters = parameters;
        this.output = output;
    }

    public CreativeModeTab getTab() {
        return tab;
    }

    public ResourceKey<CreativeModeTab> getTabKey() {
        return tabKey;
    }

    public CreativeModeTab.ItemDisplayParameters getParameters() {
        return parameters;
    }

    @Override
    public void accept(ItemStack stack) {
        if (output != null) output.accept(stack);
    }

    @Override
    public void accept(ItemStack stack, CreativeModeTab.TabVisibility visibility) {
        if (output != null) output.accept(stack, visibility);
    }

    @Override
    public void accept(ItemLike item) {
        if (output != null) output.accept(item);
    }

    @Override
    public void accept(ItemLike item, CreativeModeTab.TabVisibility visibility) {
        if (output != null) output.accept(item, visibility);
    }
}
