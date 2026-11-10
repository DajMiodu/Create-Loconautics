package com.lycoris.loconautics.mixin.sable.mergingglue;

import com.lycoris.loconautics.trainphysics.drive.WeldedBodies;
import com.lycoris.loconautics.trainphysics.spawn.GlueHost;
import com.lycoris.loconautics.trainphysics.state.SableTrainRegistry;
import com.lycoris.loconautics.trainphysics.sublevel.SubLevelContacts;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.physics.PhysicsPipeline;
import dev.ryanhcode.sable.api.physics.PhysicsPipelineBody;
import dev.ryanhcode.sable.api.physics.constraint.FixedConstraintConfiguration;
import dev.ryanhcode.sable.api.physics.constraint.FixedConstraintHandle;
import dev.ryanhcode.sable.api.physics.force.ForceTotal;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.physics.mass.MassData;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysicsData;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.simulated_team.simulated.content.blocks.merging_glue.MergingGlueBlockEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = MergingGlueBlockEntity.class, remap = false)
public abstract class MergingGlueTrainDriveMixin {

    @Unique
    private static final double LOCONAUTICS_PULL_TICKS = 10.0;

    @Shadow
    private int ageTicks;
    @Shadow
    private boolean hasControllingValues;
    @Shadow
    private boolean isController;
    @Shadow
    private Vector3d startPartnerPosition;
    @Shadow
    private Vector3d endPartnerPosition;
    @Shadow
    private Quaterniond startPartnerOrientation;
    @Shadow
    private Quaterniond endPartnerOrientation;
    @Shadow
    private FixedConstraintHandle lastConstraintHandle;

    @Unique
    private Vector3d loconautics$heldLinear;
    @Unique
    private Vector3d loconautics$heldAngular;
    @Unique
    private Vector3d loconautics$lastPiecePos;
    @Unique
    private Quaterniond loconautics$lastPieceRot;
    @Unique
    private boolean loconautics$landed;
    @Unique
    private static final double LOCONAUTICS_LAND_SPEED = 0.5;
    @Unique
    private static final double LOCONAUTICS_MIN_GLIDE = 0.05;

