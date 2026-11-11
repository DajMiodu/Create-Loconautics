package com.lycoris.loconautics.common.turntable;

import com.lycoris.loconautics.common.turntable.sequence.TurntableInstruction;
import com.lycoris.loconautics.common.turntable.sequence.TurntableInstructionType;
import com.lycoris.loconautics.common.turntable.sequence.TurntableTriggerMode;
import com.simibubi.create.content.trains.graph.TrackNodeLocation;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

public final class TurntableMotion {

    private static final int HOME_DELAY_TICKS = 40;
    private static final double DEGREE_EPSILON = 1.0e-4;
    // the servo overshoots a little, so a turn counts once it held still this long
    private static final int ALIGN_SETTLE_TICKS = 5;

    public interface Host extends Turntable {

        // signed degrees per tick the plate would turn at its current speed, before any facing flip
        float angularPerTick();

        boolean facingNegative();

        float speedOverride();

        boolean neighborSignal();

        void motionChanged();

        void bodiesMoved();
    }

    private double lastTargetAngleDegrees;
    private double targetAngleDegrees;

    private List<TurntableInstruction> instructions = TurntableInstruction.defaultList();
    private TurntableTriggerMode trigger = TurntableTriggerMode.PULSE;
    private int currentInstruction = -1;
    private double remainingDegrees;
    private int delayTimer;
    private boolean poweredPreviously;
    private int directionSign = 1;
    private int homeDelay;

    @Nullable
    private TrackNodeLocation alignMovingHead;
    @Nullable
    private TrackNodeLocation alignTarget;
    private int alignedTicks;

    private double manualRemainingDegrees;
    private int manualSign = 1;

    public void beginTick() {
        lastTargetAngleDegrees = targetAngleDegrees;
    }

    public void step(Host host, boolean scheduleDriven) {
        if (scheduleDriven) {
            advanceAlignment(host);
            return;
        }
        if (manualRemainingDegrees > DEGREE_EPSILON) {
            advanceManualTurn(host);
            return;
        }
        runSequence(host);
    }

    public void copyFrom(TurntableMotion other) {
        lastTargetAngleDegrees = other.lastTargetAngleDegrees;
        targetAngleDegrees = other.targetAngleDegrees;
        instructions = TurntableInstruction.sanitize(other.instructions);
        trigger = other.trigger;
        currentInstruction = other.currentInstruction;
        remainingDegrees = other.remainingDegrees;
        delayTimer = other.delayTimer;
        poweredPreviously = other.poweredPreviously;
        directionSign = other.directionSign;
        homeDelay = other.homeDelay;
        alignMovingHead = other.alignMovingHead;
        alignTarget = other.alignTarget;
        alignedTicks = other.alignedTicks;
        manualRemainingDegrees = other.manualRemainingDegrees;
        manualSign = other.manualSign;
    }

    public void reset() {
        targetAngleDegrees = 0.0;
        lastTargetAngleDegrees = 0.0;
        currentInstruction = -1;
        manualRemainingDegrees = 0.0;
    }

    public double targetAngleDegrees() {
        return targetAngleDegrees;
    }

    public double lastTargetAngleDegrees() {
        return lastTargetAngleDegrees;
    }

    public List<TurntableInstruction> instructions() {
        return instructions;
    }

    public TurntableTriggerMode trigger() {
        return trigger;
    }

    public void setTrigger(TurntableTriggerMode mode) {
        trigger = mode;
    }

    public int currentInstruction() {
        return currentInstruction;
    }

    public double manualRemainingDegrees() {
        return manualRemainingDegrees;
    }

    public boolean scheduleDriven() {
        return TurntableInstruction.isScheduleDriven(instructions);
    }

    public boolean isTurning() {
        return manualRemainingDegrees > DEGREE_EPSILON || currentInstruction >= 0;
    }

    public boolean settled() {
        return alignTarget == null && targetAngleDegrees == lastTargetAngleDegrees;
    }

    public boolean busy(@Nullable UUID plate) {
        return isTurning() || alignTarget != null || !settled()
                || Math.abs(Mth.wrapDegrees(targetAngleDegrees)) > TurntableRegistry.ALIGN_EPSILON
                || TurntableRegistry.trainOnPlate(plate) || TurntableRegistry.occupied(plate);
    }

