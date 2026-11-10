package com.lycoris.loconautics.content.knuckle;

import com.lycoris.loconautics.common.copycat.CopycatBlockLogic;
import com.lycoris.loconautics.common.copycat.CopycatMaterialHolder;
import com.lycoris.loconautics.common.copycat.CopycatMaterialStore;
import com.lycoris.loconautics.content.steelcable.SteelCableHolder;
import com.lycoris.loconautics.content.steelcable.SteelCableHolderBehaviour;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.lycoris.loconautics.trainphysics.sublevel.SubLevelContacts;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.List;
import java.util.UUID;

public class KnuckleBlockEntity extends SmartBlockEntity implements BlockEntitySubLevelActor, SteelCableHolder, CopycatMaterialHolder {

    private static final long NOT_ENGAGED = -1L;
    private static final double TIP_OUTWARD = 0.04;
    private static final double TIP_HEIGHT = 0.53;

    @Nullable private UUID partnerSubLevelId = null;
    @Nullable private BlockPos partnerLocalPos = null;
    private boolean coupled;

    private transient long rotateLockUntil;
    private transient long coupleLockUntil;
    @Nullable private transient UUID holdSubLevelId;
    @Nullable private transient BlockPos holdLocalPos;

    private transient long engagedAt = NOT_ENGAGED;
    private transient double engageFrom = KnuckleTuning.REST_LENGTH;
    private transient int missingScans;
    private transient KnuckleJoint.Live joint;

    private transient float clip;
    private transient boolean clipLatched;
    private transient double clipClock = Double.NaN;

    private final CopycatMaterialStore copycat = new CopycatMaterialStore(this);

