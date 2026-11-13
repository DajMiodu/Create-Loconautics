package com.lycoris.loconautics.content.hose.winch;

import com.lycoris.loconautics.client.LoconauticsPartialModels;
import com.lycoris.loconautics.client.RopeRendering;
import com.lycoris.loconautics.common.strand.StrandRenderer;
import com.lycoris.loconautics.content.steelcable.SteelCableHover;
import com.lycoris.loconautics.common.strand.StrandAnchors;
import com.lycoris.loconautics.content.hose.HoseNozzleKnot;
import com.lycoris.loconautics.content.hose.HoseSwell;
import com.lycoris.loconautics.content.hose.rope.HoseRope;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.content.kinetics.base.DirectionalAxisKineticBlock;
import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;
import com.simibubi.create.content.kinetics.base.IRotate;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3d;

import java.util.List;
import java.util.UUID;

public class HoseWinchRenderer implements BlockEntityRenderer<HoseWinchBlockEntity> {

    public HoseWinchRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(HoseWinchBlockEntity be, float partialTicks, PoseStack ps,
                       MultiBufferSource buffer, int light, int overlay) {
        Level level = be.getLevel();
        if (level == null) {
            return;
        }
        renderKinetics(be, ps, buffer, light, partialTicks);
        if (be.clientHasStrand()) {
            renderStrand(be, level, ps, buffer, partialTicks);
        }
    }

    private void renderKinetics(HoseWinchBlockEntity be, PoseStack ps, MultiBufferSource buffer, int light,
                                 float partialTicks) {
        BlockState state = be.getBlockState();
        if (!(state.getBlock() instanceof IRotate rotate)) {
            return;
        }
        Direction.Axis axis = rotate.getRotationAxis(state);
        float angle = be.coilAngleRadians(partialTicks);

        KineticBlockEntityRenderer.renderRotatingKineticBlock(be, KineticBlockEntityRenderer.shaft(axis), ps,
                buffer.getBuffer(RenderType.solid()), light);

        if (be.clientHasStrand()) {
            SuperByteBuffer coil = CachedBuffers.partial(
                    LoconauticsPartialModels.HOSE_WINCH_COIL, state);
            spin(coil, state, axis, angle).light(light).renderInto(ps, buffer.getBuffer(RenderType.solid()));
        }
    }

    private static SuperByteBuffer spin(SuperByteBuffer buffer, BlockState state, Direction.Axis axis, float angle) {
        buffer.rotateCentered(angle, Direction.get(Direction.AxisDirection.POSITIVE, axis));
        return placeAsBlock(buffer, state);
    }

    private static SuperByteBuffer placeAsBlock(SuperByteBuffer buffer, BlockState state) {
        Direction facing = state.getValue(DirectionalKineticBlock.FACING);
        boolean along = state.getValue(DirectionalAxisKineticBlock.AXIS_ALONG_FIRST_COORDINATE);
        int xRot = facing.getAxis().isHorizontal() ? 270 : (facing == Direction.UP ? 180 : 0);
        int yRot = switch (facing) {
            case NORTH -> 0;
            case SOUTH -> 180;
            case EAST -> 90;
            case WEST -> 270;
            default -> along ? 0 : 90;
        };
        buffer.rotateCentered(-yRot * Mth.DEG_TO_RAD, Direction.UP);
        buffer.rotateCentered(-xRot * Mth.DEG_TO_RAD, Direction.EAST);
        return buffer;
    }

