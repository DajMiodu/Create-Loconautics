package com.lycoris.loconautics.common.strand;

import com.lycoris.loconautics.common.sable.BodyRef;
import dev.ryanhcode.sable.api.physics.PhysicsPipeline;
import dev.ryanhcode.sable.api.physics.PhysicsPipelineBody;
import dev.ryanhcode.sable.api.physics.constraint.ConstraintJointAxis;
import dev.ryanhcode.sable.api.physics.constraint.FreeConstraintConfiguration;
import dev.ryanhcode.sable.api.physics.constraint.PhysicsConstraintHandle;
import dev.ryanhcode.sable.api.physics.object.rope.RopeHandle;
import dev.ryanhcode.sable.api.physics.object.rope.RopePhysicsObject;
import dev.ryanhcode.sable.physics.impl.rapier.Rapier3D;
import dev.ryanhcode.sable.physics.impl.rapier.rope.RapierRopeHandle;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.holding.SubLevelHoldingChunkMap;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import dev.ryanhcode.sable.sublevel.system.ticket.PhysicsChunkTicketManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

public abstract class AttachedRopePhysicsObject extends RopePhysicsObject {

    private static final int SNAP_TICKS = 5;

    public record Attachment(@Nullable UUID subLevelId, BlockPos blockPos) {}

    protected final UUID id;
    protected final Map<RopeHandle.AttachmentPoint, Attachment> attachments =
            new EnumMap<>(RopeHandle.AttachmentPoint.class);

    @Nullable protected PhysicsConstraintHandle constraint;

    @Nullable private UUID constraintBodyA;
    @Nullable private UUID constraintBodyB;

    private final List<Vector3d> lastNetworkedPoints = new ArrayList<>();

    private final StrandKnotGrab knotGrab = new StrandKnotGrab();

    private final Map<RopeHandle.AttachmentPoint, Vector3d> lastCenters = new EnumMap<>(RopeHandle.AttachmentPoint.class);

    private boolean networkingStopped;
    private int overstretchTicks;

    protected AttachedRopePhysicsObject(UUID id, Collection<Vector3d> points, double collisionRadius) {
        super(points, collisionRadius);
        this.id = id;
    }

    public UUID getUUID() {
        return id;
    }

    @Nullable
    public StrandEnd endOf(RopeHandle.AttachmentPoint point) {
        Attachment att = attachments.get(point);
        return att == null ? null : new StrandEnd(att.subLevelId(), att.blockPos());
    }

    protected void putAttachment(RopeHandle.AttachmentPoint point, @Nullable UUID subLevelId, BlockPos localPos) {
        attachments.put(point, new Attachment(subLevelId, localPos));
    }

    public double getCurrentExtension() {
        return StrandLength.current(this);
    }

    protected Vector3d resolveHolderPoint(@Nullable BlockEntity blockEntity, BlockPos blockPos, Vector3d blockCenter) {
        if (blockEntity instanceof StrandHolder holder) {
            Vec3 p = holder.getAttachmentPoint();
            traceAnchor(blockEntity, blockPos, new Vector3d(p.x, p.y, p.z), blockCenter);
            return new Vector3d(p.x, p.y, p.z);
        }
        traceAnchor(blockEntity, blockPos, blockCenter, blockCenter);
        return blockCenter;
    }

    // temporary, tag issue215, one line per new anchor of an end so a cable on the wrong side of a connector shows its point
    private final Map<BlockPos, Vector3d> tracedAnchors = new HashMap<>();

    private void traceAnchor(@Nullable BlockEntity blockEntity, BlockPos blockPos, Vector3d point, Vector3d blockCenter) {
        Vector3d known = tracedAnchors.put(blockPos.immutable(), new Vector3d(point));
        if (known != null && known.distanceSquared(point) < 1.0e-6) {
            return;
        }
        com.lycoris.loconautics.LoconauticsConstants.LOGGER.info(
                "[issue215] anchor rope={} {} holder={} pos={} state={} point=({}, {}, {}) offFromCenter=({}, {}, {})",
                id.toString().substring(0, 8), getClass().getSimpleName(),
                blockEntity == null ? "none" : blockEntity.getClass().getSimpleName(), blockPos,
                blockEntity == null ? "?" : blockEntity.getBlockState(), point.x, point.y, point.z,
                point.x - blockCenter.x, point.y - blockCenter.y, point.z - blockCenter.z);
    }

    protected boolean requireBothBodiesResolved() {
        return false;
    }

    public StrandKnotGrab knotGrab() {
        return knotGrab;
    }

    @Override
    public void updatePose() {
        if (!knotGrab.engaged()) {
            super.updatePose();
        }
    }

    @Nullable
    protected Vector3d pinnedEnd(ServerLevel level, RopeHandle.AttachmentPoint point) {
        Attachment att = attachments.get(point);
        if (att == null) {
            return null;
        }
        return StrandEndpoints.worldPoint(level, SubLevelContainer.getContainer(level), att.subLevelId(),
                att.blockPos());
    }

