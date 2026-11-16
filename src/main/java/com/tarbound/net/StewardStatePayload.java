package com.tarbound.net;

import com.tarbound.Tarbound;
import com.tarbound.entity.StockTarget;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;

/**
 * Server → client: a Steward's list as its screen shows it. {@code have}/{@code status} line up with {@code targets};
 * {@code radio} is 0 (radio reachable), 1 (not tuned) or 2 (radio not loaded); {@code craftables} (what the radio's
 * contracts make) only comes now and then, null in between.
 */
public record StewardStatePayload(int containerId, List<StockTarget> targets, long[] have, byte[] status, byte radio,
        @Nullable List<ItemStack> craftables) implements CustomPacketPayload {
    public static final Type<StewardStatePayload> TYPE = new Type<>(Tarbound.id("steward_state"));
    public static final int MAX_CRAFTABLES = 1024;

    public static final StreamCodec<RegistryFriendlyByteBuf, StewardStatePayload> STREAM_CODEC =
            StreamCodec.ofMember(StewardStatePayload::write, StewardStatePayload::read);

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(containerId);
        StockTarget.LIST_STREAM_CODEC.encode(buf, targets);
        for (int i = 0; i < targets.size(); i++) {
            buf.writeVarLong(i < have.length ? Math.max(0, have[i]) : 0);
            buf.writeByte(i < status.length ? status[i] : 0);
        }
        buf.writeByte(radio);
        buf.writeBoolean(craftables != null);
        if (craftables != null) {
            int n = Math.min(MAX_CRAFTABLES, craftables.size());
            buf.writeVarInt(n);
            for (int i = 0; i < n; i++) {
                ItemStack.STREAM_CODEC.encode(buf, craftables.get(i));
            }
        }
    }

    private static StewardStatePayload read(RegistryFriendlyByteBuf buf) {
        int id = buf.readVarInt();
        List<StockTarget> targets = StockTarget.LIST_STREAM_CODEC.decode(buf);
        long[] have = new long[targets.size()];
        byte[] status = new byte[targets.size()];
        for (int i = 0; i < targets.size(); i++) {
            have[i] = buf.readVarLong();
            status[i] = buf.readByte();
        }
        byte radio = buf.readByte();
        List<ItemStack> craftables = null;
        if (buf.readBoolean()) {
            int n = Math.min(MAX_CRAFTABLES, buf.readVarInt());
            craftables = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                craftables.add(ItemStack.STREAM_CODEC.decode(buf));
            }
        }
        return new StewardStatePayload(id, targets, have, status, radio, craftables);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
