package com.tarbound.net;

import com.tarbound.Tarbound;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;

/**
 * Client → server (from JEI/EMI): fill the 3x3 grid for a recipe.
 *
 * @param slots exactly 9 entries, row-major; each lists acceptable items for that grid slot (empty = leave blank)
 * @param max   fill as many crafts' worth as the ingredients allow (up to a stack)
 */
public record FillGridPayload(int containerId, List<List<ItemStack>> slots, boolean max) implements CustomPacketPayload {
    public static final int GRID = 9;
    /** Cap on alternatives per slot, so a huge tag can't bloat the packet. */
    public static final int MAX_ALTERNATIVES = 256;

    public static final Type<FillGridPayload> TYPE = new Type<>(Tarbound.id("fill_grid"));

    public static final StreamCodec<RegistryFriendlyByteBuf, FillGridPayload> STREAM_CODEC =
            StreamCodec.ofMember(FillGridPayload::write, FillGridPayload::read);

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(containerId);
        buf.writeBoolean(max);
        for (int i = 0; i < GRID; i++) {
            List<ItemStack> alts = (i < slots.size() ? slots.get(i) : List.<ItemStack>of()).stream()
                    .filter(s -> !s.isEmpty())
                    .limit(MAX_ALTERNATIVES)
                    .toList();
            int n = alts.size();
            buf.writeVarInt(n);
            for (int a = 0; a < n; a++) {
                ItemStack.STREAM_CODEC.encode(buf, alts.get(a).copyWithCount(1));
            }
        }
    }

    private static FillGridPayload read(RegistryFriendlyByteBuf buf) {
        int containerId = buf.readVarInt();
        boolean max = buf.readBoolean();
        List<List<ItemStack>> slots = new ArrayList<>(GRID);
        for (int i = 0; i < GRID; i++) {
            int n = buf.readVarInt();
            if (n < 0 || n > MAX_ALTERNATIVES) {
                throw new IllegalArgumentException("Too many alternatives in fill_grid: " + n);
            }
            List<ItemStack> alts = new ArrayList<>(n);
            for (int a = 0; a < n; a++) {
                alts.add(ItemStack.STREAM_CODEC.decode(buf));
            }
            slots.add(alts);
        }
        return new FillGridPayload(containerId, slots, max);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
