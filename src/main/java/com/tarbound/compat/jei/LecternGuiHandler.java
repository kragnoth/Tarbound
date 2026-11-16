package com.tarbound.compat.jei;

import com.tarbound.client.ScribingLecternScreen;
import com.tarbound.client.ScryingFontScreen;
import java.util.Optional;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import mezz.jei.api.neoforge.NeoForgeTypes;
import mezz.jei.api.runtime.IClickableIngredient;

/**
 * R and U on the Scribing Lectern's ghost slots and its crafting result (not real slots, so JEI can't see them on
 * its own): look up an ingredient's recipes, press + to lay one out, write it, and step back with the lectern's
 * back button to carry on with the recipe you came from.
 */
class LecternGuiHandler implements IGuiContainerHandler<ScribingLecternScreen> {
    @SuppressWarnings({"removal", "deprecation"})
    @Override
    public Optional<IClickableIngredient<?>> getClickableIngredientUnderMouse(ScribingLecternScreen screen, double mouseX, double mouseY) {
        ScryingFontScreen.HoveredStack hovered = screen.ghostUnderMouse(mouseX, mouseY);
        if (hovered == null || TarboundJeiPlugin.runtime == null) {
            return Optional.empty();
        }
        if (!hovered.fluid().isEmpty()) {
            return TarboundJeiPlugin.runtime.getIngredientManager()
                    .createClickableIngredient(NeoForgeTypes.FLUID_STACK, hovered.fluid(), hovered.area(), false)
                    .map(c -> c);
        }
        return TarboundJeiPlugin.runtime.getIngredientManager()
                .createClickableIngredient(VanillaTypes.ITEM_STACK, hovered.stack(), hovered.area(), false)
                .map(c -> c);
    }
}
