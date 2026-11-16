package com.tarbound.net;

import com.tarbound.Tarbound;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client → server: generalize the lectern's written contract to this item tag (a standing contract). */
public record GeneralizeContractPayload(int containerId, ResourceLocation tag) implements CustomPacketPayload {
    public static final Type<GeneralizeContractPayload> TYPE = new Type<>(Tarbound.id("generalize_contract"));

    public static final StreamCodec<RegistryFriendlyByteBuf, GeneralizeContractPayload> STREAM_CODEC =
            StreamCodec.ofMember(GeneralizeContractPayload::write, GeneralizeContractPayload::read);

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(containerId);
        buf.writeResourceLocation(tag);
    }

    private static GeneralizeContractPayload read(RegistryFriendlyByteBuf buf) {
        return new GeneralizeContractPayload(buf.readVarInt(), buf.readResourceLocation());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
