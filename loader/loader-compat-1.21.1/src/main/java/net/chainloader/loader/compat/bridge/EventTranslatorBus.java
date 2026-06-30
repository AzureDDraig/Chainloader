package net.chainloader.loader.compat.bridge;

import java.util.HashSet;
import java.util.Set;
import java.util.List;
import java.util.ArrayList;
import java.util.function.Consumer;
import java.util.logging.Logger;

import net.chainloader.api.event.*;
import net.chainloader.loader.compat.fabric.FabricLoaderShim;
import net.chainloader.loader.compat.registry.RegistryStager;
import net.chainloader.loader.compat.neoforge.NeoForgeEventTranslator;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.network.chat.Component;

/**
 * Bi-directional Event Bus Translator for ChainLoader.
 * Connects the native ChainLoader event bus, Forge's event bus, NeoForge's event bus,
 * and Fabric Loader Shim events.
 * 
 * Implements ThreadLocal-based loop prevention to ensure translations do not recursively
 * call themselves across different event buses.
 */
public class EventTranslatorBus {
    
    private static final Logger LOGGER = Logger.getLogger("ChainLoader-EventTranslatorBus");
    private static final EventTranslatorBus INSTANCE = new EventTranslatorBus();
    
    public static EventTranslatorBus getInstance() {
        return INSTANCE;
    }

    private ChainEventBus hostBus;
    private IEventBus forgeBus;
    private net.neoforged.bus.api.IEventBus neoforgeBus;
    private FabricLoaderShim fabricShim;

    // ThreadLocal set of active event instances/tokens to prevent infinite loops during translation
    private final ThreadLocal<Set<Object>> activeTranslationEvents = ThreadLocal.withInitial(HashSet::new);

    private EventTranslatorBus() {
        // Private constructor for singleton
    }

    /**
     * Initializes the translator bus with the specific platform buses.
     */
    public void init(ChainEventBus hostBus, IEventBus forgeBus, net.neoforged.bus.api.IEventBus neoforgeBus) {
        this.hostBus = hostBus;
        this.forgeBus = forgeBus;
        this.neoforgeBus = neoforgeBus;
        this.fabricShim = FabricLoaderShim.getInstance();
        
        registerBridges();
    }

    public ChainEventBus getHostBus() {
        return hostBus;
    }

    public IEventBus getForgeBus() {
        return forgeBus;
    }

    public net.neoforged.bus.api.IEventBus getNeoforgeBus() {
        return neoforgeBus;
    }

    /**
     * Fabric Block Break Callback interface for emulated Fabric API.
     */
    @FunctionalInterface
    public interface FabricBlockBreakCallback {
        boolean interact(Object world, Object player, Object pos, Object state);
    }

    /**
     * Registers all bridge listeners to translate events bi-directionally.
     */
    public void registerBridges() {
        LOGGER.info("[EventTranslatorBus] Registering bi-directional event bridges.");

        // 1. Register Host Bus listeners
        if (hostBus != null) {
            hostBus.register(BlockBreakEvent.class, this::translateHostToOthersBlockBreak);
            hostBus.register(RegistrySyncEvent.class, this::translateHostRegistrySync);
            
            hostBus.register(ServerStartingEvent.class, this::translateHostToOthersServerStarting);
            hostBus.register(ServerStartedEvent.class, this::translateHostToOthersServerStarted);
            hostBus.register(ServerStoppingEvent.class, this::translateHostToOthersServerStopping);
            hostBus.register(ServerStoppedEvent.class, this::translateHostToOthersServerStopped);
            
            hostBus.register(LevelLoadEvent.class, this::translateHostToOthersLevelLoad);
            hostBus.register(LevelUnloadEvent.class, this::translateHostToOthersLevelUnload);
            hostBus.register(LevelSaveEvent.class, this::translateHostToOthersLevelSave);
            
            hostBus.register(ItemTooltipEvent.class, this::translateHostToOthersItemTooltip);
        }

        // 2. Register Forge compatibility listeners (using SubscribeEvent pattern)
        if (forgeBus != null) {
            forgeBus.register(new ForgeEventListener());
        }

        // 3. Register NeoForge compatibility listeners (using SubscribeEvent pattern)
        if (neoforgeBus != null) {
            neoforgeBus.register(new NeoForgeEventListener());
        }

        // 4. Register Fabric compatibility bridges
        FabricApiPort.registerBridges();
    }

    // ==========================================
    // TRANSLATIONS: Host -> Others
    // ==========================================

