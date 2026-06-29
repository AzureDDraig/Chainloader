package net.chainloader.loader.compat.forge;

import java.util.HashSet;
import java.util.Set;
import net.chainloader.api.event.BlockBreakEvent;
import net.chainloader.api.event.ChainEventBus;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.IEventBus;

/**
 * Handles bi-directional translation between ChainLoader host events and
 * emulated Forge/NeoForge events.
 * 
 * It registers listeners on both the host event bus and the compatibility
 * event bus, translating and propagating events while preventing infinite loops.
 */
public class ForgeEventTranslator {

    private final ChainEventBus hostBus;
    private final IEventBus forgeBus;

    // Track active event instances on the current thread to prevent infinite translation loops.
    private static final ThreadLocal<Set<Object>> activeEvents = ThreadLocal.withInitial(HashSet::new);

    /**
     * Constructs a new ForgeEventTranslator.
     *
     * @param hostBus  The host event bus (ChainLoader native).
     * @param forgeBus The compatibility event bus (Forge/NeoForge emulation).
     */
    public ForgeEventTranslator(ChainEventBus hostBus, IEventBus forgeBus) {
        this.hostBus = hostBus;
        this.forgeBus = forgeBus;
    }

    /**
     * Initializes and registers the bi-directional event bridges.
     */
    public void registerBridges() {
        // 1. Host Event -> Forge Event bridge
        hostBus.register(BlockBreakEvent.class, this::translateHostToForgeBlockBreak);

        // 2. Forge Event -> Host Event bridge
        forgeBus.addListener(BlockEvent.BreakEvent.class, this::translateForgeToHostBlockBreak);
    }

    /**
     * Translates a ChainLoader host BlockBreakEvent to a Forge BlockEvent.BreakEvent
     * and posts it to the emulator event bus.
     *
     * @param hostEvent The native block break event.
     */
    private void translateHostToForgeBlockBreak(BlockBreakEvent hostEvent) {
        // Prevent loop if this event was generated from a Forge event translation
        if (activeEvents.get().contains(hostEvent)) {
            return;
        }

        // Create the emulated Forge event
        BlockEvent.BreakEvent forgeEvent = new BlockEvent.BreakEvent(
                (net.minecraft.world.level.LevelAccessor) hostEvent.getWorld(),
                (net.minecraft.core.BlockPos) hostEvent.getPos(),
                (net.minecraft.world.level.block.state.BlockState) hostEvent.getState(),
                (net.minecraft.world.entity.player.Player) hostEvent.getPlayer()
        );
        forgeEvent.setExpToDrop(hostEvent.getXpToDrop());
        if (hostEvent.isCanceled()) {
            forgeEvent.setCanceled(true);
        }

        // Register both in active translation context
        activeEvents.get().add(hostEvent);
        activeEvents.get().add(forgeEvent);

        try {
            // Post to Forge bus so that legacy mod listeners are notified
            forgeBus.post(forgeEvent);

            // Sync the outcomes back to the host event
            hostEvent.setCanceled(forgeEvent.isCanceled());
            hostEvent.setXpToDrop(forgeEvent.getExpToDrop());
        } finally {
            // Clean up to allow future distinct events
            activeEvents.get().remove(hostEvent);
            activeEvents.get().remove(forgeEvent);
        }
    }

    /**
     * Translates an emulated Forge BlockEvent.BreakEvent to a ChainLoader host BlockBreakEvent
     * and posts it to the host event bus.
     *
     * @param forgeEvent The emulated block break event from legacy mods.
     */
    private void translateForgeToHostBlockBreak(BlockEvent.BreakEvent forgeEvent) {
        // Prevent loop if this event was generated from a Host event translation
        if (activeEvents.get().contains(forgeEvent)) {
            return;
        }

        // Create the host event
        BlockBreakEvent hostEvent = new BlockBreakEvent(
                forgeEvent.getLevel(),
                forgeEvent.getPos(),
                forgeEvent.getState(),
                forgeEvent.getPlayer()
        );
        hostEvent.setXpToDrop(forgeEvent.getExpToDrop());
        if (forgeEvent.isCanceled()) {
            hostEvent.setCanceled(true);
        }

        // Register both in active translation context
        activeEvents.get().add(forgeEvent);
        activeEvents.get().add(hostEvent);

        try {
            // Post to the host bus so that new/native loader mods or loader itself are notified
            hostBus.post(hostEvent);

            // Sync the outcomes back to the Forge event
            if (forgeEvent.isCancelable()) {
                forgeEvent.setCanceled(hostEvent.isCanceled());
            }
            forgeEvent.setExpToDrop(hostEvent.getXpToDrop());
        } finally {
            // Clean up to allow future distinct events
            activeEvents.get().remove(forgeEvent);
            activeEvents.get().remove(hostEvent);
        }
    }
}
