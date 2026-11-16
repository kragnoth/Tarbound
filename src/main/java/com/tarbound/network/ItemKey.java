package com.tarbound.network;

import net.minecraft.world.item.ItemStack;

/**
 * Hashable identity of an item type (item + data components), ignoring count.
 */
public final class ItemKey {
    private final ItemStack proto;
    private final int hash;

    private ItemKey(ItemStack proto) {
        this.proto = proto;
        this.hash = ItemStack.hashItemAndComponents(proto);
    }

    public static ItemKey of(ItemStack stack) {
        return new ItemKey(stack.copyWithCount(1));
    }

    /** A count-1 copy; callers must not mutate it. */
    public ItemStack proto() {
        return proto;
    }

    public boolean matches(ItemStack stack) {
        return ItemStack.isSameItemSameComponents(proto, stack);
    }

    @Override
    public boolean equals(Object o) {
        return this == o || (o instanceof ItemKey other && hash == other.hash && ItemStack.isSameItemSameComponents(proto, other.proto));
    }

    @Override
    public int hashCode() {
        return hash;
    }
}