    private void renderStrand(HoseWinchBlockEntity be, Level level, PoseStack ps, MultiBufferSource buffer,
                              float partialTicks) {
        ClientSubLevel ownerSub = dev.ryanhcode.sable.Sable.HELPER.getContainingClient(be);
        Pose3dc ownerPose = ownerSub == null ? null : ownerSub.renderPose();
        List<Vec3> points = be.clientRopePoints(partialTicks);
        if (points == null) {
            Vec3 nozzlePoint = resolveNozzlePoint(be, level);
            if (nozzlePoint == null) {
                return;
            }
            Vec3 winchPoint = be.getAttachmentPoint();
            if (ownerPose != null) {
                winchPoint = transform(ownerPose, winchPoint, false);
            }
            points = buildPolyline(winchPoint, nozzlePoint, be.clientLength());
            if (ownerPose != null) {
                points = toLocal(ownerPose, points);
            }
        }
        if (points.size() < 2) {
            return;
        }
        Vec3 liveNozzle = resolveNozzlePoint(be, level);
        if (liveNozzle != null) {
            points = new java.util.ArrayList<>(points);
            points.set(points.size() - 1, ownerPose == null ? liveNozzle : transform(ownerPose, liveNozzle, true));
        }
        List<Vec3> worldPoints = points;
        if (ownerPose != null) {
            worldPoints = new java.util.ArrayList<>(points.size());
            for (Vec3 p : points) {
                worldPoints.add(transform(ownerPose, p, false));
            }
        }
        BlockPos ownerPos = be.getBlockPos();
        VertexConsumer vb = buffer.getBuffer(RenderType.solid());

        SuperByteBuffer knot = CachedBuffers.partialFacing(
                LoconauticsPartialModels.HOSE_KNOT, be.getBlockState(), Direction.NORTH);

        boolean nozzleKnot = liveNozzle != null && LoconauticsPartialModels.HOSE_NOZZLE_KNOT.get() != null;
        Quaternionf orient = new Quaternionf();
        double cumDist = 0.0;
        for (int i = 0; i < points.size() - 1; i++) {
            Vec3 p0 = points.get(i);
            Vec3 p1 = points.get(i + 1);
            double length = RopeRendering.orientSegment(orient, p0, p1);
            if (length < 1.0e-5) {
                continue;
            }

            if (i > 0 && !(nozzleKnot && i == points.size() - 2)) {
                renderKnot(ps, vb, ownerPos, p0, orient, worldLight(level, worldPoints.get(i)), knot);
            }

            SuperByteBuffer middle = CachedBuffers.partialFacing(
                    LoconauticsPartialModels.HOSE_STRAND, be.getBlockState(), Direction.NORTH);
            float swell = hoseSwell(be, cumDist, partialTicks);
            ps.pushPose();
            ps.translate(p0.x - ownerPos.getX(), p0.y - ownerPos.getY(), p0.z - ownerPos.getZ());
            ps.mulPose(orient);
            ps.scale(swell, (float) length, swell);
            ps.translate(-0.5, 0.0, -0.5);
            middle.light(worldLight(level, worldPoints.get(i))).renderInto(ps, buffer.getBuffer(RenderType.solid()));
            ps.popPose();
            cumDist += length;
        }
        if (nozzleKnot) {
            SuperByteBuffer nozzleBuffer = CachedBuffers.partialFacing(
                    LoconauticsPartialModels.HOSE_NOZZLE_KNOT, be.getBlockState(), Direction.NORTH);
            Quaternionf nozzleOrient = HoseNozzleKnot.atNozzle(orient);
            org.joml.Vector3f back = HoseNozzleKnot.backOff(nozzleOrient);
            renderKnot(ps, vb, ownerPos, points.getLast().add(back.x, back.y, back.z), nozzleOrient,
                    worldLight(level, worldPoints.getLast()), nozzleBuffer);
        } else {
            renderKnot(ps, vb, ownerPos, points.getLast(), orient, worldLight(level, worldPoints.getLast()), knot);
        }

        if (holdingHoseItem()) {
            RopeRendering.renderHitbox(ps, buffer, points, ownerPos, (float) HoseRope.COLLISION_RADIUS);
        } else if (be.clientStrandId() != null && be.clientStrandId().equals(SteelCableHover.hoveredWinchHose)) {
            RopeRendering.renderHitbox(ps, buffer, points, ownerPos, StrandRenderer.OUTLINE_RADIUS);
        }
    }

    private static Vec3 transform(Pose3dc pose, Vec3 p, boolean toLocal) {
        Vector3d in = new Vector3d(p.x, p.y, p.z);
        Vector3d out = toLocal ? pose.transformPositionInverse(in, new Vector3d())
                : pose.transformPosition(in, new Vector3d());
        return new Vec3(out.x, out.y, out.z);
    }

