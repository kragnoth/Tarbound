package com.tarbound.menu;

import com.tarbound.block.ReliquaryBlock;
import com.tarbound.block.ReliquaryBlockEntity;
import com.tarbound.registry.ModBlocks;
import com.tarbound.registry.ModMenus;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

/**
 * The Ossuary: every Reliquary tuned to its radio on one screen, with their contract slots, so contracts
 * can be handed out and taken back without walking to each one. Three Reliquaries a page; the set is
 * taken when the screen opens.
 */
public class OssuaryMenu extends AbstractContainerMenu {
    public static final int PER_PAGE = 3;
    public static final int COLS = 9;
    public static final int SLOTS_PER = ReliquaryBlockEntity.CONTRACT_SLOTS; // 18: two rows of nine
    public static final int IMAGE_WIDTH = 176;
    public static final int LIST_Y = 18;
    public static final int ENTRY_H = 48;
    /** Below a Reliquary's name line. */
    public static final int SLOTS_DY = 11;
    public static final int INV_Y = LIST_Y + PER_PAGE * ENTRY_H + 14;
    public static final int IMAGE_HEIGHT = INV_Y + 82;

    /**
     * One Reliquary as the screen shows it.
     *
     * @param machine  the block it works (its own position for an all-sides one with nothing around)
     * @param name     the spirit's name, "" if unnamed
     * @param machines how many machines it works (more than one: all-sides)
     */
    public record Entry(BlockPos reliquary, BlockPos machine, BlockState machineState, String name, int unlocked, int machines) {
        void write(RegistryFriendlyByteBuf buf) {
            buf.writeBlockPos(reliquary);
            buf.writeBlockPos(machine);
            buf.writeVarInt(Block.getId(machineState));
            buf.writeUtf(name, ReliquaryBlockEntity.MAX_NAME_LENGTH * 4);
            buf.writeVarInt(unlocked);
            buf.writeVarInt(machines);
        }

        static Entry read(RegistryFriendlyByteBuf buf) {
            return new Entry(buf.readBlockPos(), buf.readBlockPos(), Block.stateById(buf.readVarInt()),
                    buf.readUtf(ReliquaryBlockEntity.MAX_NAME_LENGTH * 4), buf.readVarInt(), buf.readVarInt());
        }
    }

    private final ContainerLevelAccess access;
    private final List<Entry> entries;
    private final int contractSlotCount;
    private int page;

    private OssuaryMenu(int containerId, Inventory inventory, ContainerLevelAccess access, List<Entry> entries, List<IItemHandler> handlers,
            List<ReliquaryBlockEntity> live) {
        super(ModMenus.OSSUARY.get(), containerId);
        this.access = access;
        this.entries = List.copyOf(entries);
        for (int e = 0; e < entries.size(); e++) {
            int entryIndex = e;
            Entry entry = entries.get(e);
            ReliquaryBlockEntity be = live == null ? null : live.get(e);
            int onPage = e % PER_PAGE;
            for (int i = 0; i < SLOTS_PER; i++) {
                int index = i;
                int x = 8 + (i % COLS) * 18;
                int y = LIST_Y + onPage * ENTRY_H + SLOTS_DY + (i / COLS) * 18;
                addSlot(new SlotItemHandler(handlers.get(e), i, x, y) {
                    @Override
                    public boolean isActive() {
                        return entryIndex / PER_PAGE == page && index < entry.unlocked();
                    }

                    // Server: the Reliquary must still be there (not broken or unloaded while the screen is open).
                    private boolean present() {
                        return be == null || (!be.isRemoved() && be.getLevel() != null && be.getLevel().getBlockEntity(be.getBlockPos()) == be);
                    }

                    @Override
                    public boolean mayPlace(ItemStack stack) {
                        return isActive() && present() && super.mayPlace(stack);
                    }

                    @Override
                    public boolean mayPickup(Player player) {
                        return isActive() && present() && super.mayPickup(player);
                    }
                });
            }
        }
        contractSlotCount = entries.size() * SLOTS_PER;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, INV_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, 8 + col * 18, INV_Y + 58));
        }
    }

    /** Server: the menu for an Ossuary at {@code ossuary} working the radio at {@code radio}. */
    public static OssuaryMenu server(int containerId, Inventory inventory, Level level, BlockPos ossuary, List<ReliquaryBlockEntity> reliquaries) {
        List<Entry> entries = new ArrayList<>();
        List<IItemHandler> handlers = new ArrayList<>();
        for (ReliquaryBlockEntity r : reliquaries) {
            entries.add(entryFor(level, r));
            handlers.add(r.contractInventory());
        }
        return new OssuaryMenu(containerId, inventory, ContainerLevelAccess.create(level, ossuary), entries, handlers, reliquaries);
    }

    public static Entry entryFor(Level level, ReliquaryBlockEntity r) {
        BlockPos pos = r.getBlockPos();
        List<BlockPos> targets = ReliquaryBlock.targets(level, pos, r.getBlockState());
        BlockPos machine = targets.isEmpty() ? pos : targets.get(0);
        return new Entry(pos, machine, level.getBlockState(machine), r.customNameString(), r.unlocked(), targets.size());
    }

    public static OssuaryMenu fromNetwork(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        int n = buf.readVarInt();
        List<Entry> entries = new ArrayList<>(n);
        List<IItemHandler> mirrors = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            entries.add(Entry.read(buf));
            mirrors.add(new ItemStackHandler(SLOTS_PER) {
                @Override
                public boolean isItemValid(int slot, ItemStack stack) {
                    return ReliquaryBlockEntity.isContract(stack);
                }

                @Override
                public int getSlotLimit(int slot) {
                    return 1;
                }
            });
        }
        return new OssuaryMenu(containerId, inventory, ContainerLevelAccess.NULL, entries, mirrors, null);
    }

    /** What the client needs to build the same slots: written by the server when the screen opens. */
    public static void writeEntries(RegistryFriendlyByteBuf buf, Level level, List<ReliquaryBlockEntity> reliquaries) {
        buf.writeVarInt(reliquaries.size());
        for (ReliquaryBlockEntity r : reliquaries) {
            entryFor(level, r).write(buf);
        }
    }

    public List<Entry> entries() {
        return entries;
    }

    public int page() {
        return page;
    }

    public int pages() {
        return Math.max(1, (entries.size() + PER_PAGE - 1) / PER_PAGE);
    }

    /** Page buttons: 0 back, 1 forward (sent with the vanilla menu-button packet, so both sides agree). */
    @Override
    public boolean clickMenuButton(Player player, int id) {
        int next = id == 0 ? page - 1 : id == 1 ? page + 1 : page;
        if (next < 0 || next >= pages()) {
            return false;
        }
        page = next;
        return true;
    }

    /** Client: follow the page (after asking the server to do the same). */
    public void setPageClient(int page) {
        this.page = Math.max(0, Math.min(page, pages() - 1));
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (slot == null || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index < contractSlotCount) {
            if (!moveItemStackTo(stack, contractSlotCount, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else {
            // Into the first open slot of the Reliquaries on this page.
            if (!ReliquaryBlockEntity.isContract(stack)) {
                return ItemStack.EMPTY;
            }
            boolean moved = false;
            for (int i = 0; i < contractSlotCount && !stack.isEmpty(); i++) {
                Slot target = slots.get(i);
                if (target.isActive() && !target.hasItem() && target.mayPlace(stack)) {
                    target.setByPlayer(stack.split(1));
                    moved = true;
                }
            }
            if (!moved) {
                return ItemStack.EMPTY;
            }
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
        return stillValid(access, player, ModBlocks.OSSUARY.get());
    }
}
