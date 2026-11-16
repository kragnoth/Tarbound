package com.tarbound.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** A Votive Skull on the floor: vanilla's skeleton skull (turned 16 ways like one) with a candle on its crown. */
public class VotiveSkullBlock extends SkullBlock {
    public static final MapCodec<VotiveSkullBlock> CODEC = simpleCodec(VotiveSkullBlock::new);
    private static final VoxelShape SHAPE = Shapes.or(Block.box(4, 0, 4, 12, 8, 12), Block.box(7, 8, 7, 9, 14, 9));

    public VotiveSkullBlock(Properties properties) {
        super(Types.SKELETON, properties);
        registerDefaultState(defaultBlockState().setValue(BlockStateProperties.LIT, true));
    }

    @Override
    public MapCodec<? extends SkullBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(BlockStateProperties.LIT);
    }

    /** The skull is drawn by vanilla's skull renderer; the block model adds the candle. */
    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    private static Vec3 wick(BlockPos pos) {
        return new Vec3(pos.getX() + 0.5, pos.getY() + 15.5 / 16, pos.getZ() + 0.5);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
            BlockHitResult hit) {
        return VotiveSkulls.use(stack, state, level, pos, player, hand, wick(pos));
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        VotiveSkulls.flame(state, level, wick(pos), random);
    }
}
