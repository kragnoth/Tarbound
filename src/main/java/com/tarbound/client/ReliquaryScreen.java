package com.tarbound.client;

import com.tarbound.block.ReliquaryBlockEntity;
import com.tarbound.menu.ReliquaryMenu;
import com.tarbound.net.RenameReliquaryPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/**
 * The reliquary's contract slots, a box at the top to name the spirit (so look-alike machines can be told
 * apart in the radio), and a reminder of what machine the spirit is bound to.
 */
public class ReliquaryScreen extends TarContainerScreen<ReliquaryMenu> {
    private EditBox nameBox;
    private String sentName;

    public ReliquaryScreen(ReliquaryMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, ReliquaryMenu.IMAGE_WIDTH, ReliquaryMenu.IMAGE_HEIGHT);
        this.sentName = menu.initialName();
    }

    @Override
    protected void init() {
        super.init();
        nameBox = new EditBox(font, leftPos + 8, topPos + 5, imageWidth - 16, 10, Component.translatable("gui.tarbound.reliquary.name"));
        nameBox.setBordered(false);
        nameBox.setMaxLength(ReliquaryBlockEntity.MAX_NAME_LENGTH);
        nameBox.setTextColor(0xFFCDBB9A);
        nameBox.setValue(sentName);
        nameBox.setHint(Component.translatable("gui.tarbound.reliquary.name_hint").withColor(0xFF8C7D66));
        addRenderableWidget(nameBox);
    }

    private void commitName() {
        String value = nameBox.getValue().strip();
        if (!value.equals(sentName)) {
            sentName = value;
            PacketDistributor.sendToServer(new RenameReliquaryPayload(menu.containerId, value));
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (nameBox.isFocused()) {
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                commitName();
                nameBox.setFocused(false);
                setFocused(null);
                return true;
            }
            if (keyCode != GLFW.GLFW_KEY_ESCAPE) {
                // Keep typed letters (E, numbers...) from closing the screen or swapping hotbar slots.
                return nameBox.keyPressed(keyCode, scanCode, modifiers) || nameBox.canConsumeInput();
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        boolean wasFocused = nameBox.isFocused();
        boolean handled = super.mouseClicked(mouseX, mouseY, button);
        if (wasFocused && !nameBox.isFocused()) {
            commitName();
        }
        return handled;
    }

    @Override
    public void removed() {
        commitName();
        super.removed();
    }

    /** Index of the locked contract slot under the mouse, or -1. */
    private int lockedSlotAt(double mx, double my) {
        for (int i = menu.unlocked(); i < ReliquaryBlockEntity.CONTRACT_SLOTS; i++) {
            var slot = menu.slots.get(i);
            int x = leftPos + slot.x;
            int y = topPos + slot.y;
            if (mx >= x && mx < x + 16 && my >= y && my < y + 16) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        if (lockedSlotAt(mouseX, mouseY) >= 0) {
            g.renderComponentTooltip(font, java.util.List.of(
                    Component.translatable("gui.tarbound.reliquary.locked").withStyle(net.minecraft.ChatFormatting.GOLD),
                    Component.translatable("gui.tarbound.reliquary.locked_hint").withStyle(net.minecraft.ChatFormatting.GRAY)), mouseX, mouseY);
        }
    }

    @Override
    protected void renderExtras(GuiGraphics g, int mouseX, int mouseY) {
        // Slots still waiting for a candle: an unlit candle, sunk in shadow.
        for (int i = menu.unlocked(); i < ReliquaryBlockEntity.CONTRACT_SLOTS; i++) {
            var slot = menu.slots.get(i);
            int x = leftPos + slot.x;
            int y = topPos + slot.y;
            g.renderItem(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.CANDLE), x, y);
            g.pose().pushPose();
            g.pose().translate(0, 0, 200);
            g.fill(x, y, x + 16, y + 16, 0xB0100C0A);
            g.pose().popPose();
        }
        // a faint underline for the name box
        g.fill(leftPos + 7, topPos + 15, leftPos + imageWidth - 7, topPos + 16, nameBox != null && nameBox.isFocused() ? C_SPIRIT : C_SLOT_HI);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        // The name box takes the title's place.
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, C_TEXT_DIM, false);
        if (minecraft != null && minecraft.level != null) {
            BlockState self = minecraft.level.getBlockState(menu.reliquaryPos());
            com.tarbound.block.ReliquaryBlock.Attach attach = self.getBlock() instanceof com.tarbound.block.ReliquaryBlock
                    ? self.getValue(com.tarbound.block.ReliquaryBlock.ATTACH) : com.tarbound.block.ReliquaryBlock.Attach.DOWN;
            BlockState below = attach.dir == null ? self : minecraft.level.getBlockState(menu.reliquaryPos().relative(attach.dir));
            boolean tuned = minecraft.level.getBlockEntity(menu.reliquaryPos()) instanceof ReliquaryBlockEntity reliquary && reliquary.link() != null;
            Component binding = !tuned
                    ? Component.translatable("gui.tarbound.reliquary.untuned")
                    : attach.dir == null
                    ? Component.translatable("gui.tarbound.reliquary.all_sides")
                    : below.isAir()
                    ? Component.translatable("gui.tarbound.reliquary.nothing")
                    : Component.translatable("gui.tarbound.reliquary.bound_to", below.getBlock().getName());
            String text = font.plainSubstrByWidth(binding.getString(), imageWidth - 16 - font.width(playerInventoryTitle) - 8);
            g.drawString(font, text, imageWidth - 8 - font.width(text), inventoryLabelY, tuned ? C_SPIRIT : 0xFFB0503C, false);
        }
    }
}
