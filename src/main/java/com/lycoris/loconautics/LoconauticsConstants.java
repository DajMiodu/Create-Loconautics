package com.lycoris.loconautics;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

public final class LoconauticsConstants {

    private LoconauticsConstants() {
    }

    public static final String MOD_ID = "loconautics";

    public static final Logger LOGGER = LogUtils.getLogger();

    public static final int NETWORK_VERSION = 80;

    public static final String CARRIAGE_NAME_INFIX = " Carriage ";

    public static final int MANEUVER_DEAD_END_RETRY_TICKS = 100;

    public static final int MANEUVER_DEFAULT_PERCENT = 10;

    public static final int MANEUVER_MAX_PERCENT = 75;

    public static final double MANEUVER_REVERSAL_MARGIN = 2.0;

    public static final double MANEUVER_JUNCTION_CLEAR = 1.0;

    public static final int MANEUVER_REVERSAL_MAX_TRIES = 128;

    public static final double MANEUVER_BERTH_GAP = 6.0;

    public static final int INDUSTRIAL_TANK_MAX_WIDTH = 5;

    public static final double RAIL_HEAD_HEIGHT = 5.0 / 16.0;

    public static final double TRACK_COLLISION_HEIGHT = 2.0 / 16.0;

    public static final ResourceLocation RAILWAYS_MONORAIL_MATERIAL =
            ResourceLocation.fromNamespaceAndPath("railways", "monorail");

    public static final double MONORAIL_COLLISION_HEIGHT = 15.0 / 16.0;

    public static final double MONORAIL_PAD_FLOOR = MONORAIL_COLLISION_HEIGHT + 1.0 / 32.0;

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
