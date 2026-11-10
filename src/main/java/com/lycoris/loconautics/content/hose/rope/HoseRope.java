package com.lycoris.loconautics.content.hose.rope;

import com.lycoris.loconautics.common.strand.AttachedRopePhysicsObject;
import com.lycoris.loconautics.content.hose.HoseType;
import com.lycoris.loconautics.content.hose.nozzle.HoseNozzleBlockEntity;
import dev.ryanhcode.sable.api.physics.constraint.PhysicsConstraintHandle;
import dev.ryanhcode.sable.api.physics.object.rope.RopeHandle;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public final class HoseRope extends AttachedRopePhysicsObject {

    public static final double COLLISION_RADIUS = 0.125;

    private static final double MIN_EXTENSION = 1.0;

    private final HoseType type;

    private double extensionGoal = 1.0;
    private double lastExtensionGoal = 1.0;
    private double lastFirstSegmentExtension = 1.0;
    private int lastPointCount;
    private boolean attachmentsDirty;

    public HoseRope(UUID id, Collection<Vector3d> points, HoseType type) {
        super(id, points, COLLISION_RADIUS);
        this.type = type;
    }

    public static HoseRope build(UUID id, HoseType type, @Nullable UUID winchSubLevelId, BlockPos winchPos,
                                 Vector3dc winchWorld, @Nullable UUID nozzleSubLevelId, BlockPos nozzleLocalPos,
                                 Vector3dc nozzleWorld, double length) {
        double dist = Math.max(0.5, Math.min(length, type.maxLength() + 1.0));
        double straight = new Vector3d(winchWorld).sub(nozzleWorld).length();
        double span = Math.max(straight, MIN_EXTENSION);
        int whole = (int) Math.floor(span);
        double first = span - whole;
        if (first < MIN_EXTENSION && whole > 0) {
            whole--;
            first += 1.0;
        }
        List<Vector3d> points = new ArrayList<>();
        points.add(new Vector3d(winchWorld));
        for (int k = 0; k <= whole; k++) {
            points.add(winchWorld.lerp(nozzleWorld, (first + k) / span, new Vector3d()));
        }
        HoseRope rope = new HoseRope(id, points, type);
        rope.putAttachment(RopeHandle.AttachmentPoint.START, winchSubLevelId, winchPos);
        rope.putAttachment(RopeHandle.AttachmentPoint.END, nozzleSubLevelId, nozzleLocalPos);
        double extension = first + Math.max(0.0, dist - straight);
        while (extension > 1.0) {
            rope.addPoint(winchWorld);
            extension -= 1.0;
        }
        rope.extensionGoal = extension;
        rope.lastExtensionGoal = extension;
        return rope;
    }

    public static HoseRope restore(UUID id, HoseType type, @Nullable UUID winchSubLevelId, BlockPos winchPos,
                                   @Nullable UUID nozzleSubLevelId, BlockPos nozzleLocalPos,
                                   List<Vector3d> points, double extensionGoal) {
        List<Vector3d> copy = new ArrayList<>(points.size());
        for (Vector3d p : points) copy.add(new Vector3d(p));
        HoseRope rope = new HoseRope(id, copy, type);
        rope.extensionGoal = extensionGoal;
        rope.lastExtensionGoal = extensionGoal;
        rope.putAttachment(RopeHandle.AttachmentPoint.START, winchSubLevelId, winchPos);
        rope.putAttachment(RopeHandle.AttachmentPoint.END, nozzleSubLevelId, nozzleLocalPos);
        return rope;
    }

    public double getExtension() {
        return extensionGoal;
    }

    public double getStretchRatio() {
        double desired = extensionGoal + (getPoints().size() - 2) * 1.0;
        if (desired <= 1.0e-4) return 1.0;
        return getCurrentExtension() / desired;
    }

    public boolean tickSnap() {
        return tickOverstretch(extensionGoal + (getPoints().size() - 2) * 1.0, type.snapStretchPercent());
    }

    public void reel(double movementSpeed, Vector3dc winchWorld) {
        double desiredExtension = extensionGoal + (getPoints().size() - 2) * 1.0;
        double currentExtension = getCurrentExtension();
        boolean stretched = currentExtension > desiredExtension * (1.0 + type.maxRopeStretchAllowed() / 100.0);
        if (stretched) {
            movementSpeed = Math.max(0.0, movementSpeed);
        }
        if (currentExtension > type.maxLength()) {
            movementSpeed = Math.min(0.0, movementSpeed);
        }
        double extension = extensionGoal + movementSpeed;
        if (extension < MIN_EXTENSION && getPoints().size() == 2) {
            extension = MIN_EXTENSION;
        } else {
            while (extension < 0.0) {
                removeFirstPoint();
                extension += 1.0;
                if (extension < MIN_EXTENSION && getPoints().size() == 2) {
                    extension = MIN_EXTENSION;
                    break;
                }
            }
            while (extension > 1.0) {
                addPoint(winchWorld);
                extension -= 1.0;
            }
            if (extension < MIN_EXTENSION && getPoints().size() <= 2) {
                extension = MIN_EXTENSION;
            }
        }
        lastExtensionGoal = extensionGoal;
        extensionGoal = extension;
    }

    public void prePhysicsTick(SubLevelPhysicsSystem system, ServerLevel level, double timeStep) {
        if (isAttachmentGrabbed(level, RopeHandle.AttachmentPoint.END,
                be -> be instanceof HoseNozzleBlockEntity nozzle && nozzle.isGrabbed())) {
            removeConstraint();
        } else if (constraintNeedsRebuild(level)) {
            reattachConstraint(system, level);
        }
        if (getPoints().size() != lastPointCount) {
            lastExtensionGoal = extensionGoal;
            lastPointCount = getPoints().size();
        }
        double extension = Mth.lerp(system.getPartialPhysicsTick(), lastExtensionGoal, extensionGoal);
        if (!Mth.equal(extension, lastFirstSegmentExtension)) {
            setFirstSegmentLength(extension);
            lastFirstSegmentExtension = extension;
        }
        if (attachmentsDirty) {
            applyAttachments(level);
            attachmentsDirty = false;
        }
    }

    @Override
    protected void configureConstraintMotors(PhysicsConstraintHandle handle) {
        dampAllAxes(handle);
    }

    public boolean hasJoint() {
        return constraint != null && constraint.isValid();
    }

    public void readJointImpulses(Vector3d linear, Vector3d angular) {
        if (hasJoint()) {
            constraint.getJointImpulses(linear, angular);
        }
    }

    public double getFirstSegmentRest() {
        return lastFirstSegmentExtension;
    }

    public double getDesiredExtension() {
        return extensionGoal + (getPoints().size() - 2) * 1.0;
    }

    @Override
    public void removeFirstPoint() {
        super.removeFirstPoint();
        attachmentsDirty = true;
    }

    @Override
    public void addPoint(Vector3dc position) {
        super.addPoint(position);
        attachmentsDirty = true;
    }

    @Override
    public void onAddition(SubLevelPhysicsSystem physicsSystem) {
        super.onAddition(physicsSystem);
        setFirstSegmentLength(extensionGoal);
        applyAttachments(physicsSystem.getLevel());
    }
}