package com.tarbound.compat;

import com.tarbound.menu.ScryingFontMenu;
import com.tarbound.net.ContentsPayload;
import com.tarbound.net.FillGridPayload;
import com.tarbound.network.FluidKey;
import com.tarbound.network.ItemKey;
import com.tarbound.network.TarNetwork;
import net.neoforged.neoforge.fluids.FluidStack;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Client-side helpers shared by the recipe-viewer integrations: what the grid can be filled from,
 * which recipe slots can't be covered, and the fill request itself.
 */
public final class RecipeFill {
    /**
     * The pool (as last synced) plus the player's main inventory.
     *
     * @param includeGrid count what's sitting in the 3x3 grid too. True for crafting fills (the grid
     *                    is emptied into the pool first); false for machine fills, which never draw
     *                    from the grid.
     */
    public static Map<ItemKey, Long> available(ScryingFontMenu menu, Player player, boolean includeGrid) {
        Map<ItemKey, Long> available = new HashMap<>();
        for (ContentsPayload.Entry e : menu.clientEntries()) {
            available.merge(ItemKey.of(e.proto()), e.count(), Long::sum);
        }
        for (ItemStack s : player.getInventory().items) {
            if (!s.isEmpty()) {
                available.merge(ItemKey.of(s), (long) s.getCount(), Long::sum);
            }
        }
        // Whatever is already in the grid goes back to the pool before a crafting fill.
        for (int i = ScryingFontMenu.GRID_START; includeGrid && i < ScryingFontMenu.GRID_END; i++) {
            ItemStack s = menu.getSlot(i).getItem();
            if (!s.isEmpty()) {
                available.merge(ItemKey.of(s), (long) s.getCount(), Long::sum);
            }
        }
        return available;
    }

    /** As {@link #available(ScryingFontMenu, Player, boolean)}, plus filled buckets the pool could make for {@code slots}. */
    public static Map<ItemKey, Long> available(ScryingFontMenu menu, Player player, boolean includeGrid, List<List<ItemStack>> slots) {
        return TarNetwork.withFills(available(menu, player, includeGrid), fluids(menu), TarNetwork.flatten(slots));
    }

    /** The pool's fluids as last synced, in mB. */
    public static Map<FluidKey, Long> fluids(ScryingFontMenu menu) {
        Map<FluidKey, Long> fluids = new HashMap<>();
        for (ContentsPayload.Entry e : menu.clientFluidEntries()) {
            fluids.merge(FluidKey.of(e.fluid()), e.count(), Long::sum);
        }
        return fluids;
    }

    /** The pool holds every fluid listed (amounts summed per fluid). */
    public static boolean hasFluids(ScryingFontMenu menu, List<FluidStack> wanted) {
        if (wanted.isEmpty()) {
            return true;
        }
        Map<FluidKey, Long> left = fluids(menu);
        for (FluidStack f : wanted) {
            FluidKey k = FluidKey.of(f);
            long n = left.getOrDefault(k, 0L) - f.getAmount();
            if (n < 0) {
                return false;
            }
            left.put(k, n);
        }
        return true;
    }

    /**
     * Indices of slots none of whose alternatives can be supplied (each alternative's count per slot,
     * shared items reserved greedily).
     */
    public static List<Integer> missing(List<List<ItemStack>> slots, Map<ItemKey, Long> available) {
        slots = com.tarbound.network.LooseMatch.widen(slots, available.keySet());
        Map<ItemKey, Long> left = new HashMap<>(available);
        List<Integer> missing = new ArrayList<>();
        for (int i = 0; i < slots.size(); i++) {
            List<ItemStack> alts = slots.get(i);
            if (alts.isEmpty()) {
                continue;
            }
            boolean found = false;
            for (ItemStack alt : alts) {
                ItemKey k = ItemKey.of(alt);
                long need = Math.max(1, alt.getCount());
                long n = left.getOrDefault(k, 0L);
                if (n >= need) {
                    left.put(k, n - need);
                    found = true;
                    break;
                }
            }
            if (!found) {
                missing.add(i);
            }
        }
        return missing;
    }

    public static void send(ScryingFontMenu menu, List<List<ItemStack>> slots, boolean max) {
        List<List<ItemStack>> grid = new ArrayList<>(FillGridPayload.GRID);
        for (int i = 0; i < FillGridPayload.GRID; i++) {
            grid.add(i < slots.size() ? slots.get(i) : List.of());
        }
        PacketDistributor.sendToServer(new FillGridPayload(menu.containerId, grid, max));
    }

    /**
     * What the radio screen can fill from, counted once and shared: EMI asks whether every recipe that uses
     * anything in storage can be made (its "craftables"), several times a second, and rebuilding the pool's
     * counts for each recipe froze the screen with big storage. See {@link #snapshot}.
     */
    public static final class Snapshot {
        public final Map<ItemKey, Long> items;
        public final Map<FluidKey, Long> fluids;
        /** Stocked items carrying data (used tools, renamed things), by item: what plain alternatives also match. */
        private final Map<net.minecraft.world.item.Item, List<ItemKey>> variants;

        private Snapshot(Map<ItemKey, Long> items, Map<FluidKey, Long> fluids) {
            this.items = java.util.Collections.unmodifiableMap(items);
            this.fluids = java.util.Collections.unmodifiableMap(fluids);
            this.variants = com.tarbound.network.LooseMatch.index(items.keySet());
        }