    public void translateHostToOthersBlockBreak(BlockBreakEvent hostEvent) {
        if (activeTranslationEvents.get().contains(hostEvent)) return;
        activeTranslationEvents.get().add(hostEvent);
        try {
            if (forgeBus != null) {
                BlockEvent.BreakEvent forgeEvent = new BlockEvent.BreakEvent(
                    (net.minecraft.world.level.LevelAccessor) hostEvent.getWorld(),
                    (net.minecraft.core.BlockPos) hostEvent.getPos(),
                    (net.minecraft.world.level.block.state.BlockState) hostEvent.getState(),
                    (net.minecraft.world.entity.player.Player) hostEvent.getPlayer()
                );
                forgeEvent.setExpToDrop(hostEvent.getXpToDrop());
                if (hostEvent.isCanceled()) forgeEvent.setCanceled(true);

                activeTranslationEvents.get().add(forgeEvent);
                try {
                    forgeBus.post(forgeEvent);
                    hostEvent.setCanceled(forgeEvent.isCanceled());
                    hostEvent.setXpToDrop(forgeEvent.getExpToDrop());
                } finally {
                    activeTranslationEvents.get().remove(forgeEvent);
                }
            }

            if (neoforgeBus != null) {
                net.neoforged.neoforge.event.level.BlockEvent.BreakEvent neoforgeEvent = new net.neoforged.neoforge.event.level.BlockEvent.BreakEvent(
                    (net.minecraft.world.level.LevelAccessor) hostEvent.getWorld(), (net.minecraft.core.BlockPos) hostEvent.getPos(), (net.minecraft.world.level.block.state.BlockState) hostEvent.getState(), (net.minecraft.world.entity.player.Player) hostEvent.getPlayer()
                );
                neoforgeEvent.setExpToDrop(hostEvent.getXpToDrop());
                if (hostEvent.isCanceled()) neoforgeEvent.setCanceled(true);

                activeTranslationEvents.get().add(neoforgeEvent);
                try {
                    neoforgeBus.post(neoforgeEvent);
                    hostEvent.setCanceled(neoforgeEvent.isCanceled());
                    hostEvent.setXpToDrop(neoforgeEvent.getExpToDrop());
                } finally {
                    activeTranslationEvents.get().remove(neoforgeEvent);
                }
            }

            if (fabricShim != null) {
                String fabricToken = "fabric_translate_" + System.identityHashCode(hostEvent);
                activeTranslationEvents.get().add(fabricToken);
                try {
                    fabricShim.dispatchEvent("fabric:player_block_break", (FabricBlockBreakCallback callback) -> {
                        boolean allow = callback.interact(hostEvent.getWorld(), hostEvent.getPlayer(), hostEvent.getPos(), hostEvent.getState());
                        if (!allow) hostEvent.setCanceled(true);
                    });
                } finally {
                    activeTranslationEvents.get().remove(fabricToken);
                }
            }
        } finally {
            activeTranslationEvents.get().remove(hostEvent);
        }
    }

    public void translateHostRegistrySync(RegistrySyncEvent hostEvent) {
        if (activeTranslationEvents.get().contains(hostEvent)) return;
        activeTranslationEvents.get().add(hostEvent);
        try {
            String registryId = hostEvent.getRegistryId();
            RegistryStager.getInstance().inject(registryId, (entryId, value) -> {});
        } finally {
            activeTranslationEvents.get().remove(hostEvent);
        }
    }

    public void translateHostToOthersServerStarting(ServerStartingEvent hostEvent) {
        if (activeTranslationEvents.get().contains(hostEvent)) return;
        activeTranslationEvents.get().add(hostEvent);
        try {
            MinecraftServer server = (MinecraftServer) hostEvent.getServer();
            if (forgeBus != null) {
                net.minecraftforge.event.server.ServerStartingEvent forgeEvent = new net.minecraftforge.event.server.ServerStartingEvent(server);
                activeTranslationEvents.get().add(forgeEvent);
                try { forgeBus.post(forgeEvent); } finally { activeTranslationEvents.get().remove(forgeEvent); }
            }
            if (neoforgeBus != null) {
                net.neoforged.neoforge.event.server.ServerStartingEvent neoforgeEvent = new net.neoforged.neoforge.event.server.ServerStartingEvent(server);
                activeTranslationEvents.get().add(neoforgeEvent);
                try { neoforgeBus.post(neoforgeEvent); } finally { activeTranslationEvents.get().remove(neoforgeEvent); }
            }
        } finally {
            activeTranslationEvents.get().remove(hostEvent);
        }
    }

