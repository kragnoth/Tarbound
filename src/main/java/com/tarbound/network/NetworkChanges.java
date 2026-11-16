package com.tarbound.network;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * When a radio's last scan may be out of date. AE2 never scans: its cables are grid nodes that join and leave as
 * they're placed and broken. Tarbound's network is world blocks, so it scans; but everything a network can hold
 * touches tar, a Reliquary or an antenna, and those notice when their neighbours change. So rather than rescanning
 * every pass, a radio keeps its scan until this world's change count moves (or its own watchers fire, see
 * {@link com.tarbound.block.ScryingFontBlockEntity#network()}), with a full rescan every few seconds anyway.
 *
 * <p>Missing a change is safe: the worst case is a scan a few seconds old. Writes still check that the block behind
 * each binding hasn't been removed ({@link BoundInventory#alive()}), as they did when scans lasted one tick.
 */
public final class NetworkChanges {
    private static final Map<ResourceKey<Level>, long[]> COUNTS = new ConcurrentHashMap<>();

    private NetworkChanges() {}

    /** Something a network in this world might hold has changed. */
    public static void mark(Level level) {
        if (level != null && !level.isClientSide) {
            COUNTS.computeIfAbsent(level.dimension(), k -> new long[1])[0]++;
        }
    }

    /** This world's change count: a radio rescans when it differs from the count at its last scan. */
    public static long count(Level level) {
        long[] c = COUNTS.get(level.dimension());
        return c == null ? 0 : c[0];
    }

    /**
     * Any block placed, broken or changed in a world: counts if it, or a block touching it, is tar (or a conduit), a
     * Reliquary, an antenna or a radio. Skipped entirely in worlds without a loaded radio.
     */
    public static void onNeighborNotify(BlockEvent.NeighborNotifyEvent event) {
        if (!(event.getLevel() instanceof Level level) || level.isClientSide || !RadioRegistry.any(level)) {
            return;
        }
        BlockPos pos = event.getPos();
        if (counts(event.getState())) {
            mark(level);
            return;
        }
        BlockPos.MutableBlockPos n = new BlockPos.MutableBlockPos();
        for (Direction d : Direction.values()) {
            n.setWithOffset(pos, d);
            if (level.isLoaded(n) && counts(level.getBlockState(n))) {
                mark(level);
                return;
            }
        }
    }

    private static boolean counts(BlockState state) {
        return TarNetwork.isTarBody(state)
                || state.getBlock() instanceof com.tarbound.block.ReliquaryBlock
                || state.getBlock() instanceof com.tarbound.block.TarAntennaBlock
                || state.getBlock() instanceof com.tarbound.block.ScryingFontBlock;
    }
}
