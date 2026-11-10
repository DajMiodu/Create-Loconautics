package com.lycoris.loconautics.content.turntablebearing;

import com.lycoris.loconautics.common.turntable.GhostTurntable;
import com.lycoris.loconautics.common.turntable.TurntableGhosts;
import com.lycoris.loconautics.common.turntable.TurntableMotion;
import com.lycoris.loconautics.common.sable.TrackHostFrames;
import com.lycoris.loconautics.common.turntable.TurntableRegistry;
import com.lycoris.loconautics.foundation.debug.TurntableTrace;
import com.lycoris.loconautics.common.turntable.TurntableSignalHold;
import com.lycoris.loconautics.config.LoconauticsConfig;
import com.lycoris.loconautics.common.turntable.sequence.TurntableInstruction;
import com.lycoris.loconautics.common.turntable.sequence.TurntableTriggerMode;
import com.lycoris.loconautics.registry.LoconauticsRegistries;
import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.content.contraptions.AssemblyException;
import com.simibubi.create.content.contraptions.IDisplayAssemblyExceptions;
import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;
import com.simibubi.create.content.kinetics.base.IRotate;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.simpleRelays.ICogWheel;
import com.simibubi.create.content.trains.graph.TrackNodeLocation;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.api.physics.PhysicsPipeline;
import dev.ryanhcode.sable.api.physics.constraint.RotaryConstraintConfiguration;
import dev.ryanhcode.sable.api.physics.constraint.RotaryConstraintHandle;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.plot.ServerLevelPlot;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import dev.simulated_team.simulated.util.SimAssemblyHelper;
import dev.simulated_team.simulated.util.SimLevelUtil;
import dev.simulated_team.simulated.util.extra_kinetics.ExtraBlockPos;
import dev.simulated_team.simulated.util.extra_kinetics.ExtraKinetics;
import net.createmod.catnip.math.AngleHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static com.lycoris.loconautics.foundation.LogFormat.shortId;

