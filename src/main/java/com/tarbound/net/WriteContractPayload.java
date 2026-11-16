package com.tarbound.net;

import com.tarbound.Tarbound;
import com.tarbound.contract.ContractData;
import javax.annotation.Nullable;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client → server (from JEI/EMI at the Scribing Lectern): write this recipe into a blank contract.
 *
 * @param craftingRecipe for a crafting-table recipe, its id; the server then builds the contract from
 *                       its own copy of the recipe and ignores {@code contract}
 */
public record WriteContractPayload(int containerId, ContractData contract, @Nullable ResourceLocation craftingRecipe)
        implements CustomPacketPayload {
    public static final Type<WriteContractPayload> TYPE = new Type<>(Tarbound.id("write_contract"));

    public static final StreamCodec<RegistryFriendlyByteBuf, WriteContractPayload> STREAM_CODEC =
            StreamCodec.ofMember(WriteContractPayload::write, WriteContractPayload::read);

    public WriteContractPayload(int containerId, ContractData contract) {
        this(containerId, contract, null);
    }

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(containerId);
        ContractData.STREAM_CODEC.encode(buf, contract);
        buf.writeBoolean(craftingRecipe != null);
        if (craftingRecipe != null) {
            buf.writeResourceLocation(craftingRecipe);
        }
    }

    private static WriteContractPayload read(RegistryFriendlyByteBuf buf) {
        int id = buf.readVarInt();
        ContractData contract = ContractData.STREAM_CODEC.decode(buf);
        ResourceLocation recipe = buf.readBoolean() ? buf.readResourceLocation() : null;
        return new WriteContractPayload(id, contract, recipe);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
