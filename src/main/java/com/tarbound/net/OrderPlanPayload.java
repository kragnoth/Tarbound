package com.tarbound.net;

import com.tarbound.Tarbound;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client → server: order {@code ops} operations of the contract in a reliquary slot, crafting whatever
 * ingredients are short through other contracts (the server plans it again itself).
 */
public record OrderPlanPayload(int containerId, BlockPos machine, int contractSlot, int ops) implements CustomPacketPayload {
    public static final Type<OrderPlanPayload> TYPE = new Type<>(Tarbound.id("order_plan"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OrderPlanPayload> STREAM_CODEC = StreamCodec.ofMember(
            (p, buf) -> {
                buf.writeVarInt(p.containerId);
                buf.writeBlockPos(p.machine);
                buf.writeVarInt(p.contractSlot);
                buf.writeVarInt(p.ops);
            },
            buf -> new OrderPlanPayload(buf.readVarInt(), buf.readBlockPos(), buf.readVarInt(), buf.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
