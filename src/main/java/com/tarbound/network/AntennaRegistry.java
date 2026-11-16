package com.tarbound.network;

import com.tarbound.block.TarAntennaBlock;
import com.tarbound.block.TarAntennaBlockEntity;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Loaded Tar Antenna pieces per level (server side), so a radio can find the antennas tuned to it
 * without searching the world. Pieces add themselves when loaded and drop out when unloaded or broken.
 */
public final class AntennaRegistry {
    /**
     * A tuned antenna column.
     *
     * @param base   the mounted end
     * @param facing the way it points
     * @param size   pieces in the column (1-3)
     * @param reach  how far it links: to the radio, and to other antennas tuned to the same radio
     * @param access   how the pool may use inventories touching it (the antenna's mode)
     * @param priority pool priority for inventories touching it (higher fills first, empties last)
     */
    public record Column(BlockPos base, Direction facing, int size, int reach, PoolAccess access, int priority) {
        /** Every piece of the column, base first. */
        public List<BlockPos> pieces() {
            List<BlockPos> out = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                out.add(base.relative(facing, i));
            }
            return out;
        }
    }

    private static final Map<Level, Set<BlockPos>> LOADED = new WeakHashMap<>();

    private AntennaRegistry() {}

    public static synchronized void add(Level level, BlockPos pos) {
        LOADED.computeIfAbsent(level, l -> new HashSet<>()).add(pos.immutable());
        NetworkChanges.mark(level);
    }

    public static synchronized void remove(Level level, BlockPos pos) {
        Set<BlockPos> set = LOADED.get(level);
        if (set != null) {
            set.remove(pos);
        }
        NetworkChanges.mark(level);
    }

    /** Every loaded antenna column in {@code level} whose base is tuned to the radio at {@code radio}. */
    public static synchronized List<Column> tunedTo(Level level, BlockPos radio) {
        Set<BlockPos> set = LOADED.get(level);
        if (set == null || set.isEmpty()) {
            return List.of();
        }
        GlobalPos target = GlobalPos.of(level.dimension(), radio);
        java.util.UUID radioId = RadioRegistry.idAt(level, radio);
        List<Column> out = new ArrayList<>();
        List<Column> stale = new ArrayList<>();
        for (BlockPos pos : set) {
            if (!level.isLoaded(pos)) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof TarAntennaBlock) || !state.getValue(TarAntennaBlock.STAGE).isBase()) {
                continue;
            }
            if (level.getBlockEntity(pos) instanceof TarAntennaBlockEntity antenna
                    && RadioRegistry.matches(antenna.link(), antenna.linkId(), target, radioId)) {
                Column column = new Column(pos, state.getValue(TarAntennaBlock.FACING), state.getValue(TarAntennaBlock.STAGE).size,
                        TarAntennaBlock.reach(state), state.getValue(TarAntennaBlock.MODE), antenna.priority());
                out.add(column);
                if (RadioRegistry.stale(antenna.link(), antenna.linkId(), target, radioId)) {
                    stale.add(column);
                }
            }
        }
        // The radio moved (or old links learn its id): retune every piece, so broken pieces drop right.
        for (Column column : stale) {
            for (BlockPos piece : column.pieces()) {
                if (level.getBlockEntity(piece) instanceof TarAntennaBlockEntity p) {
                    p.setLink(target, radioId);
                }
            }
        }
        return out;
    }
}
