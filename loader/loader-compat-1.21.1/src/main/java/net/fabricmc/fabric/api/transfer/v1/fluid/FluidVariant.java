package net.fabricmc.fabric.api.transfer.v1.fluid;

import net.minecraft.world.level.material.Fluid;

public interface FluidVariant {
    Fluid getFluid();
    static FluidVariant of(Fluid fluid) {
        return new FluidVariant() {
            @Override
            public Fluid getFluid() { return fluid; }
        };
    }
}
