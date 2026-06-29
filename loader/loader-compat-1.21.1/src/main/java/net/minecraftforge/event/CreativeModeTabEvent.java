package net.minecraftforge.event;

import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

public class CreativeModeTabEvent {
    public static class BuildContents extends net.minecraftforge.eventbus.api.Event {
        private final CreativeModeTab tab;
        private final CreativeModeTab.Output output;

        public BuildContents(CreativeModeTab tab, CreativeModeTab.Output output) {
            this.tab = tab;
            this.output = output;
        }

        public CreativeModeTab getTab() {
            return tab;
        }

        public void accept(ItemStack stack) {
            if (output != null) output.accept(stack);
        }

        public void accept(net.minecraft.world.level.ItemLike item) {
            if (output != null) output.accept(item);
        }

        public void accept(ItemStack stack, CreativeModeTab.TabVisibility visibility) {
            if (output != null) output.accept(stack, visibility);
        }

        public void accept(net.minecraft.world.level.ItemLike item, CreativeModeTab.TabVisibility visibility) {
            if (output != null) output.accept(item, visibility);
        }

        public void m_246342_(ItemStack stack) {
            accept(stack);
        }

        public void m_246342_(net.minecraft.world.level.ItemLike item) {
            accept(item);
        }

        public void m_246342_(ItemStack stack, CreativeModeTab.TabVisibility visibility) {
            accept(stack, visibility);
        }

        public void m_246342_(net.minecraft.world.level.ItemLike item, CreativeModeTab.TabVisibility visibility) {
            accept(item, visibility);
        }
    }
}
