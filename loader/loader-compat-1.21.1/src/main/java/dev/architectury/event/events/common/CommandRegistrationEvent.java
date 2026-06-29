package dev.architectury.event.events.common;

import dev.architectury.event.Event;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;

@FunctionalInterface
public interface CommandRegistrationEvent {
    void register(CommandDispatcher<CommandSourceStack> dispatcher, Commands.CommandSelection selection, CommandBuildContext context);

    Event<CommandRegistrationEvent> EVENT = new Event<>() {
        @Override
        public void register(CommandRegistrationEvent listener) {}
    };
}
