package com.tarbound.block;

import com.tarbound.network.FluidKey;
import com.tarbound.network.ItemKey;
import com.tarbound.network.RadioRegistry;
import com.tarbound.network.ShadeRegistry;
import com.tarbound.network.TarNetwork;
import com.tarbound.registry.ModBlockEntities;
import com.tarbound.registry.ModDataComponents;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
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
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * A Shade: a spirit left to watch over one order. While the order runs it holds everything the order
 * claimed (taken out of the pool, so nothing else can use it) and what the order's sub-crafts deliver.
 * The held items circle its head.
 */
public class ShadeBlockEntity extends BlockEntity implements com.tarbound.contract.OrderVessel {
    @Nullable
    private GlobalPos link;
    @Nullable
    private java.util.UUID linkId;
    /** The order it's holding for (0 = free). */
    private int orderId;
    /** How strong it is, 1 to {@link #MAX_RANK}: each Votive Skull fed to it adds one. Sets how big an order it can watch. */
    private int rank = 1;
    public static final int MAX_RANK = 5;
    private final Map<ItemKey, Long> held = new LinkedHashMap<>();
    private final Map<FluidKey, Long> heldFluids = new LinkedHashMap<>();
    /** Client: a few of the held items, most plentiful first, to circle the head. */
    private List<ItemStack> shown = List.of();

