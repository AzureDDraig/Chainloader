package net.fabricmc.fabric.api.itemgroup.v1;

import net.fabricmc.fabric.api.event.Event;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ItemGroupEvents {
    private static final Map<ResourceKey<CreativeModeTab>, Event<ModifyEntries>> EVENTS = new ConcurrentHashMap<>();

    public static Event<ModifyEntries> modifyEntriesEvent(ResourceKey<CreativeModeTab> tabKey) {
        if (tabKey == null) {
            throw new IllegalArgumentException("tabKey cannot be null");
        }
        return EVENTS.computeIfAbsent(tabKey, k -> new Event<>(ModifyEntries.class));
    }

    public static Event<ModifyEntries> modifyEntriesEvent(net.minecraft.resources.ResourceLocation tabId) {
        if (tabId == null) {
            throw new IllegalArgumentException("tabId cannot be null");
        }
        ResourceKey<CreativeModeTab> tabKey = ResourceKey.create(net.minecraft.core.registries.Registries.CREATIVE_MODE_TAB, tabId);
        return modifyEntriesEvent(tabKey);
    }

    @FunctionalInterface
    public interface ModifyEntries {
        void modifyEntries(FabricItemGroupEntries entries);
    }
}
