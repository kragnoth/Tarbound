package com.tarbound.block;

import com.tarbound.network.NetworkChanges;

import com.tarbound.contract.ContractData;
import com.tarbound.contract.ContractJob;
import com.tarbound.contract.CraftingContracts;
import com.tarbound.config.TarboundConfig;
import java.util.HashMap;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import com.tarbound.contract.StandingOrder;
import com.tarbound.network.BoundInventory;
import com.tarbound.network.ItemKey;
import com.tarbound.network.MachineLoader;
import com.tarbound.network.RadioRegistry;
import com.tarbound.network.TarNetwork;
import com.tarbound.registry.ModBlockEntities;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.network.chat.Component;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * The radio's block entity. It
 * <ul>
 *   <li>drains finished products out of every bound machine into the pool, once a second;</li>
 *   <li>runs single-step contract jobs: pushes operations into bound machines holding the contract
 *       and counts the primary output as it comes back;</li>
 *   <li>keeps standing contracts going: feeds every pool item in a standing contract's tag into the
 *       bound machine holding it, a few operations at a time, after requested jobs have had their pick.</li>
 * </ul>
 * Both run whether or not anyone has the radio open; jobs are saved with the block.
 */
public class ScryingFontBlockEntity extends BlockEntity {
    private static int idleInterval() {
        return TarboundConfig.SERVER.idleInterval.get();
    }

    private static int jobInterval() {
        return TarboundConfig.SERVER.jobInterval.get();
    }
    /** Operations a job may push per pass, across all its machines. */
    private static int opsPerPass() {
        return TarboundConfig.SERVER.opsPerPass.get();
    }
    public static int maxJobs() {
        return TarboundConfig.SERVER.maxJobs.get();
    }

    /** Lit Votive Racks counting for this radio, as of its last pass (see {@link VotiveRackBlock#racksFor}). Not saved. */
    private List<BlockPos> racks = List.of();

    /** Weeping Seep items this radio may still have made (not saved): refilled each pass, see {@link #refillSeeps}. */
    private int seepReserve;

    public int seepReserve() {
        return seepReserve;
    }

    public void useSeepReserve(int n) {
        seepReserve = Math.max(0, seepReserve - n);
    }

    /**
     * Each pass the seeps add {@code weepingSeepPerPass} each to the reserve, which banks up to four passes' worth (and
     * always at least a stack), so one machine load bigger than a pass's worth (a 64-item batch) still goes through.
     */
    private void refillSeeps(TarNetwork net) {
        int per = net.weepingSeeps() * com.tarbound.config.TarboundConfig.SERVER.weepingSeepPerPass.get();
        seepReserve = per <= 0 ? 0 : Math.min(Math.max(per * 4, 64), seepReserve + per);
    }

    public int racks() {
        return racks.size();
    }

    /** Single jobs plus orders this radio may run at once: the config's, plus each lit rack's share. */
    public int jobLimit() {
        return maxJobs() + racks.size() * TarboundConfig.SERVER.rackJobs.get();
    }

    /** How deep this radio's orders may plan (the config's, deeper with racks; never past 32). */
    public int planDepth() {
        return Math.min(32, TarboundConfig.SERVER.maxPlanDepth.get() + racks.size() * TarboundConfig.SERVER.rackPlanDepth.get());
    }

    /** How many plan lines this radio's orders may have (the config's, more with racks). */
    public int planNodes() {
        return TarboundConfig.SERVER.maxPlanNodes.get() + racks.size() * TarboundConfig.SERVER.rackPlanNodes.get();
    }

    /** Looks for this radio's racks; while there's work, they burn Spirit, and a radio that can't pay snuffs them. */
    private void tendRacks(boolean working) {
        racks = VotiveRackBlock.racksFor(level, worldPosition);
        if (!working || racks.isEmpty()) {
            return;
        }
        int cost = racks.size() * TarboundConfig.SERVER.rackSpiritCost.get();
        if (!pay(cost)) {
            VotiveRackBlock.snuff(level, racks);
            racks = List.of();
        }
    }

    public static int maxOps() {
        return TarboundConfig.SERVER.maxOpsPerJob.get();
    }
    /** A standing contract stops topping a machine up once it holds this many operations' worth. */
    private static int standingBufferOps() {
        return TarboundConfig.SERVER.standingBufferOps.get();
    }

    private final List<ContractJob> jobs = new ArrayList<>();
    private int nextJobId = 1;
    /** Multi-step orders, each with a Shade holding its stock (see {@link ShadeBlockEntity}). */
    private final List<com.tarbound.contract.CraftOrder> orders = new ArrayList<>();
    private int nextOrderId = 1;
    private int roundRobin;
    /** Some bound machine had a standing contract last pass (so keep the fast interval). */
    private boolean standingActive;
    /** Pool totals of each job's primary output at the end of the last pass (not saved; re-baselined on load). */
    private final Map<ItemKey, Long> lastOutputCounts = new java.util.HashMap<>();
    /** The same for fluid jobs, in mB. */
    private final Map<com.tarbound.network.FluidKey, Long> lastFluidCounts = new java.util.HashMap<>();

    /** This tick's scan, shared by the radio's own pass and every player with its screen open. */
    private TarNetwork cachedNetwork;
    private long cachedNetworkAt = Long.MIN_VALUE;
    /** When {@link #cachedNetwork} was scanned, and the world's change count just before (see {@link NetworkChanges}). */
    private long scannedAt = Long.MIN_VALUE;
    private long scanCount;
    /** A watched binding's capabilities were invalidated (broken, replaced, re-bound, side config, chunk unloaded). */
    private boolean watchFired;
    /** Positions with a capability listener for this radio; a listener whose position drops out stops itself. */
    private final java.util.Set<Long> watched = new java.util.HashSet<>();

    // --- Spirit (see com.tarbound.spirit.Spirit)

    /** Spirit this radio holds. Automation spends it; storage access never does. */
    private int spirit;
    /** Burn offerings kept in the pool when the store runs low (the candle button on the screen). */
    private boolean burnFromPool = true;
    /** Last comparator level, so neighbours are only told when it changes. */
    private int lastSignal = -1;
    /** Client copy of "out of Spirit", for the radio's look (synced only when it flips). */
    private boolean hungry;

    public int spirit() {
        return spirit;
    }

    // --- Battery: FE the radio holds, shown in the pool as Essence of the Void (1 FE = 1 mB)

    private int energy;

    public static int batteryCapacity() {
        return TarboundConfig.SERVER.batteryCapacity.get();
    }

    public int energy() {
        return Math.min(energy, batteryCapacity());
    }

    /** Puts up to {@code amount} FE in the battery. @return how much went (or would go) in */
    public int receiveBattery(int amount, boolean simulate) {
        int in = Math.max(0, Math.min(amount, batteryCapacity() - energy()));
        if (!simulate && in > 0) {
            energy = energy() + in;
            setChanged();
        }
        return in;
    }

    /** Takes up to {@code amount} FE from the battery. @return how much came (or would come) out */
    public int extractBattery(int amount, boolean simulate) {
        int out = Math.max(0, Math.min(amount, energy()));
        if (!simulate && out > 0) {
            energy = energy() - out;
            setChanged();
        }
        return out;
    }

