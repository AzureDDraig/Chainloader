package dev.architectury.event.events.client;

import dev.architectury.event.Event;
import net.minecraft.client.Minecraft;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class ClientTickEvent {
    public interface Client {
        void post(Minecraft minecraft);
    }

    private static final List<Client> LISTENERS = new CopyOnWriteArrayList<>();

    public static Event<Client> CLIENT_POST = new Event<>() {
        @Override
        public void register(Client listener) {
            if (listener != null) {
                System.out.println("[Architectury Stub] Registered ClientTick listener: " + listener.getClass().getName());
                LISTENERS.add(listener);
            }
        }
    };

    public static List<Client> getListeners() {
        return LISTENERS;
    }
}

