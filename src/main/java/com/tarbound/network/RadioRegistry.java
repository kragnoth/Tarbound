package com.tarbound.network;

import com.tarbound.block.ScryingFontBlockEntity;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.level.Level;

/**
 * Loaded radios by their id, per level (server side), and the rule for whether something tuned is
 * tuned to a given radio.
 * <p>
 * Every Scrying Font has an id that travels with it when it's broken and placed again. Antennas,
 * Reliquaries and Spirit Boards remember the id as well as the position, so a moved radio keeps
 * everything tuned to it: the next time it scans (or a board is used), stale positions are corrected.
 * A brand-new radio has a new id, so things tuned to the old one stay untuned until re-tuned.
 */
public final class RadioRegistry {
    private static final Map<Level, Map<UUID, BlockPos>> LOADED = new WeakHashMap<>();

    private RadioRegistry() {}

    /**
     * Records the radio at {@code pos}. @return false if another loaded radio already has this id
     * (a copied block): the caller should take a new one.
     */
    public static synchronized boolean add(Level level, UUID id, BlockPos pos) {
        Map<UUID, BlockPos> map = LOADED.computeIfAbsent(level, l -> new HashMap<>());
        BlockPos other = map.get(id);
        if (other != null && !other.equals(pos) && level.isLoaded(other)
                && level.getBlockEntity(other) instanceof ScryingFontBlockEntity f && !f.isRemoved() && id.equals(f.radioId())) {
            return false;
        }
        map.put(id, pos.immutable());
        return true;
    }

    public static synchronized void remove(Level level, UUID id, BlockPos pos) {
        Map<UUID, BlockPos> map = LOADED.get(level);
        if (map != null && pos.equals(map.get(id))) {
            map.remove(id);
        }
    }

    /** Whether {@code level} has any loaded radio (cheap: asked on every block update). */
    public static synchronized boolean any(Level level) {
        Map<UUID, BlockPos> map = LOADED.get(level);
        return map != null && !map.isEmpty();
    }

    /** Every loaded radio in {@code level} (a copy). */
    public static synchronized java.util.List<BlockPos> all(Level level) {
        Map<UUID, BlockPos> map = LOADED.get(level);
        return map == null ? java.util.List.of() : java.util.List.copyOf(map.values());
    }

    /** Where the loaded radio with this id is in {@code level}, or null. */
    @Nullable
    public static synchronized BlockPos find(Level level, UUID id) {
        Map<UUID, BlockPos> map = LOADED.get(level);
        return map == null ? null : map.get(id);
    }

    /** The id of the loaded radio at {@code pos}, or null (never loads the chunk). */
    @Nullable
    public static UUID idAt(Level level, BlockPos pos) {
        return level.isLoaded(pos) && level.getBlockEntity(pos) instanceof ScryingFontBlockEntity f ? f.radioId() : null;
    }

    /**
     * Whether something tuned to ({@code link}, {@code linkId}) belongs to the radio at {@code radio}
     * with id {@code radioId}. Matched by id when both are known; links made before radios had ids
     * (or a radio whose id isn't known) fall back to the position.
     */
    public static boolean matches(@Nullable GlobalPos link, @Nullable UUID linkId, GlobalPos radio, @Nullable UUID radioId) {
        if (link == null) {
            return false;
        }
        if (linkId != null && radioId != null) {
            return linkId.equals(radioId);
        }
        return radio.equals(link);
    }

    /** Whether a matched link needs its stored position or id brought up to date. */
    public static boolean stale(@Nullable GlobalPos link, @Nullable UUID linkId, GlobalPos radio, @Nullable UUID radioId) {
        return !radio.equals(link) || (radioId != null && !radioId.equals(linkId));
    }
}
