package com.tarbound.client;

import com.tarbound.Tarbound;
import com.tarbound.entity.WeakenedBlaze;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

/**
 * Draws the Weakened Blaze with its own model ({@link WeakenedBlazeModel}) and texture
 * ({@code textures/entity/weakened_blaze.png}, which resource packs can replace). Only the model's ember and
 * flame draw full-bright; the burnt-out body takes the world's light, capped at {@link #BODY_LIGHT} block light
 * so it's visibly duller than a blaze's always-lit body.
 */
public class WeakenedBlazeRenderer extends MobRenderer<WeakenedBlaze, WeakenedBlazeModel> {
    private static final ResourceLocation TEXTURE = Tarbound.id("textures/entity/weakened_blaze.png");
    /** Block light the body is drawn with (a vanilla blaze uses 15). */
    private static final int BODY_LIGHT = 13;

    public WeakenedBlazeRenderer(EntityRendererProvider.Context context) {
        super(context, new WeakenedBlazeModel(context.bakeLayer(WeakenedBlazeModel.LAYER)), 0.45F);
    }

    @Override
    protected int getBlockLightLevel(WeakenedBlaze entity, BlockPos pos) {
        return BODY_LIGHT;
    }

    @Override
    public ResourceLocation getTextureLocation(WeakenedBlaze entity) {
        return TEXTURE;
    }
}
