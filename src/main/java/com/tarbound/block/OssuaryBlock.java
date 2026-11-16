package com.tarbound.block;

import com.mojang.serialization.MapCodec;
import com.tarbound.menu.OssuaryMenu;
import com.tarbound.network.ReliquaryRegistry;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The Ossuary: a little flight of bone steps crowded with candles. Tuned to a radio, it opens every
 * Reliquary tuned to that radio on one screen, to hand out and take back contracts from anywhere.
 */
public class OssuaryBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final MapCodec<OssuaryBlock> CODEC = simpleCodec(OssuaryBlock::new);
    private static final Map<Direction, VoxelShape> SHAPES = new EnumMap<>(Direction.class);

    static {
        // Steps rise toward the back (the side facing away from the player who placed it).
        for (Direction d : Direction.Plane.HORIZONTAL) {
            VoxelShape s = Shapes.or(box(d, 0, 0, 16, 5, 16), box(d, 0, 5, 16, 10, 11), box(d, 0, 10, 16, 15, 6));
            SHAPES.put(d, s);
        }
    }

    /**
     * A full-width step {@code y0..y1} reaching {@code depth} pixels in from the back, for an Ossuary
     * facing {@code d} (its low front step toward {@code d}, its back away from it).
     */
    private static VoxelShape box(Direction d, double x0, double y0, double x1, double y1, double depth) {
        return switch (d) {
            case NORTH -> Block.box(x0, y0, 16 - depth, x1, y1, 16);
            case SOUTH -> Block.box(x0, y0, 0, x1, y1, depth);
            case WEST -> Block.box(16 - depth, y0, x0, 16, y1, x1);
            default -> Block.box(0, y0, x0, depth, y1, x1); // EAST
        };
    }

    public OssuaryBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // The low step faces the player.
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
        return SHAPES.get(state.getValue(FACING));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new OssuaryBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide || !(level.getBlockEntity(pos) instanceof OssuaryBlockEntity ossuary)) {
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (ossuary.link() == null) {
            player.displayClientMessage(Component.translatable("message.tarbound.ossuary.untuned").withStyle(ChatFormatting.GOLD), true);
            return InteractionResult.CONSUME;
        }
        ScryingFontBlockEntity font = ossuary.radio();
        if (font == null) {
            player.displayClientMessage(Component.translatable("message.tarbound.ossuary.no_radio").withStyle(ChatFormatting.GOLD), true);
            return InteractionResult.CONSUME;
        }
        List<ReliquaryBlockEntity> reliquaries = new ArrayList<>();
        for (BlockPos p : ReliquaryRegistry.tunedTo(level, font.getBlockPos())) {
            if (level.getBlockEntity(p) instanceof ReliquaryBlockEntity r) {
                reliquaries.add(r);
            }
        }
        if (reliquaries.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.tarbound.ossuary.empty").withStyle(ChatFormatting.GOLD), true);
            return InteractionResult.CONSUME;
        }
        // Named spirits first, alphabetically; then the rest by machine, then distance.
        reliquaries.sort(Comparator.<ReliquaryBlockEntity, Boolean>comparing(r -> r.customNameString().isEmpty())
                .thenComparing(r -> sortName(level, r))
                .thenComparingDouble(r -> r.getBlockPos().distSqr(pos)));
        List<ReliquaryBlockEntity> fixed = List.copyOf(reliquaries);
        player.openMenu(new SimpleMenuProvider((id, inventory, p) -> OssuaryMenu.server(id, inventory, level, pos, fixed),
                Component.translatable("block.tarbound.ossuary")), buf -> OssuaryMenu.writeEntries(buf, level, fixed));
        return InteractionResult.CONSUME;
    }

    private static String sortName(Level level, ReliquaryBlockEntity r) {
        if (!r.customNameString().isEmpty()) {
            return r.customNameString().toLowerCase(java.util.Locale.ROOT);
        }
        OssuaryMenu.Entry e = OssuaryMenu.entryFor(level, r);
        return e.machineState().getBlock().getName().getString().toLowerCase(java.util.Locale.ROOT);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(3) != 0) {
            return;
        }
        // A flicker over one of the candles.
        Direction f = state.getValue(FACING);
        // Wick tops for a north-facing Ossuary (low step at the front, z=0), turned like the model.
        double[][] wicks = {{0.19, 0.75, 0.19}, {0.75, 0.62, 0.125}, {0.375, 0.97, 0.5}, {0.69, 0.84, 0.44}, {0.25, 1.31, 0.81}, {0.75, 1.25, 0.81}};
        double[] w = wicks[random.nextInt(wicks.length)];
        double x = w[0], z = w[2];
        switch (f) {
            case EAST -> { x = 1 - w[2]; z = w[0]; }
            case SOUTH -> { x = 1 - w[0]; z = 1 - w[2]; }
            case WEST -> { x = w[2]; z = 1 - w[0]; }
            default -> { }
        }
        level.addParticle(ParticleTypes.SMALL_FLAME, pos.getX() + x, pos.getY() + w[1], pos.getZ() + z, 0, 0, 0);
        if (random.nextInt(4) == 0) {
            level.addParticle(ParticleTypes.SMOKE, pos.getX() + x, pos.getY() + w[1] + 0.05, pos.getZ() + z, 0, 0.01, 0);
        }
    }
}
