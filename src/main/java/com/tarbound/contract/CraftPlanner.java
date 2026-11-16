package com.tarbound.contract;

import com.tarbound.network.FluidKey;
import com.tarbound.network.ItemKey;
import com.tarbound.network.LooseMatch;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * Plans a multi-step order: what comes out of storage, what gets crafted by which contract, and what's
 * missing. Pure bookkeeping on a copy of the pool's counts, so it runs on the client for the order
 * preview and again on the server when the order starts.
 * <p>
 * Bounded so no plan can stall a server: at most {@code maxDepth} levels, {@code maxNodes} lines, and a
 * fixed amount of work per plan. A plan that hits a limit marks what it couldn't plan as missing.
 */
public final class CraftPlanner {
    /** A contract someone could run, and the name to show for where it runs. */
    /**
     * A contract the plan may use: where it is, how many bound machines hold it, and how many of those aren't busy
     * with a job right now (between contracts that make the same thing equally well, the one with idle machines wins).
     */
    public record Source(ContractData contract, Component machine, int machines, int idle) {
        public Source(ContractData contract, Component machine) {
            this(contract, machine, 0, 0);
        }
    }

    /** One bound machine's contracts, and whether a job is running on it. */
    public record MachineContracts(Component name, List<ContractData> contracts, boolean busy) {}

    /** Every contract on these machines once, with how many machines hold it and how many are idle. */
    public static List<Source> gather(List<MachineContracts> machines) {
        java.util.Map<ContractData, int[]> counts = new java.util.LinkedHashMap<>();
        java.util.Map<ContractData, Component> names = new java.util.HashMap<>();
        for (MachineContracts m : machines) {
            java.util.Set<ContractData> here = new java.util.HashSet<>();
            for (ContractData c : m.contracts()) {
                if (c == null || !here.add(c)) {
                    continue;
                }
                int[] n = counts.computeIfAbsent(c, k -> new int[2]);
                n[0]++;
                if (!m.busy()) {
                    n[1]++;
                }
                names.putIfAbsent(c, m.name());
            }
        }
        List<Source> out = new ArrayList<>();
        counts.forEach((c, n) -> out.add(new Source(c, names.get(c), n[0], n[1])));
        return out;
    }

    /** One line of the plan: an item (or fluid), how much is needed, and where it comes from. */
    public static final class Node {
        /** What this line is: the most plentiful match, or once crafted, what the contract really makes. */
        public ItemStack item;
        public final FluidStack fluid;
        public final long needed;
        public long fromStock;
        public long crafted;
        public long missing;
        @Nullable
        public Source source;
        public int ops;
        public final int depth;
        public final List<Node> children = new ArrayList<>();

        Node(ItemStack item, FluidStack fluid, long needed, int depth) {
            this.item = item;
            this.fluid = fluid;
            this.needed = needed;
            this.depth = depth;
        }

        public boolean isFluid() {
            return !fluid.isEmpty();
        }
    }

    /** A finished plan: the tree, totals of what's missing, and every craft it needs (deepest first). */
    /**
     * @param claimItems  what the plan takes out of storage as it stands now, per exact item (what an order
     *                    sets aside in its Shade before it starts)
     * @param claimFluids the same for fluids, in mB
     */
    public record Plan(Node root, Map<Object, Long> missing, List<Step> steps, boolean truncated,
            Map<ItemKey, Long> claimItems, Map<FluidKey, Long> claimFluids) {
        public boolean complete() {
            return missing.isEmpty() && !truncated;
        }
    }

    /** One job to queue: a contract and how many operations. */
    public record Step(ContractData contract, int ops) {}

    private final List<Source> sources;
    private final Map<ItemKey, Long> stock;
    private final Map<FluidKey, Long> fluids;
    private final int maxDepth;
    private final int maxNodes;
    private int nodes;
    private int work;
    private boolean truncated;
    private final Map<Object, Long> missing = new LinkedHashMap<>();
    private final Map<ContractData, Integer> stepOps = new LinkedHashMap<>();
    private final Set<ContractData> visiting = new HashSet<>();
    /** What's really in storage (no planned outputs), and what the plan takes of it. */
    private final Map<ItemKey, Long> real;
    private final Map<FluidKey, Long> realFluids;
    private final Map<ItemKey, Long> claimItems = new LinkedHashMap<>();
    private final Map<FluidKey, Long> claimFluids = new LinkedHashMap<>();

