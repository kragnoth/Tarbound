package com.tarbound.compat.emi;

import com.tarbound.client.ScryingFontScreen;
import com.tarbound.compat.MachineFill;
import com.tarbound.compat.RecipeFill;
import com.tarbound.menu.ScryingFontMenu;
import com.tarbound.net.TrayPayload;
import com.tarbound.network.ItemKey;
import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.recipe.EmiCraftingRecipe;
import dev.emi.emi.api.recipe.EmiPlayerInventory;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.VanillaEmiRecipeCategories;
import dev.emi.emi.api.recipe.handler.EmiCraftContext;
import dev.emi.emi.api.recipe.handler.EmiRecipeHandler;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.item.ItemStack;

/**
 * EMI fill for the font.
 * <ul>
 *   <li>Crafting recipes fill the 3x3 grid (EMI lays inputs out row-major across 3x3, shaped
 *       recipes padded, which maps one-to-one onto the grid).</li>
 *   <li>Any other recipe loads one operation into a bound machine that is a workstation for the
 *       recipe's category, with fuel if it needs some.</li>
 * </ul>
 * Both draw on the tar pool as well as the player's inventory.
 */
class FontEmiHandler implements EmiRecipeHandler<ScryingFontMenu> {

    /**
     * EMI asks for this every frame a fill button is drawn and every 50-400 ms for its craftables list,
     * and treats a new answer as "inventory changed, check every recipe again". So it's built once per
     * change of the pool, inventory, grid or cursor, and the same object is handed back until then.
     */
    @Nullable
    private RecipeFill.Snapshot inventoryFor;
    @Nullable
    private EmiPlayerInventory inventory;

    @Override
    public EmiPlayerInventory getInventory(AbstractContainerScreen<ScryingFontMenu> screen) {
        RecipeFill.Snapshot snap = RecipeFill.snapshot(screen.getMenu(), Minecraft.getInstance().player, true);
        if (snap != inventoryFor || inventory == null) {
            List<EmiStack> stacks = new ArrayList<>(snap.items.size() + snap.fluids.size());
            snap.items.forEach((key, count) -> stacks.add(EmiStack.of(key.proto(), count)));
            snap.fluids.forEach((key, mb) ->
                    stacks.add(dev.emi.emi.api.neoforge.NeoForgeEmiStack.of(key.proto().copyWithAmount((int) Math.min(Integer.MAX_VALUE, mb)))));
            inventory = new EmiPlayerInventory(stacks);
            inventoryFor = snap;
        }
        return inventory;
    }

    /** Each recipe's inputs as item lists (big tags expand to many stacks: worth keeping). */
    private final Map<EmiRecipe, List<List<ItemStack>>> inputCache = new java.util.WeakHashMap<>();
    private final Map<EmiRecipe, RecipeFill.Slots> slotCache = new java.util.WeakHashMap<>();
    private final Map<EmiRecipe, List<net.neoforged.neoforge.fluids.FluidStack>> fluidCache = new java.util.WeakHashMap<>();

    private RecipeFill.Slots cachedSlots(EmiRecipe recipe) {
        return slotCache.computeIfAbsent(recipe, r -> new RecipeFill.Slots(cachedInputs(r)));
    }

    private List<List<ItemStack>> cachedInputs(EmiRecipe recipe) {
        return inputCache.computeIfAbsent(recipe, FontEmiHandler::inputs);
    }

    private List<net.neoforged.neoforge.fluids.FluidStack> cachedFluids(EmiRecipe recipe) {
        return fluidCache.computeIfAbsent(recipe, FontEmiHandler::fluids);
    }

    /** Bound machine per recipe category, until the machine list or selection changes. */
    private final Map<Object, java.util.Optional<TrayPayload.Machine>> machineCache = new java.util.HashMap<>();
    @Nullable
    private Object machineCacheKey;

    private static boolean isCrafting(EmiRecipe recipe) {
        return recipe.getCategory() == VanillaEmiRecipeCategories.CRAFTING;
    }

