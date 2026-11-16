package com.tarbound.block;

import com.tarbound.contract.ContractData;
import com.tarbound.contract.StandingOrder;
import com.tarbound.network.ReliquaryRegistry;
import com.tarbound.network.AntiMagnet;
import com.tarbound.network.SpiritFire;
import com.tarbound.registry.ModBlockEntities;
import com.tarbound.registry.ModDataComponents;
import com.tarbound.registry.ModItems;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * A Reliquary's spirit. It
 * <ul>
 *   <li>holds up to nine Spirit Contracts, like an AE2 pattern provider: the recipes the machine
 *       beneath it will run for the radio's jobs;</li>
 *   <li>keeps a bound furnace burning without fuel;</li>
 *   <li>feeds FE to a bound machine that takes it (free for now, while costs are worked out);</li>
 *   <li>is tuned to one radio (shift-right-click the radio with the reliquary item) and binds the machine
 *       beneath it to that radio from anywhere in the dimension, found through {@link ReliquaryRegistry};</li>
 *   <li>can be named (anvil, or the box at the top of its screen), so look-alike machines can be
 *       told apart in the radio.</li>
 * </ul>
 */
public class ReliquaryBlockEntity extends BlockEntity implements com.tarbound.network.VitaeFeed.Credit {
    /** Contract slots a Reliquary can open with candles (it starts with one). */
    public static final int CONTRACT_SLOTS = 18;
    /** Reliquaries saved before slots had to be opened keep this many open. */
    private static final int OLD_OPEN_SLOTS = 9;
    public static final int MAX_NAME_LENGTH = 32;
    /** Free power per tick pushed into the bound machine. */
    private static final int TEND_INTERVAL = 4;

    /**
     * Contract slots open so far. A new Reliquary starts with one; each candle fed to it opens another.
     * Reliquaries from before 0.26 (no saved count) keep all nine.
     */
    private int unlocked = 1;

    public int unlocked() {
        return Math.max(1, Math.min(CONTRACT_SLOTS, unlocked));
    }

    /** Opens one more contract slot. @return false if all are open already */
    public boolean unlockSlot() {
        if (unlocked() >= CONTRACT_SLOTS) {
            return false;
        }
        unlocked = unlocked() + 1;
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
        return true;
    }

    /**
     * Closes the last open contract slot (never the first). @return the contract that was in it (maybe
     * empty), or null if only one slot is open
     */
    @Nullable
    public ItemStack lockSlot() {
        if (unlocked() <= 1) {
            return null;
        }
        int last = unlocked() - 1;
        ItemStack held = contracts.getStackInSlot(last).copy();
        contracts.setStackInSlot(last, ItemStack.EMPTY);
        unlocked = last;
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
        return held;
    }

