package com.tarbound.client;

import com.tarbound.config.TarboundConfig;

import com.tarbound.menu.ScryingFontMenu;
import com.tarbound.net.ContentsPayload;
import com.tarbound.net.FontActionPayload;
import com.tarbound.net.TrayActionPayload;
import com.tarbound.net.TrayPayload;
import com.tarbound.net.JobActionPayload;
import com.tarbound.net.StandingTogglePayload;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import com.tarbound.contract.ContractData;
import com.tarbound.network.ItemKey;
import java.util.LinkedHashMap;
import java.util.Map;
import com.tarbound.network.BoundInventory;
import com.tarbound.network.TarNetwork;
import com.tarbound.registry.ModItems;
import com.tarbound.block.ScryingFontBlockEntity;
import com.tarbound.contract.ContractJob;
import com.tarbound.contract.StandingOrder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import static com.tarbound.menu.ScryingFontMenu.*;

/**
 * Scrying Font screen: the pooled storage view and crafting grid, plus a Reliquary tab on the right
 * that opens the machine window (a scrolling list of bound machines and the slots of the one picked).
 */
public class ScryingFontScreen extends AbstractContainerScreen<ScryingFontMenu> {
    // Palette: pitch, soot and old bone.
    private static final int C_OUTLINE = 0xFF000000;
    private static final int C_PANEL = 0xFF2B2622;
    private static final int C_PANEL_HI = 0xFF4D443C;
    private static final int C_PANEL_LO = 0xFF15110F;
    private static final int C_SLOT = 0xFF171311;
    private static final int C_SLOT_LO = 0xFF0A0807;
    private static final int C_SLOT_HI = 0xFF4A4038;
    private static final int C_TEXT = 0xFFCDBB9A;
    private static final int C_TEXT_BRIGHT = 0xFFF0E4C8;
    private static final int C_TEXT_DIM = 0xFF8C7D66;
    private static final int C_WARN = 0xFFB0503C;
    private static final int C_THUMB = 0xFF8C7D66;
    private static final int C_THUMB_OFF = 0xFF3A332D;
    private static final int C_MACHINE = 0xFFB08A4A;
    private static final int C_SPIRIT = 0xFF7FE6F0;
    private static final int C_ROW_SELECTED = 0xFF4A3F2E;
    private static final int C_ROW_HOVER = 0xFF362F28;
    private static final int C_ROLE_FUEL = 0xFFE0782A;
    private static final int C_ROLE_OUTPUT = 0xFF7FE0A0;

    // ---- Pool scrollbar ----
    private static final int SCROLL_X = 174;
    private static final int SCROLL_Y = GRID_Y;
    private static final int SCROLL_W = 12;
    private static final int SCROLL_H = GRID_ROWS * 18;
    private static final int THUMB_H = 15;

    // ---- Machine tab and pop-out (relative to tabX()/popX(), topPos) ----
    private static final int TAB_W = 24;
    private static final int TAB_H = 26;
    private static final int TAB_Y = 18;
    public static final int POP_W = 118;
    public static final int POP_H = 204;
    private static final int LIST_Y = 18;
    /** Machine window: one scrolling list, each machine a header and its slots. */
    private static final int VIEW_Y = 18;
    private static final int VIEW_H = POP_H - VIEW_Y - 6;
    private static final int HDR_H = 20;
    private static final int BLOCK_GAP = 4;
    private static final int SCROLL_STEP = 18;
    private static final int ROW_H = 20;
    private static final int SLOTS_X = 8;
    private static final int SLOT_COLS = 5;
    private static final int POP_SCROLL_X = POP_W - 13;
    private static final int POP_SCROLL_W = 6;

    private enum Sort {
        COUNT("C", "gui.tarbound.sort.count"),
        NAME("A", "gui.tarbound.sort.name"),
        MOD("@", "gui.tarbound.sort.mod");

        final String glyph;
        final String key;

        Sort(String glyph, String key) {
            this.glyph = glyph;
            this.key = key;
        }

        Sort next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    // Remembered across openings for this session.
    private static Sort sort = Sort.COUNT;
    private enum Pane { NONE, MACHINES, JOBS }

    private static Pane pane = Pane.NONE;

    /** A pool item (or, for a fluid-only contract, a fluid) some bound machine's contract can make. */
    private record Craftable(ItemStack output, net.neoforged.neoforge.fluids.FluidStack fluid, BlockPos machine, int slot, ContractData contract,
            Component machineName) {
        boolean isFluid() {
            return !fluid.isEmpty();
        }

        /** Same identity as {@link #key(ContentsPayload.Entry)}: a FluidKey or an ItemKey. */
        Object key() {
            return isFluid() ? com.tarbound.network.FluidKey.of(fluid) : ItemKey.of(output);
        }

        Component name() {
            return isFluid() ? fluid.getHoverName() : output.getHoverName();
        }
    }

    /** Keyed like pool entries ({@link #key(ContentsPayload.Entry)}): ItemKeys, and FluidKeys for fluids. */
    private final Map<Object, Craftable> craftables = new LinkedHashMap<>();
    @Nullable
    private Craftable requesting;
    private int requestAmount;
    private int jobScroll;
    private int standingScroll;
    /** Cancel-all was clicked once; a second click before this time (ms) confirms. */
    private long cancelAllArmedUntil;

    private EditBox searchBox;
    /** The search box's inset; the ⇄ sync button sits to its left. */
    /** Spirit: candle button beside the result slot, flame meter at the right edge of the crafting area. */
    private static final int CANDLE_X = 152;
    private static final int CANDLE_Y = 133;
    private static final int METER_X = 172;
    private static final int METER_Y = 115;
    private static final int METER_W = 8;
    private static final int METER_H = 52;
    private static final int SEARCH_X = 104;
    private static final int SEARCH_W = 68;
    @Nullable
    private Button syncButton;
    /** The recipe viewer's search text as of our last look or push, to spot the player typing there. */
    private String lastViewerSearch = "";

    private static boolean syncOn() {
        return TarboundConfig.CLIENT.syncViewerSearch.get() && com.tarbound.compat.SearchSync.available();
    }

    private static Component craftFirstLabel() {
        return Component.literal("◆").withStyle(TarboundConfig.CLIENT.craftablesFirst.get() ? ChatFormatting.AQUA : ChatFormatting.DARK_GRAY);
    }

    private static Tooltip craftFirstTooltip() {
        return Tooltip.create(Component.translatable(TarboundConfig.CLIENT.craftablesFirst.get()
                ? "gui.tarbound.craftables_first.on" : "gui.tarbound.craftables_first.off"));
    }

    private static TarboundConfig.PoolView poolView() {
        return TarboundConfig.CLIENT.poolView.get();
    }

    private static Component poolViewLabel() {
        return switch (poolView()) {
            case ITEMS -> Component.literal("▪").withStyle(ChatFormatting.GOLD);
            case FLUIDS -> Component.literal("≈").withStyle(ChatFormatting.AQUA);
            case BOTH -> Component.literal("±").withStyle(ChatFormatting.GREEN);
        };
    }

    private static Tooltip poolViewTooltip() {
        return Tooltip.create(Component.translatable("gui.tarbound.pool_view." + poolView().name().toLowerCase(Locale.ROOT)));
    }

    private static Component syncLabel() {
        return Component.literal("⇄").withStyle(TarboundConfig.CLIENT.syncViewerSearch.get() ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY);
    }

    private static Tooltip syncTooltip() {
        return Tooltip.create(Component.translatable(TarboundConfig.CLIENT.syncViewerSearch.get()
                ? "gui.tarbound.search_sync.on" : "gui.tarbound.search_sync.off"));
    }
    private final List<ContentsPayload.Entry> view = new ArrayList<>();
    private int seenVersion = -1;
    private int scrollRow;
    private boolean draggingThumb;

    // Machine window (client side)
    @Nullable
    private BlockPos selectedPos;
    private int seenTrayVersion = -1;
    /** Machine window scroll, in pixels. */
    private int machineScroll;

    public ScryingFontScreen(ScryingFontMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = IMAGE_WIDTH;
        this.imageHeight = IMAGE_HEIGHT;
    }

    @Override
    protected void init() {
        super.init();

        String previous = searchBox != null ? searchBox.getValue() : "";
        if (searchBox == null && syncOn()) {
            String viewer = com.tarbound.compat.SearchSync.get();
            if (viewer != null) {
                previous = viewer; // opening the radio picks up what you were searching in JEI/EMI
            }
        }
        searchBox = new EditBox(font, leftPos + SEARCH_X + 3, topPos + 5, SEARCH_W - 5, 10, Component.translatable("gui.tarbound.search"));
        searchBox.setBordered(false);
        searchBox.setMaxLength(256);
        searchBox.setTextColor(0xFFE8DCC4);
        searchBox.setHint(Component.translatable("gui.tarbound.search").withStyle(ChatFormatting.DARK_GRAY));
        searchBox.setValue(previous);
        lastViewerSearch = previous;
        searchBox.setResponder(s -> {
            scrollRow = 0;
            if (syncOn()) {
                // Viewer first, so JEI's filter already holds this text when the view asks it what matches.
                lastViewerSearch = s;
                com.tarbound.compat.SearchSync.set(s);
            }
            rebuildView();
        });
        addRenderableWidget(searchBox);
        searchBox.active = false; // only while focused (see render())

        // ⇄: keep this search and the recipe viewer's in step. Only shown when JEI or EMI is loaded.
        syncButton = addRenderableWidget(Button.builder(syncLabel(), b -> {
                    boolean on = !syncOn();
                    TarboundConfig.CLIENT.syncViewerSearch.set(on);
                    TarboundConfig.CLIENT.syncViewerSearch.save();
                    if (on) {
                        // Ours wins if it has text; otherwise take the viewer's.
                        String viewer = com.tarbound.compat.SearchSync.get();
                        if (searchBox.getValue().isEmpty() && viewer != null && !viewer.isEmpty()) {
                            searchBox.setValue(viewer);
                        } else {
                            com.tarbound.compat.SearchSync.set(searchBox.getValue());
                        }
                        lastViewerSearch = searchBox.getValue();
                    }
                    b.setMessage(syncLabel());
                    b.setTooltip(syncTooltip());
                })
                .bounds(leftPos + 77, topPos + 3, 12, 12)
                .tooltip(syncTooltip())
                .build());
        syncButton.visible = com.tarbound.compat.SearchSync.available();

        // ◆: things a contract can make go to the top of the pool, whatever the sort.
        addRenderableWidget(Button.builder(craftFirstLabel(), b -> {
                    TarboundConfig.CLIENT.craftablesFirst.set(!TarboundConfig.CLIENT.craftablesFirst.get());
                    TarboundConfig.CLIENT.craftablesFirst.save();
                    b.setMessage(craftFirstLabel());
                    b.setTooltip(craftFirstTooltip());
                    rebuildView();
                })
                .bounds(leftPos + 90, topPos + 3, 12, 12)
                .tooltip(craftFirstTooltip())
                .build());

        addRenderableWidget(Button.builder(Component.literal(sort.glyph), b -> {
                    sort = sort.next();
                    b.setMessage(Component.literal(sort.glyph));
                    b.setTooltip(Tooltip.create(Component.translatable(sort.key)));
                    rebuildView();
                })
                .bounds(leftPos + SCROLL_X, topPos + 3, 12, 12)
                .tooltip(Tooltip.create(Component.translatable(sort.key)))
                .build());

        // Pool view: items, fluids or both.
        addRenderableWidget(Button.builder(poolViewLabel(), b -> {
                    TarboundConfig.CLIENT.poolView.set(TarboundConfig.CLIENT.poolView.get().next());
                    TarboundConfig.CLIENT.poolView.save();
                    b.setMessage(poolViewLabel());
                    b.setTooltip(poolViewTooltip());
                    scrollRow = 0;
                    rebuildView();
                })
                .bounds(leftPos + 8, topPos + CRAFT_Y + 13, 12, 12)
                .tooltip(poolViewTooltip())
                .build());

        addRenderableWidget(Button.builder(Component.literal("×"), b -> send(FontActionPayload.Action.CLEAR_GRID, ItemStack.EMPTY))
                .bounds(leftPos + 8, topPos + CRAFT_Y - 1, 12, 12)
                .tooltip(Tooltip.create(Component.translatable("gui.tarbound.clear_grid")))
                .build());

        rebuildView();
        syncMachines();
    }

    private int tabX() {
        return leftPos + IMAGE_WIDTH - 3;
    }

    private int popX() {
        return tabX() + TAB_W - 1;
    }

    // ==========================================================================================
    // Pool view model
    // ==========================================================================================

    /**
     * Lowercased display names, by prototype stack. The prototypes live as long as their pool entry
     * (deltas keep them), so a name is built once instead of on every comparison of every sort.
     */
    private final java.util.IdentityHashMap<Object, String> lowerNames = new java.util.IdentityHashMap<>();

    private String lowerName(ItemStack proto) {
        return lowerNames.computeIfAbsent(proto, p -> proto.getHoverName().getString().toLowerCase(Locale.ROOT));
    }

    private String lowerName(ContentsPayload.Entry e) {
        return e.isFluid()
                ? lowerNames.computeIfAbsent(e.fluid(), f -> e.fluid().getHoverName().getString().toLowerCase(Locale.ROOT))
                : lowerName(e.proto());
    }

    private static String namespace(ContentsPayload.Entry e) {
        return e.isFluid() ? BuiltInRegistries.FLUID.getKey(e.fluid().getFluid()).getNamespace() : namespace(e.proto());
    }

    /** Identity of an entry across sends: a FluidKey or an ItemKey. */
    private static Object key(ContentsPayload.Entry e) {
        return e.isFluid() ? com.tarbound.network.FluidKey.of(e.fluid()) : ItemKey.of(e.proto());
    }

    /** The pool entries the current view mode shows (fluids first). */
    private List<ContentsPayload.Entry> shownEntries() {
        List<ContentsPayload.Entry> out = new ArrayList<>();
        if (poolView().fluids()) {
            out.addAll(menu.clientFluidEntries());
        }
        if (poolView().items()) {
            out.addAll(menu.clientEntries());
        }
        return out;
    }

    /**
     * The view is holding its order while shift is down (see {@link #refreshFrozen}), so shift-clicking
     * the same slot again and again pulls the same item even when sorted by count.
     */
    private boolean frozen;

