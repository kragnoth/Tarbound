package com.tarbound.net;

import com.tarbound.Tarbound;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;

/**
 * Client → server: the player clicked the network view (or a font button).
 *
 * @param proto the item type clicked; empty for actions that don't target an entry
 */
public record FontActionPayload(int containerId, Action action, ItemStack proto) implements CustomPacketPayload {

    public enum Action {
        /** Take up to a full stack onto the cursor. */
        TAKE_STACK,
        /** Take half a stack onto the cursor. */
        TAKE_HALF,
        /** Take a full stack straight into the player inventory. */
        TAKE_TO_INVENTORY,
        /** Drop the whole carried stack into the network. */
        INSERT_CARRIED,
        /** Drop one of the carried stack into the network. */
        INSERT_CARRIED_ONE,
        /** Return the crafting grid contents to the network. */
        CLEAR_GRID,
        /** The candle button: burn offerings from the pool, or not. */
        TOGGLE_POOL_OFFERINGS;

        private static final Action[] VALUES = values();

        static Action byId(int id) {
            return id >= 0 && id < VALUES.length ? VALUES[id] : TAKE_STACK;
        }
    }

    public static final Type<FontActionPayload> TYPE = new Type<>(Tarbound.id("font_action"));

    public static final StreamCodec<RegistryFriendlyByteBuf, FontActionPayload> STREAM_CODEC =
            StreamCodec.ofMember(FontActionPayload::write, FontActionPayload::read);

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(containerId);
        buf.writeVarInt(action.ordinal());
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, proto);
    }

    private static FontActionPayload read(RegistryFriendlyByteBuf buf) {
        int id = buf.readVarInt();
        Action action = Action.byId(buf.readVarInt());
        ItemStack proto = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
        return new FontActionPayload(id, action, proto);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
