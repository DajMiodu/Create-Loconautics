package com.lycoris.loconautics.content.controls.panelgauge;

import com.lycoris.loconautics.compat.gauge.AeronauticsGauges;
import com.lycoris.loconautics.compat.gauge.DieselGauges;
import com.lycoris.loconautics.compat.gauge.PropulsionGauges;
import com.lycoris.loconautics.config.LoconauticsConfig;
import com.lycoris.loconautics.content.boiler.firebox.FireboxBlockEntity;
import com.lycoris.loconautics.content.combustion.SmallEngineBlockEntity;
import com.lycoris.loconautics.content.combustion.SmallEngineTuning;
import com.mojang.serialization.Codec;
import com.simibubi.create.content.fluids.pipes.valve.FluidValveBlock;
import com.simibubi.create.content.fluids.pipes.valve.FluidValveBlockEntity;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.content.kinetics.gauge.SpeedGaugeBlockEntity;
import com.simibubi.create.content.kinetics.gauge.StressGaugeBlockEntity;
import dev.simulated_team.simulated.content.blocks.altitude_sensor.AltitudeSensorBlockEntity;
import dev.simulated_team.simulated.content.blocks.gimbal_sensor.GimbalSensorBlockEntity;
import dev.simulated_team.simulated.content.blocks.portable_engine.PortableEngineBlockEntity;
import dev.simulated_team.simulated.content.blocks.velocity_sensor.VelocitySensorBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.function.Supplier;

public enum GaugeMetric implements StringRepresentable {

    TEMPERATURE(FireboxBlockEntity.class,
            FireboxBlockEntity::formattedTemperature,
            FireboxBlockEntity::temperatureFraction, ChatFormatting.GOLD, 27),
    PRESSURE(FireboxBlockEntity.class,
            be -> pct(be.getDisplayPressure()),
            FireboxBlockEntity::getDisplayPressure, ChatFormatting.AQUA, 21),
    STEAM_OUTPUT(FireboxBlockEntity.class,
            be -> String.format(Locale.US, "%,.0f SU", be.getDisplaySu()),
            be -> over(be.getDisplaySu(), be.getDisplayMaxSu()), ChatFormatting.AQUA, 15),
    SHAFT_RPM(FireboxBlockEntity.class,
            be -> String.format(Locale.US, "%,.0f RPM", be.getDisplayRpm()),
            be -> over(be.getDisplayRpm(), (float) (double) LoconauticsConfig.TAKEOFF_MAX_RPM.get()),
            ChatFormatting.AQUA, 9),
    BURN_RATE(FireboxBlockEntity.class,
            be -> String.format(Locale.US, "%.2fx", be.getLastBurnRate()),
            be -> over(be.getLastBurnRate(), 4f), ChatFormatting.GOLD, 22),
    FUEL_REMAINING(FireboxBlockEntity.class,
            be -> FireboxBlockEntity.formatTime(be.getDisplayRemaining()),
            be -> over(be.getDisplayRemaining(), 600f), ChatFormatting.WHITE, 32),
    WATER_LEVEL(FireboxBlockEntity.class,
            GaugeMetric::waterLevel,
            be -> be.isDisplayValid() ? over(be.getDisplayWater(), be.getWaterCapacity()) : 0f,
            ChatFormatting.BLUE, 2),
    EFFICIENCY(FireboxBlockEntity.class,
            be -> pct(be.getDisplayEfficiency()),
            FireboxBlockEntity::getDisplayEfficiency, ChatFormatting.GREEN, 17),
    NETWORK_STRESS(FireboxBlockEntity.class,
            be -> pct(be.getLastStressRatio()),
            FireboxBlockEntity::getLastStressRatio, ChatFormatting.AQUA, 23),
    TRAIN_WEIGHT(FireboxBlockEntity.class,
            GaugeMetric::trainWeight,
            be -> over((float) Math.max(0.0, be.getLastTrainMass()), 3000f), ChatFormatting.GOLD, 3),

