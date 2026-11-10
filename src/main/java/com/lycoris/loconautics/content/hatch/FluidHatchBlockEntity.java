package com.lycoris.loconautics.content.hatch;

import com.lycoris.loconautics.common.dock.DockPolling;
import com.lycoris.loconautics.common.dock.IDockable;
import com.lycoris.loconautics.config.LoconauticsConfig;
import com.lycoris.loconautics.content.hose.nozzle.HoseNozzleBlockEntity;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.createmod.catnip.animation.LerpedFloat;
import net.createmod.catnip.animation.LerpedFloat.Chaser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import org.joml.Quaterniond;

import java.util.List;

public class FluidHatchBlockEntity extends SmartBlockEntity implements IDockable {

    private boolean docked;

    @org.jetbrains.annotations.Nullable private HoseNozzleBlockEntity dockedNozzle;

    private final FluidTank tank;

    private final HatchWaterSocket socket;

    public final LerpedFloat lid = LerpedFloat.linear().startWithValue(0);

    public FluidHatchBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        lid.startWithValue(state.getValue(FluidHatchBlock.OPEN) ? 1 : 0);
        this.tank = new FluidTank(LoconauticsConfig.INJECTOR_TANK_CAPACITY.get(),
                stack -> stack.getFluid() == Fluids.WATER) {
            @Override
            protected void onContentsChanged() {
                setChanged();
            }
        };
        this.socket = new HatchWaterSocket(this,
                () -> getBlockState().getValue(FluidHatchBlock.FACING),
                () -> getBlockState().getValue(FluidHatchBlock.RAISED),
                this::dockSeatOffset, tank);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
    }

    public boolean isOpen() {
        return getBlockState().getValue(FluidHatchBlock.OPEN);
    }

    public boolean isDocked() {
        return docked;
    }

    @Override
    public void tick() {
        super.tick();
        lid.chase(isOpen() ? 1 : 0, 0.2f, Chaser.LINEAR);
        lid.tickChaser();
        if (level == null || level.isClientSide) {
            return;
        }
        DockPolling.maybeUndock(docked, dockedNozzle, n -> !n.isRemoved() && n.isDocked(), this::onUndocked);
        if (docked && dockedNozzle != null && level.hasNeighborSignal(worldPosition)) {
            dockedNozzle.forceUndock();
        }
        SubLevel hatchSub = Sable.HELPER.getContaining(this);
        Vec3 anchor = socket.dockAnchorLocal();
        DockPolling.poll(this, NozzleDockService.all(),
                nozzle -> !nozzle.isRemoved() && !nozzle.isDocked() && nozzle.canRedock(level.getGameTime()),
                nozzle -> Sable.HELPER.getContaining(nozzle),
                HoseNozzleBlockEntity::worldCenter,
                (nozzle, sub) -> {
                    nozzle.dock(this, hatchSub, anchor, getDockOrientation());
                    dockedNozzle = nozzle;
                    onDocked();
                });
    }


    @Override
    public Vec3 getDockPoint() {
        return socket.getDockPoint();
    }

    @Override
    public Quaterniond getDockOrientation() {
        return socket.getDockOrientation();
    }

    @Override
    public double dockRadius() {
        return 0.5;
    }

    @Override
    public double dockSeatOffset() {
        return 3.0 / 16.0;
    }

    @Override
    public boolean canDock() {
        return isOpen() && !docked && (level == null || !level.hasNeighborSignal(worldPosition));
    }

    @Override
    public boolean isDockPresent() {
        return !isRemoved();
    }

    @Override
    public void onDocked() {
        docked = true;
        setChanged();
    }

    @Override
    public void onUndocked() {
        docked = false;
        dockedNozzle = null;
        setChanged();
    }

    @Override
    @org.jetbrains.annotations.Nullable
    public IFluidHandler getDockedFluidHandler() {
        return socket.getDockedFluidHandler();
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putBoolean("Docked", docked);
        tag.put("Tank", tank.writeToNBT(registries, new CompoundTag()));
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        docked = tag.getBoolean("Docked");
        tank.readFromNBT(registries, tag.getCompound("Tank"));
    }
}
