package net.neoforged.neoforge.fluids;

import net.minecraft.core.Holder;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.common.MutableDataComponentHolder;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentType;

public final class FluidStack implements MutableDataComponentHolder {
    private final Holder<Fluid> fluid;
    private int amount;

    public FluidStack(Fluid fluid, int amount) {
        this.fluid = fluid.builtInRegistryHolder();
        this.amount = amount;
    }

    public FluidStack(Holder<Fluid> fluid, int amount) {
        this.fluid = fluid;
        this.amount = amount;
    }

    public Fluid getFluid() {
        return fluid.value();
    }

    public Holder<Fluid> getFluidHolder() {
        return fluid;
    }

    public int getAmount() {
        return amount;
    }

    public void setAmount(int amount) {
        this.amount = amount;
    }

    public void grow(int amount) {
        this.amount += amount;
    }

    public void shrink(int amount) {
        this.amount -= amount;
    }

    public boolean isEmpty() {
        return amount <= 0 || fluid.value() == null;
    }

    public FluidStack copy() {
        return new FluidStack(fluid, amount);
    }

    public FluidStack copyWithAmount(int amount) {
        return new FluidStack(fluid, amount);
    }

    public boolean isSameFluid(FluidStack other) {
        return this.getFluid() == other.getFluid();
    }

    public boolean isSameFluid(Fluid fluid) {
        return this.getFluid() == fluid;
    }

    public static boolean isSameFluidAndComponents(FluidStack a, FluidStack b) {
        if (a.isEmpty() && b.isEmpty()) return true;
        return !a.isEmpty() && !b.isEmpty() && a.getFluid() == b.getFluid();
    }

    public static boolean isFluidStackIdentical(FluidStack a, FluidStack b) {
        if (a.isEmpty() && b.isEmpty()) return true;
        return !a.isEmpty() && !b.isEmpty() && a.getFluid() == b.getFluid() && a.getAmount() == b.getAmount();
    }

    private final java.util.Map<net.minecraft.core.component.DataComponentType<?>, Object> componentsMap = new java.util.HashMap<>();

    @Override
    public net.minecraft.core.component.DataComponentMap getComponents() {
        return new net.minecraft.core.component.DataComponentMap() {
            @Override
            @SuppressWarnings("unchecked")
            public <T> T get(net.minecraft.core.component.DataComponentType<? extends T> type) {
                return (T) componentsMap.get(type);
            }

            @Override
            public boolean has(net.minecraft.core.component.DataComponentType<?> type) {
                return componentsMap.containsKey(type);
            }

            @Override
            public java.util.Set<net.minecraft.core.component.DataComponentType<?>> keySet() {
                return componentsMap.keySet();
            }
        };
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T set(net.minecraft.core.component.DataComponentType<? super T> type, T value) {
        if (value == null) {
            return (T) remove((net.minecraft.core.component.DataComponentType) type);
        }
        return (T) componentsMap.put(type, value);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T remove(net.minecraft.core.component.DataComponentType<? extends T> type) {
        return (T) componentsMap.remove(type);
    }

    public static final com.mojang.serialization.Codec<FluidStack> CODEC = com.mojang.serialization.Codec.unit(() -> new FluidStack((net.minecraft.core.Holder<net.minecraft.world.level.material.Fluid>) null, 0));
    public static final com.mojang.serialization.Codec<FluidStack> OPTIONAL_CODEC = CODEC;

    public static com.mojang.serialization.Codec<FluidStack> fixedAmountCodec(int amount) {
        return com.mojang.serialization.Codec.unit(() -> new FluidStack((net.minecraft.core.Holder<net.minecraft.world.level.material.Fluid>) null, amount));
    }

    public static final net.minecraft.network.codec.StreamCodec<net.minecraft.network.FriendlyByteBuf, FluidStack> STREAM_CODEC = 
        new net.minecraft.network.codec.StreamCodec<net.minecraft.network.FriendlyByteBuf, FluidStack>() {
            @Override
            public void encode(net.minecraft.network.FriendlyByteBuf buf, FluidStack value) {}
            @Override
            public FluidStack decode(net.minecraft.network.FriendlyByteBuf buf) {
                return new FluidStack((net.minecraft.core.Holder<net.minecraft.world.level.material.Fluid>) null, 0);
            }
        };
    public static final net.minecraft.network.codec.StreamCodec<net.minecraft.network.FriendlyByteBuf, FluidStack> OPTIONAL_STREAM_CODEC = STREAM_CODEC;
}
