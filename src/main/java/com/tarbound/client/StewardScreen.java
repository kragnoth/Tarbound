package com.tarbound.client;

import com.tarbound.entity.Steward;
import com.tarbound.entity.StockTarget;
import com.tarbound.menu.StewardMenu;
import com.tarbound.net.StewardEditPayload;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/**
 * A Steward's list. Top: up to 18 rows as cells (item, amount to keep, a line under it for what the Steward is doing).
 * Middle: the selected row's amount, typed or nudged. Bottom: everything the radio's contracts make; click one to put
 * it on the list. JEI/EMI items drop onto the cells.
 */
public class StewardScreen extends TarContainerScreen<StewardMenu> {
    private static final int[] STEPS = {-64, -8, -1, 1, 8, 64};
    private static final int STEP_W = 18;
    private static final int BOX_W = 44;
    private static final int C_OK = 0xFF6E9A52;
    private static final int C_BAD = 0xFFB4483C;
    private static final int C_WAIT = 0xFFC8963A;

    private EditBox amountBox;
    private EditBox searchBox;
    private int selected = -1;
    private int seenVersion = -1;
    private int scroll;
    private List<ItemStack> shownCraftables = List.of();
    private String lastSearch = "";
    private int lastCraftableVersion = -1;

    public StewardScreen(StewardMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, StewardMenu.IMAGE_WIDTH, StewardMenu.IMAGE_HEIGHT);
        this.inventoryLabelY = StewardMenu.INV_Y - 11;
    }

    @Override
    protected void init() {
        super.init();
        amountBox = new EditBox(font, boxX() + 3, topPos + StewardMenu.EDIT_Y + 3, BOX_W - 6, 10, Component.translatable("gui.tarbound.steward.keep"));
        amountBox.setBordered(false);
        amountBox.setMaxLength(7);
        amountBox.setTextColor(C_TEXT);
        amountBox.setFilter(s -> s.isEmpty() || s.chars().allMatch(Character::isDigit));
        addRenderableWidget(amountBox);
        int sw = 70;
        searchBox = new EditBox(font, leftPos + imageWidth - 8 - sw + 3, topPos + StewardMenu.LIST_LABEL_Y - 1, sw - 6, 10,
                Component.translatable("gui.tarbound.steward.search"));
        searchBox.setBordered(false);
        searchBox.setMaxLength(40);
        searchBox.setTextColor(C_TEXT);
        searchBox.setHint(Component.translatable("gui.tarbound.steward.search").withColor(C_TEXT_DIM));
        addRenderableWidget(searchBox);
    }

    // ------------------------------------------------------------------------------------------ layout

    private int boxX() {
        return leftPos + 8 + 3 * STEP_W + 4;
    }

    private int stepX(int i) {
        return i < 3 ? leftPos + 8 + i * STEP_W : boxX() + BOX_W + 4 + (i - 3) * STEP_W;
    }

    private int cellX(int i) {
        return leftPos + StewardMenu.TARGET_X + (i % StewardMenu.COLS) * 18;
    }

    private int cellY(int i) {
        return topPos + StewardMenu.TARGET_Y + (i / StewardMenu.COLS) * 18;
    }

    /** Cells that take an item: every row, plus the next empty one. */
    private int cellCount() {
        return Math.min(StockTarget.MAX, menu.targets().size() + 1);
    }

    private int cellAt(double mx, double my) {
        for (int i = 0; i < StockTarget.MAX; i++) {
            if (in(mx, my, cellX(i), cellY(i), 18, 18)) {
                return i;
            }
        }
        return -1;
    }

    private int craftableAt(double mx, double my) {
        for (int i = 0; i < StewardMenu.COLS * StewardMenu.LIST_ROWS; i++) {
            int x = leftPos + 8 + (i % StewardMenu.COLS) * 18;
            int y = topPos + StewardMenu.LIST_Y + (i / StewardMenu.COLS) * 18;
            if (in(mx, my, x, y, 18, 18)) {
                int index = scroll * StewardMenu.COLS + i;
                return index < shownCraftables.size() ? index : -1;
            }
        }
        return -1;
    }

    private static boolean in(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    /** For JEI/EMI: where an item can be dropped, and which row that is. */
    public record DropTarget(Rect2i area, int row) {}

    public List<DropTarget> dropTargets() {
        List<DropTarget> out = new ArrayList<>();
        for (int i = 0; i < cellCount(); i++) {
            out.add(new DropTarget(new Rect2i(cellX(i), cellY(i), 18, 18), i));
        }
        return out;
    }

    // ------------------------------------------------------------------------------------------ edits

    /** Puts {@code item} in row {@code row} (or a new row past the end), keeping that row's amount if it had one. */
    public void setRow(int row, ItemStack item) {
        if (item.isEmpty()) {
            return;
        }
        List<StockTarget> t = menu.targets();
        int keep = row >= 0 && row < t.size() ? t.get(row).keep() : StewardMenu.defaultKeep(item);
        int existing = -1;
        for (int i = 0; i < t.size(); i++) {
            if (ItemStack.isSameItemSameComponents(t.get(i).item(), item)) {
                existing = i;
            }
        }
        send(row, item.copyWithCount(1), existing >= 0 ? t.get(existing).keep() : keep);
        selected = existing >= 0 ? existing : row >= 0 && row < t.size() ? row : t.size();
        seenVersion = -1;
    }

    private void send(int row, ItemStack item, int keep) {
        PacketDistributor.sendToServer(new StewardEditPayload(menu.containerId, row, item, Math.max(1, Math.min(StockTarget.MAX_KEEP, keep))));
    }

    private void setKeep(int keep) {
        List<StockTarget> t = menu.targets();
        if (selected < 0 || selected >= t.size()) {
            return;
        }
        keep = Math.max(1, Math.min(StockTarget.MAX_KEEP, keep));
        send(selected, t.get(selected).item(), keep);
        amountBox.setValue(Integer.toString(keep));
    }

    private void commitBox() {
        String v = amountBox.getValue();
        if (!v.isEmpty() && selected >= 0 && selected < menu.targets().size()) {
            try {
                int keep = Integer.parseInt(v);
                if (keep != menu.targets().get(selected).keep()) {
                    setKeep(keep);
                }
            } catch (NumberFormatException ignored) {
                // too long for an int: filtered to 7 digits, so it can't be
            }
        }
    }

    // ------------------------------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        boolean boxFocused = amountBox.isFocused();
        int cell = cellAt(mx, my);
        if (cell >= 0) {
            if (boxFocused) {
                commitBox();
            }
            List<StockTarget> t = menu.targets();
            ItemStack carried = menu.getCarried();
            if (!carried.isEmpty() && cell < cellCount()) {
                setRow(cell, carried);
            } else if (button == 1 && cell < t.size()) {
                send(cell, ItemStack.EMPTY, 1);
                selected = -1;
                amountBox.setValue("");
            } else if (cell < t.size()) {
                selected = cell;
                seenVersion = -1;
            }
            amountBox.setFocused(false);
            return true;
        }
        int c = craftableAt(mx, my);
        if (c >= 0) {
            ItemStack item = shownCraftables.get(c);
            boolean replace = selected >= 0 && selected < menu.targets().size() && hasShiftDown();
            if (replace || menu.targets().size() < StockTarget.MAX || contains(item)) {
                setRow(replace ? selected : Integer.MAX_VALUE, item);
            }
            return true;
        }
        for (int i = 0; i < STEPS.length; i++) {
            if (in(mx, my, stepX(i), topPos + StewardMenu.EDIT_Y, STEP_W - 1, 15) && selected >= 0 && selected < menu.targets().size()) {
                int step = STEPS[i] * (hasShiftDown() ? 10 : 1);
                setKeep(menu.targets().get(selected).keep() + step);
                return true;
            }
        }
        boolean handled = super.mouseClicked(mx, my, button);
        if (boxFocused && !amountBox.isFocused()) {
            commitBox();
        }
        return handled;
    }

    private boolean contains(ItemStack item) {
        for (StockTarget t : menu.targets()) {
            if (ItemStack.isSameItemSameComponents(t.item(), item)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        if (my >= topPos + StewardMenu.LIST_Y && my < topPos + StewardMenu.LIST_Y + StewardMenu.LIST_ROWS * 18 && dy != 0) {
            scroll = Math.max(0, Math.min(maxScroll(), scroll - (int) Math.signum(dy)));
            return true;
        }
        int cell = cellAt(mx, my);
        if (cell >= 0 && cell < menu.targets().size() && dy != 0) {
            selected = cell;
            int step = hasShiftDown() ? 64 : hasControlDown() ? 8 : 1;
            setKeep(menu.targets().get(cell).keep() + (dy > 0 ? step : -step));
            return true;
        }
        return super.mouseScrolled(mx, my, dx, dy);
    }

    private int maxScroll() {
        int rows = (shownCraftables.size() + StewardMenu.COLS - 1) / StewardMenu.COLS;
        return Math.max(0, rows - StewardMenu.LIST_ROWS);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        for (EditBox box : new EditBox[] {amountBox, searchBox}) {
            if (box.isFocused()) {
                if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                    if (box == amountBox) {
                        commitBox();
                    }
                    box.setFocused(false);
                    setFocused(null);
                    return true;
                }
                if (keyCode != GLFW.GLFW_KEY_ESCAPE) {
                    return box.keyPressed(keyCode, scanCode, modifiers) || box.canConsumeInput();
                }
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void removed() {
        commitBox();
        super.removed();
    }

    // ------------------------------------------------------------------------------------------ drawing

    @Override
    protected void containerTick() {
        super.containerTick();
        List<StockTarget> t = menu.targets();
        if (selected >= t.size()) {
            selected = t.isEmpty() ? -1 : Math.min(selected, t.size() - 1);
            seenVersion = -1;
        }
        if (menu.version() != seenVersion && !amountBox.isFocused()) {
            seenVersion = menu.version();
            amountBox.setValue(selected >= 0 && selected < t.size() ? Integer.toString(t.get(selected).keep()) : "");
        }
        amountBox.setEditable(selected >= 0 && selected < t.size());
        String q = searchBox.getValue().toLowerCase(Locale.ROOT).strip();
        if (!q.equals(lastSearch) || lastCraftableVersion != menu.craftables().size()) {
            lastSearch = q;
            lastCraftableVersion = menu.craftables().size();
            shownCraftables = q.isEmpty() ? menu.craftables()
                    : menu.craftables().stream().filter(s -> s.getHoverName().getString().toLowerCase(Locale.ROOT).contains(q)).toList();
            scroll = Math.min(scroll, maxScroll());
        }
    }

    @Override
    protected void renderExtras(GuiGraphics g, int mouseX, int mouseY) {
        List<StockTarget> t = menu.targets();
        // the list
        for (int i = 0; i < StockTarget.MAX; i++) {
            int x = cellX(i);
            int y = cellY(i);
            inset(g, x, y, 18, 18);
            if (i < t.size()) {
                g.fill(x + 2, y + 16, x + 16, y + 17, colour(menu.status(i)));
            } else if (i == t.size()) {
                g.drawString(font, "+", x + 7, y + 5, C_TEXT_DIM, false);
            } else {
                g.fill(x + 1, y + 1, x + 17, y + 17, 0x60100C0A);
            }
            if (i == selected) {
                g.renderOutline(x, y, 18, 18, C_SPIRIT);
            }
        }
        // amount controls
        boolean editing = selected >= 0 && selected < t.size();
        for (int i = 0; i < STEPS.length; i++) {
            int x = stepX(i);
            int y = topPos + StewardMenu.EDIT_Y;
            boolean hover = editing && in(mouseX, mouseY, x, y, STEP_W - 1, 15);
            g.fill(x, y, x + STEP_W - 1, y + 15, hover ? C_PANEL_HI : C_SLOT_HI);
            String label = (STEPS[i] > 0 ? "+" : "") + STEPS[i];
            g.drawString(font, label, x + (STEP_W - 1 - font.width(label)) / 2 + 1, y + 4, editing ? C_TEXT : C_TEXT_DIM, false);
        }
        inset(g, boxX(), topPos + StewardMenu.EDIT_Y, BOX_W, 15);
        // what the radio can make
        g.drawString(font, Component.translatable("gui.tarbound.steward.made"), leftPos + 8, topPos + StewardMenu.LIST_LABEL_Y, C_TEXT_DIM, false);
        inset(g, searchBox.getX() - 3, searchBox.getY() - 2, searchBox.getWidth() + 6, 12);
        for (int i = 0; i < StewardMenu.COLS * StewardMenu.LIST_ROWS; i++) {
            int x = leftPos + 8 + (i % StewardMenu.COLS) * 18;
            int y = topPos + StewardMenu.LIST_Y + (i / StewardMenu.COLS) * 18;
            inset(g, x, y, 18, 18);
            int index = scroll * StewardMenu.COLS + i;
            if (index < shownCraftables.size()) {
                ItemStack s = shownCraftables.get(index);
                g.renderItem(s, x + 1, y + 1);
                if (contains(s)) {
                    g.fill(x + 1, y + 1, x + 3, y + 3, C_SPIRIT);
                }
            }
        }
        int rows = (shownCraftables.size() + StewardMenu.COLS - 1) / StewardMenu.COLS;
        if (rows > StewardMenu.LIST_ROWS) {
            int h = StewardMenu.LIST_ROWS * 18;
            int bar = Math.max(6, h * StewardMenu.LIST_ROWS / rows);
            int by = topPos + StewardMenu.LIST_Y + (h - bar) * scroll / Math.max(1, maxScroll());
            g.fill(leftPos + imageWidth - 6, by, leftPos + imageWidth - 4, by + bar, C_PANEL_HI);
        }
        if (shownCraftables.isEmpty()) {
            g.drawString(font, Component.translatable(menu.craftables().isEmpty() ? "gui.tarbound.steward.nothing_made" : "gui.tarbound.steward.no_match"),
                    leftPos + 10, topPos + StewardMenu.LIST_Y + 5, C_TEXT_DIM, false);
        }
    }

    private static int colour(Steward.Status s) {
        return switch (s) {
            case STOCKED -> C_OK;
            case MAKING -> C_SPIRIT;
            case NO_SLOT, ORDER_BUSY -> C_WAIT;
            case NO_RADIO -> C_TEXT_DIM;
            default -> C_BAD;
        };
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        super.renderLabels(g, mouseX, mouseY);
        if (menu.radio() != 0) {
            Component warn = Component.translatable(menu.radio() == 1 ? "gui.tarbound.steward.untuned" : "gui.tarbound.steward.no_radio");
            g.drawString(font, warn, imageWidth - 8 - font.width(warn), titleLabelY, C_BAD, false);
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        List<StockTarget> t = menu.targets();
        // items and amounts over the wells (after the slots, so nothing covers them)
        for (int i = 0; i < t.size(); i++) {
            int x = cellX(i);
            int y = cellY(i);
            g.renderItem(t.get(i).item(), x + 1, y + 1);
            g.renderItemDecorations(font, t.get(i).item(), x + 1, y, shortCount(t.get(i).keep()));
        }
        int cell = cellAt(mouseX, mouseY);
        if (cell >= 0 && cell < t.size() && menu.getCarried().isEmpty()) {
            StockTarget row = t.get(cell);
            List<Component> lines = new ArrayList<>();
            lines.add(row.item().getHoverName().copy().withStyle(ChatFormatting.WHITE));
            lines.add(Component.translatable("gui.tarbound.steward.row", String.format(Locale.ROOT, "%,d", menu.have(cell)),
                    String.format(Locale.ROOT, "%,d", row.keep())).withStyle(ChatFormatting.GRAY));
            Steward.Status s = menu.status(cell);
            lines.add(Component.translatable("gui.tarbound.steward.status." + s.name().toLowerCase(Locale.ROOT))
                    .withColor(colour(s)));
            lines.add(Component.translatable("gui.tarbound.steward.row_hint").withStyle(ChatFormatting.DARK_GRAY));
            g.renderComponentTooltip(font, lines, mouseX, mouseY);
        } else if (cell >= 0 && cell == t.size() && menu.getCarried().isEmpty()) {
            g.renderComponentTooltip(font, List.of(Component.translatable("gui.tarbound.steward.empty").withStyle(ChatFormatting.GRAY),
                    Component.translatable("gui.tarbound.steward.empty_hint").withStyle(ChatFormatting.DARK_GRAY)), mouseX, mouseY);
        } else {
            int c = craftableAt(mouseX, mouseY);
            if (c >= 0 && menu.getCarried().isEmpty()) {
                List<Component> lines = new ArrayList<>(getTooltipFromItem(minecraft, shownCraftables.get(c)));
                lines.add(Component.translatable(contains(shownCraftables.get(c)) ? "gui.tarbound.steward.on_list" : "gui.tarbound.steward.add_hint")
                        .withStyle(ChatFormatting.DARK_AQUA));
                g.renderComponentTooltip(font, lines, mouseX, mouseY);
            } else if (in(mouseX, mouseY, leftPos + 8, topPos + StewardMenu.EDIT_Y, imageWidth - 16, 15)) {
                g.renderComponentTooltip(font, List.of(Component.translatable("gui.tarbound.steward.keep_hint").withStyle(ChatFormatting.GRAY)), mouseX, mouseY);
            }
        }
    }

    /** The item under the mouse in the list or the craftables, for JEI/EMI's R and U. */
    public ItemStack stackUnderMouse(double mx, double my) {
        int cell = cellAt(mx, my);
        if (cell >= 0 && cell < menu.targets().size()) {
            return menu.targets().get(cell).item();
        }
        int c = craftableAt(mx, my);
        return c >= 0 ? shownCraftables.get(c) : ItemStack.EMPTY;
    }

    static String shortCount(long n) {
        if (n < 1000) {
            return Long.toString(n);
        }
        if (n < 10_000) {
            return String.format(Locale.ROOT, "%.1fk", n / 1000.0).replace(".0k", "k");
        }
        if (n < 1_000_000) {
            return (n / 1000) + "k";
        }
        return String.format(Locale.ROOT, "%.1fM", n / 1_000_000.0).replace(".0M", "M");
    }
}
