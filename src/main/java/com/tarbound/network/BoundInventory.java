package com.tarbound.network;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * One inventory block bound to a Scrying Font.
 *
 * <p>Storage blocks touch the tar directly, look the same from every side, and feed the shared pool
 * through {@link #poolHandler()}. Machines are bound through a Reliquary on top of them; they stay out
 * of the pool and expose every slot (as their own GUI would) through {@link #slots()}.
 */
public final class BoundInventory {
    public enum Kind {
        STORAGE,
        MACHINE
    }

    /** What a machine slot is for, as far as can be told from what it accepts. */
    public enum Role {
        INPUT,
        FUEL,
        OUTPUT
    }

    /** One machine slot: a handler and an index into it. */
    public record SlotRef(IItemHandler handler, int slot) {
        public ItemStack stack() {
            return handler.getStackInSlot(slot);
        }
    }

    private static final ItemStack FUEL_PROBE = new ItemStack(Items.COAL);
    private static final ItemStack NON_FUEL_PROBE = new ItemStack(Items.COBBLESTONE);

    private final BlockPos pos;
    private final BlockState state;
    private final Kind kind;
    private final IItemHandler poolHandler;
    private final List<SlotRef> slots;
    private final PoolAccess access;
    private final int priority;
    private final boolean station;
    /**
     * The block entities behind the handler (a double chest has two), so a binding can tell its
     * inventory was broken since the scan and stop writing into it. Empty for blocks without one.
     */
    private final List<BlockEntity> owners;
    /** The slots are the work surface under the machine (a depot under a spout), not the machine's own. */
    private final boolean surfaceBelow;

    private BoundInventory(BlockPos pos, BlockState state, Kind kind, IItemHandler poolHandler, List<SlotRef> slots, PoolAccess access,
            int priority, boolean station, List<BlockEntity> owners) {
        this(pos, state, kind, poolHandler, slots, access, priority, station, owners, false);
    }

    private BoundInventory(BlockPos pos, BlockState state, Kind kind, IItemHandler poolHandler, List<SlotRef> slots, PoolAccess access,
            int priority, boolean station, List<BlockEntity> owners, boolean surfaceBelow) {
        this.surfaceBelow = surfaceBelow;
        this.pos = pos;
        this.state = state;
        this.kind = kind;
        this.poolHandler = poolHandler;
        this.slots = slots;
        this.access = access;
        this.priority = priority;
        this.station = station;
        this.owners = owners;
    }

    /**
     * False once a block entity behind this binding has been removed (the chest was broken or moved
     * after this tick's scan). Its handler would still take items and void them, so the pool skips it.
     */
    public boolean alive() {
        for (int i = 0; i < owners.size(); i++) {
            if (owners.get(i).isRemoved()) {
                return false;
            }
        }
        return true;
    }

    /**
     * A crafting station: a Reliquary on a crafting table. A machine with no slots; its jobs craft
     * straight from the pool (see {@link com.tarbound.contract.CraftingContracts}).
     */
    public static BoundInventory station(BlockPos pos, BlockState state) {
        return new BoundInventory(pos.immutable(), state, Kind.MACHINE, null, List.of(), PoolAccess.COMMUNE, 0, true, List.of());
    }

    /**
     * The slots belong to the work surface below the machine (Create's spout over a depot). Items there
     * that a contract on this machine makes are drained back to the pool even though the surface would
     * take them again.
     */
    public boolean surfaceBelow() {
        return surfaceBelow;
    }

    /** A crafting station rather than a machine with an inventory. */
    public boolean isStation() {
        return station;
    }

    /** Storage only: higher priority storage is filled first and emptied last. */
    public int priority() {
        return priority;
    }

    /** The same binding with a different priority. */
    public BoundInventory withPriority(int priority) {
        return priority == this.priority ? this : new BoundInventory(pos, state, kind, poolHandler, slots, access, priority, station, owners, surfaceBelow);
    }

    /**
     * Queries every side of the block at {@code pos}.
     *
     * @param contact the side of this block touching the tar (null for bound machines)
     * @param viaReliquary   a Reliquary sits on this block: it is a machine whatever it looks like
     * @param access  how the pool may use it, if it's storage
     * @return null if no side exposes any item slots
     */
    @Nullable
    public static BoundInventory probe(Level level, BlockPos pos, BlockState state, @Nullable Direction contact, boolean viaReliquary,
            PoolAccess access) {
        BlockEntity be = state.hasBlockEntity() ? level.getBlockEntity(pos) : null;
        // A machine's own GUI view where the side-less capability isn't one (Mekanism's is read-only).
        IItemHandler all = viaReliquary ? nonEmpty(MachineAccess.items(be)) : null;
        if (all == null) {
            all = nonEmpty(level.getCapability(Capabilities.ItemHandler.BLOCK, pos, state, be, null));
        }
        IItemHandler[] byDir = new IItemHandler[6];
        boolean any = all != null;
        for (Direction d : Direction.values()) {
            byDir[d.get3DDataValue()] = nonEmpty(level.getCapability(Capabilities.ItemHandler.BLOCK, pos, state, be, d));
            any |= byDir[d.get3DDataValue()] != null;
        }
        if (!any) {
            return viaReliquary ? slotless(level, pos, state, be) : null;
        }

        WorldlyContainer worldly = be instanceof WorldlyContainer wc ? wc : null;
        Kind kind = viaReliquary ? Kind.MACHINE : detectKind(worldly, all, byDir);

        IItemHandler pool = all;
        if (pool == null) {
            IItemHandler contactHandler = contact == null ? null : byDir[contact.get3DDataValue()];
            pool = contactHandler != null ? contactHandler : firstNonNull(byDir);
        }

        List<SlotRef> slots = new ArrayList<>();
        if (kind == Kind.MACHINE) {
            // Prefer the side-less view: every slot, the way the machine's own screen sees them.
            List<IItemHandler> handlers = new ArrayList<>();
            if (all != null) {
                handlers.add(all);
            } else {
                List<int[]> keys = new ArrayList<>();
                for (Direction d : Direction.values()) {
                    IItemHandler h = byDir[d.get3DDataValue()];
                    if (h == null) {
                        continue;
                    }
                    int[] key = worldly == null ? null : slotKey(worldly, d);
                    boolean dup = false;
                    for (int i = 0; i < handlers.size() && !dup; i++) {
                        dup = sameView(h, key, handlers.get(i), keys.get(i));
                    }
                    if (!dup) {
                        handlers.add(h);
                        keys.add(key);
                    }
                }
            }
            for (IItemHandler h : handlers) {
                for (int i = 0; i < h.getSlots(); i++) {
                    slots.add(new SlotRef(h, i));
                }
            }
        }
        List<BlockEntity> owners = new ArrayList<>(2);
        if (be != null) {
            owners.add(be);
        }
        if (state.getBlock() instanceof net.minecraft.world.level.block.ChestBlock && state.hasProperty(net.minecraft.world.level.block.ChestBlock.TYPE)
                && state.getValue(net.minecraft.world.level.block.ChestBlock.TYPE) != net.minecraft.world.level.block.state.properties.ChestType.SINGLE) {
            BlockEntity partner = level.getBlockEntity(pos.relative(net.minecraft.world.level.block.ChestBlock.getConnectedDirection(state)));
            if (partner != null) {
                owners.add(partner);
            }
        }
        return new BoundInventory(pos.immutable(), state, kind, pool, List.copyOf(slots), access, 0, false, List.copyOf(owners));
    }

    /**
     * A bound machine with no item slots of its own: its work surface below (a spout two blocks over a depot) if
     * there is one, else just its tanks. Null if it has neither.
     */
    @Nullable
    private static BoundInventory slotless(Level level, BlockPos pos, BlockState state, @Nullable BlockEntity be) {
        List<BlockEntity> owners = new ArrayList<>(2);
        if (be != null) {
            owners.add(be);
        }
        // Create's spout and press work on a depot or belt two blocks down, over a one-block gap
        // (DepotBehaviour looks for its processor at pos.above(2)); directly below is checked too.
        for (int drop = 2; drop >= 1; drop--) {
            BlockPos below = pos.below(drop);
            BlockState belowState = level.getBlockState(below);
            if (belowState.is(com.tarbound.registry.ModTags.WORK_SURFACE)) {
                BlockEntity belowBe = belowState.hasBlockEntity() ? level.getBlockEntity(below) : null;
                IItemHandler surface = nonEmpty(level.getCapability(Capabilities.ItemHandler.BLOCK, below, belowState, belowBe, Direction.UP));
                if (surface == null) {
                    surface = nonEmpty(level.getCapability(Capabilities.ItemHandler.BLOCK, below, belowState, belowBe, null));
                }
                if (surface != null) {
                    if (belowBe != null) {
                        owners.add(belowBe);
                    }
                    List<SlotRef> slots = new ArrayList<>();
                    for (int i = 0; i < surface.getSlots(); i++) {
                        slots.add(new SlotRef(surface, i));
                    }
                    return new BoundInventory(pos.immutable(), state, Kind.MACHINE, null, List.copyOf(slots), PoolAccess.COMMUNE, 0, false,
                            List.copyOf(owners), true);
                }
            }
        }
        if (MachineAccess.fluids(level, pos) != null) {
            return new BoundInventory(pos.immutable(), state, Kind.MACHINE, null, List.of(), PoolAccess.COMMUNE, 0, false, List.copyOf(owners));
        }
        return null;
    }

    /**
     * Best guess at a slot's purpose, for display and for routing:
     * <ul>
     *   <li>OUTPUT: holds something it wouldn't accept back, or accepts neither probe item;</li>
     *   <li>FUEL: takes coal but not cobblestone;</li>
     *   <li>INPUT: anything else.</li>
     * </ul>
     */
    public static Role roleOf(IItemHandler h, int slot) {
        ItemStack in = h.getStackInSlot(slot);
        if (isFinishedOutput(h, slot)) {
            return Role.OUTPUT;
        }
        return roleFromProbes(takes(h, slot, FUEL_PROBE), takes(h, slot, NON_FUEL_PROBE), in.isEmpty());
    }

    /**
     * Whether a slot would take {@code probe}. Its validity check, and while it's empty a simulated insert as well:
     * some machines (Immersive Engineering, EnderIO) call every item valid and refuse it only at insertion.
     */
    private static boolean takes(IItemHandler h, int slot, ItemStack probe) {
        if (!h.isItemValid(slot, probe)) {
            return false;
        }
        return !h.getStackInSlot(slot).isEmpty() || h.insertItem(slot, probe.copy(), true).getCount() < probe.getCount();
    }

    private static Role roleFromProbes(boolean coal, boolean cobble, boolean empty) {
        if (coal && !cobble) {
            return Role.FUEL;
        }
        if (!coal && !cobble && empty) {
            return Role.OUTPUT;
        }
        return Role.INPUT;
    }

    /**
     * Which machine slots take the coal and cobblestone probes, per block (bit 1 = coal, bit 2 =
     * cobblestone). The answer depends on the kind of machine, not what's in it, so it's worked out
     * once per block type rather than twice per slot on every screen refresh and every load (some
     * modded machines run a recipe lookup inside isItemValid). Recomputed if a machine of that block
     * has a different slot count (upgrades and the like). Server thread only.
     */
    private static final Map<ProbeKey, byte[]> PROBES = new java.util.HashMap<>();

    /**
     * The block, and the class of the handler the slots came from: a proxy block (Entangled, Modern Dynamics' Machine
     * Extender) is one block for whatever it's bound to, so the handler class is what tells a furnace behind it from
     * a Mekanism machine. Proxies that get re-bound are also forgotten through {@link #forgetProbes}.
     */
    private record ProbeKey(net.minecraft.world.level.block.Block block, Class<?> handler) {}

    /** Drops the cached slot probes for a block, when what it reaches may have changed (its capabilities were invalidated). */
    public static void forgetProbes(net.minecraft.world.level.block.Block block) {
        PROBES.keySet().removeIf(k -> k.block() == block);
    }

    private ProbeKey probeKey() {
        return new ProbeKey(state.getBlock(), slots.isEmpty() ? Void.class : slots.get(0).handler().getClass());
    }

    @Nullable
    private byte[] probes;

    /** {@link #roleOf} for machine slot {@code index} (into {@link #slots()}), using the probe cache. */
    public Role role(int index) {
        SlotRef r = slots.get(index);
        ItemStack in = r.stack();
        if (isFinishedOutput(r.handler(), r.slot())) {
            return Role.OUTPUT;
        }
        byte p = probes()[index];
        return roleFromProbes((p & 1) != 0, (p & 2) != 0, in.isEmpty());
    }

    private byte[] probes() {
        if (probes == null) {
            byte[] cached = PROBES.get(probeKey());
            if (cached == null || cached.length != slots.size()) {
                cached = new byte[slots.size()];
                for (int i = 0; i < cached.length; i++) {
                    SlotRef r = slots.get(i);
                    cached[i] = (byte) ((takes(r.handler(), r.slot(), FUEL_PROBE) ? 1 : 0)
                            | (takes(r.handler(), r.slot(), NON_FUEL_PROBE) ? 2 : 0));
                }
                PROBES.put(probeKey(), cached);
            }
            probes = cached;
        }
        return probes;
    }

    /**
     * Strict test used when draining a machine into the pool: the slot holds something that can be taken out and
     * that it would refuse to take more of. Asked by simulated insert while the slot has room, since many machines
     * call anything valid and refuse it only at insertion (Immersive Engineering, EnderIO, Mekanism outputs); by
     * {@code isItemValid} once it's full, since a full slot refuses everything (Oritech reports a full input slot
     * as invalid, but won't give it up, so it isn't drained). Input slots take more of what they hold, or won't let
     * it go, so they're never drained.
     */
    public static boolean isFinishedOutput(IItemHandler h, int slot) {
        ItemStack in = h.getStackInSlot(slot);
        if (in.isEmpty()) {
            return false;
        }
        int limit = Math.min(h.getSlotLimit(slot), in.getMaxStackSize());
        boolean refuses = in.getCount() < limit
                ? h.insertItem(slot, in.copyWithCount(1), true).getCount() >= 1
                : !h.isItemValid(slot, in);
        return refuses && !h.extractItem(slot, 1, true).isEmpty();
    }

    @Nullable
    private static IItemHandler nonEmpty(@Nullable IItemHandler h) {
        return h != null && h.getSlots() > 0 ? h : null;
    }

    @Nullable
    private static IItemHandler firstNonNull(IItemHandler[] handlers) {
        for (IItemHandler h : handlers) {
            if (h != null) {
                return h;
            }
        }
        return null;
    }

    /**
     * Storage if every side reaches the same slots. Exact for vanilla-style sided containers; a
     * heuristic (same slot count on all six sides) for capability-only blocks.
     */
    private static Kind detectKind(@Nullable WorldlyContainer worldly, @Nullable IItemHandler all, IItemHandler[] byDir) {
        if (worldly != null) {
            int size = worldly.getContainerSize();
            for (Direction d : Direction.values()) {
                if (worldly.getSlotsForFace(d).length != size) {
                    return Kind.MACHINE;
                }
            }
            return Kind.STORAGE;
        }
        int count = -1;
        for (IItemHandler h : byDir) {
            if (h == null) {
                return Kind.MACHINE;
            }
            if (count == -1) {
                count = h.getSlots();
            } else if (h.getSlots() != count) {
                return Kind.MACHINE;
            }
        }
        if (all != null && all.getSlots() != count) {
            return Kind.MACHINE;
        }
        return Kind.STORAGE;
    }

    private static int[] slotKey(WorldlyContainer worldly, Direction d) {
        int[] slots = worldly.getSlotsForFace(d).clone();
        Arrays.sort(slots);
        return slots;
    }

    private static boolean sameView(IItemHandler a, @Nullable int[] aKey, IItemHandler b, @Nullable int[] bKey) {
        if (aKey != null && bKey != null) {
            return Arrays.equals(aKey, bKey);
        }
        if (a == b || a.equals(b)) {
            return true;
        }
        int n = a.getSlots();
        if (n != b.getSlots()) {
            return false;
        }
        boolean sawItem = false;
        for (int i = 0; i < n; i++) {
            ItemStack sa = a.getStackInSlot(i);
            if (sa != b.getStackInSlot(i)) {
                return false;
            }
            sawItem |= !sa.isEmpty();
        }
        return sawItem;
    }

    public BlockPos pos() {
        return pos;
    }

    public BlockState state() {
        return state;
    }

    public Kind kind() {
        return kind;
    }

    /** The same binding with a different access. */
    public BoundInventory withAccess(PoolAccess access) {
        return access == this.access ? this : new BoundInventory(pos, state, kind, poolHandler, slots, access, priority, station, owners, surfaceBelow);
    }

    /** Storage only: how the pool may use it (Commune, Scry or Tithe). */
    public PoolAccess access() {
        return access;
    }

    /** Storage only: the handler the pool reads and writes. */
    public IItemHandler poolHandler() {
        return poolHandler;
    }

    /**
     * Storage that never runs out and swallows whatever goes in: Create's creative crate, or a Bottomless Seep. The
     * radio only takes from it, and counts it as plenty.
     */
    public boolean bottomless() {
        if (poolHandler == null) {
            return false;
        }
        String cls = poolHandler.getClass().getName();
        return cls.equals("com.simibubi.create.content.logistics.crate.BottomlessItemHandler")
                || state.is(com.tarbound.registry.ModBlocks.BOTTOMLESS_SEEP.get());
    }

    /** Machines only: every slot, in the machine's own order. */
    public List<SlotRef> slots() {
        return slots;
    }
}
