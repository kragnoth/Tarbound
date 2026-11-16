package com.tarbound.client;

import com.tarbound.contract.ContractData;
import com.tarbound.contract.ContractDraft;
import com.tarbound.contract.ContractDraft.Ghost;
import com.tarbound.contract.StandingOrder;
import com.tarbound.menu.ScribingLecternMenu;
import com.tarbound.net.GeneralizeContractPayload;
import com.tarbound.net.WriteContractPayload;
import com.tarbound.net.WriteCraftingGridPayload;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.network.PacketDistributor;

import static com.tarbound.menu.ScribingLecternMenu.*;

/**
 * The Scribing Lectern as a pattern terminal. Ghost slots (nothing real goes in them): click with an item on the
 * cursor to set one, right-click to clear, scroll to change a processing amount (shift for bigger steps), or drag
 * items and fluids in from JEI/EMI; their + button fills the whole layout. Crafting mode shows the result of the
 * 3x3 grid; machine (processing) mode has inputs and outputs. Write turns the blank contract into it.
 * A written contract with one input can still be generalized to one of its tags here.
 */
public class ScribingLecternScreen extends TarContainerScreen<ScribingLecternMenu> {
    private static final int BUTTON_Y = 78;
    private static final int PICK_Y = 96;
    private static final int GEN_Y = PICK_Y + 13;

    /** A ghost slot: where it is, and which part of the draft it holds. */
    public record GhostTarget(Rect2i area, Part part, int index) {
        public boolean takesFluids() {
            return part != Part.GRID;
        }
    }

    public enum Part { GRID, INPUT, OUTPUT }

    private Button mode;
    private Button subs;
    private Button clear;
    private Button write;
    private Button prev;
    private Button next;
    private Button generalize;
    private Button back;
    /** What the contracts the player carries (and the written one here) make: marks the grid's links in a chain. */
    private java.util.Set<Item> contracted = java.util.Set.of();
    private List<TagKey<Item>> tags = List.of();
    private ContractData tagsFor;
    private int tagIndex;
    private int resultVersion = -1;
    private ItemStack result = ItemStack.EMPTY;

