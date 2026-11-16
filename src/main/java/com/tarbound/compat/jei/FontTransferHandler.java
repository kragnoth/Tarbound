package com.tarbound.compat.jei;

import com.tarbound.compat.RecipeFill;
import com.tarbound.menu.ScryingFontMenu;
import com.tarbound.registry.ModMenus;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import mezz.jei.api.constants.RecipeTypes;
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
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;

/**
 * JEI "+" button: fills the font's 3x3 grid from the tar pool and the player's inventory.
 */
class FontTransferHandler implements IRecipeTransferHandler<ScryingFontMenu, RecipeHolder<CraftingRecipe>> {
    private final IRecipeTransferHandlerHelper helper;

    FontTransferHandler(IRecipeTransferHandlerHelper helper) {
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
    public RecipeType<RecipeHolder<CraftingRecipe>> getRecipeType() {
        return RecipeTypes.CRAFTING;
    }

    // The six-argument form is present in every 1.21.1 JEI; newer builds route their context-based
    // overload through it by default.
    @SuppressWarnings({"removal", "deprecation"})
    @Override
    @Nullable
    public IRecipeTransferError transferRecipe(ScryingFontMenu menu, RecipeHolder<CraftingRecipe> recipe, IRecipeSlotsView recipeSlots,
                                               Player player, boolean maxTransfer, boolean doTransfer) {
        if (!menu.clientCanCraft()) {
            return helper.createUserErrorWithTooltip(Component.translatable("gui.tarbound.no_crafting_table"));
        }
        List<IRecipeSlotView> inputs = recipeSlots.getSlotViews(RecipeIngredientRole.INPUT);
        if (inputs.size() > 9) {
            return helper.createInternalError();
        }
        List<List<ItemStack>> grid = new ArrayList<>(inputs.size());
        for (IRecipeSlotView slot : inputs) {
            grid.add(slot.getItemStacks().filter(s -> !s.isEmpty()).toList());
        }

        List<Integer> missing = RecipeFill.missing(grid, RecipeFill.available(menu, player, true, grid));
        if (!missing.isEmpty()) {
            List<IRecipeSlotView> missingViews = new ArrayList<>(missing.size());
            for (int i : missing) {
                missingViews.add(inputs.get(i));
            }
            return helper.createUserErrorForMissingSlots(Component.translatable("gui.tarbound.missing"), missingViews);
        }

        if (doTransfer) {
            RecipeFill.send(menu, grid, maxTransfer);
        }
        return null;
    }
}
