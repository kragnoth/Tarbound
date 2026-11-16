package com.tarbound.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import com.tarbound.menu.ReliquaryMenu;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import com.tarbound.network.AntiMagnet;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
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
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.util.StringRepresentable;
import javax.annotation.Nullable;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Reliquary: a skull on a fallen book with a candle burning on top. Set against a machine (any side:
 * the book lies against the block clicked on, and a wrench turns it), the spirit bound in the skull
 * works it. Turned past the six sides it becomes a ring of four books working every machine it touches. A reliquary tuned to a radio (shift-right-click the radio with the reliquary
 * item) binds the machine beneath it to that radio from anywhere in the same dimension, with every
 * slot reachable. It also passes the machine's full inventory to
 * anything that pipes into the reliquary.
 */
public class ReliquaryBlock extends Block implements EntityBlock {
    /** The gold glint of a Reliquary whose spirits are hoarding. */
    public static final DustParticleOptions HOARD_GOLD = new DustParticleOptions(new org.joml.Vector3f(1.0F, 0.78F, 0.2F), 1.0F);

    public static final MapCodec<ReliquaryBlock> CODEC = simpleCodec(ReliquaryBlock::new);
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    /** Which of five angles the book lies at under the skull. */
    public static final IntegerProperty BOOK = IntegerProperty.create("book", 0, 4);
    /** Which of five cover colours the book has. */
    public static final IntegerProperty COVER = IntegerProperty.create("cover", 0, 4);
    /** Which machine it works: the block on that side (the book lies against it), or every side. */
    public static final EnumProperty<Attach> ATTACH = EnumProperty.create("attach", Attach.class);

    public enum Attach implements StringRepresentable {
        DOWN("down", Direction.DOWN),
        UP("up", Direction.UP),
        NORTH("north", Direction.NORTH),
        SOUTH("south", Direction.SOUTH),
        WEST("west", Direction.WEST),
        EAST("east", Direction.EAST),
        /** Every side: a ring of four books around a dark heart, working each machine it touches. */
        ALL("all", null);

        private final String name;
        @Nullable
        public final Direction dir;

        Attach(String name, @Nullable Direction dir) {
            this.name = name;
            this.dir = dir;
        }

        public static Attach of(Direction d) {
            for (Attach a : values()) {
                if (a.dir == d) {
                    return a;
                }
            }
            return DOWN;
        }

