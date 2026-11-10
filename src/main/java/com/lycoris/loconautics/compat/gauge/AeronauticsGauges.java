package com.lycoris.loconautics.compat.gauge;

import com.lycoris.loconautics.content.controls.panelgauge.GaugeMetric;
import dev.eriksonn.aeronautics.config.AeroConfig;
import dev.eriksonn.aeronautics.content.blocks.hot_air.hot_air_burner.HotAirBurnerBlockEntity;
import dev.eriksonn.aeronautics.content.blocks.hot_air.steam_vent.SteamVentBlockEntity;
import dev.eriksonn.aeronautics.content.blocks.propeller.bearing.propeller_bearing.PropellerBearingBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

public final class AeronauticsGauges {

    private AeronauticsGauges() {
    }

    public static List<Class<? extends BlockEntity>> bearingSources() {
        return List.of(PropellerBearingBlockEntity.class);
    }

    public static List<Class<? extends BlockEntity>> hotAirSources() {
        return List.of(HotAirBurnerBlockEntity.class);
    }

    public static List<Class<? extends BlockEntity>> steamSources() {
        return List.of(SteamVentBlockEntity.class);
    }

    @Nullable
    public static String bearingRpm(BlockEntity be) {
        if (!(be instanceof PropellerBearingBlockEntity bearing)) return null;
        return String.format(Locale.US, "%,.0f RPM", Math.abs(bearing.getRotationSpeed()));
    }

    @Nullable
    public static Float bearingSpeedFraction(BlockEntity be) {
        if (!(be instanceof PropellerBearingBlockEntity bearing)) return null;
        return GaugeMetric.over(Math.abs(bearing.getRotationSpeed()), 256f);
    }

    @Nullable
    public static String bearingThrust(BlockEntity be) {
        if (!(be instanceof PropellerBearingBlockEntity bearing)) return null;
        return String.format(Locale.US, "%,.1f", Math.abs(bearing.getThrust()));
    }

    @Nullable
    public static String bearingAirflow(BlockEntity be) {
        if (!(be instanceof PropellerBearingBlockEntity bearing)) return null;
        return String.format(Locale.US, "%,.1f", Math.abs(bearing.getAirflow()));
    }

    @Nullable
    public static String hotAirOutput(BlockEntity be) {
        if (!(be instanceof HotAirBurnerBlockEntity burner)) return null;
        return String.format(Locale.US, "%,.0f", burner.getGasOutput());
    }

    @Nullable
    public static Float hotAirFraction(BlockEntity be) {
        if (!(be instanceof HotAirBurnerBlockEntity burner)) return null;
        return GaugeMetric.over((float) burner.getGasOutput(),
                AeroConfig.server().blocks.hotAirBurnerMaxHotAir.get());
    }

    @Nullable
    public static String steamOutput(BlockEntity be) {
        if (!(be instanceof SteamVentBlockEntity vent)) return null;
        return String.format(Locale.US, "%,.0f", vent.getGasOutput());
    }

    @Nullable
    public static Float steamFraction(BlockEntity be) {
        if (!(be instanceof SteamVentBlockEntity vent)) return null;
        return GaugeMetric.over((float) vent.getGasOutput(),
                AeroConfig.server().blocks.steamVentMaxHotAir.get());
    }
}
