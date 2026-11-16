package com.tarbound.compat.jei;

import com.tarbound.compat.MachineFill;
import com.tarbound.compat.RecipeFill;
import com.tarbound.menu.ScryingFontMenu;
import com.tarbound.net.TrayPayload;
import com.tarbound.registry.ModMenus;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import mezz.jei.api.neoforge.NeoForgeTypes;

/**
 * JEI "+" on any machine recipe (smelting, blasting, modded processing...): loads one operation into
 * a bound machine that is a catalyst for this recipe type. The button stays hidden when no bound
 * machine runs the recipe.
 */
class MachineTransferHandler<R> implements IRecipeTransferHandler<ScryingFontMenu, R> {
    private final RecipeType<R> recipeType;
    private final IRecipeTransferHandlerHelper helper;

    MachineTransferHandler(RecipeType<R> recipeType, IRecipeTransferHandlerHelper helper) {
        this.recipeType = recipeType;
        this.helper = helper;
    }

    @Override
    public Class<? extends ScryingFontMenu> getContainerClass() {
        return ScryingFontMenu.class;
    }

    @Override
    public Optional<MenuType<ScryingFontMenu>> getMenuType() {
        return Optional.of(ModMenus.SCRYING_FONT.get());
    }

    @Override
    public RecipeType<R> getRecipeType() {
        return recipeType;
    }

    @SuppressWarnings({"removal", "deprecation"})
    @Override
    @Nullable
    public IRecipeTransferError transferRecipe(ScryingFontMenu menu, R recipe, IRecipeSlotsView recipeSlots,
                                               Player player, boolean maxTransfer, boolean doTransfer) {
        if (TarboundJeiPlugin.runtime == null) {
            return helper.createInternalError();
        }
        List<ItemStack> catalysts = TarboundJeiPlugin.runtime.getRecipeManager()
                .createRecipeCatalystLookup(recipeType)
                .getItemStack()
                .toList();
        TrayPayload.Machine machine = MachineFill.findMachine(menu, catalysts);
        if (machine == null) {
            return helper.createInternalError(); // hides the button: nothing bound runs this
        }

        List<IRecipeSlotView> slotViews = new ArrayList<>();
        List<List<ItemStack>> inputs = new ArrayList<>();
        for (IRecipeSlotView slot : recipeSlots.getSlotViews(RecipeIngredientRole.INPUT)) {
            List<ItemStack> alts = slot.getItemStacks().filter(s -> !s.isEmpty()).toList();
            if (!alts.isEmpty()) {
                slotViews.add(slot);
                inputs.add(alts);
            }
        }
        List<FluidStack> fluids = new ArrayList<>();
        for (IRecipeSlotView slot : recipeSlots.getSlotViews(RecipeIngredientRole.INPUT)) {
            slot.getIngredients(NeoForgeTypes.FLUID_STACK).filter(f -> !f.isEmpty()).findFirst().ifPresent(fluids::add);
        }
        if (inputs.isEmpty() && fluids.isEmpty()) {
            return helper.createInternalError();
        }
        if (!RecipeFill.hasFluids(menu, fluids)) {
            return helper.createUserErrorWithTooltip(Component.translatable("gui.tarbound.missing_fluid"));
        }

        List<Integer> missing = RecipeFill.missing(inputs, RecipeFill.available(menu, player, false, inputs));
        if (!missing.isEmpty()) {
            List<IRecipeSlotView> missingViews = new ArrayList<>(missing.size());
            for (int i : missing) {
                missingViews.add(slotViews.get(i));
            }
            return helper.createUserErrorForMissingSlots(Component.translatable("gui.tarbound.missing"), missingViews);
        }

        if (doTransfer) {
            MachineFill.send(menu, machine, inputs, fluids);
        }
        return null;
    }
}
