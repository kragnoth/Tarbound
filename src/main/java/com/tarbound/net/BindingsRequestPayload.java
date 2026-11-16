package com.tarbound.net;

import com.tarbound.Tarbound;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client → server: the player is crouching and looking at this radio; send back what it has bound. */
public record BindingsRequestPayload(BlockPos font) implements CustomPacketPayload {
    public static final Type<BindingsRequestPayload> TYPE = new Type<>(Tarbound.id("bindings_request"));

    public static final StreamCodec<RegistryFriendlyByteBuf, BindingsRequestPayload> STREAM_CODEC =
            StreamCodec.composite(BlockPos.STREAM_CODEC, BindingsRequestPayload::font, BindingsRequestPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
