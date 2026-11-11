package com.lycoris.loconautics.compat.computercraft;

import com.lycoris.loconautics.common.turntable.TurntableRegistry;
import com.lycoris.loconautics.content.turntablebearing.TurntableBearingBlockEntity;
import com.lycoris.loconautics.common.turntable.sequence.TurntableInstruction;
import com.lycoris.loconautics.common.turntable.sequence.TurntableInstructionType;
import com.lycoris.loconautics.common.turntable.sequence.TurntableSpeedModifier;
import com.lycoris.loconautics.common.turntable.sequence.TurntableTriggerMode;
import com.simibubi.create.content.contraptions.AssemblyException;
import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;
import com.simibubi.create.content.trains.graph.TrackNodeLocation;
import dan200.computercraft.api.lua.IArguments;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.IPeripheral;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

public class TurntableBearingPeripheral implements IPeripheral {

    private static final Map<TurntableBearingBlockEntity, TurntableBearingPeripheral> CACHE = new WeakHashMap<>();

    private final TurntableBearingBlockEntity bearing;

    private TurntableBearingPeripheral(TurntableBearingBlockEntity bearing) {
        this.bearing = bearing;
    }

    public static TurntableBearingPeripheral of(TurntableBearingBlockEntity bearing) {
        return CACHE.computeIfAbsent(bearing, TurntableBearingPeripheral::new);
    }

    @Override
    public String getType() {
        return "loconautics:turntable_bearing";
    }

    @Override
    public boolean equals(IPeripheral other) {
        return other instanceof TurntableBearingPeripheral p && p.bearing == bearing;
    }

    private TurntableBearingBlockEntity live() throws LuaException {
        if (bearing.isRemoved() || bearing.getLevel() == null) {
            throw new LuaException("the turntable bearing is gone, it was broken or unloaded");
        }
        return bearing;
    }

    private TurntableBearingBlockEntity assembled() throws LuaException {
        TurntableBearingBlockEntity be = live();
        if (!be.isAssembled()) {
            throw new LuaException("the turntable is not assembled, call assemble() first");
        }
        return be;
    }

    private TurntableBearingBlockEntity commandable() throws LuaException {
        TurntableBearingBlockEntity be = assembled();
        if (be.turntableScheduleDriven()) {
            throw new LuaException("the turntable is in schedule mode, the train router owns the plate");
        }
        return be;
    }

    @LuaFunction(mainThread = true)
    public boolean isAssembled() throws LuaException {
        return live().isAssembled();
    }

    @LuaFunction(mainThread = true)
    public boolean assemble() throws LuaException {
        TurntableBearingBlockEntity be = live();
        if (be.isAssembled()) {
            return false;
        }
        be.assemble();
        if (be.getLastAssemblyException() != null) {
            throw new LuaException(assemblyErrorText(be.getLastAssemblyException()));
        }
        return be.isAssembled();
    }

    @LuaFunction(mainThread = true)
    public boolean disassemble() throws LuaException {
        TurntableBearingBlockEntity be = live();
        if (!be.isAssembled()) {
            return false;
        }
        be.disassemble();
        return true;
    }

    @LuaFunction(mainThread = true)
    public String getAssemblyError() throws LuaException {
        AssemblyException exception = live().getLastAssemblyException();
        return exception == null ? null : assemblyErrorText(exception);
    }

    private static String assemblyErrorText(AssemblyException exception) {
        return exception.component.getString();
    }

    @LuaFunction(mainThread = true)
    public double getAngle() throws LuaException {
        return normalize(live().getTargetAngleDegrees());
    }

    @LuaFunction(mainThread = true)
    public void turnBy(double degrees) throws LuaException {
        commandable().turnBy(degrees);
    }

    @LuaFunction(mainThread = true)
    public void turnTo(double degrees) throws LuaException {
        commandable().turnTo(degrees);
    }

    @LuaFunction(mainThread = true)
    public void stop() throws LuaException {
        live().stopTurning();
    }

    @LuaFunction(mainThread = true)
    public boolean isTurning() throws LuaException {
        return live().isTurning();
    }

    @LuaFunction(mainThread = true)
    public boolean isSettled() throws LuaException {
        return live().turntableSettled() && !live().isTurning();
    }

