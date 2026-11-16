package com.tarbound.block;

import com.tarbound.registry.ModBlockEntities;
import com.tarbound.registry.ModItems;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;

/** Holds the lectern's blank and freshly written contract between visits. */
public class ScribingLecternBlockEntity extends BlockEntity {
    private final ItemStackHandler pages = newPages(this);

    public ScribingLecternBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SCRIBING_LECTERN.get(), pos, state);
    }

    /** Slot 0 takes blank contracts; slot 1 is filled by writing, or by hand with a contract to generalize. */
    public static ItemStackHandler newPages(@Nullable BlockEntity owner) {
        return new ItemStackHandler(2) {
            @Override
            public int getSlotLimit(int slot) {
                return slot == 1 ? 1 : 64;
            }

            @Override
            public boolean isItemValid(int slot, ItemStack stack) {
                return slot == 0 ? stack.is(ModItems.BLANK_CONTRACT.get()) : stack.is(ModItems.SPIRIT_CONTRACT.get());
            }

            @Override
            protected void onContentsChanged(int slot) {
                if (owner != null) {
                    owner.setChanged();
                }
            }
        };
    }

    public ItemStackHandler pages() {
        return pages;
    }

    public void dropContents(Level level, BlockPos pos) {
        for (int i = 0; i < pages.getSlots(); i++) {
            ItemStack s = pages.getStackInSlot(i);
            if (!s.isEmpty()) {
                Block.popResource(level, pos, s.copy());
                pages.setStackInSlot(i, ItemStack.EMPTY);
            }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("pages", pages.serializeNBT(registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("pages")) {
            pages.deserializeNBT(registries, tag.getCompound("pages"));
        }
    }
}
