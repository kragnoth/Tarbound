package com.tarbound.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A lesser Bottomless Seep. Touching a radio's tar or antennas, it weeps cheap filler (cobblestone, sand, sugar cane,
 * string, spider eyes: whatever {@code tarbound:seep_yields} lists) into that radio's jobs and orders, for the Spirit
 * each one costs. It holds nothing, and never hands anything to players, standing contracts or the board; it never
 * makes anything a contract on that radio's machines makes; and it makes only so many items each pass.
 */
public class WeepingSeepBlock extends Block {
    public static final MapCodec<WeepingSeepBlock> CODEC = simpleCodec(WeepingSeepBlock::new);

    public WeepingSeepBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(4) != 0) {
            return;
        }
        double x = pos.getX() + 0.15 + random.nextDouble() * 0.7;
        double z = pos.getZ() + 0.15 + random.nextDouble() * 0.7;
        level.addParticle(ParticleTypes.DRIPPING_OBSIDIAN_TEAR, x, pos.getY() - 0.05, z, 0, 0, 0);
    }
}