    public void driveKnotGrab(SubLevelPhysicsSystem system, ServerLevel level, double timeStep) {
        if (knotGrab.idle()) {
            return;
        }
        if (knotGrab.tickAndReportRelease(level, points, pinnedEnd(level, RopeHandle.AttachmentPoint.START),
                pinnedEnd(level, RopeHandle.AttachmentPoint.END), timeStep, getCollisionRadius())) {
            system.removeObject(this);
        }
    }

    public abstract void prePhysicsTick(SubLevelPhysicsSystem system, ServerLevel level, double timeStep);

    protected void configureConstraintMotors(PhysicsConstraintHandle handle) {
        for (ConstraintJointAxis axis : ConstraintJointAxis.LINEAR) {
            handle.setMotor(axis, 0.0, 0.0, 0.25, false, 0.0);
        }
    }

    protected static void dampAllAxes(PhysicsConstraintHandle handle) {
        for (ConstraintJointAxis axis : ConstraintJointAxis.ANGULAR) {
            handle.setMotor(axis, 0.0, 0.0, 1.3, false, 0.0);
        }
        for (ConstraintJointAxis axis : ConstraintJointAxis.LINEAR) {
            handle.setMotor(axis, 0.0, 0.0, 0.25, false, 0.0);
        }
    }

    protected Vector3d holderPoint(ServerLevel level, Attachment att, @Nullable ServerSubLevel sub) {
        BlockPos bp = att.blockPos();
        BlockEntity be = (sub != null ? sub.getLevel() : level).getBlockEntity(bp);
        Vector3d blockCenter = new Vector3d(bp.getX() + 0.5, bp.getY() + 0.5, bp.getZ() + 0.5);
        return resolveHolderPoint(be, bp, blockCenter);
    }

    protected boolean applyAttachments(ServerLevel level) {
        boolean all = true;
        for (Map.Entry<RopeHandle.AttachmentPoint, Attachment> e : attachments.entrySet()) {
            Attachment att = e.getValue();
            BodyRef body = BodyRef.of(level, att.subLevelId());
            if (body.absent()) {
                all = false;
                continue;
            }
            setAttachment(e.getKey(), holderPoint(level, att, body.sub()), body.sub());
        }
        return all;
    }

    // the native body gets a new center of mass whenever its mass changes, an end bound before that is not re derived
    void checkAnchors(ServerLevel level) {
        if (getPoints().size() < 2) {
            return;
        }
        boolean centerMoved = false;
        for (Map.Entry<RopeHandle.AttachmentPoint, Attachment> e : attachments.entrySet()) {
            ServerSubLevel sub = BodyRef.of(level, e.getValue().subLevelId()).sub();
            if (sub == null || sub.getMassTracker().getCenterOfMass() == null) {
                continue;
            }
            Vector3d center = new Vector3d(sub.getMassTracker().getCenterOfMass());
            Vector3d known = lastCenters.put(e.getKey(), center);
            if (known != null && known.distanceSquared(center) > 1.0e-8) {
                centerMoved = true;
            }
        }
        if (centerMoved) {
            com.lycoris.loconautics.LoconauticsConstants.LOGGER.info(
                    "[ropeanchor] {} rope={} center of mass moved, attachments reapplied", getClass().getSimpleName(),
                    id.toString().substring(0, 8));
            applyAttachments(level);
        }
    }

    public boolean jointImpulses(Vector3d linear, Vector3d angular) {
        if (constraint == null || !constraint.isValid()) {
            return false;
        }
        constraint.getJointImpulses(linear, angular);
        return true;
    }

    protected void reattachConstraint(SubLevelPhysicsSystem system, ServerLevel level) {
        removeConstraint();
        Attachment start = attachments.get(RopeHandle.AttachmentPoint.START);
        Attachment end = attachments.get(RopeHandle.AttachmentPoint.END);
        if (start == null || end == null) {
            return;
        }
        BodyRef refA = BodyRef.of(level, start.subLevelId());
        BodyRef refB = BodyRef.of(level, end.subLevelId());
        if (refA.absent() || refB.absent()) {
            return;
        }
        ServerSubLevel bodyA = refA.sub();
        ServerSubLevel bodyB = refB.sub();
        if (bodyA == bodyB) {
            return;
        }
        if (requireBothBodiesResolved() && (bodyA == null || bodyB == null)) {
            return;
        }
        Vector3d pos1 = holderPoint(level, start, bodyA);
        Vector3d pos2 = holderPoint(level, end, bodyB);
        PhysicsPipeline pipeline = system.getPipeline();
        FreeConstraintConfiguration config = new FreeConstraintConfiguration(pos1, pos2, new Quaterniond());
        constraint = pipeline.addConstraint(bodyA, bodyB, config);
        if (constraint == null) {
            return;
        }
        constraintBodyA = start.subLevelId();
        constraintBodyB = end.subLevelId();
        configureConstraintMotors(constraint);
    }

