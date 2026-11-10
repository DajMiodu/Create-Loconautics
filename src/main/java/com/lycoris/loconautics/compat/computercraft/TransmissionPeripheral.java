package com.lycoris.loconautics.compat.computercraft;

import com.lycoris.loconautics.content.schedule.LocomotiveHardware;
import com.lycoris.loconautics.content.transmission.TransmissionBlockEntity;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.WeakHashMap;

public class TransmissionPeripheral implements IPeripheral {

    private static final Map<TransmissionBlockEntity, TransmissionPeripheral> CACHE = new WeakHashMap<>();

    private final TransmissionBlockEntity transmission;

    private TransmissionPeripheral(TransmissionBlockEntity transmission) {
        this.transmission = transmission;
    }

    public static TransmissionPeripheral of(TransmissionBlockEntity transmission) {
        return CACHE.computeIfAbsent(transmission, TransmissionPeripheral::new);
    }

    @Override
    public String getType() {
        return "loconautics:transmission";
    }

    @Override
    public boolean equals(IPeripheral other) {
        return other instanceof TransmissionPeripheral p && p.transmission == transmission;
    }

    @Override
    public void attach(IComputerAccess computer) {
    }

    @Override
    public void detach(IComputerAccess computer) {
    }

    @LuaFunction(mainThread = true)
    public final int getPower() {
        return transmission.getPower();
    }

    @LuaFunction(mainThread = true)
    public final double getTargetRpm() {
        return transmission.getTargetRpm();
    }

    @LuaFunction(mainThread = true)
    public final double getInputRpm() {
        return transmission.getSpeed();
    }

    @LuaFunction(mainThread = true)
    public final double getOutputRpm() {
        if (!transmission.hasSource()) {
            return 0.0;
        }
        Direction output = transmission.getSourceFacing().getOpposite();
        return transmission.getSpeed() * transmission.getRotationSpeedModifier(output);
    }

    @LuaFunction(mainThread = true)
    public final boolean isReversed() {
        return transmission.isReversed();
    }

    @LuaFunction(mainThread = true)
    public final Map<String, String> getSpeedFrequency() {
        return frequency(transmission.getSpeedFreqFirst(), transmission.getSpeedFreqSecond());
    }

    @LuaFunction(mainThread = true)
    public final Map<String, String> getDirectionFrequency() {
        return frequency(transmission.getDirFreqFirst(), transmission.getDirFreqSecond());
    }

    @LuaFunction(mainThread = true)
    public final boolean isComputerControlled() {
        return transmission.isComputerDriven();
    }

    @LuaFunction(mainThread = true)
    public final boolean isConnected() throws LuaException {
        return connection().ok();
    }

    @LuaFunction(mainThread = true)
    public final Map<String, Object> getConnection() throws LuaException {
        return CcDrivetrain.report(connection());
    }

    private LocomotiveHardware.Drivetrain connection() throws LuaException {
        return CcDrivetrain.check(transmission.getLevel(), transmission.getBlockPos());
    }

    private static Map<String, String> frequency(ItemStack first, ItemStack second) {
        return Map.of("first", idOf(first), "second", idOf(second));
    }

    private static String idOf(ItemStack stack) {
        return stack.isEmpty() ? "" : BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }
}
