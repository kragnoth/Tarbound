package com.tarbound.net;

import com.tarbound.Tarbound;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server → client: what a radio has bound, for outlining in the world (see the client's BindingHighlight).
 *
 * @param storage  storage in the pool
 * @param machines machines (and crafting stations) bound through Reliquaries
 * @param tanks    tanks in the pool
 * @param unbound  machines touching the tar without a Reliquary
 */
public record BindingsPayload(BlockPos font, List<BlockPos> storage, List<BlockPos> machines, List<BlockPos> tanks, List<BlockPos> unbound)
        implements CustomPacketPayload {
    /** Most positions of each kind sent (a huge base still outlines its nearest). */
    public static final int MAX = 512;

    public static final Type<BindingsPayload> TYPE = new Type<>(Tarbound.id("bindings"));

    private static final StreamCodec<io.netty.buffer.ByteBuf, List<BlockPos>> POSITIONS = BlockPos.STREAM_CODEC.apply(ByteBufCodecs.list(MAX));

    public static final StreamCodec<RegistryFriendlyByteBuf, BindingsPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, BindingsPayload::font,
            POSITIONS, BindingsPayload::storage,
            POSITIONS, BindingsPayload::machines,
            POSITIONS, BindingsPayload::tanks,
            POSITIONS, BindingsPayload::unbound,
            BindingsPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
