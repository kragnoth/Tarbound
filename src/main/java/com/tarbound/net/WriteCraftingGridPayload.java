package com.tarbound.net;

import com.tarbound.Tarbound;
import com.tarbound.contract.ContractDraft;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;

/**
 * Client → server (the Scribing Lectern's Write button in crafting mode): the 3x3 ghost grid. The server finds
 * the crafting recipe it makes itself, and writes the contract from that.
 *
 * @param substitutes false to write a contract that takes only the exact items in the grid
 */
public record WriteCraftingGridPayload(int containerId, List<ItemStack> grid, boolean substitutes) implements CustomPacketPayload {
    public static final Type<WriteCraftingGridPayload> TYPE = new Type<>(Tarbound.id("write_crafting_grid"));

    public static final StreamCodec<RegistryFriendlyByteBuf, WriteCraftingGridPayload> STREAM_CODEC =
            StreamCodec.ofMember(WriteCraftingGridPayload::write, WriteCraftingGridPayload::read);

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(containerId);
        for (int i = 0; i < ContractDraft.GRID; i++) {
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, i < grid.size() ? grid.get(i).copyWithCount(Math.min(1, grid.get(i).getCount())) : ItemStack.EMPTY);
        }
        buf.writeBoolean(substitutes);
    }

    private static WriteCraftingGridPayload read(RegistryFriendlyByteBuf buf) {
        int id = buf.readVarInt();
        List<ItemStack> grid = new ArrayList<>(ContractDraft.GRID);
        for (int i = 0; i < ContractDraft.GRID; i++) {
            grid.add(ItemStack.OPTIONAL_STREAM_CODEC.decode(buf));
        }
        return new WriteCraftingGridPayload(id, grid, buf.readBoolean());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