    public void alignTo(TrackNodeLocation movingHead, TrackNodeLocation target) {
        alignMovingHead = movingHead;
        alignTarget = target;
        alignedTicks = 0;
    }

    public void clearAlignment() {
        alignMovingHead = null;
        alignTarget = null;
        alignedTicks = 0;
        homeDelay = HOME_DELAY_TICKS;
    }

    @Nullable
    public TrackNodeLocation alignTarget() {
        return alignTarget;
    }

    public boolean aligned() {
        return alignTarget != null && alignMovingHead != null && alignedTicks >= ALIGN_SETTLE_TICKS;
    }

    public void turnBy(double degrees) {
        manualSign = degrees < 0.0 ? -1 : 1;
        manualRemainingDegrees = Math.abs(degrees);
        currentInstruction = -1;
        remainingDegrees = 0.0;
        delayTimer = 0;
    }

    public void turnTo(double absoluteDegrees) {
        turnBy(Mth.wrapDegrees(absoluteDegrees - targetAngleDegrees));
    }

    public void stopTurning(Host host) {
        manualRemainingDegrees = 0.0;
        currentInstruction = -1;
        remainingDegrees = 0.0;
        delayTimer = 0;
        host.motionChanged();
    }

    public void runInstruction(Host host, int index) {
        manualRemainingDegrees = 0.0;
        run(host, index);
    }

    public void configure(Host host, List<TurntableInstruction> newInstructions, TurntableTriggerMode newTrigger) {
        instructions = TurntableInstruction.sanitize(newInstructions);
        trigger = newTrigger;
        currentInstruction = -1;
        remainingDegrees = 0.0;
        delayTimer = 0;
        manualRemainingDegrees = 0.0;
        clearAlignment();
        host.motionChanged();
    }

    // the state change of the redstone input only, the bearing sets its own powered block state
    public void redstoneEdge(Host host, boolean isPowered, boolean scheduleDriven) {
        if (scheduleDriven) {
            poweredPreviously = isPowered;
            return;
        }
        if (!poweredPreviously && isPowered) {
            if (currentInstruction < 0) {
                if (trigger == TurntableTriggerMode.ALTERNATE) {
                    directionSign = -directionSign;
                }
                run(host, 0);
            } else if (instructions.get(currentInstruction).type == TurntableInstructionType.AWAIT) {
                run(host, currentInstruction + 1);
            }
        }
        poweredPreviously = isPowered;
    }

    private void advanceAlignment(Host host) {
        UUID plate = host.plateSubLevelId();
        if (alignTarget != null && !TurntableRegistry.reserved(plate)) {
            clearAlignment();
        }
        if (alignTarget == null || alignMovingHead == null) {
            returnHome(host);
            return;
        }
        double error = TurntableRegistry.alignmentError(host, alignMovingHead, alignTarget);
        alignedTicks = Math.abs(error) <= TurntableRegistry.ALIGN_EPSILON ? alignedTicks + 1 : 0;
        stepBy(host, error);
    }

    private void returnHome(Host host) {
        if (homeDelay > 0) {
            homeDelay--;
            return;
        }
        UUID plate = host.plateSubLevelId();
        if (TurntableRegistry.occupied(plate) || TurntableRegistry.reserved(plate)) {
            return;
        }
        stepBy(host, -Mth.wrapDegrees(targetAngleDegrees));
    }

    private void stepBy(Host host, double error) {
        if (Math.abs(error) <= TurntableRegistry.ALIGN_EPSILON) {
            return;
        }
        double perTick = Math.abs(host.angularPerTick());
        if (perTick == 0.0) {
            return;
        }
        targetAngleDegrees = (targetAngleDegrees
                + Math.min(perTick, Math.abs(error)) * Math.signum(error)) % 360.0;
        TurntableRegistry.unweldPlateSeams(host.plateSubLevelId());
        host.bodiesMoved();
    }

    private void advanceManualTurn(Host host) {
        double perTick = Math.abs(host.angularPerTick());
        if (perTick == 0.0) {
            return;
        }
        int overrideSign = host.speedOverride() < 0.0f ? -1 : 1;
        double step = Math.min(perTick, manualRemainingDegrees) * manualSign * overrideSign;
        targetAngleDegrees = (targetAngleDegrees + step) % 360.0;
        manualRemainingDegrees -= Math.abs(step);
        TurntableRegistry.unweldPlateSeams(host.plateSubLevelId());
        host.bodiesMoved();
        if (manualRemainingDegrees <= DEGREE_EPSILON) {
            manualRemainingDegrees = 0.0;
            host.motionChanged();
        }
    }

