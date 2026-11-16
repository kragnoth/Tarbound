package com.tarbound.network;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * The bound spirit keeps a furnace (or smoker, blast furnace, or any modded furnace built on the
 * vanilla one) burning while it has something to cook, so bound furnaces never need fuel.
 *
 * <p>The burn timer ({@code litTime}/{@code litDuration}) is package-private in vanilla; the access transformer in
 * {@code META-INF/accesstransformer.cfg} opens it (0.59; reflection before).
 */
public final class SpiritFire {
    private static final int TOP_UP_BELOW = 20;
    private static final int BURN = 200;

    /** Whether a bound block of this kind is kept lit by its spirit (so it never needs fuel). */
    public static boolean feeds(BlockEntity be) {
        return com.tarbound.config.TarboundConfig.SERVER.spiritFire.get() && be instanceof AbstractFurnaceBlockEntity;
    }

    /**
     * Whether a bound block is kept lit by its spirit right now: it's a furnace, Spirit Fire is on,
     * and (with Spirit costs on) the Reliquary on it is tuned to a radio that can pay for a smelt.
     * Jobs don't load fuel into such a furnace.
     */
    public static boolean feedsNow(Level level, BlockPos machinePos, BlockEntity be) {
        if (!feeds(be)) {
            return false;
        }
        if (!com.tarbound.spirit.Spirit.costs()) {
            return true;
        }
        var reliquary = com.tarbound.block.ReliquaryBlock.reliquaryFor(level, machinePos);
        var radio = reliquary != null ? reliquary.radio() : null;
        return radio != null && radio.canPay(com.tarbound.config.TarboundConfig.SERVER.spiritFireCost.get());
    }

    /**
     * The furnace at {@code pos} is about to go out (or is out) and has something it can cook: it wants
     * another smelt's worth of Spirit Fire. Checks the burn timer first, so the recipe lookup only runs
     * when it matters.
     */
    public static boolean wantsFire(Level level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof AbstractFurnaceBlockEntity furnace) || furnace.litTime >= TOP_UP_BELOW) {
            return false;
        }
        ItemStack input = furnace.getItem(0);
        return !input.isEmpty() && cookable(level, input);
    }

    /** Gives the furnace at {@code pos} one smelt's worth of burn time (already paid for). */
    public static void kindle(Level level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof AbstractFurnaceBlockEntity furnace)) {
            return;
        }
        furnace.litTime = BURN;
        furnace.litDuration = BURN;
        furnace.setChanged();
        // The furnace only flips its LIT state when its own fuel runs out or catches, so light it here.
        BlockState state = level.getBlockState(pos);
        if (state.hasProperty(BlockStateProperties.LIT) && !state.getValue(BlockStateProperties.LIT)) {
            level.setBlock(pos, state.setValue(BlockStateProperties.LIT, true), 3);
        }
    }

    private static boolean cookable(Level level, ItemStack input) {
        SingleRecipeInput in = new SingleRecipeInput(input);
        var recipes = level.getRecipeManager();
        return recipes.getRecipeFor(RecipeType.SMELTING, in, level).isPresent()
                || recipes.getRecipeFor(RecipeType.BLASTING, in, level).isPresent()
                || recipes.getRecipeFor(RecipeType.SMOKING, in, level).isPresent();
    }

    private SpiritFire() {}
}
