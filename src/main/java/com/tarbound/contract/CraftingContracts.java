package com.tarbound.contract;

import com.tarbound.network.ItemKey;
import com.tarbound.network.TarNetwork;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/**
 * Crafting-table contracts: a Spirit Contract written from a crafting recipe keeps the recipe's id, and
 * a Reliquary on a crafting table (a crafting station) crafts it straight from the pool: no inventory,
 * nothing loaded, the result goes back into the pool. Jobs spread these crafts across every station
 * holding the contract, the same way machine jobs spread across machines.
 */
public final class CraftingContracts {
    private CraftingContracts() {}

    /** {@link #craftOnce} result: the inputs were there, but the pool had no room for what it would make. */
    public static final int NO_ROOM = -1;

    /** The crafting recipe with this id, if it is one we can run (not a special/dynamic recipe). */
    @Nullable
    public static RecipeHolder<CraftingRecipe> recipe(Level level, @Nullable ResourceLocation id) {
        if (id == null) {
            return null;
        }
        return level.getRecipeManager().byKey(id)
                .filter(h -> h.value() instanceof CraftingRecipe r && !r.isSpecial() && !r.getIngredients().isEmpty())
                .map(h -> {
                    @SuppressWarnings("unchecked")
                    RecipeHolder<CraftingRecipe> cast = (RecipeHolder<CraftingRecipe>) h;
                    return cast;
                })
                .orElse(null);
    }

    /**
     * What the contract shows and how jobs count inputs, built from the server's own copy of the recipe:
     * one entry per distinct ingredient, its count being how many grid slots use it (8 planks for a
     * chest is one entry of 8, not eight entries of 1).
     */
    public static ContractData describe(CraftingRecipe recipe, HolderLookup.Provider registries) {
        Map<String, List<ItemStack>> alternatives = new LinkedHashMap<>();
        Map<String, Integer> uses = new LinkedHashMap<>();
        for (Ingredient ingredient : recipe.getIngredients()) {
            if (ingredient.isEmpty()) {
                continue;
            }
            ItemStack[] items = ingredient.getItems();
            String key = Arrays.stream(items).map(s -> s.getItem().toString()).sorted().toList().toString();
            alternatives.putIfAbsent(key, List.of(items));
            uses.merge(key, 1, Integer::sum);
        }
        List<List<ItemStack>> inputs = new ArrayList<>();
        alternatives.forEach((key, items) -> {
            int count = uses.get(key);
            inputs.add(items.stream().map(s -> s.copyWithCount(count)).toList());
        });
        return withReturns(ContractData.of(inputs, List.of(recipe.getResultItem(registries).copy())), recipe, sampleInput(recipe));
    }

    /** The recipe laid out with the first item of each ingredient, as a crafting table would hold it. */
    private static CraftingInput sampleInput(CraftingRecipe recipe) {
        List<Ingredient> ingredients = recipe.getIngredients();
        if (recipe instanceof net.minecraft.world.item.crafting.ShapedRecipe shaped) {
            List<ItemStack> grid = new ArrayList<>();
            for (int i = 0; i < shaped.getWidth() * shaped.getHeight(); i++) {
                ItemStack[] items = i < ingredients.size() ? ingredients.get(i).getItems() : new ItemStack[0];
                grid.add(items.length > 0 ? items[0].copyWithCount(1) : ItemStack.EMPTY);
            }
            return CraftingInput.of(shaped.getWidth(), shaped.getHeight(), grid);
        }
        List<ItemStack> grid = new ArrayList<>();
        for (Ingredient ingredient : ingredients) {
            ItemStack[] items = ingredient.getItems();
            if (items.length > 0 && grid.size() < 9) {
                grid.add(items[0].copyWithCount(1));
            }
        }
        while (grid.size() < 9) {
            grid.add(ItemStack.EMPTY);
        }
        return CraftingInput.of(3, 3, grid);
    }

    /**
     * {@code data} knowing which of its inputs the recipe hands back after every craft, asked of the recipe itself
     * ({@code getRemainingItems}): a Blood Orb in a NeoVitae rune comes back, a water bucket comes back as a
     * different item (an empty bucket) so it's used up, and a custom recipe that keeps or eats things says so. An
     * input counts as handed back when every grid slot holding it gets the same item back.
     */
    public static ContractData withReturns(ContractData data, CraftingRecipe recipe, CraftingInput input) {
        List<ItemStack> left;
        try {
            left = recipe.getRemainingItems(input);
        } catch (RuntimeException e) {
            return data; // a recipe that can't answer for a sample grid: keep the item-based guess
        }
        List<Integer> returned = new ArrayList<>();
        for (int i = 0; i < data.inputs().size(); i++) {
            List<ItemStack> alts = data.inputs().get(i);
            boolean seen = false;
            boolean allBack = true;
            for (int p = 0; p < input.size() && p < left.size(); p++) {
                ItemStack in = input.getItem(p);
                if (in.isEmpty() || alts.stream().noneMatch(a -> ItemStack.isSameItem(a, in))) {
                    continue;
                }
                seen = true;
                allBack &= ItemStack.isSameItem(left.get(p), in);
            }
            if (seen && allBack) {
                returned.add(i);
            }
        }
        return data.withReturned(returned);
    }

