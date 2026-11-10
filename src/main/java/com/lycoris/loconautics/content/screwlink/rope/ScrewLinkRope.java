package com.lycoris.loconautics.content.screwlink.rope;

import com.lycoris.loconautics.common.strand.AttachedRopePhysicsObject;
import com.lycoris.loconautics.common.strand.RopeGeometry;
import com.lycoris.loconautics.content.screwlink.ScrewLinkHook;
import dev.ryanhcode.sable.api.physics.object.rope.RopeHandle;
import dev.ryanhcode.sable.sublevel.storage.holding.SubLevelHoldingChunkMap;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public final class ScrewLinkRope extends AttachedRopePhysicsObject {

    public static final double COLLISION_RADIUS = 0.125;

    private static final Vector3dc HOOK_ANCHOR = new Vector3d();

    private final double restLength;

    @Nullable private ScrewLinkHook hook;
    private boolean attachmentsApplied;

    private ScrewLinkRope(UUID id, Collection<Vector3d> points, double restLength) {
        super(id, points, COLLISION_RADIUS);
        this.restLength = restLength;
    }

    public static ScrewLinkRope build(UUID id, double restLength, @Nullable UUID subA, BlockPos localA,
                                      Vector3dc worldA, Vector3dc worldB) {
        ScrewLinkRope rope = new ScrewLinkRope(id, RopeGeometry.tautPolyline(worldA, worldB, restLength), restLength);
        rope.putAttachment(RopeHandle.AttachmentPoint.START, subA, localA);
        return rope;
    }

    public static ScrewLinkRope restore(UUID id, double restLength, @Nullable UUID subA, BlockPos localA,
                                        List<Vector3d> points) {
        List<Vector3d> copy = new ArrayList<>(points.size());
        for (Vector3d p : points) {
            copy.add(new Vector3d(p));
        }
        ScrewLinkRope rope = new ScrewLinkRope(id, copy, restLength);
        rope.putAttachment(RopeHandle.AttachmentPoint.START, subA, localA);
        return rope;
    }

    public double getRestLength() {
        return restLength;
    }

    @Nullable
    public ScrewLinkHook getHook() {
        return hook;
    }

    public void setHook(@Nullable ScrewLinkHook hook) {
        this.hook = hook;
    }

    public void coupleTo(@Nullable UUID subB, BlockPos localB) {
        putAttachment(RopeHandle.AttachmentPoint.END, subB, localB);
        attachmentsApplied = false;
    }

    public void releaseHook() {
        if (hook == null) {
            return;
        }
        List<Vector3d> points = getPoints();
        setAttachment(RopeHandle.AttachmentPoint.END, points.get(points.size() - 1), null);
        hook = null;
    }

    public void decouple() {
        attachments.remove(RopeHandle.AttachmentPoint.END);
        removeConstraint();
        attachmentsApplied = false;
    }

    @Override
    @Nullable
    protected Vector3d pinnedEnd(ServerLevel level, RopeHandle.AttachmentPoint point) {
        if (point == RopeHandle.AttachmentPoint.END && hook != null) {
            return new Vector3d(hook.getPose().position());
        }
        return super.pinnedEnd(level, point);
    }

    @Override
    public void prePhysicsTick(SubLevelPhysicsSystem system, ServerLevel level, double timeStep) {
        if (!attachmentsApplied) {
            attachmentsApplied = applyAttachments(level);
        }
        if (hook == null) {
            if (constraintNeedsRebuild(level)) {
                reattachConstraint(system, level);
            }
            return;
        }
        if (!hook.isActive()) {
            releaseHook();
            return;
        }
        hook.updatePose();
        // re applied every substep because addpoint and removefirstpoint only replay the start binding
        attachBody(level, RopeHandle.AttachmentPoint.END, hook, HOOK_ANCHOR);
        hook.driveGrabs(hook, level);
    }

    @Override
    public void onAddition(SubLevelPhysicsSystem physicsSystem) {
        super.onAddition(physicsSystem);
        attachmentsApplied = applyAttachments(physicsSystem.getLevel());
    }

    @Override
    public void onUnloaded(SubLevelHoldingChunkMap holdingChunkMap, ChunkPos chunkPos) {
        hook = null;
        super.onUnloaded(holdingChunkMap, chunkPos);
        attachmentsApplied = false;
    }

}
