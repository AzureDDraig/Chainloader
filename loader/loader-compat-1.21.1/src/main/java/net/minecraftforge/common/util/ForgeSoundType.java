package net.minecraftforge.common.util;

import net.minecraft.world.level.block.SoundType;
import net.minecraft.sounds.SoundEvent;
import java.util.function.Supplier;

public class ForgeSoundType extends SoundType {
    public ForgeSoundType(float volume, float pitch, Supplier<SoundEvent> breakSound, Supplier<SoundEvent> stepSound, Supplier<SoundEvent> placeSound, Supplier<SoundEvent> hitSound, Supplier<SoundEvent> fallSound) {
        super(volume, pitch, null, null, null, null, null);
    }
}
