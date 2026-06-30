package net.minecraftforge.fluids;

import net.minecraft.world.level.material.Fluid;
import net.minecraft.nbt.CompoundTag;

public class FluidStack {
    private final Fluid fluid;
    private final int amount;
    private final CompoundTag tag;

    public FluidStack(Fluid fluid, int amount) {
        this(fluid, amount, null);
    }

    public FluidStack(Fluid fluid, int amount, CompoundTag tag) {
        this.fluid = fluid;
        this.amount = amount;
        this.tag = tag;
    }

    public Fluid getFluid() { return fluid; }
    public int getAmount() { return amount; }
    public CompoundTag getTag() { return tag; }
}
