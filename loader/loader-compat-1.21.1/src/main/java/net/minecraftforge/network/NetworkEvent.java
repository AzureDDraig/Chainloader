package net.minecraftforge.network;

import java.util.concurrent.CompletableFuture;
import net.minecraft.world.entity.player.Player;

public class NetworkEvent {
    public static class Context {
        private final Player sender;

        public Context(Player sender) {
            this.sender = sender;
        }

        public net.minecraft.server.level.ServerPlayer getSender() {
            if (this.sender == null) return null;
            try {
                Class<?> serverPlayerClass = Class.forName("net.minecraft.server.level.ServerPlayer");
                if (serverPlayerClass.isInstance(this.sender)) {
                    return (net.minecraft.server.level.ServerPlayer) (Object) this.sender;
                }
            } catch (Throwable t) {
                // ignore
            }
            
            try {
                Class<?> minecraftClass = Class.forName("net.minecraft.client.Minecraft");
                // Use reflection for getInstance as well to be consistent
                Object mc = null;
                for (java.lang.reflect.Method m : minecraftClass.getMethods()) {
                    if (m.getParameterCount() == 0 && m.getReturnType() == minecraftClass) {
                        mc = m.invoke(null);
                        if (mc != null) {
                            break;
                        }
                    }
                }
                
                if (mc != null) {
                    Object server = null;
                    Class<?> minecraftServerClass = Class.forName("net.minecraft.server.MinecraftServer");
                    for (java.lang.reflect.Method m : mc.getClass().getMethods()) {
                        if (m.getParameterCount() == 0 && minecraftServerClass.isAssignableFrom(m.getReturnType())) {
                            server = m.invoke(mc);
                            if (server != null) {
                                break;
                            }
                        }
                    }
                    
                    if (server != null) {
                        Object playerList = null;
                        Class<?> playerListClass = Class.forName("net.minecraft.server.players.PlayerList");
                        for (java.lang.reflect.Method m : server.getClass().getMethods()) {
                            if (m.getParameterCount() == 0 && playerListClass.isAssignableFrom(m.getReturnType())) {
                                playerList = m.invoke(server);
                                if (playerList != null) {
                                    break;
                                }
                            }
                        }
                        
                        if (playerList != null) {
                            java.util.List<?> players = null;
                            for (java.lang.reflect.Method m : playerList.getClass().getMethods()) {
                                if (m.getParameterCount() == 0 && java.util.List.class.isAssignableFrom(m.getReturnType())) {
                                    String mName = m.getName();
                                    if (mName.equals("getPlayers") || mName.equals("t")) {
                                        players = (java.util.List<?>) m.invoke(playerList);
                                        if (players != null) {
                                            break;
                                        }
                                    }
                                }
                            }
                            
                            if (players != null && !players.isEmpty()) {
                                if (players.size() == 1) {
                                    return (net.minecraft.server.level.ServerPlayer) players.get(0);
                                }
                                
                                String senderName = null;
                                try {
                                    Object profile = this.sender.getClass().getMethod("getGameProfile").invoke(this.sender);
                                    senderName = (String) profile.getClass().getMethod("getName").invoke(profile);
                                } catch (Throwable t) {
                                    // ignore
                                }
                                
                                if (senderName != null) {
                                    for (Object p : players) {
                                        try {
                                            Object pProfile = p.getClass().getMethod("getGameProfile").invoke(p);
                                            String pName = (String) pProfile.getClass().getMethod("getName").invoke(pProfile);
                                            if (senderName.equals(pName)) {
                                                return (net.minecraft.server.level.ServerPlayer) p;
                                            }
                                        } catch (Throwable t) {
                                            // ignore
                                        }
                                    }
                                }
                                return (net.minecraft.server.level.ServerPlayer) players.get(0);
                            }
                        }
                    }
                }
            } catch (Throwable t) {
                t.printStackTrace();
            }
            return null;
        }

        public void setPacketHandled(boolean handled) {
            // no-op
        }

        public CompletableFuture<Void> enqueueWork(Runnable runnable) {
            runnable.run();
            return CompletableFuture.completedFuture(null);
        }
    }
}
