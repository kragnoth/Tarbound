package com.tarbound.block;

import com.mojang.serialization.MapCodec;
import java.util.EnumMap;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Transient Antenna: a tuned port between the pool and the one block it's mounted on, anywhere in the
 * radio's dimension. Glowstone dust cycles its mode:
 * <ul>
 *   <li><b>Idle:</b> does nothing.</li>
 *   <li><b>Pull:</b> takes items (finished outputs only, from a machine), fluids and FE out of the block
 *       into the pool and the radio's battery. Finishes autocrafts on machines out of the pool's reach.</li>
 *   <li><b>Push:</b> sends its filter item (or the fluid in a filter bucket) and FE from the pool into the block.</li>
 * </ul>
 * Shift-right-click with an item sets the push filter; with an empty hand clears it.
 */
public class TransientAntennaBlock extends Block implements EntityBlock {
    public static final MapCodec<TransientAntennaBlock> CODEC = simpleCodec(TransientAntennaBlock::new);
    /** Away from the block it's mounted on. */
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    public static final EnumProperty<Mode> MODE = EnumProperty.create("mode", Mode.class);

    public enum Mode implements StringRepresentable {
        IDLE("idle", ChatFormatting.GRAY),
        PULL("pull", ChatFormatting.GREEN),
        PUSH("push", ChatFormatting.LIGHT_PURPLE);

        private final String name;
        public final ChatFormatting color;

        Mode(String name, ChatFormatting color) {
            this.name = name;
            this.color = color;
        }

