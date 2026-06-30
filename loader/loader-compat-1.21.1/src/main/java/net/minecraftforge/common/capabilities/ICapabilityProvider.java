package net.minecraftforge.common.capabilities;

import net.minecraftforge.common.util.LazyOptional;
import net.minecraft.core.Direction;

public interface ICapabilityProvider {
    <T> LazyOptional<T> getCapability(Capability<T> cap, Direction side);
}
