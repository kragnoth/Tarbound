package com.tarbound.network;

import net.neoforged.neoforge.fluids.FluidStack;

/** Hashable identity of a fluid type (fluid + data components), ignoring amount. */
public final class FluidKey {
    private final FluidStack proto;
    private final int hash;

    private FluidKey(FluidStack proto) {
        this.proto = proto;
        this.hash = FluidStack.hashFluidAndComponents(proto);
    }

    public static FluidKey of(FluidStack stack) {
        return new FluidKey(stack.copyWithAmount(1));
    }

    /** An amount-1 copy; callers must not mutate it. */
    public FluidStack proto() {
        return proto;
    }

    public boolean matches(FluidStack stack) {
        return FluidStack.isSameFluidSameComponents(proto, stack);
    }

    @Override
    public boolean equals(Object o) {
        return this == o || (o instanceof FluidKey other && hash == other.hash && FluidStack.isSameFluidSameComponents(proto, other.proto));
    }

    @Override
    public int hashCode() {
        return hash;
    }

    @Override
    public String toString() {
        return net.minecraft.core.registries.BuiltInRegistries.FLUID.getKey(proto.getFluid())
                + (proto.getComponentsPatch().isEmpty() ? "" : proto.getComponentsPatch().toString());
    }
}
