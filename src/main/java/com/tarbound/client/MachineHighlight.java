package com.tarbound.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.OptionalDouble;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * Client side: shift-right-click a machine's name in the radio's machine list and it's outlined in the
 * world for a while, through walls.
 * <p>
 * Same idea as Sophisticated Item Actions' storage highlights (a client-side target with an expiry,
 * drawn after the level renders, pulsing so it catches the eye), written fresh for one target.
 */
public final class MachineHighlight {
    /** How long a highlight lasts. */
    private static final int DURATION_TICKS = 20 * 15;
    private static final float R = 0.55F, G = 0.95F, B = 1.0F; // the radio's spirit cyan

    /** Lines drawn over everything (no depth test), so the outline shows through walls. */
    static final RenderType LINES_THROUGH_WALLS = RenderType.create("tarbound_machine_highlight",
            DefaultVertexFormat.POSITION_COLOR_NORMAL, VertexFormat.Mode.LINES, 256, false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.RENDERTYPE_LINES_SHADER)
                    .setLineState(new RenderStateShard.LineStateShard(OptionalDouble.of(2.5)))
                    .setLayeringState(RenderStateShard.VIEW_OFFSET_Z_LAYERING)
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setOutputState(RenderStateShard.ITEM_ENTITY_TARGET)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setDepthTestState(RenderStateShard.NO_DEPTH_TEST)
                    .createCompositeState(false));

    @Nullable
    private static BlockPos target;
    @Nullable
    private static ResourceKey<Level> dimension;
    private static long until;

    private MachineHighlight() {}

    /** Outlines the machine at {@code pos} (and its Reliquary above it) for a while. */
    public static void show(BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        target = pos.immutable();
        dimension = mc.level.dimension();
        until = mc.level.getGameTime() + DURATION_TICKS;
    }

    public static void clear() {
        target = null;
    }

    /** The machine currently highlighted, if any. */
    @Nullable
    public static BlockPos target() {
        return active() ? target : null;
    }

    private static boolean active() {
        Minecraft mc = Minecraft.getInstance();
        if (target == null || mc.level == null || !mc.level.dimension().equals(dimension)) {
            return false;
        }
        if (mc.level.getGameTime() > until) {
            target = null;
            return false;
        }
        return true;
    }

    /** Pulses between 0 and 1 about once a second. */
    private static float pulse(float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        float t = (mc.level.getGameTime() % 20 + partialTick) / 20F;
        return 0.5F + 0.5F * Mth.sin(t * Mth.TWO_PI);
    }

    /** World render hook (registered on the game bus by {@link TarboundClient}). */
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || !active()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        float p = pulse(partial);
        Vec3 cam = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);

        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer lines = buffers.getBuffer(LINES_THROUGH_WALLS);
        // The machine and the Reliquary on it, breathing slightly.
        AABB box = new AABB(target).expandTowards(0, 1, 0).inflate(0.02 + 0.06 * p);
        LevelRenderer.renderLineBox(pose, lines, box, R, G, B, 0.55F + 0.45F * p);
        buffers.endBatch(LINES_THROUGH_WALLS);
        pose.popPose();
    }
}
