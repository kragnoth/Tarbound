package com.tarbound.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A Shade: a spirit that hovers where it's placed and watches over one multi-step order at a time,
 * holding the order's stock. Drawn entirely by {@code ShadeRenderer}; you can walk through it.
 */
public class ShadeBlock extends Block implements EntityBlock {
    public static final MapCodec<ShadeBlock> CODEC = simpleCodec(ShadeBlock::new);
    private static final VoxelShape SHAPE = Block.box(4, 3, 4, 12, 13, 12);

    public ShadeBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ShadeBlockEntity(pos, state);
    }

    @Override
    @javax.annotation.Nullable
    public <T extends BlockEntity> net.minecraft.world.level.block.entity.BlockEntityTicker<T> getTicker(Level level, BlockState state,
            net.minecraft.world.level.block.entity.BlockEntityType<T> type) {
        if (level.isClientSide || type != com.tarbound.registry.ModBlockEntities.SHADE.get()) {
            return null;
        }
        return (l, p, s, be) -> ShadeBlockEntity.serverTick(l, p, s, (ShadeBlockEntity) be);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    /** A Votive Skull raises its rank by one, up to the top rank. */
    @Override
    protected net.minecraft.world.ItemInteractionResult useItemOn(net.minecraft.world.item.ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, net.minecraft.world.InteractionHand hand, BlockHitResult hit) {
        if (!stack.is(com.tarbound.registry.ModItems.VOTIVE_SKULL.get())) {
            return net.minecraft.world.ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level.isClientSide) {
            return net.minecraft.world.ItemInteractionResult.SUCCESS;
        }
        if (level.getBlockEntity(pos) instanceof ShadeBlockEntity shade) {
            if (shade.rankUp()) {
                stack.consume(1, player);
                level.playSound(null, pos, net.minecraft.sounds.SoundEvents.SOUL_ESCAPE.value(), net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 0.7F);
                if (level instanceof net.minecraft.server.level.ServerLevel sl) {
                    sl.sendParticles(net.minecraft.core.particles.ParticleTypes.SOUL, pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 0.5, 12, 0.3, 0.4, 0.3, 0.02);
                }
                player.displayClientMessage(Component.translatable("message.tarbound.shade.ranked", shade.rank(), shade.capacity())
                        .withStyle(ChatFormatting.DARK_AQUA), true);
            } else {
                player.displayClientMessage(Component.translatable("message.tarbound.shade.max_rank").withStyle(ChatFormatting.GRAY), true);
            }
        }
        return net.minecraft.world.ItemInteractionResult.CONSUME;
    }

    /** A plain click says what it's up to. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof ShadeBlockEntity shade) {
            Component msg;
            if (shade.link() == null) {
                msg = Component.translatable("message.tarbound.shade.untuned");
            } else if (shade.free()) {
                msg = Component.translatable("message.tarbound.shade.free", shade.rank(), shade.capacity());
            } else {
                msg = Component.translatable("message.tarbound.shade.busy", shade.held().size() + shade.heldFluids().size());
            }
            player.displayClientMessage(msg.copy().withStyle(ChatFormatting.DARK_AQUA), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Broken mid-order: the order is called off and the radio gets its stock back (or it spills here). */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && !level.isClientSide && level.getBlockEntity(pos) instanceof ShadeBlockEntity shade && !shade.free()) {
            ScryingFontBlockEntity font = shade.radio();
            if (font != null) {
                font.cancelOrder(shade.orderId(), shade);
            }
            if (!shade.free()) {
                shade.release(null);
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(4) != 0 || !(level.getBlockEntity(pos) instanceof ShadeBlockEntity shade)) {
            return;
        }
        double x = pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.6;
        double y = pos.getY() + 0.3 + random.nextDouble() * 0.5;
        double z = pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.6;
        level.addParticle(shade.free() ? ParticleTypes.ASH : ParticleTypes.SOUL, x, y, z, 0, 0.02, 0);
    }
}
