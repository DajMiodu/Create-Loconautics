package com.lycoris.loconautics.content.buffer;

import com.lycoris.loconautics.common.copycat.CopycatBlockLogic;
import com.lycoris.loconautics.common.copycat.CopycatMaterialHolder;
import com.lycoris.loconautics.common.copycat.CopycatMaterialStore;
import com.lycoris.loconautics.common.copycat.CopycatPlacement;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.List;
import java.util.UUID;

public class BufferBlockEntity extends SmartBlockEntity implements BlockEntitySubLevelActor, CopycatMaterialHolder {

    private final CopycatMaterialStore copycat = new CopycatMaterialStore(this);

    @Nullable private transient UUID contactSubLevelId;
    @Nullable private transient BlockPos contactLocalPos;
    private transient volatile double pushDepth;
    private transient double smoothDepth;
    private transient int contactMisses;

    private float compressionPixels;

    private transient float shown = Float.NaN;
    private transient double shownClock = Double.NaN;

    public BufferBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
    }

    @Override
    public CopycatMaterialStore copycat() {
        return copycat;
    }

    @Override
    public ModelData getModelData() {
        return CopycatBlockLogic.modelData(copycat);
    }

    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(getBlockPos()).inflate(2.0);
    }

    public Direction facing() {
        return getBlockState().getValue(BufferBlock.FACING);
    }

    public BufferSize size() {
        return getBlockState().getValue(BufferSize.SIZE);
    }

    public Vector3d tipLocal() {
        Direction facing = facing();
        double out = size().extensionBlocks();
        return new Vector3d(
                getBlockPos().getX() + 0.5 + facing.getStepX() * out,
                getBlockPos().getY() + BufferTuning.HEAD_HEIGHT + getBlockState().getValue(CopycatPlacement.PLACEMENT).blocks(),
                getBlockPos().getZ() + 0.5 + facing.getStepZ() * out);
    }

    public float shownExtensionPixels(float partialTicks) {
        float target = size().extensionPixels() - compressionPixels;
        if (level == null || Float.isNaN(shown)) {
            shown = target;
        } else {
            double now = level.getGameTime() + partialTicks;
            double dt = Double.isNaN(shownClock) ? 0.0 : Math.max(0.0, now - shownClock);
            shownClock = now;
            shown += (target - shown) * (float) (1.0 - Math.exp(-dt * BufferTuning.DISPLAY_LERP * 4.0));
        }
        return Math.max(0.0f, shown);
    }

    void setContact(@Nullable UUID subLevelId, @Nullable BlockPos localPos) {
        if (subLevelId == null) {
            if (contactSubLevelId != null && ++contactMisses < BufferTuning.CONTACT_MISSES) {
                return;
            }
            pushDepth = 0.0;
        }
        contactMisses = 0;
        contactSubLevelId = subLevelId;
        contactLocalPos = localPos;
    }

    @Nullable
    UUID contactSubLevelId() {
        return contactSubLevelId;
    }

    @Nullable
    BlockPos contactLocalPos() {
        return contactLocalPos;
    }

    // rises fast and falls slow, so a pair that cannot move settles instead of flickering
    void syncCompression() {
        double target = Math.min(pushDepth * 0.5, size().extensionBlocks());
        smoothDepth += (target - smoothDepth) * (target > smoothDepth ? BufferTuning.ATTACK : BufferTuning.RELEASE);
        if (smoothDepth < BufferTuning.COMPRESSION_STEP * 0.5) {
            smoothDepth = 0.0;
        }
        float quantized = (float) (Math.round(smoothDepth / BufferTuning.COMPRESSION_STEP)
                * BufferTuning.COMPRESSION_STEP * 16.0);
        if (quantized != compressionPixels) {
            compressionPixels = quantized;
            setChanged();
            sendData();
        }
    }

    @Override
    public void sable$physicsTick(ServerSubLevel subLevel, RigidBodyHandle handle, double timeStep) {
        UUID partnerId = contactSubLevelId;
        BlockPos partnerPos = contactLocalPos;
        if (partnerId == null || partnerPos == null) {
            pushDepth = BufferSpring.pressWorld(subLevel, handle, this, timeStep);
            return;
        }
        if (subLevel.getUniqueId().compareTo(partnerId) >= 0) {
            return;
        }
        ServerSubLevelContainer container = SubLevelContainer.getContainer(subLevel.getLevel());
        if (container == null
                || !(container.getSubLevel(partnerId) instanceof ServerSubLevel partnerSub)
                || partnerSub.isRemoved() || partnerSub == subLevel
                || !(partnerSub.getLevel().getBlockEntity(partnerPos) instanceof BufferBlockEntity partnerBe)) {
            pushDepth = 0.0;
            return;
        }
        RigidBodyHandle partnerHandle = RigidBodyHandle.of(partnerSub);
        if (partnerHandle == null || !partnerHandle.isValid()) {
            pushDepth = 0.0;
            return;
        }
        double depth = BufferSpring.pressPair(subLevel, handle, this, partnerSub, partnerHandle, partnerBe, timeStep);
        pushDepth = depth;
        partnerBe.pushDepth = depth;
        if (depth > 0.0 && !BufferSpring.held(partnerSub)) {
            container.physicsSystem().getPipeline().wakeUp(partnerSub);
        }
    }

    static Vector3d worldTip(ServerSubLevel sub, BufferBlockEntity be) {
        return sub.logicalPose().transformPosition(be.tipLocal());
    }

    static Vector3d worldOutward(ServerSubLevel sub, BufferBlockEntity be) {
        Direction facing = be.facing();
        Vector3d out = new Vector3d(facing.getStepX(), 0.0, facing.getStepZ());
        return sub.logicalPose().transformNormal(out, out).normalize();
    }

    static Vector3d localOf(ServerSubLevel sub, Vector3dc worldDirection) {
        return new Quaterniond(sub.logicalPose().orientation()).conjugate().transform(new Vector3d(worldDirection));
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putFloat("Compression", compressionPixels);
        copycat.write(tag, registries);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        compressionPixels = tag.getFloat("Compression");
        copycat.read(tag, registries, clientPacket);
    }
}
