package com.tarbound.block;

import com.mojang.serialization.MapCodec;
import com.tarbound.registry.ModFluids;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import org.joml.Vector3f;

/**
 * The Pitch Cask: a squat oak cask sealed with tar and bound with iron hoops, holding 8 buckets of any fluid.
 * Fill and empty it with buckets or any fluid container; pipes and the radio's pool reach it like any tank (touching
 * tar or an antenna, it joins the pool). Broken, it keeps what it holds. A cask of tar weeps a little at the seams.
 */
public class PitchCaskBlock extends BaseEntityBlock {
    public static final MapCodec<PitchCaskBlock> CODEC = simpleCodec(PitchCaskBlock::new);
    private static final VoxelShape SHAPE = Shapes.or(Block.box(2, 0, 2, 14, 16, 14), Block.box(1, 3, 1, 15, 13, 15));
    private static final DustParticleOptions TAR_DROP = new DustParticleOptions(new Vector3f(0.06F, 0.05F, 0.05F), 0.6F);

    public PitchCaskBlock(Properties properties) {
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

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PitchCaskBlockEntity(pos, state);
    }

    /** Buckets and fluid containers fill and empty it; anything else places as normal. */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
            BlockHitResult hit) {
        if (stack.getCapability(Capabilities.FluidHandler.ITEM) == null) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level.isClientSide) {
            return ItemInteractionResult.SUCCESS;
        }
        return FluidUtil.interactWithFluidHandler(player, hand, level, pos, hit.getDirection())
                ? ItemInteractionResult.SUCCESS : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    /** 0 when empty, 1 for any fluid at all, up to 15 when full. */
    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof PitchCaskBlockEntity be)) {
            return 0;
        }
        FluidTank t = be.tank();
        return t.isEmpty() ? 0 : 1 + (int) (14L * t.getFluidAmount() / t.getCapacity());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    /** A cask holding tar weeps a drop now and then from the seam under a hoop. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(6) != 0 || !(level.getBlockEntity(pos) instanceof PitchCaskBlockEntity be)
                || !be.tank().getFluid().getFluid().isSame(ModFluids.TAR.get())) {
            return;
        }
        double a = random.nextDouble() * Math.PI * 2;
        double x = pos.getX() + 0.5 + Math.cos(a) * 0.45;
        double z = pos.getZ() + 0.5 + Math.sin(a) * 0.45;
        level.addParticle(TAR_DROP, x, pos.getY() + 0.2 + random.nextDouble() * 0.15, z, 0, -0.02, 0);
    }
}