    @LuaFunction(mainThread = true)
    public double getRemainingDegrees() throws LuaException {
        return live().remainingManualDegrees();
    }

    @LuaFunction(mainThread = true)
    public double getSpeed() throws LuaException {
        return live().getPlateSpeed();
    }

    @LuaFunction(mainThread = true)
    public double getCogSpeed() throws LuaException {
        return live().getCogSpeed();
    }

    @LuaFunction(mainThread = true)
    public double getSpeedOverride() throws LuaException {
        return live().getSpeedOverride();
    }

    @LuaFunction(mainThread = true)
    public void setSpeedOverride(double rpm) throws LuaException {
        live().setSpeedOverride((float) rpm);
    }

    @LuaFunction(mainThread = true)
    public void clearSpeedOverride() throws LuaException {
        live().setSpeedOverride(0.0f);
    }

    @LuaFunction(mainThread = true)
    public double getStress() throws LuaException {
        return live().billedStress();
    }

    @LuaFunction(mainThread = true)
    public double getDegreesPerTick() throws LuaException {
        return live().degreesPerTick();
    }

    @LuaFunction(mainThread = true)
    public boolean isPowered() throws LuaException {
        return live().turntablePowered();
    }

    @LuaFunction(mainThread = true)
    public String getFacing() throws LuaException {
        return live().getBlockState().getValue(DirectionalKineticBlock.FACING)
                .getName().toLowerCase(Locale.ROOT);
    }

    @LuaFunction(mainThread = true)
    public Map<String, Integer> getPos() throws LuaException {
        return vec(live().getBlockPos());
    }

    @LuaFunction(mainThread = true)
    public Map<String, Integer> getPlatePos() throws LuaException {
        BlockPos pos = live().getPlatePos();
        return pos == null ? null : vec(pos);
    }

    @LuaFunction(mainThread = true)
    public String getSubLevelId() throws LuaException {
        UUID id = live().getSubLevelID();
        return id == null ? null : id.toString();
    }

    @LuaFunction(mainThread = true)
    public Map<String, Double> getAxisPos() throws LuaException {
        return vec(assembled().turntableAxisWorld());
    }

    @LuaFunction(mainThread = true)
    public Map<String, Double> getAxisNormal() throws LuaException {
        return vec(assembled().turntableAxisNormal());
    }

    @LuaFunction(mainThread = true)
    public List<Map<String, Object>> getInstructions() throws LuaException {
        List<Map<String, Object>> out = new ArrayList<>();
        for (TurntableInstruction instruction : live().getInstructions()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("type", instruction.type.name().toLowerCase(Locale.ROOT));
            entry.put("speed", instruction.modifier.name().toLowerCase(Locale.ROOT));
            entry.put("value", instruction.value);
            out.add(entry);
        }
        return out;
    }

    @LuaFunction(mainThread = true)
    public void setInstructions(IArguments arguments) throws LuaException {
        Map<?, ?> table = arguments.getTable(0);
        List<TurntableInstruction> parsed = new ArrayList<>();
        for (int i = 1; i <= TurntableInstruction.MAX_INSTRUCTIONS; i++) {
            Object raw = table.get((double) i);
            if (raw == null) {
                break;
            }
            if (!(raw instanceof Map<?, ?> entry)) {
                throw new LuaException("instruction " + i + " must be a table");
            }
            parsed.add(parseInstruction(i, entry));
        }
        if (parsed.isEmpty()) {
            throw new LuaException("the instruction list is empty, pass at least one instruction");
        }
        live().applyConfiguration(parsed);
    }

    private static TurntableInstruction parseInstruction(int index, Map<?, ?> entry) throws LuaException {
        TurntableInstructionType type = lookup(TurntableInstructionType.class, entry.get("type"),
                "instruction " + index + " needs a type");
        TurntableSpeedModifier modifier = entry.get("speed") == null
                ? TurntableSpeedModifier.FORWARD
                : lookup(TurntableSpeedModifier.class, entry.get("speed"), "");
        int value = entry.get("value") instanceof Number number
                ? number.intValue()
                : type.defaultValue;
        return new TurntableInstruction(type, modifier, value);
    }