    /** Limits for the client's preview (the server re-plans with its own config limits when the order starts). */
    public static final int CLIENT_DEPTH = 16;
    public static final int CLIENT_NODES = 512;

    /** Work units per plan (candidate checks and stock lookups): plenty for real trees, a wall for absurd ones. */
    private static final int WORK_LIMIT = 200_000;

    private CraftPlanner(List<Source> sources, Map<ItemKey, Long> stock, Map<FluidKey, Long> fluids, int maxDepth, int maxNodes) {
        this.sources = sources;
        this.stock = new HashMap<>(stock);
        this.fluids = new HashMap<>(fluids);
        this.real = new HashMap<>(stock);
        this.realFluids = new HashMap<>(fluids);
        this.maxDepth = maxDepth;
        this.maxNodes = maxNodes;
    }

    /**
     * Plans {@code ops} operations of {@code root}'s contract.
     *
     * @param stock  what the pool holds (with filled buckets it could make)
     * @param fluids the pool's fluids, in mB
     */
    public static Plan plan(Source root, int ops, List<Source> sources, Map<ItemKey, Long> stock, Map<FluidKey, Long> fluids,
            int maxDepth, int maxNodes) {
        CraftPlanner p = new CraftPlanner(sources, stock, fluids, maxDepth, maxNodes);
        ContractData rc = root.contract();
        Node node = rc.tracksFluid()
                ? new Node(ItemStack.EMPTY, rc.trackedFluid().copyWithAmount(1), (long) ops * rc.perOp(), 0)
                : new Node(rc.primaryOutput().copyWithCount(1), FluidStack.EMPTY, (long) ops * rc.perOp(), 0);
        node.crafted = node.needed;
        p.nodes = 1;
        p.craft(node, root, ops);
        // Steps were recorded as each craft finished planning, so the ones that feed others come first.
        List<Step> steps = new ArrayList<>();
        p.stepOps.forEach((c, n) -> steps.add(new Step(c, n)));
        return new Plan(node, p.missing, steps, p.truncated, p.claimItems, p.claimFluids);
    }

    private boolean overBudget() {
        if (nodes >= maxNodes || work >= WORK_LIMIT) {
            truncated = true;
            return true;
        }
        return false;
    }

    /** Runs {@code ops} of {@code source} for {@code node}: plans its inputs, banks its spare outputs. */
    private void craft(Node node, Source source, int ops) {
        ContractData c = source.contract();
        node.source = source;
        node.ops = ops;
        visiting.add(c);
        List<List<ItemStack>> inputs = c.inputsFor(stock);
        for (int i = 0; i < inputs.size(); i++) {
            List<ItemStack> alts = inputs.get(i);
            if (alts.isEmpty() || ContractData.staysInMachine(alts)) {
                continue; // a press or mold stays in the machine: nothing to plan per operation
            }
            // Handed back by every craft (a Blood Orb): one does for all the operations.
            long need = (long) Math.max(1, alts.get(0).getCount()) * (c.returnedInput(i) ? 1 : ops);
            node.children.add(planItem(LooseMatch.widenOne(alts, stock.keySet()), c.tagFor(i), need, node.depth + 1));
        }
        for (FluidStack f : c.fluidInputs()) {
            node.children.add(planFluid(f, (long) f.getAmount() * ops, node.depth + 1));
        }
        visiting.remove(c);
        stepOps.merge(c, ops, Integer::sum);
        // Everything the operations make beyond what this line needed goes back in the ledger for later lines.
        for (ItemStack o : c.outputs()) {
            stock.merge(ItemKey.of(o), (long) o.getCount() * ops, Long::sum);
        }
        for (FluidStack f : c.fluidOutputs()) {
            fluids.merge(FluidKey.of(f), (long) f.getAmount() * ops, Long::sum);
        }
        if (!node.isFluid()) {
            take(List.of(node.item), node.crafted, false);
        } else {
            fluids.merge(FluidKey.of(node.fluid), -node.crafted, Long::sum);
        }
    }

