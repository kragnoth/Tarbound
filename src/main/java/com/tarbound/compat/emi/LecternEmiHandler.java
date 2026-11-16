package com.tarbound.compat.emi;

import com.tarbound.client.ScribingLecternScreen;
import com.tarbound.contract.ContractDraft.Ghost;
import com.tarbound.menu.ScribingLecternMenu;
import dev.emi.emi.api.EmiDragDropHandler;
import dev.emi.emi.api.recipe.EmiPlayerInventory;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.VanillaEmiRecipeCategories;
import dev.emi.emi.api.recipe.handler.EmiCraftContext;
import dev.emi.emi.api.recipe.handler.EmiRecipeHandler;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.Bounds;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * EMI at the Scribing Lectern: its fill button lays the recipe out in the lectern's ghost slots (crafting grid
 * for crafting recipes, machine slots for the rest), ready to check and Write; and stacks dragged from EMI drop
 * into the ghost slots ({@link #DRAG}).
 */
class LecternEmiHandler implements EmiRecipeHandler<ScribingLecternMenu> {

    @Override
    public EmiPlayerInventory getInventory(AbstractContainerScreen<ScribingLecternMenu> screen) {
        return new EmiPlayerInventory(List.of());
    }

    @Override
    public boolean supportsRecipe(EmiRecipe recipe) {
        if (recipe.getCategory() == VanillaEmiRecipeCategories.CRAFTING) {
            return crafting(recipe) != null;
        }
        List<com.tarbound.contract.ContractDraft.Ghost> in = ghosts(recipe.getInputs());
        return !in.isEmpty() && com.tarbound.contract.ContractDraft.fitInputs(in) != null && !outputs(recipe).isEmpty();
    }

    @Override
    public boolean canCraft(EmiRecipe recipe, EmiCraftContext<ScribingLecternMenu> context) {
        return supportsRecipe(recipe);
    }

    @Override
    public boolean craft(EmiRecipe recipe, EmiCraftContext<ScribingLecternMenu> context) {
        if (!supportsRecipe(recipe)) {
            return false;
        }
        CraftingRecipe crafting = recipe.getCategory() == VanillaEmiRecipeCategories.CRAFTING ? crafting(recipe) : null;
        if (crafting != null) {
            context.getScreenHandler().draft.fillCrafting(crafting);
        } else {
            context.getScreenHandler().draft.fillProcessing(com.tarbound.contract.ContractDraft.fitInputs(ghosts(recipe.getInputs())), outputs(recipe));
        }
        Minecraft.getInstance().setScreen(context.getScreen());
        return true;
    }

    /** The vanilla crafting recipe behind an EMI crafting recipe (null for synthetic or special ones). */
    @javax.annotation.Nullable
    private static CraftingRecipe crafting(EmiRecipe recipe) {
        var holder = recipe.getBackingRecipe();
        return holder != null && holder.value() instanceof CraftingRecipe r && !r.isSpecial() && !r.getIngredients().isEmpty() ? r : null;
    }

    /** An EMI ingredient as a ghost: its first item (every item it lists becomes a substitute), or its fluid. */
    static Ghost ghost(EmiIngredient ingredient) {
        List<EmiStack> stacks = ingredient.getEmiStacks();
        if (stacks.isEmpty()) {
            return Ghost.EMPTY;
        }
        long amount = Math.max(1, ingredient.getAmount());
        List<ItemStack> items = new ArrayList<>();
        for (EmiStack s : stacks) {
            ItemStack item = s.getItemStack();
            if (!item.isEmpty()) {
                items.add(item.copyWithCount((int) Math.min(amount, item.getMaxStackSize())));
            }
        }
        if (!items.isEmpty()) {
            return Ghost.of(items.get(0), items.size() > 1 ? items : List.of());
        }
        FluidStack fluid = EmiFluids.of(stacks.get(0), ingredient.getAmount() > 1 ? ingredient.getAmount() : 1000);
        return Ghost.of(fluid);
    }

    private static List<Ghost> ghosts(List<EmiIngredient> ingredients) {
        List<Ghost> out = new ArrayList<>();
        for (EmiIngredient i : ingredients) {
            Ghost g = ghost(i);
            if (!g.isEmpty()) {
                out.add(g);
            }
        }
        return out;
    }

    private static List<Ghost> outputs(EmiRecipe recipe) {
        List<Ghost> out = new ArrayList<>();
        for (EmiStack s : recipe.getOutputs()) {
            Ghost g = ghost(s);
            if (!g.isEmpty()) {
                out.add(g.item().isEmpty() ? g : Ghost.of(g.item()));
            }
        }
        return out;
    }

    /** Dragging a stack from EMI onto a ghost slot. */
    static final EmiDragDropHandler<ScribingLecternScreen> DRAG = new EmiDragDropHandler.BoundsBased<>((screen, consumer) -> {
        for (ScribingLecternScreen.GhostTarget t : screen.ghostTargets()) {
            var a = t.area();
            consumer.accept(new Bounds(a.getX(), a.getY(), a.getWidth(), a.getHeight()), ingredient -> {
                Ghost g = ghost(ingredient);
                if (!g.item().isEmpty()) {
                    screen.setGhost(t, g.item(), g.alternatives());
                } else if (!g.fluid().isEmpty()) {
                    screen.setGhost(t, g.fluid());
                }
            });
        }
    });
}
