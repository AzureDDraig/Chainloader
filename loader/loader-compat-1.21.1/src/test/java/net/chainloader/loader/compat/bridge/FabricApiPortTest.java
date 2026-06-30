package net.chainloader.loader.compat.bridge;

import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Tests the FabricApiPort compatibility bridges and emulators.
 */
public class FabricApiPortTest {

    @BeforeEach
    public void setUp() {
        // Reset or prepare static mappings if needed
    }

    @Test
    public void testFuelRegistry() {
        Object mockItem = new Object();
        FabricApiPort.FuelRegistry.INSTANCE.add(mockItem, 200);
        assertEquals(200, FabricApiPort.FuelRegistry.INSTANCE.timeOf(mockItem));
        
        FabricApiPort.FuelRegistry.INSTANCE.remove(mockItem);
        assertEquals(0, FabricApiPort.FuelRegistry.INSTANCE.timeOf(mockItem));
    }

    @Test
    public void testFlammableBlockRegistry() {
        Object mockBlock = new Object();
        FabricApiPort.FlammableBlockRegistry.getDefaultInstance().add(mockBlock, 15, 30);
        
        FabricApiPort.FlammableBlockRegistry.Entry entry = FabricApiPort.FlammableBlockRegistry.getDefaultInstance().get(mockBlock);
        assertEquals(15, entry.burnChance());
        assertEquals(30, entry.spreadChance());
        
        FabricApiPort.FlammableBlockRegistry.getDefaultInstance().remove(mockBlock);
        entry = FabricApiPort.FlammableBlockRegistry.getDefaultInstance().get(mockBlock);
        assertEquals(0, entry.burnChance());
        assertEquals(0, entry.spreadChance());
    }

    @Test
    public void testCompostingChanceRegistry() {
        Object mockItem = new Object();
        FabricApiPort.CompostingChanceRegistry.INSTANCE.add(mockItem, 0.65f);
        assertEquals(0.65f, FabricApiPort.CompostingChanceRegistry.INSTANCE.get(mockItem), 0.01f);
        
        FabricApiPort.CompostingChanceRegistry.INSTANCE.remove(mockItem);
        assertEquals(0.0f, FabricApiPort.CompostingChanceRegistry.INSTANCE.get(mockItem), 0.01f);
    }

    @Test
    public void testStrippableBlockRegistry() {
        Object mockLog = new Object();
        Object mockStrippedLog = new Object();
        FabricApiPort.StrippableBlockRegistry.register(mockLog, mockStrippedLog);
        assertEquals(mockStrippedLog, FabricApiPort.StrippableBlockRegistry.getStrippables().get(mockLog));
    }

    @Test
    public void testBlockEntityRendererRegistry() {
        Object mockType = new Object();
        Object mockFactory = new Object();
        FabricApiPort.BlockEntityRendererRegistry.register(mockType, mockFactory);
        assertEquals(mockFactory, FabricApiPort.BlockEntityRendererRegistry.getRenderers().get(mockType));
    }

    @Test
    public void testColorProviderRegistry() {
        Object mockBlock = new Object();
        Object mockProvider = new Object();
        FabricApiPort.ColorProviderRegistry.BLOCK.register(mockProvider, mockBlock);
        assertEquals(mockProvider, FabricApiPort.ColorProviderRegistry.BLOCK.getProviders().get(mockBlock));
    }

    @Test
    public void testRegistryEntryAddedCallback() {
        ResourceLocation registryId = new ResourceLocation("minecraft:block");
        ResourceLocation entryId = new ResourceLocation("my_mod:my_block");
        Object mockBlock = new Object();
        
        AtomicBoolean callbackFired = new AtomicBoolean(false);
        AtomicInteger receivedRawId = new AtomicInteger(-2);
        
        FabricApiPort.registerEntryAdded(registryId, (rawId, id, obj) -> {
            callbackFired.set(true);
            receivedRawId.set(rawId);
            assertEquals(entryId, id);
            assertEquals(mockBlock, obj);
        });

        FabricApiPort.triggerEntryAdded(registryId, 42, entryId, mockBlock);
        
        assertTrue(callbackFired.get(), "RegistryEntryAddedCallback should have been fired");
        assertEquals(42, receivedRawId.get(), "Should receive matching raw ID");
    }

