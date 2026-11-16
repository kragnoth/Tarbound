package com.tarbound.net;

import com.tarbound.menu.ScribingLecternMenu;
import com.tarbound.menu.ScryingFontMenu;
import com.tarbound.menu.ReliquaryMenu;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class TarboundPayloads {
    private static final String VERSION = "21";

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(VERSION);
        registrar.playToClient(ContentsPayload.TYPE, ContentsPayload.STREAM_CODEC, TarboundPayloads::handleContents);
        registrar.playToClient(RecipeChoicesPayload.TYPE, RecipeChoicesPayload.STREAM_CODEC, TarboundPayloads::handleRecipeChoices);
        registrar.playToServer(ChooseRecipePayload.TYPE, ChooseRecipePayload.STREAM_CODEC, TarboundPayloads::handleChooseRecipe);
        registrar.playToClient(TrayPayload.TYPE, TrayPayload.STREAM_CODEC, TarboundPayloads::handleTray);
        registrar.playToServer(FontActionPayload.TYPE, FontActionPayload.STREAM_CODEC, TarboundPayloads::handleAction);
        registrar.playToServer(FluidActionPayload.TYPE, FluidActionPayload.STREAM_CODEC, TarboundPayloads::handleFluidAction);
        registrar.playToServer(TrayActionPayload.TYPE, TrayActionPayload.STREAM_CODEC, TarboundPayloads::handleTrayAction);
        registrar.playToServer(FillGridPayload.TYPE, FillGridPayload.STREAM_CODEC, TarboundPayloads::handleFillGrid);
        registrar.playToServer(MachineFillPayload.TYPE, MachineFillPayload.STREAM_CODEC, TarboundPayloads::handleMachineFill);
        registrar.playToServer(OrderPlanPayload.TYPE, OrderPlanPayload.STREAM_CODEC, TarboundPayloads::handleOrderPlan);
        registrar.playToServer(JobActionPayload.TYPE, JobActionPayload.STREAM_CODEC, TarboundPayloads::handleJobAction);
        registrar.playToServer(WriteContractPayload.TYPE, WriteContractPayload.STREAM_CODEC, TarboundPayloads::handleWriteContract);
        registrar.playToServer(AntennaSettingsPayload.TYPE, AntennaSettingsPayload.STREAM_CODEC, TarboundPayloads::handleAntennaSettings);
        registrar.playToServer(StandingTogglePayload.TYPE, StandingTogglePayload.STREAM_CODEC, TarboundPayloads::handleStandingToggle);
        registrar.playToServer(RenameReliquaryPayload.TYPE, RenameReliquaryPayload.STREAM_CODEC, TarboundPayloads::handleRenameReliquary);
        registrar.playToServer(GeneralizeContractPayload.TYPE, GeneralizeContractPayload.STREAM_CODEC, TarboundPayloads::handleGeneralize);
        registrar.playToServer(WriteCraftingGridPayload.TYPE, WriteCraftingGridPayload.STREAM_CODEC, TarboundPayloads::handleWriteCraftingGrid);
        registrar.playToServer(BindingsRequestPayload.TYPE, BindingsRequestPayload.STREAM_CODEC, TarboundPayloads::handleBindingsRequest);
        registrar.playToClient(StewardStatePayload.TYPE, StewardStatePayload.STREAM_CODEC, (payload, context) -> {
            if (context.player().containerMenu instanceof com.tarbound.menu.StewardMenu menu && menu.containerId == payload.containerId()) {
                menu.receive(payload);
            }
        });
        registrar.playToServer(StewardEditPayload.TYPE, StewardEditPayload.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player && player.containerMenu instanceof com.tarbound.menu.StewardMenu menu
                    && menu.containerId == payload.containerId()) {
                menu.edit(player, payload.row(), payload.item(), payload.keep());
            }
        });
        registrar.playToClient(BindingsPayload.TYPE, BindingsPayload.STREAM_CODEC, (payload, context) -> ClientBindings.receive(payload));
    }

    /** When each player last asked for a radio's bindings (game time), so a held crouch can't flood the server. */
    private static final java.util.Map<java.util.UUID, Long> LAST_BINDINGS_REQUEST = new java.util.HashMap<>();

    private static void handleBindingsRequest(BindingsRequestPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        net.minecraft.world.level.Level level = player.level();
        net.minecraft.core.BlockPos pos = payload.font();
        long now = level.getGameTime();
        Long last = LAST_BINDINGS_REQUEST.get(player.getUUID());
        if (last != null && now >= last && now - last < 10) {
            return;
        }
        if (!level.isLoaded(pos) || player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(pos)) > 24 * 24
                || !(level.getBlockEntity(pos) instanceof com.tarbound.block.ScryingFontBlockEntity font)) {
            return;
        }
        LAST_BINDINGS_REQUEST.put(player.getUUID(), now);
        com.tarbound.network.TarNetwork network = font.network();
        java.util.List<net.minecraft.core.BlockPos> storage = new java.util.ArrayList<>();
        java.util.List<net.minecraft.core.BlockPos> machines = new java.util.ArrayList<>();
        for (com.tarbound.network.BoundInventory b : network.bound()) {
            java.util.List<net.minecraft.core.BlockPos> into = b.kind() == com.tarbound.network.BoundInventory.Kind.STORAGE ? storage : machines;
            if (into.size() < BindingsPayload.MAX) {
                into.add(b.pos());
            }
        }
        java.util.List<net.minecraft.core.BlockPos> tanks = network.tanks().stream().map(com.tarbound.network.FluidStore::pos)
                .limit(BindingsPayload.MAX).toList();
        java.util.List<net.minecraft.core.BlockPos> unbound = network.unboundAt().stream().limit(BindingsPayload.MAX).toList();
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, new BindingsPayload(pos, storage, machines, tanks, unbound));
    }

    // All handlers run on the main thread (PayloadRegistrar's default), so touching menus directly is safe.

    private static void handleContents(ContentsPayload payload, IPayloadContext context) {
        if (context.player().containerMenu instanceof ScryingFontMenu menu && menu.containerId == payload.containerId()) {
            menu.receiveContents(payload);
        }
    }

    private static void handleRecipeChoices(RecipeChoicesPayload payload, IPayloadContext context) {
        if (context.player().containerMenu instanceof ScryingFontMenu menu && menu.containerId == payload.containerId()) {
            menu.receiveRecipeChoices(payload);
        }
    }

    private static void handleChooseRecipe(ChooseRecipePayload payload, IPayloadContext context) {
        ScryingFontMenu menu = serverMenu(context, payload.containerId());
        if (menu != null) {
            menu.chooseRecipe((ServerPlayer) context.player(), payload.recipe());
        }
    }

    private static void handleTray(TrayPayload payload, IPayloadContext context) {
        if (context.player().containerMenu instanceof ScryingFontMenu menu && menu.containerId == payload.containerId()) {
            menu.receiveTray(payload);
        }
    }

    private static ScryingFontMenu serverMenu(IPayloadContext context, int containerId) {
        if (context.player() instanceof ServerPlayer player
                && player.containerMenu instanceof ScryingFontMenu menu
                && menu.containerId == containerId
                && menu.stillValid(player)) {
            return menu;
        }
        return null;
    }

    private static void handleAction(FontActionPayload payload, IPayloadContext context) {
        ScryingFontMenu menu = serverMenu(context, payload.containerId());
        if (menu != null) {
            menu.handleAction((ServerPlayer) context.player(), payload.action(), payload.proto());
        }
    }

    private static void handleFluidAction(FluidActionPayload payload, IPayloadContext context) {
        ScryingFontMenu menu = serverMenu(context, payload.containerId());
        if (menu != null) {
            menu.handleFluidAction((ServerPlayer) context.player(), payload.action(), payload.fluid());
        }
    }

    private static void handleTrayAction(TrayActionPayload payload, IPayloadContext context) {
        ScryingFontMenu menu = serverMenu(context, payload.containerId());
        if (menu != null) {
            menu.handleTrayAction((ServerPlayer) context.player(), payload);
        }
    }

    private static void handleFillGrid(FillGridPayload payload, IPayloadContext context) {
        ScryingFontMenu menu = serverMenu(context, payload.containerId());
        if (menu != null) {
            menu.fillGrid((ServerPlayer) context.player(), payload.slots(), payload.max());
        }
    }

    private static void handleMachineFill(MachineFillPayload payload, IPayloadContext context) {
        ScryingFontMenu menu = serverMenu(context, payload.containerId());
        if (menu != null) {
            menu.fillMachine((ServerPlayer) context.player(), payload.machine(), payload.inputs(), payload.fluids());
        }
    }

    private static void handleOrderPlan(OrderPlanPayload payload, IPayloadContext context) {
        ScryingFontMenu menu = serverMenu(context, payload.containerId());
        if (menu != null) {
            menu.orderPlan((ServerPlayer) context.player(), payload.machine(), payload.contractSlot(), payload.ops());
        }
    }

    private static void handleJobAction(JobActionPayload payload, IPayloadContext context) {
        ScryingFontMenu menu = serverMenu(context, payload.containerId());
        if (menu == null) {
            return;
        }
        if (payload.cancel()) {
            menu.cancelJob(payload.jobId());
        } else {
            menu.requestJob((ServerPlayer) context.player(), payload.machine(), payload.contractSlot(), payload.ops());
        }
    }

    private static void handleWriteContract(WriteContractPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player
                && player.containerMenu instanceof ScribingLecternMenu menu
                && menu.containerId == payload.containerId()
                && menu.stillValid(player)) {
            menu.write(player, payload.contract(), payload.craftingRecipe());
        }
    }

    /** The antenna each player last opened settings for, as seen by the server (so it passed claim checks). */
    private static final java.util.Map<java.util.UUID, net.minecraft.core.BlockPos> ANTENNA_SETTINGS = new java.util.HashMap<>();

    /** Server: {@code player} shift-right-clicked the antenna at {@code pos}, so may change its settings. */
    public static void allowAntennaSettings(net.minecraft.world.entity.player.Player player, net.minecraft.core.BlockPos pos) {
        ANTENNA_SETTINGS.put(player.getUUID(), pos.immutable());
    }

    /** Forget per-player state when they leave, so the maps don't grow with every player who ever joined. */
    public static void onLoggedOut(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        java.util.UUID id = event.getEntity().getUUID();
        LAST_BINDINGS_REQUEST.remove(id);
        ANTENNA_SETTINGS.remove(id);
    }

    private static void handleAntennaSettings(AntennaSettingsPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player
                && payload.pos().equals(ANTENNA_SETTINGS.get(player.getUUID()))
                && player.level().isLoaded(payload.pos())
                && player.mayInteract(player.level(), payload.pos())
                && player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(payload.pos())) <= 64.0) {
            com.tarbound.block.TarAntennaBlock.applySettings(player.level(), payload.pos(), payload.mode(), payload.priority());
        }
    }

    private static void handleStandingToggle(StandingTogglePayload payload, IPayloadContext context) {
        ScryingFontMenu menu = serverMenu(context, payload.containerId());
        if (menu != null) {
            menu.toggleStanding(payload.machine(), payload.slot());
        }
    }

    private static void handleRenameReliquary(RenameReliquaryPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player
                && player.containerMenu instanceof ReliquaryMenu menu
                && menu.containerId == payload.containerId()
                && menu.stillValid(player)) {
            menu.rename(payload.name());
        }
    }

    private static void handleGeneralize(GeneralizeContractPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player
                && player.containerMenu instanceof ScribingLecternMenu menu
                && menu.containerId == payload.containerId()
                && menu.stillValid(player)) {
            menu.generalize(player, payload.tag());
        }
    }

    private static void handleWriteCraftingGrid(WriteCraftingGridPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player
                && player.containerMenu instanceof ScribingLecternMenu menu
                && menu.containerId == payload.containerId()
                && menu.stillValid(player)) {
            menu.writeCrafting(player, payload.grid(), payload.substitutes());
        }
    }

    private TarboundPayloads() {}
}