    /**
     * New pool contents while shift is held: update each shown entry's count where it stands, without
     * re-sorting. Items that run out stay in place at 0 and items that newly arrive wait, until shift is
     * released and the view is sorted again.
     */
    private void refreshFrozen() {
        seenVersion = menu.clientVersion();
        frozen = true;
        Map<Object, Long> now = new java.util.HashMap<>();
        for (ContentsPayload.Entry e : shownEntries()) {
            now.put(key(e), e.count());
        }
        for (int i = 0; i < view.size(); i++) {
            ContentsPayload.Entry e = view.get(i);
            long count = now.getOrDefault(key(e), 0L);
            if (count != e.count()) {
                view.set(i, e.withCount(count));
            }
        }
    }

    private void rebuildView() {
        frozen = false;
        seenVersion = menu.clientVersion();
        if (lowerNames.size() > 2 * (menu.clientEntries().size() + menu.clientFluidEntries().size() + craftables.size()) + 256) {
            lowerNames.clear(); // drop names of item types long gone
            searchInfo.clear();
        }
        String raw = searchBox == null ? "" : searchBox.getValue().trim();
        view.clear();
        prepareSearch(raw);
        for (ContentsPayload.Entry e : shownEntries()) {
            if (searchMatches(e.proto(), e.fluid(), e)) {
                view.add(e);
            }
        }

        // Things a contract can make but the pool doesn't hold yet: shown as ghosts (count 0).
        java.util.Set<ItemKey> present = new java.util.HashSet<>();
        for (ContentsPayload.Entry e : menu.clientEntries()) {
            present.add(ItemKey.of(e.proto()));
        }
        java.util.Set<com.tarbound.network.FluidKey> presentFluids = new java.util.HashSet<>();
        for (ContentsPayload.Entry e : menu.clientFluidEntries()) {
            presentFluids.add(com.tarbound.network.FluidKey.of(e.fluid()));
        }
        for (Map.Entry<Object, Craftable> c : craftables.entrySet()) {
            if (c.getKey() instanceof com.tarbound.network.FluidKey fk) {
                // a fluid only a contract makes: a ghost tank in the fluid part of the list
                if (poolView().fluids() && !presentFluids.contains(fk)
                        && searchMatches(ItemStack.EMPTY, c.getValue().fluid(), null)) {
                    view.add(ContentsPayload.Entry.ofFluid(c.getValue().fluid().copyWithAmount(1), 0));
                }
                continue;
            }
            if (!poolView().items() || present.contains(c.getKey())) {
                continue;
            }
            ItemStack proto = ((ItemKey) c.getKey()).proto();
            if (searchMatches(proto, net.neoforged.neoforge.fluids.FluidStack.EMPTY, null)) {
                view.add(new ContentsPayload.Entry(proto, 0));
            }
        }

        Comparator<ContentsPayload.Entry> byName = Comparator.comparing(this::lowerName);
        Comparator<ContentsPayload.Entry> order = switch (sort) {
            case COUNT -> Comparator.comparingLong(ContentsPayload.Entry::count).reversed().thenComparing(byName);
            case NAME -> byName;
            case MOD -> Comparator.<ContentsPayload.Entry, String>comparing(ScryingFontScreen::namespace).thenComparing(byName);
        };
        if (TarboundConfig.CLIENT.craftablesFirst.get() && !craftables.isEmpty()) {
            // Craftables (and ghosts) first, each group in the chosen order. Looked up once per entry.
            java.util.Set<ContentsPayload.Entry> craftable = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
            for (ContentsPayload.Entry e : view) {
                if (craftables.containsKey(key(e))) {
                    craftable.add(e);
                }
            }
            order = Comparator.<ContentsPayload.Entry>comparingInt(e -> craftable.contains(e) ? 0 : 1).thenComparing(order);
        }
        // Fluids ahead of items (there are usually few, and mB would swamp a count sort).
        order = Comparator.<ContentsPayload.Entry>comparingInt(e -> e.isFluid() ? 0 : 1).thenComparing(order);
        view.sort(order);
        scrollRow = Mth.clamp(scrollRow, 0, maxScroll());
    }

    // --- search: JEI decides when it's loaded and holds the same text (so it works exactly like JEI's box);
    //     otherwise our own copy of the prefixes (EMI's meanings for # and $ when only EMI is installed).

    private PoolSearch search = PoolSearch.parse("", false);
    @javax.annotation.Nullable
    private com.tarbound.compat.SearchSync.Verdict jeiVerdict;
    private String verdictText = "";
    private boolean verdictFresh;
    /** Search details per pool entry (tooltips, tags, colors), built as searches need them. */
    private final java.util.IdentityHashMap<Object, PoolSearch.Info> searchInfo = new java.util.IdentityHashMap<>();

    private void prepareSearch(String raw) {
        boolean emiStyle = com.tarbound.compat.SearchSync.emi != null && com.tarbound.compat.SearchSync.jei == null;
        search = PoolSearch.parse(raw, emiStyle);
        if (!raw.equals(verdictText) || !verdictFresh) {
            verdictText = raw;
            jeiVerdict = raw.isEmpty() ? null : com.tarbound.compat.SearchSync.jeiFor(raw);
            verdictFresh = jeiVerdict != null || com.tarbound.compat.SearchSync.jei == null;
        }
        if (searchInfo.size() > lowerNames.size() + 512) {
            searchInfo.clear();
        }
    }

    private boolean searchMatches(ItemStack proto, net.neoforged.neoforge.fluids.FluidStack fluid, @javax.annotation.Nullable ContentsPayload.Entry e) {
        if (search.isEmpty()) {
            return true;
        }
        boolean isFluid = !fluid.isEmpty();
        if (jeiVerdict != null) {
            Boolean v = isFluid ? jeiVerdict.fluid(fluid) : jeiVerdict.item(proto);
            if (v != null) {
                return v;
            }
        }
        Object key = isFluid ? fluid : proto;
        PoolSearch.Info info = searchInfo.computeIfAbsent(key,
                k -> new PoolSearch.Info(proto, fluid, e != null ? lowerName(e) : lowerName(proto)));
        return search.matches(info);
    }

    private static String namespace(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace();
    }

    private int maxScroll() {
        return Math.max(0, (view.size() + GRID_COLS - 1) / GRID_COLS - GRID_ROWS);
    }

    /** Index into {@link #view} under the mouse, or -1. */
    private int hoveredIndex(double mouseX, double mouseY) {
        int rx = (int) Math.floor(mouseX) - leftPos - GRID_X;
        int ry = (int) Math.floor(mouseY) - topPos - GRID_Y;
        if (rx < 0 || ry < 0 || rx >= GRID_COLS * 18 || ry >= GRID_ROWS * 18) {
            return -1;
        }
        int index = (scrollRow + ry / 18) * GRID_COLS + rx / 18;
        return index < view.size() ? index : -1;
    }

    private boolean overGrid(double mouseX, double mouseY) {
        return in(mouseX, mouseY, leftPos + GRID_X, topPos + GRID_Y, GRID_COLS * 18, GRID_ROWS * 18);
    }

    private boolean overScrollbar(double mouseX, double mouseY) {
        return in(mouseX, mouseY, leftPos + SCROLL_X, topPos + SCROLL_Y, SCROLL_W, SCROLL_H);
    }

