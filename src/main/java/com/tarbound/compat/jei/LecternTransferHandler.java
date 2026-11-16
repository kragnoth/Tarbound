package com.tarbound.compat.jei;

import com.tarbound.contract.ContractDraft.Ghost;
import com.tarbound.menu.ScribingLecternMenu;
import com.tarbound.registry.ModMenus;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.neoforge.NeoForgeTypes;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import mezz.jei.api.recipe.transfer.IUniversalRecipeTransferHandler;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;

/**
 * JEI "+" at the Scribing Lectern lays the recipe out in the lectern's ghost slots, ready to check and Write. A
 * crafting-table recipe goes into the crafting grid where it sits on a crafting table; anything else goes into
 * the machine (processing) slots exactly as JEI shows it, INPUT slots in and OUTPUT slots out, like AE2's
 * processing patterns, so it works for every machine without knowing what the machine is.
 */
class LecternTransferHandler implements IUniversalRecipeTransferHandler<ScribingLecternMenu> {
    private final IRecipeTransferHandlerHelper helper;

    LecternTransferHandler(IRecipeTransferHandlerHelper helper) {
        this.helper = helper;
    }

    @Override
    public Class<? extends ScribingLecternMenu> getContainerClass() {
        return ScribingLecternMenu.class;
    }

    @Override
    public Optional<MenuType<ScribingLecternMenu>> getMenuType() {
        return Optional.of(ModMenus.SCRIBING_LECTERN.get());
    }

    @Override
    @Nullable
    public IRecipeTransferError transferRecipe(ScribingLecternMenu menu, Object recipe, IRecipeSlotsView slots, Player player,
                                               boolean maxTransfer, boolean doTransfer) {
        if (recipe instanceof RecipeHolder<?> holder && holder.value() instanceof CraftingRecipe crafting && !crafting.isSpecial()
                && !crafting.getIngredients().isEmpty()) {
            if (doTransfer) {
                menu.draft.fillCrafting(crafting);
            }
            return null;
        }
        List<Ghost> inputs = new ArrayList<>();
        for (IRecipeSlotView slot : slots.getSlotViews(RecipeIngredientRole.INPUT)) {
            Ghost g = ghost(slot);
            if (!g.isEmpty()) {
                inputs.add(g);
            }
        }
        List<Ghost> outputs = new ArrayList<>();
        for (IRecipeSlotView slot : slots.getSlotViews(RecipeIngredientRole.OUTPUT)) {
            Ghost g = ghost(slot);
            if (!g.isEmpty()) {
                outputs.add(Ghost.of(g.item()).isEmpty() ? g : Ghost.of(g.item()));
            }
        }
        if (inputs.isEmpty() || outputs.isEmpty()) {
            return helper.createInternalError(); // nothing item- or fluid-shaped to lay out: hide the button
        }
        List<Ghost> fitted = com.tarbound.contract.ContractDraft.fitInputs(inputs);
        if (fitted == null) {
            return helper.createUserErrorWithTooltip(net.minecraft.network.chat.Component.translatable("gui.tarbound.lectern.too_many_inputs",
                    com.tarbound.contract.ContractDraft.INPUTS));
        }
        if (doTransfer) {
            menu.draft.fillProcessing(fitted, outputs);
        }
        return null;
    }

    /** A JEI slot as a ghost: the item it shows (and every item it would accept), or its fluid. */
    private static Ghost ghost(IRecipeSlotView slot) {
        List<ItemStack> items = slot.getItemStacks().filter(s -> !s.isEmpty()).toList();
        if (!items.isEmpty()) {
            ItemStack shown = slot.getDisplayedItemStack().filter(s -> !s.isEmpty()).orElse(items.get(0));
            return Ghost.of(shown, items.size() > 1 ? items : List.of());
        }
        return slot.getIngredients(NeoForgeTypes.FLUID_STACK).filter(f -> !f.isEmpty()).findFirst()
                .map(Ghost::of).orElse(Ghost.EMPTY);
    }
}
