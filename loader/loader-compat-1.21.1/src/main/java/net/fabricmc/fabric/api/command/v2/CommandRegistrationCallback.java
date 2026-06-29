package net.fabricmc.fabric.api.command.v2;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.fabricmc.fabric.api.event.Event;

@FunctionalInterface
public interface CommandRegistrationCallback {
    Event<CommandRegistrationCallback> EVENT = new Event<>(CommandRegistrationCallback.class);

    void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext registryAccess, Commands.CommandSelection environment);
}
