package com.tarbound.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Creative only. Touching the tar like any storage, it hands its radio as much as it asks of anything no contract on
 * that radio makes (raw materials, items and fluids), and takes nothing in. Everything a contract does make still
 * has to be crafted, so an order runs its whole tree for real: a test bench for deep orders.
 */
public class BottomlessSeepBlock extends BaseEntityBlock {
    public static final MapCodec<BottomlessSeepBlock> CODEC = simpleCodec(BottomlessSeepBlock::new);

    public BottomlessSeepBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BottomlessSeepBlockEntity(pos, state);
    }
}