    public void translateHostToOthersServerStarted(ServerStartedEvent hostEvent) {
        if (activeTranslationEvents.get().contains(hostEvent)) return;
        activeTranslationEvents.get().add(hostEvent);
        try {
            MinecraftServer server = (MinecraftServer) hostEvent.getServer();
            if (forgeBus != null) {
                net.minecraftforge.event.server.ServerStartedEvent forgeEvent = new net.minecraftforge.event.server.ServerStartedEvent(server);
                activeTranslationEvents.get().add(forgeEvent);
                try { forgeBus.post(forgeEvent); } finally { activeTranslationEvents.get().remove(forgeEvent); }
            }
            if (neoforgeBus != null) {
                net.neoforged.neoforge.event.server.ServerStartedEvent neoforgeEvent = new net.neoforged.neoforge.event.server.ServerStartedEvent(server);
                activeTranslationEvents.get().add(neoforgeEvent);
                try { neoforgeBus.post(neoforgeEvent); } finally { activeTranslationEvents.get().remove(neoforgeEvent); }
            }
        } finally {
            activeTranslationEvents.get().remove(hostEvent);
        }
    }

    public void translateHostToOthersServerStopping(ServerStoppingEvent hostEvent) {
        if (activeTranslationEvents.get().contains(hostEvent)) return;
        activeTranslationEvents.get().add(hostEvent);
        try {
            MinecraftServer server = (MinecraftServer) hostEvent.getServer();
            if (forgeBus != null) {
                net.minecraftforge.event.server.ServerStoppingEvent forgeEvent = new net.minecraftforge.event.server.ServerStoppingEvent(server);
                activeTranslationEvents.get().add(forgeEvent);
                try { forgeBus.post(forgeEvent); } finally { activeTranslationEvents.get().remove(forgeEvent); }
            }
            if (neoforgeBus != null) {
                net.neoforged.neoforge.event.server.ServerStoppingEvent neoforgeEvent = new net.neoforged.neoforge.event.server.ServerStoppingEvent(server);
                activeTranslationEvents.get().add(neoforgeEvent);
                try { neoforgeBus.post(neoforgeEvent); } finally { activeTranslationEvents.get().remove(neoforgeEvent); }
            }
        } finally {
            activeTranslationEvents.get().remove(hostEvent);
        }
    }

    public void translateHostToOthersServerStopped(ServerStoppedEvent hostEvent) {
        if (activeTranslationEvents.get().contains(hostEvent)) return;
        activeTranslationEvents.get().add(hostEvent);
        try {
            MinecraftServer server = (MinecraftServer) hostEvent.getServer();
            if (forgeBus != null) {
                net.minecraftforge.event.server.ServerStoppedEvent forgeEvent = new net.minecraftforge.event.server.ServerStoppedEvent(server);
                activeTranslationEvents.get().add(forgeEvent);
                try { forgeBus.post(forgeEvent); } finally { activeTranslationEvents.get().remove(forgeEvent); }
            }
            if (neoforgeBus != null) {
                net.neoforged.neoforge.event.server.ServerStoppedEvent neoforgeEvent = new net.neoforged.neoforge.event.server.ServerStoppedEvent(server);
                activeTranslationEvents.get().add(neoforgeEvent);
                try { neoforgeBus.post(neoforgeEvent); } finally { activeTranslationEvents.get().remove(neoforgeEvent); }
            }
        } finally {
            activeTranslationEvents.get().remove(hostEvent);
        }
    }

    public void translateHostToOthersLevelLoad(LevelLoadEvent hostEvent) {
        if (activeTranslationEvents.get().contains(hostEvent)) return;
        activeTranslationEvents.get().add(hostEvent);
        try {
            LevelAccessor level = (LevelAccessor) hostEvent.getLevel();
            if (forgeBus != null) {
                net.minecraftforge.event.level.LevelEvent.Load forgeEvent = new net.minecraftforge.event.level.LevelEvent.Load(level);
                activeTranslationEvents.get().add(forgeEvent);
                try { forgeBus.post(forgeEvent); } finally { activeTranslationEvents.get().remove(forgeEvent); }
            }
            if (neoforgeBus != null) {
                net.neoforged.neoforge.event.level.LevelEvent.Load neoforgeEvent = new net.neoforged.neoforge.event.level.LevelEvent.Load(level);
                activeTranslationEvents.get().add(neoforgeEvent);
                try { neoforgeBus.post(neoforgeEvent); } finally { activeTranslationEvents.get().remove(neoforgeEvent); }
            }
        } finally {
            activeTranslationEvents.get().remove(hostEvent);
        }
    }