    private static List<Vec3> toLocal(Pose3dc pose, List<Vec3> world) {
        List<Vec3> local = new java.util.ArrayList<>(world.size());
        for (Vec3 p : world) {
            local.add(transform(pose, p, true));
        }
        return local;
    }

    private static boolean holdingHoseItem() {
        var player = net.minecraft.client.Minecraft.getInstance().player;
        if (player == null) {
            return false;
        }
        return player.getMainHandItem().getItem() instanceof com.lycoris.loconautics.content.hose.HoseItem
                || player.getOffhandItem().getItem() instanceof com.lycoris.loconautics.content.hose.HoseItem;
    }

    private static List<Vec3> buildPolyline(Vec3 winch, Vec3 nozzle, float paidOut) {
        double straight = winch.distanceTo(nozzle);
        if (straight < 1.0e-4) {
            return List.of(winch, nozzle);
        }
        Vec3 dir = nozzle.subtract(winch).scale(1.0 / straight);
        int segments = Math.max(1, (int) Math.ceil(straight - 1.0e-6));
        double firstLen = straight - (segments - 1);

        List<Vec3> points = new java.util.ArrayList<>(segments + 1);
        points.add(winch);
        double cum = firstLen;
        points.add(winch.add(dir.scale(cum)));
        for (int i = 1; i < segments; i++) {
            cum += 1.0;
            points.add(winch.add(dir.scale(cum)));
        }
        return points;
    }

    private static void renderKnot(PoseStack ps, VertexConsumer vb, BlockPos ownerPos,
                                   Vec3 point, Quaternionf orient, int light, SuperByteBuffer knot) {
        ps.pushPose();
        ps.translate(point.x - ownerPos.getX(), point.y - ownerPos.getY(), point.z - ownerPos.getZ());
        ps.mulPose(orient);
        ps.translate(-0.5, -0.5, -0.5);
        knot.light(light).renderInto(ps, vb);
        ps.popPose();
    }

    private static Vec3 resolveNozzlePoint(HoseWinchBlockEntity be, Level level) {
        UUID sub = be.clientNozzleSub();
        BlockPos local = be.clientNozzleLocal();
        if (sub == null || local == null) {
            return null;
        }
        SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null || !(container.getSubLevel(sub) instanceof ClientSubLevel clientSub)) {
            return null;
        }
        Pose3dc pose = clientSub.renderPose();
        Vec3 anchor = StrandAnchors.hoseNozzle(local);
        Vector3d world = pose.transformPosition(
                new Vector3d(anchor.x, anchor.y, anchor.z), new Vector3d());
        return new Vec3(world.x, world.y, world.z);
    }

    private static float hoseSwell(HoseWinchBlockEntity be, double cumDist, float partialTicks) {
        if (!be.clientHasFluid()) {
            return 1.0f;
        }
        boolean reachesNozzle = be.clientReach() >= be.clientLength() - 0.001f;
        if (reachesNozzle) {
            double phase = be.wavePhase(partialTicks) - cumDist * HoseSwell.PHASE_STEP * Math.PI * 2.0;
            return HoseSwell.scale(HoseSwell.wavePx(phase));
        }
        return HoseSwell.scale(HoseSwell.frontPx(be.clientFill(partialTicks), cumDist));
    }

    private static int worldLight(Level level, Vec3 point) {
        return LevelRenderer.getLightColor(level, BlockPos.containing(point.x, point.y, point.z));
    }

    @Override
    public boolean shouldRenderOffScreen(HoseWinchBlockEntity be) {
        return true;
    }

    @Override
    public AABB getRenderBoundingBox(HoseWinchBlockEntity be) {
        AABB box = be.getRenderBoundingBox();
        Level level = be.getLevel();
        if (level == null) {
            return box;
        }
        Vec3 nozzle = resolveNozzlePoint(be, level);
        return nozzle == null ? box : box.minmax(new AABB(nozzle, nozzle).inflate(1.0));
    }

    @Override
    public boolean shouldRender(HoseWinchBlockEntity be, Vec3 cameraPos) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 128;
    }
}