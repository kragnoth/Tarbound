package com.tarbound.network;

import com.tarbound.block.ReliquaryBlock;
import com.tarbound.block.ReliquaryBlockEntity;
import com.tarbound.registry.ModFluids;
import com.tarbound.registry.ModTags;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * A snapshot of one body of tar around a Scrying Font, and what it binds:
 * <ul>
 *   <li>the tar: every tar block connected to any tar within {@link #reach()} blocks of the radio;</li>
 *   <li>storage: inventory blocks that touch that tar directly and look the same from every side;</li>
 *   <li>machines: blocks with a Reliquary on top, where the reliquary is tuned to this radio (anywhere in its dimension);</li>
 *   <li>whether a crafting table touches the tar (the radio's grid only crafts if one does).</li>
 * </ul>
 * Tar Antennas tuned to the radio stand in for tar, once the radio can reach them: an antenna is in
 * range if it's within reach (3 blocks per piece, a cube around its mounted end) of the radio or of
 * another in-range antenna tuned to the same radio. Two points link if they're within the larger of
 * their two reaches, so a broadcast mast midway can relay to rabbit ears at the far end. The radio itself
 * reaches {@link #reach()}. Whatever touches any piece of an in-range antenna counts as touching the tar: inventory blocks bind as storage, tar joins the body, a crafting table unlocks
 * the grid. Reliquaries bind their machines within the antenna's reach (3 blocks per piece, a cube
 * around its mounted end). Blocks tagged {@code tarbound:never_pooled} (conveyors such as Create
 * chutes and belts) never join the pool.
 * Storage and machines are unlimited.
 * <p>
 * Snapshots are cheap and short-lived. Never hold one across ticks for writing: a broken chest's
 * handler would still accept items and void them.
 */
public final class TarNetwork {
    /** Upper bound on tar blocks walked per scan, so a flooded ocean of tar can't stall the server. */
    public static int maxTarBlocks() {
        return com.tarbound.config.TarboundConfig.SERVER.maxTarBlocks.get();
    }
    /** How far the radio and Reliquaries reach to the tar (a cube, so diagonals count). */
    public static int reach() {
        return com.tarbound.config.TarboundConfig.SERVER.radioReach.get();
    }

    public static final TarNetwork EMPTY = new TarNetwork(List.of(), List.of(), 0, 0, false);

    private final List<BoundInventory> bound;
    private final List<BoundInventory> storage;
    private final List<BoundInventory> extractOrder;
    private final List<BoundInventory> machines;
    private final int outOfRange;
    private final int unbound;
    private final boolean craftingTable;
    /** Fluid tanks in the pool, in insert order; and in drain order (lowest priority first). */
    private final List<FluidStore> tanks;
    private final List<FluidStore> tankDrainOrder;

    private TarNetwork(List<BoundInventory> bound, List<FluidStore> tanks, int outOfRange, int unbound, boolean craftingTable) {
        this.bound = bound;
        this.tanks = tanks.stream().sorted(Comparator.comparingInt(FluidStore::insertTier).reversed()).toList();
        this.tankDrainOrder = tanks.stream().sorted(Comparator.comparingInt(FluidStore::priority)).toList();
        // Highest priority first; within a priority, storage the radio can see before Tithe (hidden)
        // storage, then nearest the radio first (bound is already nearest-first).
        this.storage = bound.stream().filter(b -> b.kind() == BoundInventory.Kind.STORAGE)
                .sorted(Comparator.comparingInt(TarNetwork::insertTier).reversed()).toList();
        // Extraction order: lowest priority first, still nearest first within a priority.
        this.extractOrder = bound.stream().filter(b -> b.kind() == BoundInventory.Kind.STORAGE)
                .sorted(Comparator.comparingInt(BoundInventory::priority)).toList();
        this.machines = bound.stream().filter(b -> b.kind() == BoundInventory.Kind.MACHINE).toList();
        this.outOfRange = outOfRange;
        this.unbound = unbound;
        this.craftingTable = craftingTable;
    }

    /**
     * Insert order key: priority first, then visible before hidden. At the same priority a Tithe
     * inventory only gets what the visible storage can't hold, so everyday inserts don't vanish into
     * write-only chests; give a Tithe column a higher priority to feed it first on purpose.
     */
    private static int insertTier(BoundInventory b) {
        return b.priority() * 2 + (b.access().canExtract() ? 1 : 0);
    }

    public static boolean isTar(FluidState state) {
        return !state.isEmpty() && state.getType().isSame(ModFluids.TAR.get());
    }

    /**
     * Part of a tar body: tar itself, or a solid block that carries it (tar-soaked logs and planks,
     * tag {@code tarbound:tar_conduits}) so links can run hidden under floors and through walls.
     */
    public static boolean isTarBody(BlockState state) {
        return isTar(state.getFluidState()) || state.is(ModTags.TAR_CONDUITS);
    }

    /** The tar body this scan found (tar and conduit blocks), for asking whether a spot belongs to it. */
    private LongSet tarBody = new LongOpenHashSet();

    /** Whether {@code pos} is part of this radio's tar body (as of this tick's scan). */
    public boolean holdsTar(BlockPos pos) {
        return tarBody.contains(pos.asLong());
    }

    /** @param access how the pool may use it; null once two bindings together allow nothing */
    private record Candidate(BlockPos pos, BlockState state, @Nullable Direction contact, boolean viaReliquary, @Nullable PoolAccess access,
            int priority) {}

    /** Adds a storage candidate; bound more than one way, the stricter access wins. A reliquary always wins. */
    private static void offerStorage(Map<Long, Candidate> candidates, long key, Candidate c) {
        Candidate old = candidates.get(key);
        if (old == null) {
            candidates.put(key, c);
        } else if (!old.viaReliquary() && old.access() != null) {
            candidates.put(key, new Candidate(old.pos(), old.state(), old.contact(), false, PoolAccess.stricter(old.access(), c.access()),
                    Math.max(old.priority(), c.priority())));
        }
    }

    public static TarNetwork scan(Level level, BlockPos origin) {
        LongSet tar = new LongOpenHashSet();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        boolean craftingTable = false;

        // Seed with every tar block within reach of the radio, and every tar block touching an antenna tuned to it.
        List<AntennaRegistry.Column> tuned = AntennaRegistry.tunedTo(level, origin);
        List<AntennaRegistry.Column> antennas = inRange(origin, tuned);
        int outOfRange = tuned.size() - antennas.size();
        seedTar(level, origin, reach(), tar, queue);
        for (AntennaRegistry.Column a : antennas) {
            for (BlockPos piece : a.pieces()) {
                for (Direction d : Direction.values()) {
                    BlockPos n = piece.relative(d);
                    if (level.isLoaded(n) && isTarBody(level.getBlockState(n)) && tar.size() < maxTarBlocks() && tar.add(n.asLong())) {
                        queue.add(n);
                    }
                }
            }
        }

        LongSet touched = new LongOpenHashSet();
        LongSet weepingSeeps = new LongOpenHashSet();
        Map<Long, Candidate> candidates = new HashMap<>();
        Map<Long, Candidate> fluidCandidates = new HashMap<>();

        while (!queue.isEmpty()) {
            BlockPos tarPos = queue.poll();
            for (Direction dir : Direction.values()) {
                BlockPos n = tarPos.relative(dir);
                long key = n.asLong();
                if (n.equals(origin) || tar.contains(key) || !level.isLoaded(n)) {
                    continue;
                }
                if (isTarBody(level.getBlockState(n))) {
                    if (tar.size() >= maxTarBlocks()) {
                        continue; // the tar body is capped at maxTarBlocks
                    }
                    tar.add(key);
                    queue.add(n);
                    continue;
                }
                if (!touched.add(key)) {
                    continue;
                }
                BlockState state = level.getBlockState(n);
                if (state.is(Tags.Blocks.PLAYER_WORKSTATIONS_CRAFTING_TABLES)) {
                    craftingTable = true;
                }
                if (state.getBlock() instanceof ReliquaryBlock || state.getBlock() instanceof com.tarbound.block.ScryingFontBlock) {
                    continue; // reliquaries are handled below (their pass-through isn't storage); a radio's offering slot isn't storage
                }
                if (state.is(com.tarbound.registry.ModBlocks.WEEPING_SEEP.get())) {
                    weepingSeeps.add(key);
                    continue;
                }
                Direction contact = dir.getOpposite();
                if (state.is(ModTags.NEVER_POOLED)) {
                    continue;
                }
                if (level.getCapability(Capabilities.ItemHandler.BLOCK, n, state, null, contact) != null
                        || level.getCapability(Capabilities.ItemHandler.BLOCK, n, state, null, null) != null) {
                    offerStorage(candidates, canonicalKey(n, state), new Candidate(n, state, contact, false, PoolAccess.COMMUNE, 0));
                }
                if (fluidHandler(level, n, state, null, contact) != null) {
                    offerStorage(fluidCandidates, n.asLong(), new Candidate(n.immutable(), state, contact, false, PoolAccess.COMMUNE, 0));
                }
            }
        }

        // Reliquaries tuned to this radio bind the block they face (or, all-sides, every machine around
        // them), wherever they are in the dimension (a reliquary outranks direct contact). Found through
        // the registry, never by searching.
        Set<Long> boundMachines = new HashSet<>();
        for (BlockPos reliquaryPos : ReliquaryRegistry.tunedTo(level, origin)) {
            BlockState reliquaryState = level.getBlockState(reliquaryPos);
            if (!(reliquaryState.getBlock() instanceof ReliquaryBlock)) {
                continue;
            }
            for (BlockPos machine : ReliquaryBlock.targets(level, reliquaryPos, reliquaryState)) {
                if (!level.isLoaded(machine) || !reliquaryPos.equals(ReliquaryBlock.reliquaryPosFor(level, machine))) {
                    continue; // another Reliquary has this machine first
                }
                BlockState state = level.getBlockState(machine);
                boundMachines.add(machine.asLong()); // a bound machine's tanks are its own, not the pool's
                if (!(state.getBlock() instanceof ReliquaryBlock) && !(state.getBlock() instanceof com.tarbound.block.ScryingFontBlock)) {
                    candidates.put(canonicalKey(machine, state), new Candidate(machine.immutable(), state, null, true, PoolAccess.COMMUNE, 0));
                }
            }
        }

        for (AntennaRegistry.Column a : antennas) {
            // Whatever touches the antenna counts as touching the tar.
            List<BlockPos> pieces = a.pieces();
            for (BlockPos piece : pieces) {
                for (Direction dir : Direction.values()) {
                    BlockPos n = piece.relative(dir);
                    if (pieces.contains(n) || n.equals(origin) || !level.isLoaded(n)) {
                        continue;
                    }
                    BlockState st = level.getBlockState(n);
                    if (st.is(Tags.Blocks.PLAYER_WORKSTATIONS_CRAFTING_TABLES)) {
                        craftingTable = true;
                    }
                    if (st.is(com.tarbound.registry.ModBlocks.WEEPING_SEEP.get())) {
                        weepingSeeps.add(n.asLong());
                        continue;
                    }
                    if (st.getBlock() instanceof ReliquaryBlock || st.getBlock() instanceof com.tarbound.block.TarAntennaBlock
                            || st.getBlock() instanceof com.tarbound.block.ScryingFontBlock
                            || st.is(ModTags.NEVER_POOLED)) {
                        continue;
                    }
                    BlockEntity be = st.hasBlockEntity() ? level.getBlockEntity(n) : null;
                    if (hasItems(level, n, st, be)) {
                        offerStorage(candidates, canonicalKey(n, st), new Candidate(n.immutable(), st, dir.getOpposite(), false, a.access(), a.priority()));
                    }
                    if (fluidHandler(level, n, st, be, dir.getOpposite()) != null) {
                        offerStorage(fluidCandidates, n.asLong(), new Candidate(n.immutable(), st, dir.getOpposite(), false, a.access(), a.priority()));
                    }
                }
            }
        }

        List<Candidate> ordered = new ArrayList<>(candidates.values());
        ordered.sort(Comparator.comparingDouble(c -> c.pos().distSqr(origin)));

        List<BoundInventory> bound = new ArrayList<>();
        int unbound = 0;
        List<BlockPos> unboundAt = new ArrayList<>();
        for (Candidate c : ordered) {
            if (c.access() == null) {
                continue; // Scry and Tithe at once: the radio may neither take nor give
            }
            if (c.viaReliquary() && com.tarbound.contract.StationContracts.kindOf(c.state()) != null) {
                // a station (crafting table, stonecutter, composter): no slots, works straight from the pool
                bound.add(BoundInventory.station(c.pos(), c.state()));
                continue;
            }
            BoundInventory b = BoundInventory.probe(level, c.pos(), c.state(), c.contact(), c.viaReliquary(), c.access());
            if (b == null) {
                continue;
            }
            b = b.withPriority(c.priority());
            if (b.kind() == BoundInventory.Kind.STORAGE && b.bottomless()) {
                // a creative source (Create's creative crate, a Bottomless Seep): the radio only takes from it, since
                // anything put in would vanish
                if (!b.access().canExtract()) {
                    continue;
                }
                b = b.withAccess(PoolAccess.SCRY);
            }
            if (!c.viaReliquary() && b.kind() == BoundInventory.Kind.MACHINE) {
                unbound++; // machines only bind through a reliquary
                unboundAt.add(c.pos());
                fluidCandidates.remove(c.pos().asLong()); // ...and their tanks stay out of the pool too
                continue;
            }
            bound.add(b);
        }

        fluidCandidates.keySet().removeAll(boundMachines); // after the antenna pass, which could re-offer them
        List<FluidStore> tanks = new ArrayList<>();
        List<Candidate> fluidOrdered = new ArrayList<>(fluidCandidates.values());
        fluidOrdered.sort(Comparator.comparingDouble(c -> c.pos().distSqr(origin)));
        for (Candidate c : fluidOrdered) {
            if (c.access() == null) {
                continue;
            }
            BlockEntity be = c.state().hasBlockEntity() ? level.getBlockEntity(c.pos()) : null;
            IFluidHandler h = fluidHandler(level, c.pos(), c.state(), be, c.contact());
            if (h != null) {
                PoolAccess access = c.access();
                if (be instanceof com.tarbound.block.BottomlessSeepBlockEntity) {
                    if (!access.canExtract()) {
                        continue;
                    }
                    access = PoolAccess.SCRY; // it only gives
                }
                tanks.add(new FluidStore(c.pos(), h, access, c.priority(), be == null ? List.of() : List.of(be)));
            }
        }

        // A Bottomless Seep offers the raw inputs of the contracts on this radio's machines (worked out before anything
        // counts the pool).
        List<BoundInventory> boundMachinesNow = bound.stream().filter(b -> b.kind() == BoundInventory.Kind.MACHINE).toList();
        for (BoundInventory b : bound) {
            if (b.kind() == BoundInventory.Kind.STORAGE && level.getBlockEntity(b.pos()) instanceof com.tarbound.block.BottomlessSeepBlockEntity seep) {
                seep.offerFor(level, boundMachinesNow);
            }
        }
        for (Candidate c : fluidOrdered) {
            if (level.getBlockEntity(c.pos()) instanceof com.tarbound.block.BottomlessSeepBlockEntity seep) {
                seep.offerFor(level, boundMachinesNow);
            }
        }

        TarNetwork net = new TarNetwork(Collections.unmodifiableList(dedupeStorage(bound)), dedupeTanks(tanks), outOfRange, unbound,
                craftingTable);
        net.tarBody = tar;
        net.unboundAt = List.copyOf(unboundAt);
        net.bank = level.getBlockEntity(origin) instanceof com.tarbound.block.ScryingFontBlockEntity font ? font : null;
        if (com.tarbound.TarboundDebug.on()) {
            StringBuilder t = new StringBuilder();
            for (FluidStore f : net.tanks) {
                t.append("\n    tank ").append(f.pos().toShortString()).append(' ').append(com.tarbound.TarboundDebug.block(level, f.pos()))
                        .append(" access=").append(f.access()).append(" prio=").append(f.priority()).append(' ')
                        .append(com.tarbound.TarboundDebug.tanks(f.handler(), FluidStack.EMPTY));
            }
            com.tarbound.TarboundDebug.log(level, origin, "scan", net.storage.size() + " storage, " + net.machines.size() + " machines, "
                    + net.tanks.size() + " pool tanks" + t);
        }
        net.weepingSeeps = weepingSeeps.size();
        if (!weepingSeeps.isEmpty()) {
            List<com.tarbound.contract.ContractData> contracts = new ArrayList<>();
            for (BoundInventory b : boundMachinesNow) {
                if (ReliquaryBlock.reliquaryFor(level, b.pos()) instanceof ReliquaryBlockEntity r) {
                    r.contractSlots().stream().filter(java.util.Objects::nonNull).forEach(contracts::add);
                }
            }
            net.craftable = madeBy(contracts);
        }
        return net;
    }

    /** The block's fluid handler from the side touching the tar or antenna, else side-less; null if it has no tanks. */
    @Nullable
    private static IFluidHandler fluidHandler(Level level, BlockPos pos, BlockState state, @Nullable BlockEntity be, @Nullable Direction side) {
        IFluidHandler h = side == null ? null : level.getCapability(Capabilities.FluidHandler.BLOCK, pos, state, be, side);
        if (h == null) {
            h = level.getCapability(Capabilities.FluidHandler.BLOCK, pos, state, be, null);
        }
        return h != null && h.getTanks() > 0 ? h : null;
    }

    /**
     * Multiblock tanks (Create fluid tanks and the like) hand every block the controller's handler, or
     * show the controller's fluid: keep one, as for items.
     */
    private static List<FluidStore> dedupeTanks(List<FluidStore> tanks) {
        if (tanks.size() < 2) {
            return tanks;
        }
        List<FluidStore> bySize = new ArrayList<>(tanks);
        bySize.sort(Comparator.comparingInt((FluidStore t) -> t.handler().getTanks()).reversed());
        Map<IFluidHandler, FluidStore> kept = new IdentityHashMap<>();
        Set<FluidStack> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<FluidStore> dropped = Collections.newSetFromMap(new IdentityHashMap<>());
        for (FluidStore t : bySize) {
            if (kept.containsKey(t.handler())) {
                dropped.add(t);
                continue;
            }
            List<FluidStack> stacks = new ArrayList<>();
            for (int i = 0; i < t.handler().getTanks(); i++) {
                FluidStack f = t.handler().getFluidInTank(i);
                if (!f.isEmpty()) {
                    stacks.add(f);
                }
            }
            if (!stacks.isEmpty() && seen.containsAll(stacks)) {
                dropped.add(t);
                continue;
            }
            kept.put(t.handler(), t);
            seen.addAll(stacks);
        }
        if (dropped.isEmpty()) {
            return tanks;
        }
        List<FluidStore> out = new ArrayList<>();
        for (FluidStore t : tanks) {
            if (!dropped.contains(t)) {
                out.add(t);
            }
        }
        return out;
    }

    /**
     * Drops storage that is another view of an inventory already bound, so multiblocks aren't counted
     * once per block:
     * <ol>
     *   <li><b>Same handler object.</b> Create's item vault (and most multiblocks, and Sophisticated
     *       Storage's I/O block) hand every part the controller's own handler. The copy is dropped and
     *       the kept one takes the stricter access of the two.</li>
     *   <li><b>Same stacks.</b> A view whose non-empty stacks are all stack objects some other storage
     *       already returned (a member barrel behind its controller, or a per-side wrapper). Larger
     *       handlers are checked first, so the aggregate is the one kept.</li>
     * </ol>
     * Machines are left alone, and the rest keep their nearest-first order.
     */
    private static List<BoundInventory> dedupeStorage(List<BoundInventory> bound) {
        List<BoundInventory> storage = new ArrayList<>();
        for (BoundInventory b : bound) {
            if (b.kind() == BoundInventory.Kind.STORAGE) {
                storage.add(b);
            }
        }
        if (storage.size() < 2) {
            return bound;
        }
        List<BoundInventory> bySize = new ArrayList<>(storage);
        bySize.sort(Comparator.comparingInt((BoundInventory b) -> b.poolHandler().getSlots()).reversed());

        Map<IItemHandler, BoundInventory> kept = new IdentityHashMap<>();
        Set<ItemStack> seenStacks = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<BoundInventory> dropped = Collections.newSetFromMap(new IdentityHashMap<>());
        Map<BoundInventory, BoundInventory> replaced = new IdentityHashMap<>();
        for (BoundInventory b : bySize) {
            IItemHandler h = b.poolHandler();
            BoundInventory first = kept.get(h);
            if (first != null) {
                // 1. Same handler: one inventory reached twice. Keep one, with the stricter access.
                dropped.add(b);
                BoundInventory current = replaced.getOrDefault(first, first);
                PoolAccess access = PoolAccess.stricter(current.access(), b.access());
                if (access == null) {
                    dropped.add(first);
                } else {
                    BoundInventory merged = current.withAccess(access).withPriority(Math.max(current.priority(), b.priority()));
                    if (merged != current) {
                        replaced.put(first, merged);
                    }
                }
                continue;
            }
            List<ItemStack> stacks = new ArrayList<>();
            for (int slot = 0, n = h.getSlots(); slot < n; slot++) {
                ItemStack s = h.getStackInSlot(slot);
                if (!s.isEmpty()) {
                    stacks.add(s);
                }
            }
            if (!stacks.isEmpty() && seenStacks.containsAll(stacks)) {
                dropped.add(b); // 2. Every stack it shows, another storage already showed.
                continue;
            }
            kept.put(h, b);
            seenStacks.addAll(stacks);
        }
        if (dropped.isEmpty() && replaced.isEmpty()) {
            return bound;
        }
        List<BoundInventory> out = new ArrayList<>(bound.size());
        for (BoundInventory b : bound) {
            if (!dropped.contains(b)) {
                out.add(replaced.getOrDefault(b, b));
            }
        }
        return out;
    }

    /** Whether the antenna column mounted at {@code base} is within the radio's reach, directly or by relay. */
    public static boolean antennaInRange(Level level, BlockPos radio, BlockPos base) {
        for (AntennaRegistry.Column c : inRange(radio, AntennaRegistry.tunedTo(level, radio))) {
            if (c.base().equals(base)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The tuned antennas the radio can reach: within reach of the radio, or (relaying) of an antenna
     * already in range. A link holds if the two are within the larger of their reaches (cube distance
     * between the radio / mounted ends).
     */
    private static List<AntennaRegistry.Column> inRange(BlockPos origin, List<AntennaRegistry.Column> tuned) {
        List<AntennaRegistry.Column> reached = new ArrayList<>();
        List<AntennaRegistry.Column> pending = new ArrayList<>(tuned);
        ArrayDeque<AntennaRegistry.Column> frontier = new ArrayDeque<>();
        for (var it = pending.iterator(); it.hasNext(); ) {
            AntennaRegistry.Column a = it.next();
            if (chebyshev(origin, a.base()) <= Math.max(reach(), a.reach())) {
                it.remove();
                reached.add(a);
                frontier.add(a);
            }
        }
        while (!frontier.isEmpty() && !pending.isEmpty()) {
            AntennaRegistry.Column from = frontier.poll();
            for (var it = pending.iterator(); it.hasNext(); ) {
                AntennaRegistry.Column a = it.next();
                if (chebyshev(from.base(), a.base()) <= Math.max(from.reach(), a.reach())) {
                    it.remove();
                    reached.add(a);
                    frontier.add(a);
                }
            }
        }
        return reached;
    }

    private static int chebyshev(BlockPos a, BlockPos b) {
        return Math.max(Math.abs(a.getX() - b.getX()), Math.max(Math.abs(a.getY() - b.getY()), Math.abs(a.getZ() - b.getZ())));
    }

    /**
     * Every tar block in the cube of radius {@code r} around {@code centre}. Whole 16³ chunk sections
     * whose palette has no tar are skipped without looking at a single block, so a large radio reach
     * costs next to nothing where there's no tar. Unloaded chunks are skipped (and never loaded).
     */
    private static void seedTar(Level level, BlockPos centre, int r, LongSet tar, ArrayDeque<BlockPos> queue) {
        int x0 = centre.getX() - r, x1 = centre.getX() + r;
        int y0 = Math.max(level.getMinBuildHeight(), centre.getY() - r), y1 = Math.min(level.getMaxBuildHeight() - 1, centre.getY() + r);
        int z0 = centre.getZ() - r, z1 = centre.getZ() + r;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int cx = SectionPos.blockToSectionCoord(x0); cx <= SectionPos.blockToSectionCoord(x1); cx++) {
            for (int cz = SectionPos.blockToSectionCoord(z0); cz <= SectionPos.blockToSectionCoord(z1); cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) {
                    continue;
                }
                for (int sy = SectionPos.blockToSectionCoord(y0); sy <= SectionPos.blockToSectionCoord(y1); sy++) {
                    LevelChunkSection section = chunk.getSection(chunk.getSectionIndexFromSectionY(sy));
                    if (section.hasOnlyAir() || !section.maybeHas(TarNetwork::isTarBody)) {
                        continue;
                    }
                    int bx0 = Math.max(x0, SectionPos.sectionToBlockCoord(cx)), bx1 = Math.min(x1, SectionPos.sectionToBlockCoord(cx, 15));
                    int by0 = Math.max(y0, SectionPos.sectionToBlockCoord(sy)), by1 = Math.min(y1, SectionPos.sectionToBlockCoord(sy, 15));
                    int bz0 = Math.max(z0, SectionPos.sectionToBlockCoord(cz)), bz1 = Math.min(z1, SectionPos.sectionToBlockCoord(cz, 15));
                    for (int x = bx0; x <= bx1; x++) {
                        for (int y = by0; y <= by1; y++) {
                            for (int z = bz0; z <= bz1; z++) {
                                if (isTarBody(section.getBlockState(x & 15, y & 15, z & 15)) && tar.size() < maxTarBlocks()
                                        && tar.add(BlockPos.asLong(x, y, z))) {
                                    queue.add(p.set(x, y, z).immutable());
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private static boolean hasItems(Level level, BlockPos pos, BlockState state, @Nullable BlockEntity be) {
        if (level.getCapability(Capabilities.ItemHandler.BLOCK, pos, state, be, null) != null) {
            return true;
        }
        for (Direction d : Direction.values()) {
            if (level.getCapability(Capabilities.ItemHandler.BLOCK, pos, state, be, d) != null) {
                return true;
            }
        }
        return false;
    }

    /** Both halves of a double chest expose the same combined inventory; bind it once. */
    private static long canonicalKey(BlockPos pos, BlockState state) {
        if (state.getBlock() instanceof ChestBlock && state.hasProperty(ChestBlock.TYPE) && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
            BlockPos partner = pos.relative(ChestBlock.getConnectedDirection(state));
            return Math.min(pos.asLong(), partner.asLong());
        }
        return pos.asLong();
    }

    /** All bound blocks, nearest first. */
    /** Every binding's block is still there (a kept scan is dropped once one isn't). */
    public boolean allAlive() {
        for (BoundInventory b : bound) {
            if (!b.alive()) {
                return false;
            }
        }
        for (FluidStore t : tanks) {
            if (!t.alive()) {
                return false;
            }
        }
        return true;
    }

    public List<BoundInventory> bound() {
        return bound;
    }

    /** Bound machines, nearest the radio first. */
    public List<BoundInventory> machines() {
        return machines;
    }

    /** Bound blocks that feed the shared pool. */
    public List<BoundInventory> storage() {
        return storage;
    }

    @Nullable
    public BoundInventory find(BlockPos pos) {
        for (BoundInventory b : bound) {
            if (b.pos().equals(pos)) {
                return b;
            }
        }
        return null;
    }

    /** Tuned antennas the radio can't reach, directly or by relaying through other antennas. */
    public int outOfRange() {
        return outOfRange;
    }

    /** Machines touching the tar without a Reliquary (not bound). */
    /** Where the machines touching the tar without a Reliquary are (shown when a player inspects the radio). */
    private List<BlockPos> unboundAt = List.of();

    public List<BlockPos> unboundAt() {
        return unboundAt;
    }

    public int unbound() {
        return unbound;
    }

    /** A crafting table touches the tar, so the radio's grid can craft. */
    public boolean hasCraftingTable() {
        return craftingTable;
    }

    /** True when there is no pool storage to read from or write to. */
    public boolean isEmpty() {
        return storage.isEmpty();
    }

    /**
     * Totals of every item type the radio can see (Commune and Scry storage), in first-seen order.
     * Counted once per scan by walking every slot, then kept up to date by {@link #insert} and
     * {@link #extract}, so repeated calls in a tick are cheap. Returns a copy the caller may change.
     */
    public Map<ItemKey, Long> countContents() {
        if (counts == null) {
            counts = walkContents();
        }
        return new LinkedHashMap<>(counts);
    }

    /** How many of exactly this item the radio can see (cheap: no copy of the totals). */
    public long countOf(ItemKey key) {
        if (counts == null) {
            counts = walkContents();
        }
        return counts.getOrDefault(key, 0L);
    }

    /** Running totals for {@link #countContents()}; null until first asked for. */
    @Nullable
    private Map<ItemKey, Long> counts;

    /**
     * Forgets the cached totals, for a scan kept into a new tick (0.43): storage changes the radio didn't make
     * (hoppers, players, machines pushing out) must show up in counts and job credit straight away.
     */
    public void freshTick() {
        counts = null;
        fluidCounts = null;
    }

    /**
     * The pool's own version of an item a client sent back. Some items add data components to themselves when the
     * client only draws them (a Modonomicon guide book sets its {@code book_id} in {@code getName}), so the copy that
     * comes back from the screen can carry components the stored item never had, and match nothing. If there's no
     * exact match, the pool item of the same kind whose components the client's copy all agrees with is used (the
     * one with the most components, if several). Otherwise the client's copy, unchanged.
     */
    public ItemStack resolve(ItemStack clientProto) {
        if (clientProto.isEmpty()) {
            return clientProto;
        }
        Map<ItemKey, Long> pool = countContents();
        if (pool.containsKey(ItemKey.of(clientProto))) {
            return clientProto;
        }
        net.minecraft.core.component.DataComponentPatch sent = clientProto.getComponentsPatch();
        ItemStack best = null;
        int bestSize = -1;
        for (ItemKey key : pool.keySet()) {
            ItemStack p = key.proto();
            if (!p.is(clientProto.getItem())) {
                continue;
            }
            net.minecraft.core.component.DataComponentPatch held = p.getComponentsPatch();
            boolean agrees = true;
            for (Map.Entry<net.minecraft.core.component.DataComponentType<?>, java.util.Optional<?>> e : held.entrySet()) {
                if (!java.util.Objects.equals(sent.get(e.getKey()), e.getValue())) {
                    agrees = false;
                    break;
                }
            }
            if (agrees && held.size() > bestSize) {
                best = p;
                bestSize = held.size();
            }
        }
        return best == null ? clientProto : best.copyWithCount(clientProto.getCount());
    }

    private void adjustCount(ItemStack proto, long delta) {
        if (counts == null || delta == 0) {
            return;
        }
        ItemKey key = ItemKey.of(proto);
        long now = counts.getOrDefault(key, 0L) + delta;
        if (now > 0) {
            counts.put(key, now);
        } else {
            counts.remove(key);
        }
    }

    private Map<ItemKey, Long> walkContents() {
        Map<ItemKey, Long> totals = new LinkedHashMap<>();
        for (BoundInventory b : storage) {
            if (!b.access().canExtract() || !b.alive()) {
                continue; // tithed away (out of sight), or broken since the scan
            }
            IItemHandler h = b.poolHandler();
            boolean endless = b.bottomless();
            for (int slot = 0, n = h.getSlots(); slot < n; slot++) {
                ItemStack stack = h.getStackInSlot(slot);
                if (!stack.isEmpty()) {
                    // a creative source shows one stack but never runs out: count it as plenty
                    long count = endless ? Math.max(stack.getCount(), com.tarbound.block.BottomlessSeepBlockEntity.ITEMS) : stack.getCount();
                    totals.merge(ItemKey.of(stack), count, Long::sum);
                }
            }
        }
        return totals;
    }

    /**
     * Pulls up to {@code amount} items matching {@code proto} out of the network.
     *
     * @return the extracted stack (possibly empty); never larger than {@code amount}
     */
    public ItemStack extract(ItemStack proto, int amount) {
        if (proto.isEmpty() || amount <= 0) {
            return ItemStack.EMPTY;
        }
        int remaining = amount;
        // Take from the lowest priority first, so high-priority storage keeps its stock.
        for (BoundInventory b : extractOrder) {
            if (!b.access().canExtract() || !b.alive()) {
                continue;
            }
            IItemHandler h = b.poolHandler();
            for (int slot = 0, n = h.getSlots(); slot < n && remaining > 0; slot++) {
                ItemStack inSlot = h.getStackInSlot(slot);
                if (inSlot.isEmpty() || !ItemStack.isSameItemSameComponents(inSlot, proto)) {
                    continue;
                }
                // Some slots give at most a stack per pull (drawers, Create's creative crate): pull again while they give.
                for (int pulls = 0; remaining > 0 && pulls < 64; pulls++) {
                    ItemStack got = h.extractItem(slot, remaining, false);
                    if (got.isEmpty()) {
                        break;
                    }
                    remaining -= got.getCount();
                }
            }
            if (remaining <= 0) {
                break;
            }
        }
        int taken = amount - remaining;
        adjustCount(proto, -taken);
        // Short of a filled bucket (or other filled container)? Fill empties from the pool's fluid.
        if (remaining > 0) {
            Filled info = filledInfo(proto);
            while (info != null && remaining > 0 && !makeFilled(proto, info).isEmpty()) {
                remaining--;
                taken++;
            }
        }
        return taken > 0 ? proto.copyWithCount(taken) : ItemStack.EMPTY;
    }

    /**
     * Pushes {@code stack} into the network: first topping up existing matching stacks, then empty slots.
     *
     * @return whatever didn't fit
     */
    public ItemStack insert(ItemStack stack) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack proto = stack.copyWithCount(1);
        ItemStack rest = stack.copy();
        long seen = 0; // how many went somewhere the radio can see, for the running counts
        // Highest tier first (see insertTier). Within a tier: pass 1 merges into slots already holding this
        // item, pass 2 fills empty slots; only then does the next tier down get anything.
        int tierStart = 0;
        while (tierStart < storage.size() && !rest.isEmpty()) {
            int tier = insertTier(storage.get(tierStart));
            int tierEnd = tierStart;
            while (tierEnd < storage.size() && insertTier(storage.get(tierEnd)) == tier) {
                tierEnd++;
            }
            for (int pass = 0; pass < 2 && !rest.isEmpty(); pass++) {
                for (int i = tierStart; i < tierEnd && !rest.isEmpty(); i++) {
                    BoundInventory b = storage.get(i);
                    if (!b.access().canInsert() || !b.alive()) {
                        continue; // scried (the radio only takes from it), or broken since the scan
                    }
                    int before = rest.getCount();
                    rest = insertInto(b.poolHandler(), rest, pass == 0);
                    if (b.access().canExtract()) {
                        seen += before - rest.getCount();
                    }
                }
            }
            tierStart = tierEnd;
        }
        adjustCount(proto, seen);
        return rest;
    }

    /** How many of {@code stack} the pool would take right now (simulated, nothing moves). */
    public int fitCount(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0;
        }
        ItemStack rest = stack.copy();
        for (int pass = 0; pass < 2 && !rest.isEmpty(); pass++) {
            for (int i = 0; i < storage.size() && !rest.isEmpty(); i++) {
                BoundInventory b = storage.get(i);
                if (!b.access().canInsert() || !b.alive()) {
                    continue;
                }
                IItemHandler h = b.poolHandler();
                for (int slot = 0, n = h.getSlots(); slot < n && !rest.isEmpty(); slot++) {
                    ItemStack inSlot = h.getStackInSlot(slot);
                    boolean target = pass == 0 ? !inSlot.isEmpty() && ItemStack.isSameItemSameComponents(inSlot, rest) : inSlot.isEmpty();
                    if (target) {
                        rest = h.insertItem(slot, rest, true);
                    }
                }
            }
        }
        return stack.getCount() - rest.getCount();
    }

    /**
     * Whether every one of {@code stacks} would fit in the pool right now (simulated, nothing moves).
     * Each slot is only offered to one of the stacks, so two stacks can't both "fit" into the same
     * empty slot; that makes the check a little cautious, never optimistic.
     */
    public boolean canFitAll(List<ItemStack> stacks) {
        java.util.IdentityHashMap<IItemHandler, java.util.BitSet> used = new java.util.IdentityHashMap<>();
        for (ItemStack stack : stacks) {
            if (stack.isEmpty()) {
                continue;
            }
            ItemStack rest = stack.copy();
            for (int pass = 0; pass < 2 && !rest.isEmpty(); pass++) {
                for (int i = 0; i < storage.size() && !rest.isEmpty(); i++) {
                    BoundInventory b = storage.get(i);
                    if (!b.access().canInsert() || !b.alive()) {
                        continue;
                    }
                    IItemHandler h = b.poolHandler();
                    java.util.BitSet taken = used.computeIfAbsent(h, k -> new java.util.BitSet());
                    for (int slot = 0, n = h.getSlots(); slot < n && !rest.isEmpty(); slot++) {
                        if (taken.get(slot)) {
                            continue;
                        }
                        ItemStack inSlot = h.getStackInSlot(slot);
                        boolean target = pass == 0
                                ? !inSlot.isEmpty() && ItemStack.isSameItemSameComponents(inSlot, rest)
                                : inSlot.isEmpty();
                        if (!target) {
                            continue;
                        }
                        ItemStack after = h.insertItem(slot, rest, true);
                        if (after.getCount() < rest.getCount()) {
                            taken.set(slot);
                            rest = after;
                        }
                    }
                }
            }
            if (!rest.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------------------------------------
    // Fluids
    // ------------------------------------------------------------------------------------------

    /** A pool tank sits at {@code pos}. */
    public boolean tanksAt(BlockPos pos) {
        for (FluidStore t : tanks) {
            if (t.pos().equals(pos)) {
                return true;
            }
        }
        return false;
    }

    /** Fluid tanks in the pool. */
    public List<FluidStore> tanks() {
        return tanks;
    }

    @Nullable
    private Map<FluidKey, Long> fluidCounts;

    /** The radio whose battery is this pool's Essence of the Void beyond its tanks; null for {@link #EMPTY}. */
    @Nullable
    private com.tarbound.block.ScryingFontBlockEntity bank;

    // ------------------------------------------------------------------------------------------
    // Weeping Seeps (0.56): raw filler for orders, paid in Spirit
    // ------------------------------------------------------------------------------------------

    /** How plentiful a Weeping Seep's wares look to plans and loads (the real limits are Spirit and the pass budget). */
    public static final long SEEP_PLENTY = 1_000_000L;
    /** Weeping Seeps touching this radio's tar or antennas. */
    private int weepingSeeps;
    /** Items some contract on this radio's machines makes: a seep never hands those out (they get crafted). */
    private Set<net.minecraft.world.item.Item> craftable = Set.of();
    /** Items the seeps may still make this pass. */

    public boolean hasWeepingSeep() {
        return weepingSeeps > 0;
    }

    /** How many Weeping Seeps feed this radio's jobs. */
    public int weepingSeeps() {
        return weepingSeeps;
    }

    /** Whether a Weeping Seep here would make {@code proto} for a job: it's on the seep's list and no contract here makes it. */
    public boolean seepMakes(ItemStack proto) {
        return weepingSeeps > 0 && seepCost(proto) > 0 && !craftable.contains(proto.getItem());
    }

    /** Spirit a Weeping Seep asks for one of {@code stack}, or 0 if it won't make it (not listed, or not a plain item). */
    public static int seepCost(ItemStack stack) {
        if (stack.isEmpty() || !stack.getComponentsPatch().isEmpty()) {
            return 0;
        }
        Integer v = stack.getItemHolder().getData(com.tarbound.spirit.Spirit.SEEP_YIELDS);
        return v == null ? 0 : v;
    }

    /** Adds a seep's plenty to {@code stock} for each of {@code wanted} it would make (not craftable here). */
    public static void addSeepSupply(Map<ItemKey, Long> stock, Iterable<ItemStack> wanted, Set<net.minecraft.world.item.Item> craftable) {
        Set<ItemKey> done = new HashSet<>();
        for (ItemStack w : wanted) {
            if (seepCost(w) > 0 && !craftable.contains(w.getItem())) {
                ItemKey key = ItemKey.of(w);
                if (done.add(key)) {
                    stock.merge(key, SEEP_PLENTY, Long::sum);
                }
            }
        }
    }

    /** Everything on these machines' Reliquaries' contracts makes, as items. */
    public static Set<net.minecraft.world.item.Item> madeBy(List<com.tarbound.contract.ContractData> contracts) {
        Set<net.minecraft.world.item.Item> made = new HashSet<>();
        for (com.tarbound.contract.ContractData c : contracts) {
            c.outputs().forEach(o -> made.add(o.getItem()));
        }
        return made;
    }

    /** What jobs and orders can count on: {@link #countWithFillsFor}, plus what a Weeping Seep would make. */
    public Map<ItemKey, Long> countForJobs(List<List<ItemStack>> inputs) {
        return countForJobs(flatten(inputs));
    }

    public Map<ItemKey, Long> countForJobs(Iterable<ItemStack> wanted) {
        Map<ItemKey, Long> stock = countWithFills(wanted);
        if (weepingSeeps > 0) {
            addSeepSupply(stock, wanted, craftable);
        }
        return stock;
    }

    /**
     * {@link #extract} for a job or order: whatever the pool is short of, a Weeping Seep makes, if it's on the seep's
     * list, nothing here crafts it, the radio's seep reserve covers it and the radio can pay the Spirit.
     * <p>All or nothing (0.62): the seep makes the whole shortfall or none of it. Machine loads are all or nothing too,
     * so a part-made batch would only be handed back into storage, already paid for, and tried again next pass.
     */
    public ItemStack extractForJob(ItemStack proto, int amount) {
        ItemStack got = extract(proto, amount);
        int missing = amount - got.getCount();
        if (missing <= 0 || bank == null || !seepMakes(proto) || missing > bank.seepReserve()) {
            return got;
        }
        if (!bank.pay(missing * seepCost(proto))) {
            return got;
        }
        bank.useSeepReserve(missing);
        return proto.copyWithCount(got.getCount() + missing);
    }

    private static boolean isEssence(FluidStack f) {
        return !f.isEmpty() && f.getFluid().isSame(com.tarbound.registry.ModFluids.ESSENCE.get());
    }

    /**
     * The pool's energy: the radio's battery plus Essence of the Void in its tanks (1 FE = 1 mB).
     * Energy comes in to the battery first, then the tanks; it goes out of the tanks first, then the battery.
     */
    public net.neoforged.neoforge.energy.IEnergyStorage energy() {
        return new net.neoforged.neoforge.energy.IEnergyStorage() {
            @Override
            public int receiveEnergy(int toReceive, boolean simulate) {
                return toReceive <= 0 ? 0 : insertFluid(com.tarbound.registry.ModFluids.essence(toReceive), simulate);
            }

            @Override
            public int extractEnergy(int toExtract, boolean simulate) {
                return drainFluid(com.tarbound.registry.ModFluids.essence(1), toExtract, simulate);
            }

            @Override
            public int getEnergyStored() {
                return (int) Math.min(Integer.MAX_VALUE, fluidAmount(com.tarbound.registry.ModFluids.essence(1)));
            }

            @Override
            public int getMaxEnergyStored() {
                long room = insertFluid(com.tarbound.registry.ModFluids.essence(Integer.MAX_VALUE / 2), true);
                return (int) Math.min(Integer.MAX_VALUE, getEnergyStored() + room);
            }

            @Override
            public boolean canExtract() {
                return true;
            }

            @Override
            public boolean canReceive() {
                return true;
            }
        };
    }

    /** Totals of every fluid the radio can see, in mB (Commune and Scry tanks). A copy the caller may change. */
    public Map<FluidKey, Long> countFluids() {
        return new LinkedHashMap<>(fluidCountsMap());
    }

    private Map<FluidKey, Long> fluidCountsMap() {
        if (fluidCounts == null) {
            Map<FluidKey, Long> totals = new LinkedHashMap<>();
            for (FluidStore t : tanks) {
                if (!t.access().canExtract() || !t.alive()) {
                    continue;
                }
                for (int i = 0, n = t.handler().getTanks(); i < n; i++) {
                    FluidStack f = t.handler().getFluidInTank(i);
                    if (!f.isEmpty()) {
                        totals.merge(FluidKey.of(f), (long) f.getAmount(), Long::sum);
                    }
                }
            }
            if (bank != null && bank.energy() > 0) {
                totals.merge(FluidKey.of(com.tarbound.registry.ModFluids.essence(1)), (long) bank.energy(), Long::sum);
            }
            fluidCounts = totals;
        }
        return fluidCounts;
    }

    /** mB of this fluid the radio can see. */
    public long fluidAmount(FluidStack fluid) {
        return fluid.isEmpty() ? 0 : fluidCountsMap().getOrDefault(FluidKey.of(fluid), 0L);
    }

    private void adjustFluid(FluidStack proto, long delta) {
        if (fluidCounts == null || delta == 0) {
            return;
        }
        FluidKey key = FluidKey.of(proto);
        long now = fluidCounts.getOrDefault(key, 0L) + delta;
        if (now > 0) {
            fluidCounts.put(key, now);
        } else {
            fluidCounts.remove(key);
        }
    }

    /**
     * Pours {@code stack} into the pool's tanks: highest tier first; within a tier, tanks already
     * holding this fluid before empty ones. @return mB that went in
     */
    public int insertFluid(FluidStack stack, boolean simulate) {
        if (stack.isEmpty()) {
            return 0;
        }
        IFluidHandler.FluidAction action = simulate ? IFluidHandler.FluidAction.SIMULATE : IFluidHandler.FluidAction.EXECUTE;
        int left = stack.getAmount();
        long seen = 0;
        if (bank != null && isEssence(stack)) {
            int in = bank.receiveBattery(left, simulate); // energy fills the radio's battery before any tank
            left -= in;
            seen += in;
        }
        for (int pass = 0; pass < 2 && left > 0; pass++) {
            for (FluidStore t : tanks) {
                if (left <= 0) {
                    break;
                }
                if (!t.access().canInsert() || !t.alive() || holds(t.handler(), stack) != (pass == 0)) {
                    continue;
                }
                int in = t.handler().fill(stack.copyWithAmount(left), action);
                left -= in;
                if (t.access().canExtract()) {
                    seen += in;
                }
            }
        }
        if (!simulate) {
            adjustFluid(stack, seen);
        }
        return stack.getAmount() - left;
    }

    private static boolean holds(IFluidHandler h, FluidStack fluid) {
        for (int i = 0, n = h.getTanks(); i < n; i++) {
            if (FluidStack.isSameFluidSameComponents(h.getFluidInTank(i), fluid)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Takes up to {@code amount} mB of {@code fluid} from the pool, lowest priority first (Essence of the
     * Void: the tanks, then the radio's battery). @return mB taken
     */
    public int drainFluid(FluidStack fluid, int amount, boolean simulate) {
        int taken = drainTanks(fluid, amount, simulate);
        if (taken < amount && bank != null && isEssence(fluid)) {
            int more = bank.extractBattery(amount - taken, simulate);
            if (!simulate) {
                adjustFluid(fluid, -more);
            }
            taken += more;
        }
        return taken;
    }

    /** Moves Essence of the Void from the tanks into the radio's battery, as far as it has room. @return FE moved */
    public int refillBank() {
        if (bank == null || tanks.isEmpty()) {
            return 0;
        }
        int room = com.tarbound.block.ScryingFontBlockEntity.batteryCapacity() - bank.energy();
        if (room <= 0) {
            return 0;
        }
        FluidStack essence = com.tarbound.registry.ModFluids.essence(1);
        int got = drainTanks(essence, room, false);
        int in = bank.receiveBattery(got, false);
        adjustFluid(essence, in); // it's still in the pool, just in the battery now
        if (in < got) {
            insertFluid(essence.copyWithAmount(got - in), false);
        }
        return in;
    }

    /** As {@link #drainFluid}, from the tanks only (never the battery). */
    public int drainTanks(FluidStack fluid, int amount, boolean simulate) {
        if (fluid.isEmpty() || amount <= 0) {
            return 0;
        }
        IFluidHandler.FluidAction action = simulate ? IFluidHandler.FluidAction.SIMULATE : IFluidHandler.FluidAction.EXECUTE;
        int left = amount;
        for (FluidStore t : tankDrainOrder) {
            if (left <= 0) {
                break;
            }
            if (!t.access().canExtract() || !t.alive()) {
                continue;
            }
            int before = left;
            left -= t.handler().drain(fluid.copyWithAmount(left), action).getAmount();
            if (!simulate && com.tarbound.TarboundDebug.on() && bank != null && bank.getLevel() != null) {
                com.tarbound.TarboundDebug.log(bank.getLevel(), t.pos(), "pool tank drain", "asked " + com.tarbound.TarboundDebug.fluid(fluid.copyWithAmount(before))
                        + ", got " + (before - left) + "mB; tank " + com.tarbound.TarboundDebug.tanks(t.handler(), FluidStack.EMPTY) + " access=" + t.access());
            }
        }
        int taken = amount - left;
        if (!simulate) {
            adjustFluid(fluid, -taken);
        }
        return taken;
    }

    /** The pool seen as one tank of {@code fluid}: for filling buckets and containers from it. */
    public IFluidHandler source(FluidStack fluid) {
        FluidStack proto = fluid.copyWithAmount(1);
        return new IFluidHandler() {
            @Override
            public int getTanks() {
                return 1;
            }

            @Override
            public FluidStack getFluidInTank(int tank) {
                return proto.copyWithAmount((int) Math.min(Integer.MAX_VALUE, fluidAmount(proto)));
            }

            @Override
            public int getTankCapacity(int tank) {
                return Integer.MAX_VALUE;
            }

            @Override
            public boolean isFluidValid(int tank, FluidStack stack) {
                return FluidStack.isSameFluidSameComponents(stack, proto);
            }

            @Override
            public int fill(FluidStack resource, FluidAction action) {
                return 0;
            }

            @Override
            public FluidStack drain(FluidStack resource, FluidAction action) {
                if (!FluidStack.isSameFluidSameComponents(resource, proto)) {
                    return FluidStack.EMPTY;
                }
                return drain(resource.getAmount(), action);
            }

            @Override
            public FluidStack drain(int maxDrain, FluidAction action) {
                int got = drainFluid(proto, maxDrain, action.simulate());
                return got <= 0 ? FluidStack.EMPTY : proto.copyWithAmount(got);
            }
        };
    }

    /** The pool seen as one bottomless tank that takes any fluid: for emptying buckets into it. */
    public IFluidHandler sink() {
        return new IFluidHandler() {
            @Override
            public int getTanks() {
                return 1;
            }

            @Override
            public FluidStack getFluidInTank(int tank) {
                return FluidStack.EMPTY;
            }

            @Override
            public int getTankCapacity(int tank) {
                return Integer.MAX_VALUE;
            }

            @Override
            public boolean isFluidValid(int tank, FluidStack stack) {
                return true;
            }

            @Override
            public int fill(FluidStack resource, FluidAction action) {
                return insertFluid(resource, action.simulate());
            }

            @Override
            public FluidStack drain(FluidStack resource, FluidAction action) {
                return FluidStack.EMPTY;
            }

            @Override
            public FluidStack drain(int maxDrain, FluidAction action) {
                return FluidStack.EMPTY;
            }
        };
    }

    /** A filled container's empty form and the fluid in it (a water bucket: bucket and 1000 mB of water), or null. */
    public record Filled(ItemStack empty, FluidStack fluid) {}

    /**
     * What each item type would be emptied into, worked out once: asking an item for its fluid handler
     * every time was a big share of the cost of checking recipes (the radio's battery means nearly every
     * pool has a fluid, so this runs for every ingredient). Bounded; cleared if it ever gets big.
     */
    private static final Map<ItemKey, java.util.Optional<Filled>> FILLED = new java.util.concurrent.ConcurrentHashMap<>();

    @Nullable
    public static Filled filledInfo(ItemStack proto) {
        if (proto.isEmpty()) {
            return null;
        }
        ItemKey key = ItemKey.of(proto);
        java.util.Optional<Filled> known = FILLED.get(key);
        if (known == null) {
            if (FILLED.size() > 20_000) {
                FILLED.clear();
            }
            known = java.util.Optional.ofNullable(computeFilledInfo(proto));
            FILLED.put(key, known);
        }
        return known.orElse(null);
    }

    @Nullable
    private static Filled computeFilledInfo(ItemStack proto) {
        FluidStack fluid = net.neoforged.neoforge.fluids.FluidUtil.getFluidContained(proto).orElse(FluidStack.EMPTY);
        if (fluid.isEmpty()) {
            return null;
        }
        ItemStack empty = proto.getCraftingRemainingItem();
        if (empty.isEmpty()) {
            empty = net.neoforged.neoforge.fluids.FluidUtil.getFluidHandler(proto.copyWithCount(1)).map(h -> {
                h.drain(Integer.MAX_VALUE, IFluidHandler.FluidAction.EXECUTE);
                return h.getContainer();
            }).orElse(ItemStack.EMPTY);
        }
        return empty.isEmpty() || ItemStack.isSameItemSameComponents(empty, proto) ? null : new Filled(empty.copyWithCount(1), fluid);
    }

    /**
     * How many more of {@code proto} (a filled bucket or similar) the pool could make right now from its
     * empty containers and fluid. 0 for anything that isn't a fluid container.
     */
    public long fillable(ItemStack proto) {
        if (counts == null) {
            counts = walkContents();
        }
        return fillable(proto, counts, fluidCountsMap());
    }

    /** As {@link #fillable(ItemStack)}, from given counts (the client uses its copy of the pool). */
    public static long fillable(ItemStack proto, Map<ItemKey, Long> items, Map<FluidKey, Long> fluids) {
        Filled info = filledInfo(proto);
        if (info == null) {
            return 0;
        }
        long empties = items.getOrDefault(ItemKey.of(info.empty()), 0L);
        return Math.min(empties, fluids.getOrDefault(FluidKey.of(info.fluid()), 0L) / Math.max(1, info.fluid().getAmount()));
    }

    /** Pool contents plus filled containers that could be made on demand, for the given items. */
    public Map<ItemKey, Long> countWithFills(Iterable<ItemStack> wanted) {
        return withFills(countContents(), fluidCountsMap(), wanted);
    }

    /** As {@link #countWithFills}, for every alternative of every input listed. */
    public Map<ItemKey, Long> countWithFillsFor(List<List<ItemStack>> inputs) {
        return countWithFills(flatten(inputs));
    }

    /** {@code stock} (changed in place and returned) plus the filled containers of {@code wanted} the pool could make. */
    public static Map<ItemKey, Long> withFills(Map<ItemKey, Long> stock, Map<FluidKey, Long> fluids, Iterable<ItemStack> wanted) {
        if (fluids.isEmpty()) {
            return stock;
        }
        Map<ItemKey, Long> base = new HashMap<>(stock);
        Set<ItemKey> done = new HashSet<>();
        for (ItemStack w : wanted) {
            ItemKey key = ItemKey.of(w);
            if (!done.add(key)) {
                continue;
            }
            long extra = fillable(w, base, fluids);
            if (extra > 0) {
                stock.merge(key, extra, Long::sum);
            }
        }
        return stock;
    }

    public static List<ItemStack> flatten(List<List<ItemStack>> inputs) {
        List<ItemStack> all = new ArrayList<>();
        for (List<ItemStack> alts : inputs) {
            all.addAll(alts);
        }
        return all;
    }

    /** Fills one empty container from the pool to make {@code proto}. @return the filled item, or EMPTY (nothing changed) */
    private ItemStack makeFilled(ItemStack proto, Filled info) {
        if (fluidAmount(info.fluid()) < info.fluid().getAmount()) {
            return ItemStack.EMPTY;
        }
        ItemStack empty = extract(info.empty(), 1);
        if (empty.isEmpty()) {
            return ItemStack.EMPTY;
        }
        var result = net.neoforged.neoforge.fluids.FluidUtil.tryFillContainer(empty, source(info.fluid()), info.fluid().getAmount(), null, false);
        if (!result.isSuccess() || !ItemStack.isSameItemSameComponents(result.getResult(), proto)) {
            insert(empty);
            return ItemStack.EMPTY;
        }
        result = net.neoforged.neoforge.fluids.FluidUtil.tryFillContainer(empty, source(info.fluid()), info.fluid().getAmount(), null, true);
        return result.isSuccess() ? result.getResult() : ItemStack.EMPTY;
    }

    private static ItemStack insertInto(IItemHandler h, ItemStack rest, boolean mergeOnly) {
        for (int slot = 0, n = h.getSlots(); slot < n && !rest.isEmpty(); slot++) {
            ItemStack inSlot = h.getStackInSlot(slot);
            boolean target = mergeOnly
                    ? !inSlot.isEmpty() && ItemStack.isSameItemSameComponents(inSlot, rest)
                    : inSlot.isEmpty();
            if (target) {
                rest = h.insertItem(slot, rest, false);
            }
        }
        return rest;
    }
}
