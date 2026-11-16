package com.tarbound.client;

import com.tarbound.block.ReliquaryBlockEntity;
import com.tarbound.menu.OssuaryMenu;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Every Reliquary on the radio, three to a page: the spirit's name (or its machine's) over its contract
 * slots. Click a name to light the machine up in the world, like the radio's machine list does.
 */
public class OssuaryScreen extends TarContainerScreen<OssuaryMenu> {
    private static final int ARROW_W = 12;

    public OssuaryScreen(OssuaryMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, OssuaryMenu.IMAGE_WIDTH, OssuaryMenu.IMAGE_HEIGHT);
    }

    private int prevX() {
        return leftPos + imageWidth - 8 - ARROW_W * 2 - 2;
    }

    private int nextX() {
        return leftPos + imageWidth - 8 - ARROW_W;
    }

    private int arrowY() {
        return topPos + 4;
    }

    private void turn(int dir) {
        int id = dir < 0 ? 0 : 1;
        if (menu.clickMenuButton(minecraft.player, id) && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
        }
    }

    /** Index into the menu's entries of the name line under the mouse, or -1. */
    private int nameAt(double mx, double my) {
        int start = menu.page() * OssuaryMenu.PER_PAGE;
        for (int i = 0; i < OssuaryMenu.PER_PAGE; i++) {
            int e = start + i;
            if (e >= menu.entries().size()) {
                break;
            }
            int y = topPos + OssuaryMenu.LIST_Y + i * OssuaryMenu.ENTRY_H;
            if (mx >= leftPos + 7 && mx < leftPos + imageWidth - 7 && my >= y - 1 && my < y + 10) {
                return e;
            }
        }
        return -1;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (in(mx, my, prevX(), arrowY(), ARROW_W, 10)) {
            turn(-1);
            return true;
        }
        if (in(mx, my, nextX(), arrowY(), ARROW_W, 10)) {
            turn(1);
            return true;
        }
        int e = nameAt(mx, my);
        if (e >= 0) {
            OssuaryMenu.Entry entry = menu.entries().get(e);
            if (entry.machine().equals(MachineHighlight.target())) {
                MachineHighlight.clear();
            } else {
                MachineHighlight.show(entry.machine());
            }
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        if (dy != 0 && my < topPos + OssuaryMenu.INV_Y - 12) {
            turn(dy > 0 ? -1 : 1);
            return true;
        }
        return super.mouseScrolled(mx, my, dx, dy);
    }

    private static boolean in(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private boolean onThisPage(int slotIndex) {
        int entry = slotIndex / OssuaryMenu.SLOTS_PER;
        return entry < menu.entries().size() && entry / OssuaryMenu.PER_PAGE == menu.page();
    }

    @Override
    protected boolean drawWell(Slot slot) {
        int contractSlots = menu.entries().size() * OssuaryMenu.SLOTS_PER;
        return slot.index >= contractSlots || onThisPage(slot.index);
    }

    @Override
    protected void renderExtras(GuiGraphics g, int mouseX, int mouseY) {
        // page arrows
        boolean canBack = menu.page() > 0;
        boolean canOn = menu.page() < menu.pages() - 1;
        arrow(g, prevX(), "<", canBack, in(mouseX, mouseY, prevX(), arrowY(), ARROW_W, 10));
        arrow(g, nextX(), ">", canOn, in(mouseX, mouseY, nextX(), arrowY(), ARROW_W, 10));
        String pages = (menu.page() + 1) + "/" + menu.pages();
        g.drawString(font, pages, prevX() - 4 - font.width(pages), arrowY() + 1, C_TEXT_DIM, false);

        int start = menu.page() * OssuaryMenu.PER_PAGE;
        int hovered = nameAt(mouseX, mouseY);
        for (int i = 0; i < OssuaryMenu.PER_PAGE; i++) {
            int e = start + i;
            if (e >= menu.entries().size()) {
                break;
            }
            OssuaryMenu.Entry entry = menu.entries().get(e);
            int y = topPos + OssuaryMenu.LIST_Y + i * OssuaryMenu.ENTRY_H;
            if (e == hovered) {
                g.fill(leftPos + 7, y - 1, leftPos + imageWidth - 7, y + 10, 0x30FFFFFF);
            }
            // the machine's icon, half size, then the name
            ItemStack icon = new ItemStack(entry.machineState().getBlock().asItem());
            if (!icon.isEmpty()) {
                g.pose().pushPose();
                g.pose().translate(leftPos + 8, y, 0);
                g.pose().scale(0.5F, 0.5F, 1.0F);
                g.renderItem(icon, 0, 0);
                g.pose().popPose();
            }
            boolean lit = entry.machine().equals(MachineHighlight.target());
            String label = font.plainSubstrByWidth(name(entry).getString(), imageWidth - 30);
            g.drawString(font, label, leftPos + 18, y + 1, lit ? C_SPIRIT : C_TEXT, false);
            // slots still waiting for a candle
            for (int s = entry.unlocked(); s < OssuaryMenu.SLOTS_PER; s++) {
                Slot slot = menu.slots.get(e * OssuaryMenu.SLOTS_PER + s);
                int sx = leftPos + slot.x;
                int sy = topPos + slot.y;
                g.renderItem(new ItemStack(Items.CANDLE), sx, sy);
                g.pose().pushPose();
                g.pose().translate(0, 0, 200);
                g.fill(sx, sy, sx + 16, sy + 16, 0xB0100C0A);
                g.pose().popPose();
            }
        }
    }

    private static Component name(OssuaryMenu.Entry entry) {
        if (!entry.name().isEmpty()) {
            return Component.literal(entry.name());
        }
        if (entry.machines() > 1) {
            return Component.translatable("gui.tarbound.ossuary.all_sides", entry.machines());
        }
        return entry.machineState().isAir() ? Component.translatable("gui.tarbound.ossuary.nothing") : entry.machineState().getBlock().getName();
    }

    private void arrow(GuiGraphics g, int x, String label, boolean enabled, boolean hover) {
        g.fill(x, arrowY(), x + ARROW_W, arrowY() + 10, enabled && hover ? C_PANEL_HI : C_SLOT_HI);
        g.drawString(font, label, x + (ARROW_W - font.width(label)) / 2, arrowY() + 1, enabled ? C_TEXT : C_TEXT_DIM, false);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        int e = nameAt(mouseX, mouseY);
        if (e >= 0) {
            OssuaryMenu.Entry entry = menu.entries().get(e);
            boolean lit = entry.machine().equals(MachineHighlight.target());
            var p = entry.machine();
            g.renderComponentTooltip(font, List.of(
                    name(entry).copy().withStyle(ChatFormatting.WHITE),
                    Component.translatable("gui.tarbound.ossuary.at", p.getX(), p.getY(), p.getZ()).withStyle(ChatFormatting.GRAY),
                    Component.translatable(lit ? "gui.tarbound.ossuary.hide" : "gui.tarbound.ossuary.show").withStyle(ChatFormatting.DARK_AQUA)),
                    mouseX, mouseY);
            return;
        }
        // hovering a candle-locked slot
        int start = menu.page() * OssuaryMenu.PER_PAGE;
        for (int i = 0; i < OssuaryMenu.PER_PAGE && start + i < menu.entries().size(); i++) {
            OssuaryMenu.Entry entry = menu.entries().get(start + i);
            for (int s = entry.unlocked(); s < ReliquaryBlockEntity.CONTRACT_SLOTS; s++) {
                Slot slot = menu.slots.get((start + i) * OssuaryMenu.SLOTS_PER + s);
                if (in(mouseX, mouseY, leftPos + slot.x, topPos + slot.y, 16, 16)) {
                    g.renderComponentTooltip(font, List.of(
                            Component.translatable("gui.tarbound.reliquary.locked").withStyle(ChatFormatting.GOLD),
                            Component.translatable("gui.tarbound.reliquary.locked_hint").withStyle(ChatFormatting.GRAY)), mouseX, mouseY);
                    return;
                }
            }
        }
    }
}
