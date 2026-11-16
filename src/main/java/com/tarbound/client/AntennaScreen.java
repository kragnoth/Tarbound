package com.tarbound.client;

import com.tarbound.block.TarAntennaBlock;
import com.tarbound.block.TarAntennaBlockEntity;
import com.tarbound.net.AntennaSettingsPayload;
import com.tarbound.network.PoolAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * A Tar Antenna's settings (shift-right-click with an empty hand): its mode (Commune / Scry / Tithe)
 * and the pool priority of inventories touching it. Changes apply at once.
 */
public class AntennaScreen extends Screen {
    private static final int W = 220;
    private static final int H = 120;

    private final BlockPos pos;
    private final BlockPos base;
    private PoolAccess mode;
    private int priority;
    private final Button[] modeButtons = new Button[PoolAccess.values().length];

    private AntennaScreen(BlockPos pos, BlockPos base, PoolAccess mode, int priority) {
        super(Component.translatable("block.tarbound.tar_antenna"));
        this.pos = pos;
        this.base = base;
        this.mode = mode;
        this.priority = priority;
    }

    /** Opens the screen for the antenna at {@code pos} (client only). */
    public static void open(BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        BlockState state = mc.level.getBlockState(pos);
        if (!(state.getBlock() instanceof TarAntennaBlock)) {
            return;
        }
        BlockPos base = TarAntennaBlock.baseOf(mc.level, pos, state);
        int priority = mc.level.getBlockEntity(base) instanceof TarAntennaBlockEntity be ? be.priority() : 0;
        mc.setScreen(new AntennaScreen(pos, base, state.getValue(TarAntennaBlock.MODE), priority));
    }

    @Override
    protected void init() {
        int left = (width - W) / 2;
        int top = (height - H) / 2;
        int bw = 66;
        PoolAccess[] modes = PoolAccess.values();
        for (int i = 0; i < modes.length; i++) {
            PoolAccess m = modes[i];
            modeButtons[i] = addRenderableWidget(Button.builder(Component.translatable("mode.tarbound." + m.getSerializedName()), b -> {
                mode = m;
                send();
            }).bounds(left + 6 + i * (bw + 4), top + 30, bw, 20).build());
        }
        int y = top + 70;
        addRenderableWidget(Button.builder(Component.literal("-10"), b -> bump(-10)).bounds(left + 10, y, 30, 20).build());
        addRenderableWidget(Button.builder(Component.literal("-1"), b -> bump(-1)).bounds(left + 42, y, 24, 20).build());
        addRenderableWidget(Button.builder(Component.literal("+1"), b -> bump(1)).bounds(left + W - 66, y, 24, 20).build());
        addRenderableWidget(Button.builder(Component.literal("+10"), b -> bump(10)).bounds(left + W - 40, y, 30, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(left + (W - 80) / 2, top + H - 24, 80, 18).build());
        refreshButtons();
    }

    private void bump(int d) {
        priority = Math.max(TarAntennaBlockEntity.MIN_PRIORITY, Math.min(TarAntennaBlockEntity.MAX_PRIORITY, priority + d));
        send();
    }

    private void send() {
        PacketDistributor.sendToServer(new AntennaSettingsPayload(pos, mode, priority));
        refreshButtons();
    }

    private void refreshButtons() {
        PoolAccess[] modes = PoolAccess.values();
        for (int i = 0; i < modes.length; i++) {
            if (modeButtons[i] != null) {
                modeButtons[i].active = modes[i] != mode;
            }
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        int left = (width - W) / 2;
        int top = (height - H) / 2;
        g.drawCenteredString(font, title, width / 2, top + 6, 0xFFCDBB9A);
        g.drawCenteredString(font, Component.translatable("gui.tarbound.antenna.mode"), width / 2, top + 18, 0xFF8C7D66);
        g.drawCenteredString(font, Component.translatable("mode.tarbound." + mode.getSerializedName() + ".desc"), width / 2, top + 54,
                0xFF8C7D66);
        g.drawCenteredString(font, Component.translatable("gui.tarbound.antenna.priority", (priority > 0 ? "+" : "") + priority),
                width / 2, top + 76, 0xFFF0E4C8);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        int left = (width - W) / 2;
        int top = (height - H) / 2;
        g.fill(left - 1, top - 1, left + W + 1, top + H + 1, 0xFF000000);
        g.fill(left, top, left + W, top + H, 0xFF2B2622);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