    SPEEDOMETER_RPM(SpeedGaugeBlockEntity.class,
            be -> String.format(Locale.US, "%,.0f RPM", Math.abs(be.getSpeed())),
            be -> SpeedGaugeBlockEntity.getDialTarget(be.getSpeed()), ChatFormatting.AQUA, 9),

    STRESSOMETER_LOAD_SU(StressGaugeBlockEntity.class,
            be -> String.format(Locale.US, "%,.0f/%,.0f SU",
                    be.getNetworkStress(), be.getNetworkCapacity()),
            be -> over(be.getNetworkStress(), be.getNetworkCapacity()), ChatFormatting.AQUA, 23),
    STRESSOMETER_LOAD(StressGaugeBlockEntity.class,
            be -> pct(over(be.getNetworkStress(), be.getNetworkCapacity())),
            be -> over(be.getNetworkStress(), be.getNetworkCapacity()), ChatFormatting.AQUA, 22),
    STRESSOMETER_SPARE(StressGaugeBlockEntity.class,
            be -> String.format(Locale.US, "%,.0f SU (%s)",
                    Math.max(0f, be.getNetworkCapacity() - be.getNetworkStress()),
                    pct(over(Math.max(0f, be.getNetworkCapacity() - be.getNetworkStress()),
                            be.getNetworkCapacity()))),
            be -> over(Math.max(0f, be.getNetworkCapacity() - be.getNetworkStress()),
                    be.getNetworkCapacity()), ChatFormatting.GREEN, 17),

    SUB_LEVEL_SPEED(VelocitySensorBlockEntity.class,
            be -> speed(be, 1.0, "m/s"), GaugeMetric::speedFraction,
            ChatFormatting.AQUA, GaugeBlockEntity.DEFAULT_BACKGROUND),
    SUB_LEVEL_SPEED_FPS(VelocitySensorBlockEntity.class,
            be -> speed(be, 3.28084, "ft/s"), GaugeMetric::speedFraction,
            ChatFormatting.AQUA, GaugeBlockEntity.DEFAULT_BACKGROUND),
    SUB_LEVEL_SPEED_KMH(VelocitySensorBlockEntity.class,
            be -> speed(be, 3.6, "km/h"), GaugeMetric::speedFraction,
            ChatFormatting.AQUA, GaugeBlockEntity.DEFAULT_BACKGROUND),
    SUB_LEVEL_SPEED_MPH(VelocitySensorBlockEntity.class,
            be -> speed(be, 2.23694, "mph"), GaugeMetric::speedFraction,
            ChatFormatting.AQUA, GaugeBlockEntity.DEFAULT_BACKGROUND),

    ALTITUDE_HEIGHT(AltitudeSensorBlockEntity.class,
            be -> String.format(Locale.US, "%,.1f m", be.getWorldHeight()),
            AltitudeSensorBlockEntity::getNormalHeight, ChatFormatting.AQUA,
            GaugeBlockEntity.DEFAULT_BACKGROUND),
    ALTITUDE_AIR_PRESSURE(AltitudeSensorBlockEntity.class,
            be -> String.format(Locale.US, "%.2f%%", be.getAirPressure() * 100.0),
            be -> (float) be.getAirPressure(), ChatFormatting.WHITE, 21),

    GIMBAL_X_ANGLE(GimbalSensorBlockEntity.class,
            be -> degrees(be.getXAngle()),
            be -> angleFraction(be.getXAngle()), ChatFormatting.RED,
            GaugeBlockEntity.DEFAULT_BACKGROUND),
    GIMBAL_Z_ANGLE(GimbalSensorBlockEntity.class,
            be -> degrees(be.getZAngle()),
            be -> angleFraction(be.getZAngle()), ChatFormatting.BLUE,
            GaugeBlockEntity.DEFAULT_BACKGROUND),

    FLUID_VALVE_STATE(FluidValveBlockEntity.class,
            be -> Component.translatable(valveOpen(be)
                    ? "loconautics.gui.goggles.gauge_valve_open"
                    : "loconautics.gui.goggles.gauge_valve_closed").getString(),
            be -> valveOpen(be) ? 1f : 0f, ChatFormatting.AQUA,
            GaugeBlockEntity.DEFAULT_BACKGROUND),

