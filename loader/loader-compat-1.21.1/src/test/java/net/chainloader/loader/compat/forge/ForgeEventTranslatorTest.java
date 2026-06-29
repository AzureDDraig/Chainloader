package net.chainloader.loader.compat.forge;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import net.chainloader.api.event.BlockBreakEvent;
import net.chainloader.api.event.ChainEventBus;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventBus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests the bi-directional event bridging capabilities of ForgeEventTranslator
 * including translation correctness, cancellation propagation, and loop prevention.
 */
public class ForgeEventTranslatorTest {

    private ChainEventBus hostBus;
    private EventBus forgeBus;
    private ForgeEventTranslator translator;

    @BeforeEach
    public void setUp() {
        hostBus = new ChainEventBus();
        forgeBus = new EventBus();
        translator = new ForgeEventTranslator(hostBus, forgeBus);
        translator.registerBridges();
    }

    @Test
    public void testHostToForgeTranslation() {
        AtomicBoolean forgeHandlerCalled = new AtomicBoolean(false);
        Object mockLevel = new Object();
        Object mockPos = new Object();
        Object mockState = new Object();
        Object mockPlayer = new Object();

        // Register a legacy Forge listener
        forgeBus.addListener(BlockEvent.BreakEvent.class, event -> {
            forgeHandlerCalled.set(true);
            assertSame(mockLevel, event.getLevel());
            assertSame(mockPos, event.getPos());
            assertSame(mockState, event.getState());
            assertSame(mockPlayer, event.getPlayer());
            
            // Modify some event properties
            event.setExpToDrop(15);
            event.setCanceled(true);
        });

        // Fire host event
        BlockBreakEvent hostEvent = new BlockBreakEvent(mockLevel, mockPos, mockState, mockPlayer);
        hostEvent.setXpToDrop(5);

        boolean hostResult = hostBus.post(hostEvent);

        assertTrue(forgeHandlerCalled.get(), "Forge listener should have been invoked");
        assertTrue(hostResult, "Host event should be canceled because Forge listener canceled it");
        assertTrue(hostEvent.isCanceled(), "Host event cancel state should be synchronized");
        assertEquals(15, hostEvent.getXpToDrop(), "XP/Exp to drop should be synchronized from Forge back to Host");
    }

    @Test
    public void testForgeToHostTranslation() {
        AtomicBoolean hostHandlerCalled = new AtomicBoolean(false);
        Object mockLevel = new Object();
        Object mockPos = new Object();
        Object mockState = new Object();
        Object mockPlayer = new Object();

        // Register a host event listener
        hostBus.register(BlockBreakEvent.class, event -> {
            hostHandlerCalled.set(true);
            assertSame(mockLevel, event.getWorld());
            assertSame(mockPos, event.getPos());
            assertSame(mockState, event.getState());
            assertSame(mockPlayer, event.getPlayer());

            // Modify some properties
            event.setXpToDrop(50);
            event.setCanceled(true);
        });

        // Fire Forge event
        BlockEvent.BreakEvent forgeEvent = new BlockEvent.BreakEvent(mockLevel, mockPos, mockState, mockPlayer);
        forgeEvent.setExpToDrop(10);

        boolean forgeResult = forgeBus.post(forgeEvent);

        assertTrue(hostHandlerCalled.get(), "Host listener should have been invoked");
        assertTrue(forgeResult, "Forge event should be canceled because Host listener canceled it");
        assertTrue(forgeEvent.isCanceled(), "Forge event cancel state should be synchronized");
        assertEquals(50, forgeEvent.getExpToDrop(), "Exp/XP to drop should be synchronized from Host back to Forge");
    }

    @Test
    public void testLoopPrevention() {
        AtomicInteger hostCallCount = new AtomicInteger(0);
        AtomicInteger forgeCallCount = new AtomicInteger(0);

        Object mockLevel = new Object();
        Object mockPos = new Object();
        Object mockState = new Object();
        Object mockPlayer = new Object();

        // Listeners that do standard processing
        hostBus.register(BlockBreakEvent.class, event -> {
            hostCallCount.incrementAndGet();
        });

        forgeBus.addListener(BlockEvent.BreakEvent.class, event -> {
            forgeCallCount.incrementAndGet();
        });

        // Post a host event. It will map to a Forge event, which posts to Forge bus.
        // If there was no loop prevention, posting on Forge bus would map back to Host bus and repeat.
        BlockBreakEvent hostEvent = new BlockBreakEvent(mockLevel, mockPos, mockState, mockPlayer);
        hostBus.post(hostEvent);

        assertEquals(1, hostCallCount.get(), "Host listener should be called exactly once");
        assertEquals(1, forgeCallCount.get(), "Forge listener should be called exactly once");

        // Reset counters and test firing from the Forge side
        hostCallCount.set(0);
        forgeCallCount.set(0);

        BlockEvent.BreakEvent forgeEvent = new BlockEvent.BreakEvent(mockLevel, mockPos, mockState, mockPlayer);
        forgeBus.post(forgeEvent);

        assertEquals(1, hostCallCount.get(), "Host listener should be called exactly once");
        assertEquals(1, forgeCallCount.get(), "Forge listener should be called exactly once");
    }
}
