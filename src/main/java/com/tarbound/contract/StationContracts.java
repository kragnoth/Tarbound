package com.tarbound.contract;

import com.tarbound.network.ItemKey;
import com.tarbound.network.TarNetwork;
import com.tarbound.registry.ModTags;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * Stations: blocks with no inventory of their own that a Reliquary works straight from the pool, like the crafting
 * table. Besides crafting tables:
 * <ul>
 *   <li><b>Stonecutters</b> ({@code tarbound:cutting_stations}) cut a contract's input into its output, using the
 *       stonecutting recipe that matches it, a few cuts per pass (a contract written at the lectern from a
 *       stonecutting recipe in JEI/EMI is an ordinary one-input contract; no recipe id needed).</li>
 *   <li><b>Composters</b> ({@code tarbound:composting_stations}) are fed from the pool, one item at a time with the
 *       composter's own chance, and their bone meal comes back to the pool. They take what the Reliquary's standing
 *       contracts' tags name, or, with none, anything in {@code tarbound:compost_fodder} (seeds, saplings, leaves...).</li>
 * </ul>
 */
public final class StationContracts {
    private StationContracts() {}

    public enum Kind {
        CRAFTING,
        CUTTING,
        COMPOSTING
    }

    /** What kind of station a Reliquary makes of this block, or null if it's an ordinary machine. */
    @Nullable
    public static Kind kindOf(BlockState state) {
        if (state.is(Tags.Blocks.PLAYER_WORKSTATIONS_CRAFTING_TABLES)) {
            return Kind.CRAFTING;
        }
        if (state.is(ModTags.CUTTING_STATIONS)) {
            return Kind.CUTTING;
        }
        if (state.is(ModTags.COMPOSTING_STATIONS)) {
            return Kind.COMPOSTING;
        }
        return null;
    }

    // ------------------------------------------------------------------------------------------ stonecutting

    /** Per recipe manager (a reload makes a new one), the stonecutting recipe found for each contract. */
    private static final Map<RecipeManager, Map<ContractData, Optional<RecipeHolder<StonecutterRecipe>>>> CUTS = new WeakHashMap<>();

    /**
     * The stonecutting recipe a contract describes: one input, and an output a stonecutting recipe for that input
     * makes (same item and components, and as many as one cut gives). Null if there's none.
     */
    @Nullable
    public static synchronized RecipeHolder<StonecutterRecipe> cuttingRecipe(Level level, ContractData contract) {
        RecipeManager rm = level.getRecipeManager();
        return CUTS.computeIfAbsent(rm, k -> new java.util.HashMap<>()).computeIfAbsent(contract, c -> Optional.ofNullable(findCut(level, c))).orElse(null);
    }

    @Nullable
    private static RecipeHolder<StonecutterRecipe> findCut(Level level, ContractData c) {
        if (c.inputs().size() != 1 || c.inputs().get(0).isEmpty() || c.outputs().isEmpty() || !c.fluidInputs().isEmpty()) {
            return null;
        }
        ItemStack out = c.primaryOutput();
        for (RecipeHolder<StonecutterRecipe> h : level.getRecipeManager().getAllRecipesFor(RecipeType.STONECUTTING)) {
            ItemStack result = h.value().getResultItem(level.registryAccess());
            if (!ItemStack.isSameItemSameComponents(result, out) || result.getCount() != out.getCount()) {
                continue;
            }
            for (ItemStack alt : c.inputs().get(0)) {
                if (h.value().getIngredients().get(0).test(alt)) {
                    return h;
                }
            }
        }
        return null;
    }