        public Attach next() {
            return values()[(ordinal() + 1) % values().length];
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    // Book (loose fit for every angle), skull, candle; drawn sitting on a machine below, turned for the other sides.
    private static final double[][] BOXES_DOWN = {{2, 0, 2, 14, 2, 14}, {4, 2, 4, 12, 10, 12}, {7, 10, 7, 9, 16, 9}};
    private static final java.util.Map<Attach, VoxelShape> SHAPES = new java.util.EnumMap<>(Attach.class);

    static {
        for (Attach a : Attach.values()) {
            if (a == Attach.ALL) {
                SHAPES.put(a, Block.box(1, 0, 1, 15, 14, 15));
                continue;
            }
            VoxelShape shape = Shapes.empty();
            for (double[] b : BOXES_DOWN) {
                shape = Shapes.or(shape, rotatedBox(b, a.dir));
            }
            SHAPES.put(a, shape.optimize());
        }
    }

    /** Turns a box drawn sitting on the block below so its bottom lies against {@code toward}. */
    private static VoxelShape rotatedBox(double[] b, Direction toward) {
        double minX = 16, minY = 16, minZ = 16, maxX = 0, maxY = 0, maxZ = 0;
        for (int i = 0; i < 8; i++) {
            double x = (i & 1) == 0 ? b[0] : b[3];
            double y = (i & 2) == 0 ? b[1] : b[4];
            double z = (i & 4) == 0 ? b[2] : b[5];
            double[] p = switch (toward) {
                case DOWN -> new double[] {x, y, z};
                case UP -> new double[] {x, 16 - y, 16 - z};
                case NORTH -> new double[] {x, 16 - z, y};
                case SOUTH -> new double[] {x, z, 16 - y};
                case WEST -> new double[] {y, x, z};
                case EAST -> new double[] {16 - y, x, z};
            };
            minX = Math.min(minX, p[0]);
            minY = Math.min(minY, p[1]);
            minZ = Math.min(minZ, p[2]);
            maxX = Math.max(maxX, p[0]);
            maxY = Math.max(maxY, p[1]);
            maxZ = Math.max(maxZ, p[2]);
        }
        return Block.box(minX, minY, minZ, maxX, maxY, maxZ);
    }

    // ------------------------------------------------------------------------------------------ what it works

    /**
     * The blocks a Reliquary in {@code state} at {@code pos} works: the one it faces, or (all sides) every
     * neighbour that is a machine. See {@link #acceptsAllSides}.
     */
    public static java.util.List<BlockPos> targets(net.minecraft.world.level.LevelReader level, BlockPos pos, BlockState state) {
        Attach a = state.getValue(ATTACH);
        if (a != Attach.ALL) {
            return java.util.List.of(pos.relative(a.dir));
        }
        java.util.List<BlockPos> out = new java.util.ArrayList<>(6);
        for (Direction d : Direction.values()) {
            BlockPos n = pos.relative(d);
            if (acceptsAllSides(level, n)) {
                out.add(n);
            }
        }
        return out;
    }

    /**
     * What an all-sides Reliquary works: any neighbour with slots, tanks or power, except storage. Telling
     * a machine from storage by its slots doesn't work for mods with side-configurable machines (EnderIO
     * shows every side the same), so storage is named instead: vanilla chests, barrels and shulker boxes,
     * and the {@code tarbound:reliquary_ignores} tag (chests, barrels and shulker box tags by default; packs
     * add their drawers and crates). Never the radio, antennas, other Reliquaries, tar or conveyors.
     */
    public static boolean acceptsAllSides(net.minecraft.world.level.LevelReader reader, BlockPos pos) {
        if (!(reader instanceof Level level) || !level.isLoaded(pos)) {
            return false;
        }
        BlockState st = level.getBlockState(pos);
        if (st.isAir() || st.getBlock() instanceof ReliquaryBlock || st.getBlock() instanceof ScryingFontBlock
                || st.getBlock() instanceof TarAntennaBlock || st.getBlock() instanceof TransientAntennaBlock
                || st.getBlock() instanceof net.minecraft.world.level.block.LiquidBlock
                || st.is(com.tarbound.registry.ModTags.NEVER_POOLED) || st.is(com.tarbound.registry.ModTags.RELIQUARY_IGNORES)) {
            return false;
        }
        if (com.tarbound.contract.StationContracts.kindOf(st) != null) {
            return true; // crafting tables, stonecutters, composters
        }
        BlockEntity be = st.hasBlockEntity() ? level.getBlockEntity(pos) : null;
        if (be == null || be instanceof net.minecraft.world.level.block.entity.ChestBlockEntity
                || be instanceof net.minecraft.world.level.block.entity.BarrelBlockEntity
                || be instanceof net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity) {
            return false;
        }
        if (level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK, pos, st, be, null) != null
                || level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.BLOCK, pos, st, be, null) != null
                || level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.BLOCK, pos, st, be, null) != null) {
            return true;
        }
        // Side-configured machines may only answer for a side.
        for (Direction d : Direction.values()) {
            if (level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK, pos, st, be, d) != null
                    || level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.BLOCK, pos, st, be, d) != null
                    || level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.BLOCK, pos, st, be, d) != null) {
                return true;
            }
        }
        return false;
    }

    /** The side a Reliquary is looked for on a machine, first match wins: above, below, then around. */
    private static final Direction[] SEARCH = {Direction.UP, Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};

    /** The Reliquary working the machine at {@code machinePos}, or null. */
    @Nullable
    public static BlockPos reliquaryPosFor(Level level, BlockPos machinePos) {
        for (Direction d : SEARCH) {
            BlockPos p = machinePos.relative(d);
            if (!level.isLoaded(p)) {
                continue;
            }
            BlockState st = level.getBlockState(p);
            if (!(st.getBlock() instanceof ReliquaryBlock)) {
                continue;
            }
            Attach a = st.getValue(ATTACH);
            if (a == Attach.ALL ? acceptsAllSides(level, machinePos) : a.dir == d.getOpposite()) {
                return p;
            }
        }
        return null;
    }

    /** The Reliquary block entity working the machine at {@code machinePos}, or null. */
    @Nullable
    public static ReliquaryBlockEntity reliquaryFor(Level level, BlockPos machinePos) {
        BlockPos p = reliquaryPosFor(level, machinePos);
        return p != null && level.getBlockEntity(p) instanceof ReliquaryBlockEntity r ? r : null;
    }
    private static final Component TITLE = Component.translatable("container.tarbound.reliquary");

    public ReliquaryBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.SOUTH).setValue(BOOK, 2).setValue(COVER, 0)
                .setValue(ATTACH, Attach.DOWN));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, BOOK, COVER, ATTACH);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // The skull turns to watch whoever bound it; the book under it falls however it falls.
        RandomSource random = context.getLevel().random;
        // The book lies against the block clicked on: that's the machine it works.
        return defaultBlockState()
                .setValue(ATTACH, Attach.of(context.getClickedFace().getOpposite()))
                .setValue(FACING, context.getHorizontalDirection().getOpposite())
                .setValue(BOOK, random.nextInt(5))
                .setValue(COVER, random.nextInt(5));
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        Attach a = state.getValue(ATTACH);
        BlockState out = state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
        return a.dir == null ? out : out.setValue(ATTACH, Attach.of(rotation.rotate(a.dir)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(ATTACH));
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (type != ModBlockEntities.RELIQUARY.get()) {
            return null;
        }
        if (level.isClientSide) {
            return (l, p, s, be) -> ReliquaryBlockEntity.clientTick(l, p, s, (ReliquaryBlockEntity) be);
        }
        return (l, p, s, be) -> ReliquaryBlockEntity.serverTick(l, p, s, (ReliquaryBlockEntity) be);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ReliquaryBlockEntity(pos, state);
    }

    /** Comparators read the machine through a Reliquary facing one (as Entangled passes its bound block's signal on). */
    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return state.getValue(ATTACH).dir != null;
    }

    /** The comparator signal of the machine it faces (0 when facing all sides, nothing, or an unloaded chunk). */
    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        Attach a = state.getValue(ATTACH);
        if (a.dir == null) {
            return 0;
        }
        BlockPos machine = pos.relative(a.dir);
        if (!level.isLoaded(machine)) {
            return 0;
        }
        BlockState machineState = level.getBlockState(machine);
        if (machineState.getBlock() instanceof ReliquaryBlock || !machineState.hasAnalogOutputSignal()) {
            return 0;
        }
        int[] depth = com.tarbound.Tarbound.PASS_THROUGH_DEPTH.get();
        if (depth[0] >= 8) {
            return 0; // a loop of pass-through blocks
        }
        depth[0]++;
        try {
            return machineState.getAnalogOutputSignal(level, machine);
        } finally {
            depth[0]--;
        }
    }

    /** The machine's contents changed (vanilla tells every direct neighbour): pass it on to comparators reading this Reliquary. */
    @Override
    public void onNeighborChange(BlockState state, net.minecraft.world.level.LevelReader level, BlockPos pos, BlockPos neighbor) {
        Attach a = state.getValue(ATTACH);
        if (a.dir != null && neighbor.equals(pos.relative(a.dir)) && level instanceof Level l && !l.isClientSide) {
            int[] depth = com.tarbound.Tarbound.PASS_THROUGH_DEPTH.get();
            if (depth[0] > 0) {
                return; // already passing an update on: a machine that echoes it back mustn't start a loop
            }
            depth[0]++;
            try {
                l.updateNeighbourForOutputSignal(pos, this);
            } finally {
                depth[0]--;
            }
        }
    }

    /** Redstone dust switches this Reliquary's anti-magnet ward on or off (the dust isn't used up). */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit) {
        if (TransientAntennaBlock.isWrench(stack)) {
            // A wrench turns it to face the next side; after the six sides, all of them at once.
            Attach next = state.getValue(ATTACH).next();
            if (!level.isClientSide) {
                level.setBlock(pos, state.setValue(ATTACH, next), Block.UPDATE_ALL);
                level.playSound(null, pos, SoundEvents.BONE_BLOCK_PLACE, SoundSource.BLOCKS, 0.7F, 1.2F);
                player.displayClientMessage(Component.translatable("message.tarbound.reliquary.attach",
                        Component.translatable("attach.tarbound." + next.getSerializedName())).withStyle(ChatFormatting.DARK_AQUA), true);
                if (level.getBlockEntity(pos) instanceof ReliquaryBlockEntity reliquary) {
                    reliquary.targetsChanged();
                }
                if (next == Attach.ALL) {
                    com.tarbound.TarboundAdvancements.award(player, com.tarbound.TarboundAdvancements.ALL_SIDES);
                }
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        if (stack.is(net.minecraft.tags.ItemTags.CANDLES)) {
            // A candle offered to the spirit opens one more contract slot (the candle joins the ring).
            if (!level.isClientSide && level.getBlockEntity(pos) instanceof ReliquaryBlockEntity reliquary) {
                if (reliquary.unlockSlot()) {
                    if (!player.getAbilities().instabuild) {
                        stack.shrink(1);
                    }
                    level.playSound(null, pos, SoundEvents.CAKE_ADD_CANDLE, SoundSource.BLOCKS, 1.0F, 0.8F + reliquary.unlocked() * 0.05F);
                    level.playSound(null, pos, SoundEvents.SOUL_ESCAPE.value(), SoundSource.BLOCKS, 0.5F, 1.2F);
                    player.displayClientMessage(Component.translatable("message.tarbound.reliquary.slots",
                            reliquary.unlocked(), ReliquaryBlockEntity.CONTRACT_SLOTS).withStyle(ChatFormatting.GOLD), true);
                    if (reliquary.unlocked() >= 16) {
                        com.tarbound.TarboundAdvancements.award(player, com.tarbound.TarboundAdvancements.SIXTEEN_CANDLES);
                    }
                } else {
                    player.displayClientMessage(Component.translatable("message.tarbound.reliquary.slots_full")
                            .withStyle(ChatFormatting.GRAY), true);
                }
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        // The spirits' hoard (the anti-magnet ward): toss the greedy spirits a gold nugget and they won't let other
        // mods' magnets take what lands near their machine. The nugget is spent when the hoard starts; a nugget
        // shown again lets go for free. (Before 0.58 this was redstone dust; the save key is still anti_magnet.)
        if (!stack.is(Items.GOLD_NUGGET)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof ReliquaryBlockEntity reliquary) {
            boolean was = reliquary.antiMagnet();
            reliquary.toggleAntiMagnet();
            boolean on = reliquary.antiMagnet();
            if (on && !was && !player.getAbilities().instabuild) {
                stack.shrink(1);
            }
            level.playSound(null, pos, on ? SoundEvents.RESPAWN_ANCHOR_CHARGE : SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(),
                    SoundSource.BLOCKS, 0.5F, 1.4F);
            player.displayClientMessage(Component.translatable(on ? "message.tarbound.anti_magnet.on" : "message.tarbound.anti_magnet.off",
                    (int) AntiMagnet.radius()).withStyle(on ? ChatFormatting.RED : ChatFormatting.GRAY), true);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (player.isSecondaryUseActive()) {
            // Shift, empty hand: take back the last candle, and the contract in the slot it closes.
            if (!level.isClientSide && level.getBlockEntity(pos) instanceof ReliquaryBlockEntity reliquary) {
                ItemStack contract = reliquary.lockSlot();
                if (contract == null) {
                    player.displayClientMessage(Component.translatable("message.tarbound.reliquary.last_slot").withStyle(ChatFormatting.GRAY), true);
                } else {
                    giveOrDrop(player, new ItemStack(Items.CANDLE));
                    if (!contract.isEmpty()) {
                        giveOrDrop(player, contract);
                    }
                    level.playSound(null, pos, SoundEvents.CANDLE_EXTINGUISH, SoundSource.BLOCKS, 1.0F, 1.0F);
                    player.displayClientMessage(Component.translatable("message.tarbound.reliquary.slots",
                            reliquary.unlocked(), ReliquaryBlockEntity.CONTRACT_SLOTS).withStyle(ChatFormatting.GOLD), true);
                }
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof ReliquaryBlockEntity reliquary) {
            Component title = reliquary.customName() != null ? reliquary.customName() : TITLE;
            String name = reliquary.customNameString();
            player.openMenu(new SimpleMenuProvider(
                    (id, inventory, p) -> new ReliquaryMenu(id, inventory, reliquary.contractInventory(), ContainerLevelAccess.create(level, pos), pos, name,
                            reliquary.unlocked()),
                    title), buf -> {
                        buf.writeBlockPos(pos);
                        buf.writeVarInt(reliquary.unlocked());
                        buf.writeUtf(name, ReliquaryBlockEntity.MAX_NAME_LENGTH * 4);
                    });
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private static void giveOrDrop(Player player, ItemStack stack) {
        if (!player.getInventory().add(stack) || !stack.isEmpty()) {
            player.drop(stack, false);
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof ReliquaryBlockEntity reliquary) {
            reliquary.dropContents(level, pos);
            if (reliquary.antiMagnet()) {
                AntiMagnet.release(level, pos); // let magnets have the items again
            }
            AntiMagnet.setWard(level, pos, false);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        // Candle flame on the skull's crown, pointing away from the machine.
        Attach attach = state.getValue(ATTACH);
        Direction away = attach.dir == null ? Direction.UP : attach.dir.getOpposite();
        double fx = pos.getX() + 0.5 + away.getStepX() * 0.6;
        double fy = pos.getY() + 0.5 + away.getStepY() * 0.6;
        double fz = pos.getZ() + 0.5 + away.getStepZ() * 0.6;
        boolean particles = com.tarbound.config.TarboundConfig.CLIENT.reliquaryParticles.get();
        if (attach == Attach.ALL) {
            // No candle on the ring of books: the eyes in the dark smoulder instead.
            if (particles && random.nextInt(6) == 0) {
                level.addParticle(DustParticleOptions.REDSTONE, pos.getX() + 0.3 + random.nextDouble() * 0.4,
                        pos.getY() + 0.85, pos.getZ() + 0.3 + random.nextDouble() * 0.4, 0.0, 0.0, 0.0);
            }
        } else if (particles && random.nextInt(3) == 0) {
            level.addParticle(ParticleTypes.SMALL_FLAME, fx, fy, fz, 0.0, 0.0, 0.0);
        }
        if (particles && attach != Attach.ALL && random.nextInt(8) == 0) {
            level.addParticle(ParticleTypes.SMOKE, fx, fy + 0.05, fz, 0.0, 0.01, 0.0);
        }
        // The bound spirit stirs in the skull's eyes now and then.
        if (particles && random.nextInt(10) == 0) {
            level.addParticle(ParticleTypes.SOUL,
                    pos.getX() + 0.35 + random.nextDouble() * 0.3,
                    pos.getY() + 0.3 + random.nextDouble() * 0.3,
                    pos.getZ() + 0.35 + random.nextDouble() * 0.3,
                    0.0, 0.01, 0.0);
        }
        // The spirits' hoard: wisps snatch inward from the edge of its reach, and a glint of gold at the book.
        if (level.getBlockEntity(pos) instanceof ReliquaryBlockEntity reliquary && reliquary.antiMagnet()
                && com.tarbound.config.TarboundConfig.CLIENT.wardParticles.get() && random.nextInt(3) == 0) {
            double a = random.nextDouble() * Math.PI * 2;
            double r = AntiMagnet.radius() * (0.85 + random.nextDouble() * 0.15);
            double dx = -Math.cos(a), dz = -Math.sin(a);
            // A soul wisp keeps its speed (friction 0.96, ~30 ticks): about 0.04 per block of reach carries it most of the way home.
            double v = 0.04 * r;
            level.addParticle(ParticleTypes.SOUL, pos.getX() + 0.5 - dx * r, pos.getY() + 0.2 + random.nextDouble() * 1.2,
                    pos.getZ() + 0.5 - dz * r, dx * v, -0.005, dz * v);
            if (random.nextInt(3) == 0) {
                level.addParticle(HOARD_GOLD, fx, pos.getY() + 0.55, fz, 0.0, 0.0, 0.0);
            }
        }
        if (com.tarbound.config.TarboundConfig.CLIENT.reliquarySounds.get() && random.nextInt(200) == 0) {
            level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.4, pos.getZ() + 0.5, SoundEvents.SOUL_ESCAPE.value(),
                    SoundSource.BLOCKS, 0.4F, 0.6F + random.nextFloat() * 0.3F, false);
        }
    }
}
