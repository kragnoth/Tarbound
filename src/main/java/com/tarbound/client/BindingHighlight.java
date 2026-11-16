package com.tarbound.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tarbound.block.ReliquaryBlock;
import com.tarbound.block.ScryingFontBlock;
import com.tarbound.net.BindingsPayload;
import com.tarbound.net.BindingsRequestPayload;
import com.tarbound.net.ClientBindings;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Crouch and look at a radio and everything it has bound is outlined in the world, through walls: storage in gold,
 * machines in spirit cyan, tanks in blue, machines touching the tar without a Reliquary in red. Crouch and look at a
 * Reliquary and the machines it works are outlined. (Entangled outlines its bound block the same way.) The outlines
 * stay for two seconds after looking away. Client side; the radio's list comes from the server
 * ({@link BindingsPayload}), asked for once a second while looking.
 */
public final class BindingHighlight {
    private static final int LINGER_TICKS = 40;
    private static final int REQUEST_EVERY = 20;
    private static final double MAX_DRAW_DISTANCE = 64;

    @Nullable
    private static BlockPos radio;
    @Nullable
    private static List<BlockPos> reliquaryTargets;
    private static long lastSeen = Long.MIN_VALUE;
    private static long lastRequest = Long.MIN_VALUE;

    private BindingHighlight() {}

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            radio = null;
            reliquaryTargets = null;
            ClientBindings.clear();
            return;
        }
        long now = mc.level.getGameTime();
        if (mc.screen == null && mc.player.isShiftKeyDown() && mc.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
            BlockPos pos = hit.getBlockPos();
            BlockState state = mc.level.getBlockState(pos);
            if (state.getBlock() instanceof ScryingFontBlock) {
                if (!pos.equals(radio) || now - lastRequest >= REQUEST_EVERY || now < lastRequest) {
                    PacketDistributor.sendToServer(new BindingsRequestPayload(pos));
                    lastRequest = now;
                }
                radio = pos.immutable();
                reliquaryTargets = null;
                lastSeen = now;
            } else if (state.getBlock() instanceof ReliquaryBlock) {
                radio = null;
                reliquaryTargets = ReliquaryBlock.targets(mc.level, pos, state);
                lastSeen = now;
            }
        }
        if (now - lastSeen > LINGER_TICKS || now < lastSeen) {
            radio = null;
            reliquaryTargets = null;
        }
    }

    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || (radio == null && reliquaryTargets == null)) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Vec3 cam = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer lines = buffers.getBuffer(MachineHighlight.LINES_THROUGH_WALLS);
        if (reliquaryTargets != null) {
            boxes(pose, lines, cam, reliquaryTargets, 0.55F, 0.95F, 1.0F);
        } else {
            BindingsPayload b = ClientBindings.latest();
            if (b != null && b.font().equals(radio)) {
                boxes(pose, lines, cam, b.storage(), 1.0F, 0.8F, 0.3F);
                boxes(pose, lines, cam, b.machines(), 0.55F, 0.95F, 1.0F);
                boxes(pose, lines, cam, b.tanks(), 0.4F, 0.6F, 1.0F);
                boxes(pose, lines, cam, b.unbound(), 1.0F, 0.3F, 0.3F);
            }
        }
        buffers.endBatch(MachineHighlight.LINES_THROUGH_WALLS);
        pose.popPose();
    }

    private static void boxes(PoseStack pose, VertexConsumer lines, Vec3 cam, List<BlockPos> positions, float r, float g, float b) {
        for (BlockPos p : positions) {
            if (Vec3.atCenterOf(p).distanceToSqr(cam) > MAX_DRAW_DISTANCE * MAX_DRAW_DISTANCE) {
                continue;
            }
            LevelRenderer.renderLineBox(pose, lines, new AABB(p).inflate(0.01), r, g, b, 0.8F);
        }
    }
}
