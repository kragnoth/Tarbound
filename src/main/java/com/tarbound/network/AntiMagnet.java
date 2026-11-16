package com.tarbound.network;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

/**
 * Keeps item magnets from other mods off dropped items near a Reliquary, using the community flags
 * most magnets honour (Botania, Demagnetize, and the magnets that follow them):
 * <ul>
 *   <li>{@code PreventRemoteMovement}: magnets and similar pullers leave the item alone;</li>
 *   <li>{@code AllowMachineRemoteMovement}: machines that vacuum items (hoppers, collectors) may still
 *       take it, so only player magnets are stopped.</li>
 * </ul>
 * Items carry the flags only while inside the radius: anything we flagged that has drifted out (or
 * whose Reliquary was switched off or broken) gets them cleared again. A magnet mod that ignores these
 * flags can't be stopped this way.
 * <p>
 * The flags live in the item entity's persistent data, which isn't synced, and magnets run on the
 * client too (to predict the pull). So like Simple Magnets' Demagnetization Coil this works on
 * <b>both sides</b>: the Reliquary ticks on the client as well, and items are flagged the moment they
 * spawn inside a ward ({@link #onItemJoin}), before any magnet's first look.
 */
public final class AntiMagnet {
    /** The ward's radius, from the server config (default 4). */
    public static double radius() {
        return com.tarbound.config.TarboundConfig.SERVER.antiMagnetRadius.get();
    }

    /** Whether wards are allowed at all (server config). */
    public static boolean enabled() {
        return com.tarbound.config.TarboundConfig.SERVER.antiMagnet.get();
    }
    public static final int INTERVAL = 2;

    /** Active wards per level, on whichever side that level lives (client and server levels differ). */
    private static final Map<Level, Set<BlockPos>> WARDS = new WeakHashMap<>();

    public static synchronized void setWard(Level level, BlockPos pos, boolean active) {
        if (active) {
            WARDS.computeIfAbsent(level, l -> new HashSet<>()).add(pos.immutable());
        } else {
            Set<BlockPos> set = WARDS.get(level);
            if (set != null) {
                set.remove(pos);
            }
        }
    }

    /** Flags a freshly spawned item that lands inside any active ward (fires on both sides). */
    public static void onItemJoin(EntityJoinLevelEvent event) {
        if (!(event.getEntity() instanceof ItemEntity item)) {
            return;
        }
        if (!enabled()) {
            return;
        }
        Set<BlockPos> wards;
        synchronized (AntiMagnet.class) {
            Set<BlockPos> set = WARDS.get(event.getLevel());
            if (set == null || set.isEmpty()) {
                return;
            }
            wards = Set.copyOf(set);
        }
        double r2 = radius() * radius();
        for (BlockPos pos : wards) {
            if (item.position().distanceToSqr(Vec3.atCenterOf(pos)) <= r2) {
                flag(item);
                return;
            }
        }
    }

    private static final String PREVENT = "PreventRemoteMovement";
    private static final String ALLOW_MACHINES = "AllowMachineRemoteMovement";
    /** Marks flags we set, so we only ever clear our own (not another mod's). */
    private static final String OURS = "tarbound:demagnetized";

    private AntiMagnet() {}

    /** Flags items within {@link #radius()} of {@code pos}'s centre, and unflags ours just outside it. */
    public static void apply(Level level, BlockPos pos) {
        if (!enabled()) {
            return;
        }
        Vec3 centre = Vec3.atCenterOf(pos);
        double r2 = radius() * radius();
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(radius() + 3))) {
            if (item.position().distanceToSqr(centre) <= r2) {
                flag(item);
            } else {
                unflag(item);
            }
        }
    }

    /** Clears our flags from items around {@code pos} (the Reliquary was switched off or broken). */
    public static void release(Level level, BlockPos pos) {
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(radius() + 3))) {
            unflag(item);
        }
    }

    private static void flag(ItemEntity item) {
        CompoundTag data = item.getPersistentData();
        if (data.getBoolean(OURS)) {
            return;
        }
        if (data.getBoolean(PREVENT)) {
            return; // already held by someone else's anti-magnet; leave their flags alone
        }
        data.putBoolean(PREVENT, true);
        data.putBoolean(ALLOW_MACHINES, true);
        data.putBoolean(OURS, true);
    }

    private static void unflag(ItemEntity item) {
        CompoundTag data = item.getPersistentData();
        if (data.getBoolean(OURS)) {
            data.remove(PREVENT);
            data.remove(ALLOW_MACHINES);
            data.remove(OURS);
        }
    }
}
