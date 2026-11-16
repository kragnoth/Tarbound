package com.tarbound.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;

/** A plain container screen in the tar palette: panel plus a slot well under every menu slot. */
public abstract class TarContainerScreen<M extends AbstractContainerMenu> extends AbstractContainerScreen<M> {
    protected static final int C_OUTLINE = 0xFF000000;
    protected static final int C_PANEL = 0xFF2B2622;
    protected static final int C_PANEL_HI = 0xFF4D443C;
    protected static final int C_PANEL_LO = 0xFF15110F;
    protected static final int C_SLOT = 0xFF171311;
    protected static final int C_SLOT_LO = 0xFF0A0807;
    protected static final int C_SLOT_HI = 0xFF4A4038;
    protected static final int C_TEXT = 0xFFCDBB9A;
    protected static final int C_TEXT_DIM = 0xFF8C7D66;
    protected static final int C_SPIRIT = 0xFF7FE6F0;

    protected TarContainerScreen(M menu, Inventory inventory, Component title, int width, int height) {
        super(menu, inventory, title);
        this.imageWidth = width;
        this.imageHeight = height;
        this.inventoryLabelY = height - 94;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        g.fill(x + 1, y, x + imageWidth - 1, y + imageHeight, C_OUTLINE);
        g.fill(x, y + 1, x + imageWidth, y + imageHeight - 1, C_OUTLINE);
        g.fill(x + 1, y + 1, x + imageWidth - 1, y + imageHeight - 1, C_PANEL);
        g.fill(x + 1, y + 1, x + imageWidth - 2, y + 3, C_PANEL_HI);
        g.fill(x + 1, y + 1, x + 3, y + imageHeight - 2, C_PANEL_HI);
        g.fill(x + 3, y + imageHeight - 3, x + imageWidth - 1, y + imageHeight - 1, C_PANEL_LO);
        g.fill(x + imageWidth - 3, y + 3, x + imageWidth - 1, y + imageHeight - 1, C_PANEL_LO);
        for (Slot slot : menu.slots) {
            if (drawWell(slot)) {
                inset(g, x + slot.x - 1, y + slot.y - 1, 18, 18);
            }
        }
        renderExtras(g, mouseX, mouseY);
    }

    /** Whether to draw this slot's well (a screen with paged slots hides the other pages'). */
    protected boolean drawWell(Slot slot) {
        return true;
    }

    /** Hook for subclasses: drawn after the panel and slot wells, before items. */
    protected void renderExtras(GuiGraphics g, int mouseX, int mouseY) {}

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, titleLabelX, titleLabelY, C_TEXT, false);
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, C_TEXT_DIM, false);
    }

    protected static void inset(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, C_SLOT);
        g.fill(x, y, x + w - 1, y + 1, C_SLOT_LO);
        g.fill(x, y, x + 1, y + h - 1, C_SLOT_LO);
        g.fill(x + 1, y + h - 1, x + w, y + h, C_SLOT_HI);
        g.fill(x + w - 1, y + 1, x + w, y + h, C_SLOT_HI);
    }
}
