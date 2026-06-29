package dev.architectury.event.events.common;

import dev.architectury.event.Event;
import dev.architectury.event.EventResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.damagesource.DamageSource;

public class EntityEvent {
    @FunctionalInterface
    public interface LivingDeath {
        EventResult die(LivingEntity entity, DamageSource source);
    }

    public static Event<LivingDeath> LIVING_DEATH = new Event<>() {
        @Override
        public void register(LivingDeath listener) {}
    };
}
