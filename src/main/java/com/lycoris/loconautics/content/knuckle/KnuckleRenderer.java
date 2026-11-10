package com.lycoris.loconautics.content.knuckle;

import com.lycoris.loconautics.client.LoconauticsPartialModels;
import com.lycoris.loconautics.common.copycat.CopycatPlacement;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.simibubi.create.foundation.blockEntity.renderer.SmartBlockEntityRenderer;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.ClientSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.createmod.catnip.render.CachedBuffers;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4f;
import org.joml.Vector3d;

public class KnuckleRenderer extends SmartBlockEntityRenderer<KnuckleBlockEntity> {

    private static final float PX = 1.0f / 16.0f;

    private static final float ARM_HINGE_X = 8.0f * PX;
    private static final float ARM_HINGE_Z = 13.0f * PX;

    private static final float STRETCH_ANCHOR_Z = 15.0f * PX;
    private static final float STRETCH_LENGTH = 3.0f * PX;

    private static final float HAND_PIVOT_Y = 13.0f * PX;

    private static final float COUPLING_PIVOT_X = 13.5f * PX;
    private static final float COUPLING_PIVOT_Y = 9.0f * PX;
    private static final float COUPLING_PIVOT_Z = 0.5f * PX;

    private static final float PIN_PIVOT_X = 8.0f * PX;
    private static final float PIN_PIVOT_Y = 14.0f * PX;
    private static final float PIN_PIVOT_Z = 5.0f * PX;

    private static final float[][] HAND_YAW =
            {{0.0f, 0.0f}, {0.5f, -7.5f}, {1.0f, -10.0f}, {1.5833f, -13.75f}, {2.0f, 0.0f}};
    private static final float[][] COUPLING_YAW =
            {{1.25f, -22.5f}, {1.8333f, -6.11f}, {2.0f, 0.0f}};
    private static final float[][] PIN_YAW =
            {{1.3333f, -22.5f}, {1.5f, 0.0f}};
    private static final float[][] PIN_LIFT =
            {{1.8333f, 1.0f}, {2.0f, 0.0f}};

    private static final float[][] DRAW_IN =
            {{0.0f, 16.0f}, {0.5f, 11.0f}, {1.0f, 8.0f}, {1.25f, 5.0f}, {1.5f, 4.0f}, {2.0f, 0.0f}};

    private static final float ARM_REACH = 13.0f / 16.0f;

    private record Mate(Vector3d hinge, Vector3d wallOutward, boolean controller) {
    }

    private final WallClipVertexConsumer ownWall = new WallClipVertexConsumer();
    private final WallClipVertexConsumer partnerWall = new WallClipVertexConsumer();

    public KnuckleRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected void renderSafe(KnuckleBlockEntity be, float partialTicks, PoseStack ps,
                              MultiBufferSource bufferSource, int light, int overlay) {
        Direction myFacing = be.getBlockState().getValue(KnuckleBlock.FACING);
        BlockPos myPos = be.getBlockPos();
        BlockState state = be.getBlockState();
        VertexConsumer buffer = bufferSource.getBuffer(RenderType.cutoutMipped());
        Matrix4f base = new Matrix4f(ps.last().pose());
        ps.pushPose();
        ps.translate(0.0f, CopycatPlacement.CENTER.blocks(), 0.0f);

        Vector3d a = KnuckleBlockEntity.hingeLocal(myPos, myFacing);
        double restX = a.x - myPos.getX();
        double restZ = a.z - myPos.getZ();
        float restYaw = facingYaw(myFacing);

        ownWall.begin(buffer, base, (float) restX, 0.5f, (float) restZ,
                -myFacing.getStepX(), 0.0f, -myFacing.getStepZ());
        Mate mate = be.isConnected() || be.isReleasing() ? partnerMate(be) : null;
        Vector3d b = mate == null ? null : mate.hinge();
        boolean live = b != null && be.isConnected();
        double dx = b == null ? 0.0 : b.x - a.x;
        double dz = b == null ? 0.0 : b.z - a.z;
        double dist = Math.hypot(dx, dz);

        float clip = be.animationClip(dist, partialTicks);
        float handClip = b == null ? 0.0f : clip;
        if (b == null || clip <= 0.0f) {
            renderArm(ps, ownWall, state, light, clip, 0.0f, restX, restZ, restYaw);
            ps.popPose();
            return;
        }
        if (dist < 1.0e-6) {
            ps.popPose();
            return;
        }

        float blend = clip / KnuckleTuning.CLIP_LENGTH;
        float aim = (float) Math.toDegrees(Math.atan2(dz, dx));
        float ownYaw = restYaw + Mth.degreesDifference(restYaw, aim) * blend;

        float drawHalf = sample(DRAW_IN, clip) * PX * 0.5f;
        float shank = Math.max(0.0f, (float) (dist * 0.5) - ARM_REACH - drawHalf) * blend;
        ps.pushPose();
        ps.translate(restX, 0.0, restZ);
        ps.mulPose(Axis.YP.rotationDegrees(modelYaw(ownYaw)));
        ps.translate(-ARM_HINGE_X, 0.0f, -ARM_HINGE_Z);
        ps.translate(0.0f, 0.0f, STRETCH_ANCHOR_Z);
        ps.scale(1.0f, 1.0f, shank / STRETCH_LENGTH);
        ps.translate(0.0f, 0.0f, -STRETCH_ANCHOR_Z);
        CachedBuffers.partial(LoconauticsPartialModels.KNUCKLE_STRETCH_ARM, state)
                .light(light)
                .renderInto(ps, ownWall);
        ps.popPose();

        if (live && !mate.controller()) {
            ps.popPose();
            return;
        }
        double ux = dx / dist;
        double uz = dz / dist;
        double cx = (a.x + b.x) * 0.5 - myPos.getX();
        double cz = (a.z + b.z) * 0.5 - myPos.getZ();
        double reach = ARM_REACH + drawHalf;
        renderArm(ps, ownWall, state, light, clip, handClip,
                Mth.lerp(blend, restX, cx - ux * reach),
                Mth.lerp(blend, restZ, cz - uz * reach), ownYaw);
        if (live) {
            partnerWall.begin(buffer, base, (float) (b.x - myPos.getX()), 0.5f, (float) (b.z - myPos.getZ()),
                    (float) mate.wallOutward().x, 0.0f, (float) mate.wallOutward().z);
            renderArm(ps, partnerWall, state, light, clip, handClip,
                    Mth.lerp(blend, b.x - myPos.getX(), cx + ux * reach),
                    Mth.lerp(blend, b.z - myPos.getZ(), cz + uz * reach), ownYaw + 180.0f);
        }
        ps.popPose();
    }

