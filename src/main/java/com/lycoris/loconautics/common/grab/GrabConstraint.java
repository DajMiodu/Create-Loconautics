package com.lycoris.loconautics.common.grab;

import com.lycoris.loconautics.trainphysics.TrainConsist;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.physics.PhysicsPipelineBody;
import dev.ryanhcode.sable.api.physics.constraint.ConstraintJointAxis;
import dev.ryanhcode.sable.api.physics.constraint.FreeConstraintConfiguration;
import dev.ryanhcode.sable.api.physics.constraint.PhysicsConstraintHandle;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.UUID;

public final class GrabConstraint {

    private static final float ROTATE_ANGULAR_STIFFNESS = 10000.0f;
    private static final float ROTATE_ANGULAR_DAMPING = 850.0f;

    private final UUID playerId;
    private float scrollDistance;
    private boolean rotating;
    private final Quaterniond targetOrientation = new Quaterniond();
    @Nullable private PhysicsConstraintHandle handle;

    public GrabConstraint(UUID playerId, float scrollDistance) {
        this.playerId = playerId;
        this.scrollDistance = scrollDistance;
    }

    public void setScrollDistance(float range) {
        scrollDistance = Mth.clamp(range, GrabTarget.MIN_GRAB_DISTANCE, GrabTarget.MAX_GRAB_DISTANCE);
    }

    public void setRotation(boolean rotating, org.joml.Quaterniondc orientation) {
        this.rotating = rotating;
        if (orientation != null) {
            targetOrientation.set(orientation);
        }
    }

    public Vector3d goalFor(Player player) {
        return JOMLConversion.toJOML(player.getEyePosition()
                .add(player.getLookAngle().scale(Math.max(2.0, scrollDistance))));
    }

    public void physicsTick(PhysicsPipelineBody body, ServerLevel level, Vector3d grabCenter, Vector3d grabAnchor,
                            float rotateMaxForce) {
        removeJoint();
        Player player = level.getPlayerByUUID(playerId);
        if (player == null) {
            return;
        }
        if (!(player.onGround() || player.isInWater() || player.getAbilities().flying || player.onClimbable())) {
            return;
        }
        if (body instanceof ServerSubLevel subLevel
                && TrainConsist.standingOnConsistOf(level, player, subLevel.getUniqueId())) {
            return;
        }
        Vector3d goal = goalFor(player);
        double validRange = player.getAttribute(Attributes.BLOCK_INTERACTION_RANGE).getValue() + 2.0;
        double distSq = Sable.HELPER.distanceSquaredWithSubLevels(level, goal, grabCenter);
        if (distSq > validRange * validRange) {
            return;
        }
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            return;
        }
        SubLevelPhysicsSystem system = container.physicsSystem();
        Quaterniond frame = rotating ? new Quaterniond(targetOrientation) : new Quaterniond();
        handle = system.getPipeline().addConstraint(null, body,
                new FreeConstraintConfiguration(goal, grabAnchor, frame));
        if (handle == null) {
            return;
        }
        for (ConstraintJointAxis axis : ConstraintJointAxis.LINEAR) {
            handle.setMotor(axis, 0.0, GrabTarget.LINEAR_STIFFNESS, GrabTarget.LINEAR_DAMPING,
                    true, GrabTarget.MAX_FORCE);
        }
        for (ConstraintJointAxis axis : ConstraintJointAxis.ANGULAR) {
            if (rotating) {
                handle.setMotor(axis, 0.0, ROTATE_ANGULAR_STIFFNESS, ROTATE_ANGULAR_DAMPING, true,
                        rotateMaxForce);
            } else {
                handle.setMotor(axis, 0.0, 0.0, GrabTarget.ANGULAR_DAMPING, true, GrabTarget.MAX_FORCE);
            }
        }
    }

    public void removeJoint() {
        if (handle != null) {
            handle.remove();
            handle = null;
        }
    }
}