    ENGINE_RPM(PortableEngineBlockEntity.class,
            be -> String.format(Locale.US, "%,.0f RPM", Math.abs(be.getGeneratedSpeed())),
            be -> over(Math.abs(be.getGeneratedSpeed()), 256f), ChatFormatting.AQUA, 9),
    ENGINE_FUEL(PortableEngineBlockEntity.class,
            GaugeMetric::engineFuel, GaugeMetric::engineFuelFraction, ChatFormatting.WHITE, 32),
    TANK_FLUID(FluidTankBlockEntity.class,
            GaugeMetric::tankFluid, GaugeMetric::tankFraction, ChatFormatting.BLUE, 2),
    TANK_FILL(FluidTankBlockEntity.class,
            be -> pct(tankFraction(be)), GaugeMetric::tankFraction, ChatFormatting.BLUE, 2),

    DIESEL_FUEL(Group.DIESEL_ENGINE, DieselGauges::fuel, DieselGauges::fuelFraction, ChatFormatting.GOLD, 32),
    DIESEL_FILL(Group.DIESEL_ENGINE, DieselGauges::fuelFill, DieselGauges::fuelFraction, ChatFormatting.GOLD, 32),
    DIESEL_RPM(Group.DIESEL_ENGINE, DieselGauges::rpm, DieselGauges::rpmFraction, ChatFormatting.AQUA, 9),
    DIESEL_THROTTLE(Group.DIESEL_ENGINE, DieselGauges::throttle, DieselGauges::throttleFraction,
            ChatFormatting.GREEN, 17),
    DIESEL_BURNS_FOR(Group.DIESEL_ENGINE, DieselGauges::burnsFor, DieselGauges::burnsForFraction,
            ChatFormatting.WHITE, 32),

    THRUSTER_FUEL(Group.THRUSTER, PropulsionGauges::thrusterFuel, PropulsionGauges::thrusterFuelFraction,
            ChatFormatting.GOLD, 32),
    THRUSTER_THROTTLE(Group.THRUSTER, PropulsionGauges::thrusterThrottle, PropulsionGauges::thrusterPower,
            ChatFormatting.GREEN, 17),
    THRUSTER_THRUST(Group.THRUSTER, PropulsionGauges::thrusterThrust, PropulsionGauges::thrusterPower,
            ChatFormatting.AQUA, 9),
    THRUSTER_ION_ENERGY(Group.THRUSTER, PropulsionGauges::ionEnergy, PropulsionGauges::ionEnergyFraction,
            ChatFormatting.AQUA, 23),

    BURNER_FUEL(Group.LIQUID_BURNER, PropulsionGauges::burnerFuel, PropulsionGauges::burnerFuelFraction,
            ChatFormatting.GOLD, 32),
    BURNER_TIME(Group.LIQUID_BURNER, PropulsionGauges::burnerTime, PropulsionGauges::burnerTimeFraction,
            ChatFormatting.WHITE, 32),
    BURNER_HEAT(Group.LIQUID_BURNER, PropulsionGauges::burnerHeat, PropulsionGauges::burnerHeatFraction,
            ChatFormatting.GOLD, 22),

    PROPELLER_RPM(Group.PROPELLER_BEARING, AeronauticsGauges::bearingRpm, AeronauticsGauges::bearingSpeedFraction,
            ChatFormatting.AQUA, 9),
    PROPELLER_THRUST(Group.PROPELLER_BEARING, AeronauticsGauges::bearingThrust,
            AeronauticsGauges::bearingSpeedFraction, ChatFormatting.AQUA, 9),
    PROPELLER_AIRFLOW(Group.PROPELLER_BEARING, AeronauticsGauges::bearingAirflow,
            AeronauticsGauges::bearingSpeedFraction, ChatFormatting.WHITE, 21),

