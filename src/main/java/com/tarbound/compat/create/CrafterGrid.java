package com.tarbound.compat.create;

import com.tarbound.Tarbound;
import com.tarbound.contract.ContractData;
import com.tarbound.network.ItemKey;
import com.tarbound.network.MachineLoader;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Property;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * Create's Mechanical Crafter grids, run by a Reliquary on any one crafter of the grid.
 *
 * <p>A contract written from a {@code create:mechanical_crafting} recipe (JEI/EMI) is matched back to that recipe (same
 * result, inputs it can take). To load one operation, Tarbound walks the crafter chain the way Create does (crafters
 * pointing into each other, all facing the same way), lays the crafters out on the same grid Create crafts on (rows by
 * height, columns along the side a "left"-pointing crafter hands its items to), and looks for a spot where the recipe's
 * pattern fits: every filled cell over an empty, working crafter. Mirrored placements are tried too when the recipe
 * accepts them. It then takes the items, puts one in each crafter, and starts the grid (with Create's own powered start,
 * so pattern gaps over crafters are fine). Create does the crafting; the result leaves the last crafter as usual, so put
 * storage the pool can see (or tar) in front of it.
 *
 * <p>All by reflection: Tarbound doesn't need Create to build or run.
 */
public final class CrafterGrid {
    private static final String CRAFTER_BE = "com.simibubi.create.content.kinetics.crafter.MechanicalCrafterBlockEntity";
    private static final String GRID = "com.simibubi.create.content.kinetics.crafter.RecipeGridHandler";
    private static final String CRAFTER_BLOCK = "com.simibubi.create.content.kinetics.crafter.MechanicalCrafterBlock";
    private static final ResourceLocation CRAFTER_ID = ResourceLocation.fromNamespaceAndPath("create", "mechanical_crafter");
    private static final ResourceLocation RECIPE_TYPE = ResourceLocation.fromNamespaceAndPath("create", "mechanical_crafting");
    /** Biggest grid walked (Create's own crafter grids top out well below this). */
    private static final int MAX_CRAFTERS = 256;

    private static Api api;

    private CrafterGrid() {}

    /** A Create Mechanical Crafter. */
    public static boolean isCrafter(BlockState state) {
        return BuiltInRegistries.BLOCK.getKey(state.getBlock()).equals(CRAFTER_ID);
    }

    // ------------------------------------------------------------------------------------------ recipes

    private static final Map<RecipeManager, Map<ContractData, Optional<RecipeHolder<?>>>> RECIPES = new WeakHashMap<>();

    /** The mechanical crafting recipe a contract was written from (same result, and inputs it can take), or null. */
    @Nullable
    public static synchronized ShapedRecipe recipeFor(Level level, ContractData contract) {
        RecipeManager rm = level.getRecipeManager();
        Optional<RecipeHolder<?>> h = RECIPES.computeIfAbsent(rm, k -> new HashMap<>()).computeIfAbsent(contract, c -> Optional.ofNullable(find(level, c)));
        return h.map(r -> (ShapedRecipe) r.value()).orElse(null);
    }

    @Nullable
    private static RecipeHolder<?> find(Level level, ContractData c) {
        RecipeType<?> type = BuiltInRegistries.RECIPE_TYPE.get(RECIPE_TYPE);
        if (type == null || c.outputs().isEmpty()) {
            return null;
        }
        ItemStack out = c.primaryOutput();
        for (RecipeHolder<?> h : level.getRecipeManager().getRecipes()) {
            Recipe<?> r = h.value();
            if (r.getType() != type || !(r instanceof ShapedRecipe shaped)) {
                continue;
            }
            ItemStack result = r.getResultItem(level.registryAccess());
            if (!ItemStack.isSameItemSameComponents(result, out) || result.getCount() != out.getCount()) {
                continue;
            }
            if (inputsFit(shaped, c)) {
                return h;
            }
        }
        return null;
    }

