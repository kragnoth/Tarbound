package com.tarbound.menu;

import com.tarbound.net.ContentsPayload;
import com.tarbound.net.FluidActionPayload;
import com.tarbound.network.FluidKey;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.fluids.FluidActionResult;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.capabilities.Capabilities;
import com.tarbound.net.FontActionPayload;
import com.tarbound.net.TrayActionPayload;
import com.tarbound.net.TrayPayload;
import com.tarbound.network.BoundInventory;
import com.tarbound.Tarbound;
import com.tarbound.network.ItemKey;
import com.tarbound.network.MachineLoader;
import com.tarbound.network.SpiritFire;
import com.tarbound.block.ScryingFontBlockEntity;
import com.tarbound.block.ReliquaryBlockEntity;
import com.tarbound.contract.ContractData;
import com.tarbound.contract.ContractJob;
import com.tarbound.network.TarNetwork;
import com.tarbound.registry.ModBlocks;
import com.tarbound.registry.ModMenus;
import java.util.ArrayList;
import net.minecraft.resources.ResourceLocation;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Scrying Font menu. Three parts, two of them virtual (driven by packets, not slots):
 * <ul>
 *   <li>the pool: everything in bound storage blocks, scrollable and searchable;</li>
 *   <li>the tray: each bound block, and for machines the slots behind each relative face;</li>
 *   <li>a real 3x3 crafting grid and the player inventory.</li>
 * </ul>
 *
 * <p>Slot layout: 0 = craft result, 1-9 = craft grid, 10-36 = player inventory, 37-45 = hotbar.
 */
public class ScryingFontMenu extends AbstractContainerMenu {
    // ---- Layout (shared with the screen) ----
    public static final int IMAGE_WIDTH = 194;
    public static final int IMAGE_HEIGHT = 256;
    public static final int GRID_X = 8;
    public static final int GRID_Y = 18;
    public static final int GRID_COLS = 9;
    public static final int GRID_ROWS = 5;
    public static final int CRAFT_X = 26;
    public static final int CRAFT_Y = 115;
    public static final int RESULT_X = 122;
    public static final int RESULT_Y = 133;
    public static final int INV_X = 8;
    public static final int INV_Y = 174;
    public static final int HOTBAR_Y = 232;

    public static final int RESULT_SLOT = 0;
    public static final int GRID_START = 1;
    public static final int GRID_END = 10;
    public static final int INV_START = 10;
    public static final int HOTBAR_END = 46;

    /** Ticks between automatic rescans/resyncs while the menu is open. */
    private static final int SYNC_INTERVAL = 10;

    private final ContainerLevelAccess access;
    private final BlockPos fontPos;
    /** Opened through a Spirit Board rather than at the radio. */
    private final boolean remote;
    private final Player player;
    private final CraftingContainer craftSlots = new TransientCraftingContainer(this, 3, 3);
    private final ResultContainer resultSlots = new ResultContainer();

    // Server state
    private TarNetwork cachedNetwork = TarNetwork.EMPTY;
    private long cachedAt = Long.MIN_VALUE;
    // Not Long.MIN_VALUE: "now - lastSyncAt" would overflow to a negative number and never pass a check.
    private long lastSyncAt = -1_000_000L;
    private boolean dirty = true;
    /** Last pool sent: {@link ItemKey} → count and {@link FluidKey} → mB. */
    private Map<Object, Long> lastSentCounts;
    private int lastSentInventories = -1;
    private TrayPayload lastSentTray;
    private boolean lastCanCraft;
    /** The tray view the player last clicked; shift-clicked inventory items go there first. */
    @Nullable
    private BlockPos selectedPos;

    // Client state
    private List<ContentsPayload.Entry> clientEntries = List.of();
    private int clientInventories;
    /** Pages of the contents packet received so far (applied together when the last arrives). */
    private final List<ContentsPayload.Entry> pendingEntries = new ArrayList<>();
    private int pendingPage = -1;
    /** The pool as the client knows it, kept up to date by full sends and deltas. */
    private final Map<Object, Long> clientCounts = new java.util.LinkedHashMap<>();
    private List<ContentsPayload.Entry> clientFluidEntries = List.of();
    /** A full send has arrived, so deltas have something to apply to. */
    private boolean clientHasBase;
    private int clientVersion;
    private boolean clientCanCraft;
    private TrayPayload clientTray;
    private int clientTrayVersion;
    @Nullable
    private BlockPos clientSelection;

    public ScryingFontMenu(int containerId, Inventory inventory, ContainerLevelAccess access, BlockPos fontPos) {
        this(containerId, inventory, access, fontPos, false);
    }

