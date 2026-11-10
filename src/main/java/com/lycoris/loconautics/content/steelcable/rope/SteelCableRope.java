package com.lycoris.loconautics.content.steelcable.rope;

import com.lycoris.loconautics.common.strand.AttachedRopePhysicsObject;
import com.lycoris.loconautics.common.strand.StrandKind;
import com.lycoris.loconautics.common.strand.StrandLength;
import com.lycoris.loconautics.content.hose.nozzle.HoseNozzleBlockEntity;
import com.lycoris.loconautics.content.steelcable.SteelCableType;
import dev.ryanhcode.sable.api.physics.constraint.PhysicsConstraintHandle;
import dev.ryanhcode.sable.api.physics.object.rope.RopeHandle;
import dev.ryanhcode.sable.sublevel.storage.holding.SubLevelHoldingChunkMap;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public final class SteelCableRope extends AttachedRopePhysicsObject {

    public static final double COLLISION_RADIUS = 0.125;

    public static final double MIN_EXTENSION = 0.1;

    private final SteelCableType type;
    private final double restLength;

    private boolean attachmentsApplied;

    private double extensionGoal = 1.0;
    private double lastExtensionGoal = 1.0;
    private double lastFirstSegmentExtension = 1.0;
    private int lastPointCount;

    public SteelCableRope(UUID id, Collection<Vector3d> points, SteelCableType type, double restLength) {
        super(id, points, COLLISION_RADIUS);
        this.type = type;
        this.restLength = restLength;
    }

    public static SteelCableRope build(UUID id, SteelCableType type, double restLength,
                                       @Nullable UUID subA, BlockPos localA, Vector3dc worldA,
                                       @Nullable UUID subB, BlockPos localB, Vector3dc worldB) {
        double length = Math.max(restLength, MIN_EXTENSION);
        int whole = (int) Math.floor(length);
        double first = length - whole;
        if (first < MIN_EXTENSION && whole > 0) {
            whole--;
            first += 1.0;
        }
        List<Vector3d> points = new ArrayList<>();
        points.add(new Vector3d(worldA));
        for (int k = 0; k <= whole; k++) {
            points.add(worldA.lerp(worldB, (first + k) / length, new Vector3d()));
        }
        SteelCableRope rope = new SteelCableRope(id, points, type, restLength);
        rope.putAttachment(RopeHandle.AttachmentPoint.START, subA, localA);
        rope.putAttachment(RopeHandle.AttachmentPoint.END, subB, localB);
        rope.extensionGoal = first;
        rope.lastExtensionGoal = first;
        return rope;
    }

    public static SteelCableRope restore(UUID id, SteelCableType type, double restLength,
                                         @Nullable UUID subA, BlockPos localA,
                                         @Nullable UUID subB, BlockPos localB, List<Vector3d> points,
                                         double extensionGoal) {
        List<Vector3d> copy = new ArrayList<>(points.size());
        for (Vector3d p : points) copy.add(new Vector3d(p));
        SteelCableRope rope = new SteelCableRope(id, copy, type, restLength);
        rope.extensionGoal = extensionGoal;
        rope.lastExtensionGoal = extensionGoal;
        rope.putAttachment(RopeHandle.AttachmentPoint.START, subA, localA);
        rope.putAttachment(RopeHandle.AttachmentPoint.END, subB, localB);
        return rope;
    }

    public StrandKind kind() {
        return type.kind();
    }

    public double getExtension() {
        return extensionGoal;
    }

    public boolean tickSnap() {
        return tickOverstretch(StrandLength.rest(extensionGoal, getPoints().size()), type.snapStretchPercent());
    }

    public boolean isTaut() {
        double desired = StrandLength.rest(extensionGoal, getPoints().size());
        return getCurrentExtension() > desired * (1.0 + type.maxStretchAllowed() / 100.0);
    }

    public void updateFirstSegmentExtension(double goal) {
        lastExtensionGoal = extensionGoal;
        extensionGoal = goal;
    }

    @Override
    public void addPoint(Vector3dc position) {
        super.addPoint(position);
        attachmentsApplied = false;
    }

    @Override
    public void removeFirstPoint() {
        super.removeFirstPoint();
        attachmentsApplied = false;
    }

    public void prePhysicsTick(SubLevelPhysicsSystem system, ServerLevel level, double timeStep) {
        // a sub level that was not resolvable at addition leaves that end unbound forever unless we keep retrying
        if (!attachmentsApplied) {
            attachmentsApplied = applyAttachments(level);
        }
        if (type.carriesFluid()
                && anyAttachmentGrabbed(level, be -> be instanceof HoseNozzleBlockEntity nozzle && nozzle.isGrabbed())) {
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
    }

    @Override
    protected boolean requireBothBodiesResolved() {
        return true;
    }

    @Override
    protected void configureConstraintMotors(PhysicsConstraintHandle handle) {
        dampAllAxes(handle);
    }

    @Override
    public void onAddition(SubLevelPhysicsSystem physicsSystem) {
        super.onAddition(physicsSystem);
        setFirstSegmentLength(extensionGoal);
        attachmentsApplied = applyAttachments(physicsSystem.getLevel());
    }

    @Override
    public void onUnloaded(SubLevelHoldingChunkMap holdingChunkMap, ChunkPos chunkPos) {
        super.onUnloaded(holdingChunkMap, chunkPos);
        attachmentsApplied = false;
    }

}