        private long have(ItemKey k) {
            long n = items.getOrDefault(k, 0L);
            if (!fluids.isEmpty() && TarNetwork.filledInfo(k.proto()) != null) {
                n += TarNetwork.fillable(k.proto(), items, fluids);
            }
            return n;
        }

        /** Every slot can be supplied (shared items counted once; filled buckets the pool could make count too). */
        public boolean canSupply(Slots slots) {
            Map<ItemKey, Long> used = null;
            for (int i = 0; i < slots.keys.length; i++) {
                ItemKey[] alts = slots.keys[i];
                if (alts.length == 0) {
                    continue;
                }
                ItemKey hit = null;
                long need = slots.counts[i];
                for (ItemKey k : alts) {
                    if (fits(k, need, used)) {
                        hit = k;
                        break;
                    }
                }
                if (hit == null) {
                    // used or data-carrying copies of a plain alternative (a damaged hammer)
                    outer:
                    for (ItemKey k : alts) {
                        if (!k.proto().getComponentsPatch().isEmpty()) {
                            continue;
                        }
                        for (ItemKey v : variants.getOrDefault(k.proto().getItem(), List.of())) {
                            if (fits(v, need, used)) {
                                hit = v;
                                break outer;
                            }
                        }
                    }
                }
                if (hit == null) {
                    return false;
                }
                if (used == null) {
                    used = new HashMap<>();
                }
                used.merge(hit, need, Long::sum);
            }
            return true;
        }

        private boolean fits(ItemKey k, long need, @javax.annotation.Nullable Map<ItemKey, Long> used) {
            long left = have(k) - (used == null ? 0 : used.getOrDefault(k, 0L));
            return left >= need;
        }

        public boolean hasFluids(List<FluidStack> wanted) {
            Map<FluidKey, Long> used = new HashMap<>();
            for (FluidStack f : wanted) {
                FluidKey k = FluidKey.of(f);
                long total = used.merge(k, (long) f.getAmount(), Long::sum);
                if (fluids.getOrDefault(k, 0L) < total) {
                    return false;
                }
            }
            return true;
        }
    }

    /**
     * A recipe's slots, compiled once into item keys: checking a recipe then makes no new stacks or keys.
     * Each slot uses the first alternative's count (alternatives share it, as everywhere else).
     */
    public static final class Slots {
        final ItemKey[][] keys;
        final long[] counts;

        public Slots(List<List<ItemStack>> slots) {
            keys = new ItemKey[slots.size()][];
            counts = new long[slots.size()];
            for (int i = 0; i < slots.size(); i++) {
                List<ItemStack> alts = slots.get(i);
                java.util.LinkedHashSet<ItemKey> set = new java.util.LinkedHashSet<>();
                for (ItemStack a : alts) {
                    if (!a.isEmpty()) {
                        set.add(ItemKey.of(a));
                    }
                }
                keys[i] = set.toArray(new ItemKey[0]);
                counts[i] = alts.isEmpty() ? 1 : Math.max(1, alts.get(0).getCount());
            }
        }
    }

    /** Cached snapshots: [0] without the crafting grid (machine fills), [1] with it (grid fills). */
    private static final Snapshot[] cached = new Snapshot[2];
    @javax.annotation.Nullable
    private static ScryingFontMenu cachedMenu;
    /** What the player controls (inventory, grid, cursor): a change there shows at once. */
    private static long cachedPlayerSig;
    private static int cachedPoolVersion;
    private static long cachedAt;
    /**
     * How often (ms) storage changes alone are picked up. With machines running the pool changes every
     * second or so, and every change made EMI re-check every recipe; nobody needs the craftables list to
     * be fresher than this.
     */
    private static final long POOL_REFRESH_MS = 2000;

    /** The current snapshot for {@code menu}; {@code includeGrid}: count the crafting grid as available. */
    public static Snapshot snapshot(ScryingFontMenu menu, Player player, boolean includeGrid) {
        long sig = 17;
        for (ItemStack s : player.getInventory().items) {
            sig = sig * 31 + stackSig(s);
        }
        for (int i = ScryingFontMenu.GRID_START; i < ScryingFontMenu.GRID_END; i++) {
            sig = sig * 31 + stackSig(menu.getSlot(i).getItem());
        }
        sig = sig * 31 + stackSig(menu.getCarried());
        long now = System.currentTimeMillis();
        boolean stale = cachedMenu != menu || cachedPlayerSig != sig
                || (cachedPoolVersion != menu.clientVersion() && now - cachedAt >= POOL_REFRESH_MS);
        if (stale) {
            cached[0] = null;
            cached[1] = null;
            cachedMenu = menu;
            cachedPlayerSig = sig;
            cachedPoolVersion = menu.clientVersion();
            cachedAt = now;
        }
        int i = includeGrid ? 1 : 0;
        if (cached[i] == null) {
            cached[i] = new Snapshot(available(menu, player, includeGrid), fluids(menu));
        }
        return cached[i];
    }

    private static long stackSig(ItemStack s) {
        return s.isEmpty() ? 0 : ItemStack.hashItemAndComponents(s) * 131L + s.getCount();
    }

    private RecipeFill() {}
}