    public void translateHostToOthersLevelUnload(LevelUnloadEvent hostEvent) {
        if (activeTranslationEvents.get().contains(hostEvent)) return;
        activeTranslationEvents.get().add(hostEvent);
        try {
            LevelAccessor level = (LevelAccessor) hostEvent.getLevel();
            if (forgeBus != null) {
                net.minecraftforge.event.level.LevelEvent.Unload forgeEvent = new net.minecraftforge.event.level.LevelEvent.Unload(level);
                activeTranslationEvents.get().add(forgeEvent);
                try { forgeBus.post(forgeEvent); } finally { activeTranslationEvents.get().remove(forgeEvent); }
            }
            if (neoforgeBus != null) {
                net.neoforged.neoforge.event.level.LevelEvent.Unload neoforgeEvent = new net.neoforged.neoforge.event.level.LevelEvent.Unload(level);
                activeTranslationEvents.get().add(neoforgeEvent);
                try { neoforgeBus.post(neoforgeEvent); } finally { activeTranslationEvents.get().remove(neoforgeEvent); }
            }
        } finally {
            activeTranslationEvents.get().remove(hostEvent);
        }
    }

    public void translateHostToOthersLevelSave(LevelSaveEvent hostEvent) {
        if (activeTranslationEvents.get().contains(hostEvent)) return;
        activeTranslationEvents.get().add(hostEvent);
        try {
            LevelAccessor level = (LevelAccessor) hostEvent.getLevel();
            if (forgeBus != null) {
                net.minecraftforge.event.level.LevelEvent.Save forgeEvent = new net.minecraftforge.event.level.LevelEvent.Save(level);
                activeTranslationEvents.get().add(forgeEvent);
                try { forgeBus.post(forgeEvent); } finally { activeTranslationEvents.get().remove(forgeEvent); }
            }
            if (neoforgeBus != null) {
                net.neoforged.neoforge.event.level.LevelEvent.Save neoforgeEvent = new net.neoforged.neoforge.event.level.LevelEvent.Save(level);
                activeTranslationEvents.get().add(neoforgeEvent);
                try { neoforgeBus.post(neoforgeEvent); } finally { activeTranslationEvents.get().remove(neoforgeEvent); }
            }
        } finally {
            activeTranslationEvents.get().remove(hostEvent);
        }
    }

    @SuppressWarnings("unchecked")
    public void translateHostToOthersItemTooltip(ItemTooltipEvent hostEvent) {
        if (activeTranslationEvents.get().contains(hostEvent)) return;
        activeTranslationEvents.get().add(hostEvent);
        try {
            ItemStack stack = (ItemStack) hostEvent.getItemStack();
            Player player = (Player) hostEvent.getPlayer();
            List<Component> tooltip = (List<Component>) hostEvent.getToolTip();
            TooltipFlag flags = (TooltipFlag) hostEvent.getFlags();

            if (forgeBus != null) {
                net.minecraftforge.event.entity.player.ItemTooltipEvent forgeEvent = new net.minecraftforge.event.entity.player.ItemTooltipEvent(stack, player, tooltip, flags);
                activeTranslationEvents.get().add(forgeEvent);
                try { forgeBus.post(forgeEvent); } finally { activeTranslationEvents.get().remove(forgeEvent); }
            }
            if (neoforgeBus != null) {
                net.neoforged.neoforge.event.entity.player.ItemTooltipEvent neoforgeEvent = new net.neoforged.neoforge.event.entity.player.ItemTooltipEvent(stack, player, tooltip, flags);
                activeTranslationEvents.get().add(neoforgeEvent);
                try { neoforgeBus.post(neoforgeEvent); } finally { activeTranslationEvents.get().remove(neoforgeEvent); }
            }
        } finally {
            activeTranslationEvents.get().remove(hostEvent);
        }
    }

    // ==========================================
    // TRANSLATIONS: Forge -> Host
    // ==========================================

