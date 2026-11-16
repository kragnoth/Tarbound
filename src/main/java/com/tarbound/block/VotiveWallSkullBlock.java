package com.tarbound.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.block.WallSkullBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** A Votive Skull hung on a wall, like a wall skull, its candle burning on top. */
public class VotiveWallSkullBlock extends WallSkullBlock {
    public static final MapCodec<VotiveWallSkullBlock> CODEC = simpleCodec(VotiveWallSkullBlock::new);

    public VotiveWallSkullBlock(Properties properties) {
        super(SkullBlock.Types.SKELETON, properties);
        registerDefaultState(defaultBlockState().setValue(BlockStateProperties.LIT, true));
    }

    @Override
    public MapCodec<? extends WallSkullBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(BlockStateProperties.LIT);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    /** The skull against the wall, plus the candle on top of it (the candle rises a little above the block). */
    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Direction f = state.getValue(FACING);
        double cx = 8 - f.getStepX() * 4, cz = 8 - f.getStepZ() * 4;
        return Shapes.or(super.getShape(state, level, pos, context), Block.box(cx - 1, 12, cz - 1, cx + 1, 16, cz + 1));
    }

    private static Vec3 wick(BlockState state, BlockPos pos) {
        Direction f = state.getValue(FACING);
        return new Vec3(pos.getX() + 0.5 - f.getStepX() * 0.25, pos.getY() + 19.5 / 16, pos.getZ() + 0.5 - f.getStepZ() * 0.25);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
            BlockHitResult hit) {
        return VotiveSkulls.use(stack, state, level, pos, player, hand, wick(state, pos));
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        VotiveSkulls.flame(state, level, wick(state, pos), random);
    }
}