    /**
     * Crafts once from the pool: picks the most plentiful pool item for each ingredient, checks the
     * recipe really matches, takes the inputs, and puts the result and any leftovers (buckets and the
     * like) back into the pool. Before taking anything it checks the pool has room for the result and
     * leftovers; if not, it doesn't craft. (Should the pool fill up in between anyway, the overflow
     * drops at {@code dropAt} rather than vanishing.)
     *
     * @return how many items the craft made; 0 if the pool didn't hold what's needed, {@link #NO_ROOM}
     *         if there was no room for the output (nothing is taken in either case)
     */
    public static int craftOnce(Level level, TarNetwork net, RecipeHolder<CraftingRecipe> holder, BlockPos dropAt) {
        return craftOnce(level, net, holder, dropAt, java.util.Set.of());
    }

    /**
     * As above; for an ingredient with several matches, anything in {@code prefer} goes first (an order's
     * own stock, lent from its Shade, before loose items in storage).
     */
    public static int craftOnce(Level level, TarNetwork net, RecipeHolder<CraftingRecipe> holder, BlockPos dropAt,
            java.util.Set<ItemKey> prefer) {
        return craftOnce(level, net, holder, dropAt, prefer, null);
    }

    /** As above; with {@code only}, an ingredient may only be filled by those items (an exact-items contract). */
    public static int craftOnce(Level level, TarNetwork net, RecipeHolder<CraftingRecipe> holder, BlockPos dropAt,
            java.util.Set<ItemKey> prefer, @Nullable java.util.Set<net.minecraft.world.item.Item> only) {
        CraftingRecipe recipe = holder.value();
        NonNullList<Ingredient> ingredients = recipe.getIngredients();
        int width;
        int height;
        if (recipe instanceof ShapedRecipe shaped) {
            width = shaped.getWidth();
            height = shaped.getHeight();
        } else {
            width = 3;
            height = 3;
        }
        int size = width * height;
        if (ingredients.size() > size) {
            return 0;
        }

        // The pool, plus filled buckets it could make from empty ones and its tanks (milk for a cake).
        List<ItemStack> wanted = new ArrayList<>();
        for (Ingredient ingredient : ingredients) {
            for (ItemStack option : ingredient.getItems()) {
                wanted.add(option);
            }
        }
        Map<ItemKey, Long> stock = net.countForJobs(wanted); // only jobs craft at stations: a Weeping Seep may chip in
        List<ItemStack> grid = new ArrayList<>(size);
        List<ItemStack> chosen = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            Ingredient ingredient = i < ingredients.size() ? ingredients.get(i) : Ingredient.EMPTY;
            if (ingredient.isEmpty()) {
                grid.add(ItemStack.EMPTY);
                continue;
            }
            // Most plentiful match, but never an enchanted, renamed or filled one while an ordinary one will do.
            ItemKey best = null;
            long bestCount = 0;
            boolean bestPrecious = true;
            boolean bestPreferred = false;
            for (Map.Entry<ItemKey, Long> e : stock.entrySet()) {
                if (e.getValue() <= 0 || !ingredient.test(e.getKey().proto())
                        || (only != null && !only.contains(e.getKey().proto().getItem()))) {
                    continue;
                }
                boolean preferred = prefer.contains(e.getKey());
                boolean precious = com.tarbound.network.LooseMatch.isPrecious(e.getKey().proto());
                boolean better;
                if (best == null) {
                    better = true;
                } else if (preferred != bestPreferred) {
                    better = preferred;
                } else if (precious != bestPrecious) {
                    better = !precious;
                } else {
                    better = e.getValue() > bestCount;
                }
                if (better) {
                    best = e.getKey();
                    bestCount = e.getValue();
                    bestPrecious = precious;
                    bestPreferred = preferred;
                }
            }
            if (best == null) {
                return 0;
            }
            stock.merge(best, -1L, Long::sum);
            ItemStack one = best.proto().copyWithCount(1);
            grid.add(one);
            chosen.add(one);
        }

        CraftingInput input = CraftingInput.of(width, height, grid);
        if (!recipe.matches(input, level)) {
            return 0;
        }
        ItemStack result = recipe.assemble(input, level.registryAccess());
        if (result.isEmpty()) {
            return 0;
        }
        int made = result.getCount();
        NonNullList<ItemStack> leftovers = recipe.getRemainingItems(input);
        List<ItemStack> outputs = new ArrayList<>();
        outputs.add(result);
        for (ItemStack left : leftovers) {
            if (!left.isEmpty()) {
                outputs.add(left);
            }
        }
        // The inputs we'd take free their slots, but only if a stack runs out; don't count on it.
        if (!net.canFitAll(outputs)) {
            return NO_ROOM;
        }

        // Take the inputs; if any can't be had after all, put back what was taken.
        List<ItemStack> taken = new ArrayList<>();
        for (ItemStack need : chosen) {
            ItemStack got = net.extractForJob(need, 1);
            if (got.isEmpty()) {
                for (ItemStack back : taken) {
                    give(level, net, back, dropAt);
                }
                return 0;
            }
            taken.add(got);
        }
        give(level, net, result, dropAt);
        for (ItemStack left : leftovers) {
            if (!left.isEmpty()) {
                give(level, net, left.copy(), dropAt);
            }
        }
        return made;
    }

    private static void give(Level level, TarNetwork net, ItemStack stack, BlockPos dropAt) {
        ItemStack rest = net.insert(stack);
        if (!rest.isEmpty()) {
            Block.popResource(level, dropAt, rest);
        }
    }
}