    /**
     * @param tag the input's tag, if it has one: contracts whose output is in it count as making it, even
     *            when that item isn't in the input's stored sample (a big tag like "any planks" only keeps
     *            {@link ContractData#MAX_ALTERNATIVES} examples)
     */
    private Node planItem(List<ItemStack> alts, @Nullable TagKey<Item> tag, long need, int depth) {
        nodes++;
        ItemStack shown = mostPlentiful(alts);
        Node node = new Node(shown.copyWithCount(1), FluidStack.EMPTY, need, depth);
        node.fromStock = take(alts, need, true);
        long rest = need - node.fromStock;
        if (rest <= 0) {
            return node;
        }
        Source best = depth >= maxDepth || overBudget() ? null : bestSource(alts, tag, null);
        if (best == null) {
            node.missing = rest;
            missing.merge(ItemKey.of(shown), rest, Long::sum);
            return node;
        }
        ItemStack made = madeOf(best.contract(), alts, tag);
        if (!made.isEmpty()) {
            node.item = made.copyWithCount(1); // show (and later take from the ledger) what's really made
        }
        long perOp = outputOf(best.contract(), alts, tag);
        int ops = (int) Math.min(Integer.MAX_VALUE, (rest + perOp - 1) / perOp);
        node.crafted = rest;
        craft(node, best, ops);
        node.crafted = Math.min(node.crafted, (long) ops * perOp);
        return node;
    }

    private Node planFluid(FluidStack f, long need, int depth) {
        nodes++;
        Node node = new Node(ItemStack.EMPTY, f.copyWithAmount(1), need, depth);
        FluidKey key = FluidKey.of(f);
        long have = Math.max(0, fluids.getOrDefault(key, 0L));
        node.fromStock = Math.min(have, need);
        fluids.put(key, have - node.fromStock);
        long realHave = Math.max(0, realFluids.getOrDefault(key, 0L));
        long fromReal = Math.min(realHave, node.fromStock);
        if (fromReal > 0) {
            realFluids.put(key, realHave - fromReal);
            claimFluids.merge(key, fromReal, Long::sum);
        }
        long rest = need - node.fromStock;
        if (rest <= 0) {
            return node;
        }
        Source best = depth >= maxDepth || overBudget() ? null : bestSource(List.of(), null, f);
        if (best == null) {
            node.missing = rest;
            missing.merge(key, rest, Long::sum);
            return node;
        }
        long perOp = 0;
        for (FluidStack o : best.contract().fluidOutputs()) {
            if (FluidStack.isSameFluidSameComponents(o, f)) {
                perOp += o.getAmount();
            }
        }
        perOp = Math.max(1, perOp);
        int ops = (int) Math.min(Integer.MAX_VALUE, (rest + perOp - 1) / perOp);
        node.crafted = rest;
        craft(node, best, ops);
        return node;
    }

    /**
     * Takes up to {@code need} from the ledger across the alternatives (most plentiful first). With
     * {@code claim}, the part that's really in storage (not a planned output) is noted as claimed.
     * @return how many
     */
    private long take(List<ItemStack> alts, long need, boolean claim) {
        long got = 0;
        List<ItemStack> order = new ArrayList<>(alts);
        order.sort((a, b) -> Long.compare(stock.getOrDefault(ItemKey.of(b), 0L), stock.getOrDefault(ItemKey.of(a), 0L)));
        for (ItemStack a : order) {
            work++;
            if (got >= need) {
                break;
            }
            ItemKey k = ItemKey.of(a);
            long have = Math.max(0, stock.getOrDefault(k, 0L));
            long t = Math.min(have, need - got);
            if (t > 0) {
                stock.put(k, have - t);
                got += t;
                if (claim) {
                    long realHave = Math.max(0, real.getOrDefault(k, 0L));
                    long fromReal = Math.min(realHave, t);
                    if (fromReal > 0) {
                        real.put(k, realHave - fromReal);
                        claimItems.merge(k, fromReal, Long::sum);
                    }
                }
            }
        }
        return got;
    }