    public void translateForgeToHostBlockBreak(BlockEvent.BreakEvent forgeEvent) {
        if (activeTranslationEvents.get().contains(forgeEvent)) return;
        BlockBreakEvent hostEvent = new BlockBreakEvent(forgeEvent.getLevel(), forgeEvent.getPos(), forgeEvent.getState(), forgeEvent.getPlayer());
        hostEvent.setXpToDrop(forgeEvent.getExpToDrop());
        if (forgeEvent.isCanceled()) hostEvent.setCanceled(true);

        activeTranslationEvents.get().add(forgeEvent);
        activeTranslationEvents.get().add(hostEvent);
        try {
            if (hostBus != null) hostBus.post(hostEvent);
            if (forgeEvent.isCancelable()) forgeEvent.setCanceled(hostEvent.isCanceled());
            forgeEvent.setExpToDrop(hostEvent.getXpToDrop());
        } finally {
            activeTranslationEvents.get().remove(forgeEvent);
            activeTranslationEvents.get().remove(hostEvent);
        }
    }

    public void translateForgeToHostServerStarting(net.minecraftforge.event.server.ServerStartingEvent forgeEvent) {
        if (activeTranslationEvents.get().contains(forgeEvent)) return;
        ServerStartingEvent hostEvent = new ServerStartingEvent(forgeEvent.getServer());
        activeTranslationEvents.get().add(forgeEvent);
        activeTranslationEvents.get().add(hostEvent);
        try {
            if (hostBus != null) hostBus.post(hostEvent);
        } finally {
            activeTranslationEvents.get().remove(forgeEvent);
            activeTranslationEvents.get().remove(hostEvent);
        }
    }

    public void translateForgeToHostServerStarted(net.minecraftforge.event.server.ServerStartedEvent forgeEvent) {
        if (activeTranslationEvents.get().contains(forgeEvent)) return;
        ServerStartedEvent hostEvent = new ServerStartedEvent(forgeEvent.getServer());
        activeTranslationEvents.get().add(forgeEvent);
        activeTranslationEvents.get().add(hostEvent);
        try {
            if (hostBus != null) hostBus.post(hostEvent);
        } finally {
            activeTranslationEvents.get().remove(forgeEvent);
            activeTranslationEvents.get().remove(hostEvent);
        }
    }

    public void translateForgeToHostServerStopping(net.minecraftforge.event.server.ServerStoppingEvent forgeEvent) {
        if (activeTranslationEvents.get().contains(forgeEvent)) return;
        ServerStoppingEvent hostEvent = new ServerStoppingEvent(forgeEvent.getServer());
        activeTranslationEvents.get().add(forgeEvent);
        activeTranslationEvents.get().add(hostEvent);
        try {
            if (hostBus != null) hostBus.post(hostEvent);
        } finally {
            activeTranslationEvents.get().remove(forgeEvent);
            activeTranslationEvents.get().remove(hostEvent);
        }
    }

    public void translateForgeToHostServerStopped(net.minecraftforge.event.server.ServerStoppedEvent forgeEvent) {
        if (activeTranslationEvents.get().contains(forgeEvent)) return;
        ServerStoppedEvent hostEvent = new ServerStoppedEvent(forgeEvent.getServer());
        activeTranslationEvents.get().add(forgeEvent);
        activeTranslationEvents.get().add(hostEvent);
        try {
            if (hostBus != null) hostBus.post(hostEvent);
        } finally {
            activeTranslationEvents.get().remove(forgeEvent);
            activeTranslationEvents.get().remove(hostEvent);
        }
    }

    public void translateForgeToHostLevelLoad(net.minecraftforge.event.level.LevelEvent.Load forgeEvent) {
        if (activeTranslationEvents.get().contains(forgeEvent)) return;
        LevelLoadEvent hostEvent = new LevelLoadEvent(forgeEvent.getLevel());
        activeTranslationEvents.get().add(forgeEvent);
        activeTranslationEvents.get().add(hostEvent);
        try {
            if (hostBus != null) hostBus.post(hostEvent);
        } finally {
            activeTranslationEvents.get().remove(forgeEvent);
            activeTranslationEvents.get().remove(hostEvent);
        }
    }

    public void translateForgeToHostLevelUnload(net.minecraftforge.event.level.LevelEvent.Unload forgeEvent) {
        if (activeTranslationEvents.get().contains(forgeEvent)) return;
        LevelUnloadEvent hostEvent = new LevelUnloadEvent(forgeEvent.getLevel());
        activeTranslationEvents.get().add(forgeEvent);
        activeTranslationEvents.get().add(hostEvent);
        try {
            if (hostBus != null) hostBus.post(hostEvent);
        } finally {
            activeTranslationEvents.get().remove(forgeEvent);
            activeTranslationEvents.get().remove(hostEvent);
        }
    }

