package com.tarbound.contract;

import com.tarbound.network.FluidKey;
import com.tarbound.network.ItemKey;
import com.tarbound.network.TarNetwork;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * Whatever watches over a multi-step order and holds its stock: a {@link com.tarbound.block.ShadeBlockEntity}, or a
 * Steward (which keeps its own orders). Everything the order set aside, and what its sub-crafts make, sits here until
 * the last step is done; then {@link #release} hands it all back to the pool.
 */
public interface OrderVessel {
    /** Takes on an order (0 = free again). */
    void claim(int order);

    /** The order it's holding for, or 0. */
    int orderId();

    Map<ItemKey, Long> held();

    Map<FluidKey, Long> heldFluids();

    void hold(ItemStack stack);

    void hold(ItemKey key, long n);

    void holdFluid(FluidStack fluid);

    /** Takes up to {@code amount} of exactly {@code proto}. @return how many */
    int take(ItemStack proto, int amount);

    int takeFluid(FluidStack fluid, int amount);

    /** Ends the order: everything held goes back into {@code net}'s pool (or drops where it is), and it's free again. */
    void release(@Nullable TarNetwork net);

    /** Where it is (a Shade's block, a Steward's feet): the radio checks that chunk is loaded before judging it gone. */
    BlockPos vesselPos();

    /** An entity vessel's UUID (orders find it by this), or null for a block. */
    @Nullable
    default java.util.UUID vesselEntity() {
        return null;
    }
}
