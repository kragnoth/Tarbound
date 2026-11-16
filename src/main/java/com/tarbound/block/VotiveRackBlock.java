package com.tarbound.block;

import com.mojang.serialization.MapCodec;
import com.tarbound.config.TarboundConfig;
import com.tarbound.network.RadioRegistry;
import com.tarbound.registry.ModBlocks;
import com.tarbound.registry.ModItems;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A tar-soaked rack of candles, set up round a radio like a shrine. While it burns it gives the nearest radio
 * room for more work: more jobs and orders at once, deeper and bigger order plans. It counts if it's within reach
 * of the radio (3 blocks sideways, 1 below to 2 above; a Votive Skull set on it stretches that to 5), whatever the
 * radio's storage is bound through. It counts toward one radio only, the nearest. Light it with flint and steel or
 * a fire charge; while jobs run it costs the radio a little Spirit, and a radio that can't pay snuffs its racks.
 */
public class VotiveRackBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<VotiveRackBlock> CODEC = simpleCodec(VotiveRackBlock::new);
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    public static final BooleanProperty SKULL = BooleanProperty.create("skull");

    private static final VoxelShape NS = Block.box(0, 0, 5, 16, 14, 11);
    private static final VoxelShape EW = Block.box(5, 0, 0, 11, 14, 16);

    public VotiveRackBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(LIT, false).setValue(SKULL, false));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LIT, SKULL);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return state.getValue(FACING).getAxis() == Direction.Axis.Z ? NS : EW;
    }

    /** How far from a radio this rack still counts, sideways. */
    public static int reach(BlockState state) {
        return state.getValue(SKULL) ? TarboundConfig.SERVER.rackSkullReach.get() : TarboundConfig.SERVER.rackReach.get();
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
            BlockHitResult hit) {
        if ((stack.is(Items.FLINT_AND_STEEL) || stack.is(Items.FIRE_CHARGE)) && !state.getValue(LIT)) {
            if (!level.isClientSide) {
                level.setBlock(pos, state.setValue(LIT, true), Block.UPDATE_ALL);
                level.playSound(null, pos, stack.is(Items.FIRE_CHARGE) ? SoundEvents.FIRECHARGE_USE : SoundEvents.FLINTANDSTEEL_USE,
                        SoundSource.BLOCKS, 1.0F, 1.0F);
                level.gameEvent(player, GameEvent.BLOCK_CHANGE, pos);
                if (stack.is(Items.FLINT_AND_STEEL)) {
                    stack.hurtAndBreak(1, player, net.minecraft.world.entity.LivingEntity.getSlotForHand(hand));
                } else if (!player.getAbilities().instabuild) {
                    stack.shrink(1);
                }
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        if (stack.is(ModItems.VOTIVE_SKULL.get()) && !state.getValue(SKULL)) {
            if (!level.isClientSide) {
                level.setBlock(pos, state.setValue(SKULL, true), Block.UPDATE_ALL);
                level.playSound(null, pos, SoundEvents.BONE_BLOCK_PLACE, SoundSource.BLOCKS, 1.0F, 0.9F);
                if (!player.getAbilities().instabuild) {
                    stack.shrink(1);
                }
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    /** Shift-right-click with an empty hand: snuff it out (and take the skull back, if it has one). */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!player.isShiftKeyDown() || (!state.getValue(LIT) && !state.getValue(SKULL))) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            if (state.getValue(LIT)) {
                level.setBlock(pos, state.setValue(LIT, false), Block.UPDATE_ALL);
                level.playSound(null, pos, SoundEvents.CANDLE_EXTINGUISH, SoundSource.BLOCKS, 1.0F, 1.0F);
            } else {
                level.setBlock(pos, state.setValue(SKULL, false), Block.UPDATE_ALL);
                player.getInventory().placeItemBackInInventory(new ItemStack(ModItems.VOTIVE_SKULL.get()));
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(LIT) || random.nextInt(3) != 0) {
            return;
        }
        boolean z = state.getValue(FACING).getAxis() == Direction.Axis.Z;
        double along = (state.getValue(SKULL) ? (random.nextBoolean() ? 2 : 14) : 2.5 + 4 * random.nextInt(4)) / 16.0;
        double x = pos.getX() + (z ? along : 0.5);
        double zz = pos.getZ() + (z ? 0.5 : along);
        level.addParticle(ParticleTypes.SMALL_FLAME, x, pos.getY() + 17 / 16.0, zz, 0, 0, 0);
        if (random.nextInt(4) == 0) {
            level.addParticle(ParticleTypes.SMOKE, x, pos.getY() + 18 / 16.0, zz, 0, 0.01, 0);
        }
    }

    /**
     * Lit racks around {@code radio} that count for it: within their reach, and nearer to it than to any other loaded
     * radio. Capped at {@code maxRacks}.
     */
    public static List<BlockPos> racksFor(Level level, BlockPos radio) {
        int far = Math.max(TarboundConfig.SERVER.rackReach.get(), TarboundConfig.SERVER.rackSkullReach.get());
        List<BlockPos> radios = RadioRegistry.all(level);
        List<BlockPos> out = new java.util.ArrayList<>();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int dy = -1; dy <= 2; dy++) {
            for (int dx = -far; dx <= far; dx++) {
                for (int dz = -far; dz <= far; dz++) {
                    p.set(radio.getX() + dx, radio.getY() + dy, radio.getZ() + dz);
                    if (!level.isLoaded(p)) {
                        continue;
                    }
                    BlockState s = level.getBlockState(p);
                    if (!s.is(ModBlocks.VOTIVE_RACK.get()) || !s.getValue(LIT)) {
                        continue;
                    }
                    int reach = reach(s);
                    if (Math.abs(dx) > reach || Math.abs(dz) > reach || !nearest(radios, p, radio)) {
                        continue;
                    }
                    out.add(p.immutable());
                    if (out.size() >= TarboundConfig.SERVER.maxRacks.get()) {
                        return out;
                    }
                }
            }
        }
        return out;
    }

    /** {@code radio} is the closest loaded radio to {@code rack} (ties go to the lowest position). */
    private static boolean nearest(List<BlockPos> radios, BlockPos rack, BlockPos radio) {
        double mine = radio.distSqr(rack);
        for (BlockPos other : radios) {
            if (other.equals(radio)) {
                continue;
            }
            double d = other.distSqr(rack);
            if (d < mine || (d == mine && other.asLong() < radio.asLong())) {
                return false;
            }
        }
        return true;
    }

    /** The radio couldn't pay: its racks go out. */
    public static void snuff(Level level, List<BlockPos> racks) {
        for (BlockPos p : racks) {
            BlockState s = level.getBlockState(p);
            if (s.is(ModBlocks.VOTIVE_RACK.get()) && s.getValue(LIT)) {
                level.setBlock(p, s.setValue(LIT, false), Block.UPDATE_ALL);
                level.playSound(null, p, SoundEvents.CANDLE_EXTINGUISH, SoundSource.BLOCKS, 1.0F, 0.8F);
            }
        }
    }
}
