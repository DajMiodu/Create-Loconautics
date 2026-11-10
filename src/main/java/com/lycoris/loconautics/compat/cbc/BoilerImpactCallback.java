package com.lycoris.loconautics.compat.cbc;

import com.lycoris.loconautics.config.LoconauticsConfig;
import com.lycoris.loconautics.content.boiler.firebox.FireboxBlock;
import dev.ryanhcode.sable.api.physics.callback.BlockSubLevelCollisionCallback;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level.ExplosionInteraction;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

public final class BoilerImpactCallback implements BlockSubLevelCollisionCallback {

    public static final BoilerImpactCallback INSTANCE = new BoilerImpactCallback();

    private BoilerImpactCallback() {
    }

    @Override
    public CollisionResult sable$onCollision(BlockPos blockPos, @Nullable BlockPos otherBlockPos,
                                             Vector3d hitPos, double impactVelocity) {
        if (!LoconauticsConfig.CRASH_EXPLODE_ENABLED.get()) {
            return CollisionResult.NONE;
        }
        double threshold = LoconauticsConfig.CRASH_IMPACT_THRESHOLD.get();
        if (impactVelocity < threshold) {
            return CollisionResult.NONE;
        }

        SubLevelPhysicsSystem system = SubLevelPhysicsSystem.getCurrentlySteppingSystem();
        if (system == null) {
            return CollisionResult.NONE;
        }
        ServerLevel level = system.getLevel();
        if (level == null) {
            return CollisionResult.NONE;
        }

        BlockState state = level.getBlockState(blockPos);
        if (!(state.getBlock() instanceof FireboxBlock)) {
            return CollisionResult.NONE;
        }

        float power = (float) (double) LoconauticsConfig.CRASH_EXPLOSION_POWER.get();
        double x = blockPos.getX() + 0.5;
        double y = blockPos.getY() + 0.5;
        double z = blockPos.getZ() + 0.5;
        level.getServer().tell(new TickTask(level.getServer().getTickCount(), () -> {
            BlockState current = level.getBlockState(blockPos);
            if (current.getBlock() instanceof FireboxBlock) {
                level.explode(null, x, y, z, power, ExplosionInteraction.BLOCK);
            }
        }));
        return CollisionResult.NONE;
    }
}