    private ItemStack mostPlentiful(List<ItemStack> alts) {
        ItemStack best = alts.get(0);
        long n = -1;
        for (ItemStack a : alts) {
            long have = stock.getOrDefault(ItemKey.of(a), 0L);
            if (have > n) {
                n = have;
                best = a;
            }
        }
        return best;
    }

    /** Whether output {@code o} fills an input with these alternatives (or this tag). */
    private static boolean fills(ItemStack o, List<ItemStack> alts, @Nullable TagKey<Item> tag) {
        if (tag != null && o.is(tag)) {
            return true;
        }
        for (ItemStack a : alts) {
            if (ItemStack.isSameItemSameComponents(o, a)) {
                return true;
            }
        }
        return false;
    }

    /** The first of {@code c}'s outputs that fills the input, or empty. */
    private static ItemStack madeOf(ContractData c, List<ItemStack> alts, @Nullable TagKey<Item> tag) {
        for (ItemStack o : c.outputs()) {
            if (fills(o, alts, tag)) {
                return o;
            }
        }
        return ItemStack.EMPTY;
    }

    /** How many of the input one operation of {@code c} makes (at least 1). */
    private static long outputOf(ContractData c, List<ItemStack> alts, @Nullable TagKey<Item> tag) {
        long n = 0;
        for (ItemStack o : c.outputs()) {
            if (fills(o, alts, tag)) {
                n += o.getCount();
            }
        }
        return Math.max(1, n);
    }

    /**
     * The contract to make one of {@code alts} (or {@code fluid}) with: of those that make it (and aren't
     * already being planned above, so no loops), the one whose inputs storage covers best.
     */
    @Nullable
    private Source bestSource(List<ItemStack> alts, @Nullable TagKey<Item> tag, @Nullable FluidStack fluid) {
        Source best = null;
        double bestScore = -1;
        for (Source s : sources) {
            work++;
            ContractData c = s.contract();
            if (visiting.contains(c) || !makes(c, alts, tag, fluid)) {
                continue;
            }
            double covered = 0;
            int inputs = 0;
            for (List<ItemStack> in : c.inputs()) {
                if (ContractData.staysInMachine(in)) {
                    continue;
                }
                inputs++;
                for (ItemStack a : in) {
                    work++;
                    if (stock.getOrDefault(ItemKey.of(a), 0L) >= Math.max(1, a.getCount())) {
                        covered++;
                        break;
                    }
                }
            }
            for (FluidStack f : c.fluidInputs()) {
                inputs++;
                if (fluids.getOrDefault(FluidKey.of(f), 0L) >= f.getAmount()) {
                    covered++;
                }
            }
            double score = inputs == 0 ? 1 : covered / inputs;
            // Equal cover: the one with idle machines (then more machines) gets the work.
            boolean better = best == null || score > bestScore + 1e-9
                    || (Math.abs(score - bestScore) <= 1e-9 && (s.idle() > best.idle() || (s.idle() == best.idle() && s.machines() > best.machines())));
            if (better) {
                bestScore = score;
                best = s;
            }
        }
        return best;
    }

    private static boolean makes(ContractData c, List<ItemStack> alts, @Nullable TagKey<Item> tag, @Nullable FluidStack fluid) {
        if (fluid != null) {
            for (FluidStack o : c.fluidOutputs()) {
                if (FluidStack.isSameFluidSameComponents(o, fluid)) {
                    return true;
                }
            }
            return false;
        }
        return !madeOf(c, alts, tag).isEmpty();
    }

    /** The plan as display lines, depth first. */
    public static List<Node> lines(Plan plan) {
        List<Node> out = new ArrayList<>();
        walk(plan.root(), out);
        return out;
    }

    private static void walk(Node n, List<Node> out) {
        out.add(n);
        for (Node c : n.children) {
            walk(c, out);
        }
    }
}