    HOT_AIR_OUTPUT(Group.HOT_AIR_BURNER, AeronauticsGauges::hotAirOutput, AeronauticsGauges::hotAirFraction,
            ChatFormatting.GOLD, 22),
    STEAM_VENT_OUTPUT(Group.STEAM_VENT, AeronauticsGauges::steamOutput, AeronauticsGauges::steamFraction,
            ChatFormatting.WHITE, 21),

    SMALL_ENGINE_RPM(SmallEngineBlockEntity.class,
            be -> String.format(Locale.US, "%,.0f RPM", be.getOutputRpm()),
            be -> over(be.getOutputRpm(), SmallEngineTuning.TOP_RPM), ChatFormatting.AQUA, 9),
    SMALL_ENGINE_OUTPUT(SmallEngineBlockEntity.class,
            be -> String.format(Locale.US, "%,.0f SU", be.getOutputSu()),
            be -> over(be.getOutputSu(), SmallEngineTuning.SU_PER_ENGINE * SmallEngineTuning.MAX_TANDEM),
            ChatFormatting.AQUA, 15),
    SMALL_ENGINE_THROTTLE(SmallEngineBlockEntity.class,
            be -> SmallEngineTuning.isOff(be.getControlSignal()) ? "0%"
                    : pct(SmallEngineTuning.speedFraction(be.getControlSignal())),
            be -> SmallEngineTuning.speedFraction(be.getControlSignal()), ChatFormatting.GREEN, 17),
    SMALL_ENGINE_FUEL(SmallEngineBlockEntity.class,
            be -> String.format(Locale.US, "%,d / %,d mB", be.getFuelAmount(), be.getFuelCapacity()),
            be -> over(be.getFuelAmount(), be.getFuelCapacity()), ChatFormatting.GOLD, 32),
    SMALL_ENGINE_FILL(SmallEngineBlockEntity.class,
            be -> pct(over(be.getFuelAmount(), be.getFuelCapacity())),
            be -> over(be.getFuelAmount(), be.getFuelCapacity()), ChatFormatting.GOLD, 32),
    SMALL_ENGINE_BURNS_FOR(SmallEngineBlockEntity.class,
            GaugeMetric::smallEngineBurnsFor,
            be -> be.getBurnsForSeconds() < 0 ? 0f : over(be.getBurnsForSeconds(), 600f),
            ChatFormatting.WHITE, 32);

    public enum Group {
        FIREBOX("loconautics", "Loconautics", "Firebox", FireboxBlockEntity.class),
        SPEEDOMETER("create", "Create", "Speedometer", SpeedGaugeBlockEntity.class),
        STRESSOMETER("create", "Create", "Stressometer", StressGaugeBlockEntity.class),
        VELOCITY("simulated", "Create: Simulated", "Velocity Sensor", VelocitySensorBlockEntity.class),
        ALTITUDE("simulated", "Create: Simulated", "Altitude Sensor", AltitudeSensorBlockEntity.class),
        GIMBAL("simulated", "Create: Simulated", "Gimbal Sensor", GimbalSensorBlockEntity.class),
        SMALL_ENGINE("loconautics", "Loconautics", "Small Combustion Engine", SmallEngineBlockEntity.class),
        FLUID_VALVE("create", "Create", "Fluid Valve", FluidValveBlockEntity.class),
        PORTABLE_ENGINE("simulated", "Create: Simulated", "Portable Engine", PortableEngineBlockEntity.class),
        FLUID_TANK("create", "Create", "Fluid Tank", FluidTankBlockEntity.class),
        DIESEL_ENGINE("createdieselgenerators", "Create: Diesel Generators", "Diesel Engines",
                DieselGauges::sources),
        THRUSTER("createpropulsion", "Create: Propulsion", "Thrusters", PropulsionGauges::thrusterSources),
        LIQUID_BURNER("createpropulsion", "Create: Propulsion", "Liquid Burner", PropulsionGauges::burnerSources),
        PROPELLER_BEARING("aeronautics", "Create: Aeronautics", "Propeller Bearing",
                AeronauticsGauges::bearingSources),
        HOT_AIR_BURNER("aeronautics", "Create: Aeronautics", "Hot Air Burner", AeronauticsGauges::hotAirSources),
        STEAM_VENT("aeronautics", "Create: Aeronautics", "Steam Vent", AeronauticsGauges::steamSources);

