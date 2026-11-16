package com.tarbound.compat.jei;

import com.tarbound.client.ScryingFontScreen;
import java.util.List;
import java.util.Optional;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.neoforge.NeoForgeTypes;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import mezz.jei.api.runtime.IClickableIngredient;
import net.minecraft.client.renderer.Rect2i;

/**
 * Keeps JEI's overlay off the tray, and lets R/U work on pool entries and tray slots (which are not
 * real slots, so JEI can't see them on its own).
 */
class FontGuiHandler implements IGuiContainerHandler<ScryingFontScreen> {
    @Override
    public List<Rect2i> getGuiExtraAreas(ScryingFontScreen screen) {
        return List.of(screen.trayArea());
    }

    // The three-argument form exists in every 1.21.1 JEI (the factory overload calls it by default),
    // so overriding it keeps us working on older and newer JEI builds alike.
    @SuppressWarnings({"removal", "deprecation"})
    @Override
    public Optional<IClickableIngredient<?>> getClickableIngredientUnderMouse(ScryingFontScreen screen, double mouseX, double mouseY) {
        ScryingFontScreen.HoveredStack hovered = screen.stackUnderMouse(mouseX, mouseY);
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
