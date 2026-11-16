package com.tarbound.network;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * Loads one operation into a bound machine, all or nothing: every input into a slot that accepts it
 * (non-fuel slots first, so a log is smelted rather than burned), plus fuel if the machine burns
 * fuel and isn't spirit-fed. Used by recipe-viewer fills and by contract jobs.
 */
public final class MachineLoader {
    /** Fuel worth loading lasts at least one standard smelt (200 ticks); a single stick doesn't. */
    private static final int ONE_SMELT = 200;

    /** Where ingredients come from. */
    public interface Source {
        /** Counts of everything this source can supply. */
        Map<ItemKey, Long> snapshot();

        /** Take up to {@code amount} of {@code proto}; returns how many were taken. */
        int take(ItemStack proto, int amount);

        /** Somewhere for anything the machine refused at the last moment. */
        void giveBack(ItemStack stack);

        /** Where a press swapped out of a machine goes: storage, never an order's reserve. */
        default void returnToPool(ItemStack stack) {
            giveBack(stack);
        }

        /** Counts for loading these inputs: may add filled buckets the source can make on demand. */
        default Map<ItemKey, Long> snapshot(List<List<ItemStack>> inputs) {
            return snapshot();
        }

        /** mB of this fluid on hand. */
        default long fluidAmount(FluidStack fluid) {
            return 0;
        }

        /** Takes up to {@code amount} mB of {@code fluid}; returns how much was taken. */
        default int drainFluid(FluidStack fluid, int amount) {
            return 0;
        }

        /** Somewhere for fluid the machine refused at the last moment. */
        default void giveBackFluid(FluidStack fluid) {}
    }

    private record Target(IItemHandler handler, int slot, boolean fuel) {}

    private record Step(Target target, ItemStack proto, int count) {}

    /**
     * @return null on success, otherwise why nothing was loaded
     */
    @Nullable
    public static Component load(Level level, BoundInventory machine, List<List<ItemStack>> inputs, Source source) {
        return load(level, machine, inputs, List.of(), source);
    }

    /**
     * As {@link #load(Level, BoundInventory, List, Source)}, also pumping {@code fluids} straight into the
     * machine's tanks.
     */
    @Nullable
    public static Component load(Level level, BoundInventory machine, List<List<ItemStack>> inputs, List<FluidStack> fluids, Source source) {
        if (!com.tarbound.TarboundDebug.on()) {
            return loadInner(level, machine, inputs, fluids, source);
        }
        StringBuilder before = new StringBuilder();
        IFluidHandler tanks = fluids.isEmpty() ? null : MachineAccess.fluids(level, machine.pos());
        for (FluidStack f : fluids) {
            before.append(" | wants ").append(com.tarbound.TarboundDebug.fluid(f)).append(", pool has ").append(source.fluidAmount(f))
                    .append("mB; machine ").append(com.tarbound.TarboundDebug.tanks(tanks, f));
        }
        Component why = loadInner(level, machine, inputs, fluids, source);
        StringBuilder items = new StringBuilder();
        for (List<ItemStack> alts : inputs) {
            items.append(alts.isEmpty() ? "-" : alts.get(0).getCount() + "x" + net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(alts.get(0).getItem())
                    + (alts.size() > 1 ? "(+" + (alts.size() - 1) + " alts)" : "")).append(' ');
        }
        com.tarbound.TarboundDebug.log(level, machine.pos(), "load " + com.tarbound.TarboundDebug.block(level, machine.pos()),
                (why == null ? "OK" : "REFUSED: " + why.getString()) + " | items: " + items.toString().trim() + before);
        return why;
    }

