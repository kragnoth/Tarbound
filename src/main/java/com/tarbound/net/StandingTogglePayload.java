package com.tarbound.net;

import com.tarbound.Tarbound;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client → server: pause or resume the standing contract in a slot of the reliquary above a bound machine. */
public record StandingTogglePayload(int containerId, BlockPos machine, int slot) implements CustomPacketPayload {
    public static final Type<StandingTogglePayload> TYPE = new Type<>(Tarbound.id("standing_toggle"));

    public static final StreamCodec<RegistryFriendlyByteBuf, StandingTogglePayload> STREAM_CODEC =
            StreamCodec.ofMember(StandingTogglePayload::write, StandingTogglePayload::read);

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(containerId);
        buf.writeBlockPos(machine);
        buf.writeVarInt(slot);
    }

    private static StandingTogglePayload read(RegistryFriendlyByteBuf buf) {
        return new StandingTogglePayload(buf.readVarInt(), buf.readBlockPos(), buf.readVarInt());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
