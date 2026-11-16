package com.tarbound.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tarbound.Tarbound;
import com.tarbound.entity.WeakenedBlaze;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;

/**
 * The Weakened Blaze's own model: a blaze worn thin. Where a blaze has twelve rods in three tidy rings and a
 * full fire, this has six, in two lopsided, tilted, stuttering orbits, two of them snapped with the broken
 * tip hanging loose. The head slouches and has lost a chip; a small ember core sits where the fire was, with
 * a few flame wisps that sputter up from below it, and some debris drifts around the lot.
 *
 * <p>Every cube is a direct child of the root with a plain name (head, chip, core, rod0-5, tip1/tip4,
 * wisp0-3, shard0-2) on a 64x32 texture, so the layout is Blockbench-friendly. The texture is made by
 * {@code dev/make_weakened_blaze_texture.py}; {@code dev/weakened_blaze_uv_guide.png} shows where each cube
 * lives so it can be repainted without touching this class.
 *
 * <p>Only the core and the wisps glow: they're drawn full-bright with a flickering colour, while the
 * (burnt-out) body takes the world's light, so the fire is the one thing that dims and gutters.
 */
public class WeakenedBlazeModel extends HierarchicalModel<WeakenedBlaze> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(Tarbound.id("weakened_blaze"), "main");

    private static final float TAU = (float) (Math.PI * 2.0);
    private static final float DEG = (float) (Math.PI / 180.0);

    private static final int RODS = 6;
    /** Rods 0-2 are the high ring, 3-5 the low ring. */
    private static final int HIGH_RODS = 3;
    private static final int WISPS = 4;
    private static final int SHARDS = 3;

    /** The snapped rods: shorter, and each has a loose tip hanging below it. */
    private static boolean cracked(int rod) {
        return rod == 1 || rod == 4;
    }

    private final ModelPart root;
    private final ModelPart head;
    private final ModelPart chip;
    private final ModelPart core;
    private final ModelPart[] rods = new ModelPart[RODS];
    private final ModelPart[] tips = new ModelPart[RODS]; // null unless that rod is cracked
    private final ModelPart[] wisps = new ModelPart[WISPS];
    private final ModelPart[] shards = new ModelPart[SHARDS];

    /** Scratch for pose maths: x, y, z, xRot, zRot. */
    private final float[] pose = new float[5];
    private final float[] tipOut = new float[5];

    // Worked out in setupAnim, used when drawing (the renderer calls one right after the other).
    private final boolean[] wispOn = new boolean[WISPS];
    private float coreGlow = 1.0F;
    private float flameGlow = 1.0F;

    public WeakenedBlazeModel(ModelPart root) {
        this.root = root;
        this.head = root.getChild("head");
        this.chip = root.getChild("chip");
        this.core = root.getChild("core");
        for (int i = 0; i < RODS; i++) {
            this.rods[i] = root.getChild("rod" + i);
            this.tips[i] = cracked(i) ? root.getChild("tip" + i) : null;
        }
        for (int i = 0; i < WISPS; i++) {
            this.wisps[i] = root.getChild("wisp" + i);
        }
        for (int i = 0; i < SHARDS; i++) {
            this.shards[i] = root.getChild("shard" + i);
        }
    }

    // ---------------------------------------------------------------- geometry

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        // A little smaller than a blaze's 8x8x8 head. Texture footprint 28x14 at (0,0).
        root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 0).addBox(-3.5F, -3.5F, -3.5F, 7.0F, 7.0F, 7.0F),
                PartPose.offset(0.0F, 0.8F, 0.0F));
        // The piece that broke off its corner. 12x6 at (28,0).
        root.addOrReplaceChild("chip", CubeListBuilder.create().texOffs(28, 0).addBox(-1.5F, -1.5F, -1.5F, 3.0F, 3.0F, 3.0F),
                PartPose.offset(4.8F, -4.2F, 2.0F));
        // The ember. 16x8 at (40,0).
        root.addOrReplaceChild("core", CubeListBuilder.create().texOffs(40, 0).addBox(-2.0F, -2.0F, -2.0F, 4.0F, 4.0F, 4.0F),
                PartPose.offset(0.0F, 10.5F, 0.0F));

        float[] p = new float[5];
        for (int i = 0; i < RODS; i++) {
            rodPose(i, 0.0F, p);
            // Boxes hang from the top-centre so a lean pivots at the rod's top. Whole rod 8 tall (8x10 at (0,16)),
            // snapped rod 5 tall (8x7 at (8,16)).
            CubeListBuilder cube = cracked(i)
                    ? CubeListBuilder.create().texOffs(8, 16).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 5.0F, 2.0F)
                    : CubeListBuilder.create().texOffs(0, 16).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 8.0F, 2.0F);
            root.addOrReplaceChild("rod" + i, cube, PartPose.offsetAndRotation(p[0], p[1], p[2], p[3], 0.0F, p[4]));
            if (cracked(i)) {
                float[] t = new float[5];
                tipPose(p, i, 0.0F, t);
                // The snapped-off end. 8x5 at (32,16).
                root.addOrReplaceChild("tip" + i, CubeListBuilder.create().texOffs(32, 16).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 3.0F, 2.0F),
                        PartPose.offsetAndRotation(t[0], t[1], t[2], t[3], 0.0F, t[4]));
            }
        }

        // Flame wisps grow upward from their pivot (the box sits above it), so they shrink toward their base. 8x6 at (16,16).
        for (int i = 0; i < WISPS; i++) {
            root.addOrReplaceChild("wisp" + i, CubeListBuilder.create().texOffs(16, 16).addBox(-1.0F, -4.0F, -1.0F, 2.0F, 4.0F, 2.0F),
                    PartPose.offset(Mth.cos(i * TAU / WISPS) * 3.0F, 13.0F, Mth.sin(i * TAU / WISPS) * 3.0F));
        }
        // Drifting debris. 8x4 at (24,16).
        for (int i = 0; i < SHARDS; i++) {
            root.addOrReplaceChild("shard" + i, CubeListBuilder.create().texOffs(24, 16).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 2.0F, 2.0F),
                    PartPose.offset(8.5F + i * 1.2F, 1.0F + i * 4.0F, 0.0F));
        }

        return LayerDefinition.create(mesh, 64, 32);
    }

    // ---------------------------------------------------------------- animation maths (shared with the rest pose above)

    /**
     * Where rod {@code i} is at time {@code t}. The two rings are ellipses, off-centre, with one side
     * tipped up, and they spin unevenly - hesitating, then catching up - rather than at a steady pace.
     * Rods lean outward, and the snapped ones lean further.
     */
    private static void rodPose(int i, float t, float[] out) {
        int k = i % HIGH_RODS;
        float lean = cracked(i) ? 0.32F : 0.12F;
        float c;
        float s;
        if (i < HIGH_RODS) {
            float a = -(0.2F * t + 0.9F * Mth.sin(0.09F * t)) + k * TAU / 3.0F;
            c = Mth.cos(a);
            s = Mth.sin(a);
            out[0] = 1.5F + c * 9.0F;
            out[2] = -1.0F + s * 6.5F;
            out[1] = -2.0F + s * 2.2F + Mth.cos((i * 2 + t) * 0.25F);
        } else {
            float a = (float) (Math.PI / 4.0) + 0.075F * t + 0.7F * Mth.sin(0.07F * t + 1.7F) + k * TAU / 3.0F;
            c = Mth.cos(a);
            s = Mth.sin(a);
            out[0] = -1.0F + c * 5.5F;
            out[2] = 1.0F + s * 7.0F;
            out[1] = 3.5F - s * 1.8F + Mth.cos((i * 2 + t) * 0.25F);
        }
        float wobble = 0.05F * Mth.sin(t * 0.3F + i);
        out[3] = s * lean + wobble; // xRot: lean toward +z when the rod is on the +z side
        out[4] = -c * lean + wobble; // zRot: positive zRot swings the hanging end toward -x
    }

    /** The loose end of a snapped rod: hangs where the rod's end would be, a gap further down, twisted off-line. */
    private static void tipPose(float[] rod, int i, float t, float[] out) {
        float gap = 1.2F + 0.8F * Mth.sin(t * 0.17F + i);
        float len = 5.0F + gap;
        float xRot = rod[3] + 0.25F * Mth.sin(t * 0.13F + i);
        float zRot = rod[4] + (i % 2 == 0 ? 0.45F : -0.45F);
        // Where the point (0, len, 0) lands under the rod's own rotation (ZYX order, no yaw).
        float cx = Mth.cos(rod[3]);
        out[0] = rod[0] - len * cx * Mth.sin(rod[4]);
        out[1] = rod[1] + len * cx * Mth.cos(rod[4]);
        out[2] = rod[2] + len * Mth.sin(rod[3]);
        out[3] = xRot;
        out[4] = zRot;
    }

    @Override
    public ModelPart root() {
        return this.root;
    }

    @Override
    public void setupAnim(WeakenedBlaze entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        // Each blaze runs on its own clock, so a group doesn't flicker or orbit in step.
        float t = ageInTicks + (entity.getId() * 37 % 400);

        // Guttering: both glows are products of out-of-step sines, so they mostly hold and now and then drop out.
        this.coreGlow = Mth.clamp(0.62F + 0.45F * Mth.sin(t * 0.83F) * Mth.sin(t * 1.91F + 1.3F) + 0.12F * Mth.sin(t * 5.3F), 0.2F, 1.0F);
        this.flameGlow = Mth.clamp(0.6F + 0.5F * Mth.sin(t * 0.61F) * Mth.sin(t * 1.37F + 0.8F) + 0.15F * Mth.sin(t * 3.1F), 0.0F, 1.0F);
        if (entity.isOnFire()) { // a blaze reports "on fire" while it's shooting; the flame flares up for it
            this.flameGlow = Math.max(this.flameGlow, 0.9F);
        }

        for (int i = 0; i < RODS; i++) {
            rodPose(i, t, this.pose);
            ModelPart rod = this.rods[i];
            rod.setPos(this.pose[0], this.pose[1], this.pose[2]);
            rod.xRot = this.pose[3];
            rod.zRot = this.pose[4];
            ModelPart tip = this.tips[i];
            if (tip != null) {
                tipPose(this.pose, i, t, this.tipOut);
                tip.setPos(this.tipOut[0], this.tipOut[1], this.tipOut[2]);
                tip.xRot = this.tipOut[3];
                tip.zRot = this.tipOut[4];
            }
        }

        // Head: slouched forward, tilted to one side, hanging a little low and bobbing.
        this.head.yRot = netHeadYaw * DEG;
        this.head.xRot = headPitch * DEG + 0.12F;
        this.head.zRot = 0.14F + 0.05F * Mth.sin(t * 0.07F);
        this.head.y = 0.8F + Mth.sin(t * 0.1F) * 0.7F;

        // The chipped-off piece, tumbling slowly beside the head.
        this.chip.setPos(4.8F + Mth.sin(t * 0.13F) * 0.6F, -4.2F + Mth.sin(t * 0.09F + 1.0F) * 0.8F, 2.0F + Mth.cos(t * 0.11F) * 0.6F);
        this.chip.xRot = t * 0.04F;
        this.chip.yRot = t * 0.06F;
        this.chip.zRot = 0.5F;

        // The ember: bobbing, turning, and swelling and shrinking with its glow.
        this.core.y = 10.5F + Mth.sin(t * 0.12F) * 0.8F;
        this.core.yRot = t * 0.05F;
        this.core.xRot = 0.4F * Mth.sin(t * 0.09F);
        float coreScale = 0.8F + 0.2F * this.coreGlow;
        this.core.xScale = coreScale;
        this.core.yScale = coreScale;
        this.core.zScale = coreScale;

        // Wisps: each rises from beneath the core and gutters out, on staggered lives. Scale never reaches 0
        // (that would put 1/0 in the normal matrix), so a spent wisp is hidden instead.
        for (int i = 0; i < WISPS; i++) {
            float life = Mth.frac(t * 0.045F + i * 0.25F + (i % 2) * 0.07F);
            float ang = i * TAU / WISPS + t * 0.03F;
            float spread = 3.0F * (1.0F - 0.4F * life);
            float size = Mth.sin((float) Math.PI * life) * (0.3F + 0.9F * this.flameGlow);
            ModelPart wisp = this.wisps[i];
            wisp.setPos(Mth.cos(ang) * spread, 15.0F - 5.0F * life, Mth.sin(ang) * spread);
            wisp.yScale = Math.max(0.08F, size * 1.3F);
            float girth = 0.55F + 0.45F * Math.min(size, 1.0F);
            wisp.xScale = girth;
            wisp.zScale = girth;
            this.wispOn[i] = size > 0.08F;
        }

        // Debris: slow, tumbling, at different heights and in alternating directions.
        for (int i = 0; i < SHARDS; i++) {
            float a = t * 0.035F * (i % 2 == 0 ? 1.0F : -1.0F) + i * 2.1F;
            ModelPart shard = this.shards[i];
            shard.setPos(Mth.cos(a) * (8.5F + i * 1.2F), 1.0F + i * 4.0F + Mth.sin(t * 0.07F + i * 2.0F) * 1.5F, Mth.sin(a) * (8.5F + i * 1.2F));
            shard.xRot = t * 0.05F * (i + 1);
            shard.zRot = t * 0.03F * (i + 2);
        }
    }

    // ---------------------------------------------------------------- drawing

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay, int color) {
        // The body first, in the world's light, with the glowing parts held back...
        this.core.visible = false;
        for (ModelPart wisp : this.wisps) {
            wisp.visible = false;
        }
        this.root.render(poseStack, buffer, packedLight, packedOverlay, color);

        // ...then the ember and the flame, full-bright, dimmed and brightened by their flicker. They're children of
        // the (untransformed) root, so drawing them straight from the same pose is the same as drawing them through it.
        this.core.visible = true;
        this.core.render(poseStack, buffer, LightTexture.FULL_BRIGHT, packedOverlay, FastColor.ARGB32.multiply(color, grey(0.3F + 0.7F * this.coreGlow)));
        int flame = FastColor.ARGB32.multiply(color, grey(0.5F + 0.5F * this.flameGlow));
        for (int i = 0; i < WISPS; i++) {
            this.wisps[i].visible = this.wispOn[i];
            this.wisps[i].render(poseStack, buffer, LightTexture.FULL_BRIGHT, packedOverlay, flame);
        }
    }

    private static int grey(float level) {
        int v = Mth.clamp((int) (level * 255.0F), 0, 255);
        return FastColor.ARGB32.color(255, v, v, v);
    }
}