    @Nullable
    private static Component loadInner(Level level, BoundInventory machine, List<List<ItemStack>> inputs, List<FluidStack> fluids, Source source) {
        Component machineName = machine.state().getBlock().getName();
        if (!machine.alive()) {
            return Component.translatable("gui.tarbound.fill.no_machine"); // broken since this tick's scan
        }
        List<Target> targets = new ArrayList<>();
        List<BoundInventory.SlotRef> refs = machine.slots();
        for (int i = 0; i < refs.size(); i++) {
            BoundInventory.SlotRef r = refs.get(i);
            targets.add(new Target(r.handler(), r.slot(), machine.role(i) == BoundInventory.Role.FUEL));
        }
        Map<ItemKey, Long> available = source.snapshot(inputs);
        inputs = LooseMatch.widen(inputs, available.keySet()); // used tools count as the plain ones a recipe lists

        // Fluids: the machine must take each whole amount, and the source must have it.
        IFluidHandler tanks = null;
        if (!fluids.isEmpty()) {
            tanks = MachineAccess.fluids(level, machine.pos());
            if (tanks == null) {
                return Component.translatable("gui.tarbound.fill.no_tanks", machineName);
            }
            Map<FluidKey, Long> needed = new java.util.HashMap<>();
            for (FluidStack f : fluids) {
                needed.merge(FluidKey.of(f), (long) f.getAmount(), Long::sum);
            }
            for (Map.Entry<FluidKey, Long> e : needed.entrySet()) {
                FluidStack f = e.getKey().proto();
                if (source.fluidAmount(f) < e.getValue()) {
                    return Component.translatable("gui.tarbound.fill.missing", f.getHoverName());
                }
                int amount = (int) Math.min(Integer.MAX_VALUE, e.getValue());
                if (!fits(tanks, f, amount)) {
                    return Component.translatable("gui.tarbound.fill.refused", machineName, f.getHoverName());
                }
            }
        }

        List<Step> plan = new ArrayList<>();
        Set<Target> used = new HashSet<>();
        // Presses, molds and dies first, so they claim their slot before anything else could: one is loaded only
        // if the machine hasn't one already, and it stays there. Another contract's press left in the slot is
        // swapped out for this one, but only while the machine is otherwise empty (never mid-job).
        List<List<ItemStack>> consumed = new ArrayList<>();
        for (List<ItemStack> listed : inputs) {
            if (!com.tarbound.contract.ContractData.staysInMachine(listed)) {
                consumed.add(listed);
                continue;
            }
            if (holdsAny(refs, listed)) {
                continue;
            }
            Step step = keptStep(targets, used, listed, available);
            if (step == null && swapOutKept(refs, listed, source)) {
                step = keptStep(targets, used, listed, available);
            }
            if (step == null) {
                ItemStack shown = listed.get(0);
                return available.getOrDefault(ItemKey.of(shown), 0L) > 0 || listed.stream().anyMatch(a -> available.getOrDefault(ItemKey.of(a), 0L) > 0)
                        ? Component.translatable("gui.tarbound.fill.refused", machineName, shown.getHoverName())
                        : Component.translatable("gui.tarbound.fill.missing", shown.getHoverName());
            }
            used.add(step.target());
            available.merge(ItemKey.of(step.proto()), -1L, Long::sum);
            plan.add(step);
        }
        for (List<ItemStack> listed : consumed) {
            // Most plentiful acceptable item first, so tags draw on what there's most of.
            List<ItemStack> alts = new ArrayList<>(listed);
            // ...but enchanted, renamed or filled items only once nothing ordinary is left.
            alts.sort(Comparator.comparing((ItemStack a) -> LooseMatch.isPrecious(a))
                    .thenComparing(Comparator.comparingLong((ItemStack a) -> available.getOrDefault(ItemKey.of(a), 0L)).reversed()));
            Step step = null;
            boolean anyAvailable = false;
            for (int pass = 0; pass < 2 && step == null; pass++) {
                boolean fuelPass = pass == 1;
                for (ItemStack alt : alts) {
                    int count = Math.max(1, alt.getCount());
                    if (available.getOrDefault(ItemKey.of(alt), 0L) < count) {
                        continue;
                    }
                    anyAvailable = true;
                    for (Target t : targets) {
                        if (t.fuel() == fuelPass && !used.contains(t)
                                && (t.handler().insertItem(t.slot(), alt.copyWithCount(count), true).isEmpty()
                                        || acceptsInOrder(targets, used, t, alt.copyWithCount(count)))) {
                            step = new Step(t, alt.copyWithCount(1), count);
                            break;
                        }
                    }
                    if (step != null) {
                        break;
                    }
                }
            }
            if (step == null) {
                ItemStack shown = listed.isEmpty() ? ItemStack.EMPTY : listed.get(0);
                return anyAvailable
                        ? Component.translatable("gui.tarbound.fill.refused", machineName, shown.getHoverName())
                        : Component.translatable("gui.tarbound.fill.missing", shown.getHoverName());
            }
            used.add(step.target());
            available.merge(ItemKey.of(step.proto()), (long) -step.count(), Long::sum);
            plan.add(step);
        }

        // Fuel: none for spirit-fed furnaces; otherwise one piece that lasts a whole smelt, if the
        // machine has an empty fuel slot and isn't already lit.
        BlockEntity be = level.getBlockEntity(machine.pos());
        boolean spiritFed = be != null && SpiritFire.feedsNow(level, machine.pos(), be);
        boolean lit = machine.state().hasProperty(BlockStateProperties.LIT) && machine.state().getValue(BlockStateProperties.LIT);
        if (!spiritFed && !lit) {
            for (Target t : targets) {
                if (!t.fuel() || used.contains(t) || !t.handler().getStackInSlot(t.slot()).isEmpty()) {
                    continue;
                }
                ItemStack fuel = pickFuel(available, t);
                if (!fuel.isEmpty()) {
                    used.add(t);
                    plan.add(new Step(t, fuel, 1));
                }
                break;
            }
        }

        // Take everything first. If any input comes up short (the counts were off), put it all back
        // and load nothing: a half-loaded operation would count as pushed and never pay out.
        int[] got = new int[plan.size()];
        for (int i = 0; i < plan.size(); i++) {
            Step step = plan.get(i);
            got[i] = source.take(step.proto(), step.count());
            if (got[i] < step.count()) {
                for (int j = 0; j <= i; j++) {
                    if (got[j] > 0) {
                        source.giveBack(plan.get(j).proto().copyWithCount(got[j]));
                    }
                }
                return Component.translatable("gui.tarbound.fill.missing", step.proto().getHoverName());
            }
        }
        // Then the fluids, all or nothing as well.
        int[] drained = new int[fluids.size()];
        for (int i = 0; i < fluids.size(); i++) {
            FluidStack f = fluids.get(i);
            drained[i] = source.drainFluid(f, f.getAmount());
            if (drained[i] < f.getAmount()) {
                for (int j = 0; j <= i; j++) {
                    if (drained[j] > 0) {
                        source.giveBackFluid(fluids.get(j).copyWithAmount(drained[j]));
                    }
                }
                for (int j = 0; j < plan.size(); j++) {
                    source.giveBack(plan.get(j).proto().copyWithCount(got[j]));
                }
                return Component.translatable("gui.tarbound.fill.missing", f.getHoverName());
            }
        }
        for (int i = 0; i < fluids.size(); i++) {
            int in = fillAll(tanks, fluids.get(i).copyWithAmount(drained[i]));
            if (in < drained[i]) {
                source.giveBackFluid(fluids.get(i).copyWithAmount(drained[i] - in));
            }
        }
        for (int i = 0; i < plan.size(); i++) {
            Step step = plan.get(i);
            ItemStack rest = step.target().handler().insertItem(step.target().slot(), step.proto().copyWithCount(got[i]), false);
            if (!rest.isEmpty()) {
                rest = intoAnySlot(targets, step.target(), rest); // e.g. a basin wanting a second lot of an item in its first slot
            }
            if (!rest.isEmpty()) {
                source.giveBack(rest);
            }
        }
        return null;
    }