public class TurntableBearingBlockEntity extends KineticBlockEntity
        implements ExtraKinetics, IDisplayAssemblyExceptions, BlockEntitySubLevelActor, TurntableMotion.Host,
                TurntableGhosts.Source {

    private static final double SERVO_STIFFNESS = 1600.0;
    private static final double SERVO_DAMPING = 40.0;
    private static final double MIN_INERTIA = 10.0;
    private static final float MAX_COG_RPM = 96.0f;
    private static final float MAX_PLATE_RPM = 256.0f;
    private static final float PLATE_ANCHOR_SEPARATION_BIAS = 0.001f;

    private final TurntableCogwheelBlockEntity cogwheel;

    public boolean assembleNextTick;
    protected AssemblyException lastException;

    private final TurntableMotion motion = new TurntableMotion();

    @Nullable
    private UUID subLevelID;
    @Nullable
    private BlockPos platePos;
    @Nullable
    private RotaryConstraintHandle handle;
    private boolean assembling;

    private boolean removedForGood;

    private float speedOverride;

    private final TurntableSignalHold signals = new TurntableSignalHold();

    public TurntableBearingBlockEntity(BlockEntityType<?> typeIn, BlockPos pos, BlockState state) {
        super(typeIn, pos, state);
        cogwheel = new TurntableCogwheelBlockEntity(typeIn, pos, state, this);
    }

    @Override
    public void tick() {
        super.tick();
        cogwheel.tick();
        if (level.isClientSide) {
            return;
        }
        if (assembleNextTick) {
            assembleNextTick = false;
            if (isAssembled()) {
                disassemble();
            } else {
                assemble();
            }
        }
        if (subLevelID != null) {
            checkPersistence(subLevelID);
            adoptGhost();
        }
        tickSignals();
        motion.beginTick();
        if (!isAssembled()) {
            return;
        }
        TurntableGhosts.trackLive(this);
        TurntableRegistry.markPlate(subLevelID);
        recordFrame();
        traceDetached();
        SubLevelPhysicsSystem physicsSystem = SubLevelPhysicsSystem.get(level);
        if (physicsSystem == null || physicsSystem.getPaused()) {
            return;
        }
        if (turntableScheduleDriven()) {
            TurntableRegistry.register(this);
            motion.step(this, true);
            return;
        }
        TurntableRegistry.unregister(subLevelID);
        motion.step(this, false);
    }

    private void traceDetached() {
        if (subLevelID == null || getAttachedSubLevel() != null
                || !TurntableTrace.due("bearing" + subLevelID, level.getGameTime(), 20)) {
            return;
        }
        TrackHostFrames.Frame frame = TrackHostFrames.frameOf(subLevelID);
        SubLevelPhysicsSystem physics = SubLevelPhysicsSystem.get(level);
        TurntableTrace.log("bearing without plate " + shortId(subLevelID) + " at " + getBlockPos().toShortString()
                + " powered=" + turntablePowered() + " sched=" + turntableScheduleDriven() + " perTick="
                + angularPerTick() + " cog=" + cogwheel.getSpeed() + " handle=" + (handle != null) + " target="
                + String.format("%.2f", motion.targetAngleDegrees()) + " alignTarget=" + (motion.alignTarget() != null)
                + " aligned=" + motion.aligned() + " reserved=" + TurntableRegistry.reserved(subLevelID)
                + " registered=" + (TurntableRegistry.byPlate(subLevelID) == this) + " frameLoaded="
                + (frame != null && frame.loaded()) + " physicsPaused=" + (physics == null || physics.getPaused()));
    }

    private void adoptGhost() {
        GhostTurntable ghost = TurntableGhosts.take(subLevelID);
        if (ghost == null) {
            return;
        }
        motion.copyFrom(ghost.motion());
        signals.copyFrom(ghost.signals());
        setChanged();
        sendData();
    }

    private void recordFrame() {
        if (getAttachedSubLevel() instanceof ServerSubLevel plate && !plate.isRemoved()) {
            TrackHostFrames.record(subLevelID, level.dimension(), plate, motion.targetAngleDegrees(),
                    turntableAxisWorld(), turntableAxisNormal());
            return;
        }
        TrackHostFrames.setAngle(subLevelID, motion.targetAngleDegrees());
    }

    @Override
    @Nullable
    public GhostTurntable toGhost() {
        if (level == null || level.isClientSide || !isAssembled() || subLevelID == null
                || TrackHostFrames.frameOf(subLevelID) == null) {
            return null;
        }
        GhostTurntable ghost = new GhostTurntable(subLevelID, level.dimension(), getBlockPos());
        ghost.motion().copyFrom(motion);
        ghost.signals().copyFrom(signals);
        ghost.snapshot(turntablePowered(), angularPerTick(), facingNegative(), speedOverride,
                getBlockState().getValue(BlockStateProperties.POWERED));
        return ghost;
    }

    public boolean bindSignal(BlockPos signal) {
        if (!signals.bind(signal)) {
            return false;
        }
        setChanged();
        return true;
    }

    private void tickSignals() {
        if (signals.tick(level, plateBusy())) {
            setChanged();
        }
    }

    private boolean plateBusy() {
        return !isAssembled() || motion.busy(subLevelID);
    }

    private void releaseSignals() {
        signals.release(level);
    }

    public void turnBy(double degrees) {
        motion.turnBy(degrees);
    }

    public void turnTo(double absoluteDegrees) {
        motion.turnTo(absoluteDegrees);
    }

    public void stopTurning() {
        motion.stopTurning(this);
    }

    public boolean isTurning() {
        return motion.isTurning();
    }

    public double remainingManualDegrees() {
        return motion.manualRemainingDegrees();
    }

    public int getCurrentInstruction() {
        return motion.currentInstruction();
    }

    public void runInstruction(int index) {
        motion.runInstruction(this, index);
    }

    public float getPlateSpeed() {
        return plateSpeed();
    }

    public float getCogSpeed() {
        return limitCogSpeed(cogwheel.getSpeed());
    }

    public double degreesPerTick() {
        return Math.abs(angularPerTick());
    }

    @Override
    public float angularPerTick() {
        return convertToAngular(plateSpeed());
    }

    @Override
    public boolean facingNegative() {
        return getBlockState().getValue(DirectionalKineticBlock.FACING).getAxisDirection()
                == Direction.AxisDirection.NEGATIVE;
    }

    @Override
    public float speedOverride() {
        return speedOverride;
    }

    @Override
    public boolean neighborSignal() {
        return level.hasNeighborSignal(worldPosition);
    }

    @Override
    public void motionChanged() {
        setChanged();
        sendData();
    }

    @Override
    public void bodiesMoved() {
        wakeBodies();
    }

    @Override
    public boolean turntableSettled() {
        return motion.settled();
    }

    @Override
    @Nullable
    public UUID plateSubLevelId() {
        return subLevelID;
    }

    @Override
    public Vec3 turntableAxisWorld() {
        Direction facing = getBlockState().getValue(DirectionalKineticBlock.FACING);
        return toWorld(getBlockPos().relative(facing).getCenter(), true);
    }

    @Override
    public Vec3 turntableAxisNormal() {
        Vec3i normal = getBlockState().getValue(DirectionalKineticBlock.FACING).getNormal();
        return toWorld(new Vec3(normal.getX(), normal.getY(), normal.getZ()), false).normalize();
    }

    private Vec3 toWorld(Vec3 local, boolean asPosition) {
        SubLevel containing = getContainingSubLevel();
        if (containing == null) {
            return local;
        }
        Vector3d in = new Vector3d(local.x, local.y, local.z);
        Vector3d out = asPosition
                ? containing.logicalPose().transformPosition(in, new Vector3d())
                : containing.logicalPose().transformNormal(in, new Vector3d());
        return new Vec3(out.x, out.y, out.z);
    }

    @Override
    public boolean turntablePowered() {
        return isAssembled() && cogwheel.getSpeed() != 0.0f;
    }

    @Override
    public boolean turntableScheduleDriven() {
        return isAssembled() && subLevelID != null && motion.scheduleDriven();
    }

    @Override
    public void turntableAlignTo(TrackNodeLocation movingHead, TrackNodeLocation target) {
        motion.alignTo(movingHead, target);
    }

    @Override
    public void turntableClearAlignment() {
        motion.clearAlignment();
    }

    @Override
    @Nullable
    public TrackNodeLocation turntableAlignTarget() {
        return motion.alignTarget();
    }

    @Override
    public boolean turntableAligned() {
        return motion.aligned();
    }

    private void wakeBodies() {
        SubLevel attached = getAttachedSubLevel();
        if (attached == null || handle == null) {
            return;
        }
        PhysicsPipeline pipeline = ((ServerSubLevelContainer) SubLevelContainer.getContainer(level))
                .physicsSystem().getPipeline();
        if (getContainingSubLevel() instanceof ServerSubLevel containing) {
            pipeline.wakeUp(containing);
        }
        if (attached instanceof ServerSubLevel attachedServer) {
            pipeline.wakeUp(attachedServer);
        }
    }

    public void onRedstoneUpdate(boolean isPowered) {
        motion.redstoneEdge(this, isPowered, turntableScheduleDriven());
        if (getBlockState().getValue(BlockStateProperties.POWERED) != isPowered) {
            level.setBlock(worldPosition, getBlockState().setValue(BlockStateProperties.POWERED, isPowered), 2);
        }
    }

    public void applyConfiguration(List<TurntableInstruction> newInstructions, TurntableTriggerMode newTrigger) {
        motion.configure(this, newInstructions, newTrigger);
    }

    private float limitCogSpeed(float speed) {
        return Mth.clamp(speed, -MAX_COG_RPM, MAX_COG_RPM);
    }

    public float plateSpeed() {
        float cog = limitCogSpeed(cogwheel.getSpeed());
        if (cog == 0.0f || speedOverride == 0.0f) {
            return cog;
        }
        return speedOverride;
    }

    public float getSpeedOverride() {
        return speedOverride;
    }

    public void setSpeedOverride(float rpm) {
        float clamped = Mth.clamp(rpm, -MAX_PLATE_RPM, MAX_PLATE_RPM);
        if (clamped == speedOverride) {
            return;
        }
        speedOverride = clamped;
        pushStress();
        setChanged();
        sendData();
    }

    public void pushStress() {
        if (level == null || level.isClientSide || !cogwheel.hasNetwork()) {
            return;
        }
        cogwheel.getOrCreateNetwork().updateStressFor(cogwheel, cogwheel.calculateStressApplied());
    }

    public float billedStress() {
        return plateStressImpact(cogwheel.getTheoreticalSpeed()) * Math.abs(cogwheel.getTheoreticalSpeed());
    }

    float plateStressImpact(float networkSpeed) {
        if (!isAssembled()) {
            return 0.0f;
        }
        float impact = LoconauticsConfig.TURNTABLE_SU_IMPACT.get().floatValue();
        float fed = Math.abs(networkSpeed);
        if (fed == 0.0f) {
            return impact;
        }
        return impact * Math.abs(plateSpeed()) / fed;
    }

    public void updateServoCoefficients() {
        validateConstraintHandle();
        if (!isAssembled() || handle == null) {
            return;
        }
        SubLevel subLevelA = getContainingSubLevel();
        SubLevel subLevelB = getAttachedSubLevel();
        Vec3i facingVec3I = getBlockState().getValue(DirectionalKineticBlock.FACING).getNormal();
        Vector3d facingVec = new Vector3d(facingVec3I.getX(), facingVec3I.getY(), facingVec3I.getZ());
        Vector3d temp = new Vector3d();
        double inertiaA = Double.MAX_VALUE;
        double inertiaB = Double.MAX_VALUE;
        if (subLevelA instanceof ServerSubLevel serverA) {
            inertiaA = serverA.getMassTracker().getInertiaTensor().transform(facingVec, temp).dot(facingVec);
        }
        if (subLevelB instanceof ServerSubLevel serverB) {
            inertiaB = serverB.getMassTracker().getInertiaTensor().transform(facingVec, temp).dot(facingVec);
        }
        double totalInertia = Math.max(MIN_INERTIA, subLevelA != null && subLevelB != null
                ? Math.max(inertiaA, inertiaB)
                : Math.min(inertiaA, inertiaB));

        SubLevelPhysicsSystem physicsSystem = ((ServerSubLevelContainer) SubLevelContainer.getContainer(level))
                .physicsSystem();
        double kP = SERVO_STIFFNESS * totalInertia;
        double kD = SERVO_DAMPING * totalInertia;
        float goalRadians = AngleHelper.rad(AngleHelper.angleLerp(physicsSystem.getPartialPhysicsTick(),
                motion.lastTargetAngleDegrees(), motion.targetAngleDegrees()));
        handle.setMotor(RotaryConstraintHandle.DEFAULT_AXIS, goalRadians, kP, kD, false, 0.0);
        handle.setContactsEnabled(false);
    }

    private void validateConstraintHandle() {
        if (handle != null && !handle.isValid()) {
            handle = null;
        }
    }

    public void assemble() {
        BlockPos pos = getBlockPos();
        Direction facing = getBlockState().getValue(DirectionalKineticBlock.FACING);
        BlockPos toAssemble = pos.relative(facing);
        SimAssemblyHelper.AssemblyResult result;
        try {
            result = SimAssemblyHelper.assembleFromSingleBlock(level, pos, toAssemble, false, false);
            lastException = null;
        } catch (AssemblyException e) {
            lastException = e;
            sendData();
            return;
        }
        sendData();

        BlockState plate = LoconauticsRegistries.TURNTABLE_PLATE.get().defaultBlockState()
                .setValue(DirectionalKineticBlock.FACING, facing);
        ServerSubLevel assembledSubLevel;
        BlockPos assembleOffset;
        if (result != null) {
            assembledSubLevel = (ServerSubLevel) result.subLevel();
            assembleOffset = result.offset();
        } else {
            ServerSubLevelContainer container = (ServerSubLevelContainer) SubLevelContainer.getContainer(level);
            Pose3d pose = new Pose3d();
            pose.position().set(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
            assembledSubLevel = (ServerSubLevel) container.allocateNewSubLevel(pose);
            ServerLevelPlot plot = assembledSubLevel.getPlot();
            ChunkPos center = plot.getCenterChunk();
            plot.newEmptyChunk(center);
            plot.getEmbeddedLevelAccessor().setBlock(BlockPos.ZERO, plate, 3);
            BlockPos plotAnchor = plot.getCenterBlock();
            Vector3dc centerOfMass = assembledSubLevel.getMassTracker().getCenterOfMass();
            Vector3d subLevelCenter = JOMLConversion.atLowerCornerOf(pos);
            if (centerOfMass != null) {
                subLevelCenter.add(centerOfMass.x() - plotAnchor.getX(),
                        centerOfMass.y() - plotAnchor.getY(),
                        centerOfMass.z() - plotAnchor.getZ());
            } else {
                assembledSubLevel.logicalPose().rotationPoint()
                        .set(plotAnchor.getX() + 0.5, plotAnchor.getY() + 0.5, plotAnchor.getZ() + 0.5);
            }
            assembledSubLevel.logicalPose().position().set(subLevelCenter.x, subLevelCenter.y, subLevelCenter.z);
            assembleOffset = plotAnchor.subtract(pos);

            SubLevelPhysicsSystem physicsSystem = container.physicsSystem();
            PhysicsPipeline pipeline = physicsSystem.getPipeline();
            SubLevel containingSubLevel = getContainingSubLevel();
            if (containingSubLevel != null) {
                SubLevelAssemblyHelper.kickFromContainingSubLevel((ServerLevel) level, physicsSystem, pipeline,
                        assembledSubLevel, containingSubLevel);
                assembledSubLevel.logicalPose().orientation().set(containingSubLevel.logicalPose().orientation());
            }
            pipeline.teleport(assembledSubLevel, assembledSubLevel.logicalPose().position(),
                    assembledSubLevel.logicalPose().orientation());
            assembledSubLevel.updateLastPose();
            AllSoundEvents.CONTRAPTION_ASSEMBLE.playOnServer(level, pos);
        }

        level.setBlockAndUpdate(pos, getBlockState().setValue(TurntableBearingBlock.ASSEMBLED, true));
        BlockPos plotPos = pos.offset(assembleOffset);
        setSubLevelID(assembledSubLevel.getUniqueId());
        if (result != null) {
            level.setBlockAndUpdate(plotPos, plate);
        }
        if (level.getBlockEntity(plotPos) instanceof TurntablePlateBlockEntity plateBE) {
            plateBE.setParent(this);
            setPlatePos(plotPos);
        }
        attachConstraints(assembledSubLevel, JOMLConversion.toJOML(plotPos.relative(facing).getCenter()));
        pushStress();
    }

    public void disassemble() {
        if (isRemoved()) {
            return;
        }
        removeHandle();
        TurntableRegistry.unregister(subLevelID);
        TurntableRegistry.forgetPlate(subLevelID);
        TrackHostFrames.forget(subLevelID);
        TurntableGhosts.untrackLive(this);
        turntableClearAlignment();
        SubLevel subLevel = SubLevelContainer.getContainer(level).getSubLevel(subLevelID);
        BlockPos platePos = getPlatePos();
        if (platePos != null) {
            destroyPlate();
            if (Objects.equals(subLevel, Sable.HELPER.getContaining(level, getBlockPos()))) {
                lastException = AssemblyException.unmovableBlock(getBlockPos(), getBlockState());
                AllSoundEvents.CONTRAPTION_DISASSEMBLE.playOnServer(level, platePos);
            } else if (subLevel != null && !subLevel.isRemoved()) {
                SimAssemblyHelper.disassembleSubLevel(level, subLevel, platePos, getBlockPos(), Rotation.NONE, true);
            } else if (subLevel != null) {
                AllSoundEvents.CONTRAPTION_DISASSEMBLE.playOnServer(level, platePos);
            }
        }
        level.setBlockAndUpdate(getBlockPos(), getBlockState().setValue(TurntableBearingBlock.ASSEMBLED, false));
        pushStress();
        setSubLevelID(null);
        setPlatePos(null);
        motion.reset();
        sendData();
    }

    private void checkPersistence(UUID id) {
        BlockPos platePos = getPlatePos();
        if (platePos != null && SimLevelUtil.isAreaActuallyLoaded(level, platePos, 1)
                && !level.getBlockState(platePos).is(LoconauticsRegistries.TURNTABLE_PLATE.get())) {
            return;
        }
        validateConstraintHandle();
        if (handle == null) {
            reattachConstraint((ServerSubLevel) SubLevelContainer.getContainer(level).getSubLevel(id), true);
        }
    }

    public void reattachConstraint(@Nullable ServerSubLevel plateSubLevel, boolean updatePlate) {
        BlockPos platePos = getPlatePos();
        if (platePos == null) {
            return;
        }
        removeHandle();
        if (updatePlate) {
            associatePlateWithParent();
        }
        BlockState plateState = level.getBlockState(platePos);
        if (!plateState.is(LoconauticsRegistries.TURNTABLE_PLATE.get())) {
            return;
        }
        Direction plateFacing = plateState.getValue(DirectionalKineticBlock.FACING);
        attachConstraints(plateSubLevel, JOMLConversion.toJOML(platePos.relative(plateFacing).getCenter()));
    }

    public void associatePlateWithParent() {
        BlockPos platePos = getPlatePos();
        if (platePos != null && level.getBlockEntity(platePos) instanceof TurntablePlateBlockEntity plate) {
            plate.setParent(this);
        }
    }

    private void attachConstraints(@Nullable ServerSubLevel plateSubLevel, Vector3d attachPos) {
        BlockPos platePos = getPlatePos();
        if (platePos == null) {
            return;
        }
        BlockState plateState = level.getBlockState(platePos);
        if (!plateState.is(LoconauticsRegistries.TURNTABLE_PLATE.get())) {
            return;
        }
        Direction facing = getBlockState().getValue(DirectionalKineticBlock.FACING);
        Vector3d anchorPos = JOMLConversion.toJOML(getBlockPos().relative(facing).getCenter());
        Vec3 facingVec = Vec3.atLowerCornerOf(facing.getNormal());
        Vec3 plateFacingVec = Vec3.atLowerCornerOf(plateState.getValue(DirectionalKineticBlock.FACING).getNormal());
        RotaryConstraintConfiguration constraint = new RotaryConstraintConfiguration(
                anchorPos,
                attachPos.sub(JOMLConversion.toJOML(plateFacingVec.scale(PLATE_ANCHOR_SEPARATION_BIAS))),
                JOMLConversion.toJOML(facingVec),
                JOMLConversion.toJOML(plateFacingVec));
        ServerSubLevelContainer container = SubLevelContainer.getContainer((ServerLevel) level);
        ServerSubLevel containingSubLevel = (ServerSubLevel) Sable.HELPER.getContaining(this);
        if (containingSubLevel == plateSubLevel) {
            return;
        }
        handle = (RotaryConstraintHandle) container.physicsSystem().getPipeline()
                .addConstraint(containingSubLevel, plateSubLevel, constraint);
    }

    @Override
    protected void write(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(compound, registries, clientPacket);
        motion.write(compound);
        compound.putFloat("SpeedOverride", speedOverride);
        if (!clientPacket) {
            signals.write(compound);
        }
        if (subLevelID != null) {
            compound.putUUID("SubLevelID", subLevelID);
        }
        if (platePos != null) {
            compound.put("TurntablePlate", NbtUtils.writeBlockPos(platePos));
        }
        AssemblyException.write(compound, registries, lastException);
    }

    @Override
    protected void read(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(compound, registries, clientPacket);
        motion.read(compound);
        speedOverride = compound.getFloat("SpeedOverride");
        if (!clientPacket) {
            signals.read(compound);
        }
        if (compound.hasUUID("SubLevelID")) {
            setSubLevelID(compound.getUUID("SubLevelID"));
        }
        if (compound.contains("TurntablePlate")) {
            setPlatePos(NbtUtils.readBlockPos(compound, "TurntablePlate").orElse(null));
        }
        lastException = AssemblyException.read(compound, registries);
    }

    @Override
    public void invalidate() {
        super.invalidate();
        removeHandle();
        if (level != null && level.isClientSide) {
            return;
        }
        if (level != null) {
            TurntableGhosts.untrackLive(this);
            if (!removedForGood && isChunkUnloaded()) {
                TurntableGhosts.ghostify(this);
                return;
            }
        }
        releaseSignals();
        TurntableRegistry.unregister(subLevelID);
        TurntableRegistry.forgetPlate(subLevelID);
        TrackHostFrames.forget(subLevelID);
    }

    public void beforeAssembly() {
        assembling = true;
    }

    @Override
    public void remove() {
        removedForGood = true;
        if (!level.isClientSide && !assembling) {
            destroyPlate();
        }
        super.remove();
    }

    public boolean isAssembled() {
        return getBlockState().getValue(TurntableBearingBlock.ASSEMBLED);
    }

    @Nullable
    private SubLevel getAttachedSubLevel() {
        return SubLevelContainer.getContainer(level).getSubLevel(subLevelID);
    }

    @Nullable
    private SubLevel getContainingSubLevel() {
        return Sable.HELPER.getContaining(this);
    }

    private void destroyPlate() {
        BlockPos platePos = getPlatePos();
        if (platePos == null) {
            return;
        }
        SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            return;
        }
        if (subLevelID != null && container.getSubLevel(subLevelID) == null) {
            return;
        }
        if (level.getBlockState(platePos).is(LoconauticsRegistries.TURNTABLE_PLATE.get())) {
            LoconauticsRegistries.TURNTABLE_PLATE.get()
                    .withBlockEntityDo(level, platePos, TurntablePlateBlockEntity::beforeAssembly);
            level.setBlock(platePos, Blocks.AIR.defaultBlockState(), 2);
        }
    }

    private void removeHandle() {
        if (handle != null) {
            handle.remove();
            handle = null;
        }
    }

    public double getTargetAngleDegrees() {
        return motion.targetAngleDegrees();
    }

    public List<TurntableInstruction> getInstructions() {
        return motion.instructions();
    }

    public TurntableTriggerMode getTrigger() {
        return motion.trigger();
    }

    @Override
    public @NotNull KineticBlockEntity getExtraKinetics() {
        return cogwheel;
    }

    @Override
    public boolean shouldConnectExtraKinetics() {
        return false;
    }

    @Override
    public String getExtraKineticsSaveName() {
        return "TurntableCog";
    }

    @Override
    public float propagateRotationTo(KineticBlockEntity target, BlockState stateFrom, BlockState stateTo,
                                     BlockPos diff, boolean connectedViaAxes, boolean connectedViaCogs) {
        if (getPlatePos() != null && stateTo.getBlock() instanceof TurntablePlateBlock) {
            return 1.0f;
        }
        return super.propagateRotationTo(target, stateFrom, stateTo, diff, connectedViaAxes, connectedViaCogs);
    }

    @Override
    public boolean isCustomConnection(KineticBlockEntity other, BlockState state, BlockState otherState) {
        return getPlatePos() != null && otherState.getBlock() instanceof TurntablePlateBlock;
    }

    @Override
    public List<BlockPos> addPropagationLocations(IRotate block, BlockState state, List<BlockPos> neighbours) {
        if (getPlatePos() != null) {
            neighbours.add(getPlatePos());
        }
        return super.addPropagationLocations(block, state, neighbours);
    }

    @Nullable
    public BlockPos getPlatePos() {
        return platePos;
    }

    public void setPlatePos(@Nullable BlockPos platePos) {
        this.platePos = platePos;
    }

    @Nullable
    public UUID getSubLevelID() {
        return subLevelID;
    }

    public void setSubLevelID(@Nullable UUID subLevelID) {
        this.subLevelID = subLevelID;
    }

    @Override
    public float calculateStressApplied() {
        return 0.0f;
    }

    @Override
    public AssemblyException getLastAssemblyException() {
        return lastException;
    }

    @Override
    public @Nullable Iterable<@NotNull SubLevel> sable$getConnectionDependencies() {
        SubLevel attached = getAttachedSubLevel();
        return attached == null ? null : List.of(attached);
    }

    public static class TurntableCogwheelBlockEntity extends KineticBlockEntity
            implements ExtraKinetics.ExtraKineticsBlockEntity {

        public static final ICogWheel EXTRA_COGWHEEL_CONFIG = new ICogWheel() {
            @Override
            public boolean hasShaftTowards(LevelReader world, BlockPos pos, BlockState state, Direction face) {
                return false;
            }

            @Override
            public Direction.Axis getRotationAxis(BlockState state) {
                return state.getValue(DirectionalKineticBlock.FACING).getAxis();
            }
        };

        private final TurntableBearingBlockEntity parent;

        public TurntableCogwheelBlockEntity(BlockEntityType<?> typeIn, BlockPos pos, BlockState state,
                                              TurntableBearingBlockEntity parent) {
            super(typeIn, new ExtraBlockPos(pos), state);
            this.parent = parent;
        }

        @Override
        public void onSpeedChanged(float previousSpeed) {
            super.onSpeedChanged(previousSpeed);
            if (speed != 0.0f && !parent.isAssembled()) {
                parent.assembleNextTick = true;
            }
        }

        @Override
        public float calculateStressApplied() {
            this.lastStressApplied = parent.plateStressImpact(getTheoreticalSpeed());
            return this.lastStressApplied;
        }

        @Override
        public KineticBlockEntity getParentBlockEntity() {
            return parent;
        }

        @Override
        protected boolean canPropagateDiagonally(IRotate block, BlockState state) {
            return true;
        }

        @Override
        public Component getKey() {
            return Component.translatable("loconautics.extra_kinetics.turntable_cog");
        }
    }
}
