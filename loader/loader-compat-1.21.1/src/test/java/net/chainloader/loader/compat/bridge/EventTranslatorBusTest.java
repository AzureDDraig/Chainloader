package net.chainloader.loader.compat.bridge;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import net.chainloader.api.event.BlockBreakEvent;
import net.chainloader.api.event.ChainEventBus;
import net.chainloader.api.event.RegistrySyncEvent;
import net.chainloader.loader.compat.fabric.FabricLoaderShim;
import net.chainloader.loader.compat.registry.RegistryStager;
import net.chainloader.loader.compat.neoforge.NeoForgeEventTranslator;
import net.minecraftforge.eventbus.api.EventBus;
import net.minecraftforge.event.level.BlockEvent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for the EventTranslatorBus to ensure bi-directional translations,
 * thread-local loop prevention, and integration with RegistryStager function correctly.
 */
public class EventTranslatorBusTest {

    private ChainEventBus hostBus;
    private EventBus forgeBus;
    private NeoForgeEventTranslator.NeoForgeEventBus neoforgeBus;
    private EventTranslatorBus translatorBus;

    @BeforeEach
    public void setUp() {
        hostBus = new ChainEventBus();
        forgeBus = new EventBus();
        neoforgeBus = new NeoForgeEventTranslator.NeoForgeEventBus();
        
        translatorBus = EventTranslatorBus.getInstance();
        translatorBus.init(hostBus, forgeBus, neoforgeBus);
        
        // Reset FabricLoaderShim and RegistryStager states
        RegistryStager.getInstance().clear();
    }

    @Test
    public void testHostToOthersBlockBreakTranslation() {
        AtomicBoolean forgeCalled = new AtomicBoolean(false);
        AtomicBoolean neoforgeCalled = new AtomicBoolean(false);
        AtomicBoolean fabricCalled = new AtomicBoolean(false);

        Object mockWorld = new Object();
        Object mockPos = new Object();
        Object mockState = new Object();
        Object mockPlayer = new Object();

        // 1. Register Forge Listener
        forgeBus.addListener(BlockEvent.BreakEvent.class, event -> {
            forgeCalled.set(true);
            assertSame(mockWorld, event.getLevel());
            assertSame(mockPos, event.getPos());
            assertSame(mockState, event.getState());
            assertSame(mockPlayer, event.getPlayer());
            event.setExpToDrop(12);
        });

        // 2. Register NeoForge Listener (using subscriber instance registered on neoforgeBus)
        neoforgeBus.register(new Object() {
            @NeoForgeEventTranslator.SubscribeEvent
            public void onNeoBreak(NeoForgeEventTranslator.BlockEvent.BreakEvent event) {
                neoforgeCalled.set(true);
                assertSame(mockWorld, event.getLevel());
                assertSame(mockPos, event.getPos());
                assertSame(mockState, event.getState());
                assertSame(mockPlayer, event.getPlayer());
                event.setCanceled(true);
            }
        });

        // 3. Register Fabric Callback
        FabricLoaderShim.getInstance().registerEventCallback("fabric:player_block_break",
            (EventTranslatorBus.FabricBlockBreakCallback) (world, player, pos, state) -> {
                fabricCalled.set(true);
                assertSame(mockWorld, world);
                assertSame(mockPlayer, player);
                assertSame(mockPos, pos);
                assertSame(mockState, state);
                return true; // allow break
            });

        // 4. Post Host Event
        BlockBreakEvent hostEvent = new BlockBreakEvent(mockWorld, mockPos, mockState, mockPlayer);
        hostEvent.setXpToDrop(5);

        hostBus.post(hostEvent);

        assertTrue(forgeCalled.get(), "Forge listener should be called");
        assertTrue(neoforgeCalled.get(), "NeoForge listener should be called");
        assertTrue(fabricCalled.get(), "Fabric callback should be called");
        assertTrue(hostEvent.isCanceled(), "Host event should be canceled because NeoForge canceled it");
        assertEquals(12, hostEvent.getXpToDrop(), "XP should be synced from Forge's modification");
    }

