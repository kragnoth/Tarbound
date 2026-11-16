package com.tarbound.recipe;

import com.mojang.serialization.MapCodec;
import com.tarbound.registry.ModRecipes;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapelessRecipe;

/**
 * A shapeless recipe whose tools survive: any damageable ingredient (flint and steel, shears...) comes
 * back with one more point of damage instead of being used up. Same JSON as a vanilla shapeless recipe,
 * with type {@code tarbound:shapeless_tool}.
 */
public class ToolShapelessRecipe extends ShapelessRecipe {
    public ToolShapelessRecipe(String group, CraftingBookCategory category, ItemStack result, NonNullList<Ingredient> ingredients) {
        super(group, category, result, ingredients);
    }

    private static ToolShapelessRecipe of(ShapelessRecipe r) {
        return new ToolShapelessRecipe(r.getGroup(), r.category(), r.getResultItem(null), r.getIngredients());
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
        NonNullList<ItemStack> left = super.getRemainingItems(input);
        for (int i = 0; i < input.size(); i++) {
            ItemStack in = input.getItem(i);
            if (!in.isEmpty() && in.isDamageableItem()) {
                ItemStack tool = in.copyWithCount(1);
                tool.setDamageValue(tool.getDamageValue() + 1);
                left.set(i, tool.getDamageValue() >= tool.getMaxDamage() ? ItemStack.EMPTY : tool);
            }
        }
        return left;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipes.SHAPELESS_TOOL.get();
    }

    public static final class Serializer implements RecipeSerializer<ToolShapelessRecipe> {
        private static final MapCodec<ToolShapelessRecipe> CODEC =
                RecipeSerializer.SHAPELESS_RECIPE.codec().xmap(ToolShapelessRecipe::of, r -> r);
        private static final StreamCodec<RegistryFriendlyByteBuf, ToolShapelessRecipe> STREAM_CODEC =
                RecipeSerializer.SHAPELESS_RECIPE.streamCodec().map(ToolShapelessRecipe::of, r -> r);

        @Override
        public MapCodec<ToolShapelessRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, ToolShapelessRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
