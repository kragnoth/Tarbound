package com.tarbound.contract;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * The contract being laid out at the Scribing Lectern, before it's written: ghost items only, nothing real is
 * held. Like an AE2 pattern terminal it has two modes. Crafting: a 3x3 grid whose result is whatever crafting
 * recipe it matches. Processing: up to 9 inputs and 6 outputs (items or fluids, with amounts) for a machine.
 *
 * <p>{@link #substitutes} decides how strict the written contract is: on, an input may be anything its recipe
 * accepts (any planks, any log); off, only the exact items laid out.
 */
public class ContractDraft {
    public static final int GRID = 9;
    public static final int INPUTS = 9;
    public static final int OUTPUTS = 6;

    /** One ghost slot: an item (with what the recipe would accept instead, if known) or a fluid. */
    public record Ghost(ItemStack item, List<ItemStack> alternatives, FluidStack fluid) {
        public static final Ghost EMPTY = new Ghost(ItemStack.EMPTY, List.of(), FluidStack.EMPTY);

        public static Ghost of(ItemStack item) {
            return item.isEmpty() ? EMPTY : new Ghost(item.copy(), List.of(), FluidStack.EMPTY);
        }

        public static Ghost of(ItemStack item, List<ItemStack> alternatives) {
            return item.isEmpty() ? EMPTY : new Ghost(item.copy(), List.copyOf(alternatives), FluidStack.EMPTY);
        }

        public static Ghost of(FluidStack fluid) {
            return fluid.isEmpty() ? EMPTY : new Ghost(ItemStack.EMPTY, List.of(), fluid.copy());
        }

        public boolean isEmpty() {
            return item.isEmpty() && fluid.isEmpty();
        }

        /** The same ghost with a different amount (items capped at a stack, fluids at 64 buckets). */
        public Ghost withAmount(int amount) {
            if (!item.isEmpty()) {
                int n = Math.max(1, Math.min(amount, item.getMaxStackSize()));
                return new Ghost(item.copyWithCount(n), alternatives.stream().map(a -> a.copyWithCount(n)).toList(), fluid);
            }
            if (!fluid.isEmpty()) {
                return new Ghost(item, alternatives, fluid.copyWithAmount(Math.max(1, Math.min(amount, 64_000))));
            }
            return this;
        }

        public int amount() {
            return !item.isEmpty() ? item.getCount() : fluid.getAmount();
        }
    }

    public boolean processing;
    public boolean substitutes = true;
    public final ItemStack[] grid = new ItemStack[GRID];
    public final Ghost[] inputs = new Ghost[INPUTS];
    public final Ghost[] outputs = new Ghost[OUTPUTS];
    /** Bumped on every change, so the screen knows when to look up the crafting result again. */
    private int version;

    public ContractDraft() {
        clear();
    }

    public int version() {
        return version;
    }

    public void changed() {
        version++;
    }

    public void clear() {
        Arrays.fill(grid, ItemStack.EMPTY);
        Arrays.fill(inputs, Ghost.EMPTY);
        Arrays.fill(outputs, Ghost.EMPTY);
        changed();
    }

    public boolean isEmpty() {
        return Arrays.stream(grid).allMatch(ItemStack::isEmpty) && Arrays.stream(inputs).allMatch(Ghost::isEmpty)
                && Arrays.stream(outputs).allMatch(Ghost::isEmpty);
    }

    /** A layout as it stood, for going back to it. */
    private record Snapshot(boolean processing, boolean substitutes, ItemStack[] grid, Ghost[] inputs, Ghost[] outputs) {}

    /**
     * Layouts replaced by a recipe from JEI or EMI, newest last: building a chain, you look up an ingredient's recipe
     * from the grid, write it, and step back to the recipe you came from. Client side only; not saved.
     */
    private final java.util.ArrayDeque<Snapshot> history = new java.util.ArrayDeque<>();
    private static final int HISTORY = 16;

    /** Keeps the current layout to come back to (nothing if it's empty). */
    private void remember() {
        if (isEmpty()) {
            return;
        }
        history.addLast(new Snapshot(processing, substitutes, Arrays.stream(grid).map(ItemStack::copy).toArray(ItemStack[]::new),
                inputs.clone(), outputs.clone()));
        while (history.size() > HISTORY) {
            history.removeFirst();
        }
    }

    public boolean canGoBack() {
        return !history.isEmpty();
    }

    /** Back to the layout before the last recipe filled in. */
    public void back() {
        Snapshot s = history.pollLast();
        if (s == null) {
            return;
        }
        processing = s.processing();
        substitutes = s.substitutes();
        System.arraycopy(s.grid(), 0, grid, 0, GRID);
        System.arraycopy(s.inputs(), 0, inputs, 0, INPUTS);
        System.arraycopy(s.outputs(), 0, outputs, 0, OUTPUTS);
        changed();
    }

    /** Lay a crafting recipe out in the grid, the way it sits in a crafting table. */
    public void fillCrafting(CraftingRecipe recipe) {
        remember();
        clear();
        processing = false;
        List<Ingredient> ingredients = recipe.getIngredients();
        int width = recipe instanceof ShapedRecipe shaped ? shaped.getWidth() : 3;
        for (int i = 0; i < ingredients.size() && i < GRID; i++) {
            ItemStack[] items = ingredients.get(i).getItems();
            int slot = recipe instanceof ShapedRecipe ? (i / width) * 3 + i % width : i;
            if (items.length > 0 && slot < GRID) {
                grid[slot] = items[0].copyWithCount(1);
            }
        }
        changed();
    }

    /** Fill the processing slots (extra inputs or outputs beyond the slots are dropped). */
    /**
     * Recipes with more input slots than a contract has (a Mechanical Crafter's 5x5 or 9x9 pattern: one slot per grid
     * cell) are folded so each distinct ingredient is one input with a count (six stick cells become 6 sticks).
     * Recipes that already fit are left slot by slot, since some machines want each input in its own slot.
     *
     * @return the inputs to write, or null if even folded they have more distinct ingredients than {@link #INPUTS}
     */
    @javax.annotation.Nullable
    public static List<Ghost> fitInputs(List<Ghost> in) {
        if (in.size() <= INPUTS) {
            return in;
        }
        List<Ghost> merged = new ArrayList<>();
        List<Integer> counts = new ArrayList<>();
        outer:
        for (Ghost g : in) {
            for (int i = 0; i < merged.size(); i++) {
                Ghost m = merged.get(i);
                if (m.fluid().isEmpty() && g.fluid().isEmpty() && ItemStack.isSameItemSameComponents(m.item(), g.item())
                        && sameItems(m.alternatives(), g.alternatives())) {
                    counts.set(i, counts.get(i) + Math.max(1, g.item().getCount()));
                    continue outer;
                }
            }
            merged.add(g);
            counts.add(g.fluid().isEmpty() ? Math.max(1, g.item().getCount()) : g.fluid().getAmount());
        }
        if (merged.size() > INPUTS) {
            return null;
        }
        List<Ghost> out = new ArrayList<>();
        for (int i = 0; i < merged.size(); i++) {
            Ghost m = merged.get(i);
            if (!m.fluid().isEmpty()) {
                out.add(m);
                continue;
            }
            int n = Math.min(99, counts.get(i)); // an item stack in a contract holds at most 99
            out.add(new Ghost(m.item().copyWithCount(n), m.alternatives().stream().map(a -> a.copyWithCount(n)).toList(), FluidStack.EMPTY));
        }
        return out;
    }

    private static boolean sameItems(List<ItemStack> a, List<ItemStack> b) {
        if (a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            if (!ItemStack.isSameItemSameComponents(a.get(i), b.get(i))) {
                return false;
            }
        }
        return true;
    }

    public void fillProcessing(List<Ghost> in, List<Ghost> out) {
        remember();
        clear();
        processing = true;
        for (int i = 0; i < in.size() && i < INPUTS; i++) {
            inputs[i] = in.get(i);
        }
        for (int i = 0; i < out.size() && i < OUTPUTS; i++) {
            outputs[i] = out.get(i);
        }
        changed();
    }

    public List<ItemStack> gridList() {
        return Arrays.stream(grid).map(ItemStack::copy).toList();
    }

    /** The processing contract the slots describe (may be invalid: nothing in, or nothing out). */
    public ContractData processingContract() {
        List<List<ItemStack>> in = new ArrayList<>();
        List<FluidStack> fluidIn = new ArrayList<>();
        for (Ghost g : inputs) {
            if (!g.item().isEmpty()) {
                List<ItemStack> alts = new ArrayList<>();
                alts.add(g.item());
                if (substitutes) {
                    for (ItemStack a : g.alternatives()) {
                        if (!ItemStack.isSameItemSameComponents(a, g.item())) {
                            alts.add(a.copyWithCount(g.item().getCount()));
                        }
                    }
                }
                in.add(alts);
            } else if (!g.fluid().isEmpty()) {
                fluidIn.add(g.fluid());
            }
        }
        List<ItemStack> out = new ArrayList<>();
        List<FluidStack> fluidOut = new ArrayList<>();
        for (Ghost g : outputs) {
            if (!g.item().isEmpty()) {
                out.add(g.item());
            } else if (!g.fluid().isEmpty()) {
                fluidOut.add(g.fluid());
            }
        }
        return ContractData.of(in, out, fluidIn, fluidOut);
    }

    /**
     * A crafting contract that takes only the exact items in the grid: one input per different item, its count
     * the number of grid slots holding it.
     */
    public static ContractData exactCrafting(List<ItemStack> grid, ItemStack result) {
        List<ItemStack> seen = new ArrayList<>();
        List<Integer> uses = new ArrayList<>();
        for (ItemStack s : grid) {
            if (s.isEmpty()) {
                continue;
            }
            int at = -1;
            for (int i = 0; i < seen.size(); i++) {
                if (ItemStack.isSameItemSameComponents(seen.get(i), s)) {
                    at = i;
                    break;
                }
            }
            if (at < 0) {
                seen.add(s.copyWithCount(1));
                uses.add(1);
            } else {
                uses.set(at, uses.get(at) + 1);
            }
        }
        List<List<ItemStack>> inputs = new ArrayList<>();
        for (int i = 0; i < seen.size(); i++) {
            inputs.add(List.of(seen.get(i).copyWithCount(uses.get(i))));
        }
        return ContractData.of(inputs, List.of(result.copy()));
    }
}