    /** Every ingredient of the recipe can be met by one of the contract's listed inputs. */
    private static boolean inputsFit(ShapedRecipe recipe, ContractData c) {
        for (Ingredient ing : recipe.getIngredients()) {
            if (ing.isEmpty()) {
                continue;
            }
            boolean any = false;
            for (List<ItemStack> alts : c.inputs()) {
                for (ItemStack a : alts) {
                    if (ing.test(a)) {
                        any = true;
                        break;
                    }
                }
                if (any) {
                    break;
                }
            }
            if (!any) {
                return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------------------------------------ loading

    /**
     * Loads one operation of {@code contract} into the crafter grid the crafter at {@code pos} belongs to.
     *
     * @return null if it went in, otherwise why not (nothing is taken then)
     */
    @Nullable
    public static Component load(Level level, BlockPos pos, ContractData contract, MachineLoader.Source source) {
        Api a = api();
        if (a == null) {
            return Component.translatable("gui.tarbound.crafter.no_create");
        }
        ShapedRecipe recipe = recipeFor(level, contract);
        if (recipe == null) {
            return Component.translatable("gui.tarbound.crafter.no_recipe", contract.primaryOutput().getHoverName());
        }
        BlockEntity root = level.getBlockEntity(pos);
        if (root == null || !a.crafterClass.isInstance(root)) {
            return Component.translatable("gui.tarbound.fill.no_machine");
        }
        List<BlockEntity> chain = a.chain(root);
        if (chain.isEmpty()) {
            return Component.translatable("gui.tarbound.crafter.broken_chain");
        }
        if (a.speed(root) == 0) {
            return Component.translatable("gui.tarbound.crafter.no_speed");
        }
        // Lay the grid out as Create does: x along the side a left-pointing crafter hands to, y up.
        BlockState rootState = root.getBlockState();
        Direction xDir = a.leftTarget(rootState);
        if (xDir == null) {
            return Component.translatable("gui.tarbound.crafter.broken_chain");
        }
        xDir = xDir.getOpposite();
        Map<Long, IItemHandler> cells = new HashMap<>();
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        for (BlockEntity be : chain) {
            IItemHandler inv = a.inventory(be);
            if (inv == null) {
                return Component.translatable("gui.tarbound.crafter.broken_chain");
            }
            if (!inv.getStackInSlot(0).isEmpty()) {
                return Component.translatable("gui.tarbound.crafter.busy");
            }
            BlockPos p = be.getBlockPos();
            int x = p.getX() * xDir.getStepX() + p.getY() * xDir.getStepY() + p.getZ() * xDir.getStepZ();
            int y = p.getY();
            cells.put(key(x, y), inv);
            minX = Math.min(minX, x);
            maxX = Math.max(maxX, x);
            minY = Math.min(minY, y);
            maxY = Math.max(maxY, y);
        }
        int w = recipe.getWidth();
        int h = recipe.getHeight();
        List<Ingredient> ings = recipe.getIngredients();
        boolean mirrorOk = a.acceptsMirrored(recipe);

        // Choose what fills each pattern cell from what's on hand.
        Map<ItemKey, Long> stock = new HashMap<>(source.snapshot(contract.inputs()));
        ItemStack[] chosen = new ItemStack[w * h];
        for (int i = 0; i < ings.size() && i < chosen.length; i++) {
            Ingredient ing = ings.get(i);
            if (ing.isEmpty()) {
                continue;
            }
            ItemKey best = null;
            long bestCount = 0;
            for (Map.Entry<ItemKey, Long> e : stock.entrySet()) {
                if (e.getValue() > 0 && ing.test(e.getKey().proto()) && e.getValue() > bestCount
                        && !com.tarbound.network.LooseMatch.isPrecious(e.getKey().proto())) {
                    best = e.getKey();
                    bestCount = e.getValue();
                }
            }
            if (best == null) {
                return Component.translatable("gui.tarbound.fill.missing", firstItem(ing).getHoverName());
            }
            stock.merge(best, -1L, Long::sum);
            chosen[i] = best.proto().copyWithCount(1);
        }

        // Find a spot on the grid for the pattern (top row at the top).
        List<Placement> placements = new ArrayList<>();
        for (boolean mirror : mirrorOk ? new boolean[] {false, true} : new boolean[] {false}) {
            for (int ox = minX; ox + w - 1 <= maxX; ox++) {
                for (int top = maxY; top - (h - 1) >= minY; top--) {
                    placements.add(new Placement(ox, top, mirror));
                }
            }
        }
        Placement fit = null;
        outer:
        for (Placement pl : placements) {
            for (int r = 0; r < h; r++) {
                for (int c = 0; c < w; c++) {
                    ItemStack item = chosen[c + r * w];
                    if (item == null) {
                        continue;
                    }
                    IItemHandler inv = cells.get(key(pl.x(c, w), pl.top - r));
                    if (inv == null || !inv.insertItem(0, item, true).isEmpty()) {
                        continue outer;
                    }
                }
            }
            fit = pl;
            break;
        }
        if (fit == null) {
            ItemStack probe = null;
            for (ItemStack c : chosen) {
                if (c != null) {
                    probe = c;
                    break;
                }
            }
            if (probe != null) {
                for (IItemHandler inv : cells.values()) {
                    if (!inv.insertItem(0, probe, true).isEmpty()) {
                        return Component.translatable("gui.tarbound.crafter.busy"); // mid-craft (or covered): try again later
                    }
                }
            }
            return Component.translatable("gui.tarbound.crafter.no_fit", w, h);
        }

        // Take everything, then fill; if any crafter refuses after all, hand it all back.
        List<ItemStack> taken = new ArrayList<>();
        for (ItemStack item : chosen) {
            if (item == null) {
                continue;
            }
            if (source.take(item, 1) < 1) {
                taken.forEach(source::giveBack);
                return Component.translatable("gui.tarbound.fill.missing", item.getHoverName());
            }
            taken.add(item.copy());
        }
        List<IItemHandler> filled = new ArrayList<>();
        for (int r = 0; r < h; r++) {
            for (int c = 0; c < w; c++) {
                ItemStack item = chosen[c + r * w];
                if (item == null) {
                    continue;
                }
                IItemHandler inv = cells.get(key(fit.x(c, w), fit.top - r));
                if (!inv.insertItem(0, item.copy(), false).isEmpty()) {
                    for (IItemHandler f : filled) {
                        f.extractItem(0, 1, false);
                    }
                    taken.forEach(source::giveBack);
                    return Component.translatable("gui.tarbound.crafter.busy");
                }
                filled.add(inv);
            }
        }
        // Gaps over crafters: start the grid the way a redstone pulse would (a full grid starts by itself).
        a.start(root);
        return null;
    }

    private static ItemStack firstItem(Ingredient ing) {
        ItemStack[] items = ing.getItems();
        return items.length == 0 ? ItemStack.EMPTY : items[0];
    }

    private static long key(int x, int y) {
        return ((long) x << 32) | (y & 0xFFFFFFFFL);
    }

    private record Placement(int ox, int top, boolean mirror) {
        int x(int c, int w) {
            return ox + (mirror ? w - 1 - c : c);
        }
    }

    // ------------------------------------------------------------------------------------------ Create, by reflection

    @Nullable
    private static synchronized Api api() {
        if (api == null) {
            api = Api.load();
        }
        return api.ok ? api : null;
    }

    private static final class Api {
        final boolean ok;
        Class<?> crafterClass;
        MethodHandle getInventory;
        MethodHandle checkCompleted;
        MethodHandle getSpeed;
        MethodHandle targeting;
        MethodHandle preceding;
        MethodHandle targetDir;
        MethodHandle acceptsMirrored;

        private Api(boolean ok) {
            this.ok = ok;
        }

        static Api load() {
            try {
                MethodHandles.Lookup l = MethodHandles.publicLookup();
                Api a = new Api(true);
                a.crafterClass = Class.forName(CRAFTER_BE);
                Class<?> grid = Class.forName(GRID);
                Class<?> block = Class.forName(CRAFTER_BLOCK);
                a.getInventory = l.unreflect(a.crafterClass.getMethod("getInventory"));
                a.checkCompleted = l.findVirtual(a.crafterClass, "checkCompletedRecipe", MethodType.methodType(void.class, boolean.class));
                a.getSpeed = l.unreflect(a.crafterClass.getMethod("getSpeed"));
                a.targeting = l.unreflect(grid.getMethod("getTargetingCrafter", a.crafterClass));
                a.preceding = l.unreflect(grid.getMethod("getPrecedingCrafters", a.crafterClass));
                a.targetDir = l.unreflect(block.getMethod("getTargetDirection", BlockState.class));
                Class<?> mech = Class.forName("com.simibubi.create.content.kinetics.crafter.MechanicalCraftingRecipe");
                a.acceptsMirrored = l.unreflect(mech.getMethod("acceptsMirrored"));
                return a;
            } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
                Tarbound.LOGGER.warn("Tarbound: Create's Mechanical Crafter isn't where expected; crafter grids won't load ({})", e.toString());
                return new Api(false);
            }
        }

        @Nullable
        IItemHandler inventory(BlockEntity be) {
            try {
                return (IItemHandler) getInventory.invoke(be);
            } catch (Throwable t) {
                return null;
            }
        }

        float speed(BlockEntity be) {
            try {
                return (float) getSpeed.invoke(be);
            } catch (Throwable t) {
                return 0;
            }
        }

        void start(BlockEntity be) {
            try {
                checkCompleted.invoke(be, true);
            } catch (Throwable t) {
                Tarbound.LOGGER.debug("Tarbound: couldn't start a crafter grid", t);
            }
        }

        boolean acceptsMirrored(ShapedRecipe recipe) {
            try {
                return (boolean) acceptsMirrored.invoke(recipe);
            } catch (Throwable t) {
                return false;
            }
        }

        /** Where a crafter in this state would hand its items if it pointed left. */
        @Nullable
        Direction leftTarget(BlockState state) {
            try {
                Property<?> pointing = state.getBlock().getStateDefinition().getProperty("pointing");
                if (pointing == null) {
                    return null;
                }
                return (Direction) targetDir.invoke(withValue(state, pointing, "left"));
            } catch (Throwable t) {
                return null;
            }
        }

        private static <T extends Comparable<T>> BlockState withValue(BlockState state, Property<T> prop, String name) {
            return prop.getValue(name).map(v -> state.setValue(prop, v)).orElse(state);
        }

        /** Every crafter in {@code root}'s chain (crafters pointing into one another, facing the same way). */
        List<BlockEntity> chain(BlockEntity root) {
            List<BlockEntity> out = new ArrayList<>();
            Set<BlockEntity> seen = new HashSet<>();
            List<BlockEntity> frontier = new ArrayList<>();
            frontier.add(root);
            try {
                while (!frontier.isEmpty() && out.size() < MAX_CRAFTERS) {
                    BlockEntity cur = frontier.remove(frontier.size() - 1);
                    if (!seen.add(cur)) {
                        continue;
                    }
                    out.add(cur);
                    Object target = targeting.invoke(cur);
                    if (target instanceof BlockEntity t && !seen.contains(t)) {
                        frontier.add(t);
                    }
                    Object prev = preceding.invoke(cur);
                    if (prev instanceof List<?> list) {
                        for (Object o : list) {
                            if (o instanceof BlockEntity b && !seen.contains(b)) {
                                frontier.add(b);
                            }
                        }
                    }
                }
            } catch (Throwable t) {
                return List.of();
            }
            Direction facing = root.getBlockState().hasProperty(BlockStateProperties.HORIZONTAL_FACING)
                    ? root.getBlockState().getValue(BlockStateProperties.HORIZONTAL_FACING) : null;
            for (BlockEntity be : out) {
                if (facing == null || !be.getBlockState().hasProperty(BlockStateProperties.HORIZONTAL_FACING)
                        || be.getBlockState().getValue(BlockStateProperties.HORIZONTAL_FACING) != facing) {
                    return List.of();
                }
            }
            return out;
        }
    }
}
