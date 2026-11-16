package com.tarbound.net;

import com.tarbound.Tarbound;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client → server: use this one of the recipes the radio's grid matches. */
public record ChooseRecipePayload(int containerId, ResourceLocation recipe) implements CustomPacketPayload {
    public static final Type<ChooseRecipePayload> TYPE = new Type<>(Tarbound.id("choose_recipe"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ChooseRecipePayload> STREAM_CODEC = StreamCodec.ofMember(
            (p, buf) -> {
                buf.writeVarInt(p.containerId);
                buf.writeResourceLocation(p.recipe);
            },
            buf -> new ChooseRecipePayload(buf.readVarInt(), buf.readResourceLocation()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
