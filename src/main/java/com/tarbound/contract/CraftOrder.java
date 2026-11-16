package com.tarbound.contract;

import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * A multi-step order: a few jobs (the sub-crafts, then the thing asked for) sharing one Shade, which holds
 * everything the order set aside and everything its sub-crafts make until the last job is done.
 *
 * @param output what was asked for (for the message when it's done), or empty for a fluid
 * @param fluid  the fluid asked for, or empty for an item
 * @param amount how many (items, or mB)
 */
public record CraftOrder(int id, BlockPos shadePos, @Nullable UUID requester, ItemStack output, FluidStack fluid, long amount,
        @Nullable UUID vesselEntity) {
    public CraftOrder(int id, BlockPos shadePos, @Nullable UUID requester, ItemStack output, FluidStack fluid, long amount) {
        this(id, shadePos, requester, output, fluid, amount, null);
    }

    /** What was asked for, for messages: the item's name, or the fluid's. */
    public net.minecraft.network.chat.Component name() {
        return fluid.isEmpty() ? output.getHoverName() : fluid.getHoverName();
    }

    public CompoundTag save(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("id", id);
        tag.putLong("shade", shadePos.asLong());
        if (requester != null) {
            tag.putUUID("requester", requester);
        }
        if (!output.isEmpty()) {
            tag.put("output", output.save(registries));
        }
        if (!fluid.isEmpty()) {
            tag.put("fluid", fluid.save(registries));
        }
        tag.putLong("amount", amount);
        if (vesselEntity != null) {
            tag.putUUID("vessel_entity", vesselEntity); // 0.67: a Steward holds this order, not a Shade at shadePos
        }
        return tag;
    }

    public static CraftOrder load(CompoundTag tag, HolderLookup.Provider registries) {
        return new CraftOrder(tag.getInt("id"), BlockPos.of(tag.getLong("shade")),
                tag.hasUUID("requester") ? tag.getUUID("requester") : null,
                ItemStack.parseOptional(registries, tag.getCompound("output")), FluidStack.parseOptional(registries, tag.getCompound("fluid")),
                tag.getLong("amount"), tag.hasUUID("vessel_entity") ? tag.getUUID("vessel_entity") : null);
    }
}