    private static void renderArm(PoseStack ps, VertexConsumer buffer, BlockState state, int light,
                                  float clip, float handClip, double x, double z, float yaw) {
        ps.pushPose();
        ps.translate(x, 0.0, z);
        ps.mulPose(Axis.YP.rotationDegrees(modelYaw(yaw)));
        ps.translate(-ARM_HINGE_X, 0.0f, -ARM_HINGE_Z);
        handBone(ps, handClip);
        CachedBuffers.partial(LoconauticsPartialModels.KNUCKLE_HAND, state)
                .light(light)
                .renderInto(ps, buffer);

        ps.pushPose();
        ps.translate(COUPLING_PIVOT_X, COUPLING_PIVOT_Y, COUPLING_PIVOT_Z);
        ps.mulPose(Axis.YP.rotationDegrees(sample(COUPLING_YAW, clip)));
        ps.translate(-COUPLING_PIVOT_X, -COUPLING_PIVOT_Y, -COUPLING_PIVOT_Z);
        CachedBuffers.partial(LoconauticsPartialModels.KNUCKLE_COUPLING, state)
                .light(light)
                .renderInto(ps, buffer);
        ps.popPose();

        ps.translate(PIN_PIVOT_X, PIN_PIVOT_Y + sample(PIN_LIFT, clip) * PX, PIN_PIVOT_Z);
        ps.mulPose(Axis.YP.rotationDegrees(sample(PIN_YAW, clip)));
        ps.translate(-PIN_PIVOT_X, -PIN_PIVOT_Y, -PIN_PIVOT_Z);
        CachedBuffers.partial(LoconauticsPartialModels.KNUCKLE_PIN, state)
                .light(light)
                .renderInto(ps, buffer);
        ps.popPose();
    }

    private static void handBone(PoseStack ps, float clip) {
        ps.translate(ARM_HINGE_X, HAND_PIVOT_Y, ARM_HINGE_Z);
        ps.mulPose(Axis.YP.rotationDegrees(sample(HAND_YAW, clip)));
        ps.translate(-ARM_HINGE_X, -HAND_PIVOT_Y, -ARM_HINGE_Z);
    }

    private static float sample(float[][] keys, float seconds) {
        if (seconds <= keys[0][0]) {
            return keys[0][1];
        }
        for (int i = 1; i < keys.length; i++) {
            if (seconds > keys[i][0]) {
                continue;
            }
            float span = keys[i][0] - keys[i - 1][0];
            float t = span <= 0.0f ? 1.0f : (seconds - keys[i - 1][0]) / span;
            return Mth.lerp(t, keys[i - 1][1], keys[i][1]);
        }
        return keys[keys.length - 1][1];
    }

    private static float modelYaw(float aimDegrees) {
        return -(aimDegrees + 90.0f);
    }

    private static float facingYaw(Direction facing) {
        return (float) Math.toDegrees(Math.atan2(facing.getStepZ(), facing.getStepX()));
    }

    private static Mate partnerMate(KnuckleBlockEntity be) {
        BlockPos partnerPos = be.getPartnerLocalPos();
        ClientLevel clientLevel = Minecraft.getInstance().level;
        if (clientLevel == null || partnerPos == null) {
            return null;
        }
        ClientSubLevelContainer container = SubLevelContainer.getContainer(clientLevel);
        if (container == null
                || !(container.getSubLevel(be.getPartnerSubLevelId()) instanceof ClientSubLevel partnerSub)) {
            return null;
        }
        ClientSubLevel ownSub = Sable.HELPER.getContainingClient(be);
        Pose3dc ownPose = ownSub != null ? ownSub.renderPose() : null;
        if (ownPose == null
                || !(partnerSub.getLevel().getBlockEntity(partnerPos) instanceof KnuckleBlockEntity partnerBe)) {
            return null;
        }
        Pose3dc partnerPose = partnerSub.renderPose();
        Direction partnerFacing = partnerBe.getBlockState().getValue(KnuckleBlock.FACING);
        Vector3d hinge = KnuckleBlockEntity.hingeLocal(partnerPos, partnerFacing);
        partnerPose.transformPosition(hinge);
        ownPose.transformPositionInverse(hinge);
        Vector3d outward = new Vector3d(-partnerFacing.getStepX(), 0.0, -partnerFacing.getStepZ());
        partnerPose.transformNormal(outward);
        ownPose.transformNormalInverse(outward);
        return new Mate(hinge, outward, be.isController(ownSub.getUniqueId()));
    }
}
