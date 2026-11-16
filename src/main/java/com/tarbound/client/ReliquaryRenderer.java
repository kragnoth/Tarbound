package com.tarbound.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tarbound.block.ReliquaryBlockEntity;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** A ring of lit candles floating around a Reliquary: one per contract slot it has open. */
public class ReliquaryRenderer implements BlockEntityRenderer<ReliquaryBlockEntity> {
    private static final BlockState CANDLE = Blocks.CANDLE.defaultBlockState().setValue(CandleBlock.LIT, true);
    private final BlockRenderDispatcher blocks;

    public ReliquaryRenderer(BlockEntityRendererProvider.Context context) {
        this.blocks = context.getBlockRenderDispatcher();
    }

    @Override
    public void render(ReliquaryBlockEntity reliquary, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (reliquary.getLevel() == null) {
            return;
        }
        int n = reliquary.unlocked();
        double time = reliquary.getLevel().getGameTime() + reliquary.getBlockPos().asLong() % 400 + partialTick;
        for (int i = 0; i < n; i++) {
            Vec3 p = ReliquaryBlockEntity.candlePos(reliquary.getBlockState(), i, n, time);
            pose.pushPose();
            pose.translate(p.x, p.y, p.z);
            pose.scale(ReliquaryBlockEntity.CANDLE_SCALE, ReliquaryBlockEntity.CANDLE_SCALE, ReliquaryBlockEntity.CANDLE_SCALE);
            pose.translate(-0.5, 0.0, -0.5); // the candle model stands at the middle of its block
            blocks.renderSingleBlock(CANDLE, pose, buffers, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
            pose.popPose();
        }
    }

    @Override
    public AABB getRenderBoundingBox(ReliquaryBlockEntity reliquary) {
        return new AABB(reliquary.getBlockPos()).inflate(1.0);
    }
}
