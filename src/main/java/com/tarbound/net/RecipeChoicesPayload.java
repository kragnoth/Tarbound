package com.tarbound.net;

import com.tarbound.Tarbound;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * Server → client: every crafting recipe the radio's grid matches right now (when there's more than one),
 * with what each makes, and which one the result slot is showing. Like Polymorph, the player picks.
 */
public record RecipeChoicesPayload(int containerId, List<ResourceLocation> ids, List<ItemStack> outputs, int selected)
        implements CustomPacketPayload {
    public static final int MAX = 32;
    public static final Type<RecipeChoicesPayload> TYPE = new Type<>(Tarbound.id("recipe_choices"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RecipeChoicesPayload> STREAM_CODEC =
            StreamCodec.ofMember(RecipeChoicesPayload::write, RecipeChoicesPayload::read);

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(containerId);
        int n = Math.min(MAX, Math.min(ids.size(), outputs.size()));
        buf.writeVarInt(n);
        for (int i = 0; i < n; i++) {
            buf.writeResourceLocation(ids.get(i));
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, outputs.get(i));
        }
        buf.writeVarInt(selected);
    }

    private static RecipeChoicesPayload read(RegistryFriendlyByteBuf buf) {
        int id = buf.readVarInt();
        int n = buf.readVarInt();
        if (n < 0 || n > MAX) {
            throw new IllegalArgumentException("Bad recipe choice count " + n);
        }
        List<ResourceLocation> ids = new ArrayList<>(n);
        List<ItemStack> outputs = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            ids.add(buf.readResourceLocation());
            outputs.add(ItemStack.OPTIONAL_STREAM_CODEC.decode(buf));
        }
        return new RecipeChoicesPayload(id, ids, outputs, buf.readVarInt());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
