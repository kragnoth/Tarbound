package com.tarbound.block;

import com.mojang.serialization.MapCodec;
import com.tarbound.menu.ScryingFontMenu;
import com.tarbound.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import com.tarbound.registry.ModBlockEntities;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The Scrying Font: a haunted radio. Placed touching a body of tar, it binds up to nine inventories
 * that body touches. Its dial glows, it crackles to itself, and it tunes in when opened.
 */
public class ScryingFontBlock extends Block implements EntityBlock {
    public static final MapCodec<ScryingFontBlock> CODEC = simpleCodec(ScryingFontBlock::new);
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    private static final Component TITLE = Component.translatable("container.tarbound.scrying_font");

    // Cabinet 14 x 9 x 8 px with knobs 1 px proud of the front (antenna left out of the hitbox).
    private static final VoxelShape SHAPE_SOUTH = Block.box(1, 0, 4, 15, 9, 13);
    private static final VoxelShape SHAPE_NORTH = Block.box(1, 0, 3, 15, 9, 12);
    private static final VoxelShape SHAPE_WEST = Block.box(3, 0, 1, 12, 9, 15);
    private static final VoxelShape SHAPE_EAST = Block.box(4, 0, 1, 13, 9, 15);

    public ScryingFontBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.SOUTH));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // Dial faces the player who placed it.
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case NORTH -> SHAPE_NORTH;
            case WEST -> SHAPE_WEST;
            case EAST -> SHAPE_EAST;
            default -> SHAPE_SOUTH;
        };
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || type != ModBlockEntities.SCRYING_FONT.get()) {
            return null;
        }
        return (l, p, s, be) -> ScryingFontBlockEntity.serverTick(l, p, s, (ScryingFontBlockEntity) be);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ScryingFontBlockEntity(pos, state);
    }

    /** Right-click with offerings (candles): feed the spirits as many as fit. */
    @Override
    protected net.minecraft.world.ItemInteractionResult useItemOn(net.minecraft.world.item.ItemStack stack, BlockState state, Level level,
            BlockPos pos, Player player, net.minecraft.world.InteractionHand hand, BlockHitResult hit) {
        if (com.tarbound.spirit.Spirit.value(stack) <= 0) {
            return net.minecraft.world.ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof ScryingFontBlockEntity font) {
            int taken = font.feed(stack, false);
            if (taken > 0) {
                if (!player.getAbilities().instabuild) {
                    stack.shrink(taken);
                }
                level.playSound(null, pos, net.minecraft.sounds.SoundEvents.SOUL_ESCAPE.value(), SoundSource.BLOCKS, 0.8F, 0.7F);
                com.tarbound.TarboundAdvancements.award(player, com.tarbound.TarboundAdvancements.FEED_RADIO);
                player.displayClientMessage(Component.translatable("message.tarbound.spirit.fed", taken,
                        font.spirit(), com.tarbound.spirit.Spirit.capacity()).withStyle(net.minecraft.ChatFormatting.DARK_AQUA), true);
            } else {
                player.displayClientMessage(Component.translatable("message.tarbound.spirit.sated").withStyle(net.minecraft.ChatFormatting.GRAY), true);
            }
        }
        return net.minecraft.world.ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    /** Comparator: how full the Spirit store is (0 = empty). */
    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof ScryingFontBlockEntity font ? font.comparatorSignal() : 0;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        // Broken mid-order: every Shade working for this radio lets go of what it held.
        if (!state.is(newState.getBlock()) && !level.isClientSide && level.getBlockEntity(pos) instanceof ScryingFontBlockEntity font) {
            font.releaseAllShades();
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level.isClientSide) {
            // Played client side only, for the player tuning in, so the client config can mute it.
            if (com.tarbound.config.TarboundConfig.CLIENT.radioSounds.get()) {
                level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, ModSounds.FONT_TUNE.get(), SoundSource.BLOCKS,
                        0.6F, 0.9F + level.random.nextFloat() * 0.2F, false);
            }
        } else {
            player.openMenu(state.getMenuProvider(level, pos), pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected MenuProvider getMenuProvider(BlockState state, Level level, BlockPos pos) {
        return new SimpleMenuProvider(
                (containerId, inventory, player) -> new ScryingFontMenu(containerId, inventory, ContainerLevelAccess.create(level, pos), pos),
                TITLE);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        Direction front = state.getValue(FACING);
        double x = pos.getX() + 0.5 + front.getStepX() * 0.3;
        double z = pos.getZ() + 0.5 + front.getStepZ() * 0.3;

        boolean hungry = level.getBlockEntity(pos) instanceof ScryingFontBlockEntity font && font.hungry();
        // Something drifts out of the speaker grille now and then; a hungry radio only smoulders.
        if (com.tarbound.config.TarboundConfig.CLIENT.radioParticles.get() && random.nextInt(hungry ? 10 : 4) == 0) {
            double spread = 0.35;
            double side = (random.nextDouble() - 0.5) * spread;
            level.addParticle(!hungry && random.nextInt(5) == 0 ? ParticleTypes.SOUL : ParticleTypes.SMOKE,
                    x + front.getClockWise().getStepX() * side,
                    pos.getY() + 0.2 + random.nextDouble() * 0.25,
                    z + front.getClockWise().getStepZ() * side,
                    front.getStepX() * 0.01, 0.015, front.getStepZ() * 0.01);
        }

        // ...and it mutters to itself.
        if (com.tarbound.config.TarboundConfig.CLIENT.radioSounds.get() && random.nextInt(60) == 0) {
            level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.3, pos.getZ() + 0.5, ModSounds.FONT_STATIC.get(),
                    SoundSource.BLOCKS, 0.35F + random.nextFloat() * 0.2F, 0.85F + random.nextFloat() * 0.3F, false);
        }
    }
}