    /**
     * Some inventories only take an item into their <em>first</em> empty slot (Create's basin: one slot per item kind,
     * filled in order), so a plan that puts the second input in slot 1 is refused while slot 0 is still empty, even
     * though it'll be fine once the first input is in. Seen from here: {@code t} is empty, every empty slot of the same
     * inventory before it is already claimed by this plan (so the plan fills them in order), and the inventory's real
     * first empty slot would take {@code stack}.
     */
    private static boolean acceptsInOrder(List<Target> targets, Set<Target> used, Target t, ItemStack stack) {
        if (!t.handler().getStackInSlot(t.slot()).isEmpty()) {
            return false;
        }
        Target firstFree = null;
        for (Target o : targets) {
            if (o.handler() != t.handler() || o.slot() >= t.slot() || !o.handler().getStackInSlot(o.slot()).isEmpty()) {
                continue;
            }
            if (!used.contains(o)) {
                return false; // an earlier empty slot the plan doesn't fill: the order wouldn't hold
            }
            if (firstFree == null || o.slot() < firstFree.slot()) {
                firstFree = o;
            }
        }
        return firstFree != null && firstFree.handler().insertItem(firstFree.slot(), stack, true).isEmpty();
    }

    /** What a planned slot refused at the last moment, offered to the machine's other (non-fuel) slots on the same inventory. */
    private static ItemStack intoAnySlot(List<Target> targets, Target planned, ItemStack stack) {
        ItemStack rest = stack;
        for (int pass = 0; pass < 2 && !rest.isEmpty(); pass++) {
            for (Target o : targets) {
                if (rest.isEmpty()) {
                    break;
                }
                if (o.fuel() || o.handler() != planned.handler()) {
                    continue;
                }
                boolean same = ItemStack.isSameItemSameComponents(o.handler().getStackInSlot(o.slot()), rest);
                if (pass == 0 ? same : !same) { // slots already holding it first
                    rest = o.handler().insertItem(o.slot(), rest, false);
                }
            }
        }
        return rest;
    }

