package com.tarbound.compat.jei;

import com.tarbound.client.StewardScreen;
import java.util.ArrayList;
import java.util.List;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.ingredients.ITypedIngredient;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.world.item.ItemStack;

/** Drag an item from JEI onto a Steward's list. */
class StewardGhostHandler implements IGhostIngredientHandler<StewardScreen> {
    @Override
    @SuppressWarnings("unchecked")
    public <I> List<Target<I>> getTargetsTyped(StewardScreen gui, ITypedIngredient<I> ingredient, boolean doStart) {
        List<Target<I>> out = new ArrayList<>();
        ItemStack item = ingredient.getIngredient(VanillaTypes.ITEM_STACK).orElse(ItemStack.EMPTY);
        if (item.isEmpty()) {
            return out;
        }
        for (StewardScreen.DropTarget t : gui.dropTargets()) {
            out.add((Target<I>) new Target<Object>() {
                @Override
                public Rect2i getArea() {
                    return t.area();
                }

                @Override
                public void accept(Object ignored) {
                    gui.setRow(t.row(), item.copyWithCount(1));
                }
            });
        }
        return out;
    }

    @Override
    public void onComplete() {
    }
}