    @Test
    public void testForgeToHostBlockBreakTranslation() {
        AtomicBoolean hostCalled = new AtomicBoolean(false);
        Object mockWorld = new Object();
        Object mockPos = new Object();
        Object mockState = new Object();
        Object mockPlayer = new Object();

        hostBus.register(BlockBreakEvent.class, event -> {
            hostCalled.set(true);
            assertSame(mockWorld, event.getWorld());
            assertSame(mockPos, event.getPos());
            assertSame(mockState, event.getState());
            assertSame(mockPlayer, event.getPlayer());
            event.setXpToDrop(42);
            event.setCanceled(true);
        });

        BlockEvent.BreakEvent forgeEvent = new BlockEvent.BreakEvent(mockWorld, mockPos, mockState, mockPlayer);
        forgeEvent.setExpToDrop(1);

        forgeBus.post(forgeEvent);

        assertTrue(hostCalled.get(), "Host listener should be called");
        assertTrue(forgeEvent.isCanceled(), "Forge event should be canceled");
        assertEquals(42, forgeEvent.getExpToDrop(), "Forge XP should be updated to 42");
    }

    @Test
    public void testNeoForgeToHostBlockBreakTranslation() {
        AtomicBoolean hostCalled = new AtomicBoolean(false);
        Object mockWorld = new Object();
        Object mockPos = new Object();
        Object mockState = new Object();
        Object mockPlayer = new Object();

        hostBus.register(BlockBreakEvent.class, event -> {
            hostCalled.set(true);
            event.setCanceled(true);
        });

        NeoForgeEventTranslator.BlockEvent.BreakEvent neoEvent = 
            new NeoForgeEventTranslator.BlockEvent.BreakEvent(mockWorld, mockPos, mockState, mockPlayer, 2);

        neoforgeBus.post(neoEvent);

        assertTrue(hostCalled.get(), "Host listener should be called");
        assertTrue(neoEvent.isCanceled(), "NeoForge event should be canceled");
    }

    @Test
    public void testFabricToHostBlockBreakTranslation() {
        AtomicBoolean hostCalled = new AtomicBoolean(false);
        Object mockWorld = new Object();
        Object mockPos = new Object();
        Object mockState = new Object();
        Object mockPlayer = new Object();

        hostBus.register(BlockBreakEvent.class, event -> {
            hostCalled.set(true);
            event.setCanceled(true);
        });

        boolean allowed = translatorBus.translateFabricToHostBlockBreak(mockWorld, mockPlayer, mockPos, mockState);

        assertTrue(hostCalled.get(), "Host listener should be called");
        assertFalse(allowed, "Break should not be allowed since host listener canceled it");
    }

    @Test
    public void testRegistryStagerCoordination() {
        AtomicBoolean hostSyncCalled = new AtomicBoolean(false);
        AtomicInteger registerCount = new AtomicInteger(0);

        // 1. Stage a mock block
        Object mockBlock = new Object();
        RegistryStager.registerLegacyBlock("testmod", "super_block", () -> mockBlock);

        // 2. Listen for host RegistrySyncEvent
        hostBus.register(RegistrySyncEvent.class, event -> {
            hostSyncCalled.set(true);
            assertEquals("minecraft:block", event.getRegistryId());
        });

        // 3. Fire NeoForge RegisterEvent
        NeoForgeEventTranslator.RegisterEvent registerEvent = new NeoForgeEventTranslator.RegisterEvent("minecraft:block");
        
        // Mock register handler on the NeoForge helper side
        neoforgeBus.register(new Object() {
            @NeoForgeEventTranslator.SubscribeEvent
            public void onRegister(NeoForgeEventTranslator.RegisterEvent event) {
                // Translator bus should intercept and perform injection, but let's count invokes to see if it occurred
            }
        });

        // Directly invoke translator to simulate the event intercept
        translatorBus.translateNeoForgeRegister(registerEvent);

        assertTrue(hostSyncCalled.get(), "Host RegistrySyncEvent should be fired");
        // Check that entries for minecraft:block were cleared (meaning they were successfully injected/processed)
        assertTrue(RegistryStager.getInstance().getStagedEntries("minecraft:block").isEmpty(),
            "Staged entries should be injected and cleared");
    }

    @Test
    public void testLoopPrevention() {
        AtomicInteger hostCount = new AtomicInteger(0);
        AtomicInteger forgeCount = new AtomicInteger(0);

        hostBus.register(BlockBreakEvent.class, event -> {
            hostCount.incrementAndGet();
        });

        forgeBus.addListener(BlockEvent.BreakEvent.class, event -> {
            forgeCount.incrementAndGet();
        });

        // Fire Host event:
        // Host -> Forge (dispatched) -> Loop prevention prevents Forge -> Host
        BlockBreakEvent hostEvent = new BlockBreakEvent("world", "pos", "state", "player");
        hostBus.post(hostEvent);

        assertEquals(1, hostCount.get(), "Host listener should be called exactly once");
        assertEquals(1, forgeCount.get(), "Forge listener should be called exactly once");
    }
}