    @Test
    public void testCreativeTabEvents() {
        // Test multi-tab registration isolation (no leaks)
        net.minecraft.resources.ResourceLocation reg = new net.minecraft.resources.ResourceLocation("minecraft", "creative_mode_tab");
        net.minecraft.resources.ResourceKey<net.minecraft.world.item.CreativeModeTab> tabKey1 = 
            net.minecraft.resources.ResourceKey.create(reg, new net.minecraft.resources.ResourceLocation("mod", "tab1"));
        net.minecraft.resources.ResourceKey<net.minecraft.world.item.CreativeModeTab> tabKey2 = 
            net.minecraft.resources.ResourceKey.create(reg, new net.minecraft.resources.ResourceLocation("mod", "tab2"));

        java.util.concurrent.atomic.AtomicInteger fireCount1 = new java.util.concurrent.atomic.AtomicInteger(0);
        java.util.concurrent.atomic.AtomicInteger fireCount2 = new java.util.concurrent.atomic.AtomicInteger(0);

        net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents.modifyEntriesEvent(tabKey1).register(entries -> {
            fireCount1.incrementAndGet();
        });

        net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents.modifyEntriesEvent(tabKey2).register(entries -> {
            fireCount2.incrementAndGet();
        });

        // Fire tab1
        java.util.List<net.minecraft.world.item.ItemStack> collected = new java.util.ArrayList<>();
        net.minecraft.world.item.CreativeModeTab.Output mockOutput = item -> {
            if (item instanceof net.minecraft.world.item.ItemStack) {
                collected.add((net.minecraft.world.item.ItemStack) item);
            }
        };
        
        net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroupEntriesWrapper wrapper = 
            new net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroupEntriesWrapper(mockOutput);

        net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents.modifyEntriesEvent(tabKey1).invoker().modifyEntries(wrapper);

        assertEquals(1, fireCount1.get());
        assertEquals(0, fireCount2.get());

        // Fire tab2
        net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents.modifyEntriesEvent(tabKey2).invoker().modifyEntries(wrapper);

        assertEquals(1, fireCount1.get());
        assertEquals(1, fireCount2.get());

        // Test positioning defaults
        net.minecraft.world.item.Item itemA = new net.minecraft.world.item.Item();
        net.minecraft.world.item.Item itemB = new net.minecraft.world.item.Item();
        net.minecraft.world.item.Item itemC = new net.minecraft.world.item.Item();

        net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroupEntriesWrapper wrapper2 = 
            new net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroupEntriesWrapper(mockOutput);

        wrapper2.add(itemA); // [A]
        wrapper2.addBefore(itemA, itemB); // [B, A]
        wrapper2.addAfter(itemA, itemC); // [B, A, C]

        java.util.List<net.minecraft.world.item.ItemStack> added = wrapper2.getAddedStacks();
        assertEquals(3, added.size());
        assertEquals(itemB, added.get(0).asItem());
        assertEquals(itemA, added.get(1).asItem());
        assertEquals(itemC, added.get(2).asItem());

        // Test fallback to end when helper not found
        net.minecraft.world.item.Item itemD = new net.minecraft.world.item.Item();
        net.minecraft.world.item.Item missingItem = new net.minecraft.world.item.Item();
        wrapper2.addBefore(missingItem, itemD); // [B, A, C, D]
        assertEquals(4, added.size());
        assertEquals(itemD, added.get(3).asItem());

        // Commit to output
        collected.clear();
        wrapper2.commit();
        assertEquals(4, collected.size());
        assertEquals(itemB, collected.get(0).asItem());
    }
}
