package com.tarbound.block;

import com.tarbound.config.TarboundConfig;
import com.tarbound.network.BoundInventory;
import com.tarbound.network.PoolAccess;
import com.tarbound.network.RadioRegistry;
import com.tarbound.network.TarNetwork;
import com.tarbound.registry.ModBlockEntities;
import com.tarbound.registry.ModDataComponents;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

/** A Transient Antenna's tuning and push filter, and the pulling and pushing itself (see {@link TransientAntennaBlock}). */
public class TransientAntennaBlockEntity extends BlockEntity {
    @Nullable
    private GlobalPos link;
    @Nullable
    private java.util.UUID linkId;
    private ItemStack filter = ItemStack.EMPTY;

    public TransientAntennaBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TRANSIENT_ANTENNA.get(), pos, state);
    }

    @Nullable
    public GlobalPos link() {
        return link;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide) {
            com.tarbound.network.TransientRegistry.add(level, worldPosition);
        }
    }

    @Override
    public void setRemoved() {
        if (level != null && !level.isClientSide) {
            com.tarbound.network.TransientRegistry.remove(level, worldPosition);
        }
        super.setRemoved();
    }

    @Override
    public void onChunkUnloaded() {
        if (level != null && !level.isClientSide) {
            com.tarbound.network.TransientRegistry.remove(level, worldPosition);
        }
    }

    public ItemStack filter() {
        return filter;
    }

    public void setFilter(ItemStack stack) {
        filter = stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1);
        setChanged();
    }

    /** The loaded radio this antenna is tuned to, or null (untuned, unloaded, gone, another dimension). */
    @Nullable
    private ScryingFontBlockEntity radio() {
        if (level == null || link == null || !link.dimension().equals(level.dimension())) {
            return null;
        }
        BlockPos at = linkId != null ? RadioRegistry.find(level, linkId) : null;
        if (at == null) {
            at = link.pos();
        }
        if (!level.isLoaded(at) || !(level.getBlockEntity(at) instanceof ScryingFontBlockEntity font)) {
            return null;
        }
        if (linkId != null && !linkId.equals(font.radioId())) {
            return null;
        }
        if (!at.equals(link.pos()) || linkId == null) {
            link = GlobalPos.of(level.dimension(), at); // the radio moved (or an old link learns its id)
            linkId = font.radioId();
            setChanged();
        }
        return font;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, TransientAntennaBlockEntity antenna) {
        TransientAntennaBlock.Mode mode = state.getValue(TransientAntennaBlock.MODE);
        if (mode == TransientAntennaBlock.Mode.IDLE || (level.getGameTime() + pos.asLong()) % TarboundConfig.SERVER.transientInterval.get() != 0) {
            return;
        }
        BlockPos target = TransientAntennaBlock.target(pos, state);
        if (!level.isLoaded(target)) {
            return;
        }
        ScryingFontBlockEntity radio = antenna.radio();
        if (radio == null) {
            return;
        }
        TarNetwork net = radio.network();
        Direction side = state.getValue(TransientAntennaBlock.FACING); // the face of the target the antenna sits on
        if (mode == TransientAntennaBlock.Mode.PULL) {
            pull(level, target, side, net, antenna.pushers(level, radio, target));
        } else {
            antenna.push(level, target, side, net);
        }
    }

    // ------------------------------------------------------------------------------------------ direct routing

    /**
     * A push-mode antenna on the same radio, as a direct destination for what a pulling one takes: a pull
     * on an altar and a push on a tank pipe straight across, with no tank in the pool needed.
     */
    private record Pusher(BlockPos target, Direction side, ItemStack filter) {}

    private List<Pusher> pushers(Level level, ScryingFontBlockEntity radio, BlockPos pullTarget) {
        List<Pusher> out = new ArrayList<>();
        GlobalPos radioPos = GlobalPos.of(level.dimension(), radio.getBlockPos());
        for (BlockPos p : com.tarbound.network.TransientRegistry.all(level)) {
            if (p.equals(worldPosition) || !level.isLoaded(p)) {
                continue;
            }
            BlockState st = level.getBlockState(p);
            if (!(st.getBlock() instanceof TransientAntennaBlock) || st.getValue(TransientAntennaBlock.MODE) != TransientAntennaBlock.Mode.PUSH
                    || !(level.getBlockEntity(p) instanceof TransientAntennaBlockEntity other)
                    || !RadioRegistry.matches(other.link, other.linkId, radioPos, radio.radioId())) {
                continue;
            }
            BlockPos t = TransientAntennaBlock.target(p, st);
            if (!t.equals(pullTarget) && level.isLoaded(t)) {
                out.add(new Pusher(t, st.getValue(TransientAntennaBlock.FACING), other.filter));
            }
        }
        return out;
    }

    /** Hands {@code stack} to pushers whose filter is this item. @return what none of them took */
    private static ItemStack routeItems(Level level, List<Pusher> pushers, ItemStack stack, boolean simulate) {
        ItemStack rest = stack.copy();
        for (Pusher p : pushers) {
            if (rest.isEmpty()) {
                break;
            }
            if (p.filter().isEmpty() || !ItemStack.isSameItemSameComponents(p.filter(), rest)) {
                continue;
            }
            BlockState st = level.getBlockState(p.target());
            IItemHandler h = itemHandler(level, p.target(), st, st.hasBlockEntity() ? level.getBlockEntity(p.target()) : null, p.side());
            if (h != null) {
                rest = ItemHandlerHelper.insertItem(h, rest, simulate);
            }
        }
        return rest;
    }

    /** Hands fluid to pushers whose filter holds this fluid. @return mB they took */
    private static int routeFluid(Level level, List<Pusher> pushers, FluidStack fluid, boolean simulate) {
        int left = fluid.getAmount();
        IFluidHandler.FluidAction action = simulate ? IFluidHandler.FluidAction.SIMULATE : IFluidHandler.FluidAction.EXECUTE;
        for (Pusher p : pushers) {
            if (left <= 0) {
                break;
            }
            FluidStack wanted = p.filter().isEmpty() ? FluidStack.EMPTY : FluidUtil.getFluidContained(p.filter()).orElse(FluidStack.EMPTY);
            if (wanted.isEmpty() || !FluidStack.isSameFluidSameComponents(wanted, fluid)) {
                continue;
            }
            BlockState st = level.getBlockState(p.target());
            IFluidHandler h = fluidHandler(level, p.target(), st, st.hasBlockEntity() ? level.getBlockEntity(p.target()) : null, p.side());
            if (h != null) {
                left -= h.fill(fluid.copyWithAmount(left), action);
            }
        }
        return fluid.getAmount() - left;
    }

    /** Hands FE to pushers (they all push power). @return FE they took */
    private static int routeEnergy(Level level, List<Pusher> pushers, int amount, boolean simulate) {
        int left = amount;
        for (Pusher p : pushers) {
            if (left <= 0) {
                break;
            }
            IEnergyStorage e = energyHandler(level, p.target(), p.side());
            if (e != null && e.canReceive()) {
                left -= e.receiveEnergy(left, simulate);
            }
        }
        return amount - left;
    }

    // ------------------------------------------------------------------------------------------ pull

    private static void pull(Level level, BlockPos target, Direction side, TarNetwork net, List<Pusher> pushers) {
        BlockState state = level.getBlockState(target);
        BlockEntity be = state.hasBlockEntity() ? level.getBlockEntity(target) : null;
        boolean pooled = net.find(target) != null; // storage already in the pool: moving it around would go nowhere

        IItemHandler items = itemHandler(level, target, state, be, side);
        if (items != null && !pooled) {
            BoundInventory probe = BoundInventory.probe(level, target, state, side, false, PoolAccess.COMMUNE);
            boolean machine = probe != null && probe.kind() == BoundInventory.Kind.MACHINE;
            int budget = TarboundConfig.SERVER.transientItemsPerOp.get();
            for (int slot = 0; slot < items.getSlots() && budget > 0; slot++) {
                if (machine && !BoundInventory.isFinishedOutput(items, slot)) {
                    continue; // only what a machine has finished, never its inputs or fuel
                }
                ItemStack offered = items.extractItem(slot, budget, true);
                if (offered.isEmpty()) {
                    continue;
                }
                int direct = offered.getCount() - routeItems(level, pushers, offered, true).getCount();
                int room = direct + net.fitCount(offered.copyWithCount(offered.getCount() - direct));
                if (room <= 0) {
                    continue;
                }
                ItemStack taken = items.extractItem(slot, Math.min(room, offered.getCount()), false);
                if (taken.isEmpty()) {
                    continue;
                }
                budget -= taken.getCount();
                ItemStack rest = routeItems(level, pushers, taken, false); // straight to push antennas first
                rest = rest.isEmpty() ? rest : net.insert(rest);
                if (!rest.isEmpty()) {
                    rest = items.insertItem(slot, rest, false);
                }
                if (!rest.isEmpty()) {
                    Block.popResource(level, target.relative(side), rest);
                }
            }
        }

        IFluidHandler fluids = fluidHandler(level, target, state, be, side);
        if (fluids == null || net.tanksAt(target)) {
            com.tarbound.TarboundDebug.log(level, target, "transient pull fluid", fluids == null ? "no fluid handler" : "target is a pool tank, skipped");
        }
        if (fluids != null && !net.tanksAt(target)) {
            int budget = TarboundConfig.SERVER.transientFluidPerOp.get();
            for (int t = 0; t < fluids.getTanks() && budget > 0; t++) {
                FluidStack inTank = fluids.getFluidInTank(t);
                if (inTank.isEmpty()) {
                    continue;
                }
                FluidStack offered = fluids.drain(inTank.copyWithAmount(budget), IFluidHandler.FluidAction.SIMULATE);
                int direct = offered.isEmpty() ? 0 : routeFluid(level, pushers, offered, true);
                int room = offered.isEmpty() ? 0 : direct + (offered.getAmount() > direct ? net.insertFluid(offered.copyWithAmount(offered.getAmount() - direct), true) : 0);
                com.tarbound.TarboundDebug.log(level, target, "transient pull fluid tank " + t, "holds " + com.tarbound.TarboundDebug.fluid(inTank)
                        + ", offers " + com.tarbound.TarboundDebug.fluid(offered) + ", direct to pushers " + direct + ", room (pushers + pool) " + room);
                if (room <= 0) {
                    continue;
                }
                FluidStack taken = fluids.drain(inTank.copyWithAmount(room), IFluidHandler.FluidAction.EXECUTE);
                if (taken.isEmpty()) {
                    continue;
                }
                budget -= taken.getAmount();
                int in = routeFluid(level, pushers, taken, false); // straight to push antennas first
                if (in < taken.getAmount()) {
                    in += net.insertFluid(taken.copyWithAmount(taken.getAmount() - in), false);
                }
                if (in < taken.getAmount()) {
                    fluids.fill(taken.copyWithAmount(taken.getAmount() - in), IFluidHandler.FluidAction.EXECUTE);
                }
            }
        }

        IEnergyStorage energy = energyHandler(level, target, side);
        if (energy != null && energy.canExtract()) {
            IEnergyStorage bank = net.energy();
            int offered = energy.extractEnergy(TarboundConfig.SERVER.transientFePerOp.get(), true);
            int direct = offered <= 0 ? 0 : routeEnergy(level, pushers, offered, true);
            int room = offered <= 0 ? 0 : direct + bank.receiveEnergy(offered - direct, true);
            if (room > 0) {
                int taken = energy.extractEnergy(room, false);
                int in = routeEnergy(level, pushers, taken, false);
                in += bank.receiveEnergy(taken - in, false);
                if (in < taken) {
                    energy.receiveEnergy(taken - in, false);
                }
            }
        }
    }

    // ------------------------------------------------------------------------------------------ push

    /**
     * Why a pushing antenna isn't moving its filter right now, for the status click, or null if nothing's in the way
     * (or it isn't pushing).
     */
    @Nullable
    public net.minecraft.network.chat.Component pushProblem(Level level, BlockState state) {
        if (state.getValue(TransientAntennaBlock.MODE) != TransientAntennaBlock.Mode.PUSH || filter.isEmpty()) {
            return null;
        }
        ScryingFontBlockEntity radio = radio();
        if (radio == null) {
            return null; // the status line already says it's untuned
        }
        TarNetwork pool = radio.network();
        BlockPos target = TransientAntennaBlock.target(worldPosition, state);
        Direction side = state.getValue(TransientAntennaBlock.FACING);
        BlockState ts = level.getBlockState(target);
        BlockEntity be = ts.hasBlockEntity() ? level.getBlockEntity(target) : null;
        FluidStack fluid = FluidUtil.getFluidContained(filter).orElse(FluidStack.EMPTY);
        if (!fluid.isEmpty()) {
            IFluidHandler fluids = fluidHandler(level, target, ts, be, side);
            if (fluids == null) {
                return net.minecraft.network.chat.Component.translatable("message.tarbound.transient.why.no_tank", ts.getBlock().getName());
            }
            if (pool.tanksAt(target)) {
                return net.minecraft.network.chat.Component.translatable("message.tarbound.transient.why.pooled_tank");
            }
            if (pool.fluidAmount(fluid) <= 0) {
                return net.minecraft.network.chat.Component.translatable("message.tarbound.transient.why.no_fluid", fluid.getHoverName());
            }
            if (fluids.fill(fluid.copyWithAmount(TarboundConfig.SERVER.transientFluidPerOp.get()), IFluidHandler.FluidAction.SIMULATE) <= 0) {
                return net.minecraft.network.chat.Component.translatable("message.tarbound.transient.why.refuses", ts.getBlock().getName(),
                        fluid.getHoverName());
            }
            return null;
        }
        IItemHandler items = itemHandler(level, target, ts, be, side);
        if (items == null) {
            return net.minecraft.network.chat.Component.translatable("message.tarbound.transient.why.no_slots", ts.getBlock().getName());
        }
        if (pool.find(target) != null) {
            return net.minecraft.network.chat.Component.translatable("message.tarbound.transient.why.pooled_storage");
        }
        if (pool.countContents().getOrDefault(com.tarbound.network.ItemKey.of(filter), 0L) <= 0) {
            return net.minecraft.network.chat.Component.translatable("message.tarbound.transient.why.no_items", filter.getHoverName());
        }
        if (ItemHandlerHelper.insertItem(items, filter.copyWithCount(1), true).getCount() > 0) {
            return net.minecraft.network.chat.Component.translatable("message.tarbound.transient.why.refuses", ts.getBlock().getName(),
                    filter.getHoverName());
        }
        return null;
    }

    private void push(Level level, BlockPos target, Direction side, TarNetwork net) {
        BlockState state = level.getBlockState(target);
        BlockEntity be = state.hasBlockEntity() ? level.getBlockEntity(target) : null;
        if (!filter.isEmpty()) {
            FluidStack fluid = FluidUtil.getFluidContained(filter).orElse(FluidStack.EMPTY);
            if (!fluid.isEmpty()) {
                pushFluid(level, target, state, be, side, net, fluid);
            } else {
                pushItems(level, target, state, be, side, net);
            }
        }
        IEnergyStorage energy = energyHandler(level, target, side);
        if (energy != null && energy.canReceive()) {
            IEnergyStorage bank = net.energy();
            int wanted = energy.receiveEnergy(TarboundConfig.SERVER.transientFePerOp.get(), true);
            int got = wanted <= 0 ? 0 : bank.extractEnergy(wanted, false);
            if (got > 0) {
                int in = energy.receiveEnergy(got, false);
                if (in < got) {
                    bank.receiveEnergy(got - in, false);
                }
            }
        }
    }

    private void pushItems(Level level, BlockPos target, BlockState state, @Nullable BlockEntity be, Direction side, TarNetwork net) {
        IItemHandler items = itemHandler(level, target, state, be, side);
        if (items == null || net.find(target) != null) {
            com.tarbound.TarboundDebug.log(level, target, "transient push items", items == null
                    ? "target " + com.tarbound.TarboundDebug.block(level, target) + " has no item handler" : "target is pool storage, skipped");
            return;
        }
        int want = Math.min(TarboundConfig.SERVER.transientItemsPerOp.get(), filter.getMaxStackSize());
        ItemStack rest = ItemHandlerHelper.insertItem(items, filter.copyWithCount(want), true);
        int fits = want - rest.getCount();
        if (fits <= 0) {
            return;
        }
        ItemStack got = net.extract(filter, fits);
        if (got.isEmpty()) {
            return;
        }
        ItemStack left = ItemHandlerHelper.insertItem(items, got, false);
        if (!left.isEmpty()) {
            left = net.insert(left);
            if (!left.isEmpty()) {
                Block.popResource(level, target.relative(side), left);
            }
        }
    }

    private static void pushFluid(Level level, BlockPos target, BlockState state, @Nullable BlockEntity be, Direction side, TarNetwork net,
            FluidStack fluid) {
        IFluidHandler fluids = fluidHandler(level, target, state, be, side);
        if (fluids == null || net.tanksAt(target)) {
            com.tarbound.TarboundDebug.log(level, target, "transient push fluid", fluids == null
                    ? "target " + com.tarbound.TarboundDebug.block(level, target) + " has no fluid handler on side " + side + " (or null side)"
                    : "target is a pool tank, skipped");
            return;
        }
        int want = fluids.fill(fluid.copyWithAmount(TarboundConfig.SERVER.transientFluidPerOp.get()), IFluidHandler.FluidAction.SIMULATE);
        int got = want <= 0 ? 0 : net.drainFluid(fluid, want, false);
        if (com.tarbound.TarboundDebug.on()) {
            com.tarbound.TarboundDebug.log(level, target, "transient push fluid", "filter " + com.tarbound.TarboundDebug.fluid(fluid)
                    + ", pool has " + net.fluidAmount(fluid) + "mB, target would take " + want + ", drained " + got + "mB; target "
                    + com.tarbound.TarboundDebug.tanks(fluids, FluidStack.EMPTY));
        }
        if (got > 0) {
            int in = fluids.fill(fluid.copyWithAmount(got), IFluidHandler.FluidAction.EXECUTE);
            if (in < got) {
                net.insertFluid(fluid.copyWithAmount(got - in), false);
            }
        }
    }

    // ------------------------------------------------------------------------------------------ capabilities

    @Nullable
    private static IItemHandler itemHandler(Level level, BlockPos pos, BlockState state, @Nullable BlockEntity be, Direction side) {
        IItemHandler h = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, state, be, side);
        return h != null ? h : level.getCapability(Capabilities.ItemHandler.BLOCK, pos, state, be, null);
    }

    @Nullable
    private static IFluidHandler fluidHandler(Level level, BlockPos pos, BlockState state, @Nullable BlockEntity be, Direction side) {
        IFluidHandler h = level.getCapability(Capabilities.FluidHandler.BLOCK, pos, state, be, side);
        return h != null ? h : level.getCapability(Capabilities.FluidHandler.BLOCK, pos, state, be, null);
    }

    @Nullable
    private static IEnergyStorage energyHandler(Level level, BlockPos pos, Direction side) {
        if (level.getBlockEntity(pos) instanceof ScryingFontBlockEntity) {
            return null; // the radio's own battery: nothing to move
        }
        IEnergyStorage h = level.getCapability(Capabilities.EnergyStorage.BLOCK, pos, side);
        return h != null ? h : level.getCapability(Capabilities.EnergyStorage.BLOCK, pos, null);
    }

    // ------------------------------------------------------------------------------------------ item <-> block

    @Override
    protected void applyImplicitComponents(DataComponentInput input) {
        super.applyImplicitComponents(input);
        GlobalPos fromItem = input.get(ModDataComponents.RADIO_LINK.get());
        if (fromItem != null) {
            link = fromItem;
            linkId = input.get(ModDataComponents.RADIO_ID.get());
        }
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        if (link != null) {
            components.set(ModDataComponents.RADIO_LINK.get(), link);
            if (linkId != null) {
                components.set(ModDataComponents.RADIO_ID.get(), linkId);
            }
        }
    }

    @Override
    public void removeComponentsFromTag(CompoundTag tag) {
        tag.remove("link");
        tag.remove("link_id");
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (link != null) {
            CompoundTag l = new CompoundTag();
            l.putString("dimension", link.dimension().location().toString());
            l.putLong("pos", link.pos().asLong());
            tag.put("link", l);
            if (linkId != null) {
                tag.putUUID("link_id", linkId);
            }
        }
        if (!filter.isEmpty()) {
            tag.put("filter", filter.save(registries));
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        link = null;
        linkId = tag.hasUUID("link_id") ? tag.getUUID("link_id") : null;
        if (tag.contains("link", Tag.TAG_COMPOUND)) {
            CompoundTag l = tag.getCompound("link");
            ResourceLocation dim = ResourceLocation.tryParse(l.getString("dimension"));
            if (dim != null) {
                link = GlobalPos.of(ResourceKey.create(Registries.DIMENSION, dim), BlockPos.of(l.getLong("pos")));
            }
        }
        filter = tag.contains("filter", Tag.TAG_COMPOUND)
                ? ItemStack.parse(registries, tag.getCompound("filter")).orElse(ItemStack.EMPTY)
                : ItemStack.EMPTY;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
