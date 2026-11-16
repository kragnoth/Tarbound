package com.tarbound.menu;

import com.tarbound.block.ScryingFontBlockEntity;
import com.tarbound.contract.ContractData;
import com.tarbound.contract.CraftPlanner;
import com.tarbound.entity.Steward;
import com.tarbound.entity.StockTarget;
import com.tarbound.net.StewardStatePayload;
import com.tarbound.network.ItemKey;
import com.tarbound.registry.ModMenus;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * A Steward's list: up to {@link StockTarget#MAX} items, each with how many to keep, what storage has now and what the
 * Steward is doing about it; under it, everything the radio's contracts can make, to pick from. The rows aren't
 * slots (nothing is put in them): clicking one with an item on the cursor, clicking something it can make, dragging
 * from JEI/EMI or shift-clicking from the inventory sets a row.
 */
public class StewardMenu extends AbstractContainerMenu {
    public static final int IMAGE_WIDTH = 176;
    public static final int COLS = 9;
    public static final int TARGET_X = 8;
    public static final int TARGET_Y = 18;
    public static final int EDIT_Y = 56;
    public static final int LIST_LABEL_Y = 74;
    public static final int LIST_Y = 84;
    public static final int LIST_ROWS = 2;
    public static final int INV_Y = LIST_Y + LIST_ROWS * 18 + 14;
    public static final int IMAGE_HEIGHT = INV_Y + 82;

    /** Server only. */
    @Nullable
    private final Steward steward;
    private final int stewardId;
    private int ticks;
    private final Player owner;

    // Client view (the server's, as last sent).
    private List<StockTarget> targets = List.of();
    private long[] have = new long[0];
    private byte[] status = new byte[0];
    private byte radio;
    private List<ItemStack> craftables = List.of();
    private int version;

    private StewardMenu(int containerId, Inventory inventory, @Nullable Steward steward, int stewardId) {
        super(ModMenus.STEWARD.get(), containerId);
        this.steward = steward;
        this.stewardId = stewardId;
        this.owner = inventory.player;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, INV_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, 8 + col * 18, INV_Y + 58));
        }
    }

    public static StewardMenu server(int containerId, Inventory inventory, Steward steward) {
        return new StewardMenu(containerId, inventory, steward, steward.getId());
    }

    public static StewardMenu fromNetwork(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        return new StewardMenu(containerId, inventory, null, buf.readVarInt());
    }

    public int stewardId() {
        return stewardId;
    }

    @Override
    public boolean stillValid(Player player) {
        if (steward == null) {
            return true;
        }
        return steward.isAlive() && !steward.isRemoved() && player.distanceToSqr(steward) <= 8 * 8;
    }

    /** Shift-click from the inventory: that item goes on the list (a stack's worth to keep). */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = index >= 0 && index < slots.size() ? slots.get(index) : null;
        if (steward != null && slot != null && slot.hasItem()) {
            ItemStack stack = slot.getItem();
            steward.edit(Integer.MAX_VALUE, stack, defaultKeep(stack));
            sendState(true);
        }
        return ItemStack.EMPTY;
    }

    /** How many to keep when an item goes on the list without an amount: a stack. */
    public static int defaultKeep(ItemStack stack) {
        return Math.max(1, stack.getMaxStackSize());
    }

    // ------------------------------------------------------------------------------------------ server

    /** Server: an edit from the screen. */
    public void edit(ServerPlayer player, int row, ItemStack item, int keep) {
        if (steward == null || !stillValid(player)) {
            return;
        }
        steward.edit(row, item.copyWithCount(Math.max(1, item.getCount())), keep);
        sendState(false);
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (steward != null) {
            ticks++;
            if (ticks == 1 || ticks % 10 == 0) {
                sendState(ticks == 1 || ticks % 100 == 0);
            }
        }
    }

    private void sendState(boolean withCraftables) {
        if (steward == null || !(owner instanceof ServerPlayer player) || player.containerMenu != this
                || !ScryingFontMenu.canReceive(player)) {
            return;
        }
        List<StockTarget> list = steward.targets();
        long[] h = new long[list.size()];
        byte[] s = new byte[list.size()];
        for (int i = 0; i < list.size(); i++) {
            h[i] = steward.have(i);
            s[i] = (byte) steward.status(i).ordinal();
        }
        ScryingFontBlockEntity font = steward.radio();
        byte r = (byte) (font != null ? 0 : steward.link() == null ? 1 : 2);
        List<ItemStack> made = withCraftables && font != null ? craftables(font) : withCraftables ? List.of() : null;
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, new StewardStatePayload(containerId, list, h, s, r, made));
    }

    /** Server: one of each item the radio's contracts make. */
    private static List<ItemStack> craftables(ScryingFontBlockEntity font) {
        Map<ItemKey, ItemStack> out = new LinkedHashMap<>();
        for (CraftPlanner.Source src : font.planSources(font.network())) {
            ContractData c = src.contract();
            if (c.orderable() && !c.tracksFluid() && out.size() < StewardStatePayload.MAX_CRAFTABLES) {
                out.putIfAbsent(ItemKey.of(c.primaryOutput()), c.primaryOutput().copyWithCount(1));
            }
        }
        return new ArrayList<>(out.values());
    }

    // ------------------------------------------------------------------------------------------ client

    public void receive(StewardStatePayload p) {
        targets = p.targets();
        have = p.have();
        status = p.status();
        radio = p.radio();
        if (p.craftables() != null) {
            List<ItemStack> sorted = new ArrayList<>(p.craftables());
            sorted.sort(java.util.Comparator.comparing(st -> st.getHoverName().getString().toLowerCase(java.util.Locale.ROOT)));
            craftables = sorted;
        }
        version++;
    }

    public List<StockTarget> targets() {
        return targets;
    }

    public long have(int i) {
        return i < have.length ? have[i] : 0;
    }

    public Steward.Status status(int i) {
        return Steward.Status.of(i < status.length ? status[i] : -1);
    }

    public byte radio() {
        return radio;
    }

    public List<ItemStack> craftables() {
        return craftables;
    }

    public int version() {
        return version;
    }
}
