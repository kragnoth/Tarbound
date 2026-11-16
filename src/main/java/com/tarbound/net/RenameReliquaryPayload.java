package com.tarbound.net;

import com.tarbound.Tarbound;
import com.tarbound.block.ReliquaryBlockEntity;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client → server: name the spirit in the Reliquary whose screen is open ("" clears the name). */
public record RenameReliquaryPayload(int containerId, String name) implements CustomPacketPayload {
    public static final Type<RenameReliquaryPayload> TYPE = new Type<>(Tarbound.id("rename_reliquary"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RenameReliquaryPayload> STREAM_CODEC =
            StreamCodec.ofMember(RenameReliquaryPayload::write, RenameReliquaryPayload::read);

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(containerId);
        buf.writeUtf(name, ReliquaryBlockEntity.MAX_NAME_LENGTH * 4);
    }

    private static RenameReliquaryPayload read(RegistryFriendlyByteBuf buf) {
        return new RenameReliquaryPayload(buf.readVarInt(), buf.readUtf(ReliquaryBlockEntity.MAX_NAME_LENGTH * 4));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
