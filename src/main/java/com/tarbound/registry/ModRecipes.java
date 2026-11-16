package com.tarbound.registry;

import com.tarbound.Tarbound;
import com.tarbound.recipe.ToolShapelessRecipe;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModRecipes {
    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS = DeferredRegister.create(Registries.RECIPE_SERIALIZER, Tarbound.MODID);

    public static final DeferredHolder<RecipeSerializer<?>, ToolShapelessRecipe.Serializer> SHAPELESS_TOOL =
            SERIALIZERS.register("shapeless_tool", ToolShapelessRecipe.Serializer::new);

    public static final DeferredHolder<RecipeSerializer<?>, net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer<com.tarbound.recipe.CloneContractRecipe>> CLONE_CONTRACT =
            SERIALIZERS.register("clone_contract", () -> new net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer<>(com.tarbound.recipe.CloneContractRecipe::new));

    private ModRecipes() {}
}
