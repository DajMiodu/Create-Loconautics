package com.lycoris.loconautics.client;

import com.lycoris.loconautics.LoconauticsConstants;
import net.createmod.catnip.render.SpriteShiftEntry;
import net.createmod.catnip.render.SpriteShifter;

public class LoconauticsSpriteShifts {

    public static final SpriteShiftEntry STEEL_CABLE_WINCH_COIL = SpriteShifter.get(
            LoconauticsConstants.id("block/steelcable/winch_coil"),
            LoconauticsConstants.id("block/steelcable/winch_coil_scroll")
    );

    public static final SpriteShiftEntry LOCOMOTIVE_GAUGE = SpriteShifter.get(
            LoconauticsConstants.id("block/controls/gauge_atlas"),
            LoconauticsConstants.id("block/controls/gauge_atlas")
    );

    public static final SpriteShiftEntry VALVE = SpriteShifter.get(
            LoconauticsConstants.id("block/controls/valve_atlas"),
            LoconauticsConstants.id("block/controls/valve_atlas")
    );

    public static final SpriteShiftEntry BODY = SpriteShifter.get(
            LoconauticsConstants.id("block/controls/body_atlas"),
            LoconauticsConstants.id("block/controls/body_atlas")
    );

    public static final SpriteShiftEntry SLIM_BODY = SpriteShifter.get(
            LoconauticsConstants.id("block/controls/slim_body_atlas"),
            LoconauticsConstants.id("block/controls/slim_body_atlas")
    );

    public static final SpriteShiftEntry BIG_BODY = SpriteShifter.get(
            LoconauticsConstants.id("block/controls/big_body_atlas"),
            LoconauticsConstants.id("block/controls/big_body_atlas")
    );

    public static final SpriteShiftEntry BIG_BUTTON = SpriteShifter.get(
            LoconauticsConstants.id("block/controls/big_button_atlas"),
            LoconauticsConstants.id("block/controls/big_button_atlas")
    );

    public static final SpriteShiftEntry GAUGE_BUTTON = SpriteShifter.get(
            LoconauticsConstants.id("block/controls/button_atlas"),
            LoconauticsConstants.id("block/controls/button_atlas")
    );

    public static final SpriteShiftEntry GAUGE_LEVER = SpriteShifter.get(
            LoconauticsConstants.id("block/controls/lever_atlas"),
            LoconauticsConstants.id("block/controls/lever_atlas")
    );

    public static final SpriteShiftEntry GAUGE_SWITCH = SpriteShifter.get(
            LoconauticsConstants.id("block/controls/switch_atlas"),
            LoconauticsConstants.id("block/controls/switch_atlas")
    );

    public static void init() {}
}