    private static boolean in(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    // ==========================================================================================
    // Machine window model
    // ==========================================================================================

    private List<TrayPayload.Machine> machines() {
        TrayPayload tray = menu.clientTray();
        return tray == null ? List.of() : tray.machines();
    }

    @Nullable
    private TrayPayload.Machine selected() {
        if (selectedPos == null) {
            return null;
        }
        for (TrayPayload.Machine m : machines()) {
            if (m.pos().equals(selectedPos)) {
                return m;
            }
        }
        return null;
    }

    /** Keep the selection valid as machines come and go: same machine if still bound, else the first. */
    private void syncMachines() {
        seenTrayVersion = menu.clientTrayVersion();
        craftables.clear();
        for (TrayPayload.Machine m : machines()) {
            for (int i = 0; i < m.contracts().size(); i++) {
                ContractData c = m.contracts().get(i);
                if (c != null && c.isValid() && c.orderable()) {
                    Craftable cr = new Craftable(c.tracksFluid() ? ItemStack.EMPTY : c.primaryOutput(), c.trackedFluid(), m.pos(), i, c,
                            m.displayName());
                    craftables.putIfAbsent(cr.key(), cr);
                }
            }
        }
        if (requesting != null && !craftables.containsKey(requesting.key())) {
            requesting = null;
        }
        rebuildView();
        jobScroll = Mth.clamp(jobScroll, 0, jobMaxScroll());
        standingScroll = Mth.clamp(standingScroll, 0, standingMaxScroll());
        List<TrayPayload.Machine> list = machines();
        if (selected() == null) {
            selectedPos = list.isEmpty() ? null : list.get(0).pos();
        }
        machineScroll = Mth.clamp(machineScroll, 0, machineMaxScroll());
    }

    /** One machine's place in the scrolling list: content-relative top and total height. */
    /** One machine in the window: header, {@code rows} of slots, then {@code tankRows} of tanks. */
    private record MachineBlock(int index, int top, int height, int rows, int tankRows) {}

    private List<MachineBlock> machineLayout() {
        List<MachineBlock> out = new ArrayList<>();
        int top = 0;
        List<TrayPayload.Machine> list = machines();
        for (int i = 0; i < list.size(); i++) {
            int rows = (list.get(i).slots().size() + SLOT_COLS - 1) / SLOT_COLS; // 0 for a crafting station
            int tankRows = (list.get(i).tanks().size() + SLOT_COLS - 1) / SLOT_COLS;
            int h = HDR_H + (rows + tankRows) * 18;
            out.add(new MachineBlock(i, top, h, rows, tankRows));
            top += h + BLOCK_GAP;
        }
        return out;
    }

    private int machineContentHeight() {
        List<MachineBlock> layout = machineLayout();
        if (layout.isEmpty()) {
            return 0;
        }
        MachineBlock last = layout.get(layout.size() - 1);
        return last.top() + last.height();
    }

    private int machineMaxScroll() {
        return Math.max(0, machineContentHeight() - VIEW_H);
    }

    /** Mouse inside the machine list viewport. */
    private boolean inMachineView(double mx, double my) {
        return pane == Pane.MACHINES && in(mx, my, popX() + 6, topPos + VIEW_Y, POP_SCROLL_X - 7, VIEW_H);
    }

    /** Content-relative y of the mouse in the machine viewport. */
    private int contentY(double my) {
        return (int) Math.floor(my) - topPos - VIEW_Y + machineScroll;
    }

        private boolean overTab(double mx, double my) {
        return in(mx, my, tabX(), topPos + TAB_Y, TAB_W, TAB_H);
    }

    private boolean inPopout(double mx, double my) {
        return pane != Pane.NONE && in(mx, my, popX(), topPos, POP_W, POP_H);
    }

    /** Machine whose header is under the mouse (index into machines()), or -1. */
    private int hoveredRow(double mx, double my) {
        if (!inMachineView(mx, my)) {
            return -1;
        }
        int cy = contentY(my);
        for (MachineBlock b : machineLayout()) {
            if (cy >= b.top() && cy < b.top() + HDR_H) {
                return b.index();
            }
        }
        return -1;
    }

    /** A machine slot under the mouse. */
    private record SlotHit(TrayPayload.Machine machine, int slot, int screenX, int screenY) {}

    @Nullable
    private SlotHit hoveredMachineSlot(double mx, double my) {
        if (!inMachineView(mx, my)) {
            return null;
        }
        int cy = contentY(my);
        int rx = (int) Math.floor(mx) - popX() - SLOTS_X;
        if (rx < 0 || rx >= SLOT_COLS * 18) {
            return null;
        }
        for (MachineBlock b : machineLayout()) {
            int sy = cy - b.top() - HDR_H;
            if (sy >= 0 && sy < b.rows() * 18) {
                TrayPayload.Machine m = machines().get(b.index());
                int index = (sy / 18) * SLOT_COLS + rx / 18;
                if (index >= m.slots().size()) {
                    return null;
                }
                int screenX = popX() + SLOTS_X + (rx / 18) * 18;
                int screenY = topPos + VIEW_Y - machineScroll + b.top() + HDR_H + (sy / 18) * 18;
                return new SlotHit(m, index, screenX, screenY);
            }
        }
        return null;
    }

    private record TankHit(TrayPayload.Machine machine, int tank, int screenX, int screenY) {}

    /** The machine tank under the mouse in the machine window, or null. */
    @Nullable
    private TankHit hoveredMachineTank(double mx, double my) {
        if (pane != Pane.MACHINES || !inMachineView(mx, my)) {
            return null;
        }
        int cy = contentY(my);
        int rx = (int) Math.floor(mx) - popX() - SLOTS_X;
        if (rx < 0 || rx >= SLOT_COLS * 18) {
            return null;
        }
        for (MachineBlock b : machineLayout()) {
            int ty = cy - b.top() - HDR_H - b.rows() * 18;
            if (ty >= 0 && ty < b.tankRows() * 18) {
                TrayPayload.Machine m = machines().get(b.index());
                int index = (ty / 18) * SLOT_COLS + rx / 18;
                if (index >= m.tanks().size()) {
                    return null;
                }
                int screenX = popX() + SLOTS_X + (rx / 18) * 18;
                int screenY = topPos + VIEW_Y - machineScroll + b.top() + HDR_H + b.rows() * 18 + (ty / 18) * 18;
                return new TankHit(m, index, screenX, screenY);
            }
        }
        return null;
    }

    // ==========================================================================================
    // Recipe-viewer hooks
    // ==========================================================================================

    /** Screen area of the machine tab (and the pop-out when open), so JEI/EMI keep their overlays off it. */
    public Rect2i trayArea() {
        return pane != Pane.NONE
                ? new Rect2i(tabX(), topPos, POP_W + TAB_W, POP_H)
                : new Rect2i(tabX(), topPos + TAB_Y, TAB_W, TAB_H * 2 + 2 + STOP_H + 2);
    }

    /** A hovered pool entry or slot: an item, or a fluid (then {@code stack} is empty). */
    public record HoveredStack(ItemStack stack, net.neoforged.neoforge.fluids.FluidStack fluid, Rect2i area) {
        public HoveredStack(ItemStack stack, Rect2i area) {
            this(stack, net.neoforged.neoforge.fluids.FluidStack.EMPTY, area);
        }
    }

    /** The pool entry or machine slot item under the mouse, for recipe/usage lookups. */
    @Nullable
    public HoveredStack stackUnderMouse(double mx, double my) {
        int idx = hoveredIndex(mx, my);
        if (idx >= 0) {
            int i = idx - scrollRow * GRID_COLS;
            int x = leftPos + GRID_X + 1 + (i % GRID_COLS) * 18;
            int y = topPos + GRID_Y + 1 + (i / GRID_COLS) * 18;
            ContentsPayload.Entry e = view.get(idx);
            return new HoveredStack(e.proto(), e.fluid().copyWithAmount(1000), new Rect2i(x, y, 16, 16));
        }
        TankHit tank = hoveredMachineTank(mx, my);
        if (tank != null && !tank.machine().tanks().get(tank.tank()).fluid().isEmpty()) {
            return new HoveredStack(ItemStack.EMPTY, tank.machine().tanks().get(tank.tank()).fluid().copyWithAmount(1000),
                    new Rect2i(tank.screenX() + 1, tank.screenY() + 1, 16, 16));
        }
        SlotHit hit = hoveredMachineSlot(mx, my);
        if (hit != null && !hit.machine().slots().get(hit.slot()).isEmpty()) {
            return new HoveredStack(hit.machine().slots().get(hit.slot()), new Rect2i(hit.screenX() + 1, hit.screenY() + 1, 16, 16));
        }
        return null;
    }

    // ==========================================================================================
    // Rendering
    // ==========================================================================================

    @Override
    protected void containerTick() {
        super.containerTick();
        if (syncButton != null) {
            syncButton.visible = com.tarbound.compat.SearchSync.available(); // JEI's runtime can arrive late
        }
        if (syncOn()) {
            // Typed in the viewer's box: mirror it here.
            String viewer = com.tarbound.compat.SearchSync.get();
            if (viewer != null && !viewer.equals(lastViewerSearch)) {
                lastViewerSearch = viewer;
                if (!viewer.equals(searchBox.getValue())) {
                    searchBox.setValue(viewer);
                }
            }
        }
        if (menu.clientVersion() != seenVersion) {
            if (hasShiftDown() && !view.isEmpty()) {
                refreshFrozen(); // shift held: counts update, but nothing moves
            } else {
                rebuildView();
            }
        } else if (frozen && !hasShiftDown()) {
            rebuildView(); // shift let go: sort again
        }
        if (menu.clientTrayVersion() != seenTrayVersion) {
            syncMachines();
        }
        menu.setClientSelection(selectedPos);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // EMI turns its hotkeys (R, U, A...) off on any screen with an "active" text box, focused or not,
        // so the search box only counts as active while you're typing in it. Clicks on it are caught in
        // mouseClicked by position instead.
        searchBox.active = searchBox.isFocused();
        super.render(g, mouseX, mouseY, partialTick);
        if (!menu.clientCanCraft()) {
            renderCraftingLocked(g);
        }
        if (renderRecipeChooser(g, mouseX, mouseY)) {
            return; // its own tooltip was drawn
        }
        if (requesting != null) {
            renderRequest(g, mouseX, mouseY);
            return;
        }
        if (overCancelAll(mouseX, mouseY)) {
            g.renderComponentTooltip(font, List.of(
                    Component.translatable(cancelAllArmed() ? "gui.tarbound.jobs.cancel_all_confirm" : "gui.tarbound.jobs.cancel_all", jobs().size())
                            .withStyle(ChatFormatting.RED),
                    Component.translatable("gui.tarbound.jobs.cancel_all_note").withStyle(ChatFormatting.DARK_GRAY)), mouseX, mouseY);
            return;
        }
        int standRow = hoveredStandingRow(mouseX, mouseY);
        if (standRow >= 0) {
            StandingRow r = standingRows().get(standRow);
            List<Component> lines = new ArrayList<>();
            lines.add(StandingOrder.tagName(r.view().tag()).copy().withStyle(ChatFormatting.AQUA));
            lines.add(Component.literal("#" + r.view().tag()).withStyle(ChatFormatting.DARK_GRAY));
            lines.add(Component.translatable("gui.tarbound.standing.per_op", r.view().count()).withStyle(ChatFormatting.GRAY));
            lines.add(Component.translatable("gui.tarbound.craft.via", r.machine().displayName()).withStyle(ChatFormatting.GRAY));
            lines.add(Component.translatable("gui.tarbound.machine.pos", r.machine().pos().getX(), r.machine().pos().getY(), r.machine().pos().getZ())
                    .withStyle(ChatFormatting.DARK_GRAY));
            lines.add(Component.translatable(r.view().paused() ? "gui.tarbound.standing.paused" : "gui.tarbound.standing.running")
                    .withStyle(r.view().paused() ? ChatFormatting.GOLD : ChatFormatting.GREEN));
            lines.add(Component.translatable(r.view().paused() ? "gui.tarbound.standing.resume_hint" : "gui.tarbound.standing.pause_hint")
                    .withStyle(ChatFormatting.DARK_GRAY));
            g.renderComponentTooltip(font, lines, mouseX, mouseY);
            return;
        }
        if (overJobsTab(mouseX, mouseY)) {
            g.renderComponentTooltip(font, List.of(
                    Component.translatable("gui.tarbound.jobs.tab", jobs().size()),
                    Component.translatable(pane == Pane.JOBS ? "gui.tarbound.machines.close" : "gui.tarbound.jobs.open")
                            .withStyle(ChatFormatting.DARK_GRAY)), mouseX, mouseY);
            return;
        }
        int jobRow = hoveredJobRow(mouseX, mouseY);
        if (jobRow >= 0 && jobRows().get(jobRow).header()) {
            JobRow row = jobRows().get(jobRow);
            TrayPayload.JobView j = row.shown();
            List<Component> lines = new ArrayList<>();
            lines.add(Component.translatable("gui.tarbound.order.header", orderName(j)).withStyle(ChatFormatting.AQUA));
            lines.add(Component.translatable("gui.tarbound.order.steps_left", row.steps()).withStyle(ChatFormatting.GRAY));
            lines.add(statusLabel(j.status()));
            lines.add(Component.translatable(openOrders.contains(row.order()) ? "gui.tarbound.order.collapse" : "gui.tarbound.order.expand")
                    .withStyle(ChatFormatting.DARK_GRAY));
            if (hoveredJobCancel(mouseX, mouseY) >= 0) {
                lines.add(Component.translatable("gui.tarbound.order.cancel").withStyle(ChatFormatting.RED));
            }
            g.renderComponentTooltip(font, lines, mouseX, mouseY);
            return;
        }
        if (jobRow >= 0) {
            TrayPayload.JobView j = jobRows().get(jobRow).job();
            List<Component> lines = new ArrayList<>();
            if (j.isFluid()) {
                lines.add(j.fluid().getHoverName());
                lines.add(Component.translatable("gui.tarbound.job.progress_fluid", String.format(Locale.ROOT, "%,d", j.received()),
                        String.format(Locale.ROOT, "%,d", j.expected()), j.pushedOps(), j.totalOps()).withStyle(ChatFormatting.GRAY));
            } else {
                lines.addAll(getTooltipFromContainerItem(j.output()));
                lines.add(Component.translatable("gui.tarbound.job.progress", j.received(), j.expected(), j.pushedOps(), j.totalOps())
                        .withStyle(ChatFormatting.GRAY));
            }
            lines.add(statusLabel(j.status()));
            lines.addAll(waitLines(j));
            if (j.order() != 0) {
                lines.add(Component.translatable(j.root() ? "gui.tarbound.order.step_last" : "gui.tarbound.order.step_sub", orderName(j))
                        .withStyle(ChatFormatting.DARK_AQUA));
            }
            if (hoveredJobCancel(mouseX, mouseY) >= 0) {
                lines.add(Component.translatable(j.order() != 0 ? "gui.tarbound.order.cancel" : "gui.tarbound.job.cancel").withStyle(ChatFormatting.RED));
            }
            g.renderComponentTooltip(font, lines, mouseX, mouseY);
            return;
        }
        if (!menu.getCarried().isEmpty()) {
            renderTooltip(g, mouseX, mouseY);
            return;
        }

        int idx = hoveredIndex(mouseX, mouseY);
        if (idx >= 0) {
            ContentsPayload.Entry e = view.get(idx);
            if (e.isFluid()) {
                List<Component> lines = fluidTooltip(e);
                Craftable c = craftables.get(key(e));
                if (c != null) {
                    lines.add(Component.translatable(e.count() == 0 ? "gui.tarbound.craft.hint_ghost" : "gui.tarbound.craft.hint", c.machineName())
                            .withStyle(ChatFormatting.AQUA));
                }
                g.renderComponentTooltip(font, lines, mouseX, mouseY);
                return;
            }
            List<Component> lines = new ArrayList<>(getTooltipFromContainerItem(e.proto()));
            lines.add(Component.translatable("gui.tarbound.stored", String.format(Locale.ROOT, "%,d", e.count()))
                    .withStyle(ChatFormatting.GOLD));
            Craftable c = craftables.get(ItemKey.of(e.proto()));
            if (c != null) {
                lines.add(Component.translatable(e.count() == 0 ? "gui.tarbound.craft.hint_ghost" : "gui.tarbound.craft.hint", c.machineName())
                        .withStyle(ChatFormatting.AQUA));
            }
            g.renderTooltip(font, lines, e.proto().getTooltipImage(), e.proto(), mouseX, mouseY);
            return;
        }

        TrayPayload tray = menu.clientTray();
        if (overTab(mouseX, mouseY)) {
            List<Component> lines = new ArrayList<>();
            lines.add(Component.translatable("gui.tarbound.machines.tab", machines().size()));
            lines.add(Component.translatable(pane == Pane.MACHINES ? "gui.tarbound.machines.close" : "gui.tarbound.machines.open")
                    .withStyle(ChatFormatting.DARK_GRAY));
            g.renderComponentTooltip(font, lines, mouseX, mouseY);
            return;
        }

        TankHit tankHit = hoveredMachineTank(mouseX, mouseY);
        if (tankHit != null) {
            TrayPayload.Tank t = tankHit.machine().tanks().get(tankHit.tank());
            List<Component> lines = new ArrayList<>();
            lines.add(t.fluid().isEmpty() ? Component.translatable("gui.tarbound.tank.empty") : t.fluid().getHoverName());
            lines.add(Component.translatable("gui.tarbound.tank.amount", String.format(Locale.ROOT, "%,d", t.fluid().getAmount()),
                    String.format(Locale.ROOT, "%,d", t.capacity())).withStyle(ChatFormatting.GOLD));
            lines.add(Component.translatable("gui.tarbound.tank.hint").withStyle(ChatFormatting.DARK_GRAY));
            g.renderComponentTooltip(font, lines, mouseX, mouseY);
            return;
        }
        SlotHit hit = hoveredMachineSlot(mouseX, mouseY);
        if (hit != null) {
            ItemStack stack = hit.machine().slots().get(hit.slot());
            List<Component> lines = new ArrayList<>(stack.isEmpty() ? List.of() : getTooltipFromContainerItem(stack));
            lines.add(roleLabel(hit.machine().roles()[hit.slot()]));
            g.renderTooltip(font, lines, stack.getTooltipImage(), stack, mouseX, mouseY);
            return;
        }

        int row = hoveredRow(mouseX, mouseY);
        if (row >= 0) {
            TrayPayload.Machine m = machines().get(row);
            List<Component> lines = new ArrayList<>();
            lines.add(m.displayName());
            if (!m.name().isEmpty()) {
                lines.add(m.state().getBlock().getName().copy().withStyle(ChatFormatting.GRAY));
            }
            for (TrayPayload.StandingView v : m.standing()) {
                lines.add(Component.translatable(v.paused() ? "gui.tarbound.machine.standing_paused" : "gui.tarbound.machine.standing", StandingOrder.tagName(v.tag()))
                        .withStyle(v.paused() ? ChatFormatting.DARK_GRAY : ChatFormatting.DARK_AQUA));
            }
            if (m.spiritFed()) {
                lines.add(Component.translatable("gui.tarbound.spirit_fed").withStyle(ChatFormatting.AQUA));
            }
            lines.add(Component.translatable("gui.tarbound.machine.pos", m.pos().getX(), m.pos().getY(), m.pos().getZ())
                    .withStyle(ChatFormatting.DARK_GRAY));
            lines.add(Component.translatable(m.pos().equals(MachineHighlight.target())
                    ? "gui.tarbound.machine.highlighted" : "gui.tarbound.machine.highlight").withStyle(ChatFormatting.DARK_AQUA));
            g.renderComponentTooltip(font, lines, mouseX, mouseY);
            return;
        }

        if (pane == Pane.MACHINES && tray != null && tray.unbound() > 0
                && in(mouseX, mouseY, popX() + 6, topPos + 4, POP_W - 12, 10)) {
            List<Component> lines = new ArrayList<>();
            {
                lines.add(Component.translatable("gui.tarbound.unbound", tray.unbound()).withStyle(ChatFormatting.GOLD));
            }
            g.renderComponentTooltip(font, lines, mouseX, mouseY);
            return;
        }

        if (overSpiritMeter(mouseX, mouseY)) {
            g.renderComponentTooltip(font, List.of(
                    Component.translatable("gui.tarbound.spirit.meter", menu.clientSpirit(), menu.clientSpiritCapacity())
                            .withStyle(menu.clientSpirit() > 0 ? ChatFormatting.GOLD : ChatFormatting.LIGHT_PURPLE),
                    Component.translatable(menu.clientSpirit() > 0 ? "gui.tarbound.spirit.what" : "gui.tarbound.spirit.hungry")
                            .withStyle(ChatFormatting.GRAY),
                    Component.translatable("gui.tarbound.spirit.feed").withStyle(ChatFormatting.DARK_GRAY)), mouseX, mouseY);
            return;
        }
        if (overCandle(mouseX, mouseY)) {
            g.renderComponentTooltip(font, List.of(
                    Component.translatable(menu.clientBurnFromPool() ? "gui.tarbound.spirit.pool_on" : "gui.tarbound.spirit.pool_off")
                            .withStyle(menu.clientBurnFromPool() ? ChatFormatting.GOLD : ChatFormatting.GRAY),
                    Component.translatable("gui.tarbound.spirit.pool_hint").withStyle(ChatFormatting.DARK_GRAY)), mouseX, mouseY);
            return;
        }
        if (!menu.clientCanCraft() && (in(mouseX, mouseY, leftPos + CRAFT_X - 1, topPos + CRAFT_Y - 1, 54, 54)
                || in(mouseX, mouseY, leftPos + RESULT_X - 5, topPos + RESULT_Y - 5, 26, 26))) {
            g.renderTooltip(font, Component.translatable("gui.tarbound.no_crafting_table"), mouseX, mouseY);
            return;
        }

        renderTooltip(g, mouseX, mouseY);
    }

    /** The Spirit meter (a flame filling from the bottom) and the candle button. Hidden with costs off. */
    private void renderSpirit(GuiGraphics g, int x, int y) {
        if (!menu.clientSpiritCosts()) {
            return;
        }
        int mx = x + METER_X, my = y + METER_Y;
        inset(g, mx - 1, my - 1, METER_W + 2, METER_H + 2);
        float frac = Math.min(1F, menu.clientSpirit() / (float) menu.clientSpiritCapacity());
        int filled = Math.round(frac * METER_H);
        for (int i = 0; i < filled; i++) {
            // deep orange at the bottom to pale yellow at the top
            float t = i / (float) Math.max(1, METER_H - 1);
            int r = 0xFF, gg = (int) (0x70 + t * 0x7F), b = (int) (0x20 + t * 0x50);
            g.fill(mx, my + METER_H - 1 - i, mx + METER_W, my + METER_H - i, 0xFF000000 | r << 16 | gg << 8 | b);
        }
        if (menu.clientSpirit() <= 0) {
            g.drawString(font, "!", mx + 2, my + METER_H / 2 - 4, C_WARN, false);
        }
        int cx = x + CANDLE_X, cy = y + CANDLE_Y;
        inset(g, cx - 1, cy - 1, 18, 18);
        g.renderItem(new ItemStack(Items.CANDLE), cx, cy);
        if (!menu.clientBurnFromPool()) {
            g.pose().pushPose();
            g.pose().translate(0, 0, 210);
            g.fill(cx, cy, cx + 16, cy + 16, 0xA0171311); // snuffed
            g.pose().popPose();
        }
    }

    private boolean overSpiritMeter(double mx, double my) {
        return menu.clientSpiritCosts() && in(mx, my, leftPos + METER_X - 1, topPos + METER_Y - 1, METER_W + 2, METER_H + 2);
    }

    private boolean overCandle(double mx, double my) {
        return menu.clientSpiritCosts() && in(mx, my, leftPos + CANDLE_X - 1, topPos + CANDLE_Y - 1, 18, 18);
    }

    private static Component roleLabel(byte role) {
        BoundInventory.Role r = BoundInventory.Role.values()[Mth.clamp(role, 0, 2)];
        return switch (r) {
            case INPUT -> Component.translatable("gui.tarbound.role.input").withStyle(ChatFormatting.GRAY);
            case FUEL -> Component.translatable("gui.tarbound.role.fuel").withStyle(ChatFormatting.GOLD);
            case OUTPUT -> Component.translatable("gui.tarbound.role.output").withStyle(ChatFormatting.GREEN);
        };
    }

    /** Grey out the grid and show a ghost crafting table where the result would be. */
    private void renderCraftingLocked(GuiGraphics g) {
        g.pose().pushPose();
        g.pose().translate(0, 0, 260);
        g.fill(leftPos + CRAFT_X - 1, topPos + CRAFT_Y - 1, leftPos + CRAFT_X + 53, topPos + CRAFT_Y + 53, 0xB0100D0B);
        g.fill(leftPos + RESULT_X - 4, topPos + RESULT_Y - 4, leftPos + RESULT_X + 20, topPos + RESULT_Y + 20, 0xB0100D0B);
        g.pose().popPose();
        g.pose().pushPose();
        g.pose().translate(0, 0, 100);
        g.renderItem(new ItemStack(Items.CRAFTING_TABLE), leftPos + RESULT_X, topPos + RESULT_Y);
        g.pose().popPose();
        g.pose().pushPose();
        g.pose().translate(0, 0, 300);
        g.fill(leftPos + RESULT_X, topPos + RESULT_Y, leftPos + RESULT_X + 16, topPos + RESULT_Y + 16, 0x90100D0B);
        g.pose().popPose();
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        if (pane == Pane.MACHINES) {
            renderPopout(g, mouseX, mouseY);
        } else if (pane == Pane.JOBS) {
            renderJobs(g, mouseX, mouseY);
        }
        renderTab(g, mouseX, mouseY);
        renderJobsTab(g, mouseX, mouseY);
        renderCancelAll(g, mouseX, mouseY);
        renderMainPanel(g, mouseX, mouseY);
    }

    private void renderMainPanel(GuiGraphics g, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        panel(g, x, y, imageWidth, imageHeight);

        inset(g, x + SEARCH_X, y + 3, SEARCH_W, 13);
        for (int row = 0; row < GRID_ROWS; row++) {
            for (int col = 0; col < GRID_COLS; col++) {
                slot(g, x + GRID_X + col * 18, y + GRID_Y + row * 18);
            }
        }
        scrollbar(g, x + SCROLL_X, y + SCROLL_Y, SCROLL_W, SCROLL_H, scrollRow, maxScroll());

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                slot(g, x + CRAFT_X - 1 + col * 18, y + CRAFT_Y - 1 + row * 18);
            }
        }
        arrow(g, x + 90, y + RESULT_Y + 1);
        inset(g, x + RESULT_X - 5, y + RESULT_Y - 5, 26, 26);
        renderSpirit(g, x, y);

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                slot(g, x + INV_X - 1 + col * 18, y + INV_Y - 1 + row * 18);
            }
        }
        for (int col = 0; col < 9; col++) {
            slot(g, x + INV_X - 1 + col * 18, y + HOTBAR_Y - 1);
        }

        int first = scrollRow * GRID_COLS;
        int hovered = hoveredIndex(mouseX, mouseY);
        for (int i = 0; i < GRID_COLS * GRID_ROWS; i++) {
            int index = first + i;
            if (index >= view.size()) {
                break;
            }
            ContentsPayload.Entry e = view.get(index);
            int ix = x + GRID_X + 1 + (i % GRID_COLS) * 18;
            int iy = y + GRID_Y + 1 + (i / GRID_COLS) * 18;
            if (e.isFluid()) {
                renderFluid(g, e.fluid(), ix, iy, 16, 16);
                if (e.count() == 0) {
                    g.pose().pushPose();
                    g.pose().translate(0, 0, 210);
                    g.fill(ix, iy, ix + 16, iy + 16, 0x9A171311); // ghost: only a contract makes it
                    g.pose().popPose();
                } else {
                    drawCount(g, Math.max(2, e.count()), ix, iy, formatCount(e.count()));
                }
                if (craftables.containsKey(key(e))) {
                    g.pose().pushPose();
                    g.pose().translate(0, 0, 220);
                    g.fill(ix, iy, ix + 3, iy + 1, C_SPIRIT);
                    g.fill(ix, iy, ix + 1, iy + 3, C_SPIRIT);
                    g.pose().popPose();
                }
                if (index == hovered) {
                    renderSlotHighlight(g, ix, iy, 0);
                }
                continue;
            }
            boolean craftable = craftables.containsKey(ItemKey.of(e.proto()));
            g.renderItem(e.proto(), ix, iy);
            if (e.count() == 0) {
                // Ghost: nothing in the pool, but a contract can make it.
                g.pose().pushPose();
                g.pose().translate(0, 0, 210);
                g.fill(ix, iy, ix + 16, iy + 16, 0x9A171311);
                g.pose().popPose();
            } else {
                drawCount(g, e.count(), ix, iy);
            }
            if (craftable) {
                g.pose().pushPose();
                g.pose().translate(0, 0, 220);
                g.fill(ix, iy, ix + 3, iy + 1, C_SPIRIT);
                g.fill(ix, iy, ix + 1, iy + 3, C_SPIRIT);
                g.pose().popPose();
            }
            if (index == hovered) {
                renderSlotHighlight(g, ix, iy, 0);
            }
        }
    }

    /** The Reliquary tab hanging off the right edge: opens/closes the machine window. */
    private void renderTab(GuiGraphics g, int mouseX, int mouseY) {
        int x = tabX();
        int y = topPos + TAB_Y;
        panel(g, x, y, TAB_W, TAB_H);
        if (pane == Pane.MACHINES) {
            // Blend the tab into the pop-out so they read as one piece.
            g.fill(x + TAB_W - 3, y + 2, x + TAB_W, y + TAB_H - 2, C_PANEL);
        }
        if (overTab(mouseX, mouseY)) {
            g.fill(x + 3, y + 3, x + TAB_W - 3, y + TAB_H - 3, 0x20FFFFFF);
        }
        g.renderItem(new ItemStack(ModItems.RELIQUARY.get()), x + 4, y + 4);
        int n = machines().size();
        if (n > 0) {
            String count = Integer.toString(n);
            g.pose().pushPose();
            g.pose().translate(0, 0, 200);
            g.drawString(font, count, x + TAB_W - 3 - font.width(count), y + TAB_H - 10, C_SPIRIT, true);
            g.pose().popPose();
        }
    }

    private void renderPopout(GuiGraphics g, int mouseX, int mouseY) {
        int x = popX();
        int y = topPos;
        panel(g, x, y, POP_W, POP_H);
        TrayPayload tray = menu.clientTray();
        List<TrayPayload.Machine> list = machines();

        // Title: "Machines (N)", ! for unbound machines
        String title = Component.translatable("gui.tarbound.machines.title", list.size()).getString();
        g.drawString(font, title, x + 8, y + 6, C_TEXT, false);
        if (tray != null && tray.unbound() > 0) {
            g.drawString(font, "!", x + 8 + font.width(title) + 3, y + 6, C_MACHINE, false);
        }

        inset(g, x + 5, y + VIEW_Y - 1, POP_SCROLL_X - 6, VIEW_H + 2);
        if (list.isEmpty()) {
            g.drawString(font, Component.translatable("gui.tarbound.machines.none").getString(), x + 10, y + VIEW_Y + 6, C_TEXT_DIM, false);
            for (var line : font.split(Component.translatable("gui.tarbound.machines.none2"), POP_SCROLL_X - 16)) {
                g.drawString(font, line, x + 10, y + VIEW_Y + 17, C_TEXT_DIM, false);
            }
        }

        int hoveredRow = hoveredRow(mouseX, mouseY);
        SlotHit hoveredSlot = hoveredMachineSlot(mouseX, mouseY);
        int viewTop = y + VIEW_Y;
        g.enableScissor(x + 6, viewTop, x + POP_SCROLL_X - 1, viewTop + VIEW_H);
        for (MachineBlock b : machineLayout()) {
            int by = viewTop - machineScroll + b.top();
            if (by + b.height() < viewTop || by > viewTop + VIEW_H) {
                continue;
            }
            TrayPayload.Machine m = list.get(b.index());
            boolean isSel = m.pos().equals(selectedPos);

            // header
            if (isSel || b.index() == hoveredRow) {
                g.fill(x + 6, by, x + POP_SCROLL_X - 1, by + HDR_H, isSel ? C_ROW_SELECTED : C_ROW_HOVER);
            }
            ItemStack icon = new ItemStack(m.state().getBlock());
            g.renderItem(icon.isEmpty() ? new ItemStack(Items.FURNACE) : icon, x + 8, by + 2);
            String name = font.plainSubstrByWidth(m.displayName().getString(), POP_SCROLL_X - 36);
            g.drawString(font, name, x + 27, by + 6, isSel ? C_TEXT_BRIGHT : C_MACHINE, false);
            if (m.spiritFed()) {
                g.fill(x + POP_SCROLL_X - 6, by + 8, x + POP_SCROLL_X - 4, by + 10, C_SPIRIT);
            }
            if (!m.standing().isEmpty()) {
                g.fill(x + POP_SCROLL_X - 6, by + 12, x + POP_SCROLL_X - 4, by + 14, C_ROLE_OUTPUT);
            }
            if (m.pos().equals(MachineHighlight.target())) {
                g.drawString(font, "◎", x + POP_SCROLL_X - 14, by + 6, 0xFF8CF2FF, false); // highlighted in the world
            }

            // slots
            int sy0 = by + HDR_H;
            for (int i = 0; i < b.rows() * SLOT_COLS; i++) {
                int sx = x + SLOTS_X + (i % SLOT_COLS) * 18;
                int sy = sy0 + (i / SLOT_COLS) * 18;
                if (i >= m.slots().size()) {
                    continue;
                }
                slot(g, sx, sy);
                byte role = m.roles()[i];
                if (role == BoundInventory.Role.FUEL.ordinal()) {
                    g.fill(sx + 1, sy + 15, sx + 17, sy + 17, C_ROLE_FUEL);
                } else if (role == BoundInventory.Role.OUTPUT.ordinal()) {
                    g.fill(sx + 1, sy + 15, sx + 17, sy + 17, C_ROLE_OUTPUT);
                }
                ItemStack stack = m.slots().get(i);
                if (!stack.isEmpty()) {
                    g.renderItem(stack, sx + 1, sy + 1);
                    g.renderItemDecorations(font, stack, sx + 1, sy + 1);
                }
                if (hoveredSlot != null && hoveredSlot.machine() == m && hoveredSlot.slot() == i) {
                    renderSlotHighlight(g, sx + 1, sy + 1, 0);
                }
            }
            // tanks: read-only, filled from the bottom by how full they are
            int ty0 = sy0 + b.rows() * 18;
            TankHit hoveredTank = hoveredMachineTank(mouseX, mouseY);
            for (int t = 0; t < m.tanks().size(); t++) {
                int sx = x + SLOTS_X + (t % SLOT_COLS) * 18;
                int sy = ty0 + (t / SLOT_COLS) * 18;
                slot(g, sx, sy);
                TrayPayload.Tank tank = m.tanks().get(t);
                if (!tank.fluid().isEmpty() && tank.capacity() > 0) {
                    int h = Mth.clamp((int) Math.ceil(16.0 * tank.fluid().getAmount() / tank.capacity()), 1, 16);
                    g.enableScissor(sx + 1, sy + 17 - h, sx + 17, sy + 17); // stacks with (inside) the window's own scissor
                    renderFluid(g, tank.fluid(), sx + 1, sy + 1, 16, 16);
                    g.disableScissor();
                }
                g.fill(sx + 1, sy + 1, sx + 2, sy + 17, 0x40FFFFFF); // glass edge, so an empty tank reads as a tank
                if (hoveredTank != null && hoveredTank.machine() == m && hoveredTank.tank() == t) {
                    renderSlotHighlight(g, sx + 1, sy + 1, 0);
                }
            }
            // divider under the block
            g.fill(x + 8, by + b.height() + BLOCK_GAP / 2, x + POP_SCROLL_X - 3, by + b.height() + BLOCK_GAP / 2 + 1, C_SLOT_HI);
        }
        g.disableScissor();
        scrollbar(g, x + POP_SCROLL_X, y + VIEW_Y - 1, POP_SCROLL_W, VIEW_H + 2, machineScroll, machineMaxScroll());
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, 8, 6, C_TEXT, false);

        String status;
        int color;
        if (menu.clientInventories() == 0 && machines().isEmpty()) {
            status = Component.translatable("gui.tarbound.nothing_bound").getString();
            color = C_WARN;
        } else {
            status = Component.translatable("gui.tarbound.status", menu.clientInventories()).getString();
            color = C_TEXT_DIM;
        }
        TrayPayload trayNow = menu.clientTray();
        if (trayNow != null && trayNow.outOfRange() > 0) {
            status = Component.translatable("gui.tarbound.antennas_out_of_range", trayNow.outOfRange()).getString();
            color = C_WARN;
        }
        g.drawString(font, status, imageWidth - 8 - font.width(status), RESULT_Y + 25, color, false);
    }

    private List<Component> fluidTooltip(ContentsPayload.Entry e) {
        List<Component> lines = new ArrayList<>();
        lines.add(e.fluid().getHoverName());
        lines.add(Component.translatable("gui.tarbound.fluid.amount", String.format(Locale.ROOT, "%,d", e.count()),
                trim(e.count() / 1000.0)).withStyle(ChatFormatting.GOLD));
        if (e.count() > 0) {
            lines.add(Component.translatable("gui.tarbound.fluid.fill_hint").withStyle(ChatFormatting.DARK_GRAY));
        }
        if (net.minecraft.client.Minecraft.getInstance().options.advancedItemTooltips) {
            lines.add(Component.literal(BuiltInRegistries.FLUID.getKey(e.fluid().getFluid()).toString()).withStyle(ChatFormatting.DARK_GRAY));
        }
        return lines;
    }

    /** Draws a fluid's still texture, tinted, as a 16×16 icon. */
    static void renderFluid(GuiGraphics g, net.neoforged.neoforge.fluids.FluidStack fluid, int x, int y, int w, int h) {
        if (fluid.isEmpty() || w <= 0 || h <= 0) {
            return;
        }
        var ext = net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions.of(fluid.getFluid());
        ResourceLocation still = ext.getStillTexture(fluid);
        if (still == null) {
            return;
        }
        var sprite = net.minecraft.client.Minecraft.getInstance()
                .getTextureAtlas(net.minecraft.world.inventory.InventoryMenu.BLOCK_ATLAS).apply(still);
        int tint = ext.getTintColor(fluid);
        float a = ((tint >>> 24) & 0xFF) / 255F;
        if (a == 0) {
            a = 1F;
        }
        g.blit(x, y, 0, w, h, sprite, ((tint >> 16) & 0xFF) / 255F, ((tint >> 8) & 0xFF) / 255F, (tint & 0xFF) / 255F, a);
    }

    private void drawCount(GuiGraphics g, long count, int ix, int iy) {
        if (count <= 1) {
            return;
        }
        drawCount(g, count, ix, iy, formatCount(count));
    }

    private void drawCount(GuiGraphics g, long count, int ix, int iy, String text) {
        float scale = Math.min(1.0F, 16.0F / font.width(text));
        if (scale > 0.75F && text.length() > 3) {
            scale = 0.75F;
        }
        g.pose().pushPose();
        g.pose().translate(ix + 17, iy + 17, 200);
        g.pose().scale(scale, scale, 1.0F);
        g.drawString(font, text, -font.width(text), -8, 0xFFFFFFFF, true);
        g.pose().popPose();
    }

    static String formatCount(long n) {
        if (n < 1_000) return Long.toString(n);
        if (n < 10_000) return trim(n / 1_000.0) + "k";
        if (n < 1_000_000) return (n / 1_000) + "k";
        if (n < 10_000_000) return trim(n / 1_000_000.0) + "M";
        if (n < 1_000_000_000) return (n / 1_000_000) + "M";
        return trim(n / 1_000_000_000.0) + "B";
    }

    private static String trim(double v) {
        String s = String.format(Locale.ROOT, "%.1f", Math.floor(v * 10) / 10);
        return s.endsWith(".0") ? s.substring(0, s.length() - 2) : s;
    }

    // ==========================================================================================
    // Jobs tab and pane
    // ==========================================================================================

    private List<TrayPayload.JobView> jobs() {
        TrayPayload tray = menu.clientTray();
        return tray == null ? List.of() : tray.jobs();
    }

    /**
     * One line of the job list: an order's header (job null; {@code shown} is the step it reports, its last if that's
     * still running, else the newest), or a job (indented when it's a step of an order).
     */
    private record JobRow(@javax.annotation.Nullable TrayPayload.JobView job, int order, TrayPayload.JobView shown, int steps) {
        boolean header() {
            return job == null;
        }
    }

    /** Orders the player has opened up in the list, to see every step (client side, this screen only). */
    private final java.util.Set<Integer> openOrders = new java.util.HashSet<>();

    /** The job list as shown: each order as one header line (its steps under it when opened), then single jobs. */
    private List<JobRow> jobRows() {
        java.util.Map<Integer, List<TrayPayload.JobView>> byOrder = new java.util.LinkedHashMap<>();
        List<TrayPayload.JobView> singles = new ArrayList<>();
        for (TrayPayload.JobView j : jobs()) {
            if (j.order() == 0) {
                singles.add(j);
            } else {
                byOrder.computeIfAbsent(j.order(), k -> new ArrayList<>()).add(j);
            }
        }
        List<JobRow> rows = new ArrayList<>();
        for (java.util.Map.Entry<Integer, List<TrayPayload.JobView>> e : byOrder.entrySet()) {
            List<TrayPayload.JobView> steps = e.getValue();
            TrayPayload.JobView shown = steps.stream().filter(TrayPayload.JobView::root).findFirst().orElse(steps.get(steps.size() - 1));
            rows.add(new JobRow(null, e.getKey(), shown, steps.size()));
            if (openOrders.contains(e.getKey())) {
                for (TrayPayload.JobView j : steps) {
                    rows.add(new JobRow(j, e.getKey(), j, 1));
                }
            }
        }
        for (TrayPayload.JobView j : singles) {
            rows.add(new JobRow(j, 0, j, 1));
        }
        openOrders.retainAll(byOrder.keySet());
        return rows;
    }

    private int jobsTabY() {
        return topPos + TAB_Y + TAB_H + 2;
    }

    private boolean overJobsTab(double mx, double my) {
        return in(mx, my, tabX(), jobsTabY(), TAB_W, TAB_H);
    }

    private static final int JOB_ROWS = 5;
    private static final int STAND_HDR_Y = LIST_Y + JOB_ROWS * ROW_H + 6;
    private static final int STAND_Y = STAND_HDR_Y + 12;
    private static final int STAND_ROWS = 3;
    private static final int STOP_H = 14;

    /** A standing contract and the machine whose reliquary holds it. */
    private record StandingRow(TrayPayload.Machine machine, TrayPayload.StandingView view) {}

    private List<StandingRow> standingRows() {
        List<StandingRow> rows = new ArrayList<>();
        for (TrayPayload.Machine m : machines()) {
            for (TrayPayload.StandingView v : m.standing()) {
                rows.add(new StandingRow(m, v));
            }
        }
        return rows;
    }

    private int standingMaxScroll() {
        return Math.max(0, standingRows().size() - STAND_ROWS);
    }

    private int hoveredStandingRow(double mx, double my) {
        if (pane != Pane.JOBS) {
            return -1;
        }
        int rx = (int) Math.floor(mx) - popX() - 6;
        int ry = (int) Math.floor(my) - topPos - STAND_Y;
        if (rx < 0 || rx >= POP_SCROLL_X - 8 || ry < 0 || ry >= STAND_ROWS * ROW_H) {
            return -1;
        }
        int index = standingScroll + ry / ROW_H;
        return index < standingRows().size() ? index : -1;
    }

    /** Standing row whose pause/resume toggle is under the mouse, or -1. */
    private int hoveredStandingToggle(double mx, double my) {
        int row = hoveredStandingRow(mx, my);
        if (row < 0) {
            return -1;
        }
        int cx = popX() + POP_SCROLL_X - 13;
        int cy = topPos + STAND_Y + (row - standingScroll) * ROW_H + 5;
        return in(mx, my, cx, cy, 10, 10) ? row : -1;
    }

    /** First item in a tag, for an icon. */
    private static ItemStack tagIcon(String tag) {
        ResourceLocation id = ResourceLocation.tryParse(tag);
        if (id == null) {
            return new ItemStack(Items.BARRIER);
        }
        return BuiltInRegistries.ITEM.getTag(TagKey.create(Registries.ITEM, id))
                .flatMap(set -> set.stream().findFirst())
                .map(h -> new ItemStack(h.value()))
                .orElse(new ItemStack(Items.BARRIER));
    }

    // --- cancel all (under the jobs tab, two clicks)

    private int cancelAllY() {
        return jobsTabY() + TAB_H + 2;
    }

    private boolean overCancelAll(double mx, double my) {
        return !jobs().isEmpty() && in(mx, my, tabX(), cancelAllY(), TAB_W, STOP_H);
    }

    private boolean cancelAllArmed() {
        return System.currentTimeMillis() < cancelAllArmedUntil;
    }

    private void clickCancelAll() {
        if (cancelAllArmed()) {
            PacketDistributor.sendToServer(JobActionPayload.cancelAll(menu.containerId));
            cancelAllArmedUntil = 0;
        } else {
            cancelAllArmedUntil = System.currentTimeMillis() + 3000;
        }
    }

    private void renderCancelAll(GuiGraphics g, int mouseX, int mouseY) {
        if (jobs().isEmpty()) {
            return;
        }
        int x = tabX();
        int y = cancelAllY();
        panel(g, x, y, TAB_W, STOP_H);
        boolean armed = cancelAllArmed();
        boolean hover = overCancelAll(mouseX, mouseY);
        g.fill(x + 3, y + 3, x + TAB_W - 3, y + STOP_H - 3, armed ? C_WARN : hover ? 0xFF6A3328 : 0xFF4A2A22);
        String label = armed ? "?" : "×";
        g.drawString(font, label, x + (TAB_W - font.width(label)) / 2, y + 3, C_TEXT_BRIGHT, false);
    }

    private int jobMaxScroll() {
        return Math.max(0, jobRows().size() - JOB_ROWS);
    }

    private int hoveredJobRow(double mx, double my) {
        if (pane != Pane.JOBS) {
            return -1;
        }
        int rx = (int) Math.floor(mx) - popX() - 6;
        int ry = (int) Math.floor(my) - topPos - LIST_Y;
        if (rx < 0 || rx >= POP_SCROLL_X - 8 || ry < 0 || ry >= JOB_ROWS * ROW_H) {
            return -1;
        }
        int index = jobScroll + ry / ROW_H;
        return index < jobRows().size() ? index : -1;
    }

    /** Job index whose cancel × is under the mouse, or -1. */
    private int hoveredJobCancel(double mx, double my) {
        int row = hoveredJobRow(mx, my);
        if (row < 0) {
            return -1;
        }
        int cx = popX() + POP_SCROLL_X - 13;
        int cy = topPos + LIST_Y + (row - jobScroll) * ROW_H + 5;
        return in(mx, my, cx, cy, 10, 10) ? row : -1;
    }

    /** What an order (of a job in it) is for, with how many. */
    private static Component orderName(TrayPayload.JobView j) {
        if (!j.orderFluid().isEmpty()) {
            return Component.translatable("gui.tarbound.order.name_fluid", String.format(Locale.ROOT, "%,d", j.orderAmount() / 1000), j.orderFluid().getHoverName());
        }
        return Component.translatable("gui.tarbound.order.name", j.orderAmount(), j.orderOutput().getHoverName());
    }

    /** Tooltip lines for what a job is held up by (see {@link TrayPayload.Wait}). */
    private List<Component> waitLines(TrayPayload.JobView j) {
        List<Component> out = new ArrayList<>();
        TrayPayload.Wait w = j.hold();
        Component what = !w.item().isEmpty()
                ? Component.translatable("gui.tarbound.job.wait_item", w.item().getCount(), w.item().getHoverName())
                : !w.fluid().isEmpty()
                        ? Component.translatable("gui.tarbound.job.wait_fluid", String.format(Locale.ROOT, "%,d", w.fluid().getAmount()), w.fluid().getHoverName())
                        : null;
        if (what != null) {
            out.add(what.copy().withStyle(ChatFormatting.GOLD));
            if (w.feeder() != 0) {
                TrayPayload.JobView feeder = jobs().stream().filter(o -> o.id() == w.feeder()).findFirst().orElse(null);
                Component name = feeder == null ? Component.literal("#" + w.feeder())
                        : feeder.isFluid() ? feeder.fluid().getHoverName() : feeder.output().getHoverName();
                out.add(Component.translatable("gui.tarbound.job.wait_feeder", name).withStyle(ChatFormatting.DARK_AQUA));
            } else {
                out.add(Component.translatable("gui.tarbound.job.wait_none").withStyle(ChatFormatting.DARK_GRAY));
            }
        }
        if (w.note() != null) {
            out.add(w.note().copy().withStyle(ChatFormatting.GRAY));
        }
        return out;
    }

    private static Component statusLabel(byte status) {
        ContractJob.Status s = ContractJob.Status.values()[Mth.clamp(status, 0, ContractJob.Status.values().length - 1)];
        return switch (s) {
            case RUNNING -> Component.translatable("gui.tarbound.job.running").withStyle(ChatFormatting.GREEN);
            case WAITING_INPUTS -> Component.translatable("gui.tarbound.job.waiting").withStyle(ChatFormatting.GOLD);
            case NO_MACHINE -> Component.translatable("gui.tarbound.job.no_machine").withStyle(ChatFormatting.RED);
            case MACHINE_BUSY -> Component.translatable("gui.tarbound.job.busy").withStyle(ChatFormatting.YELLOW);
            case FINISHING -> Component.translatable("gui.tarbound.job.finishing").withStyle(ChatFormatting.AQUA);
            case POOL_FULL -> Component.translatable("gui.tarbound.job.pool_full").withStyle(ChatFormatting.RED);
            case STALLED -> Component.translatable("gui.tarbound.job.stalled").withStyle(ChatFormatting.GOLD);
            case HUNGRY -> Component.translatable("gui.tarbound.job.hungry").withStyle(ChatFormatting.LIGHT_PURPLE);
            case NO_TANK_ROOM -> Component.translatable("gui.tarbound.job.no_tank_room").withStyle(ChatFormatting.RED);
        };
    }

    private void renderJobsTab(GuiGraphics g, int mouseX, int mouseY) {
        int x = tabX();
        int y = jobsTabY();
        panel(g, x, y, TAB_W, TAB_H);
        if (pane == Pane.JOBS) {
            g.fill(x + TAB_W - 3, y + 2, x + TAB_W, y + TAB_H - 2, C_PANEL);
        }
        if (overJobsTab(mouseX, mouseY)) {
            g.fill(x + 3, y + 3, x + TAB_W - 3, y + TAB_H - 3, 0x20FFFFFF);
        }
        g.renderItem(new ItemStack(ModItems.SPIRIT_CONTRACT.get()), x + 4, y + 4);
        int n = jobs().size();
        if (n > 0) {
            String count = Integer.toString(n);
            g.pose().pushPose();
            g.pose().translate(0, 0, 200);
            g.drawString(font, count, x + TAB_W - 3 - font.width(count), y + TAB_H - 10, C_SPIRIT, true);
            g.pose().popPose();
        }
    }

    private void renderJobs(GuiGraphics g, int mouseX, int mouseY) {
        int x = popX();
        int y = topPos;
        panel(g, x, y, POP_W, POP_H);
        List<TrayPayload.JobView> list = jobs();
        g.drawString(font, Component.translatable("gui.tarbound.jobs.title", list.size()).getString(), x + 8, y + 6, C_TEXT, false);
        inset(g, x + 5, y + LIST_Y - 1, POP_SCROLL_X - 6, JOB_ROWS * ROW_H + 2);
        if (list.isEmpty()) {
            g.drawString(font, Component.translatable("gui.tarbound.jobs.none").getString(), x + 10, y + LIST_Y + 6, C_TEXT_DIM, false);
            g.drawString(font, Component.translatable("gui.tarbound.jobs.none2").getString(), x + 10, y + LIST_Y + 17, C_TEXT_DIM, false);
            g.drawString(font, Component.translatable("gui.tarbound.jobs.none3").getString(), x + 10, y + LIST_Y + 28, C_TEXT_DIM, false);
        }
        int hoveredCancel = hoveredJobCancel(mouseX, mouseY);
        List<JobRow> jobRows = jobRows();
        for (int r = 0; r < JOB_ROWS; r++) {
            int index = jobScroll + r;
            if (index >= jobRows.size()) {
                break;
            }
            JobRow row = jobRows.get(index);
            TrayPayload.JobView j = row.shown();
            int ry = y + LIST_Y + r * ROW_H;
            int indent = !row.header() && row.order() != 0 ? 10 : 0;
            int ix = x + 8 + indent;
            String name;
            if (row.header()) {
                // the order: what was ordered, its last step's progress, an arrow to open it up
                if (!j.orderFluid().isEmpty()) {
                    renderFluid(g, j.orderFluid(), ix, ry + 2, 16, 16);
                } else {
                    g.renderItem(j.orderOutput(), ix, ry + 2);
                }
                name = (openOrders.contains(row.order()) ? "▾ " : "▸ ") + orderName(j).getString();
            } else {
                if (indent > 0) {
                    g.fill(x + 10, ry, x + 11, ry + ROW_H, C_TEXT_DIM); // the order's thread down the left
                }
                if (j.isFluid()) {
                    renderFluid(g, j.fluid(), ix, ry + 2, 16, 16);
                } else {
                    g.renderItem(j.output(), ix, ry + 2);
                }
                name = (j.isFluid() ? j.fluid().getHoverName() : j.output().getHoverName()).getString();
            }
            int tx = x + 27 + indent;
            g.drawString(font, font.plainSubstrByWidth(name, POP_SCROLL_X - 48 - indent), tx, ry + 2, row.header() ? C_SPIRIT : C_TEXT, false);
            String progress = row.header()
                    ? Component.translatable("gui.tarbound.order.steps_short", row.steps()).getString() + "  "
                            + formatCount(j.received()) + "/" + formatCount(j.expected())
                    : formatCount(j.received()) + "/" + formatCount(j.expected());
            g.drawString(font, progress, tx, ry + 11, statusLabel(j.status()).getStyle().getColor() == null
                    ? C_TEXT_DIM : 0xFF000000 | statusLabel(j.status()).getStyle().getColor().getValue(), false);
            // progress bar
            int barX = tx + font.width(progress) + 4;
            int barW = Math.max(4, POP_SCROLL_X - 18 - (barX - x));
            g.fill(barX, ry + 13, barX + barW, ry + 16, C_SLOT_LO);
            int done = j.expected() == 0 ? 0 : (int) (barW * Math.min(1.0, j.received() / (double) j.expected()));
            g.fill(barX, ry + 13, barX + done, ry + 16, C_SPIRIT);
            int cx = x + POP_SCROLL_X - 13;
            int cy = ry + 5;
            g.fill(cx, cy, cx + 10, cy + 10, index == hoveredCancel ? C_WARN : C_SLOT_HI);
            g.drawString(font, "×", cx + 2, cy + 1, C_TEXT_BRIGHT, false);
        }
        scrollbar(g, x + POP_SCROLL_X, y + LIST_Y - 1, POP_SCROLL_W, JOB_ROWS * ROW_H + 2, jobScroll, jobMaxScroll());

        // Standing contracts across every bound reliquary
        List<StandingRow> rows = standingRows();
        g.drawString(font, Component.translatable("gui.tarbound.standing.title", rows.size()).getString(), x + 8, y + STAND_HDR_Y, C_TEXT, false);
        inset(g, x + 5, y + STAND_Y - 1, POP_SCROLL_X - 6, STAND_ROWS * ROW_H + 2);
        if (rows.isEmpty()) {
            g.drawString(font, Component.translatable("gui.tarbound.standing.none").getString(), x + 10, y + STAND_Y + 6, C_TEXT_DIM, false);
            g.drawString(font, Component.translatable("gui.tarbound.standing.none2").getString(), x + 10, y + STAND_Y + 17, C_TEXT_DIM, false);
        }
        int hoveredToggle = hoveredStandingToggle(mouseX, mouseY);
        for (int r = 0; r < STAND_ROWS; r++) {
            int index = standingScroll + r;
            if (index >= rows.size()) {
                break;
            }
            StandingRow row = rows.get(index);
            int ry = y + STAND_Y + r * ROW_H;
            g.renderItem(tagIcon(row.view().tag()), x + 8, ry + 2);
            if (row.view().paused()) {
                g.fill(x + 8, ry + 2, x + 24, ry + 18, 0x90100D0B);
            }
            int textColor = row.view().paused() ? C_TEXT_DIM : C_TEXT;
            g.drawString(font, font.plainSubstrByWidth(StandingOrder.tagName(row.view().tag()).getString(), POP_SCROLL_X - 48), x + 27, ry + 2, textColor, false);
            g.drawString(font, font.plainSubstrByWidth(row.machine().displayName().getString(), POP_SCROLL_X - 48), x + 27, ry + 11,
                    row.view().paused() ? C_TEXT_DIM : C_MACHINE, false);
            int cx = x + POP_SCROLL_X - 13;
            int cy = ry + 5;
            g.fill(cx, cy, cx + 10, cy + 10, index == hoveredToggle ? C_PANEL_HI : C_SLOT_HI);
            if (row.view().paused()) {
                // play triangle
                for (int i = 0; i < 4; i++) {
                    g.fill(cx + 3 + i, cy + 2 + i, cx + 4 + i, cy + 8 - i, C_ROLE_OUTPUT);
                }
            } else {
                // pause bars
                g.fill(cx + 3, cy + 2, cx + 5, cy + 8, C_SPIRIT);
                g.fill(cx + 6, cy + 2, cx + 8, cy + 8, C_SPIRIT);
            }
        }
        scrollbar(g, x + POP_SCROLL_X, y + STAND_Y - 1, POP_SCROLL_W, STAND_ROWS * ROW_H + 2, standingScroll, standingMaxScroll());
    }

    // ==========================================================================================
    // Request panel (single-step job)
    // ==========================================================================================

    private static final int REQ_W = 164;
    private static final int REQ_H = 150;
    /** The plan list in the order panel: where it starts, line height, and lines shown. */
    private static final int PLAN_Y = 52;
    private static final int PLAN_LINE = 10;
    private static final int PLAN_ROWS = 7;

    @Nullable
    private com.tarbound.contract.CraftPlanner.Plan plan;
    private List<com.tarbound.contract.CraftPlanner.Node> planLines = List.of();
    private int planScroll;
    /** What the cached plan was made for (amount, pool version, tray version), so it's only redone when they change. */
    private long planKey = Long.MIN_VALUE;

    /** The order's plan, redone only when the amount, the pool or the machines changed. */
    private com.tarbound.contract.CraftPlanner.Plan currentPlan() {
        long key = ((long) requestAmount << 40) ^ ((long) menu.clientVersion() << 20) ^ menu.clientTrayVersion()
                ^ System.identityHashCode(requesting);
        if (plan == null || key != planKey) {
            planKey = key;
            List<com.tarbound.contract.CraftPlanner.MachineContracts> held = new ArrayList<>();
            java.util.Set<ContractData> seen = new java.util.HashSet<>();
            List<List<ItemStack>> allInputs = new ArrayList<>();
            for (TrayPayload.Machine m : machines()) {
                List<ContractData> cs = m.contracts().stream().filter(java.util.Objects::nonNull).toList();
                // busy: a job still loading makes what one of its contracts makes (the tray doesn't carry the job's contract)
                boolean busy = jobs().stream().anyMatch(j -> j.pushedOps() < j.totalOps() && cs.stream().anyMatch(c -> !j.isFluid()
                        ? ItemStack.isSameItemSameComponents(c.primaryOutput(), j.output())
                        : c.tracksFluid() && net.neoforged.neoforge.fluids.FluidStack.isSameFluidSameComponents(c.trackedFluid(), j.fluid())));
                held.add(new com.tarbound.contract.CraftPlanner.MachineContracts(m.displayName(), cs, busy));
                for (ContractData c : cs) {
                    if (seen.add(c)) {
                        allInputs.addAll(c.inputs());
                    }
                }
            }
            List<com.tarbound.contract.CraftPlanner.Source> sources = com.tarbound.contract.CraftPlanner.gather(held);
            Map<com.tarbound.network.FluidKey, Long> fluids = poolFluids();
            Map<com.tarbound.network.ItemKey, Long> stock = TarNetwork.withFills(poolCounts(), fluids, TarNetwork.flatten(allInputs));
            TrayPayload tray = menu.clientTray();
            if (tray != null && tray.weepingSeep()) {
                stock = new java.util.HashMap<>(stock);
                TarNetwork.addSeepSupply(stock, TarNetwork.flatten(allInputs), TarNetwork.madeBy(new ArrayList<>(seen)));
            }
            plan = com.tarbound.contract.CraftPlanner.plan(
                    new com.tarbound.contract.CraftPlanner.Source(requesting.contract(), requesting.machineName()), requestOps(), sources,
                    stock, fluids,
                    planDepth(), planNodes());
            planLines = com.tarbound.contract.CraftPlanner.lines(plan);
            planScroll = Mth.clamp(planScroll, 0, Math.max(0, planLines.size() - PLAN_ROWS));
        }
        return plan;
    }
    private static final int[] STEPS = {-64, -8, -1, 1, 8, 64};
    private static final int[] FLUID_STEPS = {-10, -5, -1, 1, 5, 10};
    private static final int[] STEP_X = {4, 28, 48, 100, 118, 138};
    private static final int[] STEP_W = {22, 18, 16, 16, 18, 22};

    private int reqX() {
        return leftPos + GRID_X - 1;
    }

    private int reqY() {
        return topPos + GRID_Y - 1;
    }

    private void openRequest(Craftable c) {
        requesting = c;
        requestAmount = c.isFluid() ? 1 : Math.max(1, c.output().getCount());
    }

    /** Ordering a fluid: the amount is in whole buckets. */
    private boolean requestingFluid() {
        return requesting != null && requesting.isFluid();
    }

    /** What one operation makes: items, or mB for a fluid. */
    private int perOp() {
        return requesting == null ? 1 : requesting.contract().perOp();
    }

    /** The amount asked for, in items or mB. */
    private long requestedUnits() {
        return requestingFluid() ? requestAmount * 1000L : requestAmount;
    }

    private int requestOps() {
        return (int) ((requestedUnits() + perOp() - 1) / perOp());
    }

    private int maxRequest() {
        long most = (long) perOp() * ScryingFontBlockEntity.maxOps();
        return (int) Math.max(1, Math.min(Integer.MAX_VALUE, requestingFluid() ? most / 1000 : most));
    }

    /** The amount buttons: items step by 1, 8 and 64; fluids by whole buckets, 1, 5 and 10. */
    private int[] steps() {
        return requestingFluid() ? FLUID_STEPS : STEPS;
    }

    /** Scroll step on the amount: shift for bigger steps (10 buckets for a fluid, 8 items). */
    private int scrollStep() {
        return hasShiftDown() ? (requestingFluid() ? 10 : 8) : 1;
    }

    private Map<ItemKey, Long> poolCounts() {
        Map<ItemKey, Long> counts = new java.util.HashMap<>();
        for (ContentsPayload.Entry e : menu.clientEntries()) {
            counts.merge(ItemKey.of(e.proto()), e.count(), Long::sum);
        }
        return counts;
    }

    private Map<com.tarbound.network.FluidKey, Long> poolFluids() {
        Map<com.tarbound.network.FluidKey, Long> fluids = new java.util.HashMap<>();
        for (ContentsPayload.Entry e : menu.clientFluidEntries()) {
            fluids.merge(com.tarbound.network.FluidKey.of(e.fluid()), e.count(), Long::sum);
        }
        return fluids;
    }

    private boolean requestReady() {
        return requesting != null && currentPlan().complete() && !needsShade();
    }

    /** The plan has sub-crafts and no free Shade tuned to this radio can watch that many steps. */
    private boolean needsShade() {
        TrayPayload tray = menu.clientTray();
        int steps = requesting == null ? 0 : currentPlan().steps().size();
        return steps > 1 && (tray == null || tray.freeShades() < steps);
    }

    /** The server's plan limits (the preview must plan exactly as the server will), or the old defaults until known. */
    private int planDepth() {
        TrayPayload tray = menu.clientTray();
        return tray == null ? com.tarbound.contract.CraftPlanner.CLIENT_DEPTH : tray.planDepth();
    }

    private int planNodes() {
        TrayPayload tray = menu.clientTray();
        return tray == null ? com.tarbound.contract.CraftPlanner.CLIENT_NODES : tray.planNodes();
    }

    private void startRequest() {
        if (requesting != null) {
            // The server plans again from its own counts; sub-crafts become jobs queued ahead of this one.
            PacketDistributor.sendToServer(new com.tarbound.net.OrderPlanPayload(menu.containerId, requesting.machine(), requesting.slot(), requestOps()));
        }
    }

    /** Index into the plan lines under the mouse, or -1. */
    private int hoveredPlanLine(double mx, double my) {
        if (requesting == null) {
            return -1;
        }
        int x = reqX() + 4;
        int y = reqY() + PLAN_Y;
        if (mx < x || mx >= x + REQ_W - 12 || my < y || my >= y + PLAN_ROWS * PLAN_LINE) {
            return -1;
        }
        int i = planScroll + (int) ((my - y) / PLAN_LINE);
        return i < planLines.size() ? i : -1;
    }

    private void renderRequest(GuiGraphics g, int mouseX, int mouseY) {
        Craftable c = requesting;
        int x = reqX();
        int y = reqY();
        g.pose().pushPose();
        g.pose().translate(0, 0, 400);
        panel(g, x, y, REQ_W, REQ_H);
        if (c.isFluid()) {
            renderFluid(g, c.fluid(), x + 5, y + 5, 16, 16);
        } else {
            g.renderItem(c.output(), x + 5, y + 5);
        }
        g.drawString(font, font.plainSubstrByWidth(c.name().getString(), REQ_W - 30), x + 25, y + 5, C_TEXT_BRIGHT, false);
        g.drawString(font, font.plainSubstrByWidth(Component.translatable("gui.tarbound.craft.via", c.machineName()).getString(), REQ_W - 30),
                x + 25, y + 14, C_TEXT_DIM, false);

        // amount stepper
        int ay = y + 26;
        int[] steps = steps();
        for (int i = 0; i < steps.length; i++) {
            int bx = x + STEP_X[i];
            boolean hover = in(mouseX, mouseY, bx, ay, STEP_W[i], 12);
            g.fill(bx, ay, bx + STEP_W[i], ay + 12, hover ? C_PANEL_HI : C_SLOT_HI);
            String label = (steps[i] > 0 ? "+" : "") + steps[i];
            g.drawString(font, label, bx + (STEP_W[i] - font.width(label)) / 2, ay + 2, C_TEXT_BRIGHT, false);
        }
        inset(g, x + 66, ay - 1, 32, 14);
        String amount = requestingFluid() ? requestAmount + " B" : Integer.toString(requestAmount);
        g.drawString(font, amount, x + 82 - font.width(amount) / 2, ay + 2, C_TEXT_BRIGHT, false);
        Component opsLine = requestingFluid()
                ? Component.translatable("gui.tarbound.craft.ops_fluid", requestOps(), String.format(Locale.ROOT, "%,d", (long) perOp() * requestOps()))
                : Component.translatable("gui.tarbound.craft.ops", requestOps(), perOp() * requestOps());
        g.drawString(font, opsLine.getString(), x + 5, y + 42, C_TEXT_DIM, false);

        // the plan: what comes out of storage, what gets crafted first, what's missing
        com.tarbound.contract.CraftPlanner.Plan pl = currentPlan();
        inset(g, x + 3, y + PLAN_Y - 2, REQ_W - 6, PLAN_ROWS * PLAN_LINE + 4);
        int hoveredLine = hoveredPlanLine(mouseX, mouseY);
        for (int row = 0; row < PLAN_ROWS; row++) {
            int i = planScroll + row;
            if (i >= planLines.size()) {
                break;
            }
            var n = planLines.get(i);
            int ly = y + PLAN_Y + row * PLAN_LINE;
            if (i == hoveredLine) {
                g.fill(x + 4, ly, x + REQ_W - 5, ly + PLAN_LINE, 0x30FFFFFF);
            }
            int lx = x + 6 + Math.min(n.depth, 8) * 6;
            g.pose().pushPose();
            g.pose().translate(lx, ly + 1, 0);
            g.pose().scale(0.5F, 0.5F, 1.0F);
            if (n.isFluid()) {
                renderFluid(g, n.fluid, 0, 0, 16, 16);
            } else {
                g.renderItem(n.item, 0, 0);
            }
            g.pose().popPose();
            String name = formatCount(n.needed) + " " + (n.isFluid() ? n.fluid.getHoverName().getString() : n.item.getHoverName().getString());
            String tag;
            int tagColor;
            if (n.missing > 0) {
                tag = "-" + formatCount(n.missing);
                tagColor = C_WARN;
            } else if (n.crafted > 0 && n.fromStock > 0) {
                tag = "\u2713" + formatCount(n.fromStock) + " +" + formatCount(n.crafted);
                tagColor = C_SPIRIT;
            } else if (n.crafted > 0) {
                tag = "+" + formatCount(n.crafted);
                tagColor = C_SPIRIT;
            } else {
                tag = "\u2713";
                tagColor = 0xFF7FE0A0;
            }
            int tagW = font.width(tag);
            int nameMax = x + REQ_W - 8 - tagW - 3 - (lx + 10);
            g.drawString(font, font.plainSubstrByWidth(name, Math.max(10, nameMax)), lx + 10, ly + 1, n.depth == 0 ? C_TEXT_BRIGHT : C_TEXT, false);
            g.drawString(font, tag, x + REQ_W - 8 - tagW, ly + 1, tagColor, false);
        }
        if (planLines.size() > PLAN_ROWS) {
            String more = (planScroll + PLAN_ROWS) + "/" + planLines.size();
            g.drawString(font, more, x + REQ_W - 6 - font.width(more), y + 42, C_TEXT_DIM, false);
        }

        // buttons
        int by = y + REQ_H - 18;
        boolean ready = requestReady();
        boolean hoverCancel = in(mouseX, mouseY, x + 5, by, 60, 13);
        boolean hoverStart = in(mouseX, mouseY, x + REQ_W - 65, by, 60, 13);
        g.fill(x + 5, by, x + 65, by + 13, hoverCancel ? C_PANEL_HI : C_SLOT_HI);
        String cancel = Component.translatable("gui.tarbound.craft.cancel").getString();
        g.drawString(font, cancel, x + 35 - font.width(cancel) / 2, by + 3, C_TEXT_BRIGHT, false);
        g.fill(x + REQ_W - 65, by, x + REQ_W - 5, by + 13, !ready ? C_THUMB_OFF : hoverStart ? 0xFF3F8C6E : 0xFF2E6B54);
        String start = Component.translatable("gui.tarbound.craft.start").getString();
        g.drawString(font, start, x + REQ_W - 35 - font.width(start) / 2, by + 3, ready ? C_TEXT_BRIGHT : C_TEXT_DIM, false);
        g.pose().popPose();

        if (hoverStart && !ready) {
            List<Component> why = new ArrayList<>();
            if (pl.complete() && needsShade()) {
                TrayPayload tray = menu.clientTray();
                int stepCount = pl.steps().size();
                if (tray != null && tray.freeShades() > 0) {
                    why.add(Component.translatable("gui.tarbound.order.weak_shade", stepCount, com.tarbound.block.ShadeBlockEntity.rankFor(stepCount))
                            .withStyle(ChatFormatting.RED));
                    why.add(Component.translatable("gui.tarbound.order.weak_shade.hint").withStyle(ChatFormatting.GRAY));
                } else {
                    why.add(Component.translatable("gui.tarbound.order.no_shade").withStyle(ChatFormatting.RED));
                    why.add(Component.translatable("gui.tarbound.order.no_shade.hint").withStyle(ChatFormatting.GRAY));
                }
            } else {
                why.add(Component.translatable(pl.truncated() ? "gui.tarbound.plan.too_big" : "gui.tarbound.job.short").withStyle(ChatFormatting.RED));
            }
            int listed = 0;
            for (var e : pl.missing().entrySet()) {
                if (listed++ >= 6) {
                    break;
                }
                Component what = e.getKey() instanceof ItemKey ik ? ik.proto().getHoverName()
                        : ((com.tarbound.network.FluidKey) e.getKey()).proto().getHoverName();
                why.add(Component.literal(formatCount(e.getValue()) + " ").append(what).withStyle(ChatFormatting.GRAY));
            }
            g.renderComponentTooltip(font, why, mouseX, mouseY);
        } else if (hoveredLine >= 0) {
            var n = planLines.get(hoveredLine);
            List<Component> lines = new ArrayList<>();
            lines.add(n.isFluid() ? n.fluid.getHoverName() : n.item.getHoverName());
            lines.add(Component.translatable("gui.tarbound.plan.needed", formatCount(n.needed)).withStyle(ChatFormatting.GRAY));
            if (n.fromStock > 0) {
                lines.add(Component.translatable("gui.tarbound.plan.stored", formatCount(n.fromStock)).withStyle(ChatFormatting.GREEN));
            }
            if (n.crafted > 0 && n.source != null) {
                lines.add(Component.translatable("gui.tarbound.plan.crafted", formatCount(n.crafted), n.ops, n.source.machine())
                        .withStyle(ChatFormatting.AQUA));
            }
            if (n.missing > 0) {
                lines.add(Component.translatable("gui.tarbound.plan.missing", formatCount(n.missing)).withStyle(ChatFormatting.RED));
            }
            g.renderComponentTooltip(font, lines, mouseX, mouseY);
        }
    }

    private void handleRequestClick(double mx, double my, int button) {
        int x = reqX();
        int y = reqY();
        if (!in(mx, my, x, y, REQ_W, REQ_H)) {
            requesting = null;
            return;
        }
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return;
        }
        int ay = y + 26;
        int[] steps = steps();
        for (int i = 0; i < steps.length; i++) {
            if (in(mx, my, x + STEP_X[i], ay, STEP_W[i], 12)) {
                requestAmount = Mth.clamp(requestAmount + steps[i], 1, maxRequest());
                return;
            }
        }
        int by = y + REQ_H - 18;
        if (in(mx, my, x + 5, by, 60, 13)) {
            requesting = null;
        } else if (in(mx, my, x + REQ_W - 65, by, 60, 13) && requestReady()) {
            startRequest();
            requesting = null;
            pane = Pane.JOBS;
        }
    }

    // Vanilla-shaped bevels in the tar palette.

    private static void panel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x + 1, y, x + w - 1, y + h, C_OUTLINE);
        g.fill(x, y + 1, x + w, y + h - 1, C_OUTLINE);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, C_PANEL);
        g.fill(x + 1, y + 1, x + w - 2, y + 3, C_PANEL_HI);
        g.fill(x + 1, y + 1, x + 3, y + h - 2, C_PANEL_HI);
        g.fill(x + 3, y + h - 3, x + w - 1, y + h - 1, C_PANEL_LO);
        g.fill(x + w - 3, y + 3, x + w - 1, y + h - 1, C_PANEL_LO);
    }

    /** An 18x18 slot whose item sits at (x+1, y+1). */
    private static void slot(GuiGraphics g, int x, int y) {
        inset(g, x, y, 18, 18);
    }

    private static void inset(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, C_SLOT);
        g.fill(x, y, x + w - 1, y + 1, C_SLOT_LO);
        g.fill(x, y, x + 1, y + h - 1, C_SLOT_LO);
        g.fill(x + 1, y + h - 1, x + w, y + h, C_SLOT_HI);
        g.fill(x + w - 1, y + 1, x + w, y + h, C_SLOT_HI);
    }

    private static void scrollbar(GuiGraphics g, int x, int y, int w, int h, int row, int max) {
        inset(g, x, y, w, h);
        int thumbH = Math.min(THUMB_H, h - 2);
        int thumbY = y + 1 + (max == 0 ? 0 : (h - 2 - thumbH) * row / max);
        g.fill(x + 1, thumbY, x + w - 1, thumbY + thumbH, max == 0 ? C_THUMB_OFF : C_THUMB);
        if (max > 0) {
            g.fill(x + 1, thumbY, x + w - 1, thumbY + 1, 0xFFB8A88C);
        }
    }

    private static void arrow(GuiGraphics g, int x, int y) {
        g.fill(x, y + 6, x + 14, y + 10, C_TEXT_DIM);
        for (int i = 0; i < 8; i++) {
            g.fill(x + 14 + i, y + i, x + 15 + i, y + 16 - i, C_TEXT_DIM);
        }
    }

    // ==========================================================================================
    // Input
    // ==========================================================================================

    // ==========================================================================================
    // Recipe chooser (several recipes match the grid: pick which one, like Polymorph)
    // ==========================================================================================

    private boolean choosingRecipe;
    private static final int CHOICE_COLS = 6;

    private boolean hasRecipeChoices() {
        return menu.clientCanCraft() && menu.clientChoiceIds().size() > 1 && !menu.slots.get(RESULT_SLOT).getItem().isEmpty();
    }

    /** The little button on the result slot's corner. */
    private boolean overChooserButton(double mx, double my) {
        return hasRecipeChoices() && in(mx, my, leftPos + RESULT_X + 10, topPos + RESULT_Y - 4, 10, 9);
    }

    private int choiceRows() {
        return (menu.clientChoiceIds().size() + CHOICE_COLS - 1) / CHOICE_COLS;
    }

    private int choicePanelW() {
        return Math.min(CHOICE_COLS, menu.clientChoiceIds().size()) * 18 + 6;
    }

    private int choicePanelX() {
        int w = choicePanelW();
        return leftPos + Mth.clamp(RESULT_X + 8 - w / 2, 4, IMAGE_WIDTH - 4 - w);
    }

    private int choicePanelY() {
        return topPos + RESULT_Y - 8 - choiceRows() * 18 - 6;
    }

    /** Index of the choice under the mouse in the open chooser, or -1. */
    private int hoveredChoice(double mx, double my) {
        if (!choosingRecipe || !hasRecipeChoices()) {
            return -1;
        }
        int rx = (int) Math.floor(mx) - choicePanelX() - 3;
        int ry = (int) Math.floor(my) - choicePanelY() - 3;
        if (rx < 0 || ry < 0 || rx >= CHOICE_COLS * 18 || ry >= choiceRows() * 18) {
            return -1;
        }
        int i = (ry / 18) * CHOICE_COLS + rx / 18;
        return i < menu.clientChoiceIds().size() ? i : -1;
    }

    private boolean inChoicePanel(double mx, double my) {
        return choosingRecipe && hasRecipeChoices()
                && in(mx, my, choicePanelX(), choicePanelY(), choicePanelW(), choiceRows() * 18 + 6);
    }

    /** Draws the chooser button (and the open panel). @return true if a tooltip of its own was drawn */
    private boolean renderRecipeChooser(GuiGraphics g, int mouseX, int mouseY) {
        if (!hasRecipeChoices()) {
            choosingRecipe = false;
            return false;
        }
        g.pose().pushPose();
        g.pose().translate(0, 0, 400);
        int bx = leftPos + RESULT_X + 10;
        int by = topPos + RESULT_Y - 4;
        boolean hoverButton = overChooserButton(mouseX, mouseY);
        g.fill(bx, by, bx + 10, by + 9, C_OUTLINE);
        g.fill(bx + 1, by + 1, bx + 9, by + 8, hoverButton || choosingRecipe ? 0xFF2E6B54 : C_PANEL_HI);
        String n = Integer.toString(menu.clientChoiceIds().size());
        g.drawString(font, n, bx + 5 - font.width(n) / 2, by + 1, C_SPIRIT, false);
        if (choosingRecipe) {
            int px = choicePanelX();
            int py = choicePanelY();
            int w = choicePanelW();
            int h = choiceRows() * 18 + 6;
            panel(g, px, py, w, h);
            int hovered = hoveredChoice(mouseX, mouseY);
            List<ItemStack> outs = menu.clientChoiceOutputs();
            for (int i = 0; i < outs.size(); i++) {
                int sx = px + 3 + (i % CHOICE_COLS) * 18;
                int sy = py + 3 + (i / CHOICE_COLS) * 18;
                slot(g, sx, sy);
                if (i == menu.clientChoice()) {
                    g.fill(sx, sy, sx + 18, sy + 1, 0xFF7FE0A0);
                    g.fill(sx, sy + 17, sx + 18, sy + 18, 0xFF7FE0A0);
                    g.fill(sx, sy, sx + 1, sy + 18, 0xFF7FE0A0);
                    g.fill(sx + 17, sy, sx + 18, sy + 18, 0xFF7FE0A0);
                }
                g.renderItem(outs.get(i), sx + 1, sy + 1);
                g.renderItemDecorations(font, outs.get(i), sx + 1, sy + 1);
                if (i == hovered) {
                    renderSlotHighlight(g, sx + 1, sy + 1, 0);
                }
            }
        }
        g.pose().popPose();
        int hovered = hoveredChoice(mouseX, mouseY);
        if (hovered >= 0) {
            ItemStack out = menu.clientChoiceOutputs().get(hovered);
            List<Component> lines = new ArrayList<>(getTooltipFromContainerItem(out));
            lines.add(Component.literal(menu.clientChoiceIds().get(hovered).toString()).withStyle(ChatFormatting.DARK_GRAY));
            g.renderTooltip(font, lines, out.getTooltipImage(), out, mouseX, mouseY);
            return true;
        }
        if (hoverButton) {
            g.renderComponentTooltip(font, List.of(
                    Component.translatable("gui.tarbound.recipes.choose", menu.clientChoiceIds().size()).withStyle(ChatFormatting.AQUA),
                    Component.translatable("gui.tarbound.recipes.choose_hint").withStyle(ChatFormatting.DARK_GRAY)), mouseX, mouseY);
            return true;
        }
        return false;
    }

    /** Handles a click on the chooser button or panel. @return true if it was one */
    private boolean clickRecipeChooser(double mx, double my, int button) {
        if (!hasRecipeChoices()) {
            return false;
        }
        if (overChooserButton(mx, my)) {
            choosingRecipe = !choosingRecipe;
            return true;
        }
        if (!choosingRecipe) {
            return false;
        }
        int i = hoveredChoice(mx, my);
        if (i >= 0) {
            PacketDistributor.sendToServer(new com.tarbound.net.ChooseRecipePayload(menu.containerId, menu.clientChoiceIds().get(i)));
            choosingRecipe = false;
            return true;
        }
        if (inChoicePanel(mx, my)) {
            return true;
        }
        choosingRecipe = false; // a click anywhere else closes it (and still does what it would)
        return false;
    }

    private void send(FontActionPayload.Action action, ItemStack proto) {
        PacketDistributor.sendToServer(new FontActionPayload(menu.containerId, action, proto));
    }

    private void sendFluid(com.tarbound.net.FluidActionPayload.Action action, net.neoforged.neoforge.fluids.FluidStack fluid) {
        PacketDistributor.sendToServer(new com.tarbound.net.FluidActionPayload(menu.containerId, action, fluid));
    }

    private void sendTray(TrayActionPayload.Action action, BlockPos pos, int slot) {
        PacketDistributor.sendToServer(new TrayActionPayload(menu.containerId, action, pos, 0, slot));
    }

    @Override
    protected boolean hasClickedOutside(double mouseX, double mouseY, int guiLeft, int guiTop, int button) {
        return super.hasClickedOutside(mouseX, mouseY, guiLeft, guiTop, button)
                && !overTab(mouseX, mouseY) && !overJobsTab(mouseX, mouseY) && !overCancelAll(mouseX, mouseY) && !inPopout(mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (in(mouseX, mouseY, searchBox.getX(), searchBox.getY(), searchBox.getWidth(), searchBox.getHeight())) {
            searchBox.active = true; // see render(): inactive boxes ignore clicks
            if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                searchBox.setValue("");
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }
        if (searchBox.isFocused()) {
            setFocused(null);
        }
        boolean left = button == GLFW.GLFW_MOUSE_BUTTON_LEFT;
        boolean right = button == GLFW.GLFW_MOUSE_BUTTON_RIGHT;

        if (requesting != null) {
            handleRequestClick(mouseX, mouseY, button);
            return true;
        }
        if (clickRecipeChooser(mouseX, mouseY, button)) {
            return true;
        }
        if (overTab(mouseX, mouseY)) {
            if (left) {
                pane = pane == Pane.MACHINES ? Pane.NONE : Pane.MACHINES;
            }
            return true;
        }
        if (overJobsTab(mouseX, mouseY)) {
            if (left) {
                pane = pane == Pane.JOBS ? Pane.NONE : Pane.JOBS;
            }
            return true;
        }
        if (overCandle(mouseX, mouseY)) {
            if (left) {
                send(FontActionPayload.Action.TOGGLE_POOL_OFFERINGS, ItemStack.EMPTY);
            }
            return true;
        }
        if (overCancelAll(mouseX, mouseY)) {
            if (left) {
                clickCancelAll();
            }
            return true;
        }

        boolean middle = button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE;
        if (overGrid(mouseX, mouseY) && menu.getCarried().isEmpty()) {
            int idx = hoveredIndex(mouseX, mouseY);
            if (idx >= 0) {
                ContentsPayload.Entry e = view.get(idx);
                Craftable c = craftables.get(key(e));
                // A frozen view (shift held) can show an item that just ran out at 0: shift-clicking it
                // again shouldn't open the order panel, so ghosts open it on a plain click only.
                if (c != null && ((e.count() == 0 && !hasShiftDown()) || middle || (left && hasControlDown()))) {
                    openRequest(c);
                    return true;
                }
            }
        }
        if (overGrid(mouseX, mouseY) && (left || right)) {
            ItemStack carried = menu.getCarried();
            int fidx = hoveredIndex(mouseX, mouseY);
            ContentsPayload.Entry hoveredFluid = fidx >= 0 && view.get(fidx).isFluid() ? view.get(fidx) : null;
            if (poolView().fluids() && ScryingFontMenu.holdsFluid(carried)) {
                sendFluid(com.tarbound.net.FluidActionPayload.Action.POUR_CARRIED, net.neoforged.neoforge.fluids.FluidStack.EMPTY);
                return true;
            }
            if (hoveredFluid != null && hoveredFluid.count() > 0 && (carried.isEmpty() || ScryingFontMenu.isEmptyContainer(carried))) {
                sendFluid(hasShiftDown() ? com.tarbound.net.FluidActionPayload.Action.FILL_TO_INVENTORY
                        : com.tarbound.net.FluidActionPayload.Action.FILL, hoveredFluid.fluid());
                return true;
            }
            if (!menu.getCarried().isEmpty()) {
                send(left ? FontActionPayload.Action.INSERT_CARRIED : FontActionPayload.Action.INSERT_CARRIED_ONE, ItemStack.EMPTY);
            } else {
                int idx = hoveredIndex(mouseX, mouseY);
                if (idx >= 0) {
                    FontActionPayload.Action action = left && hasShiftDown() ? FontActionPayload.Action.TAKE_TO_INVENTORY
                            : left ? FontActionPayload.Action.TAKE_STACK
                            : FontActionPayload.Action.TAKE_HALF;
                    send(action, view.get(idx).proto());
                }
            }
            return true;
        }
        if (overScrollbar(mouseX, mouseY) && left) {
            draggingThumb = true;
            scrollToMouse(mouseY);
            return true;
        }

        if (inPopout(mouseX, mouseY)) {
            handlePopoutClick(mouseX, mouseY, left, right);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void handlePopoutClick(double mx, double my, boolean left, boolean right) {
        if (!(left || right)) {
            return;
        }
        if (pane == Pane.JOBS) {
            int job = hoveredJobCancel(mx, my);
            if (job >= 0 && left) {
                PacketDistributor.sendToServer(JobActionPayload.cancel(menu.containerId, jobRows().get(job).shown().id()));
            } else if (left) {
                int row = hoveredJobRow(mx, my);
                if (row >= 0 && jobRows().get(row).header()) {
                    int order = jobRows().get(row).order();
                    if (!openOrders.remove(order)) {
                        openOrders.add(order);
                    }
                    jobScroll = Mth.clamp(jobScroll, 0, jobMaxScroll());
                }
            }
            int toggle = hoveredStandingToggle(mx, my);
            if (toggle >= 0 && left) {
                StandingRow r = standingRows().get(toggle);
                PacketDistributor.sendToServer(new StandingTogglePayload(menu.containerId, r.machine().pos(), r.view().slot()));
            }
            return;
        }
        int row = hoveredRow(mx, my);
        if (row >= 0) {
            TrayPayload.Machine m = machines().get(row);
            if (right && hasShiftDown()) {
                // Shift-right-click a header: highlight the machine in the world, or stop if it already is.
                if (m.pos().equals(MachineHighlight.target())) {
                    MachineHighlight.clear();
                } else {
                    MachineHighlight.show(m.pos());
                }
                return;
            }
            if (left) {
                selectedPos = m.pos();
                sendTray(TrayActionPayload.Action.SELECT, m.pos(), 0);
            }
            return;
        }
        SlotHit hit = hoveredMachineSlot(mx, my);
        if (hit != null) {
            // Using a machine's slot also selects it (shift-clicks from your inventory go there).
            if (!hit.machine().pos().equals(selectedPos)) {
                selectedPos = hit.machine().pos();
                sendTray(TrayActionPayload.Action.SELECT, selectedPos, 0);
            }
            TrayActionPayload.Action action = left && hasShiftDown() ? TrayActionPayload.Action.QUICK_MOVE
                    : left ? TrayActionPayload.Action.LEFT
                    : TrayActionPayload.Action.RIGHT;
            sendTray(action, hit.machine().pos(), hit.slot());
        }
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (draggingThumb) {
            scrollToMouse(mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (draggingThumb && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            draggingThumb = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void scrollToMouse(double mouseY) {
        int max = maxScroll();
        if (max == 0) {
            scrollRow = 0;
            return;
        }
        double t = (mouseY - (topPos + SCROLL_Y + 1) - THUMB_H / 2.0) / (SCROLL_H - 2 - THUMB_H);
        scrollRow = Mth.clamp((int) Math.round(t * max), 0, max);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY != 0) {
            int dir = (int) Math.signum(scrollY);
            if (requesting != null && in(mouseX, mouseY, reqX(), reqY(), REQ_W, REQ_H)) {
                if (mouseY >= reqY() + PLAN_Y - 2) {
                    planScroll = Mth.clamp(planScroll - dir, 0, Math.max(0, planLines.size() - PLAN_ROWS));
                } else {
                    requestAmount = Mth.clamp(requestAmount + dir * scrollStep(), 1, maxRequest());
                }
                return true;
            }
            if (overGrid(mouseX, mouseY) || overScrollbar(mouseX, mouseY)) {
                int step = hasShiftDown() ? GRID_ROWS : 1;
                scrollRow = Mth.clamp(scrollRow - dir * step, 0, maxScroll());
                return true;
            }
            if (requesting != null) {
                requestAmount = Mth.clamp(requestAmount + dir * scrollStep(), 1, maxRequest());
                return true;
            }
            if (inPopout(mouseX, mouseY) && pane == Pane.JOBS) {
                if (my(mouseY) < STAND_HDR_Y) {
                    jobScroll = Mth.clamp(jobScroll - dir, 0, jobMaxScroll());
                } else {
                    standingScroll = Mth.clamp(standingScroll - dir, 0, standingMaxScroll());
                }
                return true;
            }
            if (inPopout(mouseX, mouseY)) {
                int step = hasShiftDown() ? VIEW_H : SCROLL_STEP;
                machineScroll = Mth.clamp(machineScroll - dir * step, 0, machineMaxScroll());
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private int my(double mouseY) {
        return (int) mouseY - topPos;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (requesting != null && (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_ENTER)) {
            if (keyCode == GLFW.GLFW_KEY_ENTER && requestReady()) {
                startRequest();
            }
            requesting = null;
            return true;
        }
        if (searchBox.isFocused()) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                setFocused(null);
                return true;
            }
            // Swallow everything else so typing "e" doesn't close the screen.
            searchBox.keyPressed(keyCode, scanCode, modifiers);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
