package com.tarbound.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import net.neoforged.neoforge.client.model.pipeline.VertexConsumerWrapper;
import com.mojang.math.Axis;
import com.tarbound.block.ShadeBlockEntity;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.SkullModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

/**
 * A Shade: a see-through head floating over its spot, wearing the face of whoever's online (a new one
 * every few seconds). While it watches an order, ghostly copies of what it holds circle it, fading in and out.
 */
public class ShadeRenderer implements BlockEntityRenderer<ShadeBlockEntity> {
    /** Ticks each face is worn for. */
    private static final int FACE_TICKS = 120;
    /** Ticks before the circle swaps which held items it shows. */
    private static final int ITEM_TICKS = 80;
    private static final int MAX_SHOWN = 6;

    private final SkullModel head;
    private final ItemRenderer items;

    public ShadeRenderer(BlockEntityRendererProvider.Context context) {
        this.head = new SkullModel(context.bakeLayer(ModelLayers.PLAYER_HEAD));
        this.items = context.getItemRenderer();
    }

    @Override
    public void render(ShadeBlockEntity shade, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (shade.getLevel() == null) {
            return;
        }
        long seed = shade.getBlockPos().asLong();
        long ticks = shade.getLevel().getGameTime() + Math.floorMod(seed, 1000L);
        float time = ticks + partialTick;
        boolean busy = !shade.free();

        // the head: bobbing, slowly turning, see-through and a little blue
        float bob = Mth.sin(time * 0.05F) * 0.05F;
        pose.pushPose();
        pose.translate(0.5, 0.3 + bob, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees((time * (busy ? 1.2F : 0.5F)) % 360F));
        pose.scale(-1.0F, -1.0F, 1.0F);
        head.setupAnim(0.0F, 0.0F, 0.0F);
        int alpha = busy ? 0xB0 : 0x80;
        int color = alpha << 24 | 0xC8E6FF;
        VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucent(face(ticks / FACE_TICKS + seed)));
        head.renderToBuffer(pose, vc, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, color);
        pose.popPose();

        // what it holds, circling
        List<ItemStack> held = shade.shown();
        if (!busy || held.isEmpty()) {
            return;
        }
        int n = Math.min(MAX_SHOWN, held.size());
        int offset = (int) Math.floorMod(ticks / ITEM_TICKS, (long) held.size());
        for (int i = 0; i < n; i++) {
            ItemStack stack = held.get((offset + i) % held.size());
            float angle = time * 1.5F + i * 360F / n;
            float fade = 0.25F + 0.45F * (0.5F + 0.5F * Mth.sin(time * 0.12F + i * 1.7F));
            pose.pushPose();
            pose.translate(0.5, 0.55 + Mth.sin(time * 0.07F + i) * 0.08, 0.5);
            pose.mulPose(Axis.YP.rotationDegrees(angle));
            pose.translate(0.0, 0.0, 0.5);
            pose.scale(0.3F, 0.3F, 0.3F);
            items.renderStatic(stack, ItemDisplayContext.FIXED, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, pose,
                    new Ghost(buffers, fade), shade.getLevel(), (int) seed + i);
            pose.popPose();
        }
    }

    /** One online player's skin, picked by {@code pick}; Steve and friends when nobody's listed. */
    private static ResourceLocation face(long pick) {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection != null) {
            List<PlayerInfo> online = new ArrayList<>(connection.getOnlinePlayers());
            if (!online.isEmpty()) {
                online.sort(Comparator.comparing(p -> p.getProfile().getId()));
                return online.get((int) Math.floorMod(pick, (long) online.size())).getSkin().texture();
            }
        }
        return DefaultPlayerSkin.getDefaultTexture();
    }

    @Override
    public AABB getRenderBoundingBox(ShadeBlockEntity shade) {
        return new AABB(shade.getBlockPos()).inflate(1.0);
    }

    /**
     * Draws items see-through: block-atlas items go to the translucent item sheet with their alpha scaled
     * down; enchantment glint is skipped (it would show solid over the ghost).
     */
    private record Ghost(MultiBufferSource inner, float alpha) implements MultiBufferSource {
        @Override
        public VertexConsumer getBuffer(RenderType type) {
            String name = type.toString();
            if (name.contains("glint")) {
                return NOTHING;
            }
            boolean blockAtlas = type == RenderType.solid() || type == RenderType.cutout() || type == RenderType.cutoutMipped()
                    || type == RenderType.translucent() || type == Sheets.solidBlockSheet() || type == Sheets.cutoutBlockSheet()
                    || type == Sheets.translucentCullBlockSheet() || type == Sheets.translucentItemSheet();
            return new Faded(inner.getBuffer(blockAtlas ? Sheets.translucentItemSheet() : type), alpha);
        }
    }

    /** Passes vertices through with alpha multiplied. */
    private static final class Faded extends VertexConsumerWrapper {
        private final float alpha;

        Faded(VertexConsumer inner, float alpha) {
            super(inner);
            this.alpha = alpha;
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            parent.setColor(r, g, b, Mth.clamp((int) (a * alpha), 0, 255));
            return this;
        }
    }

    /** Swallows everything. */
    private static final class Nothing extends VertexConsumerWrapper {
        Nothing() {
            super(null);
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            return this;
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            return this;
        }

        @Override
        public VertexConsumer misc(VertexFormatElement element, int... values) {
            return this;
        }
    }

    private static final VertexConsumer NOTHING = new Nothing();
}
