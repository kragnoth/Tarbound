package com.tarbound.net;

import com.tarbound.Tarbound;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client → server: an interaction with the bound-block tray.
 *
 * @param pos  the bound block, as listed in the last {@link TrayPayload}
 * @param view index into that block's views (-1 with {@link Action#SELECT} clears the selection)
 * @param slot slot within the view, for pickup actions
 */
public record TrayActionPayload(int containerId, Action action, BlockPos pos, int view, int slot) implements CustomPacketPayload {

    public enum Action {
        /** Left click: pick up a stack, or place the carried stack. */
        LEFT,
        /** Right click: pick up half, or place one. */
        RIGHT,
        /** Shift click: send the slot's stack to the pool (then the player's inventory). */
        QUICK_MOVE,
        /** The player is looking at this block/view; shift-clicked inventory items go here first. */
        SELECT;

        private static final Action[] VALUES = values();

        static Action byId(int id) {
            return id >= 0 && id < VALUES.length ? VALUES[id] : SELECT;
        }
    }

    public static final Type<TrayActionPayload> TYPE = new Type<>(Tarbound.id("tray_action"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TrayActionPayload> STREAM_CODEC =
            StreamCodec.ofMember(TrayActionPayload::write, TrayActionPayload::read);

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(containerId);
        buf.writeVarInt(action.ordinal());
        buf.writeBlockPos(pos);
        buf.writeVarInt(view + 1);
        buf.writeVarInt(slot);
    }

    private static TrayActionPayload read(RegistryFriendlyByteBuf buf) {
        return new TrayActionPayload(buf.readVarInt(), Action.byId(buf.readVarInt()), buf.readBlockPos(), buf.readVarInt() - 1, buf.readVarInt());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