    /** @param remote opened from a Spirit Board: valid while the player carries a board that reaches the radio */
    public ScryingFontMenu(int containerId, Inventory inventory, ContainerLevelAccess access, BlockPos fontPos, boolean remote) {
        super(ModMenus.SCRYING_FONT.get(), containerId);
        this.remote = remote;
        this.access = access;
        this.fontPos = fontPos.immutable();
        this.player = inventory.player;

        addSlot(new FontResultSlot(this, inventory.player, craftSlots, resultSlots, 0, RESULT_X, RESULT_Y));
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                addSlot(new Slot(craftSlots, col + row * 3, CRAFT_X + col * 18, CRAFT_Y + row * 18));
            }
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, INV_X + col * 18, INV_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, INV_X + col * 18, HOTBAR_Y));
        }

        // Spirit store, synced as container data (vanilla sends each value as 16 bits, so ints use two).
        if (inventory.player.level().isClientSide) {
            spiritData = new net.minecraft.world.inventory.SimpleContainerData(SPIRIT_DATA);
        } else {
            spiritData = new net.minecraft.world.inventory.ContainerData() {
                @Override
                public int get(int index) {
                    ScryingFontBlockEntity font = fontEntity();
                    int spirit = font == null ? 0 : font.spirit();
                    int cap = com.tarbound.spirit.Spirit.capacity();
                    return switch (index) {
                        case 0 -> spirit & 0xFFFF;
                        case 1 -> spirit >>> 16;
                        case 2 -> cap & 0xFFFF;
                        case 3 -> cap >>> 16;
                        case 4 -> font != null && font.burnFromPool() ? 1 : 0;
                        default -> com.tarbound.spirit.Spirit.costs() ? 1 : 0;
                    };
                }

                @Override
                public void set(int index, int value) {}

                @Override
                public int getCount() {
                    return SPIRIT_DATA;
                }
            };
        }
        addDataSlots(spiritData);
    }

    private static final int SPIRIT_DATA = 6;
    private final net.minecraft.world.inventory.ContainerData spiritData;

    /** Client: Spirit in the radio's store. */
    public int clientSpirit() {
        return (spiritData.get(0) & 0xFFFF) | (spiritData.get(1) << 16);
    }

    /** Client: the store's capacity. */
    public int clientSpiritCapacity() {
        return Math.max(1, (spiritData.get(2) & 0xFFFF) | (spiritData.get(3) << 16));
    }

    /** Client: the candle button (burn offerings from the pool) is on. */
    public boolean clientBurnFromPool() {
        return spiritData.get(4) != 0;
    }

    /** Client: Spirit costs are on (server config); with them off the meter is hidden. */
    public boolean clientSpiritCosts() {
        return spiritData.get(5) != 0;
    }

    public static ScryingFontMenu fromNetwork(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        return new ScryingFontMenu(containerId, inventory, ContainerLevelAccess.NULL, buf.readBlockPos());
    }

    public BlockPos fontPos() {
        return fontPos;
    }

    // ------------------------------------------------------------------------------------------
    // Network access (server only)
    // ------------------------------------------------------------------------------------------

    /**
     * The tar network: the radio's own (kept until something changes, see ScryingFontBlockEntity#network), or, with
     * the radio's chunk loaded but no block entity, one scanned at most once per tick.
     */
    private TarNetwork network() {
        Level level = player.level();
        if (level.isClientSide) {
            return TarNetwork.EMPTY;
        }
        ScryingFontBlockEntity font = fontEntity();
        if (font != null) {
            return font.network(); // shared with the radio and any other open screens this tick
        }
        if (!level.isLoaded(fontPos)) {
            return TarNetwork.EMPTY; // the radio's chunk is gone: nothing to read (and don't load it)
        }
        long now = level.getGameTime();
        if (now != cachedAt) {
            cachedNetwork = TarNetwork.scan(level, fontPos);
            cachedAt = now;
        }
        return cachedNetwork;
    }

    private void invalidate() {
        cachedAt = Long.MIN_VALUE;
        ScryingFontBlockEntity font = fontEntity();
        if (font != null) {
            font.invalidateNetwork();
        }
        dirty = true;
    }

    /** A Spirit Board screen whose radio ran out of Spirit: closes on the next validity check. */
    private boolean boardStarved;
    /** Last time the board paid (not Long.MIN_VALUE: "now - boardPaidAt" would overflow and never charge). */
    private long boardPaidAt = -1_000_000L;

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (player instanceof ServerPlayer serverPlayer) {
            long now = serverPlayer.level().getGameTime();
            if (remote && !boardStarved && now - boardPaidAt >= 20) {
                // A Spirit Board connection costs Spirit every second it stays open.
                ScryingFontBlockEntity font = fontEntity();
                boardPaidAt = now;
                if (font != null && !font.pay(com.tarbound.config.TarboundConfig.SERVER.boardCostPerSecond.get())) {
                    boardStarved = true;
                    serverPlayer.displayClientMessage(Component.translatable("message.tarbound.board.hungry")
                            .withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE), true);
                }
            }
            if (dirty || now - lastSyncAt >= SYNC_INTERVAL) {
                dirty = false;
                lastSyncAt = now;
                syncContents(serverPlayer);
            }
        }
    }

    /**
     * Whether this player's client can take Tarbound's screen payloads. Always true for a real player (they can't join
     * without the mod); false for a fake or mock player (machines, test harnesses), which must never be sent them.
     */
    public static boolean canReceive(ServerPlayer serverPlayer) {
        if (serverPlayer instanceof net.neoforged.neoforge.common.util.FakePlayer || serverPlayer.connection == null) {
            return false; // a machine's player: no client, and its stand-in connection has no channel to ask
        }
        try {
            return serverPlayer.connection.hasChannel(TrayPayload.TYPE.id());
        } catch (RuntimeException e) {
            return false;
        }
    }

    private void syncContents(ServerPlayer serverPlayer) {
        if (!canReceive(serverPlayer)) {
            return;
        }
        TarNetwork net = network();

        if (net.hasCraftingTable() != lastCanCraft) {
            lastCanCraft = net.hasCraftingTable();
            updateCraftingResult(serverPlayer);
            lastSentCounts = null; // force a contents packet carrying the new flag
        }

        TrayPayload tray = buildTray(net);
        if (!tray.sameAs(lastSentTray)) {
            lastSentTray = tray;
            PacketDistributor.sendToPlayer(serverPlayer, tray);
        }

        Map<Object, Long> counts = new java.util.LinkedHashMap<>(net.countContents());
        counts.putAll(net.countFluids());
        int inventories = net.storage().size() + net.tanks().size();
        if (counts.equals(lastSentCounts) && inventories == lastSentInventories) {
            return;
        }
        Map<Object, Long> previous = lastSentCounts;
        lastSentCounts = counts;
        lastSentInventories = inventories;
        if (previous == null) {
            sendContentsPaged(serverPlayer, counts, false, inventories, net.hasCraftingTable());
            return;
        }
        // Only what changed: new or different counts, and 0 for item types that are gone.
        Map<Object, Long> changes = new java.util.LinkedHashMap<>();
        for (Map.Entry<Object, Long> e : counts.entrySet()) {
            if (!e.getValue().equals(previous.get(e.getKey()))) {
                changes.put(e.getKey(), e.getValue());
            }
        }
        for (Object gone : previous.keySet()) {
            if (!counts.containsKey(gone)) {
                changes.put(gone, 0L);
            }
        }
        if (changes.size() * 2 > counts.size() + 16) {
            sendContentsPaged(serverPlayer, counts, false, inventories, net.hasCraftingTable()); // most things changed
        } else {
            sendContentsPaged(serverPlayer, changes, true, inventories, net.hasCraftingTable());
        }
    }

    /**
     * Sends the pool split into pages of at most {@link ContentsPayload#PAGE_BYTES} encoded bytes, so a
     * huge storage (or items carrying lots of data) can't push one packet over the network limit.
     */
    private void sendContentsPaged(ServerPlayer serverPlayer, Map<Object, Long> counts, boolean delta, int inventories, boolean canCraft) {
        List<List<ContentsPayload.Entry>> pages = new ArrayList<>();
        List<ContentsPayload.Entry> page = new ArrayList<>();
        int pageBytes = 0;
        RegistryFriendlyByteBuf sizer = new RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), serverPlayer.registryAccess());
        try {
            for (Map.Entry<Object, Long> e : counts.entrySet()) {
                sizer.clear();
                ContentsPayload.Entry entry;
                if (e.getKey() instanceof FluidKey fk) {
                    FluidStack.STREAM_CODEC.encode(sizer, fk.proto());
                    entry = ContentsPayload.Entry.ofFluid(fk.proto(), e.getValue());
                } else {
                    ItemKey ik = (ItemKey) e.getKey();
                    ItemStack.STREAM_CODEC.encode(sizer, ik.proto());
                    entry = new ContentsPayload.Entry(ik.proto(), e.getValue());
                }
                int bytes = sizer.readableBytes() + 11;
                if (bytes > ContentsPayload.PAGE_BYTES) {
                    if (delta) {
                        continue; // never shown in the first place
                    }
                    Tarbound.LOGGER.warn("Not showing {} in the pool: its data alone is {} bytes", e.getKey(), bytes);
                    continue;
                }
                if (pageBytes + bytes > ContentsPayload.PAGE_BYTES && !page.isEmpty()) {
                    pages.add(page);
                    page = new ArrayList<>();
                    pageBytes = 0;
                }
                page.add(entry);
                pageBytes += bytes;
            }
        } finally {
            sizer.release();
        }
        pages.add(page); // the last (or only, possibly empty) page
        int total = Math.min(pages.size(), ContentsPayload.MAX_PAGES);
        for (int i = 0; i < total; i++) {
            PacketDistributor.sendToPlayer(serverPlayer, new ContentsPayload(containerId, i, total, delta, pages.get(i), inventories, canCraft));
        }
    }

    private TrayPayload buildTray(TarNetwork net) {
        Level level = player.level();
        List<TrayPayload.Machine> machines = new ArrayList<>(net.machines().size());
        for (BoundInventory b : net.machines()) {
            List<BoundInventory.SlotRef> refs = b.slots();
            List<ItemStack> slots = new ArrayList<>(refs.size());
            byte[] roles = new byte[refs.size()];
            for (int i = 0; i < refs.size(); i++) {
                BoundInventory.SlotRef r = refs.get(i);
                slots.add(r.stack().copy());
                roles[i] = (byte) b.role(i).ordinal();
            }
            BlockEntity be = level.getBlockEntity(b.pos());
            List<ContractData> contracts = List.of();
            String name = "";
            List<TrayPayload.StandingView> standing = List.of();
            if (com.tarbound.block.ReliquaryBlock.reliquaryFor(level, b.pos()) instanceof ReliquaryBlockEntity reliquary) {
                contracts = reliquary.contractSlots();
                name = reliquary.customNameString();
                standing = reliquary.standingSlots().stream()
                        .map(s -> new TrayPayload.StandingView(s.slot(), s.order().tag().location().toString(), s.order().count(), s.paused()))
                        .toList();
            }
            List<TrayPayload.Tank> tanks = new ArrayList<>();
            if (!b.isStation()) {
                var fh = com.tarbound.network.MachineAccess.fluids(level, b.pos());
                for (int t = 0; fh != null && t < fh.getTanks() && t < TrayPayload.Tank.MAX; t++) {
                    tanks.add(new TrayPayload.Tank(fh.getFluidInTank(t).copy(), fh.getTankCapacity(t)));
                }
            }
            machines.add(new TrayPayload.Machine(b.pos(), b.state(), slots, roles, be != null && SpiritFire.feedsNow(level, b.pos(), be), contracts, name,
                    standing, tanks));
        }
        List<TrayPayload.JobView> jobs = new ArrayList<>();
        ScryingFontBlockEntity font = fontEntity();
        if (font != null) {
            for (ContractJob job : font.jobs()) {
                com.tarbound.contract.CraftOrder order = job.orderId() == 0 ? null : font.order(job.orderId());
                TrayPayload.Wait wait = new TrayPayload.Wait(job.waitItem().copy(), job.waitFluid().copy(), job.waitFeeder(), job.refusal());
                jobs.add((order == null
                        ? new TrayPayload.JobView(job.id(), job.contract().primaryOutput().copy(), job.contract().trackedFluid().copy(),
                                job.totalOps(), job.pushedOps(), job.received(), job.expected(), (byte) job.status().ordinal())
                        : new TrayPayload.JobView(job.id(), job.contract().primaryOutput().copy(), job.contract().trackedFluid().copy(),
                                job.totalOps(), job.pushedOps(), job.received(), job.expected(), (byte) job.status().ordinal(),
                                order.id(), !job.deliverToShade(), order.output().copy(), order.fluid().copy(), order.amount())).withWait(wait));
            }
        }
        ScryingFontBlockEntity shadeFont = fontEntity();
        int freeShades = shadeFont == null ? 0 : com.tarbound.block.ShadeBlockEntity.bestFree(player.level(), shadeFont);
        return new TrayPayload(containerId, net.outOfRange(), net.unbound(), freeShades,
                font == null ? com.tarbound.config.TarboundConfig.SERVER.maxPlanDepth.get() : font.planDepth(),
                font == null ? com.tarbound.config.TarboundConfig.SERVER.maxPlanNodes.get() : font.planNodes(),
                net.hasWeepingSeep(), machines, jobs);
    }

    /**
     * The radio's block entity, if its chunk is loaded. Checked first because getBlockEntity on an
     * unloaded chunk loads it synchronously, which a Spirit Board screen would do every tick. A remote
     * radio needs its chunk kept loaded (a chunk loader) to stay reachable.
     */
    @Nullable
    private ScryingFontBlockEntity fontEntity() {
        Level level = player.level();
        return level.isLoaded(fontPos) && level.getBlockEntity(fontPos) instanceof ScryingFontBlockEntity be ? be : null;
    }

    // ------------------------------------------------------------------------------------------
    // Contract jobs
    // ------------------------------------------------------------------------------------------

    public void requestJob(ServerPlayer serverPlayer, BlockPos machinePos, int contractSlot, int ops) {
        ScryingFontBlockEntity font = fontEntity();
        TarNetwork net = network();
        BoundInventory machine = net.find(machinePos);
        if (font == null || machine == null || machine.kind() != BoundInventory.Kind.MACHINE
                || !(com.tarbound.block.ReliquaryBlock.reliquaryFor(player.level(), machinePos) instanceof ReliquaryBlockEntity reliquary)) {
            serverPlayer.displayClientMessage(Component.translatable("gui.tarbound.fill.no_machine"), true);
            return;
        }
        List<ContractData> slots = reliquary.contractSlots();
        if (contractSlot < 0 || contractSlot >= slots.size() || slots.get(contractSlot) == null) {
            return;
        }
        ContractData contract = slots.get(contractSlot);
        if (!contract.orderable()) {
            return;
        }
        int clamped = Math.max(1, Math.min(ops, ScryingFontBlockEntity.maxOps()));
        if (font.workInUse() >= font.jobLimit()) {
            serverPlayer.displayClientMessage(Component.translatable("gui.tarbound.job.too_many", font.jobLimit()), true);
            return;
        }
        if (ScryingFontBlockEntity.maxOps(contract, net.countForJobs(contract.inputs()), net.countFluids()) < clamped) {
            serverPlayer.displayClientMessage(Component.translatable("gui.tarbound.job.short"), true);
            return;
        }
        font.addJob(contract, clamped).requestedBy(serverPlayer.getUUID());
        com.tarbound.TarboundAdvancements.award(serverPlayer, com.tarbound.TarboundAdvancements.FIRST_ORDER);
        dirty = true;
        broadcastChanges();
    }

    /**
     * Server: plan and start an order. The ingredients storage is short of are crafted first through
     * other contracts (each becomes its own job, queued ahead of the ones it feeds); the plan is made
     * again here from the server's own counts, never trusted from the client.
     */
    public void orderPlan(ServerPlayer serverPlayer, BlockPos machinePos, int contractSlot, int ops) {
        ScryingFontBlockEntity font = fontEntity();
        TarNetwork net = network();
        if (font == null || !(com.tarbound.block.ReliquaryBlock.reliquaryFor(player.level(), machinePos) instanceof ReliquaryBlockEntity reliquary)) {
            serverPlayer.displayClientMessage(Component.translatable("gui.tarbound.fill.no_machine"), true);
            return;
        }
        List<ContractData> slots = reliquary.contractSlots();
        if (contractSlot < 0 || contractSlot >= slots.size() || slots.get(contractSlot) == null || !slots.get(contractSlot).orderable()) {
            return;
        }
        ContractData contract = slots.get(contractSlot);
        int clamped = Math.max(1, Math.min(ops, ScryingFontBlockEntity.maxOps()));
        List<com.tarbound.contract.CraftPlanner.Source> sources = planSources(net);
        List<List<ItemStack>> allInputs = new ArrayList<>();
        for (var s : sources) {
            allInputs.addAll(s.contract().inputs());
        }
        com.tarbound.contract.CraftPlanner.Plan plan = com.tarbound.contract.CraftPlanner.plan(
                new com.tarbound.contract.CraftPlanner.Source(contract, Component.empty()), clamped, sources,
                net.countForJobs(allInputs), net.countFluids(),
                font.planDepth(), font.planNodes());
        if (!plan.complete()) {
            serverPlayer.displayClientMessage(Component.translatable(plan.truncated() ? "gui.tarbound.plan.too_big" : "gui.tarbound.job.short"), true);
            return;
        }
        if (font.workInUse() + 1 > font.jobLimit()) {
            serverPlayer.displayClientMessage(Component.translatable("gui.tarbound.job.too_many", font.jobLimit()), true);
            return;
        }
        if (plan.steps().size() > 1) {
            // Sub-crafts: a Shade has to watch over the order, holding its stock so nothing else takes it.
            int steps = plan.steps().size();
            com.tarbound.block.ShadeBlockEntity shade = com.tarbound.block.ShadeBlockEntity.freeFor(player.level(), font, steps);
            if (shade == null) {
                int best = com.tarbound.block.ShadeBlockEntity.bestFree(player.level(), font);
                serverPlayer.displayClientMessage(best == 0 ? Component.translatable("gui.tarbound.order.no_shade")
                        : Component.translatable("gui.tarbound.order.weak_shade", steps, com.tarbound.block.ShadeBlockEntity.rankFor(steps)), true);
                return;
            }
            if (!font.startOrder(serverPlayer.getUUID(), shade, plan, net)) {
                serverPlayer.displayClientMessage(Component.translatable("gui.tarbound.job.short"), true);
                return;
            }
        } else {
            for (var step : plan.steps()) {
                font.addJob(step.contract(), Math.max(1, Math.min(step.ops(), ScryingFontBlockEntity.maxOps()))).requestedBy(serverPlayer.getUUID());
            }
        }
        com.tarbound.TarboundAdvancements.award(serverPlayer, com.tarbound.TarboundAdvancements.FIRST_ORDER);
        dirty = true;
        broadcastChanges();
    }

    /** Every contract on this radio's Reliquaries, once each, for the planner. */
    private List<com.tarbound.contract.CraftPlanner.Source> planSources(TarNetwork net) {
        ScryingFontBlockEntity font = fontEntity();
        return font == null ? List.of() : font.planSources(net);
    }

    /** Server: pause or resume the standing contract in a slot of the reliquary on a bound machine. */
    public void toggleStanding(BlockPos machinePos, int slot) {
        BoundInventory machine = network().find(machinePos);
        if (machine != null && machine.kind() == BoundInventory.Kind.MACHINE
                && com.tarbound.block.ReliquaryBlock.reliquaryFor(player.level(), machinePos) instanceof ReliquaryBlockEntity reliquary) {
            reliquary.togglePaused(slot);
            dirty = true;
            broadcastChanges();
        }
    }

    public void cancelJob(int jobId) {
        ScryingFontBlockEntity font = fontEntity();
        if (font != null) {
            if (jobId == com.tarbound.net.JobActionPayload.ALL) {
                font.cancelAllJobs();
            } else {
                font.cancelJob(jobId);
            }
            dirty = true;
            broadcastChanges();
        }
    }

    // ------------------------------------------------------------------------------------------
    // Client view
    // ------------------------------------------------------------------------------------------

    public void receiveTray(TrayPayload payload) {
        clientTray = payload;
        clientTrayVersion++;
    }

    /** Last tray state from the server; null until the first sync arrives. */
    @Nullable
    public TrayPayload clientTray() {
        return clientTray;
    }

    public int clientTrayVersion() {
        return clientTrayVersion;
    }

    /** The block selected in the tray (client side), so recipe viewers can prefer it. */
    @Nullable
    public BlockPos clientSelection() {
        return clientSelection;
    }

    public void setClientSelection(@Nullable BlockPos pos) {
        clientSelection = pos;
    }

    public void receiveContents(ContentsPayload payload) {
        if (payload.page() == 0) {
            pendingEntries.clear();
            pendingPage = -1;
        }
        if (payload.page() != pendingPage + 1) {
            pendingEntries.clear(); // lost track of a page sequence: wait for the next full one
            pendingPage = -1;
            return;
        }
        pendingEntries.addAll(payload.entries());
        pendingPage = payload.page();
        if (payload.page() < payload.pages() - 1) {
            return; // more pages coming
        }
        if (!payload.delta()) {
            clientCounts.clear();
            clientHasBase = true;
        } else if (!clientHasBase) {
            pendingEntries.clear(); // a delta with nothing to apply it to; the server always sends full first
            pendingPage = -1;
            return;
        }
        for (ContentsPayload.Entry e : pendingEntries) {
            Object key = e.isFluid() ? FluidKey.of(e.fluid()) : ItemKey.of(e.proto());
            if (e.count() > 0) {
                clientCounts.put(key, e.count());
            } else {
                clientCounts.remove(key);
            }
        }
        List<ContentsPayload.Entry> entries = new ArrayList<>(clientCounts.size());
        List<ContentsPayload.Entry> fluids = new ArrayList<>();
        for (Map.Entry<Object, Long> e : clientCounts.entrySet()) {
            if (e.getKey() instanceof FluidKey fk) {
                fluids.add(ContentsPayload.Entry.ofFluid(fk.proto(), e.getValue()));
            } else {
                entries.add(new ContentsPayload.Entry(((ItemKey) e.getKey()).proto(), e.getValue()));
            }
        }
        clientEntries = List.copyOf(entries);
        clientFluidEntries = List.copyOf(fluids);
        pendingEntries.clear();
        pendingPage = -1;
        clientInventories = payload.inventories();
        clientCanCraft = payload.canCraft();
        clientVersion++;
    }

    /** Item entries in the pool (no fluids). */
    public List<ContentsPayload.Entry> clientEntries() {
        return clientEntries;
    }

    /** Fluid entries in the pool, amounts in mB. */
    public List<ContentsPayload.Entry> clientFluidEntries() {
        return clientFluidEntries;
    }

    public int clientInventories() {
        return clientInventories;
    }

    /** A crafting table touches the tar, so the grid can craft. */
    public boolean clientCanCraft() {
        return clientCanCraft;
    }

    /** Bumped whenever new contents arrive, so the screen knows to re-sort/filter. */
    public int clientVersion() {
        return clientVersion;
    }

    // ------------------------------------------------------------------------------------------
    // Actions from the network view
    // ------------------------------------------------------------------------------------------

    /** Each alternative sent by the client, as the pool's own version of it (see {@link TarNetwork#resolve}). */
    private List<List<ItemStack>> resolveAll(List<List<ItemStack>> lists) {
        TarNetwork net = network();
        List<List<ItemStack>> out = new ArrayList<>(lists.size());
        for (List<ItemStack> alts : lists) {
            out.add(alts.stream().map(net::resolve).toList());
        }
        return out;
    }

    public void handleAction(ServerPlayer serverPlayer, FontActionPayload.Action action, ItemStack proto) {
        TarNetwork net = network();
        ItemStack carried = getCarried();
        proto = net.resolve(proto); // the screen's copy may have picked up components the stored item lacks

        switch (action) {
            case TAKE_STACK, TAKE_HALF -> {
                if (proto.isEmpty() || (!carried.isEmpty() && !ItemStack.isSameItemSameComponents(carried, proto))) {
                    return;
                }
                int max = proto.getMaxStackSize();
                int want;
                if (action == FontActionPayload.Action.TAKE_STACK) {
                    want = max;
                } else {
                    long available = net.countContents().getOrDefault(ItemKey.of(proto), 0L);
                    want = (int) ((Math.min(available, max) + 1) / 2);
                }
                want = Math.min(want, max - carried.getCount());
                if (want <= 0) {
                    return;
                }
                ItemStack got = net.extract(proto, want);
                if (got.isEmpty()) {
                    return;
                }
                if (carried.isEmpty()) {
                    setCarried(got);
                } else {
                    carried.grow(got.getCount());
                    setCarried(carried);
                }
            }
            case TAKE_TO_INVENTORY -> {
                if (proto.isEmpty()) {
                    return;
                }
                int room = roomInInventory(serverPlayer.getInventory(), proto);
                if (room <= 0) {
                    return;
                }
                ItemStack got = net.extract(proto, Math.min(room, proto.getMaxStackSize()));
                if (!got.isEmpty()) {
                    serverPlayer.getInventory().add(got);
                    if (!got.isEmpty()) {
                        // Shouldn't happen given the room check, but never void items.
                        ItemStack rest = net.insert(got);
                        if (!rest.isEmpty()) {
                            serverPlayer.drop(rest, false);
                        }
                    }
                }
            }
            case INSERT_CARRIED -> {
                if (!carried.isEmpty()) {
                    setCarried(net.insert(carried));
                }
            }
            case INSERT_CARRIED_ONE -> {
                if (!carried.isEmpty()) {
                    ItemStack rest = net.insert(carried.copyWithCount(1));
                    if (rest.isEmpty()) {
                        carried.shrink(1);
                        setCarried(carried.isEmpty() ? ItemStack.EMPTY : carried);
                    }
                }
            }
            case CLEAR_GRID -> clearGridToNetwork(serverPlayer);
            case TOGGLE_POOL_OFFERINGS -> {
                ScryingFontBlockEntity font = fontEntity();
                if (font != null) {
                    font.toggleBurnFromPool();
                }
            }
        }

        dirty = true;
        broadcastChanges();
    }

    // ------------------------------------------------------------------------------------------
    // Fluids from the network view
    // ------------------------------------------------------------------------------------------

    /** An item that holds fluid but is empty right now (an empty bucket). */
    public static boolean isEmptyContainer(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        ItemStack one = stack.copyWithCount(1);
        return FluidUtil.getFluidHandler(one).isPresent() && FluidUtil.getFluidContained(one).isEmpty();
    }

    /** An item holding some fluid (a water bucket). */
    public static boolean holdsFluid(ItemStack stack) {
        return !stack.isEmpty() && FluidUtil.getFluidContained(stack.copyWithCount(1)).isPresent();
    }

    public void handleFluidAction(ServerPlayer serverPlayer, FluidActionPayload.Action action, FluidStack fluid) {
        TarNetwork net = network();
        ItemStack carried = getCarried();
        switch (action) {
            case FILL, FILL_TO_INVENTORY -> {
                if (fluid.isEmpty()) {
                    return;
                }
                boolean toInventory = action == FluidActionPayload.Action.FILL_TO_INVENTORY;
                boolean useCarried = isEmptyContainer(carried);
                if (!useCarried && !carried.isEmpty() && !toInventory) {
                    return; // the cursor is busy with something else
                }
                ItemStack container = useCarried ? carried.copyWithCount(1) : net.extract(new ItemStack(Items.BUCKET), 1);
                if (container.isEmpty()) {
                    serverPlayer.displayClientMessage(Component.translatable("gui.tarbound.fluid.no_bucket"), true);
                    return;
                }
                FluidActionResult result = FluidUtil.tryFillContainer(container, net.source(fluid), Integer.MAX_VALUE, serverPlayer, true);
                if (!result.isSuccess()) {
                    if (!useCarried) {
                        giveBackToPool(serverPlayer, net, container);
                    }
                    serverPlayer.displayClientMessage(Component.translatable("gui.tarbound.fluid.cant_fill", fluid.getHoverName()), true);
                    return;
                }
                ItemStack filled = result.getResult();
                if (useCarried) {
                    carried.shrink(1);
                    setCarried(carried.isEmpty() ? ItemStack.EMPTY : carried);
                }
                if (!toInventory && getCarried().isEmpty()) {
                    setCarried(filled);
                } else if (!serverPlayer.getInventory().add(filled) || !filled.isEmpty()) {
                    serverPlayer.drop(filled, false);
                }
            }
            case POUR_CARRIED -> {
                if (!holdsFluid(carried)) {
                    return;
                }
                FluidActionResult result = FluidUtil.tryEmptyContainer(carried.copyWithCount(1), net.sink(), Integer.MAX_VALUE, serverPlayer, true);
                if (!result.isSuccess()) {
                    serverPlayer.displayClientMessage(Component.translatable("gui.tarbound.fluid.no_room"), true);
                    return;
                }
                ItemStack emptied = result.getResult();
                carried.shrink(1);
                if (carried.isEmpty()) {
                    setCarried(emptied);
                } else {
                    setCarried(carried);
                    if (!emptied.isEmpty() && (!serverPlayer.getInventory().add(emptied) || !emptied.isEmpty())) {
                        serverPlayer.drop(emptied, false);
                    }
                }
            }
        }
        dirty = true;
        broadcastChanges();
    }

    private static void giveBackToPool(ServerPlayer serverPlayer, TarNetwork net, ItemStack stack) {
        ItemStack rest = net.insert(stack);
        if (!rest.isEmpty() && !serverPlayer.getInventory().add(rest)) {
            serverPlayer.drop(rest, false);
        }
    }

    public void handleTrayAction(ServerPlayer serverPlayer, TrayActionPayload p) {
        TarNetwork net = network();
        if (p.action() == TrayActionPayload.Action.SELECT) {
            selectedPos = p.view() < 0 ? null : p.pos();
            return;
        }
        BoundInventory b = net.find(p.pos());
        if (b == null || b.kind() != BoundInventory.Kind.MACHINE || !b.alive() || p.slot() < 0 || p.slot() >= b.slots().size()) {
            return;
        }
        BoundInventory.SlotRef ref = b.slots().get(p.slot());
        IItemHandler h = ref.handler();
        int slot = ref.slot();
        ItemStack inSlot = h.getStackInSlot(slot);
        ItemStack carried = getCarried();

        switch (p.action()) {
            case LEFT -> {
                if (carried.isEmpty()) {
                    if (!inSlot.isEmpty()) {
                        setCarried(h.extractItem(slot, inSlot.getMaxStackSize(), false));
                    }
                } else {
                    setCarried(h.insertItem(slot, carried, false));
                }
            }
            case RIGHT -> {
                if (carried.isEmpty()) {
                    if (!inSlot.isEmpty()) {
                        setCarried(h.extractItem(slot, (inSlot.getCount() + 1) / 2, false));
                    }
                } else if (h.insertItem(slot, carried.copyWithCount(1), false).isEmpty()) {
                    carried.shrink(1);
                    setCarried(carried.isEmpty() ? ItemStack.EMPTY : carried);
                }
            }
            case QUICK_MOVE -> {
                if (inSlot.isEmpty()) {
                    return;
                }
                ItemStack got = h.extractItem(slot, inSlot.getMaxStackSize(), false);
                ItemStack rest = net.insert(got);
                if (!rest.isEmpty()) {
                    serverPlayer.getInventory().add(rest);
                }
                if (!rest.isEmpty()) {
                    rest = h.insertItem(slot, rest, false);
                }
                if (!rest.isEmpty()) {
                    serverPlayer.drop(rest, false);
                }
            }
            default -> {
                return;
            }
        }
        dirty = true;
        broadcastChanges();
    }

    /**
     * Recipe-viewer fill: clear the grid into the pool, then place one ingredient per slot (or as many
     * crafts' worth as possible when {@code max}), drawing from the pool first and then the player.
     */
    public void fillGrid(ServerPlayer serverPlayer, List<List<ItemStack>> wanted, boolean max) {
        wanted = resolveAll(wanted);
        TarNetwork net = network();
        if (!net.hasCraftingTable()) {
            serverPlayer.displayClientMessage(Component.translatable("gui.tarbound.no_crafting_table"), true);
            return;
        }
        Inventory inventory = serverPlayer.getInventory();

        for (int i = 0; i < craftSlots.getContainerSize(); i++) {
            ItemStack stack = craftSlots.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            ItemStack rest = net.insert(stack);
            if (!rest.isEmpty()) {
                inventory.add(rest);
            }
            craftSlots.setItem(i, rest.isEmpty() ? ItemStack.EMPTY : rest);
            if (!rest.isEmpty()) {
                return; // nowhere to put the old grid; leave it alone
            }
        }

        Map<ItemKey, Long> available = net.countWithFillsFor(wanted); // filled buckets made from empty ones and the tanks count
        for (ItemStack s : inventory.items) {
            if (!s.isEmpty()) {
                available.merge(ItemKey.of(s), (long) s.getCount(), Long::sum);
            }
        }

        wanted = com.tarbound.network.LooseMatch.widen(wanted, available.keySet()); // a used flint and steel fills a plain one's slot
        int size = Math.min(wanted.size(), craftSlots.getContainerSize());
        ItemKey[] chosen = new ItemKey[size];
        Map<ItemKey, Integer> uses = new HashMap<>();
        for (int i = 0; i < size; i++) {
            ItemKey best = null;
            long bestLeft = 0;
            boolean bestPrecious = true;
            for (ItemStack alt : wanted.get(i)) {
                if (alt.isEmpty()) {
                    continue;
                }
                ItemKey k = ItemKey.of(alt);
                long left = available.getOrDefault(k, 0L) - uses.getOrDefault(k, 0);
                if (left <= 0) {
                    continue;
                }
                boolean precious = com.tarbound.network.LooseMatch.isPrecious(alt); // an enchanted tool only if nothing plain is left
                if (best == null || (bestPrecious && !precious) || (precious == bestPrecious && left > bestLeft)) {
                    best = k;
                    bestLeft = left;
                    bestPrecious = precious;
                }
            }
            if (best != null) {
                chosen[i] = best;
                uses.merge(best, 1, Integer::sum);
            }
        }

        int crafts = 1;
        if (max && !uses.isEmpty()) {
            long limit = Integer.MAX_VALUE;
            for (Map.Entry<ItemKey, Integer> e : uses.entrySet()) {
                limit = Math.min(limit, available.getOrDefault(e.getKey(), 0L) / e.getValue());
                limit = Math.min(limit, e.getKey().proto().getMaxStackSize());
            }
            crafts = (int) Math.max(1, limit);
        }

        for (int i = 0; i < size; i++) {
            if (chosen[i] == null) {
                continue;
            }
            ItemStack proto = chosen[i].proto();
            int got = net.extract(proto, crafts).getCount();
            if (got < crafts) {
                got += takeFromInventory(inventory, proto, crafts - got);
            }
            if (got > 0) {
                craftSlots.setItem(i, proto.copyWithCount(got));
            }
        }
        dirty = true;
        broadcastChanges();
    }

    // ------------------------------------------------------------------------------------------
    // Machine fill (recipe viewers): one operation, loaded the way a player would by hand
    // ------------------------------------------------------------------------------------------

    /**
     * Loads one operation of a recipe into a bound machine (see {@link MachineLoader}), drawing on the
     * pool first and then the player's inventory. Deliberately lossy until machine spirits run jobs.
     */
    public void fillMachine(ServerPlayer serverPlayer, BlockPos pos, List<List<ItemStack>> inputs, List<FluidStack> fluids) {
        inputs = resolveAll(inputs);
        TarNetwork net = network();
        BoundInventory machine = net.find(pos);
        if (machine == null || machine.kind() != BoundInventory.Kind.MACHINE) {
            serverPlayer.displayClientMessage(Component.translatable("gui.tarbound.fill.no_machine"), true);
            return;
        }
        Inventory inventory = serverPlayer.getInventory();
        MachineLoader.Source playerSource = new MachineLoader.Source() {
            @Override
            public Map<ItemKey, Long> snapshot(List<List<ItemStack>> wanted) {
                Map<ItemKey, Long> available = snapshot();
                return TarNetwork.withFills(available, net.countFluids(), TarNetwork.flatten(wanted));
            }

            @Override
            public long fluidAmount(FluidStack fluid) {
                return net.fluidAmount(fluid);
            }

            @Override
            public int drainFluid(FluidStack fluid, int amount) {
                return net.drainFluid(fluid, amount, false);
            }

            @Override
            public void giveBackFluid(FluidStack fluid) {
                net.insertFluid(fluid, false);
            }

            @Override
            public Map<ItemKey, Long> snapshot() {
                Map<ItemKey, Long> available = new HashMap<>(net.countContents());
                for (ItemStack s : inventory.items) {
                    if (!s.isEmpty()) {
                        available.merge(ItemKey.of(s), (long) s.getCount(), Long::sum);
                    }
                }
                return available;
            }

            @Override
            public int take(ItemStack proto, int amount) {
                int got = net.extract(proto, amount).getCount();
                if (got < amount) {
                    got += takeFromInventory(inventory, proto, amount - got);
                }
                return got;
            }

            @Override
            public void giveBack(ItemStack stack) {
                ItemStack rest = net.insert(stack);
                if (!rest.isEmpty()) {
                    inventory.add(rest);
                }
                if (!rest.isEmpty()) {
                    serverPlayer.drop(rest, false);
                }
            }
        };
        ScryingFontBlockEntity loadingFont = fontEntity();
        Component error = MachineLoader.load(serverPlayer.level(), machine, inputs, fluids,
                loadingFont != null ? loadingFont.recordLoads(machine.pos(), playerSource) : playerSource);
        if (error != null) {
            serverPlayer.displayClientMessage(error, true);
        }
        dirty = true;
        broadcastChanges();
    }

    private static int takeFromInventory(Inventory inventory, ItemStack proto, int amount) {
        int taken = 0;
        for (int i = 0; i < inventory.items.size() && taken < amount; i++) {
            ItemStack s = inventory.items.get(i);
            if (!s.isEmpty() && ItemStack.isSameItemSameComponents(s, proto)) {
                int n = Math.min(amount - taken, s.getCount());
                s.shrink(n);
                taken += n;
            }
        }
        if (taken > 0) {
            inventory.setChanged();
        }
        return taken;
    }

    /** How many of {@code proto} the main inventory (hotbar + 27 slots) can still accept. */
    private static int roomInInventory(Inventory inventory, ItemStack proto) {
        int room = 0;
        int max = proto.getMaxStackSize();
        for (int i = 0; i < inventory.items.size(); i++) {
            ItemStack s = inventory.items.get(i);
            if (s.isEmpty()) {
                room += max;
            } else if (ItemStack.isSameItemSameComponents(s, proto)) {
                room += Math.max(0, Math.min(max, s.getMaxStackSize()) - s.getCount());
            }
            if (room >= max) {
                return max;
            }
        }
        return room;
    }

    private void clearGridToNetwork(ServerPlayer serverPlayer) {
        TarNetwork net = network();
        for (int i = 0; i < craftSlots.getContainerSize(); i++) {
            ItemStack stack = craftSlots.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            ItemStack rest = net.insert(stack);
            if (!rest.isEmpty()) {
                serverPlayer.getInventory().add(rest);
            }
            craftSlots.setItem(i, rest.isEmpty() ? ItemStack.EMPTY : rest);
        }
    }

    /**
     * After a craft consumes the grid, top each emptied slot back up from the network with the
     * same item it held before, so repeated (and shift-) crafting keeps going.
     */
    void refillGrid(ItemStack[] before) {
        if (player.level().isClientSide) {
            return;
        }
        TarNetwork net = network();
        if (net.isEmpty()) {
            return;
        }
        for (int i = 0; i < before.length; i++) {
            if (!before[i].isEmpty() && craftSlots.getItem(i).isEmpty()) {
                ItemStack got = net.extract(before[i], 1);
                if (!got.isEmpty()) {
                    craftSlots.setItem(i, got);
                }
            }
        }
        dirty = true;
    }

    // ------------------------------------------------------------------------------------------
    // Crafting
    // ------------------------------------------------------------------------------------------

    /** The recipe the result slot is showing, so batch crafts don't search all recipes per craft. */
    @Nullable
    private RecipeHolder<CraftingRecipe> lastRecipe;
    /** A batch craft is running: grid changes don't each trigger a recipe search. */
    private boolean batching;

    /**
     * Result-slot shortcuts, done server side in one go:
     * <ul>
     *   <li>shift-click: craft into the inventory, up to {@code shiftCraftStacks} stacks (default one),
     *       instead of vanilla's craft-until-the-inventory-is-full;</li>
     *   <li>right-click: craft as much as the grid and pool allow, up to one stack, onto the cursor;</li>
     *   <li>left-click: one craft, as always.</li>
     * </ul>
     * Vanilla's shift-click loop runs a full recipe search after every single craft; here the recipe
     * found for the result is reused (one {@code matches} check per craft) and the search runs once at
     * the end.
     */
    @Override
    public void clicked(int slotId, int button, net.minecraft.world.inventory.ClickType type, Player clicker) {
        boolean shift = type == net.minecraft.world.inventory.ClickType.QUICK_MOVE;
        boolean right = type == net.minecraft.world.inventory.ClickType.PICKUP && button == 1;
        if (slotId == RESULT_SLOT && (shift || right)) {
            if (clicker instanceof ServerPlayer sp) {
                batchCraft(sp, shift);
            }
            return; // the client waits for the server's result rather than predicting it
        }
        super.clicked(slotId, button, type, clicker);
    }

    private void batchCraft(ServerPlayer sp, boolean toInventory) {
        ItemStack shown = resultSlots.getItem(0).copy();
        if (shown.isEmpty() || lastRecipe == null) {
            return;
        }
        int max = shown.getMaxStackSize();
        int limit;
        if (toInventory) {
            limit = max * com.tarbound.config.TarboundConfig.SERVER.shiftCraftStacks.get();
        } else {
            ItemStack carried = getCarried();
            if (!carried.isEmpty() && !ItemStack.isSameItemSameComponents(carried, shown)) {
                return;
            }
            limit = max - carried.getCount();
        }
        Level level = sp.level();
        Slot result = slots.get(RESULT_SLOT);
        int made = 0;
        batching = true;
        try {
            while (true) {
                ItemStack next = resultSlots.getItem(0);
                if (next.isEmpty() || !ItemStack.isSameItemSameComponents(next, shown)) {
                    break;
                }
                int per = next.getCount();
                if (made + per > limit || (toInventory && roomInInventory(sp.getInventory(), next) < per)) {
                    break;
                }
                ItemStack got = result.safeTake(per, per, sp); // uses up the grid, refills it from the pool
                if (got.isEmpty()) {
                    break;
                }
                made += got.getCount();
                if (toInventory) {
                    moveItemStackTo(got, INV_START, HOTBAR_END, true);
                    if (!got.isEmpty()) {
                        sp.drop(got, false);
                    }
                } else {
                    ItemStack carried = getCarried();
                    if (carried.isEmpty()) {
                        setCarried(got);
                    } else {
                        carried.grow(got.getCount());
                        setCarried(carried);
                    }
                }
                // The next result from the recipe we already have: no search across every recipe.
                CraftingInput input = craftSlots.asCraftInput();
                ItemStack again = lastRecipe.value().matches(input, level)
                        ? lastRecipe.value().assemble(input, level.registryAccess()) : ItemStack.EMPTY;
                resultSlots.setItem(0, again);
            }
        } finally {
            batching = false;
        }
        updateCraftingResult(sp);
        dirty = true;
        broadcastChanges();
    }

    @Override
    public void slotsChanged(Container container) {
        if (batching) {
            return;
        }
        if (container == craftSlots && player instanceof ServerPlayer serverPlayer) {
            updateCraftingResult(serverPlayer);
        }
    }

    /** The recipe the player picked among several the grid matches (kept while it still matches). */
    @Nullable
    private ResourceLocation chosenRecipe;
    /** What the client was last told about recipe choices, so it's only resent when it changes. */
    private List<ResourceLocation> sentChoices = List.of();
    @Nullable
    private ResourceLocation sentChoice;

    private void sendRecipeChoices(ServerPlayer serverPlayer, List<RecipeHolder<CraftingRecipe>> matches, @Nullable RecipeHolder<CraftingRecipe> shown) {
        List<ResourceLocation> ids = matches.size() > 1 ? matches.stream().map(RecipeHolder::id).toList() : List.of();
        ResourceLocation shownId = shown == null ? null : shown.id();
        if (ids.equals(sentChoices) && java.util.Objects.equals(shownId, sentChoice)) {
            return;
        }
        sentChoices = ids;
        sentChoice = shownId;
        CraftingInput input = craftSlots.asCraftInput();
        List<ItemStack> outputs = new ArrayList<>(ids.size());
        int selected = -1;
        for (int i = 0; i < ids.size(); i++) {
            outputs.add(matches.get(i).value().assemble(input, serverPlayer.level().registryAccess()));
            if (ids.get(i).equals(shownId)) {
                selected = i;
            }
        }
        if (canReceive(serverPlayer)) {
            PacketDistributor.sendToPlayer(serverPlayer, new com.tarbound.net.RecipeChoicesPayload(containerId, ids, outputs, selected));
        }
    }

    /** Server: the player picked one of the matching recipes. */
    public void chooseRecipe(ServerPlayer serverPlayer, ResourceLocation id) {
        if (sentChoices.contains(id)) {
            chosenRecipe = id;
            updateCraftingResult(serverPlayer);
        }
    }

    // Client side of the recipe chooser.
    private List<ResourceLocation> clientChoiceIds = List.of();
    private List<ItemStack> clientChoiceOutputs = List.of();
    private int clientChoice = -1;

    public void receiveRecipeChoices(com.tarbound.net.RecipeChoicesPayload payload) {
        clientChoiceIds = List.copyOf(payload.ids());
        clientChoiceOutputs = List.copyOf(payload.outputs());
        clientChoice = payload.selected();
    }

    /** Client: the recipes the grid matches when there's more than one (else empty). */
    public List<ResourceLocation> clientChoiceIds() {
        return clientChoiceIds;
    }

    public List<ItemStack> clientChoiceOutputs() {
        return clientChoiceOutputs;
    }

    public int clientChoice() {
        return clientChoice;
    }

    private void updateCraftingResult(ServerPlayer serverPlayer) {
        Level level = serverPlayer.level();
        CraftingInput input = craftSlots.asCraftInput();
        ItemStack result = ItemStack.EMPTY;
        lastRecipe = null;
        if (!network().hasCraftingTable()) {
            // No crafting table touches the tar: the grid holds items but won't craft.
            sendRecipeChoices(serverPlayer, List.of(), null);
            resultSlots.setItem(0, result);
            setRemoteSlot(RESULT_SLOT, result);
            serverPlayer.connection.send(new ClientboundContainerSetSlotPacket(containerId, incrementStateId(), RESULT_SLOT, result));
            return;
        }
        // Every recipe these ingredients match: when there's more than one, the player picks (as with
        // Polymorph), and the pick holds while it still matches.
        List<RecipeHolder<CraftingRecipe>> matches = new ArrayList<>(level.getServer().getRecipeManager()
                .getRecipesFor(RecipeType.CRAFTING, input, level));
        matches.removeIf(h -> !h.value().assemble(input, level.registryAccess()).isItemEnabled(level.enabledFeatures())
                || h.value().assemble(input, level.registryAccess()).isEmpty());
        Optional<RecipeHolder<CraftingRecipe>> recipe = matches.stream()
                .filter(h -> h.id().equals(chosenRecipe)).findFirst()
                .or(() -> level.getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level))
                .filter(matches::contains)
                .or(() -> matches.stream().findFirst());
        sendRecipeChoices(serverPlayer, matches, recipe.orElse(null));
        if (recipe.isPresent()) {
            RecipeHolder<CraftingRecipe> holder = recipe.get();
            if (resultSlots.setRecipeUsed(level, serverPlayer, holder)) {
                ItemStack assembled = holder.value().assemble(input, level.registryAccess());
                if (assembled.isItemEnabled(level.enabledFeatures())) {
                    result = assembled;
                    lastRecipe = holder;
                }
            }
        }
        resultSlots.setItem(0, result);
        setRemoteSlot(RESULT_SLOT, result);
        serverPlayer.connection.send(new ClientboundContainerSetSlotPacket(containerId, incrementStateId(), RESULT_SLOT, result));
    }

    // ------------------------------------------------------------------------------------------
    // Vanilla menu plumbing
    // ------------------------------------------------------------------------------------------

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (slot == null || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();

        if (index == RESULT_SLOT) {
            access.execute((level, pos) -> stack.getItem().onCraftedBy(stack, level, player));
            if (!moveItemStackTo(stack, INV_START, HOTBAR_END, true)) {
                return ItemStack.EMPTY;
            }
            slot.onQuickCraft(stack, original);
            if (stack.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
            if (stack.getCount() == original.getCount()) {
                return ItemStack.EMPTY;
            }
            slot.onTake(player, stack);
            return original;
        }

        // Everything else goes to/through the network, which only the server can see.
        if (player.level().isClientSide) {
            return ItemStack.EMPTY;
        }

        if (index < GRID_END) {
            // Crafting grid -> network, then player inventory.
            ItemStack rest = network().insert(stack);
            if (!rest.isEmpty()) {
                moveItemStackTo(rest, INV_START, HOTBAR_END, false);
            }
            slot.setByPlayer(rest.isEmpty() ? ItemStack.EMPTY : rest);
        } else {
            // Player inventory -> the machine the player last clicked (input slots, then fuel), then the pool.
            ItemStack rest = stack.copy();
            if (selectedPos != null) {
                BoundInventory sel = network().find(selectedPos);
                if (sel != null && sel.kind() == BoundInventory.Kind.MACHINE && sel.alive()) {
                    rest = insertIntoMachine(sel, rest);
                }
            }
            rest = network().insert(rest);
            slot.setByPlayer(rest.isEmpty() ? ItemStack.EMPTY : rest);
        }
        dirty = true;
        // Returning EMPTY stops vanilla from looping the shift-click.
        return ItemStack.EMPTY;
    }

    private static ItemStack insertIntoMachine(BoundInventory machine, ItemStack stack) {
        ItemStack rest = stack;
        for (BoundInventory.Role pass : new BoundInventory.Role[]{BoundInventory.Role.INPUT, BoundInventory.Role.FUEL}) {
            List<BoundInventory.SlotRef> refs = machine.slots();
            for (int i = 0; i < refs.size(); i++) {
                if (rest.isEmpty()) {
                    return ItemStack.EMPTY;
                }
                if (machine.role(i) == pass) {
                    BoundInventory.SlotRef r = refs.get(i);
                    rest = r.handler().insertItem(r.slot(), rest, false);
                }
            }
        }
        return rest;
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
        return slot.container != resultSlots && super.canTakeItemForPickAll(stack, slot);
    }

    @Override
    public boolean stillValid(Player player) {
        if (remote) {
            // Board check first: it tests the chunk is loaded before touching the block entity.
            return !boardStarved && com.tarbound.item.SpiritBoardItem.carriesBoardFor(player, fontPos) && fontEntity() != null;
        }
        return stillValid(access, player, ModBlocks.SCRYING_FONT.get());
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (player.level().isClientSide) {
            return;
        }
        // Return the grid to the network first; whatever doesn't fit goes back to the player.
        invalidate();
        TarNetwork net = network();
        boolean gone = !player.isAlive() || (player instanceof ServerPlayer sp && sp.hasDisconnected());
        for (int i = 0; i < craftSlots.getContainerSize(); i++) {
            ItemStack stack = craftSlots.removeItemNoUpdate(i);
            if (stack.isEmpty()) {
                continue;
            }
            ItemStack rest = net.insert(stack);
            if (rest.isEmpty()) {
                continue;
            }
            if (gone) {
                player.drop(rest, false);
            } else {
                player.getInventory().placeItemBackInInventory(rest);
            }
        }
    }
}
