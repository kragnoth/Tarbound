package com.tarbound.network;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/**
 * One fluid tank block bound to a radio: its handler, and the antenna mode and priority it was bound
 * with (touching tar is Commune, priority 0). The fluid side of {@link BoundInventory}.
 */
public record FluidStore(BlockPos pos, IFluidHandler handler, PoolAccess access, int priority, List<BlockEntity> owners) {
    /** False once the block behind it was removed since this tick's scan. */
    public boolean alive() {
        for (BlockEntity be : owners) {
            if (be.isRemoved()) {
                return false;
            }
        }
        return true;
    }

    /** Insert order key, as for items: priority, then visible before Tithe. */
    int insertTier() {
        return priority * 2 + (access.canExtract() ? 1 : 0);
    }
}