        private final String modId;
        private final String modName;
        private final String blockName;
        private final Supplier<List<Class<? extends BlockEntity>>> sourceList;
        private List<Class<? extends BlockEntity>> sources;

        Group(String modId, String modName, String blockName, Class<? extends BlockEntity> source) {
            this(modId, modName, blockName, () -> List.of(source));
        }

        Group(String modId, String modName, String blockName,
              Supplier<List<Class<? extends BlockEntity>>> sourceList) {
            this.modId = modId;
            this.modName = modName;
            this.blockName = blockName;
            this.sourceList = sourceList;
        }

        public String modId() {
            return modId;
        }

        public String modName() {
            return modName;
        }

        public String blockName() {
            return blockName;
        }

        private List<Class<? extends BlockEntity>> sources() {
            if (sources == null) {
                sources = ModList.get().isLoaded(modId) ? sourceList.get() : List.of();
            }
            return sources;
        }

        @Nullable
        public static Group of(@Nullable BlockEntity source) {
            if (source == null) return null;
            for (Group group : values()) {
                for (Class<? extends BlockEntity> type : group.sources()) {
                    if (type.isInstance(source)) return group;
                }
            }
            return null;
        }

        static Group declaring(Class<? extends BlockEntity> source) {
            for (Group group : values()) {
                if (group.sources().contains(source)) return group;
            }
            throw new IllegalArgumentException("no panelgauge group declares " + source);
        }
    }

    public static final Codec<GaugeMetric> CODEC = StringRepresentable.fromValues(GaugeMetric::values);

    public static final StreamCodec<io.netty.buffer.ByteBuf, GaugeMetric> STREAM_CODEC =
            ByteBufCodecs.idMapper(GaugeMetric::byOrdinal, GaugeMetric::ordinal);

    public static GaugeMetric byOrdinal(int ordinal) {
        GaugeMetric[] all = values();
        return ordinal < 0 || ordinal >= all.length ? TEMPERATURE : all[ordinal];
    }

    private final Group group;
    private final Function<BlockEntity, String> reader;
    private final Function<BlockEntity, Float> normalizer;
    private final ChatFormatting color;
    private final int defaultBackground;

    <S extends BlockEntity> GaugeMetric(Class<S> source, Function<S, String> reader,
                                        Function<S, Float> normalizer,
                                        ChatFormatting color, int defaultBackground) {
        this.group = Group.declaring(source);
        this.reader = from(source, reader);
        this.normalizer = from(source, normalizer);
        this.color = color;
        this.defaultBackground = defaultBackground;
    }

    GaugeMetric(Group group, Function<BlockEntity, String> reader, Function<BlockEntity, Float> normalizer,
                ChatFormatting color, int defaultBackground) {
        this.group = group;
        this.reader = reader;
        this.normalizer = normalizer;
        this.color = color;
        this.defaultBackground = defaultBackground;
    }

    private static <S extends BlockEntity, T> Function<BlockEntity, T> from(Class<S> source,
                                                                            Function<S, T> reader) {
        return be -> source.isInstance(be) ? reader.apply(source.cast(be)) : null;
    }

    private static boolean valveOpen(FluidValveBlockEntity be) {
        BlockState state = be.getBlockState();
        return state.hasProperty(FluidValveBlock.ENABLED) && state.getValue(FluidValveBlock.ENABLED);
    }

    private static String smallEngineBurnsFor(SmallEngineBlockEntity be) {
        int seconds = be.getBurnsForSeconds();
        if (seconds < 0) return Component.translatable("loconautics.gui.goggles.gauge_small_engine_idle").getString();
        return FireboxBlockEntity.formatTime(seconds);
    }

    public static String formatTime(int sec) {
        return FireboxBlockEntity.formatTime(sec);
    }

