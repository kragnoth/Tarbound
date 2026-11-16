package com.tarbound.recipe;

import com.tarbound.registry.ModDataComponents;
import com.tarbound.registry.ModItems;
import com.tarbound.registry.ModRecipes;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/**
 * A written Spirit Contract plus blank contracts copies it onto each blank (like copying a written
 * book). The original comes back.
 */
public class CloneContractRecipe extends CustomRecipe {
    public CloneContractRecipe(CraftingBookCategory category) {
        super(category);
    }

    private static boolean written(ItemStack s) {
        return s.is(ModItems.SPIRIT_CONTRACT.get()) && s.get(ModDataComponents.CONTRACT.get()) != null;
    }

    /** @return the written contract, or EMPTY if the grid isn't exactly one written contract and some blanks */
    private static ItemStack original(CraftingInput input) {
        ItemStack found = ItemStack.EMPTY;
        int blanks = 0;
        for (int i = 0; i < input.size(); i++) {
            ItemStack s = input.getItem(i);
            if (s.isEmpty()) {
                continue;
            }
            if (written(s)) {
                if (!found.isEmpty()) {
                    return ItemStack.EMPTY;
                }
                found = s;
            } else if (s.is(ModItems.BLANK_CONTRACT.get())) {
                blanks++;
            } else {
                return ItemStack.EMPTY;
            }
        }
        return blanks > 0 ? found : ItemStack.EMPTY;
    }

    private static int blanks(CraftingInput input) {
        int n = 0;
        for (int i = 0; i < input.size(); i++) {
            if (input.getItem(i).is(ModItems.BLANK_CONTRACT.get())) {
                n++;
            }
        }
        return n;
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return !original(input).isEmpty();
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        ItemStack orig = original(input);
        return orig.isEmpty() ? ItemStack.EMPTY : orig.copyWithCount(blanks(input));
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
        NonNullList<ItemStack> out = NonNullList.withSize(input.size(), ItemStack.EMPTY);
        for (int i = 0; i < out.size(); i++) {
            ItemStack s = input.getItem(i);
            if (written(s)) {
                out.set(i, s.copyWithCount(1)); // the original stays
            } else if (s.hasCraftingRemainingItem()) {
                out.set(i, s.getCraftingRemainingItem());
            }
        }
        return out;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 2;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipes.CLONE_CONTRACT.get();
    }
}