    private static <E extends Enum<E>> E lookup(Class<E> clazz, Object name, String missing) throws LuaException {
        if (!(name instanceof String text)) {
            throw new LuaException(missing);
        }
        for (E value : clazz.getEnumConstants()) {
            if (value.name().equalsIgnoreCase(text)) {
                return value;
            }
        }
        throw new LuaException("\"" + text + "\" is not one of " + names(clazz));
    }

    private static <E extends Enum<E>> String names(Class<E> clazz) {
        StringBuilder builder = new StringBuilder();
        for (E value : clazz.getEnumConstants()) {
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append(value.name().toLowerCase(Locale.ROOT));
        }
        return builder.toString();
    }

    @LuaFunction(mainThread = true)
    public String getTrigger() throws LuaException {
        return live().getTrigger().name().toLowerCase(Locale.ROOT);
    }

    @LuaFunction(mainThread = true)
    public void setTrigger(String mode) throws LuaException {
        TurntableTriggerMode trigger = lookup(TurntableTriggerMode.class, mode, "mode must be a string");
        live().setTrigger(trigger);
    }

    @LuaFunction(mainThread = true)
    public void runSequence() throws LuaException {
        commandable().runInstruction(0);
    }

    @LuaFunction(mainThread = true)
    public void resume() throws LuaException {
        TurntableBearingBlockEntity be = commandable();
        int current = be.getCurrentInstruction();
        if (current < 0) {
            throw new LuaException("no sequence is running, call runSequence() first");
        }
        be.runInstruction(current + 1);
    }

    @LuaFunction(mainThread = true)
    public void cancelSequence() throws LuaException {
        live().stopTurning();
    }

    @LuaFunction(mainThread = true)
    public int getCurrentInstruction() throws LuaException {
        return live().getCurrentInstruction() + 1;
    }

    @LuaFunction(mainThread = true)
    public boolean isRunningSequence() throws LuaException {
        return live().getCurrentInstruction() >= 0;
    }

    @LuaFunction(mainThread = true)
    public void pulse(boolean powered) throws LuaException {
        live().onRedstoneUpdate(powered);
    }

    @LuaFunction(mainThread = true)
    public boolean isScheduleDriven() throws LuaException {
        return live().turntableScheduleDriven();
    }

    @LuaFunction(mainThread = true)
    public boolean isOccupied() throws LuaException {
        return TurntableRegistry.trainOnPlate(live().getSubLevelID());
    }

    @LuaFunction(mainThread = true)
    public boolean isAligned() throws LuaException {
        return live().turntableAligned();
    }

    @LuaFunction(mainThread = true)
    public Map<String, Double> getAlignTarget() throws LuaException {
        TrackNodeLocation target = live().turntableAlignTarget();
        return target == null ? null : vec(target.getLocation());
    }

    @LuaFunction(mainThread = true)
    public void clearAlignment() throws LuaException {
        live().turntableClearAlignment();
    }

    @LuaFunction(mainThread = true)
    public Map<String, Object> getState() throws LuaException {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("assembled", isAssembled());
        out.put("angle", getAngle());
        out.put("turning", isTurning());
        out.put("settled", isSettled());
        out.put("powered", isPowered());
        out.put("speed", getSpeed());
        out.put("cogSpeed", getCogSpeed());
        out.put("speedOverride", getSpeedOverride());
        out.put("stress", getStress());
        out.put("degreesPerTick", getDegreesPerTick());
        out.put("remaining", getRemainingDegrees());
        out.put("trigger", getTrigger());
        out.put("instruction", getCurrentInstruction());
        out.put("scheduleDriven", isScheduleDriven());
        out.put("occupied", isOccupied());
        out.put("aligned", isAligned());
        out.put("error", getAssemblyError());
        return out;
    }

    private static double normalize(double degrees) {
        double wrapped = degrees % 360.0;
        return wrapped < 0.0 ? wrapped + 360.0 : wrapped;
    }

    private static Map<String, Integer> vec(BlockPos pos) {
        Map<String, Integer> out = new HashMap<>();
        out.put("x", pos.getX());
        out.put("y", pos.getY());
        out.put("z", pos.getZ());
        return out;
    }

    private static Map<String, Double> vec(Vec3 vec) {
        Map<String, Double> out = new HashMap<>();
        out.put("x", vec.x);
        out.put("y", vec.y);
        out.put("z", vec.z);
        return out;
    }
}
