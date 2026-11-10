package com.lycoris.loconautics.content.industrial;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.foundation.blockEntity.ComparatorUtil;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.fluid.SmartFluidTank;
import net.createmod.catnip.animation.LerpedFloat;
import net.createmod.catnip.animation.LerpedFloat.Chaser;
import net.createmod.catnip.nbt.NBTHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;

import java.util.List;
import java.util.Set;

public class IndustrialTankBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation {

    private static final int SYNC_RATE = 8;

    private final SmartFluidTank tank;
    private final IFluidHandler handler = new HandlerView();

    private BlockPos controller;
    private int size = 1;
    private int minY;
    private int[] layerCounts = {1};
    private int luminosity;

    private int syncCooldown;
    private boolean queuedSync;
    private boolean forceFluidLevelUpdate = true;
    private LerpedFloat fluidLevel;

    private int lastLight = -1;
    private int lastLit = -1;
    private int lastComparator = -1;
    private boolean lastReversed;

    public IndustrialTankBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        tank = new SmartFluidTank(cellCapacity(), this::onFluidChanged);
    }

    public static int cellCapacity() {
        return (int) (FluidTankBlockEntity.getCapacityMultiplier() * IndustrialBlocks.CAPACITY_FACTOR);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {}

    public IFluidHandler getFluidHandler() {
        return handler;
    }

    public boolean isController() {
        return controller == null || controller.equals(worldPosition);
    }

    public BlockPos getControllerPos() {
        return isController() ? worldPosition : controller;
    }

    public IndustrialTankBlockEntity getControllerBE() {
        if (isController() || !hasLevel()) return this;
        BlockEntity be = level.getBlockEntity(controller);
        return be instanceof IndustrialTankBlockEntity tankBE ? tankBE : null;
    }

    public FluidStack getFluid() {
        return tank.getFluid();
    }

    public int getSize() {
        return size;
    }

    public int[] getLayerCounts() {
        return layerCounts;
    }

    public int getMinY() {
        return minY;
    }

    public int getLuminosity() {
        return luminosity;
    }

    public float fillState() {
        return (float) tank.getFluidAmount() / tank.getCapacity();
    }

    public float cellFill(int cellY, float partialTicks) {
        if (fluidLevel == null) return 0;
        int index = cellY - minY;
        if (index < 0 || index >= layerCounts.length) return 0;

        float volume = fluidLevel.getValue(partialTicks) * size;
        boolean reversed = tank.getFluid().getFluid().getFluidType().isLighterThanAir();
        float before = 0;
        for (int i = 0; i < layerCounts.length; i++) {
            if (reversed ? i > index : i < index) before += layerCounts[i];
        }
        return Mth.clamp((volume - before) / layerCounts[index], 0, 1);
    }

    public void configureController(int newSize, int newMinY, int[] newLayers, FluidStack content) {
        controller = null;
        size = newSize;
        minY = newMinY;
        layerCounts = newLayers;
        int capacity = newSize * cellCapacity();
        FluidStack limited = content.copy();
        if (limited.getAmount() > capacity) limited.setAmount(capacity);
        tank.setCapacity(capacity);
        tank.setFluid(limited);
        forceFluidLevelUpdate = true;
        refreshSummary(true);
        setChanged();
        sendDataNow();
    }

    public void becomeMember(BlockPos controllerPos) {
        controller = controllerPos;
        size = 1;
        minY = worldPosition.getY();
        layerCounts = new int[] {1};
        tank.setCapacity(cellCapacity());
        tank.setFluid(FluidStack.EMPTY);
        fluidLevel = null;
        setChanged();
        sendDataNow();
    }

    public void setLuminosity(int value) {
        if (luminosity == value) return;
        luminosity = value;
        setChanged();
        sendData();
    }

    private void onFluidChanged(FluidStack stack) {
        if (!hasLevel() || level.isClientSide || !isController()) return;
        refreshSummary(false);
        setChanged();
        sendData();
    }

    private void refreshSummary(boolean force) {
        if (!hasLevel() || level.isClientSide) return;

        FluidStack fluid = tank.getFluid();
        FluidType type = fluid.getFluid().getFluidType();
        int light = (int) (type.getLightLevel(fluid) / 1.2f);
        boolean reversed = type.isLighterThanAir();
        float fill = fillState();
        int lit = Math.min(layerCounts.length, (int) (fill * layerCounts.length) + 1);
        int comparator = ComparatorUtil.fractionToRedstoneLevel(fill);

        boolean lightChanged = light != lastLight || lit != lastLit || reversed != lastReversed;
        boolean comparatorChanged = comparator != lastComparator;
        if (!force && !lightChanged && !comparatorChanged) return;
        lastLight = light;
        lastLit = lit;
        lastReversed = reversed;
        lastComparator = comparator;

        TankMultiblock.Group group = TankMultiblock.collect(level, List.of(worldPosition), Set.of());
        for (BlockPos pos : group.cells()) {
            if (!(level.getBlockEntity(pos) instanceof IndustrialTankBlockEntity cell)) continue;
            if (!cell.getControllerPos().equals(worldPosition)) continue;
            int index = Mth.clamp(pos.getY() - minY, 0, layerCounts.length - 1);
            boolean bright = reversed ? layerCounts.length - index <= lit : index < lit;
            cell.setLuminosity(bright ? light : light > 0 ? 1 : 0);
            level.updateNeighbourForOutputSignal(pos, cell.getBlockState().getBlock());
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (syncCooldown > 0) {
            syncCooldown--;
            if (syncCooldown == 0 && queuedSync) sendData();
        }
        if (fluidLevel != null) fluidLevel.tickChaser();
    }

    @Override
    public void lazyTick() {
        super.lazyTick();
        if (!(level instanceof ServerLevel serverLevel) || isController()) return;
        if (!serverLevel.isLoaded(controller)) return;
        if (serverLevel.getBlockEntity(controller) instanceof IndustrialTankBlockEntity head && head.isController()) {
            return;
        }
        TankMultiblock.reform(serverLevel, worldPosition);
    }

    @Override
    public void initialize() {
        super.initialize();
        sendData();
    }

    @Override
    public void sendData() {
        if (syncCooldown > 0) {
            queuedSync = true;
            return;
        }
        super.sendData();
        queuedSync = false;
        syncCooldown = SYNC_RATE;
    }

    public void sendDataNow() {
        syncCooldown = 0;
        queuedSync = false;
        sendData();
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        IndustrialTankBlockEntity head = getControllerBE();
        if (head == null) return false;
        return containedFluidTooltip(tooltip, isPlayerSneaking,
                level.getCapability(Capabilities.FluidHandler.BLOCK, head.getBlockPos(), null));
    }

    @Override
    protected void read(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(compound, registries, clientPacket);

        int lightBefore = luminosity;

        luminosity = compound.getInt("Luminosity");
        controller = compound.contains("Controller") ? NBTHelper.readBlockPos(compound, "Controller") : null;

        if (isController()) {
            size = Math.max(1, compound.getInt("Size"));
            minY = compound.getInt("MinY");
            int[] layers = compound.getIntArray("Layers");
            layerCounts = layers.length == 0 ? new int[] {size} : layers;
            tank.setCapacity(size * cellCapacity());
            tank.readFromNBT(registries, compound.getCompound("TankContent"));
            if (tank.getSpace() < 0) tank.drain(-tank.getSpace(), FluidAction.EXECUTE);
        } else {
            size = 1;
        }

        if (compound.contains("ForceFluidLevel") || fluidLevel == null) {
            fluidLevel = LerpedFloat.linear().startWithValue(fillState());
        }

        if (!clientPacket) return;

        if (isController()) fluidLevel.chase(fillState(), 0.5f, Chaser.EXP);
        if (luminosity != lightBefore && hasLevel()) {
            level.getChunkSource().getLightEngine().checkBlock(worldPosition);
        }
        if (compound.contains("LazySync")) {
            fluidLevel.chase(fluidLevel.getChaseTarget(), 0.125f, Chaser.EXP);
        }
    }

    @Override
    protected void write(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        compound.putInt("Luminosity", luminosity);
        if (!isController()) {
            compound.put("Controller", NbtUtils.writeBlockPos(controller));
        } else {
            compound.put("TankContent", tank.writeToNBT(registries, new CompoundTag()));
            compound.putInt("Size", size);
            compound.putInt("MinY", minY);
            compound.putIntArray("Layers", layerCounts);
        }
        super.write(compound, registries, clientPacket);

        if (!clientPacket) return;
        if (forceFluidLevelUpdate) compound.putBoolean("ForceFluidLevel", true);
        if (queuedSync) compound.putBoolean("LazySync", true);
        forceFluidLevelUpdate = false;
    }

    private final class HandlerView implements IFluidHandler {

        private SmartFluidTank target() {
            IndustrialTankBlockEntity head = getControllerBE();
            return head == null ? null : head.tank;
        }

        @Override
        public int getTanks() {
            return 1;
        }

        @Override
        public FluidStack getFluidInTank(int index) {
            SmartFluidTank target = target();
            return target == null ? FluidStack.EMPTY : target.getFluid();
        }

        @Override
        public int getTankCapacity(int index) {
            SmartFluidTank target = target();
            return target == null ? 0 : target.getCapacity();
        }

        @Override
        public boolean isFluidValid(int index, FluidStack stack) {
            SmartFluidTank target = target();
            return target != null && target.isFluidValid(stack);
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            SmartFluidTank target = target();
            return target == null ? 0 : target.fill(resource, action);
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            SmartFluidTank target = target();
            return target == null ? FluidStack.EMPTY : target.drain(resource, action);
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            SmartFluidTank target = target();
            return target == null ? FluidStack.EMPTY : target.drain(maxDrain, action);
        }
    }
}