    public void translateForgeToHostLevelSave(net.minecraftforge.event.level.LevelEvent.Save forgeEvent) {
        if (activeTranslationEvents.get().contains(forgeEvent)) return;
        LevelSaveEvent hostEvent = new LevelSaveEvent(forgeEvent.getLevel());
        activeTranslationEvents.get().add(forgeEvent);
        activeTranslationEvents.get().add(hostEvent);
        try {
            if (hostBus != null) hostBus.post(hostEvent);
        } finally {
            activeTranslationEvents.get().remove(forgeEvent);
            activeTranslationEvents.get().remove(hostEvent);
        }
    }

    public void translateForgeToHostItemTooltip(net.minecraftforge.event.entity.player.ItemTooltipEvent forgeEvent) {
        if (activeTranslationEvents.get().contains(forgeEvent)) return;
        ItemTooltipEvent hostEvent = new ItemTooltipEvent(forgeEvent.getItemStack(), forgeEvent.getPlayer(), forgeEvent.getToolTip(), forgeEvent.getFlags());
        activeTranslationEvents.get().add(forgeEvent);
        activeTranslationEvents.get().add(hostEvent);
        try {
            if (hostBus != null) hostBus.post(hostEvent);
        } finally {
            activeTranslationEvents.get().remove(forgeEvent);
            activeTranslationEvents.get().remove(hostEvent);
        }
    }

    // ==========================================
    // TRANSLATIONS: NeoForge -> Host
    // ==========================================

    public void translateNeoForgeToHostBlockBreak(net.neoforged.neoforge.event.level.BlockEvent.BreakEvent neoforgeEvent) {
        if (activeTranslationEvents.get().contains(neoforgeEvent)) return;
        BlockBreakEvent hostEvent = new BlockBreakEvent(neoforgeEvent.getLevel(), neoforgeEvent.getPos(), neoforgeEvent.getState(), neoforgeEvent.getPlayer());
        hostEvent.setXpToDrop(neoforgeEvent.getExpToDrop());
        if (neoforgeEvent.isCanceled()) hostEvent.setCanceled(true);

        activeTranslationEvents.get().add(neoforgeEvent);
        activeTranslationEvents.get().add(hostEvent);
        try {
            if (hostBus != null) hostBus.post(hostEvent);
            if (neoforgeEvent.isCancelable()) neoforgeEvent.setCanceled(hostEvent.isCanceled());
            neoforgeEvent.setExpToDrop(hostEvent.getXpToDrop());
        } finally {
            activeTranslationEvents.get().remove(neoforgeEvent);
            activeTranslationEvents.get().remove(hostEvent);
        }
    }

    public void translateNeoForgeToHostServerStarting(net.neoforged.neoforge.event.server.ServerStartingEvent neoforgeEvent) {
        if (activeTranslationEvents.get().contains(neoforgeEvent)) return;
        ServerStartingEvent hostEvent = new ServerStartingEvent(neoforgeEvent.getServer());
        activeTranslationEvents.get().add(neoforgeEvent);
        activeTranslationEvents.get().add(hostEvent);
        try {
            if (hostBus != null) hostBus.post(hostEvent);
        } finally {
            activeTranslationEvents.get().remove(neoforgeEvent);
            activeTranslationEvents.get().remove(hostEvent);
        }
    }

    public void translateNeoForgeToHostServerStarted(net.neoforged.neoforge.event.server.ServerStartedEvent neoforgeEvent) {
        if (activeTranslationEvents.get().contains(neoforgeEvent)) return;
        ServerStartedEvent hostEvent = new ServerStartedEvent(neoforgeEvent.getServer());
        activeTranslationEvents.get().add(neoforgeEvent);
        activeTranslationEvents.get().add(hostEvent);
        try {
            if (hostBus != null) hostBus.post(hostEvent);
        } finally {
            activeTranslationEvents.get().remove(neoforgeEvent);
            activeTranslationEvents.get().remove(hostEvent);
        }
    }

    public void translateNeoForgeToHostServerStopping(net.neoforged.neoforge.event.server.ServerStoppingEvent neoforgeEvent) {
        if (activeTranslationEvents.get().contains(neoforgeEvent)) return;
        ServerStoppingEvent hostEvent = new ServerStoppingEvent(neoforgeEvent.getServer());
        activeTranslationEvents.get().add(neoforgeEvent);
        activeTranslationEvents.get().add(hostEvent);
        try {
            if (hostBus != null) hostBus.post(hostEvent);
        } finally {
            activeTranslationEvents.get().remove(neoforgeEvent);
            activeTranslationEvents.get().remove(hostEvent);
        }
    }

