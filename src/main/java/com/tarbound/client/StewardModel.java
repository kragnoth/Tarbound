package com.tarbound.client;

import com.tarbound.Tarbound;
import com.tarbound.entity.Steward;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * The Steward: an enderman's build in a cracked shell of tar, with an observer of cast iron for a head. Two long arms
 * hang from the shoulders the enderman way; two shorter ones broke out of its flanks lower down (a lump of wet tar
 * where each came through) and are held together in front of it, like a clerk's, and work when it has a job running. Drops of tar swell and fall from
 * the cracks.
 *
 * <p>Every cube is a direct child of the root with a plain name on a 64x64 texture
 * ({@code dev/make_steward_texture.py}, guide in {@code dev/steward_uv_guide.png}); the glow layer uses the same
 * layout. The feet are at y = 24, the top of the head at y = -24 (3 blocks).
 */
public class StewardModel extends HierarchicalModel<Steward> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(Tarbound.id("steward"), "main");

    private static final int DRIPS = 3;
    /** Ticks for a drop to swell and fall, and each drop's head start. */
    private static final float[] DRIP_PERIOD = {70, 95, 120};
    private static final float[] DRIP_PHASE = {0, 41, 17};

    private final ModelPart root;
    private final ModelPart head;
    private final ModelPart rightArm;
    private final ModelPart leftArm;
    private final ModelPart rightLow;
    private final ModelPart leftLow;
    private final ModelPart[] drips = new ModelPart[DRIPS];

    public StewardModel(ModelPart root) {
        this.root = root;
        this.head = root.getChild("head");
        this.rightArm = root.getChild("right_arm");
        this.leftArm = root.getChild("left_arm");
        this.rightLow = root.getChild("right_lower_arm");
        this.leftLow = root.getChild("left_lower_arm");
        for (int i = 0; i < DRIPS; i++) {
            drips[i] = root.getChild("drip" + i);
        }
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition r = mesh.getRoot();
        CubeDeformation none = CubeDeformation.NONE;
        r.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 0).addBox(-4, -8, -4, 8, 8, 8, none), PartPose.offset(0, -16, 0));
        r.addOrReplaceChild("body", CubeListBuilder.create().texOffs(32, 0).addBox(-4, -12, -2, 8, 12, 4, none), PartPose.offset(0, -4, 0));
        r.addOrReplaceChild("right_arm", CubeListBuilder.create().texOffs(0, 16).addBox(-1, -1, -1, 2, 28, 2, none),
                PartPose.offset(-5, -15, 0));
        r.addOrReplaceChild("left_arm", CubeListBuilder.create().texOffs(0, 16).mirror().addBox(-1, -1, -1, 2, 28, 2, none),
                PartPose.offset(5, -15, 0));
        r.addOrReplaceChild("right_lower_arm", CubeListBuilder.create().texOffs(8, 16).addBox(-1, -1, -1, 2, 20, 2, none),
                PartPose.offset(-4, -9, -0.5F));
        r.addOrReplaceChild("left_lower_arm", CubeListBuilder.create().texOffs(8, 16).mirror().addBox(-1, -1, -1, 2, 20, 2, none),
                PartPose.offset(4, -9, -0.5F));
        r.addOrReplaceChild("right_socket", CubeListBuilder.create().texOffs(24, 16).addBox(-1.5F, -1.5F, -1.5F, 3, 3, 3, none),
                PartPose.offset(-4, -9, -0.5F));
        r.addOrReplaceChild("left_socket", CubeListBuilder.create().texOffs(24, 16).mirror().addBox(-1.5F, -1.5F, -1.5F, 3, 3, 3, none),
                PartPose.offset(4, -9, -0.5F));
        r.addOrReplaceChild("right_leg", CubeListBuilder.create().texOffs(16, 16).addBox(-1, 0, -1, 2, 28, 2, none), PartPose.offset(-2, -4, 0));
        r.addOrReplaceChild("left_leg", CubeListBuilder.create().texOffs(16, 16).mirror().addBox(-1, 0, -1, 2, 28, 2, none), PartPose.offset(2, -4, 0));
        // Drops: under the shell's front edge, on the chest crack, under the right socket.
        float[][] at = {{-2.5F, -4, -1.6F}, {0.5F, -8, -2.1F}, {-4, -7.5F, -0.5F}};
        for (int i = 0; i < DRIPS; i++) {
            r.addOrReplaceChild("drip" + i, CubeListBuilder.create().texOffs(36, 16).addBox(-0.5F, 0, -0.5F, 1, 2, 1, none),
                    PartPose.offset(at[i][0], at[i][1], at[i][2]));
        }
        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public ModelPart root() {
        return root;
    }

    @Override
    public void setupAnim(Steward entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        root.getAllParts().forEach(ModelPart::resetPose);
        head.yRot = netHeadYaw * Mth.DEG_TO_RAD;
        head.xRot = headPitch * Mth.DEG_TO_RAD;

        // The long arms hang and sway a little, out of step with each other.
        rightArm.xRot = Mth.sin(ageInTicks * 0.045F) * 0.05F;
        leftArm.xRot = Mth.sin(ageInTicks * 0.045F + 2.1F) * 0.05F;
        rightArm.zRot = 0.07F + Mth.sin(ageInTicks * 0.033F) * 0.025F;
        leftArm.zRot = -0.07F - Mth.sin(ageInTicks * 0.033F + 1.3F) * 0.025F;

        // The short arms are held forward; working, they move like hands counting or writing.
        boolean working = entity.working();
        float speed = working ? 0.22F : 0.05F;
        float reach = working ? 0.22F : 0.05F;
        rightLow.xRot = -0.75F + Mth.sin(ageInTicks * speed) * reach;
        leftLow.xRot = -0.75F + Mth.sin(ageInTicks * speed + Mth.PI) * reach;
        rightLow.yRot = -0.38F;
        leftLow.yRot = 0.38F;
        rightLow.zRot = 0.12F + (working ? Mth.cos(ageInTicks * speed) * 0.06F : 0.0F);
        leftLow.zRot = -0.12F - (working ? Mth.cos(ageInTicks * speed + Mth.PI) * 0.06F : 0.0F);

        // Drops swell slowly, then fall and start again.
        for (int i = 0; i < DRIPS; i++) {
            float t = ((ageInTicks + DRIP_PHASE[i]) % DRIP_PERIOD[i]) / DRIP_PERIOD[i];
            drips[i].yScale = 0.25F + t * t * 1.75F;
            drips[i].xScale = drips[i].zScale = 0.8F + t * 0.3F;
        }
    }
}
