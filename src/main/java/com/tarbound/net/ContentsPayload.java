package com.tarbound.net;

import com.tarbound.Tarbound;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * Server → client: the aggregated pool a Scrying Font is reading, sent as one or more pages so no
 * single packet can grow past the network limit, however big the storage behind the radio is. The
 * client collects pages 0..pages-1 and applies them together when the last one arrives.
 * <p>
 * The first send after the screen opens is the <b>full</b> pool. After that the server only sends a
 * <b>delta</b>: the item types (and fluids) whose count changed, with count 0 meaning "gone". A delta that would be
 * as big as the pool itself is sent as a full one instead.
 *
 * @param page  this page's index
 * @param pages how many pages make up this send (at least 1, even for an empty pool or delta)
 * @param delta the entries are changes to apply, not the whole pool
 */
public record ContentsPayload(int containerId, int page, int pages, boolean delta, List<Entry> entries, int inventories, boolean canCraft)
        implements CustomPacketPayload {
    /**
     * One item type and its total count across the network ({@code proto} always has count 1), or one
     * fluid and its total in mB ({@code fluid} set, amount 1; {@code proto} empty).
     */
    public record Entry(ItemStack proto, FluidStack fluid, long count) {
        public Entry(ItemStack proto, long count) {
            this(proto, FluidStack.EMPTY, count);
        }

        public static Entry ofFluid(FluidStack fluid, long amount) {
            return new Entry(ItemStack.EMPTY, fluid, amount);
        }

        public boolean isFluid() {
            return !fluid.isEmpty();
        }

        public Entry withCount(long newCount) {
            return new Entry(proto, fluid, newCount);
        }
    }

    /** Encoded bytes per page; vanilla's limit for one server → client custom payload is 1 MiB. */
    public static final int PAGE_BYTES = 512 * 1024;
    public static final int MAX_PAGES = 4096;

    public static final Type<ContentsPayload> TYPE = new Type<>(Tarbound.id("contents"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ContentsPayload> STREAM_CODEC =
            StreamCodec.ofMember(ContentsPayload::write, ContentsPayload::read);

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(containerId);
        buf.writeVarInt(page);
        buf.writeVarInt(pages);
        buf.writeBoolean(delta);
        buf.writeVarInt(inventories);
        buf.writeBoolean(canCraft);
        buf.writeVarInt(entries.size());
        for (Entry e : entries) {
            buf.writeBoolean(e.isFluid());
            if (e.isFluid()) {
                FluidStack.STREAM_CODEC.encode(buf, e.fluid());
            } else {
                ItemStack.STREAM_CODEC.encode(buf, e.proto());
            }
            buf.writeVarLong(e.count());
        }
    }

    private static ContentsPayload read(RegistryFriendlyByteBuf buf) {
        int containerId = buf.readVarInt();
        int page = buf.readVarInt();
        int pages = buf.readVarInt();
        if (pages < 1 || pages > MAX_PAGES || page < 0 || page >= pages) {
            throw new IllegalArgumentException("Bad contents page " + page + "/" + pages);
        }
        boolean delta = buf.readBoolean();
        int inventories = buf.readVarInt();
        boolean canCraft = buf.readBoolean();
        int size = buf.readVarInt();
        if (size < 0 || size > 1_000_000) {
            throw new IllegalArgumentException("Bad contents page size " + size);
        }
        List<Entry> entries = new ArrayList<>(Math.min(size, 4096));
        for (int i = 0; i < size; i++) {
            if (buf.readBoolean()) {
                FluidStack fluid = FluidStack.STREAM_CODEC.decode(buf);
                entries.add(Entry.ofFluid(fluid, buf.readVarLong()));
            } else {
                ItemStack proto = ItemStack.STREAM_CODEC.decode(buf);
                entries.add(new Entry(proto, buf.readVarLong()));
            }
        }
        return new ContentsPayload(containerId, page, pages, delta, entries, inventories, canCraft);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