    /**
     * One cut at a stonecutter station: takes one matching input (an order's lent stock first) and puts the result in
     * the pool. @return how many items it made, 0 if there was nothing to cut, {@link CraftingContracts#NO_ROOM} if the
     * pool has no room for the result (nothing is taken then)
     */
    public static int cutOnce(Level level, TarNetwork net, RecipeHolder<StonecutterRecipe> holder, ContractData contract, BlockPos dropAt,
            java.util.Set<ItemKey> prefer) {
        List<ItemStack> alts = contract.inputs().get(0);
        Map<ItemKey, Long> stock = net.countForJobs(alts);
        ItemKey best = null;
        long bestCount = 0;
        boolean bestPreferred = false;
        for (Map.Entry<ItemKey, Long> e : stock.entrySet()) {
            if (e.getValue() <= 0 || !holder.value().getIngredients().get(0).test(e.getKey().proto())) {
                continue;
            }
            boolean preferred = prefer.contains(e.getKey());
            if (best == null || (preferred && !bestPreferred) || (preferred == bestPreferred && e.getValue() > bestCount)) {
                best = e.getKey();
                bestCount = e.getValue();
                bestPreferred = preferred;
            }
        }
        if (best == null) {
            return 0;
        }
        ItemStack one = best.proto().copyWithCount(1);
        ItemStack result = holder.value().assemble(new SingleRecipeInput(one), level.registryAccess());
        if (result.isEmpty()) {
            return 0;
        }
        if (!net.canFitAll(List.of(result))) {
            return CraftingContracts.NO_ROOM;
        }
        if (net.extractForJob(one, 1).isEmpty()) {
            return 0;
        }
        int made = result.getCount();
        ItemStack rest = net.insert(result);
        if (!rest.isEmpty()) {
            Block.popResource(level, dropAt, rest);
        }
        return made;
    }

    // ------------------------------------------------------------------------------------------ composting

    /**
     * Works a composter station: collects ready bone meal into the pool, then feeds it up to {@code most} items the
     * standing contracts' tags (or {@code tarbound:compost_fodder}) name. @return how many items went in
     */
    public static int tendComposter(ServerLevel level, BlockPos pos, TarNetwork pool, List<StandingOrder> orders, int most) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof ComposterBlock)) {
            return 0;
        }
        if (state.getValue(ComposterBlock.LEVEL) == ComposterBlock.READY) {
            // Take the bone meal the way a hopper below would, so the composter empties itself.
            IItemHandler out = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, Direction.DOWN);
            if (out != null && pool.fitCount(new ItemStack(net.minecraft.world.item.Items.BONE_MEAL)) > 0) {
                ItemStack meal = out.extractItem(0, 1, false);
                if (!meal.isEmpty()) {
                    ItemStack rest = pool.insert(meal);
                    if (!rest.isEmpty()) {
                        Block.popResource(level, pos.above(), rest);
                    }
                }
            }
            return 0;
        }
        int fed = 0;
        Map<ItemKey, Long> stock = pool.countContents();
        for (Map.Entry<ItemKey, Long> e : stock.entrySet()) {
            ItemStack proto = e.getKey().proto();
            if (e.getValue() <= 0 || ComposterBlock.getValue(proto) <= 0 || !fodder(proto, orders)) {
                continue;
            }
            long left = e.getValue();
            while (fed < most && left > 0) {
                state = level.getBlockState(pos);
                if (!(state.getBlock() instanceof ComposterBlock) || state.getValue(ComposterBlock.LEVEL) >= ComposterBlock.MAX_LEVEL) {
                    return fed; // full (or about to be ready): wait for the bone meal
                }
                ItemStack one = pool.extract(proto, 1);
                if (one.isEmpty()) {
                    break;
                }
                ComposterBlock.insertItem(null, state, level, one, pos);
                fed++;
                left--;
            }
            if (fed >= most) {
                break;
            }
        }
        return fed;
    }

    private static boolean fodder(ItemStack stack, List<StandingOrder> orders) {
        if (orders.isEmpty()) {
            return stack.is(ModTags.COMPOST_FODDER);
        }
        for (StandingOrder o : orders) {
            if (o.matches(stack)) {
                return true;
            }
        }
        return false;
    }
}
