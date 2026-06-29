package net.fabricmc.fabric.api.client.rendering.v1;

import net.minecraft.client.color.block.BlockColor;
import net.minecraft.client.color.item.ItemColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.item.Item;

public interface ColorProviderRegistry<Provider, Objects> {
    ColorProviderRegistry<BlockColor, Block> BLOCK = new ColorProviderRegistry<BlockColor, Block>() {
        @Override
        public void register(BlockColor provider, Block... objects) {
            System.out.println("[ChainLoader] BlockColor registered for " + objects.length + " blocks.");
        }
    };

    ColorProviderRegistry<ItemColor, net.minecraft.world.level.ItemLike> ITEM = new ColorProviderRegistry<ItemColor, net.minecraft.world.level.ItemLike>() {
        @Override
        public void register(ItemColor provider, net.minecraft.world.level.ItemLike... objects) {
            System.out.println("[ChainLoader] ItemColor registered for " + objects.length + " items.");
        }
    };

    void register(Provider provider, Objects... objects);
}
