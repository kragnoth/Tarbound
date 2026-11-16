package com.tarbound.net;

import com.tarbound.Tarbound;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;

/**
 * Client → server: set row {@code row} of the open Steward's list to keep {@code keep} of {@code item} (a row past the
 * end adds one; an empty item removes the row). The item is only a filter, like a contract's ghosts.
 */
public record StewardEditPayload(int containerId, int row, ItemStack item, int keep) implements CustomPacketPayload {
    public static final Type<StewardEditPayload> TYPE = new Type<>(Tarbound.id("steward_edit"));

    public static final StreamCodec<RegistryFriendlyByteBuf, StewardEditPayload> STREAM_CODEC =
            StreamCodec.ofMember(StewardEditPayload::write, StewardEditPayload::read);

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(containerId);
        buf.writeVarInt(row);
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, item);
        buf.writeVarInt(keep);
    }

    private static StewardEditPayload read(RegistryFriendlyByteBuf buf) {
        return new StewardEditPayload(buf.readVarInt(), buf.readVarInt(), ItemStack.OPTIONAL_STREAM_CODEC.decode(buf), buf.readVarInt());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
