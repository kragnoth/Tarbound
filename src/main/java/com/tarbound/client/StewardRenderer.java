package com.tarbound.client;

import com.tarbound.Tarbound;
import com.tarbound.entity.Steward;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.resources.ResourceLocation;

/**
 * Draws the Steward ({@link StewardModel}). The tar takes the world's light; the Reliquary eyes behind its grille, the
 * lamp on the back of its head and the soul light in its cracks are drawn full-bright from
 * {@code textures/entity/steward_glow.png}, the way an enderman's eyes are.
 */
public class StewardRenderer extends MobRenderer<Steward, StewardModel> {
    private static final ResourceLocation TEXTURE = Tarbound.id("textures/entity/steward.png");
    private static final RenderType GLOW = RenderType.eyes(Tarbound.id("textures/entity/steward_glow.png"));

    public StewardRenderer(EntityRendererProvider.Context context) {
        super(context, new StewardModel(context.bakeLayer(StewardModel.LAYER)), 0.5F);
        addLayer(new EyesLayer<>(this) {
            @Override
            public RenderType renderType() {
                return GLOW;
            }
        });
    }

    @Override
    public ResourceLocation getTextureLocation(Steward entity) {
        return TEXTURE;
    }
}
