package com.lycoris.loconautics.common.turntable;

import com.lycoris.loconautics.common.sable.TrackHostFrames;
import com.lycoris.loconautics.foundation.debug.TurntableTrace;
import com.lycoris.loconautics.trainphysics.drive.ExternalForceDerail;
import com.simibubi.create.content.trains.graph.TrackNodeLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

import static com.lycoris.loconautics.foundation.LogFormat.shortId;

public final class GhostTurntable implements TurntableMotion.Host {

    private static final int ORPHAN_TICKS = 100;

    private final UUID plate;
    private final ResourceKey<Level> dimension;
    private final BlockPos bearingPos;
    private final TurntableMotion motion = new TurntableMotion();
    private final TurntableSignalHold signals = new TurntableSignalHold();

    private boolean powered;
    private float angularPerTick;
    private boolean facingNegative;
    private float speedOverride;
    private boolean neighborSignal;
    private int orphanTicks;

    public GhostTurntable(UUID plate, ResourceKey<Level> dimension, BlockPos bearingPos) {
        this.plate = plate;
        this.dimension = dimension;
        this.bearingPos = bearingPos;
    }

    public void snapshot(boolean powered, float angularPerTick, boolean facingNegative, float speedOverride,
                         boolean neighborSignal) {
        this.powered = powered;
        this.angularPerTick = angularPerTick;
        this.facingNegative = facingNegative;
        this.speedOverride = speedOverride;
        this.neighborSignal = neighborSignal;
    }

    public TurntableMotion motion() {
        return motion;
    }

    public TurntableSignalHold signals() {
        return signals;
    }

    public ResourceKey<Level> dimension() {
        return dimension;
    }

    public BlockPos bearingPos() {
        return bearingPos;
    }

    @Nullable
    public TrackHostFrames.Frame frame() {
        return TrackHostFrames.frameOf(plate);
    }

    public void tick(MinecraftServer server) {
        ServerLevel level = server.getLevel(dimension);
        if (level == null) {
            return;
        }
        signals.tick(level, motion.busy(plate));
        if (ExternalForceDerail.staffLockedById(level, List.of(plate))) {
            return;
        }
        motion.beginTick();
        motion.step(this, motion.scheduleDriven());
        TrackHostFrames.setAngle(plate, motion.targetAngleDegrees());
        trace(level);
    }

    private void trace(ServerLevel level) {
        if (!TurntableTrace.due("ghost" + plate, level.getGameTime(), 20)) {
            return;
        }
        TrackHostFrames.Frame frame = frame();
        TurntableTrace.log("ghost plate " + shortId(plate) + " target=" + String.format("%.2f", motion.targetAngleDegrees())
                + " perTick=" + angularPerTick + " powered=" + powered + " sched=" + motion.scheduleDriven()
                + " alignTarget=" + (motion.alignTarget() != null) + " aligned=" + motion.aligned()
                + " settled=" + motion.settled() + " reserved=" + TurntableRegistry.reserved(plate)
                + " occupied=" + TurntableRegistry.occupied(plate) + " registered="
                + (TurntableRegistry.byPlate(plate) == this) + " frameLoaded=" + (frame != null && frame.loaded())
                + " bearingLoaded=" + level.isLoaded(bearingPos) + " orphanTicks=" + orphanTicks);
    }

    // the frame is gone or the bearing block stopped existing while its chunk is loaded
    boolean orphaned(MinecraftServer server) {
        ServerLevel level = server.getLevel(dimension);
        if (level == null) {
            return false;
        }
        boolean bearingGone = level.isLoaded(bearingPos)
                && !(level.getBlockEntity(bearingPos) instanceof TurntableGhosts.Source);
        orphanTicks = frame() == null || bearingGone ? orphanTicks + 1 : 0;
        return orphanTicks > ORPHAN_TICKS;
    }

    @Override
    public UUID plateSubLevelId() {
        return plate;
    }

    @Override
    @Nullable
    public Vec3 turntableAxisWorld() {
        TrackHostFrames.Frame frame = frame();
        return frame == null ? null : frame.axis();
    }

    @Override
    @Nullable
    public Vec3 turntableAxisNormal() {
        TrackHostFrames.Frame frame = frame();
        return frame == null ? null : frame.normal();
    }

    @Override
    public boolean turntablePowered() {
        return powered;
    }

    @Override
    public boolean turntableScheduleDriven() {
        return motion.scheduleDriven();
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

    @Override
    public boolean turntableSettled() {
        return motion.settled();
    }

    @Override
    public float angularPerTick() {
        return angularPerTick;
    }

    @Override
    public boolean facingNegative() {
        return facingNegative;
    }

    @Override
    public float speedOverride() {
        return speedOverride;
    }

    @Override
    public boolean neighborSignal() {
        return neighborSignal;
    }

    @Override
    public void motionChanged() {
        TurntableGhosts.markDirty();
    }

    @Override
    public void bodiesMoved() {
        TurntableGhosts.markDirty();
    }

    CompoundTag write() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Plate", plate);
        tag.putString("Dimension", dimension.location().toString());
        tag.put("BearingPos", NbtUtils.writeBlockPos(bearingPos));
        tag.putBoolean("Powered", powered);
        tag.putFloat("AngularPerTick", angularPerTick);
        tag.putBoolean("FacingNegative", facingNegative);
        tag.putFloat("SpeedOverride", speedOverride);
        tag.putBoolean("NeighborSignal", neighborSignal);
        CompoundTag motionTag = new CompoundTag();
        motion.write(motionTag);
        tag.put("Motion", motionTag);
        signals.write(tag);
        return tag;
    }

    @Nullable
    static GhostTurntable read(CompoundTag tag) {
        ResourceLocation dimension = ResourceLocation.tryParse(tag.getString("Dimension"));
        BlockPos pos = NbtUtils.readBlockPos(tag, "BearingPos").orElse(null);
        if (!tag.hasUUID("Plate") || dimension == null || pos == null) {
            return null;
        }
        GhostTurntable ghost = new GhostTurntable(tag.getUUID("Plate"),
                ResourceKey.create(Registries.DIMENSION, dimension), pos);
        ghost.snapshot(tag.getBoolean("Powered"), tag.getFloat("AngularPerTick"), tag.getBoolean("FacingNegative"),
                tag.getFloat("SpeedOverride"), tag.getBoolean("NeighborSignal"));
        ghost.motion.read(tag.getCompound("Motion"));
        ghost.signals.read(tag);
        return ghost;
    }
}