    public ShadeBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SHADE.get(), pos, state);
    }

    // ------------------------------------------------------------------------------------------ tuning

    @Nullable
    public GlobalPos link() {
        return link;
    }

    /** The loaded radio this Shade is tuned to, or null. */
    @Nullable
    public ScryingFontBlockEntity radio() {
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
        return linkId == null || linkId.equals(font.radioId()) ? font : null;
    }

    public boolean tunedTo(ScryingFontBlockEntity font) {
        return level != null && link != null
                && RadioRegistry.matches(link, linkId, GlobalPos.of(level.dimension(), font.getBlockPos()), font.radioId());
    }

    // ------------------------------------------------------------------------------------------ the order

    public int orderId() {
        return orderId;
    }

    public boolean free() {
        return orderId == 0;
    }

    public int rank() {
        return rank;
    }

    /** Most steps (sub-crafts plus the thing ordered) an order this Shade watches may have. */
    public int capacity() {
        return capacityOf(rank);
    }

    /** 4, 8, 16, 32, 64 steps for ranks 1 to 5. */
    public static int capacityOf(int rank) {
        return 4 << (Math.max(1, Math.min(MAX_RANK, rank)) - 1);
    }

    /** The lowest rank that can watch an order of {@code steps} steps (MAX_RANK + 1 if none can). */
    public static int rankFor(int steps) {
        for (int r = 1; r <= MAX_RANK; r++) {
            if (capacityOf(r) >= steps) {
                return r;
            }
        }
        return MAX_RANK + 1;
    }

    /** Feeds it a Votive Skull. @return false if it's already as strong as it gets */
    public boolean rankUp() {
        if (rank >= MAX_RANK) {
            return false;
        }
        rank++;
        changed();
        return true;
    }

    @Override
    public BlockPos vesselPos() {
        return worldPosition;
    }

    public void claim(int order) {
        orderId = order;
        changed();
    }

    public Map<ItemKey, Long> held() {
        return held;
    }

    public Map<FluidKey, Long> heldFluids() {
        return heldFluids;
    }

    public void hold(ItemStack stack) {
        if (!stack.isEmpty()) {
            held.merge(ItemKey.of(stack), (long) stack.getCount(), Long::sum);
            changed();
        }
    }

    public void hold(ItemKey key, long n) {
        if (n > 0) {
            held.merge(key, n, Long::sum);
            changed();
        }
    }

    public void holdFluid(FluidStack fluid) {
        if (!fluid.isEmpty()) {
            heldFluids.merge(FluidKey.of(fluid), (long) fluid.getAmount(), Long::sum);
            changed();
        }
    }

    /** Takes up to {@code amount} of exactly {@code proto}. @return how many */
    public int take(ItemStack proto, int amount) {
        ItemKey key = ItemKey.of(proto);
        long have = held.getOrDefault(key, 0L);
        int got = (int) Math.min(have, amount);
        if (got > 0) {
            if (have - got <= 0) {
                held.remove(key);
            } else {
                held.put(key, have - got);
            }
            changed();
        }
        return got;
    }

    public int takeFluid(FluidStack fluid, int amount) {
        FluidKey key = FluidKey.of(fluid);
        long have = heldFluids.getOrDefault(key, 0L);
        int got = (int) Math.min(have, amount);
        if (got > 0) {
            if (have - got <= 0) {
                heldFluids.remove(key);
            } else {
                heldFluids.put(key, have - got);
            }
            changed();
        }
        return got;
    }

    /**
     * Ends the order: everything held goes back into {@code net}'s pool (or, with no pool, drops here),
     * and the Shade is free again.
     */
    public void release(@Nullable TarNetwork net) {
        for (Map.Entry<ItemKey, Long> e : held.entrySet()) {
            long n = e.getValue();
            ItemStack proto = e.getKey().proto();
            while (n > 0) {
                int chunk = (int) Math.min(n, proto.getMaxStackSize());
                ItemStack rest = net != null ? net.insert(proto.copyWithCount(chunk)) : proto.copyWithCount(chunk);
                if (!rest.isEmpty() && level != null) {
                    Block.popResource(level, worldPosition, rest);
                }
                n -= chunk;
            }
        }
        if (net != null) {
            for (Map.Entry<FluidKey, Long> e : heldFluids.entrySet()) {
                net.insertFluid(e.getKey().proto().copyWithAmount((int) Math.min(Integer.MAX_VALUE, e.getValue())), false);
            }
        }
        held.clear();
        heldFluids.clear();
        orderId = 0;
        changed();
    }

    private void changed() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    /** Client: the held items shown circling (up to nine kinds, most plentiful first). */
    public List<ItemStack> shown() {
        return shown;
    }

    /**
     * Every few seconds, a busy Shade checks its radio still knows the order (a radio broken and put back
     * forgets its orders); if not, it gives everything back.
     */
    public static void serverTick(Level level, BlockPos pos, BlockState state, ShadeBlockEntity shade) {
        if (shade.free() || (level.getGameTime() + pos.asLong()) % 100 != 0) {
            return;
        }
        ScryingFontBlockEntity font = shade.radio();
        if (font != null && !font.hasOrder(shade.orderId)) {
            shade.release(font.network());
        }
    }

    // ------------------------------------------------------------------------------------------ registry

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide) {
            ShadeRegistry.add(level, worldPosition);
        }
    }

    @Override
    public void setRemoved() {
        if (level != null && !level.isClientSide) {
            ShadeRegistry.remove(level, worldPosition);
        }
        super.setRemoved();
    }

    @Override
    public void onChunkUnloaded() {
        if (level != null && !level.isClientSide) {
            ShadeRegistry.remove(level, worldPosition);
        }
    }

    // ------------------------------------------------------------------------------------------ item <-> block

    @Override
    protected void applyImplicitComponents(DataComponentInput input) {
        super.applyImplicitComponents(input);
        Integer r = input.get(ModDataComponents.SHADE_RANK.get());
        if (r != null) {
            rank = Math.max(1, Math.min(MAX_RANK, r));
        }
        GlobalPos fromItem = input.get(ModDataComponents.RADIO_LINK.get());
        if (fromItem != null) {
            link = fromItem;
            linkId = input.get(ModDataComponents.RADIO_ID.get());
        }
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        if (rank > 1) {
            components.set(ModDataComponents.SHADE_RANK.get(), rank);
        }
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
        tag.remove("rank");
    }

    // ------------------------------------------------------------------------------------------ save / sync

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
        tag.putInt("order", orderId);
        tag.putInt("rank", rank);
        ListTag items = new ListTag();
        for (Map.Entry<ItemKey, Long> e : held.entrySet()) {
            CompoundTag t = new CompoundTag();
            t.put("item", e.getKey().proto().save(registries));
            t.putLong("n", e.getValue());
            items.add(t);
        }
        tag.put("held", items);
        ListTag fluids = new ListTag();
        for (Map.Entry<FluidKey, Long> e : heldFluids.entrySet()) {
            CompoundTag t = new CompoundTag();
            t.put("fluid", e.getKey().proto().save(registries));
            t.putLong("n", e.getValue());
            fluids.add(t);
        }
        tag.put("held_fluids", fluids);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("shown")) {
            // A client update: just the order flag, rank and the items to show.
            orderId = tag.getInt("order");
            rank = Math.max(1, tag.getInt("rank"));
            List<ItemStack> list = new ArrayList<>();
            ListTag l = tag.getList("shown", Tag.TAG_COMPOUND);
            for (int i = 0; i < l.size(); i++) {
                ItemStack.parse(registries, l.getCompound(i)).ifPresent(list::add);
            }
            shown = List.copyOf(list);
            return;
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
        orderId = tag.getInt("order");
        rank = tag.contains("rank") ? Math.max(1, Math.min(MAX_RANK, tag.getInt("rank"))) : 1;
        held.clear();
        ListTag items = tag.getList("held", Tag.TAG_COMPOUND);
        for (int i = 0; i < items.size(); i++) {
            CompoundTag t = items.getCompound(i);
            ItemStack s = ItemStack.parseOptional(registries, t.getCompound("item"));
            if (!s.isEmpty()) {
                held.merge(ItemKey.of(s), t.getLong("n"), Long::sum);
            }
        }
        heldFluids.clear();
        ListTag fluids = tag.getList("held_fluids", Tag.TAG_COMPOUND);
        for (int i = 0; i < fluids.size(); i++) {
            CompoundTag t = fluids.getCompound(i);
            FluidStack f = FluidStack.parseOptional(registries, t.getCompound("fluid"));
            if (!f.isEmpty()) {
                heldFluids.merge(FluidKey.of(f), t.getLong("n"), Long::sum);
            }
        }
    }

    /** Clients get the order flag and up to nine held item kinds (most plentiful first), nothing more. */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("order", orderId);
        tag.putInt("rank", rank);
        ListTag list = new ListTag();
        held.entrySet().stream()
                .sorted(Map.Entry.<ItemKey, Long>comparingByValue().reversed())
                .limit(9)
                .forEach(e -> list.add(e.getKey().proto().save(registries)));
        tag.put("shown", list);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /**
     * The weakest free Shade loaded in {@code level}, tuned to {@code font}, that can watch an order of {@code steps}
     * steps (so the strong ones stay free for big orders), or null.
     */
    @Nullable
    public static ShadeBlockEntity freeFor(Level level, ScryingFontBlockEntity font, int steps) {
        ShadeBlockEntity best = null;
        for (BlockPos p : ShadeRegistry.all(level)) {
            if (level.isLoaded(p) && level.getBlockEntity(p) instanceof ShadeBlockEntity shade && shade.free() && shade.tunedTo(font)
                    && shade.capacity() >= steps && (best == null || shade.rank < best.rank)) {
                best = shade;
            }
        }
        return best;
    }

    /** The most steps any free Shade tuned to {@code font} can watch; 0 if none is free. */
    public static int bestFree(Level level, ScryingFontBlockEntity font) {
        int best = 0;
        for (BlockPos p : ShadeRegistry.all(level)) {
            if (level.isLoaded(p) && level.getBlockEntity(p) instanceof ShadeBlockEntity shade && shade.free() && shade.tunedTo(font)) {
                best = Math.max(best, shade.capacity());
            }
        }
        return best;
    }
}
