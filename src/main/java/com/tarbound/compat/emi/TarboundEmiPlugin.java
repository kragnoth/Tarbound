package com.tarbound.compat.emi;

import com.tarbound.client.ScryingFontScreen;
import com.tarbound.registry.ModItems;
import com.tarbound.registry.ModMenus;
import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.VanillaEmiRecipeCategories;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.stack.EmiStackInteraction;
import dev.emi.emi.api.widget.Bounds;
import net.minecraft.client.renderer.Rect2i;

/** Loaded by EMI only; nothing else references this package. */
@EmiEntrypoint
public class TarboundEmiPlugin implements EmiPlugin {
    @Override
    public void register(EmiRegistry registry) {
        com.tarbound.compat.SearchSync.emi = new com.tarbound.compat.SearchSync.Bridge() {
            @Override
            public String get() {
                return dev.emi.emi.api.EmiApi.getSearchText();
            }

            @Override
            public void set(String text) {
                dev.emi.emi.api.EmiApi.setSearchText(text);
            }
        };
        registry.addRecipeHandler(ModMenus.SCRYING_FONT.get(), new FontEmiHandler());
        registry.addRecipeHandler(ModMenus.SCRIBING_LECTERN.get(), new LecternEmiHandler());
        registry.addDragDropHandler(com.tarbound.client.ScribingLecternScreen.class, LecternEmiHandler.DRAG);
        registry.addDragDropHandler(com.tarbound.client.StewardScreen.class, new dev.emi.emi.api.EmiDragDropHandler.BoundsBased<>((screen, consumer) -> {
            for (com.tarbound.client.StewardScreen.DropTarget t : screen.dropTargets()) {
                var a = t.area();
                consumer.accept(new Bounds(a.getX(), a.getY(), a.getWidth(), a.getHeight()), ingredient -> {
                    for (EmiStack s : ingredient.getEmiStacks()) {
                        if (!s.getItemStack().isEmpty()) {
                            screen.setRow(t.row(), s.getItemStack().copyWithCount(1));
                            return;
                        }
                    }
                });
            }
        }));
        registry.addStackProvider(com.tarbound.client.StewardScreen.class, (screen, x, y) -> {
            net.minecraft.world.item.ItemStack s = screen.stackUnderMouse(x, y);
            return s.isEmpty() ? EmiStackInteraction.EMPTY : new EmiStackInteraction(EmiStack.of(s), null, false);
        });
        registry.addWorkstation(VanillaEmiRecipeCategories.CRAFTING, EmiStack.of(ModItems.SCRYING_FONT.get()));

        registry.addExclusionArea(ScryingFontScreen.class, (screen, out) -> {
            Rect2i a = screen.trayArea();
            out.accept(new Bounds(a.getX(), a.getY(), a.getWidth(), a.getHeight()));
        });

        // R and U on the lectern's ghost slots and result, to build a chain of contracts from the grid. Not
        // clickable, so clicks still set and clear ghosts.
        registry.addStackProvider(com.tarbound.client.ScribingLecternScreen.class, (screen, x, y) -> {
            ScryingFontScreen.HoveredStack hovered = screen.ghostUnderMouse(x, y);
            if (hovered == null) {
                return EmiStackInteraction.EMPTY;
            }
            return new EmiStackInteraction(hovered.fluid().isEmpty()
                    ? EmiStack.of(hovered.stack())
                    : dev.emi.emi.api.neoforge.NeoForgeEmiStack.of(hovered.fluid()), null, false);
        });

        registry.addStackProvider(ScryingFontScreen.class, (screen, x, y) -> {
            ScryingFontScreen.HoveredStack hovered = screen.stackUnderMouse(x, y);
            if (hovered == null) {
                return EmiStackInteraction.EMPTY;
            }
            // Not clickable: EMI binds left and right click to recipes/uses on clickable stacks and eats the
            // click, so taking an item out of the pool would open EMI instead. Keys (R, U, A) still work.
            return new EmiStackInteraction(hovered.fluid().isEmpty()
                    ? EmiStack.of(hovered.stack())
                    : dev.emi.emi.api.neoforge.NeoForgeEmiStack.of(hovered.fluid()), null, false);
        });
    }
}
