package com.lycoris.loconautics.compat.gauge;

import com.lycoris.loconautics.compat.Reflected;
import com.lycoris.loconautics.content.controls.panelgauge.GaugeMetric;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.properties.Property;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

// Create: Propulsion, read by reflection so the mod is not needed to build or run
public final class PropulsionGauges {

    private static final String PACKAGE = "dev.propulsionteam.propulsionsimulated.";
    private static final Class<?> CONFIG = Reflected.classOf(PACKAGE + "PropulsionConfig");
    private static final Class<? extends BlockEntity> THRUSTER =
            Reflected.blockEntity(PACKAGE + "content.thruster.AbstractThrusterBlockEntity");
    private static final Class<? extends BlockEntity> ION =
            Reflected.blockEntity(PACKAGE + "content.thruster.ion_thruster.IonThrusterBlockEntity");
    private static final Class<? extends BlockEntity> BURNER =
            Reflected.blockEntity(PACKAGE + "content.heat.burners.liquid.LiquidBurnerBlockEntity");
    private static final Property<?> HEAT = heatProperty();

    private PropulsionGauges() {
    }

    @Nullable
    private static Property<?> heatProperty() {
        Class<?> block = Reflected.classOf(PACKAGE + "content.heat.burners.AbstractBurnerBlock");
        try {
            return block != null && block.getField("HEAT").get(null) instanceof Property<?> property ? property : null;
        } catch (Throwable missing) {
            return null;
        }
    }

    public static List<Class<? extends BlockEntity>> thrusterSources() {
        return Reflected.present(THRUSTER);
    }

    public static List<Class<? extends BlockEntity>> burnerSources() {
        return Reflected.present(BURNER);
    }

    @Nullable
    private static Integer integer(BlockEntity be, Class<?> type, String getter) {
        Double value = Reflected.is(type, be) ? Reflected.number(be, getter) : null;
        return value == null ? null : (int) (double) value;
    }

    @Nullable
    public static String thrusterFuel(BlockEntity be) {
        Integer amount = integer(be, THRUSTER, "getFuelAmountMb");
        Integer capacity = integer(be, THRUSTER, "getFuelCapacityMb");
        if (amount == null || capacity == null || capacity <= 0) return null;
        return String.format(Locale.US, "%,d / %,d mB", amount, capacity);
    }

    @Nullable
    public static Float thrusterFuelFraction(BlockEntity be) {
        Integer amount = integer(be, THRUSTER, "getFuelAmountMb");
        Integer capacity = integer(be, THRUSTER, "getFuelCapacityMb");
        return amount == null || capacity == null ? null : GaugeMetric.over(amount, capacity);
    }

    @Nullable
    public static String thrusterThrottle(BlockEntity be) {
        Float power = thrusterPower(be);
        return power == null ? null : GaugeMetric.pct(power);
    }

    @Nullable
    public static Float thrusterPower(BlockEntity be) {
        Double power = Reflected.is(THRUSTER, be) ? Reflected.number(be, "getPower") : null;
        return power == null ? null : (float) (double) power;
    }

    @Nullable
    public static String thrusterThrust(BlockEntity be) {
        Double thrust = Reflected.is(THRUSTER, be) ? Reflected.number(be, "getCurrentThrust") : null;
        Object perKn = Reflected.callStatic(CONFIG, "getThrustUnitsPerKnOrDefault");
        if (thrust == null || !(perKn instanceof Number units)) return null;
        return String.format(Locale.US, "%,.2f pN", thrust / units.doubleValue());
    }

    @Nullable
    public static String ionEnergy(BlockEntity be) {
        Integer stored = integer(be, ION, "getEnergyStoredFe");
        Integer capacity = integer(be, ION, "getEnergyCapacity");
        if (stored == null || capacity == null) return null;
        return String.format(Locale.US, "%,d / %,d FE", stored, capacity);
    }

    @Nullable
    public static Float ionEnergyFraction(BlockEntity be) {
        Integer stored = integer(be, ION, "getEnergyStoredFe");
        Integer capacity = integer(be, ION, "getEnergyCapacity");
        return stored == null || capacity == null ? null : GaugeMetric.over(stored, capacity);
    }

    @Nullable
    private static IFluidHandler burnerTank(BlockEntity be) {
        return Reflected.is(BURNER, be) && Reflected.call(be, "getPrimaryFluidHandler") instanceof IFluidHandler tank
                ? tank : null;
    }

    @Nullable
    public static String burnerFuel(BlockEntity be) {
        IFluidHandler tank = burnerTank(be);
        if (tank == null) return null;
        return String.format(Locale.US, "%,d / %,d mB", tank.getFluidInTank(0).getAmount(), tank.getTankCapacity(0));
    }

    @Nullable
    public static Float burnerFuelFraction(BlockEntity be) {
        IFluidHandler tank = burnerTank(be);
        return tank == null ? null : GaugeMetric.over(tank.getFluidInTank(0).getAmount(), tank.getTankCapacity(0));
    }

    @Nullable
    public static String burnerTime(BlockEntity be) {
        Integer burn = integer(be, BURNER, "getBurnTime");
        return burn == null ? null : GaugeMetric.formatTime(burn / 20);
    }

    @Nullable
    public static Float burnerTimeFraction(BlockEntity be) {
        Integer burn = integer(be, BURNER, "getBurnTime");
        return burn == null ? null : GaugeMetric.over(burn, 1200f);
    }

    @Nullable
    private static BlazeBurnerBlock.HeatLevel heat(BlockEntity be) {
        if (!Reflected.is(BURNER, be) || HEAT == null) return null;
        return be.getBlockState().getValue(HEAT) instanceof BlazeBurnerBlock.HeatLevel level ? level : null;
    }

    @Nullable
    public static String burnerHeat(BlockEntity be) {
        BlazeBurnerBlock.HeatLevel level = heat(be);
        if (level == null) return null;
        String name = level.name().toLowerCase(Locale.ROOT);
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    @Nullable
    public static Float burnerHeatFraction(BlockEntity be) {
        BlazeBurnerBlock.HeatLevel level = heat(be);
        return level == null ? null : level.ordinal() / (float) (BlazeBurnerBlock.HeatLevel.values().length - 1);
    }
}
