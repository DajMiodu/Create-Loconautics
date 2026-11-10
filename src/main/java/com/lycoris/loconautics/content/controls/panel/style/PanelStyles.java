package com.lycoris.loconautics.content.controls.panel.style;

import com.lycoris.loconautics.content.controls.panel.style.PanelStyle.ModelKit;

public final class PanelStyles {

    public static final PanelStyleSet LEVER = new PanelStyleSet("lever");

    public static final PanelStyleSet SWITCH = new PanelStyleSet("switch");

    public static final PanelStyleSet GAUGE = new PanelStyleSet("gauge");

    public static final PanelStyleSet BUTTON = new PanelStyleSet("button");

    public static final int GAUGE_PLATE;

    public static final int GAUGE_LIGHT;

    public static final int BUTTON_PLATE;

    public static final int BUTTON_LIGHT;

    public static final int BUTTON_GLASS;

    public static final int LEVER_PLATE;

    public static final int LEVER_LIGHT;

    public static final int SWITCH_PLATE;

    public static final int SWITCH_LIGHT;

    public static final int SWITCH_GLASS;

    static {
        // append only, saved panels store the index, options are bit positions
        LEVER.register("default", null);
        LEVER.register("ball", new ModelKit("ball", 5, 3, true));
        LEVER_PLATE = LEVER.registerOption("plate", true);
        LEVER_LIGHT = LEVER.registerOption("light", false);

        SWITCH.register("default", null);
        SWITCH_PLATE = SWITCH.registerOption("plate", true);
        SWITCH_LIGHT = SWITCH.registerOption("light", false);
        SWITCH_GLASS = SWITCH.registerOption("glass", false);

        GAUGE.register("default", null);
        GAUGE_PLATE = GAUGE.registerOption("plate", true);
        GAUGE_LIGHT = GAUGE.registerOption("light", false);

        BUTTON.register("default", null);
        BUTTON.register("big", new ModelKit("big", 12, 6, false), false);
        BUTTON_PLATE = BUTTON.registerOption("plate", true);
        BUTTON_LIGHT = BUTTON.registerOption("light", false);
        BUTTON_GLASS = BUTTON.registerOption("glass", false);
    }

    private PanelStyles() {}
}
