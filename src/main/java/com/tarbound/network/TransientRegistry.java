package com.tarbound.network;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/** Loaded Transient Antennas per level (server side), so a pulling one can find the pushing ones on its radio. */
public final class TransientRegistry {
    private static final Map<Level, Set<BlockPos>> LOADED = new WeakHashMap<>();

    private TransientRegistry() {}

    public static synchronized void add(Level level, BlockPos pos) {
        LOADED.computeIfAbsent(level, l -> new HashSet<>()).add(pos.immutable());
    }

    public static synchronized void remove(Level level, BlockPos pos) {
        Set<BlockPos> set = LOADED.get(level);
        if (set != null) {
            set.remove(pos);
        }
    }

    public static synchronized List<BlockPos> all(Level level) {
        Set<BlockPos> set = LOADED.get(level);
        return set == null ? List.of() : new ArrayList<>(set);
    }
}
