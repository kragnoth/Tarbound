package com.tarbound.compat.jei;

import com.tarbound.client.ScribingLecternScreen;
import com.tarbound.client.ScribingLecternScreen.GhostTarget;
import java.util.ArrayList;
import java.util.List;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.neoforge.NeoForgeTypes;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

/** Drag an item or fluid from JEI onto one of the Scribing Lectern's ghost slots. */
class LecternGhostHandler implements IGhostIngredientHandler<ScribingLecternScreen> {
    @Override
    @SuppressWarnings("unchecked")
    public <I> List<Target<I>> getTargetsTyped(ScribingLecternScreen gui, ITypedIngredient<I> ingredient, boolean doStart) {
        List<Target<I>> out = new ArrayList<>();
        ItemStack item = ingredient.getIngredient(VanillaTypes.ITEM_STACK).orElse(ItemStack.EMPTY);
        FluidStack fluid = ingredient.getIngredient(NeoForgeTypes.FLUID_STACK).orElse(FluidStack.EMPTY);
        for (GhostTarget t : gui.ghostTargets()) {
            if (!item.isEmpty()) {
                out.add((Target<I>) target(t.area(), x -> gui.setGhost(t, item.copyWithCount(1), List.of())));
            } else if (!fluid.isEmpty() && t.takesFluids()) {
                out.add((Target<I>) target(t.area(), x -> gui.setGhost(t, fluid.copyWithAmount(1000))));
            }
        }
        return out;
    }

    private static Target<Object> target(Rect2i area, java.util.function.Consumer<Object> accept) {
        return new Target<>() {
            @Override
            public Rect2i getArea() {
                return area;
            }

            @Override
            public void accept(Object ingredient) {
                accept.accept(ingredient);
            }
        };
    }

    @Override
    public void onComplete() {
    }
}