    public void translateNeoForgeToHostServerStopped(net.neoforged.neoforge.event.server.ServerStoppedEvent neoforgeEvent) {
        if (activeTranslationEvents.get().contains(neoforgeEvent)) return;
        ServerStoppedEvent hostEvent = new ServerStoppedEvent(neoforgeEvent.getServer());
        activeTranslationEvents.get().add(neoforgeEvent);
        activeTranslationEvents.get().add(hostEvent);
        try {
            if (hostBus != null) hostBus.post(hostEvent);
        } finally {
            activeTranslationEvents.get().remove(neoforgeEvent);
            activeTranslationEvents.get().remove(hostEvent);
        }
    }

    public void translateNeoForgeToHostLevelLoad(net.neoforged.neoforge.event.level.LevelEvent.Load neoforgeEvent) {
        if (activeTranslationEvents.get().contains(neoforgeEvent)) return;
        LevelLoadEvent hostEvent = new LevelLoadEvent(neoforgeEvent.getLevel());
        activeTranslationEvents.get().add(neoforgeEvent);
        activeTranslationEvents.get().add(hostEvent);
        try {
            if (hostBus != null) hostBus.post(hostEvent);
        } finally {
            activeTranslationEvents.get().remove(neoforgeEvent);
            activeTranslationEvents.get().remove(hostEvent);
        }
    }

    public void translateNeoForgeToHostLevelUnload(net.neoforged.neoforge.event.level.LevelEvent.Unload neoforgeEvent) {
        if (activeTranslationEvents.get().contains(neoforgeEvent)) return;
        LevelUnloadEvent hostEvent = new LevelUnloadEvent(neoforgeEvent.getLevel());
        activeTranslationEvents.get().add(neoforgeEvent);
        activeTranslationEvents.get().add(hostEvent);
        try {
            if (hostBus != null) hostBus.post(hostEvent);
        } finally {
            activeTranslationEvents.get().remove(neoforgeEvent);
            activeTranslationEvents.get().remove(hostEvent);
        }
    }

    public void translateNeoForgeToHostLevelSave(net.neoforged.neoforge.event.level.LevelEvent.Save neoforgeEvent) {
        if (activeTranslationEvents.get().contains(neoforgeEvent)) return;
        LevelSaveEvent hostEvent = new LevelSaveEvent(neoforgeEvent.getLevel());
        activeTranslationEvents.get().add(neoforgeEvent);
        activeTranslationEvents.get().add(hostEvent);
        try {
            if (hostBus != null) hostBus.post(hostEvent);
        } finally {
            activeTranslationEvents.get().remove(neoforgeEvent);
            activeTranslationEvents.get().remove(hostEvent);
        }
    }

    public void translateNeoForgeToHostItemTooltip(net.neoforged.neoforge.event.entity.player.ItemTooltipEvent neoforgeEvent) {
        if (activeTranslationEvents.get().contains(neoforgeEvent)) return;
        ItemTooltipEvent hostEvent = new ItemTooltipEvent(neoforgeEvent.getItemStack(), neoforgeEvent.getPlayer(), neoforgeEvent.getToolTip(), neoforgeEvent.getFlags());
        activeTranslationEvents.get().add(neoforgeEvent);
        activeTranslationEvents.get().add(hostEvent);
        try {
            if (hostBus != null) hostBus.post(hostEvent);
        } finally {
            activeTranslationEvents.get().remove(neoforgeEvent);
            activeTranslationEvents.get().remove(hostEvent);
        }
    }

    // ==========================================
    // TRANSLATIONS: Fabric -> Host
    // ==========================================

    public boolean translateFabricToHostBlockBreak(Object world, Object player, Object pos, Object state) {
        String fabricToken = "fabric_block_break_" + world + "_" + player + "_" + pos;
        if (activeTranslationEvents.get().contains(fabricToken)) return true;

        BlockBreakEvent hostEvent = new BlockBreakEvent(world, pos, state, player);
        activeTranslationEvents.get().add(fabricToken);
        activeTranslationEvents.get().add(hostEvent);
        try {
            if (hostBus != null) hostBus.post(hostEvent);
            return !hostEvent.isCanceled();
        } finally {
            activeTranslationEvents.get().remove(fabricToken);
            activeTranslationEvents.get().remove(hostEvent);
        }
    }

    // ==========================================
    // SUBSCRIBERS / LISTENERS
    // ==========================================

