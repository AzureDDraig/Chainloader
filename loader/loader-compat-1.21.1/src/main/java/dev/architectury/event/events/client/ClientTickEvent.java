package dev.architectury.event.events.client;

import dev.architectury.event.Event;
import net.minecraft.client.Minecraft;

public class ClientTickEvent {
    public interface Client {
        void post(Minecraft minecraft);
    }

    public static Event<Client> CLIENT_POST = new Event<>() {
        @Override
        public void register(Client listener) {}
    };
}
