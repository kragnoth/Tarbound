package com.tarbound.block;

import com.mojang.serialization.MapCodec;
import com.tarbound.network.PoolAccess;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import java.util.EnumMap;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Tar Antenna. Tuned to a Scrying Font (shift-right-click the radio with the item), it binds what's
 * around it the way touching tar does. It mounts on any face and stacks like dripstone, up to three
 * long; the whole column changes shape as it grows and reaches {@link #reachPerSegment()} blocks per
 * segment: rabbit ears (3), rooftop mast and aerial (6), broadcast mast with a stacked aerial (9).
 */
public class TarAntennaBlock extends Block implements EntityBlock {
    public static final MapCodec<TarAntennaBlock> CODEC = simpleCodec(TarAntennaBlock::new);
    /** The way the antenna points: away from the block it's mounted on. */
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    public static final EnumProperty<Stage> STAGE = EnumProperty.create("stage", Stage.class);
    /** Commune / Scry / Tithe: how the radio may use inventories touching this antenna. Same on every piece. */
    public static final EnumProperty<PoolAccess> MODE = EnumProperty.create("mode", PoolAccess.class);
    public static final int MAX_SEGMENTS = 3;
    public static int reachPerSegment() {
        return com.tarbound.config.TarboundConfig.SERVER.antennaReachPerPiece.get();
    }

    /** Which piece of which size of column this block is (like dripstone thickness). */
    public enum Stage implements StringRepresentable {
        SINGLE("single", 1, 0),
        BASE2("base2", 2, 0),
        TOP2("top2", 2, 1),
        BASE3("base3", 3, 0),
        MID3("mid3", 3, 1),
        TOP3("top3", 3, 2);

        private final String name;
        public final int size;
        public final int index;

        Stage(String name, int size, int index) {
            this.name = name;
            this.size = size;
            this.index = index;
        }

        public static Stage of(int size, int index) {
            for (Stage s : values()) {
                if (s.size == size && s.index == index) {
                    return s;
                }
            }
            return SINGLE;
        }

        public boolean isBase() {
            return index == 0;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    /** Hitboxes drawn pointing up: x0, y0, z0, x1, y1, z1. */
    private static final Map<Stage, double[][]> BOXES_UP = Map.of(
            Stage.SINGLE, new double[][] {{5, 0, 5, 11, 3, 11}, {3, 3, 7, 13, 16, 9}},
            Stage.BASE2, new double[][] {{4, 0, 4, 12, 1, 12}, {6, 1, 6, 10, 16, 10}},
            Stage.TOP2, new double[][] {{7, 0, 7, 9, 10, 9}, {0.5, 10, 0.5, 15.5, 13, 15.5}},
            Stage.BASE3, new double[][] {{3, 0, 3, 13, 1, 13}, {5.5, 1, 5.5, 10.5, 4, 10.5}, {6.75, 1, 6.75, 9.25, 16, 9.25}},
            Stage.MID3, new double[][] {{6.75, 0, 6.75, 9.25, 16, 9.25}, {0.5, 7, 2.5, 15.5, 8.5, 13.5}},
            Stage.TOP3, new double[][] {{6.75, 0, 6.75, 9.25, 16, 9.25}, {0.5, 9, 0.5, 15.5, 14.5, 15.5}});
    private static final Map<Stage, Map<Direction, VoxelShape>> SHAPES = new EnumMap<>(Stage.class);

    static {
        for (Stage stage : Stage.values()) {
            Map<Direction, VoxelShape> byDir = new EnumMap<>(Direction.class);
            for (Direction d : Direction.values()) {
                VoxelShape shape = Shapes.empty();
                for (double[] b : BOXES_UP.get(stage)) {
                    shape = Shapes.or(shape, rotatedBox(b, d));
                }
                byDir.put(d, shape.optimize());
            }
            SHAPES.put(stage, byDir);
        }
    }

    public TarAntennaBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.UP).setValue(STAGE, Stage.SINGLE).setValue(MODE, PoolAccess.COMMUNE));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, STAGE, MODE);
    }

    // ------------------------------------------------------------------------------------------ placement

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Direction facing = context.getClickedFace();
        BlockState state = defaultBlockState().setValue(FACING, facing);
        if (!canSurvive(state, level, pos)) {
            return null;
        }
        if (columnLength(level, pos, facing) > MAX_SEGMENTS) {
            return null;
        }
        // A piece stacked onto a column takes the column's mode.
        BlockState behind = level.getBlockState(pos.relative(facing.getOpposite()));
        if (behind.getBlock() instanceof TarAntennaBlock && behind.getValue(FACING) == facing) {
            state = state.setValue(MODE, behind.getValue(MODE));
        }
        return withStage(level, pos, state);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        // A piece stacked onto a tuned column takes the column's tuning; a tuned piece stacked onto an
        // untuned column tunes the whole column.
        if (level.isClientSide || !(level.getBlockEntity(pos) instanceof TarAntennaBlockEntity antenna)) {
            return;
        }
        if (placer instanceof net.minecraft.world.entity.player.Player player
                && columnLength(level, pos, state.getValue(FACING)) >= MAX_SEGMENTS) {
            com.tarbound.TarboundAdvancements.award(player, com.tarbound.TarboundAdvancements.CELL_TOWER);
        }
        BlockPos base = baseOf(level, pos, state);
        if (base.equals(pos) || !(level.getBlockEntity(base) instanceof TarAntennaBlockEntity baseAntenna)) {
            return;
        }
        if (baseAntenna.link() != null) {
            antenna.setLink(baseAntenna.link(), baseAntenna.linkId());
        } else if (antenna.link() != null) {
            Direction facing = state.getValue(FACING);
            for (BlockPos p = base; !p.equals(pos); p = p.relative(facing)) {
                if (level.getBlockEntity(p) instanceof TarAntennaBlockEntity piece) {
                    piece.setLink(antenna.link(), antenna.linkId());
                }
            }
        }
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        Direction facing = state.getValue(FACING);
        BlockPos behind = pos.relative(facing.getOpposite());
        BlockState support = level.getBlockState(behind);
        if (support.getBlock() instanceof TarAntennaBlock) {
            return support.getValue(FACING) == facing;
        }
        // Anything with something solid to bolt onto: full blocks, chests, barrels, machines...
        return !support.isAir() && !support.getCollisionShape(level, behind).isEmpty();
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level,
            BlockPos pos, BlockPos neighborPos) {
        if (!canSurvive(state, level, pos)) {
            return net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
        }
        Direction facing = state.getValue(FACING);
        if (direction.getAxis() != facing.getAxis()) {
            return state;
        }
        return withStage(level, pos, state);
    }

    /** Recomputes this piece's stage from the antennas behind and ahead of it. */
    private static BlockState withStage(BlockGetter level, BlockPos pos, BlockState state) {
        Direction facing = state.getValue(FACING);
        int behind = countRun(level, pos, facing, facing.getOpposite());
        int ahead = countRun(level, pos, facing, facing);
        int size = Math.min(MAX_SEGMENTS, behind + ahead + 1);
        int index = Math.min(behind, size - 1);
        return state.setValue(STAGE, Stage.of(size, index));
    }

    /** Antenna pieces pointing {@code facing} in a row from {@code pos} (exclusive) towards {@code step}. */
    private static int countRun(BlockGetter level, BlockPos pos, Direction facing, Direction step) {
        int n = 0;
        BlockPos.MutableBlockPos p = pos.mutable();
        for (int i = 0; i < MAX_SEGMENTS + 1; i++) {
            p.move(step);
            BlockState s = level.getBlockState(p);
            if (!(s.getBlock() instanceof TarAntennaBlock) || s.getValue(FACING) != facing) {
                break;
            }
            n++;
        }
        return n;
    }

    /** Length the column at {@code pos} would have with a piece pointing {@code facing} there. */
    public static int columnLength(BlockGetter level, BlockPos pos, Direction facing) {
        return countRun(level, pos, facing, facing.getOpposite()) + countRun(level, pos, facing, facing) + 1;
    }

    /** The mounted end of the column {@code pos} belongs to. */
    public static BlockPos baseOf(BlockGetter level, BlockPos pos, BlockState state) {
        Direction facing = state.getValue(FACING);
        return pos.relative(facing.getOpposite(), countRun(level, pos, facing, facing.getOpposite()));
    }

    /** The free end of the column {@code pos} belongs to. */
    public static BlockPos tipOf(BlockGetter level, BlockPos pos, BlockState state) {
        Direction facing = state.getValue(FACING);
        return pos.relative(facing, countRun(level, pos, facing, facing));
    }

    /** How far a column with this base reaches. */
    public static int reach(BlockState baseState) {
        return baseState.getValue(STAGE).size * reachPerSegment();
    }

    // ------------------------------------------------------------------------------------------ mode

    /** Holding an item, clicks go to the item (placing or extending antennas), not to the mode switch. */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit) {
        return stack.isEmpty() ? ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION : ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
    }

    /** Empty hand: right-click cycles the column's mode; shift-right-click opens its settings (mode and priority). */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player.isSecondaryUseActive()) {
            if (level.isClientSide) {
                com.tarbound.client.AntennaScreen.open(pos); // only ever runs on the client
            } else {
                // The server saw this click (claim and protection mods cancel it before here), so settings for this
                // antenna are allowed from this player until they open another one or log out.
                com.tarbound.net.TarboundPayloads.allowAntennaSettings(player, pos);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        PoolAccess mode = state.getValue(MODE).next();
        if (!level.isClientSide) {
            setColumnMode(level, pos, state, mode);
            level.playSound(null, pos, SoundEvents.COPPER_BULB_TURN_ON, SoundSource.BLOCKS, 0.6F, 0.8F + mode.ordinal() * 0.2F);
            player.displayClientMessage(Component.translatable("message.tarbound.antenna.mode",
                    Component.translatable("mode.tarbound." + mode.getSerializedName()).withStyle(modeColor(mode)),
                    Component.translatable("mode.tarbound." + mode.getSerializedName() + ".desc").withStyle(ChatFormatting.GRAY)), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Server: apply a settings-screen change to the column at {@code pos}. */
    public static void applySettings(Level level, BlockPos pos, PoolAccess mode, int priority) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof TarAntennaBlock)) {
            return;
        }
        if (state.getValue(MODE) != mode) {
            setColumnMode(level, pos, state, mode);
        }
        if (level.getBlockEntity(baseOf(level, pos, state)) instanceof TarAntennaBlockEntity base) {
            base.setPriority(priority);
        }
    }

    public static ChatFormatting modeColor(PoolAccess mode) {
        return switch (mode) {
            case COMMUNE -> ChatFormatting.RED;
            case SCRY -> ChatFormatting.GOLD;
            case TITHE -> ChatFormatting.LIGHT_PURPLE;
        };
    }

    private static void setColumnMode(Level level, BlockPos pos, BlockState state, PoolAccess mode) {
        Direction facing = state.getValue(FACING);
        BlockPos base = baseOf(level, pos, state);
        for (int i = 0; i < MAX_SEGMENTS; i++) {
            BlockPos p = base.relative(facing, i);
            BlockState s = level.getBlockState(p);
            if (!(s.getBlock() instanceof TarAntennaBlock) || s.getValue(FACING) != facing) {
                break;
            }
            level.setBlock(p, s.setValue(MODE, mode), Block.UPDATE_CLIENTS);
        }
    }

    // ------------------------------------------------------------------------------------------ shape & looks

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(STAGE)).get(state.getValue(FACING));
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
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TarAntennaBlockEntity(pos, state);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        // Only the tip of a tuned column whispers.
        if (!com.tarbound.config.TarboundConfig.CLIENT.antennaParticles.get()) {
            return;
        }
        if (level.getBlockState(pos.relative(state.getValue(FACING))).getBlock() instanceof TarAntennaBlock) {
            return;
        }
        if (!(level.getBlockEntity(pos) instanceof TarAntennaBlockEntity antenna) || antenna.link() == null) {
            return;
        }
        int size = state.getValue(STAGE).size;
        if (random.nextInt(8 - size * 2) != 0) {
            return;
        }
        Direction f = state.getValue(FACING);
        double x = pos.getX() + 0.5 + f.getStepX() * 0.45 + (random.nextDouble() - 0.5) * 0.3;
        double y = pos.getY() + 0.5 + f.getStepY() * 0.45 + (random.nextDouble() - 0.5) * 0.3;
        double z = pos.getZ() + 0.5 + f.getStepZ() * 0.45 + (random.nextDouble() - 0.5) * 0.3;
        level.addParticle(random.nextInt(4) == 0 ? ParticleTypes.SOUL : ParticleTypes.SMOKE, x, y, z,
                f.getStepX() * 0.02, f.getStepY() * 0.02 + 0.01, f.getStepZ() * 0.02);
    }

    /** Rotates a box drawn pointing up to point {@code d}, matching the blockstate's model rotations. */
    private static VoxelShape rotatedBox(double[] b, Direction d) {
        double minX = 16, minY = 16, minZ = 16, maxX = 0, maxY = 0, maxZ = 0;
        for (int i = 0; i < 8; i++) {
            double x = (i & 1) == 0 ? b[0] : b[3];
            double y = (i & 2) == 0 ? b[1] : b[4];
            double z = (i & 4) == 0 ? b[2] : b[5];
            double[] p = rotatePoint(x, y, z, d);
            minX = Math.min(minX, p[0]);
            minY = Math.min(minY, p[1]);
            minZ = Math.min(minZ, p[2]);
            maxX = Math.max(maxX, p[0]);
            maxY = Math.max(maxY, p[1]);
            maxZ = Math.max(maxZ, p[2]);
        }
        return Block.box(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private static double[] rotatePoint(double x, double y, double z, Direction d) {
        if (d == Direction.UP) {
            return new double[] {x, y, z};
        }
        if (d == Direction.DOWN) {
            return new double[] {x, 16 - y, 16 - z};
        }
        // Tip over to point north, then turn about Y.
        double nx = x, ny = z, nz = 16 - y;
        return switch (d) {
            case SOUTH -> new double[] {16 - nx, ny, 16 - nz};
            case EAST -> new double[] {16 - nz, ny, nx};
            case WEST -> new double[] {nz, ny, 16 - nx};
            default -> new double[] {nx, ny, nz};
        };
    }
}