    /**
     * Whether the machine's tanks will take all of {@code amount}. Some tanks only take so much per call (PowerfulJS's
     * scripted tanks have a max receive rate, as do many tech-mod tanks), so a simulated fill that takes some but not
     * all still counts if the tanks have the room for the rest; {@link #fillAll} then fills in several calls.
     */
    private static boolean fits(IFluidHandler tanks, FluidStack f, int amount) {
        int sim = tanks.fill(f.copyWithAmount(amount), IFluidHandler.FluidAction.SIMULATE);
        if (sim >= amount) {
            return true;
        }
        if (sim <= 0) {
            return false;
        }
        long room = 0;
        for (int t = 0; t < tanks.getTanks(); t++) {
            FluidStack in = tanks.getFluidInTank(t);
            if (in.isEmpty() ? tanks.isFluidValid(t, f) : FluidStack.isSameFluidSameComponents(in, f)) {
                room += Math.max(0, tanks.getTankCapacity(t) - in.getAmount());
            }
        }
        return room >= amount;
    }

    /** Fills all of {@code f}, a call at a time while the tanks keep taking some; returns how much went in. */
    private static int fillAll(IFluidHandler tanks, FluidStack f) {
        int done = 0;
        for (int calls = 0; calls < 64 && done < f.getAmount(); calls++) {
            int in = tanks.fill(f.copyWithAmount(f.getAmount() - done), IFluidHandler.FluidAction.EXECUTE);
            if (in <= 0) {
                break;
            }
            done += in;
        }
        return done;
    }

