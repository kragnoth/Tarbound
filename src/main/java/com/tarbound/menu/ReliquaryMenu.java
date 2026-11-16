package com.tarbound.menu;

import com.tarbound.block.ReliquaryBlockEntity;
import com.tarbound.registry.ModBlocks;
import com.tarbound.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

/** The reliquary's eighteen contract slots (6x3, opened with candles) and the player inventory. */
public class ReliquaryMenu extends AbstractContainerMenu {
    public static final int IMAGE_WIDTH = 176;
    public static final int IMAGE_HEIGHT = 166;
    private static final int SLOTS = ReliquaryBlockEntity.CONTRACT_SLOTS;

    private final ContainerLevelAccess access;
    private final BlockPos pos;
    private final String initialName;
    private final int unlocked;

    public ReliquaryMenu(int containerId, Inventory inventory, IItemHandler contracts, ContainerLevelAccess access, BlockPos pos, String name,
            int unlocked) {
        super(ModMenus.RELIQUARY.get(), containerId);
        this.access = access;
        this.pos = pos.immutable();
        this.initialName = name;
        this.unlocked = unlocked;
        // Three rows of six: the slots open left to right, top to bottom, a candle each.
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 6; col++) {
                int index = col + row * 6;
                addSlot(new SlotItemHandler(contracts, index, 35 + col * 18, 17 + row * 18) {
                    @Override
                    public boolean isActive() {
                        return index < ReliquaryMenu.this.unlocked; // still waiting for its candle
                    }
                });
            }
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, 8 + col * 18, 142));
        }
    }

    public static ReliquaryMenu fromNetwork(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        ItemStackHandler mirror = new ItemStackHandler(SLOTS) {
            @Override
            public boolean isItemValid(int slot, ItemStack stack) {
                return ReliquaryBlockEntity.isContract(stack);
            }

            @Override
            public int getSlotLimit(int slot) {
                return 1;
            }
        };
        BlockPos pos = buf.readBlockPos();
        int unlocked = buf.readVarInt();
        return new ReliquaryMenu(containerId, inventory, mirror, ContainerLevelAccess.NULL, pos, buf.readUtf(ReliquaryBlockEntity.MAX_NAME_LENGTH * 4),
                unlocked);
    }

    /** Contract slots open (the rest wait for candles). */
    public int unlocked() {
        return unlocked;
    }

    public BlockPos reliquaryPos() {
        return pos;
    }

    /** The spirit's name when the screen opened ("" if unnamed). */
    public String initialName() {
        return initialName;
    }

    /** Server: name (or, if blank, un-name) the spirit. */
    public void rename(String name) {
        access.execute((level, p) -> {
            if (level.getBlockEntity(p) instanceof ReliquaryBlockEntity reliquary) {
                reliquary.setCustomName(name);
            }
        });
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (slot == null || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index < SLOTS) {
            if (!moveItemStackTo(stack, SLOTS, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!ReliquaryBlockEntity.isContract(stack) || !moveItemStackTo(stack, 0, Math.min(SLOTS, unlocked), false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return stack.getCount() == original.getCount() ? ItemStack.EMPTY : original;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, ModBlocks.RELIQUARY.get());
    }
}