    @Inject(method = "sable$physicsTick", at = @At("HEAD"), cancellable = true)
    private void loconautics$easeLighterTrain(ServerSubLevel subLevel, RigidBodyHandle handle, double timeStep,
                                              CallbackInfo ci) {
        if (!isController || !hasControllingValues) {
            return;
        }
        MergingGlueBlockEntity self = (MergingGlueBlockEntity) (Object) this;
        MergingGlueBlockEntity partnerGlue = self.getPartnerGlue();
        if (partnerGlue == null || !(subLevel.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (!(Sable.HELPER.getContaining((BlockEntity) partnerGlue) instanceof ServerSubLevel partner)) {
            return;
        }
        ServerSubLevel train = GlueHost.trainOf(subLevel, partner);
        if (train != null) {
            ci.cancel();
            loconautics$glidePiece(level, subLevel, partner, partnerGlue, train, timeStep);
            return;
        }
        if (SableTrainRegistry.bySubLevel(subLevel.getUniqueId()) == null
                || SableTrainRegistry.bySubLevel(partner.getUniqueId()) == null) {
            return;
        }
        ci.cancel();
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        PhysicsPipeline pipeline = container.physicsSystem().getPipeline();
        self.removeConstraint();

        boolean partnerMoves = partner.getMassTracker().getMass() < subLevel.getMassTracker().getMass();
        ServerSubLevel heavy = partnerMoves ? subLevel : partner;
        if (loconautics$heldLinear == null) {
            loconautics$heldLinear = pipeline.getLinearVelocity(heavy, new Vector3d());
            loconautics$heldAngular = pipeline.getAngularVelocity(heavy, new Vector3d());
        }

        double time = ageTicks + container.physicsSystem().getPartialPhysicsTick();
        double t = Mth.clamp(time / LOCONAUTICS_PULL_TICKS, 0.0, 1.0);
        double ease = t * t * t * (t * (t * 6.0 - 15.0) + 10.0);
        Vector3d relPos = startPartnerPosition.lerp((Vector3dc) endPartnerPosition, ease, new Vector3d());
        Quaterniond relRot = startPartnerOrientation.slerp((Quaterniondc) endPartnerOrientation, ease, new Quaterniond());
        Vector3d partnerCenter = partnerGlue.getCenter(new Vector3d());

        lastConstraintHandle = pipeline.addConstraint((PhysicsPipelineBody) subLevel, (PhysicsPipelineBody) partner,
                new FixedConstraintConfiguration(relPos, partnerCenter, relRot));
        lastConstraintHandle.setContactsEnabled(false);

        Vector3d addLinear = loconautics$heldLinear.sub(pipeline.getLinearVelocity(heavy, new Vector3d()), new Vector3d());
        Vector3d addAngular = loconautics$heldAngular.sub(pipeline.getAngularVelocity(heavy, new Vector3d()), new Vector3d());
        pipeline.addLinearAndAngularVelocity(heavy, addLinear, addAngular);

    }

    @Unique
    private void loconautics$glidePiece(ServerLevel level, ServerSubLevel controller, ServerSubLevel partner,
                                        MergingGlueBlockEntity partnerGlue, ServerSubLevel train, double dt) {
        MergingGlueBlockEntity self = (MergingGlueBlockEntity) (Object) this;
        self.removeConstraint();
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        PhysicsPipeline pipeline = container.physicsSystem().getPipeline();
        ServerSubLevel piece = train == controller ? partner : controller;
        SubLevelContacts.disable(controller, partner);

        double time = ageTicks + container.physicsSystem().getPartialPhysicsTick();
        double t = Mth.clamp(time / LOCONAUTICS_PULL_TICKS, 0.0, 1.0);
        double ease = t * t * t * (t * (t * 6.0 - 15.0) + 10.0);
        Vector3d relPos = startPartnerPosition.lerp((Vector3dc) endPartnerPosition, ease, new Vector3d());
        Quaterniond relRot = startPartnerOrientation.slerp((Quaterniondc) endPartnerOrientation, ease, new Quaterniond());
        Vector3d partnerCenter = partnerGlue.getCenter(new Vector3d());
        Pose3d controllerPose = controller.logicalPose();
        Pose3d partnerPose = partner.logicalPose();
        Quaterniond rot;
        Vector3d pos;
        if (piece == partner) {
            rot = new Quaterniond(controllerPose.orientation()).mul(relRot);
            Vector3d meet = controllerPose.transformPosition(new Vector3d(relPos), new Vector3d());
            pos = meet.sub(rot.transform(new Vector3d(partnerCenter).sub(partnerPose.rotationPoint())));
        } else {
            rot = new Quaterniond(partnerPose.orientation()).mul(new Quaterniond(relRot).conjugate());
            Vector3d meet = partnerPose.transformPosition(new Vector3d(partnerCenter), new Vector3d());
            pos = meet.sub(rot.transform(new Vector3d(relPos).sub(controllerPose.rotationPoint())));
        }
        if (!pos.isFinite() || !rot.isFinite()) {
            return;
        }

        Pose3d piecePose = piece.logicalPose();
        Vector3d from = loconautics$lastPiecePos != null ? loconautics$lastPiecePos : new Vector3d(piecePose.position());
        Quaterniond fromRot = loconautics$lastPieceRot != null ? loconautics$lastPieceRot
                : new Quaterniond(piecePose.orientation());
        Vector3d linear = dt > 1.0e-6 ? new Vector3d(pos).sub(from).div(dt) : new Vector3d();
        Vector3d spin = WeldedBodies.angularVelocity(fromRot, rot, dt);
        loconautics$lastPiecePos = new Vector3d(pos);
        loconautics$lastPieceRot = new Quaterniond(rot);
        piecePose.position().set(pos);
        piecePose.orientation().set(rot);
        pipeline.wakeUp(piece);
        pipeline.teleport(piece, pos, rot);
        pipeline.resetVelocity(piece);
        pipeline.addLinearAndAngularVelocity(piece, linear, spin);

        if (t >= 1.0 && !loconautics$landed) {
            loconautics$landed = true;
            loconautics$land(pipeline, self, partnerGlue, controller, train, piece);
        }
    }

    @Unique
    private void loconautics$land(PhysicsPipeline pipeline, MergingGlueBlockEntity self, MergingGlueBlockEntity partnerGlue,
                                  ServerSubLevel controller, ServerSubLevel train, ServerSubLevel piece) {
        RigidBodyHandle trainHandle = RigidBodyHandle.of(train);
        MassData trainMass = train.getMassTracker();
        MassData pieceMass = piece.getMassTracker();
        if (trainHandle == null || trainMass == null || pieceMass == null || trainMass.isInvalid()
                || pieceMass.isInvalid()) {
            return;
        }
        Vector3d glide = new Vector3d(endPartnerPosition).sub(startPartnerPosition);
        Vector3d direction;
        if (glide.length() < LOCONAUTICS_MIN_GLIDE) {
            direction = new Vector3d(DimensionPhysicsData.getGravity(train.getLevel())).normalize();
        } else {
            direction = controller.logicalPose().orientation().transform(glide).normalize()
                    .mul(piece == controller ? -1.0 : 1.0);
        }
        if (!direction.isFinite()) {
            return;
        }
        double sharedMass = trainMass.getMass() * pieceMass.getMass() / (trainMass.getMass() + pieceMass.getMass());
        Vector3d impulse = direction.mul(sharedMass * LOCONAUTICS_LAND_SPEED);
        Vector3d contact = (train == controller ? self : partnerGlue).getCenter(new Vector3d());
        ForceTotal forces = new ForceTotal();
        forces.applyImpulseAtPoint(trainMass, contact,
                new Quaterniond(train.logicalPose().orientation()).conjugate().transform(new Vector3d(impulse)));
        pipeline.wakeUp(train);
        trainHandle.applyForcesAndReset(forces);
    }
}