    public class ForgeEventListener {
        @net.minecraftforge.eventbus.api.SubscribeEvent
        public void onBlockBreak(BlockEvent.BreakEvent event) {
            translateForgeToHostBlockBreak(event);
        }

        @net.minecraftforge.eventbus.api.SubscribeEvent
        public void onServerStarting(net.minecraftforge.event.server.ServerStartingEvent event) {
            translateForgeToHostServerStarting(event);
        }

        @net.minecraftforge.eventbus.api.SubscribeEvent
        public void onServerStarted(net.minecraftforge.event.server.ServerStartedEvent event) {
            translateForgeToHostServerStarted(event);
        }

        @net.minecraftforge.eventbus.api.SubscribeEvent
        public void onServerStopping(net.minecraftforge.event.server.ServerStoppingEvent event) {
            translateForgeToHostServerStopping(event);
        }

        @net.minecraftforge.eventbus.api.SubscribeEvent
        public void onServerStopped(net.minecraftforge.event.server.ServerStoppedEvent event) {
            translateForgeToHostServerStopped(event);
        }

        @net.minecraftforge.eventbus.api.SubscribeEvent
        public void onLevelLoad(net.minecraftforge.event.level.LevelEvent.Load event) {
            translateForgeToHostLevelLoad(event);
        }

        @net.minecraftforge.eventbus.api.SubscribeEvent
        public void onLevelUnload(net.minecraftforge.event.level.LevelEvent.Unload event) {
            translateForgeToHostLevelUnload(event);
        }

        @net.minecraftforge.eventbus.api.SubscribeEvent
        public void onLevelSave(net.minecraftforge.event.level.LevelEvent.Save event) {
            translateForgeToHostLevelSave(event);
        }

        @net.minecraftforge.eventbus.api.SubscribeEvent
        public void onItemTooltip(net.minecraftforge.event.entity.player.ItemTooltipEvent event) {
            translateForgeToHostItemTooltip(event);
        }

        @net.minecraftforge.eventbus.api.SubscribeEvent
        public void onPlayerTick(net.minecraftforge.event.TickEvent.PlayerTickEvent event) {
            net.chainloader.loader.core.event.ChainEventBridge.postPlayerTick(event.player);
        }
    }

    public class NeoForgeEventListener {
        @net.neoforged.bus.api.SubscribeEvent
        public void onBlockBreak(net.neoforged.neoforge.event.level.BlockEvent.BreakEvent event) {
            translateNeoForgeToHostBlockBreak(event);
        }

        @net.neoforged.bus.api.SubscribeEvent
        public void onServerStarting(net.neoforged.neoforge.event.server.ServerStartingEvent event) {
            translateNeoForgeToHostServerStarting(event);
        }

        @net.neoforged.bus.api.SubscribeEvent
        public void onServerStarted(net.neoforged.neoforge.event.server.ServerStartedEvent event) {
            translateNeoForgeToHostServerStarted(event);
        }

        @net.neoforged.bus.api.SubscribeEvent
        public void onServerStopping(net.neoforged.neoforge.event.server.ServerStoppingEvent event) {
            translateNeoForgeToHostServerStopping(event);
        }

        @net.neoforged.bus.api.SubscribeEvent
        public void onServerStopped(net.neoforged.neoforge.event.server.ServerStoppedEvent event) {
            translateNeoForgeToHostServerStopped(event);
        }

        @net.neoforged.bus.api.SubscribeEvent
        public void onLevelLoad(net.neoforged.neoforge.event.level.LevelEvent.Load event) {
            translateNeoForgeToHostLevelLoad(event);
        }

        @net.neoforged.bus.api.SubscribeEvent
        public void onLevelUnload(net.neoforged.neoforge.event.level.LevelEvent.Unload event) {
            translateNeoForgeToHostLevelUnload(event);
        }

        @net.neoforged.bus.api.SubscribeEvent
        public void onLevelSave(net.neoforged.neoforge.event.level.LevelEvent.Save event) {
            translateNeoForgeToHostLevelSave(event);
        }

        @net.neoforged.bus.api.SubscribeEvent
        public void onItemTooltip(net.neoforged.neoforge.event.entity.player.ItemTooltipEvent event) {
            translateNeoForgeToHostItemTooltip(event);
        }

        @net.neoforged.bus.api.SubscribeEvent
        public void onPlayerTick(net.neoforged.neoforge.event.tick.PlayerTickEvent event) {
            net.chainloader.loader.core.event.ChainEventBridge.postPlayerTick(event.getEntity());
        }
    }
}
