package com.tarbound.compat.jei;

import com.tarbound.Tarbound;
import com.tarbound.client.ScryingFontScreen;
import com.tarbound.registry.ModItems;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.ResourceLocation;

/** Loaded by JEI only; nothing else references this package. */
@JeiPlugin
public class TarboundJeiPlugin implements IModPlugin {
    private static final ResourceLocation UID = Tarbound.id("jei_plugin");

    static IJeiRuntime runtime;

    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    public void registerRecipeTransferHandlers(IRecipeTransferRegistration registration) {
        registration.addRecipeTransferHandler(new FontTransferHandler(registration.getTransferHelper()), RecipeTypes.CRAFTING);

        // Every other recipe type gets a machine-fill handler; it only shows its button when a bound
        // machine is a catalyst for that type.
        Set<RecipeType<?>> types = new LinkedHashSet<>(List.of(
                RecipeTypes.SMELTING, RecipeTypes.BLASTING, RecipeTypes.SMOKING, RecipeTypes.CAMPFIRE_COOKING));
        try {
            registration.getJeiHelpers().getAllRecipeTypes().forEach(types::add);
        } catch (RuntimeException e) {
            Tarbound.LOGGER.warn("Couldn't list JEI recipe types; machine fill limited to vanilla cooking", e);
        }
        types.remove(RecipeTypes.CRAFTING);
        for (RecipeType<?> type : types) {
            registerMachineHandler(registration, type);
        }

        // The Scribing Lectern writes any recipe into a Spirit Contract.
        registration.addUniversalRecipeTransferHandler(new LecternTransferHandler(registration.getTransferHelper()));
    }

    private static <R> void registerMachineHandler(IRecipeTransferRegistration registration, RecipeType<R> type) {
        registration.addRecipeTransferHandler(new MachineTransferHandler<>(type, registration.getTransferHelper()), type);
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(ModItems.SCRYING_FONT.get().getDefaultInstance(), RecipeTypes.CRAFTING);
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addGuiContainerHandler(ScryingFontScreen.class, new FontGuiHandler());
        registration.addGhostIngredientHandler(com.tarbound.client.ScribingLecternScreen.class, new LecternGhostHandler());
        registration.addGhostIngredientHandler(com.tarbound.client.StewardScreen.class, new StewardGhostHandler());
        registration.addGuiContainerHandler(com.tarbound.client.StewardScreen.class, new mezz.jei.api.gui.handlers.IGuiContainerHandler<>() {
            @SuppressWarnings({"removal", "deprecation"})
            @Override
            public java.util.Optional<mezz.jei.api.runtime.IClickableIngredient<?>> getClickableIngredientUnderMouse(
                    com.tarbound.client.StewardScreen screen, double mouseX, double mouseY) {
                net.minecraft.world.item.ItemStack s = screen.stackUnderMouse(mouseX, mouseY);
                if (s.isEmpty() || runtime == null) {
                    return java.util.Optional.empty();
                }
                return runtime.getIngredientManager().createClickableIngredient(mezz.jei.api.constants.VanillaTypes.ITEM_STACK, s,
                        new net.minecraft.client.renderer.Rect2i((int) mouseX, (int) mouseY, 1, 1), false).map(c -> c);
            }
        });
        registration.addGuiContainerHandler(com.tarbound.client.ScribingLecternScreen.class, new LecternGuiHandler());
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        runtime = jeiRuntime;
        com.tarbound.compat.SearchSync.jei = new com.tarbound.compat.SearchSync.Bridge() {
            @Override
            public String get() {
                return jeiRuntime.getIngredientFilter().getFilterText();
            }

            @Override
            public void set(String text) {
                jeiRuntime.getIngredientFilter().setFilterText(text);
            }
        };
        com.tarbound.compat.SearchSync.jeiVerdict = () -> verdict(jeiRuntime);
    }

    /** Every item and fluid type JEI knows (built once per runtime): items it doesn't know are judged by our own search. */
    private static java.util.Set<net.minecraft.world.item.Item> knownItems;
    private static java.util.Set<net.minecraft.world.level.material.Fluid> knownFluids;

    private static com.tarbound.compat.SearchSync.Verdict verdict(IJeiRuntime rt) {
        if (knownItems == null) {
            java.util.Set<net.minecraft.world.item.Item> items = new java.util.HashSet<>();
            for (net.minecraft.world.item.ItemStack s : rt.getIngredientManager().getAllItemStacks()) {
                items.add(s.getItem());
            }
            java.util.Set<net.minecraft.world.level.material.Fluid> fluids = new java.util.HashSet<>();
            for (net.neoforged.neoforge.fluids.FluidStack f : rt.getIngredientManager().getAllIngredients(mezz.jei.api.neoforge.NeoForgeTypes.FLUID_STACK)) {
                fluids.add(f.getFluid());
            }
            knownItems = items;
            knownFluids = fluids;
        }
        java.util.Set<com.tarbound.network.ItemKey> exact = new java.util.HashSet<>();
        java.util.Set<net.minecraft.world.item.Item> plain = new java.util.HashSet<>();
        for (net.minecraft.world.item.ItemStack s : rt.getIngredientFilter().getFilteredItemStacks()) {
            exact.add(com.tarbound.network.ItemKey.of(s));
            plain.add(s.getItem());
        }
        java.util.Set<net.minecraft.world.level.material.Fluid> shownFluids = new java.util.HashSet<>();
        for (net.neoforged.neoforge.fluids.FluidStack f : rt.getIngredientFilter().getFilteredIngredients(mezz.jei.api.neoforge.NeoForgeTypes.FLUID_STACK)) {
            shownFluids.add(f.getFluid());
        }
        java.util.Set<net.minecraft.world.item.Item> known = knownItems;
        java.util.Set<net.minecraft.world.level.material.Fluid> knownF = knownFluids;
        return new com.tarbound.compat.SearchSync.Verdict() {
            @Override
            public Boolean item(net.minecraft.world.item.ItemStack stack) {
                if (!known.contains(stack.getItem())) {
                    return null;
                }
                // Exact match, or a used/renamed copy of an item JEI shows (JEI lists the plain one).
                return exact.contains(com.tarbound.network.ItemKey.of(stack))
                        || (!stack.getComponentsPatch().isEmpty() && plain.contains(stack.getItem()));
            }

            @Override
            public Boolean fluid(net.neoforged.neoforge.fluids.FluidStack stack) {
                return knownF.contains(stack.getFluid()) ? shownFluids.contains(stack.getFluid()) : null;
            }
        };
    }

    @Override
    public void onRuntimeUnavailable() {
        runtime = null;
        com.tarbound.compat.SearchSync.jei = null;
        com.tarbound.compat.SearchSync.jeiVerdict = null;
        knownItems = null;
        knownFluids = null;
    }
}
