package com.tarbound.net;

import com.tarbound.Tarbound;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * Client → server: the player clicked a fluid in the pool, or clicked the pool holding a filled bucket.
 *
 * @param fluid the fluid clicked; empty for {@link Action#POUR_CARRIED}
 */
public record FluidActionPayload(int containerId, Action action, FluidStack fluid) implements CustomPacketPayload {

    public enum Action {
        /** Fill a bucket (the one held, else an empty one from the pool) and put it on the cursor. */
        FILL,
        /** As FILL, but the filled bucket goes into the player's inventory. */
        FILL_TO_INVENTORY,
        /** Pour the held bucket (or other container) into the pool. */
        POUR_CARRIED;

        private static final Action[] VALUES = values();

        static Action byId(int id) {
            return id >= 0 && id < VALUES.length ? VALUES[id] : FILL;
        }
    }

    public static final Type<FluidActionPayload> TYPE = new Type<>(Tarbound.id("fluid_action"));

    public static final StreamCodec<RegistryFriendlyByteBuf, FluidActionPayload> STREAM_CODEC =
            StreamCodec.ofMember(FluidActionPayload::write, FluidActionPayload::read);

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(containerId);
        buf.writeVarInt(action.ordinal());
        FluidStack.OPTIONAL_STREAM_CODEC.encode(buf, fluid);
    }

    private static FluidActionPayload read(RegistryFriendlyByteBuf buf) {
        int id = buf.readVarInt();
        Action action = Action.byId(buf.readVarInt());
        FluidStack fluid = FluidStack.OPTIONAL_STREAM_CODEC.decode(buf);
        return new FluidActionPayload(id, action, fluid);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
