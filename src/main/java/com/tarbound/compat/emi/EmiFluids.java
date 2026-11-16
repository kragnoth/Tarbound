package com.tarbound.compat.emi;

import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;

/** EMI fluid stacks to NeoForge ones (EMI keys a fluid stack by its Fluid; amounts are in mB). */
final class EmiFluids {
    static FluidStack of(EmiStack stack, long amount) {
        if (stack == null || !(stack.getKey() instanceof Fluid fluid) || fluid == Fluids.EMPTY) {
            return FluidStack.EMPTY;
        }
        int mb = (int) Math.max(1, Math.min(Integer.MAX_VALUE, amount));
        var patch = stack.getComponentChanges();
        return patch == null || patch.isEmpty()
                ? new FluidStack(fluid, mb)
                : new FluidStack(fluid.builtInRegistryHolder(), mb, patch);
    }

    private EmiFluids() {}
}
