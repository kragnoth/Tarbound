package com.tarbound.network;

import com.tarbound.block.ReliquaryBlockEntity;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.level.Level;

/**
 * Loaded Reliquaries per level (server side). A radio asks for the reliquaries tuned to it instead of
 * searching around its tar, so a tuned reliquary binds from anywhere in the radio's dimension while loaded.
 */
public final class ReliquaryRegistry {
    private static final Map<Level, Set<BlockPos>> LOADED = new WeakHashMap<>();

    private ReliquaryRegistry() {}

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

    /** Positions of loaded reliquaries in {@code level} tuned to the radio at {@code radio}. */
    public static synchronized List<BlockPos> tunedTo(Level level, BlockPos radio) {
        Set<BlockPos> set = LOADED.get(level);
        if (set == null || set.isEmpty()) {
            return List.of();
        }
        GlobalPos target = GlobalPos.of(level.dimension(), radio);
        java.util.UUID radioId = RadioRegistry.idAt(level, radio);
        List<BlockPos> out = new ArrayList<>();
        List<ReliquaryBlockEntity> stale = new ArrayList<>();
        for (BlockPos pos : set) {
            if (level.isLoaded(pos) && level.getBlockEntity(pos) instanceof ReliquaryBlockEntity reliquary
                    && RadioRegistry.matches(reliquary.link(), reliquary.linkId(), target, radioId)) {
                out.add(pos);
                if (RadioRegistry.stale(reliquary.link(), reliquary.linkId(), target, radioId)) {
                    stale.add(reliquary);
                }
            }
        }
        for (ReliquaryBlockEntity reliquary : stale) {
            reliquary.setLink(target, radioId); // the radio moved (or an old link learns its id)
        }
        return out;
    }
}
