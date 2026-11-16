package com.tarbound.contract;

import com.tarbound.registry.ModDataComponents;
import com.tarbound.registry.ModItems;
import javax.annotation.Nullable;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

/**
 * A single-step job: run one contract {@code totalOps} times. The radio pushes operations into any
 * bound machine whose reliquary holds the contract, and counts the primary output as it drains back into
 * the pool (like AE2's crafting CPU, it never watches the machine itself). A fluid-only contract's job counts
 * its fluid instead, in mB, as it arrives in the pool's tanks.
 */
public final class ContractJob {
    public enum Status {
        RUNNING,
        WAITING_INPUTS,
        NO_MACHINE,
        MACHINE_BUSY,
        FINISHING,
        /** A crafting station had the inputs but the pool had no room for the result. */
        POOL_FULL,
        /** Everything is loaded but outputs stopped arriving; the job closes itself soon. */
        STALLED,
        /** The radio is out of Spirit: feed it candles. */
        HUNGRY,
        /** A fluid job's machine is done but the pool's tanks have no room for what it made. */
        NO_TANK_ROOM
    }

    private final int id;
    private final ContractData contract;
    private int totalOps;
    /** The multi-step order this job is part of (0 = none). */
    private int orderId;
    /** A sub-craft of an order: what it makes goes to the order's Shade, not loose in the pool. */
    private boolean deliverToShade;
    /** Operations added past the plan because outputs came up short (chance outputs). */
    private int extraOps;
    private int pushedOps;
    private long received;
    private Status status = Status.RUNNING;
    /** Game time {@link #received} last changed, and the value it changed to (not saved). */
    private long progressAt = -1;
    private long progressMark = -1;
    /** Closed for lack of progress, though not every output arrived. */
    private boolean abandoned;
    /** Who asked for a single job (told when it's done); orders tell their own requester. Saved as {@code requester}. */
    @Nullable
    private java.util.UUID requester;

    public ContractJob(int id, ContractData contract, int totalOps) {
        this(id, contract, totalOps, 0, 0);
    }

    private ContractJob(int id, ContractData contract, int totalOps, int pushedOps, long received) {
        this.id = id;
        this.contract = contract;
        this.totalOps = totalOps;
        this.pushedOps = pushedOps;
        this.received = received;
    }

    public int id() {
        return id;
    }

    public ContractData contract() {
        return contract;
    }

    public int totalOps() {
        return totalOps;
    }

    public int pushedOps() {
        return pushedOps;
    }

    public long received() {
        return received;
    }

    public int orderId() {
        return orderId;
    }

    public boolean deliverToShade() {
        return deliverToShade;
    }

    /** Makes this job part of order {@code order}; {@code toShade} for the order's sub-crafts. */
    public ContractJob forOrder(int order, boolean toShade) {
        this.orderId = order;
        this.deliverToShade = toShade;
        return this;
    }

    public int extraOps() {
        return extraOps;
    }

    /** One more operation, for a sub-craft whose outputs came up short. */
    public void addOp() {
        totalOps++;
        extraOps++;
        resetStall();
    }

    /** Starts the no-progress clock over. */
    public void resetStall() {
        progressAt = -1;
    }

    public Status status() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
        if (status != Status.WAITING_INPUTS) {
            waitItem = ItemStack.EMPTY;
            waitFluid = net.neoforged.neoforge.fluids.FluidStack.EMPTY;
            waitFeeder = 0;
        }
        if (status != Status.MACHINE_BUSY) {
            refusal = null;
        }
    }

    // What the job is held up by, for the radio's job list (worked out each pass, not saved).
    private ItemStack waitItem = ItemStack.EMPTY;
    private net.neoforged.neoforge.fluids.FluidStack waitFluid = net.neoforged.neoforge.fluids.FluidStack.EMPTY;
    private int waitFeeder;
    @Nullable
    private net.minecraft.network.chat.Component refusal;

    /** Waiting for inputs: the first one storage is short of ({@code count} = how many short for one operation). */
    public void waitingFor(ItemStack item, net.neoforged.neoforge.fluids.FluidStack fluid, int feederJob) {
        waitItem = item;
        waitFluid = fluid;
        waitFeeder = feederJob;
    }

    /** The machine said no: its loader's reason (it's full, it won't take that item...). */
    public void refusedBecause(@Nullable net.minecraft.network.chat.Component why) {
        refusal = why;
    }

    public ItemStack waitItem() {
        return waitItem;
    }

    public net.neoforged.neoforge.fluids.FluidStack waitFluid() {
        return waitFluid;
    }

    /** Id of another job on this radio making what this one waits for, or 0. */
    public int waitFeeder() {
        return waitFeeder;
    }

    @Nullable
    public net.minecraft.network.chat.Component refusal() {
        return refusal;
    }

    public long expected() {
        return (long) totalOps * contract.perOp();
    }

    public boolean allPushed() {
        return pushedOps >= totalOps;
    }

    public void pushed() {
        pushedOps++;
    }

    /** Credit drained primary outputs (items, or mB for a fluid job); returns how many this job took. */
    public long credit(long amount) {
        long take = Math.min(amount, Math.max(0, expected() - received));
        received += take;
        return take;
    }

    /**
     * Finished once everything asked for has arrived, even with operations still unloaded: recipes with chance
     * outputs (washing gravel for nuggets) often give more than one operation's worth, and the leftover operations
     * would otherwise sit waiting for inputs nobody needs any more.
     */
    public boolean done() {
        return abandoned || received >= expected();
    }

    /**
     * Ticks since an output last arrived, counted from the first time this is asked. Chance-based
     * outputs, items taken out as they arrive, or outputs a machine never gives up can leave a job
     * short forever; the radio closes it once this passes the server's {@code jobStallSeconds}.
     */
    public long stalledFor(long now) {
        if (progressAt < 0 || progressMark != received) {
            progressMark = received;
            progressAt = now;
        }
        return now - progressAt;
    }

    public void abandon() {
        abandoned = true;
    }

    public boolean abandoned() {
        return abandoned;
    }

    @Nullable
    public java.util.UUID requester() {
        return requester;
    }

    public ContractJob requestedBy(@Nullable java.util.UUID who) {
        requester = who;
        return this;
    }

    public CompoundTag save(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("id", id);
        tag.putInt("total", totalOps);
        tag.putInt("pushed", pushedOps);
        tag.putLong("received", received);
        if (orderId != 0) {
            tag.putInt("order", orderId);
            tag.putBoolean("to_shade", deliverToShade);
            tag.putInt("extra", extraOps);
        }
        if (requester != null) {
            tag.putUUID("requester", requester);
        }
        ItemStack carrier = new ItemStack(ModItems.SPIRIT_CONTRACT.get());
        carrier.set(ModDataComponents.CONTRACT.get(), contract);
        tag.put("contract", carrier.save(registries));
        return tag;
    }

    @Nullable
    public static ContractJob load(CompoundTag tag, HolderLookup.Provider registries) {
        ItemStack carrier = ItemStack.parseOptional(registries, tag.getCompound("contract"));
        ContractData contract = carrier.get(ModDataComponents.CONTRACT.get());
        if (contract == null || !contract.isValid()) {
            return null;
        }
        ContractJob job = new ContractJob(tag.getInt("id"), contract, tag.getInt("total"), tag.getInt("pushed"), tag.getLong("received"));
        job.orderId = tag.getInt("order");
        job.deliverToShade = tag.getBoolean("to_shade");
        job.extraOps = tag.getInt("extra");
        job.requester = tag.hasUUID("requester") ? tag.getUUID("requester") : null;
        return job;
    }
}
