package com.tarbound.spirit;

import com.tarbound.block.ScryingFontBlockEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * The radio's item face for hoppers and pipes: one slot that only takes offerings, and only as many as
 * fit in the Spirit store. Nothing can be pulled out, and it doesn't open the pool to pipes.
 */
public final class OfferingSlot implements IItemHandler {
    private final ScryingFontBlockEntity font;

    public OfferingSlot(ScryingFontBlockEntity font) {
        this.font = font;
    }

    @Override
    public int getSlots() {
        return 1;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        return ItemStack.EMPTY;
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        if (stack.isEmpty() || font.isRemoved()) {
            return stack;
        }
        int taken = font.feed(stack, simulate);
        return taken <= 0 ? stack : stack.copyWithCount(stack.getCount() - taken);
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        return ItemStack.EMPTY;
    }

    @Override
    public int getSlotLimit(int slot) {
        return 64;
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return Spirit.value(stack) > 0;
    }
}