    // isvalid only checks the joint, a body sable already removed slips right past it
    protected boolean constraintBodiesLive(ServerLevel level) {
        return !BodyRef.of(level, constraintBodyA).absent() && !BodyRef.of(level, constraintBodyB).absent();
    }

    public boolean constraintLive() {
        return constraint != null && constraint.isValid();
    }

    protected boolean constraintNeedsRebuild(ServerLevel level) {
        return constraint == null || !constraint.isValid() || !constraintBodiesLive(level);
    }

    protected boolean anyAttachmentGrabbed(ServerLevel level, Predicate<BlockEntity> grabbedCheck) {
        for (Attachment att : attachments.values()) {
            if (isAttachmentGrabbed(level, att, grabbedCheck)) {
                return true;
            }
        }
        return false;
    }

    protected boolean isAttachmentGrabbed(ServerLevel level, RopeHandle.AttachmentPoint point,
                                          Predicate<BlockEntity> grabbedCheck) {
        Attachment att = attachments.get(point);
        return att != null && isAttachmentGrabbed(level, att, grabbedCheck);
    }

    private boolean isAttachmentGrabbed(ServerLevel level, Attachment att, Predicate<BlockEntity> grabbedCheck) {
        if (att.subLevelId() == null) {
            return false;
        }
        ServerSubLevel sub = BodyRef.of(level, att.subLevelId()).sub();
        if (sub == null) {
            return false;
        }
        BlockEntity be = sub.getLevel().getBlockEntity(att.blockPos());
        return be != null && grabbedCheck.test(be);
    }

    protected void attachBody(ServerLevel level, RopeHandle.AttachmentPoint point, PhysicsPipelineBody body,
                              Vector3dc localPoint) {
        if (!isActive() || !(handle instanceof RapierRopeHandle rapier)) {
            return;
        }
        Rapier3D.setRopeAttachment(Rapier3D.getSceneHandle(level), rapier.handle(), Rapier3D.getID(body),
                localPoint.x(), localPoint.y(), localPoint.z(), point == RopeHandle.AttachmentPoint.END);
    }

    protected void removeConstraint() {
        if (constraint != null) {
            if (constraint.isValid()) {
                constraint.remove();
            }
            constraint = null;
            constraintBodyA = null;
            constraintBodyB = null;
        }
    }

    @Override
    public void onAddition(SubLevelPhysicsSystem physicsSystem) {
        super.onAddition(physicsSystem);
        if (knotGrab.traceTicks > 0) {
            com.lycoris.loconautics.LoconauticsConstants.LOGGER.info("[knotgrab] rope added to system id={}", id);
        }
    }

    @Override
    public void onRemoved() {
        super.onRemoved();
        knotGrab.reset();
        if (knotGrab.traceTicks > 0) {
            com.lycoris.loconautics.LoconauticsConstants.LOGGER.info("[knotgrab] rope removed from system id={}", id);
        }
        removeConstraint();
    }

    @Override
    public void onUnloaded(SubLevelHoldingChunkMap holdingChunkMap, ChunkPos chunkPos) {
        super.onUnloaded(holdingChunkMap, chunkPos);
        knotGrab.reset();
        removeConstraint();
    }

    public boolean areAttachmentsLoaded(ServerLevel level) {
        for (Attachment att : attachments.values()) {
            if (att.subLevelId() != null) {
                // getsublevel lies about a removed body for a tick or two, that is when rapier panics
                if (BodyRef.of(level, att.subLevelId()).absent()) {
                    return false;
                }
                continue;
            }
            if (!PhysicsChunkTicketManager.isChunkLoadedEnough(
                    level, att.blockPos().getX() >> 4, att.blockPos().getZ() >> 4)) {
                return false;
            }
        }
        return true;
    }

    public boolean tickOverstretch(double desiredExtension, float snapStretchPercent) {
        if (snapStretchPercent <= 0.0f || desiredExtension <= 1.0e-4
                || getCurrentExtension() <= desiredExtension * (1.0 + snapStretchPercent / 100.0)) {
            overstretchTicks = 0;
            return false;
        }
        return ++overstretchTicks >= SNAP_TICKS;
    }

    public boolean needsSync() {
        List<Vector3d> pts = getPoints();
        if (lastNetworkedPoints.size() != pts.size()) {
            return true;
        }
        double threshold = Mth.square(0.00625);
        for (int i = 0; i < pts.size(); i++) {
            if (pts.get(i).distanceSquared(lastNetworkedPoints.get(i)) > threshold) {
                return true;
            }
        }
        return false;
    }

    public boolean isNetworkingStopped() {
        return networkingStopped;
    }

    public void setNetworkingStopped(boolean stopped) {
        networkingStopped = stopped;
    }

    public void justSynced() {
        lastNetworkedPoints.clear();
        for (Vector3d p : getPoints()) {
            lastNetworkedPoints.add(new Vector3d(p));
        }
    }
}
