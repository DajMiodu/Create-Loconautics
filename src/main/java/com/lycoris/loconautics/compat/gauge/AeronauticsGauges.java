package com.lycoris.loconautics.compat.gauge;

import com.lycoris.loconautics.content.controls.panelgauge.GaugeMetric;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;

// reads Create: Aeronautics through reflection, so there is no compile time dependency: the gauge groups only exist while the mod is loaded, and a name that does not resolve reads as null
public final class AeronauticsGauges {

    private static final String PACKAGE = "dev.eriksonn.aeronautics.";
    private static final String PROPELLER_BEARING =
            PACKAGE + "content.blocks.propeller.bearing.propeller_bearing.PropellerBearingBlockEntity";
    private static final String HOT_AIR_BURNER =
            PACKAGE + "content.blocks.hot_air.hot_air_burner.HotAirBurnerBlockEntity";
    private static final String STEAM_VENT = PACKAGE + "content.blocks.hot_air.steam_vent.SteamVentBlockEntity";
    private static final String AERO_CONFIG = PACKAGE + "config.AeroConfig";

    private static final float DEFAULT_HOT_AIR_BURNER_MAX = 500f;
    private static final float DEFAULT_STEAM_VENT_MAX = 5000f;

    private static final Class<? extends BlockEntity> BEARING = load(PROPELLER_BEARING);
    private static final Class<? extends BlockEntity> BURNER = load(HOT_AIR_BURNER);
    private static final Class<? extends BlockEntity> VENT = load(STEAM_VENT);

    private AeronauticsGauges() {
    }

    @SuppressWarnings("unchecked")
    @Nullable
    private static Class<? extends BlockEntity> load(String name) {
        try {
            Class<?> type = Class.forName(name, false, AeronauticsGauges.class.getClassLoader());
            return BlockEntity.class.isAssignableFrom(type) ? (Class<? extends BlockEntity>) type : null;
        } catch (Throwable missing) {
            return null;
        }
    }

    private static List<Class<? extends BlockEntity>> sourceOf(@Nullable Class<? extends BlockEntity> type) {
        return type == null ? List.of() : List.of(type);
    }

    @Nullable
    private static Double read(BlockEntity be, @Nullable Class<? extends BlockEntity> type, String getter) {
        if (type == null || !type.isInstance(be)) return null;
        try {
            Method method = type.getMethod(getter);
            return method.invoke(be) instanceof Number number ? number.doubleValue() : null;
        } catch (Throwable unresolved) {
            return null;
        }
    }

    private static float configured(String field, float fallback) {
        try {
            Object server = Class.forName(AERO_CONFIG).getMethod("server").invoke(null);
            Field blocksField = server.getClass().getField("blocks");
            Object blocks = blocksField.get(server);
            Object value = blocks.getClass().getField(field).get(blocks);
            return value.getClass().getMethod("get").invoke(value) instanceof Number number
                    ? number.floatValue() : fallback;
        } catch (Throwable unresolved) {
            return fallback;
        }
    }

    public static List<Class<? extends BlockEntity>> bearingSources() {
        return sourceOf(BEARING);
    }

    public static List<Class<? extends BlockEntity>> hotAirSources() {
        return sourceOf(BURNER);
    }

    public static List<Class<? extends BlockEntity>> steamSources() {
        return sourceOf(VENT);
    }

    @Nullable
    public static String bearingRpm(BlockEntity be) {
        Double speed = read(be, BEARING, "getRotationSpeed");
        return speed == null ? null : String.format(Locale.US, "%,.0f RPM", Math.abs(speed));
    }

    @Nullable
    public static Float bearingSpeedFraction(BlockEntity be) {
        Double speed = read(be, BEARING, "getRotationSpeed");
        return speed == null ? null : GaugeMetric.over((float) Math.abs(speed), 256f);
    }

    @Nullable
    public static String bearingThrust(BlockEntity be) {
        Double thrust = read(be, BEARING, "getThrust");
        return thrust == null ? null : String.format(Locale.US, "%,.1f", Math.abs(thrust));
    }

    @Nullable
    public static String bearingAirflow(BlockEntity be) {
        Double airflow = read(be, BEARING, "getAirflow");
        return airflow == null ? null : String.format(Locale.US, "%,.1f", Math.abs(airflow));
    }

    @Nullable
    public static String hotAirOutput(BlockEntity be) {
        Double output = read(be, BURNER, "getGasOutput");
        return output == null ? null : String.format(Locale.US, "%,.0f", output);
    }

    @Nullable
    public static Float hotAirFraction(BlockEntity be) {
        Double output = read(be, BURNER, "getGasOutput");
        return output == null ? null : GaugeMetric.over(output.floatValue(),
                configured("hotAirBurnerMaxHotAir", DEFAULT_HOT_AIR_BURNER_MAX));
    }

    @Nullable
    public static String steamOutput(BlockEntity be) {
        Double output = read(be, VENT, "getGasOutput");
        return output == null ? null : String.format(Locale.US, "%,.0f", output);
    }

    @Nullable
    public static Float steamFraction(BlockEntity be) {
        Double output = read(be, VENT, "getGasOutput");
        return output == null ? null : GaugeMetric.over(output.floatValue(),
                configured("steamVentMaxHotAir", DEFAULT_STEAM_VENT_MAX));
    }
}
