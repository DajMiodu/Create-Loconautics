package com.lycoris.loconautics.content.ghostwheel;

import dev.ryanhcode.sable.api.physics.callback.BlockSubLevelCollisionCallback;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

public final class GhostWheelCollisionCallback implements BlockSubLevelCollisionCallback {

    public static final GhostWheelCollisionCallback INSTANCE = new GhostWheelCollisionCallback();

    private GhostWheelCollisionCallback() {
    }

    private static boolean isOwnCar(ServerLevel level, BlockPos padPos, BlockPos otherPos) {
        java.util.UUID padSub = subLevelIdAt(level, padPos);
        java.util.UUID otherSub = subLevelIdAt(level, otherPos);
        if (padSub == null || otherSub == null) {
            return false;
        }
        java.util.UUID parent = com.lycoris.loconautics.trainphysics.wheels.GhostWheelBodies.parentCarOf(padSub);
        return otherSub.equals(parent);
    }

    private static java.util.UUID subLevelIdAt(ServerLevel level, BlockPos pos) {
        return dev.ryanhcode.sable.companion.SableCompanion.INSTANCE
                .getContaining(level, pos.getX() >> 4, pos.getZ() >> 4) instanceof
                dev.ryanhcode.sable.sublevel.ServerSubLevel sub ? sub.getUniqueId() : null;
    }

    @Override
    public CollisionResult sable$onCollision(BlockPos blockPos, @Nullable BlockPos otherBlockPos,
                                             Vector3d hitPos, double impactVelocity) {
        if (otherBlockPos == null) {
            return new CollisionResult(new Vector3d(), true);
        }
        SubLevelPhysicsSystem system = SubLevelPhysicsSystem.getCurrentlySteppingSystem();
        if (system == null) {
            return CollisionResult.NONE;
        }
        ServerLevel level = system.getLevel();
        if (level == null) {
            return CollisionResult.NONE;
        }
        if (isOwnCar(level, blockPos, otherBlockPos)) {
            return new CollisionResult(new Vector3d(), true);
        }
        // the rail glue owns a railed car, so its plate ignores every world block, track and ground alike
        if (subLevelIdAt(level, otherBlockPos) == null) {
            java.util.UUID padSub = subLevelIdAt(level, blockPos);
            if (padSub != null && com.lycoris.loconautics.trainphysics.wheels.GhostWheelBodies.isRailedPlate(padSub)) {
                return new CollisionResult(new Vector3d(), true);
            }
        }
        return CollisionResult.NONE;
    }
}
