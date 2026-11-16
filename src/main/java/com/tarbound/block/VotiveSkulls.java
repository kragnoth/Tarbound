package com.tarbound.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;

/**
 * What the floor and wall Votive Skull share: a candle burning on a skeleton skull. The skull is vanilla's own
 * (same block entity and renderer, so resource packs restyle it), the candle is vanilla's candle model. Lit, it
 * gives off a candle's light and flame; an empty hand puts it out, flint and steel or a fire charge relights it.
 */
public final class VotiveSkulls {
    private VotiveSkulls() {}

    public static ItemInteractionResult use(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, Vec3 wick) {
        boolean lit = state.getValue(BlockStateProperties.LIT);
        if (lit && stack.isEmpty()) {
            if (!level.isClientSide) {
                level.setBlock(pos, state.setValue(BlockStateProperties.LIT, false), Block.UPDATE_ALL);
                level.playSound(null, pos, SoundEvents.CANDLE_EXTINGUISH, SoundSource.BLOCKS, 1.0F, 1.0F);
                level.gameEvent(player, GameEvent.BLOCK_CHANGE, pos);
            } else {
                level.addParticle(ParticleTypes.SMOKE, wick.x, wick.y, wick.z, 0, 0.1, 0);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        boolean flint = stack.is(Items.FLINT_AND_STEEL);
        if (!lit && (flint || stack.is(Items.FIRE_CHARGE))) {
            if (!level.isClientSide) {
                level.setBlock(pos, state.setValue(BlockStateProperties.LIT, true), Block.UPDATE_ALL);
                level.playSound(null, pos, flint ? SoundEvents.FLINTANDSTEEL_USE : SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 1.0F, 1.0F);
                level.gameEvent(player, GameEvent.BLOCK_CHANGE, pos);
                if (flint) {
                    stack.hurtAndBreak(1, player, net.minecraft.world.entity.LivingEntity.getSlotForHand(hand));
                } else {
                    stack.consume(1, player);
                }
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    /** A candle's flame and the odd wisp of smoke at {@code wick}. */
    public static void flame(BlockState state, Level level, Vec3 wick, RandomSource random) {
        if (!state.getValue(BlockStateProperties.LIT)) {
            return;
        }
        if (random.nextFloat() < 0.3F) {
            level.addParticle(ParticleTypes.SMOKE, wick.x, wick.y, wick.z, 0, 0, 0);
            if (random.nextFloat() < 0.17F) {
                level.playLocalSound(wick.x, wick.y, wick.z, SoundEvents.CANDLE_AMBIENT, SoundSource.BLOCKS, 1.0F + random.nextFloat(),
                        random.nextFloat() * 0.7F + 0.3F, false);
            }
        }
        level.addParticle(ParticleTypes.SMALL_FLAME, wick.x, wick.y, wick.z, 0, 0, 0);
    }
}
