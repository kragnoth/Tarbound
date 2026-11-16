package com.tarbound.net;

import com.tarbound.Tarbound;
import com.tarbound.network.PoolAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client → server: set an antenna column's mode and pool priority (from its settings screen). */
public record AntennaSettingsPayload(BlockPos pos, PoolAccess mode, int priority) implements CustomPacketPayload {
    public static final Type<AntennaSettingsPayload> TYPE = new Type<>(Tarbound.id("antenna_settings"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AntennaSettingsPayload> STREAM_CODEC =
            StreamCodec.ofMember(AntennaSettingsPayload::write, AntennaSettingsPayload::read);

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeEnum(mode);
        buf.writeVarInt(priority);
    }

    private static AntennaSettingsPayload read(RegistryFriendlyByteBuf buf) {
        return new AntennaSettingsPayload(buf.readBlockPos(), buf.readEnum(PoolAccess.class), buf.readVarInt());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