    public KnuckleBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        super(type, pos, blockState);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        behaviours.add(new SteelCableHolderBehaviour(this, strandCapacity()));
    }

    @Override
    public Vec3 getAttachmentPoint() {
        Vector3d tip = tipLocal(getBlockPos(), getBlockState().getValue(KnuckleBlock.FACING));
        return new Vec3(tip.x, tip.y, tip.z);
    }

    public float animationClip(double hingeGap, float partialTicks) {
        float target = clipTarget(hingeGap);
        if (level == null) {
            return target;
        }
        double now = level.getGameTime() + partialTicks;
        boolean firstFrame = Double.isNaN(clipClock);
        double dt = firstFrame ? 0.0 : Math.max(0.0, now - clipClock);
        clipClock = now;
        if (firstFrame) {
            clip = target;
            return clip;
        }
        clip += (target - clip) * (float) (1.0 - Math.exp(-dt * KnuckleTuning.positionLerp));
        if (Math.abs(target - clip) < 1.0e-3f) {
            clip = target;
        }
        return clip;
    }

    private float clipTarget(double hingeGap) {
        if (!isConnected()) {
            clipLatched = false;
            if (isReleasing()) {
                return KnuckleTuning.clipForGap(hingeGap);
            }
            return isRedstoneLocked() ? KnuckleTuning.CLIP_LENGTH : 0.0f;
        }
        if (clipLatched) {
            return KnuckleTuning.CLIP_LENGTH;
        }
        float gapClip = KnuckleTuning.clipForGap(hingeGap);
        if (gapClip >= KnuckleTuning.CLIP_LENGTH) {
            clipLatched = true;
        }
        return gapClip;
    }

    public boolean isRedstoneLocked() {
        return level != null
                && KnuckleBlock.redstoneLocked(level, getBlockPos(), getBlockState().getValue(KnuckleBlock.FACING));
    }

    @Override
    public CopycatMaterialStore copycat() {
        return copycat;
    }

    @Override
    public net.neoforged.neoforge.client.model.data.ModelData getModelData() {
        return CopycatBlockLogic.modelData(copycat);
    }

    public boolean isConnected() {
        return coupled && partnerSubLevelId != null;
    }

    public boolean isReleasing() {
        return !coupled && partnerSubLevelId != null;
    }

    public boolean isController(UUID ownSubLevelId) {
        return partnerSubLevelId != null && ownSubLevelId.compareTo(partnerSubLevelId) < 0;
    }

    @Nullable
    public UUID getPartnerSubLevelId() {
        return partnerSubLevelId;
    }

    @Nullable
    public BlockPos getPartnerLocalPos() {
        return partnerLocalPos;
    }

    public void movePartnerPos(BlockPos partnerLocalPos) {
        this.partnerLocalPos = partnerLocalPos;
        this.missingScans = 0;
        setChanged();
    }

    public void setConnection(UUID partnerSubLevel, BlockPos partnerLocalPos) {
        this.partnerSubLevelId = partnerSubLevel;
        this.partnerLocalPos = partnerLocalPos;
        this.coupled = true;
        this.missingScans = 0;
        releaseCoupling();
        writeConnectedState(true);
        setChanged();
        sendData();
    }

    boolean partnerMissedTooLong() {
        return ++missingScans >= KnuckleTuning.MISSING_PARTNER_SCANS;
    }

    void partnerSeen() {
        missingScans = 0;
    }

    public void clearConnection() {
        returnContacts();
        releaseCoupling();
        coupled = false;
        if (level != null) {
            rotateLockUntil = level.getGameTime() + KnuckleTuning.ROTATE_LOCK_TICKS;
            coupleLockUntil = level.getGameTime() + KnuckleTuning.COUPLE_LOCK_TICKS;
        }
        writeConnectedState(false);
        setChanged();
        sendData();
    }

    private void returnContacts() {
        if (partnerSubLevelId != null && level != null && !level.isClientSide()
                && Sable.HELPER.getContaining(this) instanceof SubLevel own) {
            SubLevelContacts.enableCoupled(own.getUniqueId(), partnerSubLevelId);
        }
    }

    public void clearPartner() {
        partnerSubLevelId = null;
        partnerLocalPos = null;
        setChanged();
        sendData();
    }

    public boolean isRotateLocked() {
        return level != null && level.getGameTime() < rotateLockUntil;
    }

    public boolean isCouplingLocked() {
        return level != null && level.getGameTime() < coupleLockUntil;
    }

    void holdApartFrom(UUID otherSubLevel, BlockPos otherLocalPos) {
        holdSubLevelId = otherSubLevel;
        holdLocalPos = otherLocalPos;
    }

    void releaseHold() {
        holdSubLevelId = null;
        holdLocalPos = null;
    }

    boolean isHeldApart() {
        return holdSubLevelId != null;
    }

    @Nullable
    UUID holdSubLevelId() {
        return holdSubLevelId;
    }

    @Nullable
    BlockPos holdLocalPos() {
        return holdLocalPos;
    }

    private void writeConnectedState(boolean connected) {
        if (level == null) {
            return;
        }
        BlockState state = level.getBlockState(getBlockPos());
        if (state.hasProperty(KnuckleBlock.CONNECTED) && state.getValue(KnuckleBlock.CONNECTED) != connected) {
            level.setBlock(getBlockPos(), state.setValue(KnuckleBlock.CONNECTED, connected), 3);
        }
    }

    @Override
    public void sable$physicsTick(ServerSubLevel subLevel, RigidBodyHandle handle, double timeStep) {
        if (!coupled || partnerSubLevelId == null || partnerLocalPos == null || level == null
                || !isController(subLevel.getUniqueId())) {
            releaseCoupling();
            return;
        }
        ServerSubLevelContainer container = SubLevelContainer.getContainer(subLevel.getLevel());
        if (container == null
                || !(container.getSubLevel(partnerSubLevelId) instanceof ServerSubLevel partnerSub)
                || partnerSub == subLevel || partnerSub.isRemoved()
                || !(partnerSub.getLevel().getBlockEntity(partnerLocalPos) instanceof KnuckleBlockEntity partnerBe)
                || !partnerBe.isConnected()) {
            releaseCoupling();
            return;
        }
        Vector3d hingeA = hingeLocal(getBlockPos(), getBlockState().getValue(KnuckleBlock.FACING));
        Vector3d hingeB = hingeLocal(partnerBe.getBlockPos(), partnerBe.getBlockState().getValue(KnuckleBlock.FACING));
        if (engagedAt == NOT_ENGAGED) {
            Vector3d worldA = subLevel.logicalPose().transformPosition(new Vector3d(hingeA));
            Vector3d worldB = partnerSub.logicalPose().transformPosition(new Vector3d(hingeB));
            engagedAt = level.getGameTime();
            engageFrom = worldA.distance(worldB);
        }
        boolean bar308 = KnuckleRailCoupler.rigid(subLevel) && KnuckleRailCoupler.rigid(partnerSub);
        if (bar308) {
            KnuckleJoint.release(joint);
            joint = null;
            double length = engageFrom + (KnuckleTuning.REST_LENGTH - engageFrom) * engageGain();
            KnuckleRailCoupler.put(this, new KnuckleRailCoupler.Pair(subLevel.getUniqueId(), hingeA,
                    partnerSub.getUniqueId(), hingeB, length, level.getGameTime()));
            return;
        }
        KnuckleRailCoupler.remove(this);
        joint = KnuckleJoint.hold(joint, subLevel, hingeA, partnerSub, hingeB, engageFrom, engageGain());
    }

    @Override
    public void invalidate() {
        KnuckleRailCoupler.remove(this);
        KnuckleJoint.release(joint);
        joint = null;
        super.invalidate();
    }

    boolean engageDone() {
        return level != null && engagedAt != NOT_ENGAGED && level.getGameTime() - engagedAt >= KnuckleTuning.ENGAGE_TICKS;
    }

    private double engageGain() {
        long elapsed = level.getGameTime() - engagedAt;
        return Math.max(0.0, Math.min(1.0, (double) elapsed / KnuckleTuning.ENGAGE_TICKS));
    }

    public void releaseCoupling() {
        KnuckleRailCoupler.remove(this);
        KnuckleJoint.release(joint);
        joint = null;
        engagedAt = NOT_ENGAGED;
        engageFrom = KnuckleTuning.REST_LENGTH;
    }

    @Override
    public Iterable<SubLevel> sable$getConnectionDependencies() {
        if (coupled && partnerSubLevelId != null && level != null) {
            SubLevelContainer container = SubLevelContainer.getContainer(level);
            if (container != null) {
                SubLevel partner = container.getSubLevel(partnerSubLevelId);
                if (partner != null) {
                    return List.of(partner);
                }
            }
        }
        return List.of();
    }

    static Vector3d tipLocal(BlockPos p, Direction facing) {
        return new Vector3d(
                p.getX() + 0.5 + facing.getStepX() * TIP_OUTWARD,
                p.getY() + TIP_HEIGHT,
                p.getZ() + 0.5 + facing.getStepZ() * TIP_OUTWARD);
    }

    public Vector3d hingePlot() {
        return hingeLocal(getBlockPos(), getBlockState().getValue(KnuckleBlock.FACING));
    }

    static Vector3d hingeLocal(BlockPos p, Direction facing) {
        return new Vector3d(
                p.getX() + 0.5 - 0.5 * facing.getStepX(),
                p.getY() + 7.0 / 16.0,
                p.getZ() + 0.5 - 0.5 * facing.getStepZ());
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        if (partnerSubLevelId != null) {
            tag.putString("PartnerSubLevel", partnerSubLevelId.toString());
        }
        if (partnerLocalPos != null) {
            tag.putLong("PartnerLocalPos", partnerLocalPos.asLong());
        }
        tag.putBoolean("Coupled", coupled);
        copycat.write(tag, registries);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        partnerSubLevelId = tag.contains("PartnerSubLevel") ? UUID.fromString(tag.getString("PartnerSubLevel")) : null;
        partnerLocalPos = tag.contains("PartnerLocalPos") ? BlockPos.of(tag.getLong("PartnerLocalPos")) : null;
        coupled = tag.getBoolean("Coupled");
        copycat.read(tag, registries, clientPacket);
    }
}