    private static String engineFuel(PortableEngineBlockEntity be) {
        if (be.isCurrentFuelInfinite()) return "∞";
        return FireboxBlockEntity.formatTime(be.getCurrentBurnTime() / 20);
    }

    private static float engineFuelFraction(PortableEngineBlockEntity be) {
        if (be.isCurrentFuelInfinite()) return 1f;
        return over(be.getCurrentBurnTime(), be.getTotalBurnTime());
    }

    private static String tankFluid(FluidTankBlockEntity be) {
        FluidTankBlockEntity controller = be.getControllerBE();
        if (controller == null) return null;
        return String.format(Locale.US, "%,d / %,d mB",
                controller.getTankInventory().getFluidAmount(),
                controller.getTankInventory().getCapacity());
    }

    private static float tankFraction(FluidTankBlockEntity be) {
        FluidTankBlockEntity controller = be.getControllerBE();
        if (controller == null) return 0f;
        return over(controller.getTankInventory().getFluidAmount(),
                controller.getTankInventory().getCapacity());
    }

    private static String speed(VelocitySensorBlockEntity be, double factor, String unit) {
        return String.format(Locale.US, "%.1f %s", Math.abs(be.getAdjustedVelocity()) * factor, unit);
    }

    private static float speedFraction(VelocitySensorBlockEntity be) {
        return over((float) Math.abs(be.getAdjustedVelocity()), be.getMaxSpeed().getValue());
    }

    private static String degrees(double radians) {
        return String.format(Locale.US, "%.1f°", Math.toDegrees(radians));
    }

    private static float angleFraction(double radians) {
        return (float) ((Math.toDegrees(radians) + 90.0) / 180.0);
    }

    public Group group() {
        return group;
    }

    public boolean accepts(BlockEntity source) {
        return source != null && reader.apply(source) != null;
    }

    public static GaugeMetric[] valuesOf(Group group) {
        List<GaugeMetric> list = new ArrayList<>();
        for (GaugeMetric metric : values()) {
            if (metric.group == group) list.add(metric);
        }
        return list.toArray(new GaugeMetric[0]);
    }

    public static GaugeMetric defaultFor(Group group) {
        return valuesOf(group)[0];
    }

    public int defaultBackground() {
        return defaultBackground;
    }

    public float normalized(BlockEntity source) {
        if (source == null) return 0f;
        Float value = normalizer.apply(source);
        return value == null || Float.isNaN(value) ? 0f : Math.max(0f, Math.min(1f, value));
    }

    public static float over(float value, float fullScale) {
        return fullScale <= 0f ? 0f : value / fullScale;
    }

    public String read(BlockEntity source) {
        if (source == null) return "--";
        String value = reader.apply(source);
        return value == null ? "--" : value;
    }

    public ChatFormatting color() {
        return color;
    }

    public ChatFormatting colorFor(BlockEntity source) {
        return color;
    }

    public String langKey() {
        return "gui.goggles.gauge_" + getSerializedName();
    }

    private static String waterLevel(FireboxBlockEntity be) {
        if (!be.isDisplayValid()) {
            return Component.translatable("loconautics.gui.goggles.gauge_boiler_missing").getString();
        }
        return String.format(Locale.US, "%,d / %,d mB", be.getDisplayWater(), be.getWaterCapacity());
    }

    public boolean faulted(BlockEntity source) {
        return this == WATER_LEVEL && source instanceof FireboxBlockEntity be && !be.isDisplayValid();
    }

    private static String trainWeight(FireboxBlockEntity be) {
        if (!be.onATrain()) {
            return Component.translatable("loconautics.gui.goggles.gauge_not_on_train").getString();
        }
        return String.format(Locale.US, "%,.0f", be.getLastTrainMass());
    }

    public static GaugeMetric byName(String name) {
        for (GaugeMetric metric : values()) {
            if (metric.getSerializedName().equals(name)) return metric;
        }
        return TEMPERATURE;
    }

    public static String pct(float fraction) {
        return (int) (fraction * 100f) + "%";
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