    @Override
    public boolean supportsRecipe(EmiRecipe recipe) {
        if (isCrafting(recipe)) {
            return recipe.getInputs().size() <= 9 && (!(recipe instanceof EmiCraftingRecipe crafting) || crafting.canFit(3, 3));
        }
        // Machine recipes: only offer the button when a bound machine can run it.
        return Minecraft.getInstance().screen instanceof ScryingFontScreen screen
                && machineFor(screen.getMenu(), recipe) != null
                && (!cachedInputs(recipe).stream().allMatch(List::isEmpty) || !cachedFluids(recipe).isEmpty());
    }

    @Override
    public boolean canCraft(EmiRecipe recipe, EmiCraftContext<ScryingFontMenu> context) {
        ScryingFontMenu menu = context.getScreenHandler();
        boolean crafting = isCrafting(recipe);
        if (crafting ? !menu.clientCanCraft() : machineFor(menu, recipe) == null) {
            return false;
        }
        RecipeFill.Snapshot snap = RecipeFill.snapshot(menu, Minecraft.getInstance().player, crafting);
        return snap.canSupply(cachedSlots(recipe)) && (crafting || snap.hasFluids(cachedFluids(recipe)));
    }

    @Override
    public boolean craft(EmiRecipe recipe, EmiCraftContext<ScryingFontMenu> context) {
        if (!canCraft(recipe, context)) {
            return false;
        }
        ScryingFontMenu menu = context.getScreenHandler();
        if (isCrafting(recipe)) {
            RecipeFill.send(menu, inputs(recipe), context.getAmount() > 1);
        } else {
            TrayPayload.Machine machine = machineFor(menu, recipe);
            List<List<ItemStack>> inputs = inputs(recipe).stream().filter(a -> !a.isEmpty()).toList();
            MachineFill.send(menu, machine, inputs, fluids(recipe));
        }
        Minecraft.getInstance().setScreen(context.getScreen());
        return true;
    }

    @Nullable
    private TrayPayload.Machine machineFor(ScryingFontMenu menu, EmiRecipe recipe) {
        Object key = java.util.List.of(menu, menu.clientTrayVersion(), String.valueOf(menu.clientSelection()));
        if (!key.equals(machineCacheKey)) {
            machineCache.clear();
            machineCacheKey = key;
        }
        return machineCache.computeIfAbsent(recipe.getCategory(), c -> java.util.Optional.ofNullable(findMachineFor(menu, recipe))).orElse(null);
    }

    @Nullable
    private static TrayPayload.Machine findMachineFor(ScryingFontMenu menu, EmiRecipe recipe) {
        List<ItemStack> workstations = new ArrayList<>();
        for (EmiIngredient w : EmiApi.getRecipeManager().getWorkstations(recipe.getCategory())) {
            for (EmiStack s : w.getEmiStacks()) {
                ItemStack item = s.getItemStack();
                if (!item.isEmpty()) {
                    workstations.add(item);
                }
            }
        }
        return MachineFill.findMachine(menu, workstations);
    }

    /** The recipe's fluid inputs (the first fluid of each), amounts in mB. */
    private static List<net.neoforged.neoforge.fluids.FluidStack> fluids(EmiRecipe recipe) {
        List<net.neoforged.neoforge.fluids.FluidStack> out = new ArrayList<>();
        for (EmiIngredient ingredient : recipe.getInputs()) {
            for (EmiStack stack : ingredient.getEmiStacks()) {
                var f = EmiFluids.of(stack, Math.max(1, ingredient.getAmount()));
                if (!f.isEmpty()) {
                    out.add(f);
                    break;
                }
            }
        }
        return out;
    }

    /** One entry per recipe input, each listing acceptable stacks with the count one operation uses. */
    private static List<List<ItemStack>> inputs(EmiRecipe recipe) {
        List<List<ItemStack>> out = new ArrayList<>();
        for (EmiIngredient ingredient : recipe.getInputs()) {
            List<ItemStack> alts = new ArrayList<>();
            long amount = Math.max(1, ingredient.getAmount());
            for (EmiStack stack : ingredient.getEmiStacks()) {
                ItemStack item = stack.getItemStack();
                if (!item.isEmpty()) {
                    alts.add(item.copyWithCount((int) Math.min(amount, item.getMaxStackSize())));
                }
            }
            out.add(alts);
        }
        return out;
    }
}