    private final ItemStackHandler contracts = new ItemStackHandler(CONTRACT_SLOTS) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return slot < unlocked() && isContract(stack);
        }

        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }

        @Override
        protected void onContentsChanged(int slot) {
            paused[slot] = false; // a new contract starts running
            setChanged();
        }
    };
    /** Standing contracts the player has paused from the radio, by slot. */
    private final boolean[] paused = new boolean[CONTRACT_SLOTS];

    /** A standing contract in a slot, and whether it's paused. */
    public record StandingSlot(int slot, StandingOrder order, boolean paused) {}

    @Nullable
    private Component customName;
    @Nullable
    private GlobalPos link;
    /** The tuned radio's id (see {@link com.tarbound.network.RadioRegistry}); null for links made before 0.19.1. */
    @Nullable
    private java.util.UUID linkId;
    /** Keeps other mods' item magnets off drops within {@link AntiMagnet#radius()} (the spirits' hoard, toggled with a gold nugget). */
    private boolean antiMagnet;

    public ReliquaryBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.RELIQUARY.get(), pos, state);
    }

    /** A written contract: a normal one, or a standing one. */
    public static boolean isContract(ItemStack stack) {
        if (!stack.is(ModItems.SPIRIT_CONTRACT.get())) {
            return false;
        }
        ContractData data = stack.get(ModDataComponents.CONTRACT.get());
        return (data != null && data.isValid()) || stack.has(ModDataComponents.STANDING.get());
    }

    public boolean antiMagnet() {
        return antiMagnet;
    }

    /** Switches the anti-magnet ward on or off; switching off frees the items it was holding. */
    public void toggleAntiMagnet() {
        if (!antiMagnet && !AntiMagnet.enabled()) {
            return; // switched off in the server config
        }
        antiMagnet = !antiMagnet;
        if (level != null) {
            AntiMagnet.setWard(level, worldPosition, antiMagnet);
            if (!antiMagnet) {
                AntiMagnet.release(level, worldPosition);
            }
        }
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    /** FE paid for in Spirit but not yet delivered (not saved: at most one Spirit's worth is lost on unload). */
    private long feCredit;
    /** Vitae paid for in Spirit but not yet delivered (not saved, like {@link #feCredit}). */
    private long vitaeCredit;

    @Override
    public long vitaeCredit() {
        return vitaeCredit;
    }

    @Override
    public void vitaeCredit(long credit) {
        vitaeCredit = credit;
    }

    /**
     * The loaded radio this spirit is tuned to, found by its id (so a moved radio is still found), or
     * null if it's untuned, unloaded, gone or in another dimension. Server side.
     */
    @Nullable
    public com.tarbound.block.ScryingFontBlockEntity radio() {
        if (level == null || link == null || !link.dimension().equals(level.dimension())) {
            return null;
        }
        BlockPos at = linkId != null ? com.tarbound.network.RadioRegistry.find(level, linkId) : null;
        if (at == null) {
            at = link.pos();
        }
        if (!level.isLoaded(at) || !(level.getBlockEntity(at) instanceof com.tarbound.block.ScryingFontBlockEntity font)) {
            return null;
        }
        return linkId == null || linkId.equals(font.radioId()) ? font : null;
    }

    /** The radio this spirit is tuned to, or null. */
    @Nullable
    public GlobalPos link() {
        return link;
    }

    @Nullable
    public java.util.UUID linkId() {
        return linkId;
    }

    /** Brings the tuning up to date (the radio moved, or a pre-id link learns its radio's id). */
    public void setLink(GlobalPos link, @Nullable java.util.UUID linkId) {
        this.link = link;
        this.linkId = linkId;
        com.tarbound.network.NetworkChanges.mark(level);
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null) {
            if (!level.isClientSide) {
                ReliquaryRegistry.add(level, worldPosition);
                watchMachine();
            }
            AntiMagnet.setWard(level, worldPosition, antiMagnet); // both sides
        }
    }

    /** The machine whose capabilities this Reliquary passes through, while it's being watched. */
    @Nullable
    private BlockPos watching;

    /**
     * A Reliquary hands out the inventory of the machine it faces (see Tarbound's capability registration), so a pipe
     * or hopper that cached that would keep a dead handler after the machine is broken or swapped. NeoForge only
     * invalidates a position's capabilities when its own block changes, so we listen on the machine and pass its
     * invalidations on, the way Modern Dynamics' Machine Extender does (and PowerfulJS for scripted blocks).
     */
    private void watchMachine() {
        if (!(level instanceof net.minecraft.server.level.ServerLevel server)) {
            return;
        }
        BlockState state = getBlockState();
        ReliquaryBlock.Attach attach = state.hasProperty(ReliquaryBlock.ATTACH) ? state.getValue(ReliquaryBlock.ATTACH) : ReliquaryBlock.Attach.DOWN;
        BlockPos machine = attach.dir == null ? null : worldPosition.relative(attach.dir);
        if (java.util.Objects.equals(machine, watching)) {
            return;
        }
        watching = machine;
        if (machine == null) {
            return;
        }
        BlockPos self = worldPosition;
        server.registerCapabilityListener(machine, () -> {
            if (isRemoved() || !machine.equals(watching)) {
                return false; // gone, or facing elsewhere now: stop listening
            }
            server.invalidateCapabilities(self);
            // Re-bound proxies (Entangled) reach a different machine through the same block: learn its slots afresh.
            com.tarbound.network.BoundInventory.forgetProbes(server.getBlockState(machine).getBlock());
            return true;
        });
    }

    @Override
    public void setRemoved() {
        if (level != null) {
            if (!level.isClientSide) {
                ReliquaryRegistry.remove(level, worldPosition);
            }
            AntiMagnet.setWard(level, worldPosition, false);
        }
        super.setRemoved();
    }

    @Override
    public void onChunkUnloaded() {
        if (level != null) {
            if (!level.isClientSide) {
                ReliquaryRegistry.remove(level, worldPosition);
            }
            AntiMagnet.setWard(level, worldPosition, false);
        }
    }

    /** Client side: keep the ward running here too, since magnets predict their pull on the client. */
    public static void clientTick(Level level, BlockPos pos, BlockState state, ReliquaryBlockEntity reliquary) {
        if (reliquary.antiMagnet && (level.getGameTime() + pos.asLong()) % AntiMagnet.INTERVAL == 0) {
            AntiMagnet.apply(level, pos);
        }
        // A flame flickers on one of the circling candles now and then.
        if (com.tarbound.config.TarboundConfig.CLIENT.reliquaryParticles.get() && level.random.nextInt(6) == 0) {
            int n = reliquary.unlocked();
            net.minecraft.world.phys.Vec3 c = candlePos(state, level.random.nextInt(n), n, level.getGameTime() + pos.asLong() % 400);
            level.addParticle(net.minecraft.core.particles.ParticleTypes.SMALL_FLAME,
                    pos.getX() + c.x, pos.getY() + c.y + CANDLE_HEIGHT, pos.getZ() + c.z, 0.0, 0.002, 0.0);
        }
    }

    /** How big the circling candles are drawn, and how tall that makes them (in blocks). */
    public static final float CANDLE_SCALE = 0.45F;
    public static final double CANDLE_HEIGHT = 6.0 / 16.0 * CANDLE_SCALE + 0.02;
    private static final double RING_RADIUS = 0.62;

    /**
     * Where candle {@code i} of {@code n} floats at {@code time} (ticks), relative to the block's corner: a
     * slow ring around the Reliquary, square to the machine it works, bobbing a little.
     */
    public static net.minecraft.world.phys.Vec3 candlePos(BlockState state, int i, int n, double time) {
        ReliquaryBlock.Attach attach = state.hasProperty(ReliquaryBlock.ATTACH) ? state.getValue(ReliquaryBlock.ATTACH) : ReliquaryBlock.Attach.DOWN;
        Direction away = attach.dir == null ? Direction.UP : attach.dir.getOpposite();
        double angle = time / 40.0 + i * (Math.PI * 2 / Math.max(1, n));
        double bob = Math.sin(time / 15.0 + i * 1.7) * 0.04;
        double ca = Math.cos(angle) * RING_RADIUS;
        double sa = Math.sin(angle) * RING_RADIUS;
        double x = 0.5, y = 0.35, z = 0.5;
        switch (away.getAxis()) {
            case Y -> { x += ca; z += sa; y += away.getStepY() * 0.1 + bob; }
            case X -> { y += ca + bob; z += sa; x += away.getStepX() * 0.1; }
            case Z -> { x += ca; y += sa + bob; z += away.getStepZ() * 0.1; }
        }
        return new net.minecraft.world.phys.Vec3(x, y, z);
    }

    /** The spirit's name, or null if it hasn't been given one. */
    @Nullable
    public Component customName() {
        return customName;
    }

    /** Names the spirit; blank clears it. */
    public void setCustomName(@Nullable String name) {
        String trimmed = name == null ? "" : name.strip();
        if (trimmed.length() > MAX_NAME_LENGTH) {
            trimmed = trimmed.substring(0, MAX_NAME_LENGTH);
        }
        customName = trimmed.isEmpty() ? null : Component.literal(trimmed);
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public String customNameString() {
        return customName == null ? "" : customName.getString();
    }

    public ItemStackHandler contractInventory() {
        return contracts;
    }

    /** Contract in each slot (null where empty), in slot order. */
    public List<ContractData> contractSlots() {
        List<ContractData> out = new ArrayList<>(CONTRACT_SLOTS);
        for (int i = 0; i < CONTRACT_SLOTS; i++) {
            ItemStack s = contracts.getStackInSlot(i);
            ContractData data = i < unlocked() && isContract(s) ? s.get(ModDataComponents.CONTRACT.get()) : null;
            out.add(data != null && data.isValid() ? data : null);
        }
        return out;
    }

    /** Every standing contract in this reliquary, paused or not, in slot order. */
    public List<StandingSlot> standingSlots() {
        List<StandingSlot> out = new ArrayList<>();
        for (int i = 0; i < unlocked(); i++) {
            ItemStack s = contracts.getStackInSlot(i);
            StandingOrder o = s.is(ModItems.SPIRIT_CONTRACT.get()) ? s.get(ModDataComponents.STANDING.get()) : null;
            if (o != null) {
                out.add(new StandingSlot(i, o, paused[i]));
            }
        }
        return out;
    }

    /** The standing contracts that are running (not paused), in slot order. */
    public List<StandingOrder> standingOrders() {
        return standingSlots().stream().filter(s -> !s.paused()).map(StandingSlot::order).toList();
    }

    /** Pauses or resumes the standing contract in {@code slot}. */
    public void togglePaused(int slot) {
        if (slot >= 0 && slot < CONTRACT_SLOTS) {
            paused[slot] = !paused[slot];
            setChanged();
        }
    }

    /** The crafting recipe id written on the contract in this reliquary matching {@code contract}, if any. */
    @Nullable
    public net.minecraft.resources.ResourceLocation craftingRecipeFor(ContractData contract) {
        for (int i = 0; i < CONTRACT_SLOTS; i++) {
            ItemStack s = contracts.getStackInSlot(i);
            ContractData data = isContract(s) ? s.get(ModDataComponents.CONTRACT.get()) : null;
            if (data != null && data.sameAs(contract)) {
                return s.get(ModDataComponents.CRAFTING_RECIPE.get());
            }
        }
        return null;
    }

    /** True if the crafting contract in this reliquary matching {@code contract} keeps to its exact items. */
    public boolean exactFor(ContractData contract) {
        for (int i = 0; i < CONTRACT_SLOTS; i++) {
            ItemStack s = contracts.getStackInSlot(i);
            ContractData data = isContract(s) ? s.get(ModDataComponents.CONTRACT.get()) : null;
            if (data != null && data.sameAs(contract)) {
                return s.getOrDefault(ModDataComponents.EXACT_ITEMS.get(), false);
            }
        }
        return false;
    }

    public boolean holds(ContractData contract) {
        for (ContractData c : contractSlots()) {
            if (c != null && c.sameAs(contract)) {
                return true;
            }
        }
        return false;
    }

    public void dropContents(Level level, BlockPos pos) {
        for (int i = 0; i < CONTRACT_SLOTS; i++) {
            ItemStack s = contracts.getStackInSlot(i);
            if (!s.isEmpty()) {
                Block.popResource(level, pos, s.copy());
                contracts.setStackInSlot(i, ItemStack.EMPTY);
            }
        }
    }

    /** The machines this Reliquary works, refreshed now and then (an all-sides one checks its neighbours). */
    private java.util.List<BlockPos> targets = java.util.List.of();
    private long targetsAt = Long.MIN_VALUE;
    private final java.util.Map<BlockPos, BlockCapabilityCache<IEnergyStorage, Direction>> energyCaches = new java.util.HashMap<>();

    /** The wrench turned it: look again right away. */
    public void targetsChanged() {
        targetsAt = Long.MIN_VALUE;
        energyCaches.clear();
        com.tarbound.network.NetworkChanges.mark(level);
        if (level != null && !level.isClientSide) {
            level.invalidateCapabilities(worldPosition); // what it passes through has changed
            watchMachine();
        }
    }

    public java.util.List<BlockPos> targets() {
        if (level == null) {
            return java.util.List.of();
        }
        long now = level.getGameTime();
        if (now - targetsAt >= 20 || targetsAt == Long.MIN_VALUE) {
            targetsAt = now;
            targets = ReliquaryBlock.targets(level, worldPosition, getBlockState());
            energyCaches.keySet().retainAll(targets);
        }
        return targets;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, ReliquaryBlockEntity reliquary) {
        java.util.List<BlockPos> machines = reliquary.targets();
        if ((level.getGameTime() + pos.asLong()) % TEND_INTERVAL == 0 && com.tarbound.config.TarboundConfig.SERVER.spiritFire.get()) {
            // Spirit Fire: a smelt's worth of burn at a time, paid for by the tuned radio.
            for (BlockPos machine : machines) {
                if (!SpiritFire.wantsFire(level, machine)) {
                    continue;
                }
                boolean paid;
                if (com.tarbound.spirit.Spirit.costs()) {
                    var radio = reliquary.radio();
                    paid = radio != null && radio.pay(com.tarbound.config.TarboundConfig.SERVER.spiritFireCost.get());
                } else {
                    paid = true;
                }
                if (paid) {
                    SpiritFire.kindle(level, machine);
                }
            }
        }
        if ((level.getGameTime() + pos.asLong()) % com.tarbound.network.VitaeFeed.INTERVAL == 0 && com.tarbound.network.VitaeFeed.vitae() != null) {
            var radio = reliquary.radio();
            for (BlockPos machine : machines) {
                com.tarbound.network.VitaeFeed.feed(level, pos, machine, radio, reliquary);
            }
        }
        if (reliquary.antiMagnet && (level.getGameTime() + pos.asLong()) % AntiMagnet.INTERVAL == 0) {
            AntiMagnet.apply(level, pos);
        }
        if (level instanceof ServerLevel serverLevel && com.tarbound.config.TarboundConfig.SERVER.machinePower.get()) {
            for (BlockPos machine : machines) {
                Direction side = Direction.getNearest(pos.getX() - machine.getX(), pos.getY() - machine.getY(), pos.getZ() - machine.getZ());
                BlockCapabilityCache<IEnergyStorage, Direction> cache = reliquary.energyCaches.computeIfAbsent(machine,
                        m -> BlockCapabilityCache.create(Capabilities.EnergyStorage.BLOCK, serverLevel, m, side));
                powerMachine(reliquary, cache.getCapability());
            }
        }
    }

    /** Feeds FE to one machine: the radio's battery first, then Spirit bought a little at a time. */
    private static void powerMachine(ReliquaryBlockEntity reliquary, @Nullable IEnergyStorage sink) {
        int fe = com.tarbound.config.TarboundConfig.SERVER.freeFePerTick.get();
        if (fe <= 0 || sink == null || !sink.canReceive()) {
            return;
        }
        int wanted = sink.receiveEnergy(fe, true);
        if (wanted <= 0) {
            return;
        }
        if (!com.tarbound.spirit.Spirit.costs()) {
            sink.receiveEnergy(fe, false);
            return;
        }
        var bank = reliquary.radio();
        if (bank != null && bank.energy() > 0) {
            int took = sink.receiveEnergy(bank.extractBattery(wanted, true), false);
            bank.extractBattery(took, false);
            wanted -= took;
        }
        if (wanted > 0 && reliquary.feCredit < wanted && bank != null && bank.pay(1)) {
            reliquary.feCredit += com.tarbound.config.TarboundConfig.SERVER.fePerSpirit.get();
        }
        int give = (int) Math.min(wanted, reliquary.feCredit);
        if (wanted > 0 && give > 0) {
            reliquary.feCredit -= sink.receiveEnergy(give, false);
        }
    }

    @Override
    protected void applyImplicitComponents(DataComponentInput input) {
        super.applyImplicitComponents(input);
        customName = input.get(DataComponents.CUSTOM_NAME);
        GlobalPos fromItem = input.get(ModDataComponents.RADIO_LINK.get());
        if (fromItem != null) {
            link = fromItem;
            linkId = input.get(ModDataComponents.RADIO_ID.get());
        }
        Integer slots = input.get(ModDataComponents.CONTRACT_SLOTS.get());
        if (slots != null) {
            unlocked = slots;
        }
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        if (customName != null) {
            components.set(DataComponents.CUSTOM_NAME, customName);
        }
        if (link != null) {
            components.set(ModDataComponents.RADIO_LINK.get(), link);
            if (linkId != null) {
                components.set(ModDataComponents.RADIO_ID.get(), linkId);
            }
        }
        components.set(ModDataComponents.CONTRACT_SLOTS.get(), unlocked());
    }

    @Override
    public void removeComponentsFromTag(CompoundTag tag) {
        tag.remove("unlocked");
        tag.remove("CustomName");
        tag.remove("link");
        tag.remove("link_id");
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("contracts", contracts.serializeNBT(registries));
        tag.putInt("unlocked", unlocked());
        byte[] p = new byte[CONTRACT_SLOTS];
        for (int i = 0; i < CONTRACT_SLOTS; i++) {
            p[i] = (byte) (paused[i] ? 1 : 0);
        }
        tag.putByteArray("paused", p);
        tag.putBoolean("anti_magnet", antiMagnet);
        if (link != null) {
            CompoundTag l = new CompoundTag();
            l.putString("dimension", link.dimension().location().toString());
            l.putLong("pos", link.pos().asLong());
            tag.put("link", l);
            if (linkId != null) {
                tag.putUUID("link_id", linkId);
            }
        }
        if (customName != null) {
            tag.putString("CustomName", Component.Serializer.toJson(customName, registries));
        }
    }

    /** Clients get the name, tuning and anti-magnet state (not the contracts) so they can show them. */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("anti_magnet", antiMagnet);
        tag.putInt("unlocked", unlocked()); // the candles circling it
        if (link != null) {
            CompoundTag l = new CompoundTag();
            l.putString("dimension", link.dimension().location().toString());
            l.putLong("pos", link.pos().asLong());
            tag.put("link", l);
            if (linkId != null) {
                tag.putUUID("link_id", linkId);
            }
        }
        if (customName != null) {
            tag.putString("CustomName", Component.Serializer.toJson(customName, registries));
        }
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("contracts")) {
            // Saved before 0.29 with nine slots: deserializing would shrink the inventory to nine, so
            // read into a scratch handler and copy the contracts across, keeping their slots.
            ItemStackHandler saved = new ItemStackHandler(CONTRACT_SLOTS);
            saved.deserializeNBT(registries, tag.getCompound("contracts"));
            for (int i = 0; i < CONTRACT_SLOTS; i++) {
                contracts.setStackInSlot(i, i < saved.getSlots() ? saved.getStackInSlot(i) : ItemStack.EMPTY);
            }
        }
        // No count saved: a Reliquary from before slots had to be opened keeps the nine it had.
        unlocked = tag.contains("unlocked") ? tag.getInt("unlocked") : OLD_OPEN_SLOTS;
        boolean wasWarding = antiMagnet;
        antiMagnet = tag.getBoolean("anti_magnet");
        if (level != null && level.isClientSide && wasWarding != antiMagnet) {
            AntiMagnet.setWard(level, worldPosition, antiMagnet);
            if (!antiMagnet) {
                AntiMagnet.release(level, worldPosition);
            }
        }
        if (tag.contains("paused")) {
            byte[] p = tag.getByteArray("paused");
            for (int i = 0; i < CONTRACT_SLOTS; i++) {
                paused[i] = i < p.length && p[i] != 0;
            }
        }
        link = null;
        linkId = tag.hasUUID("link_id") ? tag.getUUID("link_id") : null;
        if (tag.contains("link", Tag.TAG_COMPOUND)) {
            CompoundTag l = tag.getCompound("link");
            ResourceLocation dim = ResourceLocation.tryParse(l.getString("dimension"));
            if (dim != null) {
                link = GlobalPos.of(ResourceKey.create(Registries.DIMENSION, dim), BlockPos.of(l.getLong("pos")));
            }
        }
        customName = tag.contains("CustomName") ? parseCustomNameSafe(tag.getString("CustomName"), registries) : null;
    }
}