    private void runSequence(Host host) {
        if (currentInstruction < 0 || currentInstruction >= instructions.size()) {
            return;
        }
        TurntableInstruction instruction = instructions.get(currentInstruction);
        switch (instruction.type) {
            case TURN_ANGLE -> advanceTurn(host, instruction);
            case DELAY -> {
                if (--delayTimer <= 0) {
                    run(host, currentInstruction + 1);
                }
            }
            case AWAIT -> {
            }
            case END -> run(host, -1);
        }
    }

    private void advanceTurn(Host host, TurntableInstruction instruction) {
        float perTick = host.angularPerTick() * instruction.signedSpeedModifier() * directionSign;
        if (host.facingNegative()) {
            perTick = -perTick;
        }
        if (perTick == 0.0f) {
            return;
        }
        double step = Math.min(Math.abs(perTick), remainingDegrees) * Math.signum(perTick);
        targetAngleDegrees = (targetAngleDegrees + step) % 360.0;
        remainingDegrees -= Math.abs(step);
        host.bodiesMoved();
        if (remainingDegrees <= DEGREE_EPSILON) {
            run(host, currentInstruction + 1);
        }
    }

    private void run(Host host, int index) {
        if (index < 0 || index >= instructions.size()
                || instructions.get(index).type == TurntableInstructionType.END) {
            currentInstruction = -1;
            remainingDegrees = 0.0;
            delayTimer = 0;
            if (trigger == TurntableTriggerMode.HOLD && host.neighborSignal()
                    && !instructions.isEmpty() && instructions.get(0).type != TurntableInstructionType.END) {
                run(host, 0);
                return;
            }
            host.motionChanged();
            return;
        }
        TurntableInstruction instruction = instructions.get(index);
        currentInstruction = index;
        remainingDegrees = instruction.type == TurntableInstructionType.TURN_ANGLE ? instruction.value : 0.0;
        delayTimer = instruction.type == TurntableInstructionType.DELAY ? instruction.value : 0;
        host.motionChanged();
    }

    public void write(CompoundTag compound) {
        compound.putDouble("TargetAngle", targetAngleDegrees);
        compound.put("Instructions", TurntableInstruction.writeList(instructions));
        compound.putString("Trigger", trigger.name());
        compound.putInt("InstructionIndex", currentInstruction);
        compound.putDouble("RemainingDegrees", remainingDegrees);
        compound.putInt("DelayTimer", delayTimer);
        compound.putInt("DirectionSign", directionSign);
        compound.putBoolean("PoweredPreviously", poweredPreviously);
        compound.putDouble("ManualRemaining", manualRemainingDegrees);
        compound.putInt("ManualSign", manualSign);
    }

    public void read(CompoundTag compound) {
        targetAngleDegrees = compound.getDouble("TargetAngle");
        lastTargetAngleDegrees = targetAngleDegrees;
        if (compound.contains("Instructions")) {
            instructions = TurntableInstruction.readList(compound.getList("Instructions", Tag.TAG_COMPOUND));
        }
        trigger = triggerOrDefault(compound.getString("Trigger"));
        currentInstruction = compound.contains("InstructionIndex") ? compound.getInt("InstructionIndex") : -1;
        remainingDegrees = compound.getDouble("RemainingDegrees");
        delayTimer = compound.getInt("DelayTimer");
        directionSign = compound.contains("DirectionSign") ? compound.getInt("DirectionSign") : 1;
        poweredPreviously = compound.getBoolean("PoweredPreviously");
        manualRemainingDegrees = compound.getDouble("ManualRemaining");
        manualSign = compound.contains("ManualSign") ? compound.getInt("ManualSign") : 1;
    }

    private static TurntableTriggerMode triggerOrDefault(String name) {
        for (TurntableTriggerMode mode : TurntableTriggerMode.values()) {
            if (mode.name().equals(name)) {
                return mode;
            }
        }
        return TurntableTriggerMode.PULSE;
    }
}