    /** The battery alone, for cables connected straight to the radio. */
    public net.neoforged.neoforge.energy.IEnergyStorage battery() {
        return new net.neoforged.neoforge.energy.IEnergyStorage() {
            @Override
            public int receiveEnergy(int toReceive, boolean simulate) {
                return receiveBattery(toReceive, simulate);
            }

            @Override
            public int extractEnergy(int toExtract, boolean simulate) {
                return extractBattery(toExtract, simulate);
            }

            @Override
            public int getEnergyStored() {
                return energy();
            }

            @Override
            public int getMaxEnergyStored() {
                return batteryCapacity();
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

    /** Tops the battery up from Essence of the Void in the pool's tanks (spare energy). */
    private void refillBattery(TarNetwork net) {
        net.refillBank();
    }

    public boolean burnFromPool() {
        return burnFromPool;
    }

    public void toggleBurnFromPool() {
        burnFromPool = !burnFromPool;
        setChanged();
    }

    /** Out of Spirit while costs are on (client side: as last synced). */
    public boolean hungry() {
        return level != null && level.isClientSide ? hungry : com.tarbound.spirit.Spirit.costs() && spirit <= 0;
    }

    /** Whether {@code cost} Spirit is there to spend (always, with costs off). */
    public boolean canPay(int cost) {
        return cost <= 0 || !com.tarbound.spirit.Spirit.costs() || spirit >= cost;
    }

    /** Spends {@code cost} Spirit if it's there. @return false (and nothing spent) if it isn't */
    public boolean pay(int cost) {
        if (cost <= 0 || !com.tarbound.spirit.Spirit.costs()) {
            return true;
        }
        if (spirit < cost) {
            return false;
        }
        spirit -= cost;
        spiritChanged();
        return true;
    }

    /**
     * Offers items from {@code stack}: as many as fit whole in the store.
     *
     * @return how many of the stack would be (or were) taken
     */
    public int feed(ItemStack stack, boolean simulate) {
        int per = com.tarbound.spirit.Spirit.value(stack);
        if (per <= 0) {
            return 0;
        }
        int room = com.tarbound.spirit.Spirit.capacity() - spirit;
        int n = Math.min(stack.getCount(), Math.max(0, room / per));
        if (n == 0 && spirit == 0) {
            n = Math.min(1, stack.getCount()); // an offering worth more than the whole store still lights an empty one
        }
        if (n > 0 && !simulate) {
            spirit = (int) Math.min(com.tarbound.spirit.Spirit.capacity(), (long) spirit + (long) n * per);
            spiritChanged();
        }
        return n;
    }

    /** Adds Spirit directly (a mob dying in this radio's tar), up to the store's capacity. */
    public void addSpirit(int amount) {
        if (amount > 0) {
            spirit = (int) Math.min(com.tarbound.spirit.Spirit.capacity(), (long) spirit + amount);
            spiritChanged();
        }
    }

    private void spiritChanged() {
        setChanged();
        if (level == null || level.isClientSide) {
            return;
        }
        int signal = comparatorSignal();
        if (signal != lastSignal) {
            lastSignal = signal;
            level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
        }
        boolean nowHungry = hungry();
        if (nowHungry != hungry) {
            hungry = nowHungry;
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    /** Comparator level: 0 when empty, otherwise 1-15 by how full the store is. */
    public int comparatorSignal() {
        int cap = com.tarbound.spirit.Spirit.capacity();
        return spirit <= 0 ? 0 : Math.max(1, Math.min(15, Math.round(15F * spirit / cap)));
    }

    /**
     * With the candle button on, burns offerings from the pool once the store is below half, a few at
     * a time, so a stock of candles in storage keeps the spirits fed. Never overfills.
     */
    private void burnOfferings(TarNetwork net) {
        if (!burnFromPool || !com.tarbound.spirit.Spirit.costs()) {
            return;
        }
        int cap = com.tarbound.spirit.Spirit.capacity();
        if (spirit >= cap / 2) {
            return;
        }
        int burned = 0;
        for (Map.Entry<ItemKey, Long> e : net.countContents().entrySet()) {
            int per = com.tarbound.spirit.Spirit.value(e.getKey().proto());
            if (per <= 0) {
                continue;
            }
            while (burned < 8 && spirit < cap / 2 && (spirit + per <= cap || spirit == 0)) {
                if (net.extract(e.getKey().proto(), 1).isEmpty()) {
                    break;
                }
                spirit = Math.min(cap, spirit + per);
                burned++;
            }
            if (burned >= 8 || spirit >= cap / 2) {
                break;
            }
        }
        if (burned > 0) {
            spiritChanged();
        }
    }

    /** This radio's identity; follows the block when it's broken and placed again (see {@link RadioRegistry}). */
    @javax.annotation.Nullable
    private java.util.UUID radioId;

    public ScryingFontBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SCRYING_FONT.get(), pos, state);
    }

    /** This radio's id, made on first use. */
    public java.util.UUID radioId() {
        if (radioId == null) {
            radioId = java.util.UUID.randomUUID();
            setChanged();
        }
        return radioId;
    }

    private void register() {
        if (level != null && !level.isClientSide && !RadioRegistry.add(level, radioId(), worldPosition)) {
            // Another loaded radio already has this id (a copied block): this one becomes a new radio.
            radioId = java.util.UUID.randomUUID();
            setChanged();
            RadioRegistry.add(level, radioId, worldPosition);
        }
    }

    private void unregister() {
        if (level != null && !level.isClientSide && radioId != null) {
            RadioRegistry.remove(level, radioId, worldPosition);
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        register();
    }

    @Override
    public void setRemoved() {
        unregister();
        super.setRemoved();
    }

    @Override
    public void onChunkUnloaded() {
        unregister();
    }

    // --- item <-> block: the id travels with the radio

    @Override
    protected void applyImplicitComponents(DataComponentInput input) {
        super.applyImplicitComponents(input);
        Integer storedSpirit = input.get(com.tarbound.registry.ModDataComponents.SPIRIT.get());
        if (storedSpirit != null) {
            spirit = Math.max(0, storedSpirit);
        }
        Integer storedEnergy = input.get(com.tarbound.registry.ModDataComponents.ENERGY.get());
        if (storedEnergy != null) {
            energy = Math.max(0, storedEnergy);
        }
        java.util.UUID fromItem = input.get(com.tarbound.registry.ModDataComponents.RADIO_ID.get());
        if (fromItem != null && !fromItem.equals(radioId)) {
            unregister();
            radioId = fromItem;
            register();
        }
    }

    @Override
    protected void collectImplicitComponents(net.minecraft.core.component.DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        components.set(com.tarbound.registry.ModDataComponents.RADIO_ID.get(), radioId());
        if (spirit > 0) {
            components.set(com.tarbound.registry.ModDataComponents.SPIRIT.get(), spirit);
        }
        if (energy > 0) {
            components.set(com.tarbound.registry.ModDataComponents.ENERGY.get(), energy);
        }
    }

    @Override
    public void removeComponentsFromTag(CompoundTag tag) {
        tag.remove("radio_id");
        tag.remove("spirit");
        tag.remove("energy");
    }

    /** Clients only learn whether the radio is out of Spirit (for its look); the rest syncs via the screen. */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("hungry", hungry());
        return tag;
    }

    @Override
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    /**
     * The radio's network. Kept between passes while nothing it could hold has changed: no block placed, broken or
     * changed next to tar, a Reliquary, an antenna or a radio in this world ({@link NetworkChanges}); no bound
     * block's capabilities invalidated (it was broken or replaced, an Entangled block re-bound, side settings
     * changed, its chunk unloaded); every binding still alive. Rescanned anyway every {@code networkRescanSeconds}
     * (0 rescans every pass, as before 0.43). Writes still check each binding is alive, so a missed change costs a
     * scan a few seconds old, never items.
     */
    public TarNetwork network() {
        if (level == null || level.isClientSide) {
            return TarNetwork.EMPTY;
        }
        long now = level.getGameTime();
        if (cachedNetwork != null && cachedNetworkAt == now) {
            return cachedNetwork;
        }
        int every = TarboundConfig.SERVER.networkRescanSeconds.get() * 20;
        boolean keep = cachedNetwork != null && every > 0 && !watchFired
                && NetworkChanges.count(level) == scanCount
                && now >= scannedAt && now - scannedAt < every
                && cachedNetwork.allAlive();
        if (!keep) {
            scanCount = NetworkChanges.count(level); // before the scan: a change during it means another next time
            watchFired = false;
            cachedNetwork = TarNetwork.scan(level, worldPosition);
            scannedAt = now;
            watch(cachedNetwork);
        } else {
            cachedNetwork.freshTick();
        }
        cachedNetworkAt = now;
        return cachedNetwork;
    }

    /** Listens on every bound position, so a change there (not just next to the tar) brings a rescan. */
    private void watch(TarNetwork net) {
        if (!(level instanceof net.minecraft.server.level.ServerLevel server)) {
            return;
        }
        java.util.Set<Long> now = new java.util.HashSet<>();
        for (BoundInventory b : net.bound()) {
            now.add(b.pos().asLong());
        }
        for (com.tarbound.network.FluidStore t : net.tanks()) {
            now.add(t.pos().asLong());
        }
        watched.retainAll(now);
        for (long p : now) {
            if (watched.add(p)) {
                server.registerCapabilityListener(BlockPos.of(p), () -> {
                    if (isRemoved() || !watched.contains(p)) {
                        return false; // radio gone, or no longer bound there: stop listening
                    }
                    watchFired = true;
                    return true;
                });
            }
        }
    }

    /** Forces the next {@link #network()} call to rescan. */
    public void invalidateNetwork() {
        cachedNetwork = null;
    }

    public List<ContractJob> jobs() {
        return Collections.unmodifiableList(jobs);
    }

    /** The order with this id, or null. */
    @javax.annotation.Nullable
    public com.tarbound.contract.CraftOrder order(int id) {
        for (com.tarbound.contract.CraftOrder o : orders) {
            if (o.id() == id) {
                return o;
            }
        }
        return null;
    }

    public ContractJob addJob(ContractData contract, int ops) {
        ContractJob job = new ContractJob(nextJobId++, contract, ops);
        jobs.add(job);
        setChanged();
        return job;
    }

    /** Cancels a job; a job that's part of an order cancels the whole order. */
    public void cancelJob(int id) {
        for (ContractJob j : jobs) {
            if (j.id() == id && j.orderId() != 0) {
                cancelOrder(j.orderId(), null);
                return;
            }
        }
        if (jobs.removeIf(j -> j.id() == id)) {
            setChanged();
        }
    }

    public void cancelAllJobs() {
        for (com.tarbound.contract.CraftOrder o : List.copyOf(orders)) {
            cancelOrder(o.id(), null);
        }
        if (!jobs.isEmpty()) {
            jobs.clear();
            lastOutputCounts.clear();
            lastFluidCounts.clear();
            setChanged();
        }
    }

    // --- Orders: multi-step crafts watched over by a Shade

    /** Whether this radio still has order {@code id} running. */
    /**
     * Job slots in use: single jobs, plus one per order however many sub-crafts it runs (they're the order's
     * business, the way AE2 counts crafting CPUs, not patterns). Checked against {@link #maxJobs()}.
     */
    public int workInUse() {
        int n = orders.size();
        for (ContractJob j : jobs) {
            if (j.orderId() == 0) {
                n++;
            }
        }
        return n;
    }

    public boolean hasOrder(int id) {
        return order(id) != null;
    }

    /** The radio is going away: every order ends, and its Shade gives back (or drops) what it held. */
    public void releaseAllShades() {
        TarNetwork net = network();
        for (com.tarbound.contract.CraftOrder o : List.copyOf(orders)) {
            com.tarbound.contract.OrderVessel shade = shadeFor(o);
            if (shade != null) {
                shade.release(net.isEmpty() ? null : net);
            }
        }
        orders.clear();
        jobs.removeIf(j -> j.orderId() != 0);
        setChanged();
    }

    /** Every contract on this radio's Reliquaries, once each, for the planner (busy machines marked). */
    public List<com.tarbound.contract.CraftPlanner.Source> planSources(TarNetwork net) {
        java.util.Set<ContractData> running = new java.util.HashSet<>();
        for (ContractJob job : jobs) {
            if (!job.allPushed()) {
                running.add(job.contract());
            }
        }
        List<com.tarbound.contract.CraftPlanner.MachineContracts> machines = new ArrayList<>();
        for (BoundInventory m : net.machines()) {
            ReliquaryBlockEntity r = ReliquaryBlock.reliquaryFor(level, m.pos());
            if (r == null) {
                continue;
            }
            List<ContractData> held = r.contractSlots().stream().filter(java.util.Objects::nonNull).toList();
            machines.add(new com.tarbound.contract.CraftPlanner.MachineContracts(Component.empty(), held,
                    held.stream().anyMatch(running::contains)));
        }
        return com.tarbound.contract.CraftPlanner.gather(machines);
    }

    public List<com.tarbound.contract.CraftOrder> orders() {
        return Collections.unmodifiableList(orders);
    }

    /**
     * Starts a multi-step order: sets aside in {@code shade} everything the plan takes from storage, then
     * queues its steps (sub-crafts deliver to the Shade; the last step, the thing asked for, goes to the pool).
     *
     * @return false (and nothing taken) if storage no longer has what the plan counted on
     */
    public boolean startOrder(@javax.annotation.Nullable java.util.UUID requester, com.tarbound.contract.OrderVessel shade,
            com.tarbound.contract.CraftPlanner.Plan plan, TarNetwork net) {
        int id = nextOrderId++;
        shade.claim(id);
        boolean shortOf = false;
        for (Map.Entry<ItemKey, Long> e : plan.claimItems().entrySet()) {
            ItemStack proto = e.getKey().proto();
            long need = e.getValue();
            while (need > 0) {
                ItemStack got = net.extract(proto, (int) Math.min(need, Math.max(1, proto.getMaxStackSize())));
                if (got.isEmpty()) {
                    break;
                }
                shade.hold(got);
                need -= got.getCount();
            }
            // What a Weeping Seep makes isn't in storage to set aside: the plan counted on the seep, and the order's
            // jobs ask it for the rest as they load (Shade first, then the pool and its seeps).
            shortOf |= need > 0 && !net.seepMakes(proto);
        }
        for (Map.Entry<com.tarbound.network.FluidKey, Long> e : plan.claimFluids().entrySet()) {
            int want = (int) Math.min(Integer.MAX_VALUE, e.getValue());
            int got = net.drainFluid(e.getKey().proto(), want, false);
            shade.holdFluid(e.getKey().proto().copyWithAmount(got));
            shortOf |= got < want;
        }
        if (shortOf) {
            shade.release(net); // everything back where it came from
            return false;
        }
        List<com.tarbound.contract.CraftPlanner.Step> steps = plan.steps();
        for (int i = 0; i < steps.size(); i++) {
            addJob(steps.get(i).contract(), Math.max(1, Math.min(steps.get(i).ops(), maxOps()))).forOrder(id, i < steps.size() - 1);
        }
        orders.add(new com.tarbound.contract.CraftOrder(id, shade.vesselPos(), requester,
                plan.root().isFluid() ? ItemStack.EMPTY : plan.root().item.copyWithCount(1),
                plan.root().isFluid() ? plan.root().fluid.copyWithAmount(1) : FluidStack.EMPTY, plan.root().needed, shade.vesselEntity()));
        setChanged();
        return true;
    }

    /**
     * Cancels an order and all its jobs; the Shade gives back what it held to the pool.
     *
     * @param known the order's Shade, if the caller has it (a Shade being broken)
     */
    public void cancelOrder(int orderId, @javax.annotation.Nullable com.tarbound.contract.OrderVessel known) {
        jobs.removeIf(j -> j.orderId() == orderId);
        com.tarbound.contract.CraftOrder order = order(orderId);
        if (order != null) {
            orders.remove(order);
        }
        com.tarbound.contract.OrderVessel shade = known != null ? known : order != null ? shadeFor(order) : null;
        if (shade != null && shade.orderId() == orderId) {
            shade.release(network());
        }
        setChanged();
    }

    /** The order's Shade, if it's loaded and still holding for this order. */
    @javax.annotation.Nullable
    private com.tarbound.contract.OrderVessel shadeFor(com.tarbound.contract.CraftOrder order) {
        if (level == null || !level.isLoaded(order.shadePos())) {
            return null;
        }
        if (order.vesselEntity() != null) {
            return level instanceof net.minecraft.server.level.ServerLevel sl && sl.getEntity(order.vesselEntity()) instanceof com.tarbound.contract.OrderVessel v
                    && v.orderId() == order.id() ? v : null;
        }
        return level.getBlockEntity(order.shadePos()) instanceof ShadeBlockEntity s && s.orderId() == order.id() ? s : null;
    }

    /**
     * The order's vessel ought to be reachable now: its chunk is loaded, and for a Steward its entities are too (they
     * load after the blocks, so a fresh chunk would otherwise look like the Steward was gone).
     */
    private boolean vesselSettled(com.tarbound.contract.CraftOrder o) {
        if (!level.isLoaded(o.shadePos())) {
            return false;
        }
        return o.vesselEntity() == null || (level instanceof net.minecraft.server.level.ServerLevel sl
                && sl.areEntitiesLoaded(net.minecraft.world.level.ChunkPos.asLong(o.shadePos())));
    }

    @javax.annotation.Nullable
    private com.tarbound.contract.OrderVessel shadeFor(int orderId) {
        com.tarbound.contract.CraftOrder o = order(orderId);
        return o == null ? null : shadeFor(o);
    }

    /** Orders whose Shade is gone (broken while this radio was unloaded) end; their jobs go with them. */
    private void dropOrphanedOrders() {
        for (com.tarbound.contract.CraftOrder o : List.copyOf(orders)) {
            if (vesselSettled(o) && shadeFor(o) == null) {
                jobs.removeIf(j -> j.orderId() == o.id());
                orders.remove(o);
                setChanged();
            }
        }
    }

    /** Orders with no jobs left are done: the Shade lets go (leftovers to the pool) and the asker hears about it. */
    private boolean finishOrders(TarNetwork net) {
        boolean changed = false;
        for (com.tarbound.contract.CraftOrder o : List.copyOf(orders)) {
            boolean running = false;
            for (ContractJob j : jobs) {
                if (j.orderId() == o.id()) {
                    running = true;
                    break;
                }
            }
            if (running) {
                continue;
            }
            com.tarbound.contract.OrderVessel shade = shadeFor(o);
            if (shade == null && vesselSettled(o)) {
                orders.remove(o); // nothing left to release
                changed = true;
                continue;
            }
            if (shade == null) {
                continue; // its chunk is unloaded: let go once it's back
            }
            shade.release(net);
            orders.remove(o);
            changed = true;
            tell(o.requester(), o.fluid().isEmpty()
                    ? Component.translatable("message.tarbound.order.done", o.amount(), o.name())
                    : Component.translatable("message.tarbound.order.done_fluid", String.format(java.util.Locale.ROOT, "%,d", o.amount()), o.name()));
        }
        return changed;
    }

    /** A single job finished (or gave up): its requester hears about it, if they're online. */
    private void tellDone(ContractJob job) {
        Component name = job.contract().tracksFluid() ? job.contract().trackedFluid().getHoverName() : job.contract().primaryOutput().getHoverName();
        String amount = job.contract().tracksFluid() ? String.format(java.util.Locale.ROOT, "%,d mB", job.received()) : String.valueOf(job.received());
        tell(job.requester(), Component.translatable(job.abandoned() ? "message.tarbound.job.gave_up" : "message.tarbound.job.done", amount, name));
    }

    /** A line in chat for one player, with a soft chime, if they're online. */
    private void tell(@javax.annotation.Nullable java.util.UUID who, Component message) {
        if (who == null || level == null || level.getServer() == null) {
            return;
        }
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(who);
        if (player == null || !TarboundConfig.SERVER.jobNotices.get()) {
            return;
        }
        player.sendSystemMessage(message.copy().withStyle(ChatFormatting.DARK_AQUA));
        player.playNotifySound(net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_CHIME, net.minecraft.sounds.SoundSource.BLOCKS, 0.8F, 1.2F);
    }

    /** What an order job borrowed from its Shade for one pass: per item/fluid, the pool's count before, and how much went in. */
    private record Lent(Map<ItemKey, long[]> items, Map<com.tarbound.network.FluidKey, long[]> fluids) {}

    /**
     * Puts enough of what {@code shade} holds for this pass's operations of {@code job} into the pool, so the
     * job's ordinary loading finds it there. {@link #reclaim} takes back whatever wasn't used.
     */
    private Lent lend(TarNetwork net, com.tarbound.contract.OrderVessel shade, ContractJob job) {
        ContractData c = job.contract();
        long ops = Math.max(1, Math.min(opsPerPass(), job.totalOps() - job.pushedOps()));
        Map<ItemKey, Long> want = new java.util.LinkedHashMap<>();
        List<List<ItemStack>> inputs = c.inputs();
        for (int i = 0; i < inputs.size(); i++) {
            List<ItemStack> alts = inputs.get(i);
            TagKey<Item> tag = c.tagFor(i);
            long per = alts.isEmpty() ? 1 : Math.max(1, alts.get(0).getCount());
            long times = c.returnedInput(i) ? 1 : ops; // a Blood Orb comes back after each craft
            for (ItemKey k : shade.held().keySet()) {
                ItemStack proto = k.proto();
                boolean fits = tag != null && proto.is(tag);
                for (ItemStack a : alts) {
                    fits |= a.is(proto.getItem());
                }
                if (fits) {
                    want.merge(k, per * times, Long::sum);
                }
            }
        }
        Map<ItemKey, long[]> items = new java.util.LinkedHashMap<>();
        for (Map.Entry<ItemKey, Long> e : want.entrySet()) {
            ItemStack proto = e.getKey().proto();
            long pre = net.countOf(e.getKey());
            int n = shade.take(proto, (int) Math.min(Integer.MAX_VALUE, e.getValue()));
            long in = 0;
            while (n > 0) {
                int chunk = Math.min(n, Math.max(1, proto.getMaxStackSize()));
                ItemStack rest = net.insert(proto.copyWithCount(chunk));
                in += chunk - rest.getCount();
                n -= chunk;
                if (!rest.isEmpty()) {
                    shade.hold(e.getKey(), rest.getCount() + (long) n); // the pool is full: keep the rest
                    break;
                }
            }
            if (in > 0) {
                items.put(e.getKey(), new long[] {pre, in});
            }
        }
        Map<com.tarbound.network.FluidKey, long[]> fluids = new java.util.LinkedHashMap<>();
        for (FluidStack f : c.fluidInputs()) {
            com.tarbound.network.FluidKey key = com.tarbound.network.FluidKey.of(f);
            if (fluids.containsKey(key)) {
                continue;
            }
            long pre = net.fluidAmount(f);
            int n = shade.takeFluid(f, (int) Math.min(Integer.MAX_VALUE, (long) f.getAmount() * ops));
            int in = n > 0 ? net.insertFluid(f.copyWithAmount(n), false) : 0;
            if (in < n) {
                shade.holdFluid(f.copyWithAmount(n - in));
            }
            if (in > 0) {
                fluids.put(key, new long[] {pre, in});
            }
        }
        return new Lent(items, fluids);
    }

    /** Takes back into the Shade whatever the pass didn't use of what {@link #lend} put out. */
    private void reclaim(TarNetwork net, com.tarbound.contract.OrderVessel shade, Lent lent) {
        for (Map.Entry<ItemKey, long[]> e : lent.items().entrySet()) {
            long back = Math.max(0, Math.min(e.getValue()[1], net.countOf(e.getKey()) - e.getValue()[0]));
            ItemStack proto = e.getKey().proto();
            while (back > 0) {
                ItemStack got = net.extract(proto, (int) Math.min(back, Math.max(1, proto.getMaxStackSize())));
                if (got.isEmpty()) {
                    break;
                }
                shade.hold(got);
                back -= got.getCount();
            }
        }
        for (Map.Entry<com.tarbound.network.FluidKey, long[]> e : lent.fluids().entrySet()) {
            FluidStack proto = e.getKey().proto();
            long back = Math.max(0, Math.min(e.getValue()[1], net.fluidAmount(proto) - e.getValue()[0]));
            if (back > 0) {
                int got = net.drainFluid(proto, (int) Math.min(Integer.MAX_VALUE, back), false);
                shade.holdFluid(proto.copyWithAmount(got));
            }
        }
    }

    /** A sub-craft's outputs go to its order's Shade (out of the pool, where other jobs could take them). */
    private void deliverToShade(TarNetwork net, ContractJob job, long amount) {
        if (!job.deliverToShade() || amount <= 0) {
            return;
        }
        com.tarbound.contract.OrderVessel shade = shadeFor(job.orderId());
        if (shade == null) {
            return;
        }
        if (job.contract().tracksFluid()) {
            // a fluid sub-craft: what it made goes from the pool's tanks into the Shade
            FluidStack fluid = job.contract().trackedFluid();
            int got = net.drainFluid(fluid, (int) Math.min(Integer.MAX_VALUE, amount), false);
            shade.holdFluid(fluid.copyWithAmount(got));
            return;
        }
        // What the operations also made in fluid (a contract tracking its item) goes along: the order's later steps
        // count on it, and loose in the pool's tanks anything could take it.
        if (!job.contract().fluidOutputs().isEmpty()) {
            long ops = amount / Math.max(1, job.contract().perOp());
            for (FluidStack f : job.contract().fluidOutputs()) {
                long want = (long) f.getAmount() * ops;
                if (want > 0) {
                    int got = net.drainFluid(f, (int) Math.min(Integer.MAX_VALUE, want), false);
                    if (got > 0) {
                        shade.holdFluid(f.copyWithAmount(got));
                    }
                }
            }
        }
        ItemStack proto = job.contract().primaryOutput().copyWithCount(1);
        while (amount > 0) {
            ItemStack got = net.extract(proto, (int) Math.min(amount, Math.max(1, proto.getMaxStackSize())));
            if (got.isEmpty()) {
                break;
            }
            shade.hold(got);
            amount -= got.getCount();
        }
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, ScryingFontBlockEntity font) {
        int interval = font.jobs.isEmpty() && !font.standingActive ? idleInterval() : jobInterval();
        if ((level.getGameTime() + pos.asLong()) % interval != 0) {
            return;
        }
        TarNetwork net = font.network();
        font.refillSeeps(net);
        if (!net.isEmpty()) {
            font.burnOfferings(net);
            font.refillBattery(net);
        }
        font.tendRacks(!font.jobs.isEmpty() || !font.orders.isEmpty());
        if (net.isEmpty() || net.machines().isEmpty()) {
            for (ContractJob job : font.jobs) {
                job.setStatus(ContractJob.Status.NO_MACHINE);
            }
            return;
        }
        font.drainOutputs(net);
        font.dropOrphanedOrders();
        boolean hasJobs = !font.jobs.isEmpty();
        if (hasJobs) {
            font.creditPoolGains(net);
            font.runJobs(level, net);
        } else {
            font.lastOutputCounts.clear();
            font.lastFluidCounts.clear();
            if (!font.orders.isEmpty() && font.finishOrders(net)) {
                font.setChanged();
            }
        }
        font.runStanding(level, net);
        if (hasJobs) {
            font.snapshotOutputs(net);
        }
    }

    /**
     * Standing contracts: for each bound machine whose reliquary holds one, load operations of any pool item
     * in the contract's tag (most plentiful first), until the machine refuses, holds
     * {@link #standingBufferOps()} operations' worth, or {@link #opsPerPass()} have gone in this pass.
     */
    private void runStanding(Level level, TarNetwork net) {
        boolean any = false;
        Map<ItemKey, Long> stock = null;
        for (BoundInventory m : net.machines()) {
            if (!(ReliquaryBlock.reliquaryFor(level, m.pos()) instanceof ReliquaryBlockEntity reliquary)) {
                continue;
            }
            List<StandingOrder> orders = reliquary.standingOrders();
            if (m.isStation()) {
                // A composter composts what its standing contracts name (or the compost fodder tag); other stations
                // don't run standing contracts.
                if (com.tarbound.contract.StationContracts.kindOf(m.state()) == com.tarbound.contract.StationContracts.Kind.COMPOSTING
                        && level instanceof net.minecraft.server.level.ServerLevel sl) {
                    int cost = TarboundConfig.SERVER.standingOpCost.get();
                    int most = cost <= 0 || !com.tarbound.spirit.Spirit.costs() ? opsPerPass() : (int) Math.min(opsPerPass(), spirit / cost);
                    int fed = com.tarbound.contract.StationContracts.tendComposter(sl, m.pos(), net, orders, Math.max(0, most));
                    if (fed > 0) {
                        pay(fed * cost);
                        any = true;
                        stock = null;
                    }
                }
                continue;
            }
            if (orders.isEmpty()) {
                continue;
            }
            for (StandingOrder order : orders) {
                long held = 0;
                for (BoundInventory.SlotRef ref : m.slots()) {
                    ItemStack in = ref.stack();
                    if (order.matches(in)) {
                        held += in.getCount();
                    }
                }
                long room = standingBufferOps() - held / order.count();
                if (room <= 0) {
                    continue;
                }
                if (stock == null) {
                    stock = net.countContents();
                }
                List<Map.Entry<ItemKey, Long>> candidates = new ArrayList<>();
                for (Map.Entry<ItemKey, Long> e : stock.entrySet()) {
                    if (e.getValue() >= order.count() && order.matches(e.getKey().proto())) {
                        candidates.add(e);
                    }
                }
                candidates.sort(Map.Entry.<ItemKey, Long>comparingByValue().reversed());
                int limit = (int) Math.min(room, opsPerPass());
                int pushes = 0;
                for (Map.Entry<ItemKey, Long> c : candidates) {
                    List<List<ItemStack>> inputs = List.of(List.of(c.getKey().proto().copyWithCount(order.count())));
                    int cost = TarboundConfig.SERVER.standingOpCost.get();
                    while (pushes < limit && canPay(cost)
                            && MachineLoader.load(level, m, inputs, List.of(), recordLoads(m.pos(), MachineLoader.pool(level, net, m.pos().above()))) == null) {
                        pay(cost);
                        pushes++;
                    }
                    if (pushes >= limit) {
                        break;
                    }
                }
                if (pushes > 0) {
                    stock = null;
                    any = true;
                }
            }
        }
        // Stay on the fast interval only while standing contracts are actually loading something;
        // once they're all topped up (or starved), drop to the idle interval until there's work.
        standingActive = any;
    }

    /**
     * Machines whose last drain moved nothing, by what their slots held then. Until that changes they're left alone
     * (the slot and face checks run simulated inserts, which some machines answer with a recipe lookup), apart from
     * a look every {@link #DRAIN_RECHECK} passes. The same idea as PipezLagFix's backoff for idle pipes, but woken
     * by the machine changing rather than by a timer. Not saved.
     */
    private final Map<BlockPos, Long> idleDrains = new java.util.HashMap<>();
    private static final int DRAIN_RECHECK = 8;
    private int drainPass;

    /** A cheap fingerprint of a machine's slots: item, count and components of each. */
    private static long slotPrint(BoundInventory machine) {
        long h = 1;
        for (BoundInventory.SlotRef ref : machine.slots()) {
            ItemStack in = ref.stack();
            h = h * 31 + (in.isEmpty() ? 0 : ItemStack.hashItemAndComponents(in) * 64L + in.getCount());
        }
        return h;
    }

    /** Move finished products out of bound machines into the pool (jobs see them via pool gains). */
    private void drainOutputs(TarNetwork net) {
        java.util.Set<ItemKey> noRoom = new java.util.HashSet<>(); // a full pool is checked once per item type per pass
        boolean recheck = ++drainPass % DRAIN_RECHECK == 0;
        if (recheck) {
            idleDrains.keySet().removeIf(p -> net.machines().stream().noneMatch(m -> m.pos().equals(p))); // unbound since
            // A ledger for a machine that's loaded but no longer bound here (broken, Reliquary removed) is dropped.
            loadedInto.keySet().removeIf(p -> level.isLoaded(p) && net.machines().stream().noneMatch(m -> m.pos().equals(p)));
        }
        for (BoundInventory machine : net.machines()) {
            if (!machine.alive() || com.tarbound.compat.create.CrafterGrid.isCrafter(machine.state())) {
                continue; // a crafter grid hands its result out of its last crafter; nothing to drain here
            }
            drainFluidOutputs(net, machine);
            long print = slotPrint(machine);
            Long idle = idleDrains.get(machine.pos());
            if (!recheck && idle != null && idle == print) {
                continue; // nothing to drain last time, and nothing has changed
            }
            int roomBefore = noRoom.size();
            drainItems(net, machine, noRoom);
            long after = slotPrint(machine);
            if (after == print && noRoom.size() == roomBefore) {
                idleDrains.put(machine.pos(), after); // nothing moved (and not for want of room): idle until it changes
            } else {
                idleDrains.remove(machine.pos());
            }
        }
    }

    /**
     * The drain rule, in one place. The radio knows what it put into the machine and hasn't seen worked yet (its
     * ledger, {@link #loadedInto}); that is never drained. Beyond it, a slot's contents drain if a contract on this
     * machine's Reliquary makes them, or if the slot plainly holds an output (won't take more, will give it up) and no
     * contract here uses that item. So loaded inputs stay however the machine reports their slot (Oritech's full input
     * slots, Create's depot mid-process, AE2's presses), and chained products drain even where another contract here
     * uses them (a Blood Altar's fresh slate, before it upgrades it). Byproducts no contract lists (slag, an empty
     * bucket) drain by the slot test alone.
     */
    private void drainItems(TarNetwork net, BoundInventory machine, java.util.Set<ItemKey> noRoom) {
        MachineGoods goods = machineGoods(machine);
        Map<ItemKey, Integer> keep = new java.util.HashMap<>(reconcileLoads(machine));
        List<BoundInventory.SlotRef> drainable = new java.util.ArrayList<>();
        // Loaded items are counted against the slots that won't drain first, so an input slot holding them
        // doesn't leave an output slot of the same item protected by mistake.
        for (BoundInventory.SlotRef ref : machine.slots()) {
            ItemStack in = ref.stack();
            if (in.isEmpty()) {
                continue;
            }
            ItemKey key = ItemKey.of(in);
            if (goods.made().contains(key) || (!goods.used().contains(key) && BoundInventory.isFinishedOutput(ref.handler(), ref.slot()))) {
                drainable.add(ref);
            } else {
                keep.computeIfPresent(key, (k, v) -> v - in.getCount() > 0 ? v - in.getCount() : null);
            }
        }
        for (BoundInventory.SlotRef ref : drainable) {
            ItemStack in = ref.stack();
            if (in.isEmpty()) {
                continue;
            }
            ItemKey key = ItemKey.of(in);
            if (goods.made().contains(key) && goods.used().contains(key) && BoundInventory.isFinishedOutput(ref.handler(), ref.slot())) {
                // A catalyst the recipe hands back (a ghast tear in, the same tear out): once it's sitting in an output
                // slot it's the product, not the input we loaded, so the ledger doesn't hold it.
                drainSlot(net, machine, ref.handler(), ref.slot(), noRoom, in.getCount());
                continue;
            }
            int held = keep.getOrDefault(key, 0);
            keep.put(key, Math.max(0, held - in.getCount()));
            if (in.getCount() > held) {
                drainSlot(net, machine, ref.handler(), ref.slot(), noRoom, in.getCount() - held);
            }
        }
    }

    /**
     * Moves one machine slot's contents into the pool. Never inserts before the items have really left the machine
     * (a handler whose simulated pull promises more than the real one gives would duplicate the difference): see
     * what's on offer and what the pool has room for, both simulated; take that for real; insert exactly what was
     * taken. Whatever then doesn't fit (the pool filled in between) goes back into the slot, or drops at the machine.
     */
    private int drainSlot(TarNetwork net, BoundInventory machine, IItemHandler h, int slot, java.util.Set<ItemKey> noRoom, int max) {
        ItemStack offered = h.extractItem(slot, Math.min(64, max), true);
        if (offered.isEmpty()) {
            return 0;
        }
        ItemKey key = ItemKey.of(offered);
        int room = noRoom.contains(key) ? 0 : Math.min(net.fitCount(offered), offered.getCount());
        if (room <= 0) {
            noRoom.add(key);
            return 0;
        }
        ItemStack taken = h.extractItem(slot, room, false);
        if (taken.isEmpty()) {
            return 0;
        }
        ItemStack rest = net.insert(taken);
        int moved = taken.getCount() - rest.getCount();
        if (!rest.isEmpty()) {
            rest = h.insertItem(slot, rest, false);
        }
        if (!rest.isEmpty()) {
            Block.popResource(level, machine.pos().above(), rest);
        }
        return moved;
    }

    /** What the contracts on a machine's Reliquary make, and what they use (an item can be both). */
    private record MachineGoods(java.util.Set<ItemKey> made, java.util.Set<ItemKey> used) {}

    /**
     * Per machine, what this radio has loaded into it and hasn't yet seen worked (consumed or transformed), by item.
     * Saved (key {@code loaded}), so a reload mid-job doesn't forget what's work in progress.
     */
    private final Map<BlockPos, Map<ItemKey, Integer>> loadedInto = new java.util.HashMap<>();

    /** A source that notes what it hands to the machine at {@code machine} (taken, less anything given back). */
    public MachineLoader.Source recordLoads(BlockPos machine, MachineLoader.Source inner) {
        Map<ItemKey, Integer> ledger = loadedInto.computeIfAbsent(machine.immutable(), p -> new java.util.HashMap<>());
        return new MachineLoader.Source() {
            @Override
            public Map<ItemKey, Long> snapshot() {
                return inner.snapshot();
            }

            @Override
            public Map<ItemKey, Long> snapshot(List<List<ItemStack>> inputs) {
                return inner.snapshot(inputs);
            }

            @Override
            public int take(ItemStack proto, int amount) {
                int got = inner.take(proto, amount);
                if (got > 0) {
                    ledger.merge(ItemKey.of(proto), got, Integer::sum);
                }
                return got;
            }

            @Override
            public void giveBack(ItemStack stack) {
                ledger.computeIfPresent(ItemKey.of(stack), (k, v) -> v - stack.getCount() > 0 ? v - stack.getCount() : null);
                inner.giveBack(stack);
            }

            @Override
            public void returnToPool(ItemStack stack) {
                inner.returnToPool(stack);
            }

            @Override
            public long fluidAmount(FluidStack fluid) {
                return inner.fluidAmount(fluid);
            }

            @Override
            public int drainFluid(FluidStack fluid, int amount) {
                return inner.drainFluid(fluid, amount);
            }

            @Override
            public void giveBackFluid(FluidStack fluid) {
                inner.giveBackFluid(fluid);
            }
        };
    }

    /**
     * The machine's ledger, trimmed to what it still holds: whatever it consumed or turned into something else is no
     * longer ours to protect. Empty for a machine this radio has nothing loaded in.
     */
    private Map<ItemKey, Integer> reconcileLoads(BoundInventory machine) {
        Map<ItemKey, Integer> ledger = loadedInto.get(machine.pos());
        if (ledger == null) {
            return Map.of();
        }
        Map<ItemKey, Integer> present = new java.util.HashMap<>();
        for (BoundInventory.SlotRef ref : machine.slots()) {
            ItemStack in = ref.stack();
            if (!in.isEmpty()) {
                present.merge(ItemKey.of(in), in.getCount(), Integer::sum);
            }
        }
        ledger.replaceAll((k, v) -> Math.min(v, present.getOrDefault(k, 0)));
        ledger.values().removeIf(v -> v <= 0);
        if (ledger.isEmpty()) {
            loadedInto.remove(machine.pos());
        }
        return ledger;
    }

    private MachineGoods machineGoods(BoundInventory machine) {
        if (!(ReliquaryBlock.reliquaryFor(level, machine.pos()) instanceof ReliquaryBlockEntity reliquary)) {
            return new MachineGoods(java.util.Set.of(), java.util.Set.of());
        }
        java.util.Set<ItemKey> outs = new java.util.HashSet<>();
        java.util.Set<ItemKey> ins = new java.util.HashSet<>();
        for (ContractData c : reliquary.contractSlots()) {
            if (c == null) {
                continue;
            }
            for (ItemStack o : c.outputs()) {
                outs.add(ItemKey.of(o));
            }
            for (List<ItemStack> alts : c.inputs()) {
                for (ItemStack i : alts) {
                    ins.add(ItemKey.of(i));
                }
            }
        }
        return new MachineGoods(outs, ins);
    }

    /**
     * Pumps fluids a contract on this machine's Reliquary makes (and none of them uses) out of the
     * machine's tanks into the pool's. Only those fluids: water a machine was given to work with stays put.
     */
    private void drainFluidOutputs(TarNetwork net, BoundInventory machine) {
        if (net.tanks().isEmpty() || machine.isStation()
                || !(ReliquaryBlock.reliquaryFor(level, machine.pos()) instanceof ReliquaryBlockEntity reliquary)) {
            return;
        }
        java.util.Set<com.tarbound.network.FluidKey> outs = new java.util.LinkedHashSet<>();
        java.util.Set<com.tarbound.network.FluidKey> ins = new java.util.HashSet<>();
        for (ContractData c : reliquary.contractSlots()) {
            if (c == null) {
                continue;
            }
            for (FluidStack f : c.fluidOutputs()) {
                outs.add(com.tarbound.network.FluidKey.of(f));
            }
            for (FluidStack f : c.fluidInputs()) {
                ins.add(com.tarbound.network.FluidKey.of(f));
            }
        }
        outs.removeAll(ins);
        if (outs.isEmpty()) {
            return;
        }
        IFluidHandler tanks = com.tarbound.network.MachineAccess.fluids(level, machine.pos());
        if (tanks == null) {
            return;
        }
        for (com.tarbound.network.FluidKey key : outs) {
            // As with items: see what's there and what fits, both simulated; then move exactly that.
            FluidStack offered = tanks.drain(key.proto().copyWithAmount(Integer.MAX_VALUE), IFluidHandler.FluidAction.SIMULATE);
            if (offered.isEmpty()) {
                continue;
            }
            int room = net.insertFluid(offered, true);
            if (room <= 0) {
                continue;
            }
            FluidStack taken = tanks.drain(key.proto().copyWithAmount(room), IFluidHandler.FluidAction.EXECUTE);
            if (taken.isEmpty()) {
                continue;
            }
            int in = net.insertFluid(taken, false);
            if (in < taken.getAmount()) {
                tanks.fill(taken.copyWithAmount(taken.getAmount() - in), IFluidHandler.FluidAction.EXECUTE); // back where it came from
            }
        }
    }

    /**
     * Jobs are credited by how much of their primary output the pool gained since the last pass,
     * however it got there: drained from a bound machine, or carried in by belts, funnels and chutes
     * into storage touching the tar (like AE2, which counts anything entering the network).
     */
    private void creditPoolGains(TarNetwork net) {
        Map<ItemKey, Long> counts = net.countContents();
        Map<com.tarbound.network.FluidKey, Long> fluidCounts = net.countFluids();
        for (ContractJob job : List.copyOf(jobs)) {
            if (job.contract().tracksFluid()) {
                com.tarbound.network.FluidKey key = com.tarbound.network.FluidKey.of(job.contract().trackedFluid());
                Long before = lastFluidCounts.get(key);
                if (before == null) {
                    continue; // first look at this fluid: baseline only
                }
                long gain = fluidCounts.getOrDefault(key, 0L) - before;
                if (gain > 0) {
                    creditFluid(net, key, gain);
                    lastFluidCounts.put(key, before + gain);
                }
                continue;
            }
            ItemKey key = ItemKey.of(job.contract().primaryOutput());
            Long before = lastOutputCounts.get(key);
            if (before == null) {
                continue; // first look at this item: baseline only
            }
            long gain = counts.getOrDefault(key, 0L) - before;
            if (gain > 0) {
                credit(net, key.proto(), gain);
                lastOutputCounts.put(key, before + gain); // don't credit the same gain twice
            }
        }
    }

    private void snapshotOutputs(TarNetwork net) {
        Map<ItemKey, Long> counts = net.countContents();
        Map<com.tarbound.network.FluidKey, Long> fluidCounts = net.countFluids();
        lastOutputCounts.clear();
        lastFluidCounts.clear();
        for (ContractJob job : jobs) {
            if (job.contract().tracksFluid()) {
                com.tarbound.network.FluidKey key = com.tarbound.network.FluidKey.of(job.contract().trackedFluid());
                lastFluidCounts.put(key, fluidCounts.getOrDefault(key, 0L));
                continue;
            }
            ItemKey key = ItemKey.of(job.contract().primaryOutput());
            lastOutputCounts.put(key, counts.getOrDefault(key, 0L));
        }
    }

    /** As {@link #credit} for fluid jobs: {@code amount} mB of {@code key} arrived in the pool's tanks. */
    private void creditFluid(TarNetwork net, com.tarbound.network.FluidKey key, long amount) {
        boolean changed = false;
        for (ContractJob job : jobs) {
            if (amount <= 0) {
                break;
            }
            if (job.contract().tracksFluid() && com.tarbound.network.FluidKey.of(job.contract().trackedFluid()).equals(key)) {
                long took = job.credit(amount);
                deliverToShade(net, job, took);
                amount -= took;
                changed |= took > 0;
            }
        }
        if (changed) {
            setChanged();
        }
    }

    private void credit(TarNetwork net, ItemStack what, long amount) {
        boolean changed = false;
        for (ContractJob job : jobs) {
            if (amount <= 0) {
                break;
            }
            if (!job.contract().tracksFluid() && ItemStack.isSameItemSameComponents(job.contract().primaryOutput(), what)) {
                long took = job.credit(amount);
                deliverToShade(net, job, took);
                amount -= took;
                changed |= took > 0;
            }
        }
        if (changed) {
            setChanged();
        }
    }

    private void runJobs(Level level, TarNetwork net) {
        boolean changed = false;
        for (ContractJob job : List.copyOf(jobs)) {
            if (job.orderId() == 0) {
                changed |= runJob(level, net, job);
                continue;
            }
            com.tarbound.contract.OrderVessel shade = shadeFor(job.orderId());
            if (shade == null) {
                job.setStatus(ContractJob.Status.WAITING_INPUTS); // its Shade's chunk isn't loaded
                continue;
            }
            // Machines load an order's ingredients straight from its Shade. Crafting stations craft from the
            // pool, so for them the Shade lends this pass's share into storage and takes back what's left.
            Lent lent = job.allPushed() || !runsOnStations(level, net, job) ? null : lend(net, shade, job);
            lentKeys = lent == null ? java.util.Set.of() : lent.items().keySet();
            loadingShade = lent == null ? shade : null;
            try {
                changed |= runJob(level, net, job);
            } finally {
                lentKeys = java.util.Set.of();
                loadingShade = null;
                if (lent != null) {
                    reclaim(net, shade, lent);
                }
            }
        }
        for (ContractJob job : jobs) {
            if (job.done() && job.orderId() == 0) {
                tellDone(job);
            }
        }
        changed |= jobs.removeIf(ContractJob::done);
        changed |= finishOrders(net);
        if (changed) {
            setChanged();
        }
    }

    /** Items lent from a Shade for the job running now: crafting stations use these first. */
    private java.util.Set<ItemKey> lentKeys = java.util.Set.of();
    /**
     * The Shade the job running now loads its machines from directly (an order job on real machines), or null.
     * Loading straight from it, rather than lending its stock through storage, means nothing set aside can be
     * routed into a Tithe chest, grabbed by a hopper or a standing contract, or mistaken for new output.
     */
    @javax.annotation.Nullable
    private com.tarbound.contract.OrderVessel loadingShade;

    /** Whether any bound crafting station holds this job's contract (it then crafts from the pool). */
    private static boolean runsOnStations(Level level, TarNetwork net, ContractJob job) {
        for (BoundInventory m : net.machines()) {
            if (m.isStation() && ReliquaryBlock.reliquaryFor(level, m.pos()) instanceof ReliquaryBlockEntity r && r.holds(job.contract())) {
                return true;
            }
        }
        return false;
    }

    /** The pool's stock for {@code job}, plus what its Shade holds when it loads from it. */
    private Map<ItemKey, Long> stockFor(TarNetwork net, ContractJob job) {
        Map<ItemKey, Long> stock = new HashMap<>(net.countForJobs(job.contract().inputs()));
        if (loadingShade != null) {
            loadingShade.held().forEach((k, v) -> stock.merge(k, v, Long::sum));
        }
        return stock;
    }

    private Map<com.tarbound.network.FluidKey, Long> fluidsFor(TarNetwork net) {
        Map<com.tarbound.network.FluidKey, Long> fluids = new HashMap<>(net.countFluids());
        if (loadingShade != null) {
            loadingShade.heldFluids().forEach((k, v) -> fluids.merge(k, v, Long::sum));
        }
        return fluids;
    }

    /** Loads from {@code shade} first, then the pool; anything refused goes back to the Shade. */
    private static MachineLoader.Source shadeFirst(com.tarbound.contract.OrderVessel shade, TarNetwork net, MachineLoader.Source pool) {
        return new MachineLoader.Source() {
            @Override
            public Map<ItemKey, Long> snapshot() {
                Map<ItemKey, Long> m = new HashMap<>(pool.snapshot());
                shade.held().forEach((k, v) -> m.merge(k, v, Long::sum));
                return m;
            }

            @Override
            public Map<ItemKey, Long> snapshot(List<List<ItemStack>> inputs) {
                Map<ItemKey, Long> m = new HashMap<>(pool.snapshot(inputs));
                shade.held().forEach((k, v) -> m.merge(k, v, Long::sum));
                return m;
            }

            @Override
            public int take(ItemStack proto, int amount) {
                int got = shade.take(proto, amount);
                return got < amount ? got + pool.take(proto, amount - got) : got;
            }

            @Override
            public void giveBack(ItemStack stack) {
                shade.hold(stack);
            }

            @Override
            public void returnToPool(ItemStack stack) {
                pool.giveBack(stack);
            }

            @Override
            public long fluidAmount(FluidStack fluid) {
                return shade.heldFluids().getOrDefault(com.tarbound.network.FluidKey.of(fluid), 0L) + pool.fluidAmount(fluid);
            }

            @Override
            public int drainFluid(FluidStack fluid, int amount) {
                int got = shade.takeFluid(fluid, amount);
                return got < amount ? got + pool.drainFluid(fluid, amount - got) : got;
            }

            @Override
            public void giveBackFluid(FluidStack fluid) {
                shade.holdFluid(fluid);
            }
        };
    }

    /** Whether an earlier job of the same order (one feeding this one) is still queued. */
    private boolean feedersRunning(ContractJob job) {
        for (ContractJob j : jobs) {
            if (j == job) {
                return false;
            }
            if (j.orderId() == job.orderId()) {
                return true;
            }
        }
        return false;
    }

    /** One pass of one job. @return whether anything changed worth saving */
    private boolean runJob(Level level, TarNetwork net, ContractJob job) {
        boolean changed = false;
        Map<com.tarbound.network.FluidKey, Long> fluids = fluidsFor(net);
        {
            // The pool (plus filled buckets it could make for this contract from empty ones and its tanks),
            // and the order's Shade when this job loads from it.
            Map<ItemKey, Long> stock = stockFor(net, job);
            if (job.allPushed()) {
                if (job.contract().tracksFluid() && job.received() < job.expected()
                        && net.insertFluid(job.contract().trackedFluid().copyWithAmount(1), true) <= 0) {
                    // Nowhere to put the fluid: the machine keeps it until there's tank room. Not a stall.
                    job.resetStall();
                    job.setStatus(ContractJob.Status.NO_TANK_ROOM);
                    return changed;
                }
                long limit = TarboundConfig.SERVER.jobStallSeconds.get() * 20L;
                long stalled = job.stalledFor(level.getGameTime());
                if (job.deliverToShade() && job.received() < job.expected() && stalled >= Math.min(200, limit / 2)
                        && job.extraOps() < Math.max(2, job.totalOps() / 4)) {
                    // A sub-craft came up short (chance outputs): run it again rather than starve the order.
                    job.addOp();
                    job.setStatus(ContractJob.Status.RUNNING);
                    return true;
                }
                if (stalled >= limit) {
                    job.abandon(); // outputs stopped coming; close it rather than wait forever
                } else {
                    job.setStatus(stalled * 2 >= limit ? ContractJob.Status.STALLED : ContractJob.Status.FINISHING);
                }
                return changed;
            }
            // Every bound machine (or crafting station) whose Reliquary holds this contract shares the job.
            // A crafting contract only runs on stations, and a machine contract only on real machines.
            List<BoundInventory> machines = new ArrayList<>();
            Map<BlockPos, RecipeHolder<CraftingRecipe>> recipes = new HashMap<>();
            Map<BlockPos, RecipeHolder<net.minecraft.world.item.crafting.StonecutterRecipe>> cuts = new HashMap<>();
            Map<BlockPos, java.util.Set<net.minecraft.world.item.Item>> exactItems = new HashMap<>();
            for (BoundInventory m : net.machines()) {
                if (!(ReliquaryBlock.reliquaryFor(level, m.pos()) instanceof ReliquaryBlockEntity reliquary) || !reliquary.holds(job.contract())) {
                    continue;
                }
                RecipeHolder<CraftingRecipe> recipe = CraftingContracts.recipe(level, reliquary.craftingRecipeFor(job.contract()));
                com.tarbound.contract.StationContracts.Kind kind = m.isStation() ? com.tarbound.contract.StationContracts.kindOf(m.state()) : null;
                if (kind == com.tarbound.contract.StationContracts.Kind.CUTTING) {
                    // A stonecutter runs a plain one-input contract that a stonecutting recipe matches.
                    var cut = recipe == null ? com.tarbound.contract.StationContracts.cuttingRecipe(level, job.contract()) : null;
                    if (cut == null) {
                        continue;
                    }
                    cuts.put(m.pos(), cut);
                    machines.add(m);
                    continue;
                }
                if (kind == com.tarbound.contract.StationContracts.Kind.COMPOSTING) {
                    continue; // composters only work standing contracts
                }
                if (m.isStation() != (recipe != null)) {
                    continue;
                }
                if (recipe != null) {
                    recipes.put(m.pos(), recipe);
                    if (reliquary.exactFor(job.contract())) {
                        java.util.Set<net.minecraft.world.item.Item> only = new java.util.HashSet<>();
                        job.contract().inputs().forEach(alts -> alts.forEach(st -> only.add(st.getItem())));
                        exactItems.put(m.pos(), only);
                    }
                }
                machines.add(m);
            }
            if (machines.isEmpty()) {
                job.setStatus(ContractJob.Status.NO_MACHINE);
                if (com.tarbound.TarboundDebug.on()) {
                    StringBuilder why = new StringBuilder("no bound machine runs this contract (" + job.contract().primaryOutput().getHoverName().getString()
                            + "); machines on this radio:");
                    for (BoundInventory m : net.machines()) {
                        boolean holds = ReliquaryBlock.reliquaryFor(level, m.pos()) instanceof ReliquaryBlockEntity r && r.holds(job.contract());
                        why.append("\n    ").append(m.pos().toShortString()).append(' ').append(com.tarbound.TarboundDebug.block(level, m.pos()))
                                .append(holds ? " holds it" : " doesn't hold it").append(m.isStation() ? " (crafting station)" : "");
                    }
                    com.tarbound.TarboundDebug.log(level, worldPosition, "job " + job.id(), why.toString());
                }
                return changed;
            }
            if (!hasInputs(job.contract(), stock, fluids)) {
                job.setStatus(ContractJob.Status.WAITING_INPUTS);
                noteShortfall(job, stock, fluids);
                if (com.tarbound.TarboundDebug.on()) {
                    StringBuilder why = new StringBuilder("waiting for inputs (" + job.contract().primaryOutput().getHoverName().getString() + "):");
                    for (net.neoforged.neoforge.fluids.FluidStack f : job.contract().fluidInputs()) {
                        why.append("\n    fluid ").append(com.tarbound.TarboundDebug.fluid(f)).append(" key ").append(com.tarbound.network.FluidKey.of(f))
                                .append(" -> pool has ").append(fluids.getOrDefault(com.tarbound.network.FluidKey.of(f), 0L)).append("mB");
                    }
                    why.append("\n    pool fluids: ").append(fluids);
                    List<List<ItemStack>> listed = job.contract().inputsFor(stock);
                    for (List<ItemStack> alts : listed) {
                        long have = 0;
                        for (ItemStack a : alts) {
                            have += stock.getOrDefault(ItemKey.of(a), 0L);
                        }
                        why.append("\n    item ").append(alts.isEmpty() ? "-" : alts.get(0).getCount() + "x " + alts.get(0).getHoverName().getString()
                                + (alts.size() > 1 ? " (+" + (alts.size() - 1) + " alts)" : "")).append(" -> stock ").append(have);
                    }
                    com.tarbound.TarboundDebug.log(level, worldPosition, "job " + job.id(), why.toString());
                }
                // Part of an order whose feeders are all done, and still nothing to work with: it never will.
                if (job.orderId() != 0 && !feedersRunning(job)
                        && job.stalledFor(level.getGameTime()) >= TarboundConfig.SERVER.jobStallSeconds.get() * 20L) {
                    job.abandon();
                    return true;
                }
                return changed;
            }
            List<List<ItemStack>> inputs = job.contract().inputsFor(stock); // tagged inputs widened to the pool
            int pushes = 0;
            int failures = 0;
            Component lastRefusal = null;
            boolean noRoom = false;
            boolean starved = false;
            Map<BlockPos, Integer> stationCrafts = new HashMap<>();
            int perStation = TarboundConfig.SERVER.stationCraftsPerPass.get();
            int stationCost = TarboundConfig.SERVER.stationCraftCost.get();
            int opCost = TarboundConfig.SERVER.jobOpCost.get();
            while (pushes < opsPerPass() && !job.allPushed() && failures < machines.size()) {
                BoundInventory m = machines.get(Math.floorMod(roundRobin++, machines.size()));
                if (!canPay(m.isStation() ? stationCost : opCost)) {
                    starved = true;
                    break; // out of Spirit: wait to be fed
                }
                boolean done;
                if (m.isStation()) {
                    // A crafting station crafts a few times per pass; more stations, more crafts.
                    int used = stationCrafts.getOrDefault(m.pos(), 0);
                    var cut = cuts.get(m.pos());
                    int made = used >= perStation ? 0
                            : cut != null ? com.tarbound.contract.StationContracts.cutOnce(level, net, cut, job.contract(), m.pos().above(), lentKeys)
                            : CraftingContracts.craftOnce(level, net, recipes.get(m.pos()), m.pos().above(), lentKeys, exactItems.get(m.pos()));
                    if (made == CraftingContracts.NO_ROOM) {
                        noRoom = true;
                        break; // the pool is full for every station alike: don't re-check it per station
                    }
                    done = made > 0;
                    if (done) {
                        stationCrafts.put(m.pos(), used + 1);
                        // The result went into the pool mid-pass, so the pool-gain check (start of pass vs.
                        // last pass's end snapshot) would never see it: credit it here instead. The
                        // end-of-pass snapshot already includes it, so it isn't counted twice.
                        deliverToShade(net, job, job.credit(made));
                    }
                } else {
                    MachineLoader.Source source = MachineLoader.forJobs(level, net, m.pos().above());
                    if (loadingShade != null) {
                        source = shadeFirst(loadingShade, net, source);
                    }
                    Component refused = com.tarbound.compat.create.CrafterGrid.isCrafter(m.state())
                            ? com.tarbound.compat.create.CrafterGrid.load(level, m.pos(), job.contract(), source) // a Create crafter grid
                            : MachineLoader.load(level, m, inputs, job.contract().fluidInputs(), recordLoads(m.pos(), source));
                    done = refused == null;
                    if (refused != null) {
                        lastRefusal = refused;
                    }
                }
                if (done) {
                    pay(m.isStation() ? stationCost : opCost);
                    job.pushed();
                    pushes++;
                    failures = 0;
                    changed = true;
                } else {
                    failures++;
                }
            }
            if (pushes > 0) {
                stock = stockFor(net, job);
                fluids = fluidsFor(net);
                job.setStatus(ContractJob.Status.RUNNING);
            } else {
                job.setStatus(starved ? ContractJob.Status.HUNGRY : noRoom ? ContractJob.Status.POOL_FULL
                        : hasInputs(job.contract(), stock, fluids) ? ContractJob.Status.MACHINE_BUSY : ContractJob.Status.WAITING_INPUTS);
                if (job.status() == ContractJob.Status.MACHINE_BUSY) {
                    job.refusedBecause(lastRefusal);
                } else if (job.status() == ContractJob.Status.WAITING_INPUTS) {
                    noteShortfall(job, stock, fluids);
                }
            }
        }
        return changed;
    }

    /** Enough in stock for one operation (each input from its most plentiful alternative). */
    /** Notes on the job which input storage is short of for one operation, and which job here (if any) is making it. */
    private void noteShortfall(ContractJob job, Map<ItemKey, Long> stock, Map<com.tarbound.network.FluidKey, Long> fluids) {
        ContractData c = job.contract();
        for (FluidStack f : c.fluidInputs()) {
            long have = fluids.getOrDefault(com.tarbound.network.FluidKey.of(f), 0L);
            if (have < f.getAmount()) {
                job.waitingFor(ItemStack.EMPTY, f.copyWithAmount((int) (f.getAmount() - have)), feederFor(job, null, f));
                return;
            }
        }
        List<List<ItemStack>> listed = c.inputsFor(stock);
        for (int i = 0; i < listed.size(); i++) {
            List<ItemStack> alts = listed.get(i);
            if (alts.isEmpty() || ContractData.staysInMachine(alts)) {
                continue;
            }
            int need = c.returnedInput(i) ? 1 : Math.max(1, alts.get(0).getCount());
            long have = 0;
            for (ItemStack a : alts) {
                have = Math.max(have, stock.getOrDefault(ItemKey.of(a), 0L));
            }
            if (have < need) {
                job.waitingFor(alts.get(0).copyWithCount((int) (need - have)), FluidStack.EMPTY, feederFor(job, alts, null));
                return;
            }
        }
    }

    /** Another unfinished job on this radio that makes one of {@code alts} (or {@code fluid}): its id, or 0. */
    private int feederFor(ContractJob job, @javax.annotation.Nullable List<ItemStack> alts, @javax.annotation.Nullable FluidStack fluid) {
        for (ContractJob other : jobs) {
            if (other == job || other.done()) {
                continue;
            }
            ContractData oc = other.contract();
            if (fluid != null) {
                for (FluidStack o : oc.fluidOutputs()) {
                    if (FluidStack.isSameFluidSameComponents(o, fluid)) {
                        return other.id();
                    }
                }
            } else if (alts != null) {
                for (ItemStack o : oc.outputs()) {
                    for (ItemStack a : alts) {
                        if (ItemStack.isSameItemSameComponents(o, a)) {
                            return other.id();
                        }
                    }
                }
            }
        }
        return 0;
    }

    public static boolean hasInputs(ContractData contract, Map<ItemKey, Long> stock, Map<com.tarbound.network.FluidKey, Long> fluids) {
        return maxOps(contract, stock, fluids) >= 1;
    }

    /**
     * How many operations the stock covers, inputs sharing items accounted for approximately.
     * {@code stock} should already count filled buckets the pool can make ({@link TarNetwork#countWithFillsFor});
     * {@code fluids} is the pool's tanks, in mB.
     */
    public static long maxOps(ContractData contract, Map<ItemKey, Long> stock, Map<com.tarbound.network.FluidKey, Long> fluids) {
        long ops = Long.MAX_VALUE;
        for (net.neoforged.neoforge.fluids.FluidStack f : contract.fluidInputs()) {
            ops = Math.min(ops, fluids.getOrDefault(com.tarbound.network.FluidKey.of(f), 0L) / Math.max(1, f.getAmount()));
        }
        List<List<ItemStack>> listed = contract.inputsFor(stock);
        for (int idx = 0; idx < listed.size(); idx++) {
            List<ItemStack> alts = listed.get(idx);
            if (ContractData.staysInMachine(alts)) {
                continue; // a press or mold: one in the machine does for every operation (the loader checks)
            }
            if (contract.returnedInput(idx)) {
                // handed back by every craft: one on hand is enough for any number of operations
                boolean have = alts.stream().anyMatch(a -> stock.getOrDefault(ItemKey.of(a), 0L) >= Math.max(1, a.getCount()));
                if (!have) {
                    return 0;
                }
                continue;
            }
            long best = 0;
            for (ItemStack alt : alts) {
                long per = Math.max(1, alt.getCount());
                best = Math.max(best, stock.getOrDefault(ItemKey.of(alt), 0L) / per);
            }
            ops = Math.min(ops, best);
        }
        return ops == Long.MAX_VALUE ? 0 : ops;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ListTag list = new ListTag();
        for (ContractJob job : jobs) {
            list.add(job.save(registries));
        }
        tag.put("jobs", list);
        tag.putInt("nextJob", nextJobId);
        ListTag orderList = new ListTag();
        for (com.tarbound.contract.CraftOrder o : orders) {
            orderList.add(o.save(registries));
        }
        tag.put("orders", orderList);
        tag.putInt("next_order", nextOrderId);
        tag.putUUID("radio_id", radioId());
        tag.putInt("spirit", spirit);
        tag.putInt("energy", energy);
        tag.putBoolean("burn_from_pool", burnFromPool);
        ListTag loaded = new ListTag();
        for (Map.Entry<BlockPos, Map<ItemKey, Integer>> e : loadedInto.entrySet()) {
            ListTag items = new ListTag();
            for (Map.Entry<ItemKey, Integer> i : e.getValue().entrySet()) {
                if (i.getValue() > 0) {
                    CompoundTag item = new CompoundTag();
                    item.put("item", i.getKey().proto().copyWithCount(1).save(registries));
                    item.putInt("count", i.getValue());
                    items.add(item);
                }
            }
            if (!items.isEmpty()) {
                CompoundTag machine = new CompoundTag();
                machine.putLong("pos", e.getKey().asLong());
                machine.put("items", items);
                loaded.add(machine);
            }
        }
        tag.put("loaded", loaded);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        jobs.clear();
        ListTag list = tag.getList("jobs", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            ContractJob job = ContractJob.load(list.getCompound(i), registries);
            if (job != null) {
                jobs.add(job);
            }
        }
        nextJobId = Math.max(1, tag.getInt("nextJob"));
        orders.clear();
        ListTag orderList = tag.getList("orders", Tag.TAG_COMPOUND);
        for (int i = 0; i < orderList.size(); i++) {
            orders.add(com.tarbound.contract.CraftOrder.load(orderList.getCompound(i), registries));
        }
        nextOrderId = Math.max(1, tag.getInt("next_order"));
        loadedInto.clear();
        ListTag loaded = tag.getList("loaded", Tag.TAG_COMPOUND); // since 0.50; absent before, so nothing protected
        for (int m = 0; m < loaded.size(); m++) {
            CompoundTag machine = loaded.getCompound(m);
            Map<ItemKey, Integer> ledger = new java.util.HashMap<>();
            ListTag items = machine.getList("items", Tag.TAG_COMPOUND);
            for (int i = 0; i < items.size(); i++) {
                CompoundTag item = items.getCompound(i);
                ItemStack.parse(registries, item.getCompound("item"))
                        .ifPresent(stack -> ledger.merge(ItemKey.of(stack), item.getInt("count"), Integer::sum));
            }
            if (!ledger.isEmpty()) {
                loadedInto.put(BlockPos.of(machine.getLong("pos")), ledger);
            }
        }
        if (tag.hasUUID("radio_id")) {
            radioId = tag.getUUID("radio_id");
        }
        if (tag.contains("hungry") && !tag.contains("nextJob")) {
            hungry = tag.getBoolean("hungry"); // a client update
            return;
        }
        spirit = Math.max(0, tag.getInt("spirit"));
        energy = Math.max(0, tag.getInt("energy"));
        burnFromPool = !tag.contains("burn_from_pool") || tag.getBoolean("burn_from_pool");
        hungry = com.tarbound.spirit.Spirit.costs() && spirit <= 0;
    }
}
