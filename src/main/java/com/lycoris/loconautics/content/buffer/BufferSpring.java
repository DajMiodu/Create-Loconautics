package com.lycoris.loconautics.content.buffer;

import com.lycoris.loconautics.common.impact.TrainImpacts;
import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.common.sable.SubLevelData;
import com.lycoris.loconautics.trainphysics.TrainConsist;
import com.lycoris.loconautics.common.impact.TrainKnockback;
import com.lycoris.loconautics.trainphysics.state.SableTrainRegistry;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.simulated_team.simulated.content.physics_staff.PhysicsStaffServerHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.UUID;

final class BufferSpring {

    private static final double WALL_PROBE = 1.0;
    private static final double WALL_STEP = 0.05;

    private BufferSpring() {
    }

    private record Body(ServerSubLevel sub, RigidBodyHandle handle, SableTrain train, boolean held, double inverse) {
    }

    static double pressPair(ServerSubLevel subA, RigidBodyHandle handleA, BufferBlockEntity beA,
                            ServerSubLevel subB, RigidBodyHandle handleB, BufferBlockEntity beB, double step) {
        Vector3d tipA = BufferBlockEntity.worldTip(subA, beA);
        Vector3d tipB = BufferBlockEntity.worldTip(subB, beB);
        Vector3d axis = BufferBlockEntity.worldOutward(subA, beA);
        double depth = -tipB.sub(tipA, new Vector3d()).dot(axis);
        if (depth <= 0.0 || subA.getMassTracker().isInvalid() || subB.getMassTracker().isInvalid()) {
            return Math.max(0.0, depth);
        }
        Body bodyA = bodyOf(subA, handleA, tipA, axis);
        Body bodyB = bodyOf(subB, handleB, tipB, axis);
        double inverse = bodyA.inverse() + bodyB.inverse();
        if (inverse <= 1.0e-9) {
            return depth;
        }
        double closing = pointVelocity(subA, handleA, tipA).sub(pointVelocity(subB, handleB, tipB)).dot(axis);
        double impulse = solve(depth, closing, inverse, step);
        if (impulse > 0.0) {
            Vector3d push = axis.mul(impulse, new Vector3d());
            deliver(bodyA, tipA, push.negate(new Vector3d()));
            deliver(bodyB, tipB, push);
        }
        return depth;
    }

    static double pressWorld(ServerSubLevel sub, RigidBodyHandle handle, BufferBlockEntity be, double step) {
        Vector3d tip = BufferBlockEntity.worldTip(sub, be);
        Vector3d axis = BufferBlockEntity.worldOutward(sub, be);
        double depth = wallDepth(sub.getLevel(), tip, axis);
        if (depth <= 0.0 || sub.getMassTracker().isInvalid()) {
            return depth;
        }
        Body body = bodyOf(sub, handle, tip, axis);
        if (body.inverse() <= 1.0e-9) {
            return depth;
        }
        double impulse = solve(depth, pointVelocity(sub, handle, tip).dot(axis), body.inverse(), step);
        if (impulse > 0.0) {
            deliver(body, tip, axis.mul(-impulse, new Vector3d()));
        }
        return depth;
    }

    private static double solve(double depth, double closing, double inverse, double step) {
        double frequency = depth > BufferTuning.SOFT_STROKE ? BufferTuning.HARD_FREQUENCY : BufferTuning.SOFT_FREQUENCY;
        double stiff = step * frequency * frequency;
        double nextClosing = (closing - stiff * depth)
                / (1.0 + 2.0 * BufferTuning.DAMPING_RATIO * step * frequency + step * stiff);
        nextClosing = Math.max(nextClosing, -BufferTuning.MAX_REBOUND);
        return (closing - nextClosing) / inverse;
    }

    private static Body bodyOf(ServerSubLevel sub, RigidBodyHandle handle, Vector3dc tip, Vector3dc axis) {
        SableTrain train = SableTrainRegistry.bySubLevel(sub.getUniqueId());
        if (train != null && (train.isGhost() || train.car() == null)) {
            train = null;
        }
        if (heldRake(sub, train)) {
            return new Body(sub, handle, train, true, 0.0);
        }
        double inverse = train != null
                ? 1.0 / TrainImpacts.rakeMass(train, sub)
                : sub.getMassTracker().getInverseNormalMass(
                sub.logicalPose().transformPositionInverse(new Vector3d(tip)), BufferBlockEntity.localOf(sub, axis));
        return new Body(sub, handle, train, false, inverse);
    }

    static boolean held(ServerSubLevel sub) {
        return PhysicsStaffServerHandler.get(sub.getLevel()).isLocked(sub);
    }

    private static boolean heldRake(ServerSubLevel sub, SableTrain train) {
        if (held(sub)) {
            return true;
        }
        if (train == null) {
            return false;
        }
        ServerLevel level = sub.getLevel();
        for (UUID id : TrainConsist.connectedSubLevels(level, sub.getUniqueId())) {
            ServerSubLevel member = SubLevelData.resolve(level, id);
            if (member != null && held(member)) {
                return true;
            }
        }
        return false;
    }

    private static void deliver(Body body, Vector3dc worldPoint, Vector3dc worldImpulse) {
        if (body.held()) {
            return;
        }
        if (body.train() == null) {
            Vector3d plotPoint = body.sub().logicalPose().transformPositionInverse(new Vector3d(worldPoint));
            body.handle().applyImpulseAtPoint(plotPoint, BufferBlockEntity.localOf(body.sub(), worldImpulse));
            return;
        }
        Vector3d deltaV = new Vector3d(worldImpulse).mul(body.inverse());
        ServerLevel level = body.sub().getLevel();
        for (UUID id : TrainConsist.connectedSubLevels(level, body.sub().getUniqueId())) {
            SableTrain member = SableTrainRegistry.bySubLevel(id);
            if (member == null || member.isGhost() || member.car() == null) {
                continue;
            }
            if (member.isDerailed() || member.isPhysics()) {
                TrainKnockback.applyKnockbackNow(member, deltaV);
            } else {
                TrainKnockback.applyRailRecoil(member, deltaV);
            }
        }
    }

    private static double wallDepth(ServerLevel level, Vector3dc tip, Vector3dc axis) {
        double depth = 0.0;
        for (double back = 0.0; back <= WALL_PROBE; back += WALL_STEP) {
            double x = tip.x() - axis.x() * back;
            double y = tip.y() - axis.y() * back;
            double z = tip.z() - axis.z() * back;
            BlockPos pos = BlockPos.containing(x, y, z);
            if (!level.isLoaded(pos) || !solidAt(level, pos, x - pos.getX(), y - pos.getY(), z - pos.getZ())) {
                return depth;
            }
            depth = back;
        }
        return depth;
    }

    private static boolean solidAt(ServerLevel level, BlockPos pos, double x, double y, double z) {
        BlockState state = level.getBlockState(pos);
        VoxelShape shape = state.getCollisionShape(level, pos);
        if (shape.isEmpty()) {
            return false;
        }
        for (AABB box : shape.toAabbs()) {
            if (box.contains(x, y, z)) {
                return true;
            }
        }
        return false;
    }

    private static Vector3d pointVelocity(ServerSubLevel sub, RigidBodyHandle handle, Vector3dc worldPoint) {
        Vector3d centre = sub.logicalPose().transformPosition(new Vector3d(sub.getMassTracker().getCenterOfMass()));
        Vector3d arm = new Vector3d(worldPoint).sub(centre);
        return handle.getAngularVelocity(new Vector3d()).cross(arm).add(handle.getLinearVelocity(new Vector3d()));
    }
}