    public ScribingLecternScreen(ScribingLecternMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, IMAGE_WIDTH, IMAGE_HEIGHT);
    }

    private ContractDraft draft() {
        return menu.draft;
    }

    @Override
    protected void init() {
        super.init();
        mode = addRenderableWidget(Button.builder(Component.empty(), b -> {
            draft().processing = !draft().processing;
            draft().changed();
            updateButtons();
        }).bounds(leftPos + 8, topPos + BUTTON_Y, 48, 12).build());
        subs = addRenderableWidget(Button.builder(Component.empty(), b -> {
            draft().substitutes = !draft().substitutes;
            updateButtons();
        }).bounds(leftPos + 58, topPos + BUTTON_Y, 56, 12).build());
        clear = addRenderableWidget(Button.builder(Component.literal("✕"), b -> draft().clear())
                .bounds(leftPos + 116, topPos + BUTTON_Y, 14, 12)
                .tooltip(Tooltip.create(Component.translatable("gui.tarbound.lectern.clear"))).build());
        write = addRenderableWidget(Button.builder(Component.translatable("gui.tarbound.lectern.write"), b -> sendWrite())
                .bounds(leftPos + 132, topPos + BUTTON_Y, 36, 12).build());
        prev = addRenderableWidget(Button.builder(Component.literal("<"), b -> step(-1))
                .bounds(leftPos + 8, topPos + PICK_Y, 12, 12).build());
        next = addRenderableWidget(Button.builder(Component.literal(">"), b -> step(1))
                .bounds(leftPos + imageWidth - 20, topPos + PICK_Y, 12, 12).build());
        generalize = addRenderableWidget(Button.builder(Component.translatable("gui.tarbound.lectern.generalize"), b -> sendGeneralize())
                .bounds(leftPos + (imageWidth - 90) / 2, topPos + GEN_Y, 90, 12)
                .tooltip(Tooltip.create(Component.translatable("gui.tarbound.lectern.generalize_tip")))
                .build());
        back = addRenderableWidget(Button.builder(Component.literal("↶"), b -> draft().back())
                .bounds(leftPos + imageWidth - 22, topPos + 4, 14, 12)
                .tooltip(Tooltip.create(Component.translatable("gui.tarbound.lectern.back"))).build());
        updateButtons();
        refreshTags();
    }

    /** Re-reads what the player's written contracts make (cheap: the inventory and two lectern slots). */
    private void refreshContracted() {
        java.util.Set<Item> made = new java.util.HashSet<>();
        java.util.function.Consumer<ItemStack> look = s -> {
            ContractData d = s.get(com.tarbound.registry.ModDataComponents.CONTRACT.get());
            if (d != null) {
                d.outputs().forEach(o -> made.add(o.getItem()));
            }
        };
        if (minecraft != null && minecraft.player != null) {
            minecraft.player.getInventory().items.forEach(look);
        }
        menu.slots.forEach(slot -> look.accept(slot.getItem()));
        contracted = made;
    }

    /**
     * The ghost (or crafting result) under the mouse, for JEI and EMI: their recipe and use keys work on the grid,
     * so a chain of contracts can be built from it, ingredient by ingredient.
     */
    @javax.annotation.Nullable
    public ScryingFontScreen.HoveredStack ghostUnderMouse(double mx, double my) {
        GhostTarget t = targetAt(mx, my);
        if (t != null) {
            Ghost g = ghostAt(t);
            if (g.isEmpty()) {
                return null;
            }
            return new ScryingFontScreen.HoveredStack(g.item(), g.fluid(), t.area());
        }
        if (!draft().processing && resultArea().contains((int) mx, (int) my) && !result().isEmpty()) {
            return new ScryingFontScreen.HoveredStack(result(), FluidStack.EMPTY, resultArea());
        }
        return null;
    }

    private void updateButtons() {
        mode.setMessage(Component.translatable(draft().processing ? "gui.tarbound.lectern.mode_machine" : "gui.tarbound.lectern.mode_crafting"));
        mode.setTooltip(Tooltip.create(Component.translatable("gui.tarbound.lectern.mode_tip")));
        subs.setMessage(Component.translatable(draft().substitutes ? "gui.tarbound.lectern.subs_on" : "gui.tarbound.lectern.subs_off"));
        subs.setTooltip(Tooltip.create(Component.translatable(draft().substitutes ? "gui.tarbound.lectern.subs_on_tip" : "gui.tarbound.lectern.subs_off_tip")));
        Component why = writeProblem();
        write.active = why == null;
        write.setTooltip(Tooltip.create(why != null ? why : Component.translatable("gui.tarbound.lectern.write_tip")));
    }

    /** Why Write can't be pressed right now, or null. */
    private Component writeProblem() {
        if (!menu.hasBlank()) {
            return Component.translatable("gui.tarbound.lectern.need_blank");
        }
        if (!menu.writtenSlotFree()) {
            return Component.translatable("gui.tarbound.lectern.take_written");
        }
        if (draft().processing ? !draft().processingContract().isValid() : result().isEmpty()) {
            return Component.translatable(draft().processing ? "gui.tarbound.lectern.nothing" : "gui.tarbound.lectern.no_recipe");
        }
        return null;
    }

    private void sendWrite() {
        if (writeProblem() != null) {
            return;
        }
        if (draft().processing) {
            PacketDistributor.sendToServer(new WriteContractPayload(menu.containerId, draft().processingContract()));
        } else {
            PacketDistributor.sendToServer(new WriteCraftingGridPayload(menu.containerId, draft().gridList(), draft().substitutes));
        }
    }

    /** What the crafting grid makes, looked up again only when the grid changes. */
    public ItemStack result() {
        if (resultVersion != draft().version()) {
            resultVersion = draft().version();
            result = ItemStack.EMPTY;
            var level = Minecraft.getInstance().level;
            if (level != null && !draft().processing) {
                CraftingInput input = CraftingInput.of(3, 3, draft().gridList());
                result = level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level)
                        .map(h -> h.value().assemble(input, level.registryAccess()))
                        .orElse(ItemStack.EMPTY);
            }
        }
        return result;
    }

    // ---------------------------------------------------------------- ghost slots

    /** Every ghost slot showing now, in screen coordinates (JEI and EMI drop onto these too). */
    public List<GhostTarget> ghostTargets() {
        List<GhostTarget> out = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            Rect2i r = new Rect2i(leftPos + GRID_X + (i % 3) * 18, topPos + GRID_Y + (i / 3) * 18, 16, 16);
            out.add(new GhostTarget(r, draft().processing ? Part.INPUT : Part.GRID, i));
        }
        if (draft().processing) {
            for (int i = 0; i < ContractDraft.OUTPUTS; i++) {
                out.add(new GhostTarget(new Rect2i(leftPos + OUT_X + (i % 2) * 18, topPos + OUT_Y + (i / 2) * 18, 16, 16), Part.OUTPUT, i));
            }
        }
        return out;
    }

    private Rect2i resultArea() {
        return new Rect2i(leftPos + OUT_X + 9, topPos + OUT_Y + 18, 16, 16);
    }

    /** Put an item (with the alternatives a recipe would accept, if known) into a ghost slot. */
    public void setGhost(GhostTarget t, ItemStack item, List<ItemStack> alternatives) {
        if (item.isEmpty()) {
            return;
        }
        switch (t.part()) {
            case GRID -> draft().grid[t.index()] = item.copyWithCount(1);
            case INPUT -> draft().inputs[t.index()] = Ghost.of(item, alternatives);
            case OUTPUT -> draft().outputs[t.index()] = Ghost.of(item);
        }
        draft().changed();
    }

    /** Put a fluid into a ghost slot (processing only; a bucket's worth if it says no amount). */
    public void setGhost(GhostTarget t, FluidStack fluid) {
        if (fluid.isEmpty() || !t.takesFluids()) {
            return;
        }
        Ghost g = Ghost.of(fluid.getAmount() > 0 ? fluid : fluid.copyWithAmount(1000));
        if (t.part() == Part.INPUT) {
            draft().inputs[t.index()] = g;
        } else {
            draft().outputs[t.index()] = g;
        }
        draft().changed();
    }

    private Ghost ghostAt(GhostTarget t) {
        return switch (t.part()) {
            case GRID -> Ghost.of(draft().grid[t.index()]);
            case INPUT -> draft().inputs[t.index()];
            case OUTPUT -> draft().outputs[t.index()];
        };
    }

    private void putGhost(GhostTarget t, Ghost g) {
        switch (t.part()) {
            case GRID -> draft().grid[t.index()] = g.item().isEmpty() ? ItemStack.EMPTY : g.item().copyWithCount(1);
            case INPUT -> draft().inputs[t.index()] = g;
            case OUTPUT -> draft().outputs[t.index()] = g;
        }
        draft().changed();
    }

    private GhostTarget targetAt(double mx, double my) {
        for (GhostTarget t : ghostTargets()) {
            Rect2i r = t.area();
            if (mx >= r.getX() - 1 && mx < r.getX() + 17 && my >= r.getY() - 1 && my < r.getY() + 17) {
                return t;
            }
        }
        return null;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        GhostTarget t = targetAt(mx, my);
        if (t != null) {
            if (button == 1) {
                putGhost(t, Ghost.EMPTY);
            } else if (button == 0) {
                ItemStack carried = menu.getCarried();
                if (!carried.isEmpty()) {
                    // a filled bucket or tank on the cursor sets its fluid, when shift is held (or for fluid-only slots)
                    FluidStack fluid = t.takesFluids() && Screen.hasShiftDown()
                            ? FluidUtil.getFluidContained(carried).orElse(FluidStack.EMPTY) : FluidStack.EMPTY;
                    if (!fluid.isEmpty()) {
                        setGhost(t, fluid);
                    } else {
                        setGhost(t, t.part() == Part.GRID ? carried.copyWithCount(1) : carried.copy(), List.of());
                    }
                }
            }
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        GhostTarget t = targetAt(mx, my);
        if (t != null && t.part() != Part.GRID && dy != 0) {
            Ghost g = ghostAt(t);
            if (!g.isEmpty()) {
                int step = !g.fluid().isEmpty() ? (Screen.hasShiftDown() ? 1000 : 100) : (Screen.hasShiftDown() ? 16 : 1);
                int amount = g.amount() + (dy > 0 ? step : -step);
                if (!g.fluid().isEmpty() && dy < 0 && g.amount() > 1 && amount < 1) {
                    amount = 1;
                }
                putGhost(t, g.withAmount(amount));
            }
            return true;
        }
        return super.mouseScrolled(mx, my, dx, dy);
    }

    // ---------------------------------------------------------------- the tag picker (as before)

    private void step(int d) {
        if (!tags.isEmpty()) {
            tagIndex = Math.floorMod(tagIndex + d, tags.size());
        }
    }

    private void sendGeneralize() {
        if (!tags.isEmpty()) {
            PacketDistributor.sendToServer(new GeneralizeContractPayload(menu.containerId, tags.get(tagIndex).location()));
        }
    }

    /** Re-reads the written slot; the tag list is rebuilt only when the contract there changes. */
    private void refreshTags() {
        ContractData data = menu.generalizable();
        if (data == null ? tagsFor != null : !data.sameAs(tagsFor)) {
            tagsFor = data;
            tags = ScribingLecternMenu.candidateTags(data);
            tagIndex = 0;
        }
        boolean show = !tags.isEmpty();
        prev.visible = show;
        next.visible = show;
        generalize.visible = show;
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        refreshTags();
        refreshContracted();
        updateButtons();
        if (back != null) {
            back.visible = draft().canGoBack();
        }
    }

    // ---------------------------------------------------------------- drawing

    @Override
    protected void renderExtras(GuiGraphics g, int mouseX, int mouseY) {
        for (GhostTarget t : ghostTargets()) {
            Rect2i r = t.area();
            inset(g, r.getX() - 1, r.getY() - 1, 18, 18);
            Ghost ghost = ghostAt(t);
            if (!ghost.item().isEmpty()) {
                g.renderItem(ghost.item(), r.getX(), r.getY());
                if (t.part() != Part.GRID) {
                    g.renderItemDecorations(font, ghost.item(), r.getX(), r.getY());
                }
                if (t.part() == Part.INPUT && draft().substitutes && !ghost.alternatives().isEmpty()) {
                    g.fill(r.getX(), r.getY(), r.getX() + 3, r.getY() + 3, C_SPIRIT); // marks an input with substitutes
                }
                if (t.part() != Part.OUTPUT) {
                    // top right: green if a contract you carry makes this ingredient, amber if none does yet
                    int mark = contracted.contains(ghost.item().getItem()) ? 0xFF6FD66F : 0xFFE0A040;
                    g.pose().pushPose();
                    g.pose().translate(0, 0, 250);
                    g.fill(r.getX() + 13, r.getY(), r.getX() + 16, r.getY() + 3, mark);
                    g.pose().popPose();
                }
            } else if (!ghost.fluid().isEmpty()) {
                ScryingFontScreen.renderFluid(g, ghost.fluid(), r.getX(), r.getY(), 16, 16);
                String amount = ghost.fluid().getAmount() >= 1000 && ghost.fluid().getAmount() % 1000 == 0
                        ? (ghost.fluid().getAmount() / 1000) + "B" : ghost.fluid().getAmount() + "";
                g.pose().pushPose();
                g.pose().translate(r.getX() + 16, r.getY() + 11, 300);
                g.pose().scale(0.5F, 0.5F, 1F);
                g.drawString(font, amount, -font.width(amount), 0, 0xFFFFFFFF, true);
                g.pose().popPose();
            }
        }
        // arrow from the grid to the outputs
        int ax = leftPos + GRID_X + 54 + 3;
        int ay = topPos + GRID_Y + 25;
        g.fill(ax, ay, ax + 14, ay + 2, C_TEXT_DIM);
        for (int i = 0; i < 4; i++) {
            g.fill(ax + 14 + i, ay - 3 + i, ax + 15 + i, ay + 5 - i, C_TEXT_DIM);
        }
        if (!draft().processing) {
            Rect2i r = resultArea();
            inset(g, r.getX() - 3, r.getY() - 3, 22, 22);
            ItemStack out = result();
            if (!out.isEmpty()) {
                g.renderItem(out, r.getX(), r.getY());
                g.renderItemDecorations(font, out, r.getX(), r.getY());
            }
        }
        // hover highlight
        GhostTarget hovered = targetAt(mouseX, mouseY);
        if (hovered != null) {
            Rect2i r = hovered.area();
            g.fill(r.getX(), r.getY(), r.getX() + 16, r.getY() + 16, 0x30FFFFFF);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, titleLabelX, titleLabelY, C_TEXT, false);
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, C_TEXT_DIM, false);
        if (!tags.isEmpty()) {
            String text = font.plainSubstrByWidth(StandingOrder.tagName(tags.get(tagIndex)).getString()
                    + (tags.size() > 1 ? "  (" + (tagIndex + 1) + "/" + tags.size() + ")" : ""), imageWidth - 48);
            g.drawString(font, text, (imageWidth - font.width(text)) / 2, PICK_Y + 2, C_SPIRIT, false);
            return;
        }
        Component hint = !menu.hasBlank()
                ? Component.translatable("gui.tarbound.lectern.hint_blank")
                : !menu.writtenSlotFree()
                ? Component.translatable("gui.tarbound.lectern.hint_take")
                : Component.translatable("gui.tarbound.lectern.hint_write");
        int y = PICK_Y;
        for (var line : font.split(hint, imageWidth - 16)) {
            g.drawString(font, line, (imageWidth - font.width(line)) / 2, y, C_TEXT_DIM, false);
            y += 10;
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        if (!menu.getCarried().isEmpty()) {
            return;
        }
        GhostTarget t = targetAt(mouseX, mouseY);
        if (t != null) {
            Ghost ghost = ghostAt(t);
            List<Component> lines = new ArrayList<>();
            if (!ghost.item().isEmpty()) {
                lines.addAll(getTooltipFromContainerItem(ghost.item()));
                if (t.part() == Part.INPUT && !ghost.alternatives().isEmpty()) {
                    lines.add(Component.translatable(draft().substitutes ? "gui.tarbound.lectern.alternatives" : "gui.tarbound.lectern.alternatives_off",
                            ghost.alternatives().size()).withStyle(ChatFormatting.DARK_AQUA));
                }
                if (t.part() != Part.OUTPUT) {
                    lines.add(contracted.contains(ghost.item().getItem())
                            ? Component.translatable("gui.tarbound.lectern.has_contract").withStyle(ChatFormatting.GREEN)
                            : Component.translatable("gui.tarbound.lectern.no_contract").withStyle(ChatFormatting.GOLD));
                }
            } else if (!ghost.fluid().isEmpty()) {
                lines.add(ghost.fluid().getHoverName());
                lines.add(Component.literal(ghost.fluid().getAmount() + " mB").withStyle(ChatFormatting.GRAY));
            }
            lines.add(Component.translatable(t.part() == Part.GRID ? "gui.tarbound.lectern.ghost_tip" : "gui.tarbound.lectern.ghost_tip_amount")
                    .withStyle(ChatFormatting.DARK_GRAY));
            g.renderComponentTooltip(font, lines, mouseX, mouseY);
        } else if (!draft().processing && resultArea().contains(mouseX, mouseY) && !result().isEmpty()) {
            g.renderTooltip(font, result(), mouseX, mouseY);
        }
    }
}