        public Mode next() {
            return values()[(ordinal() + 1) % values().length];
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    /** The rabbit-ears footprint, pointing up. */
    private static final double[][] BOXES_UP = {{5, 0, 5, 11, 3, 11}, {3, 3, 7, 13, 16, 9}};
    private static final Map<Direction, VoxelShape> SHAPES = new EnumMap<>(Direction.class);

    static {
        for (Direction d : Direction.values()) {
            VoxelShape shape = Shapes.empty();
            for (double[] b : BOXES_UP) {
                shape = Shapes.or(shape, rotatedBox(b, d));
            }
            SHAPES.put(d, shape.optimize());
        }
    }

    public TransientAntennaBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.UP).setValue(MODE, Mode.IDLE));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, MODE);
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = defaultBlockState().setValue(FACING, context.getClickedFace());
        return canSurvive(state, context.getLevel(), context.getClickedPos()) ? state : null;
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockPos behind = pos.relative(state.getValue(FACING).getOpposite());
        BlockState support = level.getBlockState(behind);
        return !support.isAir() && !support.getCollisionShape(level, behind).isEmpty();
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level,
            BlockPos pos, BlockPos neighborPos) {
        return canSurvive(state, level, pos) ? state : Blocks.AIR.defaultBlockState();
    }

    /** Any wrench (the common {@code c:tools/wrench} tag) cycles the mode like glowstone, for free. */
    public static boolean isWrench(ItemStack stack) {
        return stack.is(net.neoforged.neoforge.common.Tags.Items.TOOLS_WRENCH);
    }

    /** The block it's mounted on. */
    public static BlockPos target(BlockPos pos, BlockState state) {
        return pos.relative(state.getValue(FACING).getOpposite());
    }

    // ------------------------------------------------------------------------------------------ use

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit) {
        boolean wrench = isWrench(stack);
        if (stack.is(Items.GLOWSTONE_DUST) || wrench) {
            Mode mode = state.getValue(MODE).next();
            if (!level.isClientSide) {
                level.setBlock(pos, state.setValue(MODE, mode), Block.UPDATE_ALL);
                if (!wrench && !player.getAbilities().instabuild) {
                    stack.shrink(1); // glowstone is spent; a wrench is never touched
                }
                level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.0F, 0.7F + mode.ordinal() * 0.3F);
                player.displayClientMessage(Component.translatable("message.tarbound.transient.mode",
                        Component.translatable("mode.tarbound.transient." + mode.getSerializedName()).withStyle(mode.color),
                        Component.translatable("mode.tarbound.transient." + mode.getSerializedName() + ".desc").withStyle(ChatFormatting.GRAY)), true);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    /**
     * Shift-right-click with an item sets the push filter. Vanilla hands a sneaking click straight to the
     * held item (a bucket would pour, a block would be placed), so it's caught here first, on both sides.
     */
    public static void onRightClickBlock(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        ItemStack stack = event.getItemStack();
        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        if (!player.isSecondaryUseActive() || stack.isEmpty() || stack.is(Items.GLOWSTONE_DUST) || isWrench(stack)
                || !(level.getBlockState(pos).getBlock() instanceof TransientAntennaBlock)) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.sidedSuccess(level.isClientSide));
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof TransientAntennaBlockEntity antenna) {
            antenna.setFilter(stack);
            player.displayClientMessage(Component.translatable("message.tarbound.transient.filter", describe(antenna.filter()))
                    .withStyle(ChatFormatting.DARK_AQUA), true);
            level.playSound(null, pos, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 0.6F, 1.2F);
        }
    }

    /** What a push filter sends: a filled bucket (or any fluid container) means its fluid, not the container. */
    static Component describe(ItemStack filter) {
        var fluid = net.neoforged.neoforge.fluids.FluidUtil.getFluidContained(filter);
        return fluid.isPresent() && !fluid.get().isEmpty()
                ? Component.translatable("message.tarbound.transient.fluid", fluid.get().getHoverName())
                : filter.getHoverName();
    }

    /** Empty hand: shift clears the push filter; a plain click says what the antenna is doing. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof TransientAntennaBlockEntity antenna) {
            if (player.isSecondaryUseActive()) {
                antenna.setFilter(ItemStack.EMPTY);
                player.displayClientMessage(Component.translatable("message.tarbound.transient.filter_cleared")
                        .withStyle(ChatFormatting.DARK_AQUA), true);
            } else {
                Mode mode = state.getValue(MODE);
                Component filter = antenna.filter().isEmpty()
                        ? Component.translatable("message.tarbound.transient.no_filter")
                        : describe(antenna.filter());
                Component status = Component.translatable(antenna.link() == null ? "message.tarbound.transient.status_untuned"
                                : "message.tarbound.transient.status",
                        Component.translatable("mode.tarbound.transient." + mode.getSerializedName()).withStyle(mode.color), filter);
                Component problem = antenna.pushProblem(level, state);
                if (problem == null) {
                    player.displayClientMessage(status, true);
                } else {
                    // Two lines, so in chat: what it's set to, and why nothing's moving.
                    player.sendSystemMessage(status);
                    player.sendSystemMessage(problem.copy().withStyle(ChatFormatting.GOLD));
                }
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    // ------------------------------------------------------------------------------------------ block entity

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TransientAntennaBlockEntity(pos, state);
    }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || type != com.tarbound.registry.ModBlockEntities.TRANSIENT_ANTENNA.get()) {
            return null;
        }
        return (l, p, s, be) -> TransientAntennaBlockEntity.serverTick(l, p, s, (TransientAntennaBlockEntity) be);
    }

    // ------------------------------------------------------------------------------------------ looks

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
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
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        Mode mode = state.getValue(MODE);
        if (mode == Mode.IDLE || !com.tarbound.config.TarboundConfig.CLIENT.antennaParticles.get() || random.nextInt(3) != 0) {
            return;
        }
        Direction f = state.getValue(FACING);
        double x = pos.getX() + 0.5 + f.getStepX() * 0.4 + (random.nextDouble() - 0.5) * 0.4;
        double y = pos.getY() + 0.5 + f.getStepY() * 0.4 + (random.nextDouble() - 0.5) * 0.4;
        double z = pos.getZ() + 0.5 + f.getStepZ() * 0.4 + (random.nextDouble() - 0.5) * 0.4;
        // Portal motes drift in when pulling, out when pushing.
        double s = mode == Mode.PULL ? -0.4 : 0.4;
        level.addParticle(ParticleTypes.PORTAL, x, y, z, f.getStepX() * s, f.getStepY() * s, f.getStepZ() * s);
    }

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
        double nx = x, ny = z, nz = 16 - y;
        return switch (d) {
            case SOUTH -> new double[] {16 - nx, ny, 16 - nz};
            case EAST -> new double[] {16 - nz, ny, nx};
            case WEST -> new double[] {nz, ny, 16 - nx};
            default -> new double[] {nx, ny, nz};
        };
    }
}
