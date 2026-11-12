package com.lycoris.loconautics.content.combustion;

import com.lycoris.loconautics.common.advancement.TrainAdvancements;
import com.lycoris.loconautics.common.dock.DockPolling;
import com.lycoris.loconautics.common.dock.HorizontalDockPoint;
import com.lycoris.loconautics.common.dock.IDockable;
import com.lycoris.loconautics.compat.heat.PropulsionEngineHeat;
import com.lycoris.loconautics.content.hatch.NozzleDockService;
import com.lycoris.loconautics.content.hose.nozzle.HoseNozzleBlockEntity;
import com.lycoris.loconautics.content.controls.panel.PanelDriveBus;
import com.lycoris.loconautics.foundation.LoconauticsLang;
import com.lycoris.loconautics.foundation.smoke.SmokeEmitter;
import com.lycoris.loconautics.foundation.smoke.SmokeOptions;
import com.simibubi.create.content.kinetics.base.GeneratingKineticBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Vector3f;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class SmallEngineBlockEntity extends GeneratingKineticBlockEntity implements IDockable {

    private static final double SMOKE_SPREAD = 4.0 / 16.0;
    private static final int SMOKE_INTERVAL = 4;
    private static final float MIN_SMOKE_SHARE = 0.12f;
    private static final int MAX_SMOKE_PUFFS = 4;
    private static final float MIN_SMOKE_SCALE = 0.55f;
    private static final float MIN_SHARE = 0.001f;
    private static final float SPIN_UP = 0.014f;
    private static final float SPIN_DOWN = 0.01f;
    private static final int LEVEL_INTERVAL = 10;
    private static final int LEVEL_STEP = 100;
    private static final int NETWORK_INTERVAL = 10;
    private static final int SHOWN_STEP = 5;
    private static final int BURNS_FOR_SLACK = 40;

    public enum Status {
        RUNNING("running", ChatFormatting.GREEN),
        NO_FUEL("no_fuel", ChatFormatting.RED),
        SWITCHED_OFF("switched_off", ChatFormatting.GOLD),
        ROW_FULL("row_full", ChatFormatting.RED),
        IDLE("idle", ChatFormatting.GRAY);

        private final String key;
        private final ChatFormatting color;

        Status(String key, ChatFormatting color) {
            this.key = key;
            this.color = color;
        }
    }

    private final FluidTank tank;
    private final IFluidHandler inlet;
    private final IFluidHandler anySide;
    private final IFluidHandler dockPort;

    private boolean docked;
    private @Nullable HoseNozzleBlockEntity dockedNozzle;

    private boolean head = true;
    private boolean refused;
    private boolean running;
    private int controlSignal;
    private float ownRpm;
    private float burnCarry;
    private int fuelOrdinal = -1;
    private int poolAmount;
    private int poolCapacity = SmallEngineTuning.TANK_CAPACITY;
    private long burnsUntil = -1;
    private float poolRate;
    private long poolStamp;
    private FuelNetwork.Reading networkReading = FuelNetwork.Reading.NONE;
    private @Nullable BlockEntityBehaviour heat;

    private int activeEngines;
    private float rowRpm;

    private @Nullable SmokeEmitter smoke;
    private float pistonPhase;
    private float pistonPhasePrev;
    private float shownSpin;
    private boolean spinStarted;
    private boolean spinLoggedRunning;
    private float shownRpm;
    private float lastRpm;
    private float lastShare = 1f;

    public SmallEngineBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        this.tank = new FluidTank(SmallEngineTuning.TANK_CAPACITY, CombustionFuel::accepts) {
            @Override
            protected void onContentsChanged() {
                setChanged();
                sendData();
            }
        };
        this.inlet = new FuelInlet(this, Drain.NONE);
        this.anySide = new FuelInlet(this, Drain.OWN);
        this.dockPort = new FuelInlet(this, Drain.ROW);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);
        if (!PropulsionEngineHeat.loaded()) return;
        heat = PropulsionEngineHeat.create(this);
        behaviours.add(heat);
    }

    public FluidTank getTank() {
        return tank;
    }

    @Nullable
    public IFluidHandler fluidHandler(@Nullable Direction side) {
        if (side == null) return anySide;
        return side == Direction.DOWN ? inlet : null;
    }

    public boolean isHead() {
        return head;
    }

    public boolean isRunning() {
        return running;
    }

    public int getControlSignal() {
        return controlSignal;
    }

    public int getFuelAmount() {
        if (level == null || poolRate <= 0f) return poolAmount;
        float left = poolAmount - poolRate * Math.max(0L, level.getGameTime() - poolStamp);
        return Mth.clamp(Math.round(left), 0, poolCapacity);
    }

    public int getFuelCapacity() {
        return poolCapacity;
    }

    public float getOutputRpm() {
        return head ? rowRpm : ownRpm;
    }

    public float getOutputSu() {
        return head ? SmallEngineTuning.SU_PER_RPM * activeEngines * rowRpm : 0f;
    }

    // seconds of fuel left in the engines and the pipe network, counted down on the client from a synced end time
    public int getBurnsForSeconds() {
        if (burnsUntil < 0 || level == null) return -1;
        return (int) Math.max(0L, (burnsUntil - level.getGameTime()) / 20L);
    }

    public Status getStatus() {
        if (running) return Status.RUNNING;
        if (refused) return Status.ROW_FULL;
        if (SmallEngineTuning.isOff(controlSignal)) return Status.SWITCHED_OFF;
        if (poolAmount <= 0) return Status.NO_FUEL;
        return Status.IDLE;
    }

    @Nullable
    public static SmallEngineBlockEntity resolve(@Nullable Level level, BlockPos bound) {
        if (level == null || !level.isLoaded(bound)) return null;
        BlockEntity at = level.getBlockEntity(bound);
        if (at instanceof SmallEngineBlockEntity engine) return engine;
        if (at != null) return null;

        BlockPos survivor = EngineRow.survivorNear(level, bound);
        return survivor != null && level.getBlockEntity(survivor) instanceof SmallEngineBlockEntity engine
                ? engine : null;
    }

    public void driveFromPanel(int signal) {
        if (level == null || level.isClientSide) return;
        Direction facing = getBlockState().getValue(SmallEngineBlock.HORIZONTAL_FACING);
        BlockPos owner = EngineRow.headOf(level, worldPosition, facing);
        PanelDriveBus.report(level, owner, Mth.clamp(signal, 0, SmallEngineTuning.OFF_SIGNAL));
    }

    public void handOverFuel() {
        if (level == null) return;
        Direction facing = getBlockState().getValue(SmallEngineBlock.HORIZONTAL_FACING);

        List<SmallEngineBlockEntity> survivors = new ArrayList<>(2 * SmallEngineTuning.MAX_TANDEM);
        for (Direction side : new Direction[]{facing, facing.getOpposite()}) {
            BlockPos at = worldPosition;
            BlockState from = getBlockState();
            for (int i = 0; i < SmallEngineTuning.MAX_TANDEM && EngineRow.linked(level, at, from, facing, side); i++) {
                at = at.relative(side);
                from = level.getBlockState(at);
                if (level.getBlockEntity(at) instanceof SmallEngineBlockEntity near) survivors.add(near);
            }
        }

        FluidStack total = tank.getFluid().copy();
        boolean sameFuel = true;
        for (SmallEngineBlockEntity near : survivors) {
            FluidStack other = near.tank.getFluid();
            if (other.isEmpty()) continue;
            if (total.isEmpty()) total = other.copy();
            else if (FluidStack.isSameFluidSameComponents(total, other)) total.grow(other.getAmount());
            else sameFuel = false;
        }
        if (total.isEmpty()) return;

        if (!sameFuel || survivors.isEmpty()) {
            FluidStack held = tank.getFluid().copy();
            for (SmallEngineBlockEntity near : survivors) {
                held.shrink(near.tank.fill(held, IFluidHandler.FluidAction.EXECUTE));
            }
            return;
        }

        int share = total.getAmount() / survivors.size();
        int extra = total.getAmount() - share * survivors.size();
        for (SmallEngineBlockEntity near : survivors) {
            int want = share + (extra-- > 0 ? 1 : 0);
            int kept = Math.min(want, near.tank.getCapacity());
            near.tank.setFluid(total.copyWithAmount(kept));
            near.setChanged();
            near.sendData();
        }
    }

    @Override
    public void remove() {
        if (level != null && !level.isClientSide) PanelDriveBus.release(level, worldPosition);
        if (level != null && level.isClientSide && smoke != null) {
            smoke.stop();
            smoke = null;
        }
        super.remove();
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null) return;
        if (level.isClientSide) {
            clientTick();
            return;
        }

        Direction facing = getBlockState().getValue(SmallEngineBlock.HORIZONTAL_FACING);
        BlockPos headPos = EngineRow.headOf(level, worldPosition, facing);
        List<BlockPos> members = EngineRow.membersFrom(level, headPos, facing);
        List<SmallEngineBlockEntity> engines = enginesAt(members);

        boolean nowHead = headPos.equals(worldPosition);
        boolean nowRefused = !members.contains(worldPosition);
        int nowSignal = rowSignal(members, headPos);

        FluidTank source = nowRefused ? tank : fuelSource(engines);
        CombustionFuel fuel = CombustionFuel.of(source.getFluid());
        boolean nowRunning = !nowRefused && burn(fuel, nowSignal, source, engines);
        float nowRpm = nowRunning ? fuel.topRpm() * SmallEngineTuning.speedFraction(nowSignal) : 0f;

        int nowPool = 0;
        int nowCapacity = tank.getCapacity();
        if (!nowRefused) {
            nowCapacity = engines.size() * SmallEngineTuning.TANK_CAPACITY;
            for (SmallEngineBlockEntity engine : engines) nowPool += engine.tank.getFluidAmount();
        } else {
            nowPool = tank.getFluidAmount();
        }

        if (!nowRefused && level.getGameTime() % NETWORK_INTERVAL == 0) {
            networkReading = FuelNetwork.read(level, members, source.getFluid());
        } else if (nowRefused) {
            networkReading = FuelNetwork.Reading.NONE;
        }
        long now = level.getGameTime();
        float nowBurn = 0f;
        if (fuel != null && !nowRefused) {
            float each = fuel.burnPerTick() * SmallEngineTuning.speedFraction(nowSignal);
            for (SmallEngineBlockEntity engine : engines) {
                if (engine == this ? nowRunning : engine.running) nowBurn += each;
            }
        }

        boolean rateMoved = Math.abs(nowBurn - poolRate) > 1e-4f;
        boolean poolDrifted = Math.abs(nowPool - getFuelAmount()) >= Math.max(SHOWN_STEP, nowCapacity / 200);
        boolean poolEdge = nowPool != poolAmount && (nowPool == 0 || nowPool == nowCapacity);
        boolean reanchor = rateMoved || poolDrifted || poolEdge || nowCapacity != poolCapacity;
        float nextRate = reanchor ? nowBurn : poolRate;
        int nextAmount = reanchor ? nowPool : poolAmount;
        long nextStamp = reanchor ? now : poolStamp;

        long nowBurnsUntil = nowBurn <= 0f ? -1L : burnsUntil;
        if (nowBurn > 0f) {
            float stored = networkReading.amount();
            for (SmallEngineBlockEntity engine : engines) stored += engine.tank.getFluidAmount();
            long estimate = now + (long) (stored / nowBurn);
            if (burnsUntil < 0 || rateMoved || Math.abs(estimate - burnsUntil) > BURNS_FOR_SLACK) nowBurnsUntil = estimate;
        }

        int nowFuelOrdinal = fuel == null ? -1 : fuel.ordinal();
        boolean lostHead = head && !nowHead;
        boolean changed = nowHead != head || nowFuelOrdinal != fuelOrdinal || nowRefused != refused || nowRunning != running
                || nowSignal != controlSignal || nowRpm != ownRpm
                || reanchor || nowBurnsUntil != burnsUntil;
        if (nowRunning && (!running || nowFuelOrdinal != fuelOrdinal) && level instanceof ServerLevel serverLevel) {
            TrainAdvancements.engineStarted(serverLevel, worldPosition, nowFuelOrdinal, members.size());
        }
        head = nowHead;
        refused = nowRefused;
        running = nowRunning;
        controlSignal = nowSignal;
        ownRpm = nowRpm;
        fuelOrdinal = nowFuelOrdinal;
        poolAmount = nextAmount;
        poolRate = nextRate;
        poolStamp = nextStamp;
        poolCapacity = nowCapacity;
        burnsUntil = nowBurnsUntil;
        if (changed) {
            setChanged();
            sendData();
        }

        if (heat != null) {
            float made = nowRunning ? PropulsionEngineHeat.perTick() * SmallEngineTuning.speedFraction(nowSignal) : 0f;
            PropulsionEngineHeat.tick(heat, level, worldPosition, made);
        }

        if (lostHead) {
            activeEngines = 0;
            rowRpm = 0f;
            updateGeneratedRotation();
        } else if (head) {
            refreshOutput(engines);
            if (level.getGameTime() % LEVEL_INTERVAL == 0) levelFuel(engines);
        }
        pollDock();
    }

    private Direction dockFace() {
        return getBlockState().getValue(SmallEngineBlock.HORIZONTAL_FACING).getOpposite();
    }

    private boolean atTail() {
        BlockState state = getBlockState();
        Direction facing = state.getValue(SmallEngineBlock.HORIZONTAL_FACING);
        return !EngineRow.joinsToward(state, facing, facing.getOpposite());
    }

    private void pollDock() {
        DockPolling.maybeUndock(docked, dockedNozzle, n -> !n.isRemoved() && n.isDocked(), this::onUndocked);
        if (!canDock()) return;
        SubLevel engineSub = Sable.HELPER.getContaining(this);
        Vec3 anchor = HorizontalDockPoint.anchorLocal(this, dockFace(), dockSeatOffset());
        DockPolling.poll(this, NozzleDockService.all(),
                nozzle -> !nozzle.isRemoved() && !nozzle.isDocked() && nozzle.canRedock(level.getGameTime()),
                nozzle -> Sable.HELPER.getContaining(nozzle),
                HoseNozzleBlockEntity::worldCenter,
                (nozzle, sub) -> {
                    nozzle.dock(this, engineSub, anchor, getDockOrientation());
                    dockedNozzle = nozzle;
                    onDocked();
                });
    }

    @Override
    public Vec3 getDockPoint() {
        return HorizontalDockPoint.worldPoint(this,
                HorizontalDockPoint.anchorLocal(this, dockFace(), dockSeatOffset()));
    }

    @Override
    public Quaterniond getDockOrientation() {
        return HorizontalDockPoint.orientation(dockFace());
    }

    @Override
    public double dockRadius() {
        return 0.75;
    }

    @Override
    public double dockSeatOffset() {
        return 1.0;
    }

    @Override
    public boolean canDock() {
        return level != null && !level.isClientSide && !docked && atTail();
    }

    @Override
    public boolean isDockPresent() {
        return !isRemoved() && atTail();
    }

    @Override
    public void onDocked() {
        docked = true;
    }

    @Override
    public void onUndocked() {
        docked = false;
        dockedNozzle = null;
    }

    @Override
    public @Nullable IFluidHandler getDockedFluidHandler() {
        return dockPort;
    }

    public float getShownSpin() {
        return shownSpin;
    }

    public float getRunningShare() {
        return SmallEngineTuning.speedFraction(controlSignal);
    }

    public Vector3f pistonShake(float partialTick, float seed) {
        return EnginePistons.shake(Mth.lerp(partialTick, pistonPhasePrev, pistonPhase), seed, shownRpm,
                running ? getRunningShare() : lastShare);
    }

    private void clientTick() {
        SmallEngineSounds.tick(this, running);
        pistonPhasePrev = pistonPhase;
        if (running != spinLoggedRunning) {
            spinLoggedRunning = running;
            com.lycoris.loconautics.LoconauticsConstants.LOGGER.info("[enginespin] running flipped to {} at {} tick={}",
                    running, worldPosition, level.getGameTime());
        }
        float spinTarget = running ? getRunningShare() : 0f;
        if (running) {
            lastShare = Math.max(spinTarget, MIN_SHARE);
            lastRpm = ownRpm;
        }
        if (!spinStarted) {
            spinStarted = true;
            if (running) shownSpin = spinTarget;
            com.lycoris.loconautics.LoconauticsConstants.LOGGER.info("[enginespin] new client engine at {} running={} rpm={} spin={}",
                    worldPosition, running, ownRpm, shownSpin);
        }
        float spinStep = spinTarget > shownSpin ? SPIN_UP : SPIN_DOWN;
        shownSpin += Mth.clamp(spinTarget - shownSpin, -spinStep, spinStep);
        shownRpm = lastRpm * Mth.clamp(shownSpin / lastShare, 0f, 1f);
        if (shownRpm > 0f) {
            pistonPhase += shownRpm / 60f / 20f * Mth.TWO_PI;
            if (pistonPhase > Mth.TWO_PI) {
                pistonPhase -= Mth.TWO_PI;
                pistonPhasePrev -= Mth.TWO_PI;
            }
        }
        if (!running) {
            if (smoke != null) smoke.stop();
            return;
        }
        if (smoke == null) smoke = new SmokeEmitter();
        smoke.track(new Vec3(worldPosition.getX() + 0.5, worldPosition.getY() + 1.0, worldPosition.getZ() + 0.5));
        if (level.getGameTime() % SMOKE_INTERVAL != 0) return;
        float share = getRunningShare();
        if (level.random.nextFloat() > Math.max(MIN_SMOKE_SHARE, share)) return;
        SmokeOptions exhaust = SmokeOptions.exhaust(share);
        SmokeOptions sized = new SmokeOptions(exhaust.color(), exhaust.style(), Mth.lerp(share, MIN_SMOKE_SCALE, 1f));
        smoke.puff(level, sized, 1 + Math.round(share * (MAX_SMOKE_PUFFS - 1)), SMOKE_SPREAD);
    }

    private static void levelFuel(List<SmallEngineBlockEntity> engines) {
        SmallEngineBlockEntity fullest = null;
        SmallEngineBlockEntity emptiest = null;
        for (SmallEngineBlockEntity engine : engines) {
            if (fullest == null || engine.tank.getFluidAmount() > fullest.tank.getFluidAmount()) fullest = engine;
            if (emptiest == null || engine.tank.getFluidAmount() < emptiest.tank.getFluidAmount()) emptiest = engine;
        }
        if (fullest == null || emptiest == fullest) return;

        int gap = fullest.tank.getFluidAmount() - emptiest.tank.getFluidAmount();
        if (gap < 2) return;

        FluidStack offer = fullest.tank.drain(Math.min(LEVEL_STEP, gap / 2), IFluidHandler.FluidAction.SIMULATE);
        int moved = emptiest.tank.fill(offer, IFluidHandler.FluidAction.EXECUTE);
        if (moved > 0) fullest.tank.drain(moved, IFluidHandler.FluidAction.EXECUTE);
    }

    private List<SmallEngineBlockEntity> enginesAt(List<BlockPos> members) {
        List<SmallEngineBlockEntity> engines = new ArrayList<>(members.size());
        for (BlockPos pos : members) {
            BlockEntity be = pos.equals(worldPosition) ? this : level.getBlockEntity(pos);
            if (be instanceof SmallEngineBlockEntity engine) engines.add(engine);
        }
        return engines;
    }

    List<SmallEngineBlockEntity> rowEngines() {
        if (level == null) return List.of(this);
        Direction facing = getBlockState().getValue(SmallEngineBlock.HORIZONTAL_FACING);
        BlockPos headPos = EngineRow.headOf(level, worldPosition, facing);
        List<SmallEngineBlockEntity> engines = enginesAt(EngineRow.membersFrom(level, headPos, facing));
        return engines.contains(this) ? engines : List.of(this);
    }

    private int rowSignal(List<BlockPos> members, BlockPos owner) {
        int signal = Math.round(PanelDriveBus.report(level, owner, 0f));
        for (BlockPos pos : members) {
            signal = Math.max(signal, level.getBestNeighborSignal(pos));
        }
        return Mth.clamp(signal, 0, SmallEngineTuning.OFF_SIGNAL);
    }

    private FluidTank fuelSource(List<SmallEngineBlockEntity> engines) {
        if (CombustionFuel.of(tank.getFluid()) != null) return tank;
        for (SmallEngineBlockEntity engine : engines) {
            if (CombustionFuel.of(engine.tank.getFluid()) != null) return engine.tank;
        }
        return tank;
    }

    private boolean burn(@Nullable CombustionFuel fuel, int signal, FluidTank source,
                         List<SmallEngineBlockEntity> engines) {
        if (fuel == null || SmallEngineTuning.isOff(signal)) return false;

        burnCarry += fuel.burnPerTick() * SmallEngineTuning.speedFraction(signal);
        int whole = (int) burnCarry;
        if (whole <= 0) return true;

        burnCarry -= whole;
        FluidStack kind = source.getFluid().copyWithAmount(1);
        int left = whole - source.drain(whole, IFluidHandler.FluidAction.EXECUTE).getAmount();
        for (SmallEngineBlockEntity engine : engines) {
            if (left <= 0) break;
            if (engine.tank == source || !FluidStack.isSameFluidSameComponents(engine.tank.getFluid(), kind)) continue;
            left -= engine.tank.drain(left, IFluidHandler.FluidAction.EXECUTE).getAmount();
        }
        return left <= 0;
    }

    private void refreshOutput(List<SmallEngineBlockEntity> engines) {
        int count = 0;
        float slowest = Float.MAX_VALUE;
        for (SmallEngineBlockEntity engine : engines) {
            if (!engine.running) continue;
            count++;
            slowest = Math.min(slowest, engine.ownRpm);
        }
        float nowRpm = count == 0 ? 0f : slowest;
        if (count == activeEngines && nowRpm == rowRpm) return;

        activeEngines = count;
        rowRpm = nowRpm;
        updateGeneratedRotation();
    }

    @Override
    public float getGeneratedSpeed() {
        if (!head || activeEngines <= 0 || rowRpm <= 0f) return 0f;
        return convertToDirection(rowRpm, SmallEngineBlock.output(getBlockState()));
    }

    @Override
    public float calculateAddedStressCapacity() {
        float capacity = head ? SmallEngineTuning.SU_PER_RPM * activeEngines : 0f;
        this.lastCapacityProvided = capacity;
        return capacity;
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        LoconauticsLang.translate("gui.goggles.small_engine_stats").forGoggles(tooltip);

        Status status = getStatus();
        Object[] args = status == Status.RUNNING
                ? new Object[]{Math.round(SmallEngineTuning.speedFraction(controlSignal) * 100f)}
                : new Object[0];
        LoconauticsLang.translate("gui.goggles.small_engine_status_" + status.key, args)
                .style(status.color).forGoggles(tooltip, 1);
        return true;
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.put("Tank", tank.writeToNBT(registries, new CompoundTag()));
        tag.putBoolean("Head", head);
        tag.putBoolean("Refused", refused);
        tag.putBoolean("Running", running);
        tag.putInt("Signal", controlSignal);
        tag.putFloat("OwnRpm", ownRpm);
        tag.putFloat("BurnCarry", burnCarry);
        tag.putInt("Fuel", fuelOrdinal);
        tag.putInt("Pool", poolAmount);
        tag.putInt("PoolCapacity", poolCapacity);
        tag.putFloat("PoolRate", poolRate);
        tag.putLong("PoolStamp", poolStamp);
        tag.putLong("BurnsUntil", burnsUntil);
        tag.putInt("Active", activeEngines);
        tag.putFloat("RowRpm", rowRpm);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        tank.readFromNBT(registries, tag.getCompound("Tank"));
        if (tank.getFluidAmount() > tank.getCapacity()) tank.getFluid().setAmount(tank.getCapacity());
        head = !tag.contains("Head") || tag.getBoolean("Head");
        refused = tag.getBoolean("Refused");
        running = tag.getBoolean("Running");
        controlSignal = tag.getInt("Signal");
        ownRpm = tag.getFloat("OwnRpm");
        burnCarry = tag.getFloat("BurnCarry");
        fuelOrdinal = tag.contains("Fuel") ? tag.getInt("Fuel") : -1;
        poolAmount = tag.contains("Pool") ? tag.getInt("Pool") : tank.getFluidAmount();
        poolCapacity = tag.contains("PoolCapacity") ? tag.getInt("PoolCapacity") : tank.getCapacity();
        poolRate = tag.getFloat("PoolRate");
        poolStamp = tag.getLong("PoolStamp");
        burnsUntil = tag.contains("BurnsUntil") ? tag.getLong("BurnsUntil") : -1L;
        activeEngines = tag.getInt("Active");
        rowRpm = tag.getFloat("RowRpm");
    }

    private enum Drain { NONE, OWN, ROW }

    private record FuelInlet(SmallEngineBlockEntity engine, Drain drain) implements IFluidHandler {

        @Override
        public int getTanks() {
            return 1;
        }

        @Override
        public FluidStack getFluidInTank(int index) {
            FluidStack kind = FluidStack.EMPTY;
            int total = 0;
            for (SmallEngineBlockEntity member : engine.rowEngines()) {
                if (member.tank.getFluid().isEmpty()) continue;
                if (kind.isEmpty()) kind = member.tank.getFluid();
                total += member.tank.getFluidAmount();
            }
            return kind.isEmpty() ? FluidStack.EMPTY : kind.copyWithAmount(total);
        }

        @Override
        public int getTankCapacity(int index) {
            return engine.rowEngines().size() * SmallEngineTuning.TANK_CAPACITY;
        }

        @Override
        public boolean isFluidValid(int index, FluidStack stack) {
            return CombustionFuel.accepts(stack);
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            int filled = 0;
            for (SmallEngineBlockEntity member : engine.rowEngines()) {
                if (filled >= resource.getAmount()) break;
                filled += member.tank.fill(resource.copyWithAmount(resource.getAmount() - filled), action);
            }
            return filled;
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            if (drain == Drain.NONE || resource.isEmpty()) return FluidStack.EMPTY;
            if (drain == Drain.OWN) return engine.tank.drain(resource, action);
            FluidStack taken = FluidStack.EMPTY;
            for (SmallEngineBlockEntity member : engine.rowEngines()) {
                int left = resource.getAmount() - taken.getAmount();
                if (left <= 0) break;
                FluidStack got = member.tank.drain(resource.copyWithAmount(left), action);
                if (got.isEmpty()) continue;
                if (taken.isEmpty()) taken = got;
                else taken.grow(got.getAmount());
            }
            return taken;
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            if (drain == Drain.NONE) return FluidStack.EMPTY;
            if (drain == Drain.OWN) return engine.tank.drain(maxDrain, action);
            for (SmallEngineBlockEntity member : engine.rowEngines()) {
                FluidStack kind = member.tank.getFluid();
                if (!kind.isEmpty()) return drain(kind.copyWithAmount(maxDrain), action);
            }
            return FluidStack.EMPTY;
        }
    }
}
