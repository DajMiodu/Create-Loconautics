package com.lycoris.loconautics.content.turntablebearing;

import com.lycoris.loconautics.registry.LoconauticsRegistries;
import com.simibubi.create.content.kinetics.base.IRotate;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

public class TurntablePlateBlockEntity extends KineticBlockEntity implements BlockEntitySubLevelActor {

    @Nullable
    private BlockPos parent;
    @Nullable
    private UUID parentSubLevelId;
    private boolean assembling;

    public TurntablePlateBlockEntity(BlockEntityType<?> typeIn, BlockPos pos, BlockState state) {
        super(typeIn, pos, state);
    }

    public void beforeAssembly() {
        assembling = true;
    }

    @Override
    public void remove() {
        if (level != null && !level.isClientSide && !assembling) {
            destroyBearing();
        }
        super.remove();
    }

    private void destroyBearing() {
        if (parent != null && level.getBlockState(parent).is(LoconauticsRegistries.TURNTABLE_BEARING.get())) {
            level.destroyBlock(parent, false);
        }
    }

    public void setParent(TurntableBearingBlockEntity be) {
        SubLevel subLevel = Sable.HELPER.getContaining(be);
        parent = be.getBlockPos();
        parentSubLevelId = subLevel != null ? subLevel.getUniqueId() : null;
    }

    @Override
    public float propagateRotationTo(KineticBlockEntity target, BlockState stateFrom, BlockState stateTo,
                                     BlockPos diff, boolean connectedViaAxes, boolean connectedViaCogs) {
        if (parent != null && target.equals(level.getBlockEntity(parent))) {
            return 1.0f;
        }
        return super.propagateRotationTo(target, stateFrom, stateTo, diff, connectedViaAxes, connectedViaCogs);
    }

    @Override
    public boolean isCustomConnection(KineticBlockEntity other, BlockState state, BlockState otherState) {
        return parent != null && other.equals(level.getBlockEntity(parent));
    }

    @Override
    public List<BlockPos> addPropagationLocations(IRotate block, BlockState state, List<BlockPos> neighbours) {
        if (parent != null) {
            neighbours.add(parent);
        }
        return super.addPropagationLocations(block, state, neighbours);
    }

    @Override
    protected void write(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(compound, registries, clientPacket);
        if (parent != null) {
            compound.put("ParentPos", NbtUtils.writeBlockPos(parent));
        }
        if (parentSubLevelId != null) {
            compound.putUUID("ParentSubLevelId", parentSubLevelId);
        }
    }

    @Override
    protected void read(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(compound, registries, clientPacket);
        if (compound.contains("ParentPos")) {
            parent = NbtUtils.readBlockPos(compound, "ParentPos").orElse(null);
        }
        if (compound.hasUUID("ParentSubLevelId")) {
            parentSubLevelId = compound.getUUID("ParentSubLevelId");
        }
    }

    @Override
    public void sable$physicsTick(ServerSubLevel subLevel, RigidBodyHandle handle, double timeStep) {
        if (parent != null && level.getBlockEntity(parent) instanceof TurntableBearingBlockEntity bearing) {
            bearing.updateServoCoefficients();
        }
    }

    @Override
    public @Nullable Iterable<@NotNull SubLevel> sable$getConnectionDependencies() {
        if (parent == null || parentSubLevelId == null) {
            return null;
        }
        SubLevel subLevel = SubLevelContainer.getContainer(level).getSubLevel(parentSubLevelId);
        return subLevel != null ? List.of(subLevel) : null;
    }

    public void setParentAssembleNextTick() {
        if (parent != null && level.getBlockEntity(parent) instanceof TurntableBearingBlockEntity bearing) {
            bearing.assembleNextTick = true;
        }
    }

    public void fixParentLinkingWhenMoved() {
        if (level.isClientSide() || parent == null) {
            return;
        }
        if (!(level.getBlockEntity(parent) instanceof TurntableBearingBlockEntity bearing)) {
            return;
        }
        bearing.setPlatePos(getBlockPos());
        BlockEntity self = this;
        ServerSubLevel newSubLevel = (ServerSubLevel) Sable.HELPER.getContaining(self);
        if (newSubLevel == null) {
            bearing.setSubLevelID(null);
            bearing.reattachConstraint(null, true);
            return;
        }
        UUID newId = newSubLevel.getUniqueId();
        if (!newId.equals(bearing.getSubLevelID())) {
            bearing.setSubLevelID(newId);
            bearing.reattachConstraint(newSubLevel, true);
        }
    }
}