    /** Whether any of the machine's slots already holds one of {@code alts}. */
    private static boolean holdsAny(List<BoundInventory.SlotRef> refs, List<ItemStack> alts) {
        for (BoundInventory.SlotRef r : refs) {
            ItemStack in = r.handler().getStackInSlot(r.slot());
            if (in.isEmpty()) {
                continue;
            }
            for (ItemStack a : alts) {
                if (ItemStack.isSameItemSameComponents(in, a)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** One of a kept input, into the first free slot that takes it (non-fuel slots only), if the source has one. */
    @Nullable
    private static Step keptStep(List<Target> targets, Set<Target> used, List<ItemStack> alts, Map<ItemKey, Long> available) {
        for (ItemStack alt : alts) {
            if (available.getOrDefault(ItemKey.of(alt), 0L) < 1) {
                continue;
            }
            for (Target t : targets) {
                if (!t.fuel() && !used.contains(t) && t.handler().insertItem(t.slot(), alt.copyWithCount(1), true).isEmpty()) {
                    return new Step(t, alt.copyWithCount(1), 1);
                }
            }
        }
        return null;
    }

    /**
     * Takes another contract's press (or mold) out of the machine so this contract's can go in, only if the machine
     * holds nothing else at all: nothing being worked on, nothing finished waiting to drain. @return whether one moved
     */
    private static boolean swapOutKept(List<BoundInventory.SlotRef> refs, List<ItemStack> wanted, Source source) {
        BoundInventory.SlotRef other = null;
        for (BoundInventory.SlotRef r : refs) {
            ItemStack in = r.handler().getStackInSlot(r.slot());
            if (in.isEmpty()) {
                continue;
            }
            if (!in.is(com.tarbound.registry.ModTags.STAYS_IN_MACHINE)) {
                return false; // busy: something's in there besides presses
            }
            if (other == null) {
                other = r;
            }
        }
        if (other == null) {
            return false;
        }
        ItemStack out = other.handler().extractItem(other.slot(), 64, false);
        if (out.isEmpty()) {
            return false;
        }
        source.returnToPool(out);
        return true;
    }

    /**
     * The fuel at hand that the slot takes and that lasts at least one smelt, preferring the shortest
     * such burn (least waste). If nothing lasts a smelt, the longest-burning fuel there is. Only items
     * in {@code tarbound:pool_fuel} are considered (so jobs never burn tools or furniture), and only the
     * tag's own members are looked up, rather than walking the whole pool.
     */
    private static ItemStack pickFuel(Map<ItemKey, Long> available, Target target) {
        ItemStack best = ItemStack.EMPTY;
        int bestBurn = 0;
        boolean bestLasts = false;
        for (net.minecraft.core.Holder<net.minecraft.world.item.Item> holder : net.minecraft.core.registries.BuiltInRegistries.ITEM
                .getTagOrEmpty(com.tarbound.registry.ModTags.POOL_FUEL)) {
            ItemStack proto = new ItemStack(holder);
            if (available.getOrDefault(ItemKey.of(proto), 0L) <= 0) {
                continue;
            }
            int burn = proto.getBurnTime(null);
            if (burn <= 0 || !target.handler().insertItem(target.slot(), proto.copyWithCount(1), true).isEmpty()) {
                continue;
            }
            boolean lasts = burn >= ONE_SMELT;
            boolean better = best.isEmpty()
                    || (lasts && !bestLasts)
                    || (lasts && burn < bestBurn)
                    || (!lasts && !bestLasts && burn > bestBurn);
            if (better) {
                best = proto;
                bestBurn = burn;
                bestLasts = lasts;
            }
        }
        return best.isEmpty() ? ItemStack.EMPTY : best.copyWithCount(1);
    }

    /** A source that draws only from the tar pool (contract jobs). Refused items go back to the pool, or drop. */
    /** {@link #pool} for jobs and orders: a Weeping Seep on the network makes up what the pool is short of. */
    public static Source forJobs(Level level, TarNetwork net, net.minecraft.core.BlockPos dropAt) {
        Source pool = pool(level, net, dropAt);
        if (!net.hasWeepingSeep()) {
            return pool;
        }
        return new Source() {
            @Override
            public Map<ItemKey, Long> snapshot() {
                return pool.snapshot();
            }

            @Override
            public int take(ItemStack proto, int amount) {
                return net.extractForJob(proto, amount).getCount();
            }

            @Override
            public void giveBack(ItemStack stack) {
                pool.giveBack(stack);
            }

            @Override
            public Map<ItemKey, Long> snapshot(List<List<ItemStack>> inputs) {
                return net.countForJobs(inputs);
            }

            @Override
            public long fluidAmount(FluidStack fluid) {
                return pool.fluidAmount(fluid);
            }

            @Override
            public int drainFluid(FluidStack fluid, int amount) {
                return pool.drainFluid(fluid, amount);
            }

            @Override
            public void giveBackFluid(FluidStack fluid) {
                pool.giveBackFluid(fluid);
            }
        };
    }

    public static Source pool(Level level, TarNetwork net, net.minecraft.core.BlockPos dropAt) {
        return new Source() {
            @Override
            public Map<ItemKey, Long> snapshot() {
                return new java.util.HashMap<>(net.countContents());
            }

            @Override
            public int take(ItemStack proto, int amount) {
                return net.extract(proto, amount).getCount();
            }

            @Override
            public void giveBack(ItemStack stack) {
                ItemStack rest = net.insert(stack);
                if (!rest.isEmpty()) {
                    Block.popResource(level, dropAt, rest);
                }
            }

            @Override
            public Map<ItemKey, Long> snapshot(List<List<ItemStack>> inputs) {
                return net.countWithFillsFor(inputs);
            }

            @Override
            public long fluidAmount(FluidStack fluid) {
                return net.fluidAmount(fluid);
            }

            @Override
            public int drainFluid(FluidStack fluid, int amount) {
                return net.drainFluid(fluid, amount, false);
            }

            @Override
            public void giveBackFluid(FluidStack fluid) {
                net.insertFluid(fluid, false); // nowhere for spilt fluid to go; the tank it came from had room a moment ago
            }
        };
    }

    private MachineLoader() {}
}
