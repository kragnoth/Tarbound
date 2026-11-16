package com.tarbound.menu;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.item.ItemStack;

/** Crafting result slot that pulls replacement ingredients from the tar network after each craft. */
public class FontResultSlot extends ResultSlot {
    private final ScryingFontMenu menu;
    private final CraftingContainer grid;

    public FontResultSlot(ScryingFontMenu menu, Player player, CraftingContainer grid, Container result, int slot, int x, int y) {
        super(player, grid, result, slot, x, y);
        this.menu = menu;
        this.grid = grid;
    }

    @Override
    public void onTake(Player player, ItemStack stack) {
        ItemStack[] before = new ItemStack[grid.getContainerSize()];
        for (int i = 0; i < before.length; i++) {
            before[i] = grid.getItem(i).copy();
        }
        super.onTake(player, stack);
        menu.refillGrid(before);
    }
}
