package com.lycoris.loconautics.content.controls.panel.visual;

import com.lycoris.loconautics.content.controls.panel.PanelGlass;
import com.lycoris.loconautics.client.LoconauticsPartialModels;
import com.lycoris.loconautics.client.LoconauticsSpriteShifts;
import com.lycoris.loconautics.content.controls.panel.PanelBlockEntity;
import com.lycoris.loconautics.content.controls.panel.PanelPart;
import com.lycoris.loconautics.content.controls.panel.PanelSlot;
import com.lycoris.loconautics.content.controls.panel.style.PanelStyles;
import com.lycoris.loconautics.content.controls.panelswitch.SwitchPart;
import com.lycoris.loconautics.content.controls.panelswitch.SwitchPartRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.instance.TransformedInstance;
import org.jetbrains.annotations.Nullable;

public final class SwitchPartVisual extends PanelPartVisual {

    private final int body;
    private final int knobColor;
    private final int style;
    private final int options;

    @Nullable
    private final TransformedInstance plate;
    private final TransformedInstance shell;
    private final TransformedInstance paddle;
    @Nullable
    private final PanelBulbVisual bulb;
    @Nullable
    private final PanelGlassVisual glass;

    private float lastFraction = Float.NaN;

    public SwitchPartVisual(VisualizationContext context, SwitchPart flip) {
        super(context);
        body = flip.body();
        knobColor = flip.knobColor();
        style = flip.style();
        options = flip.shownOptions();
        PanelGlass glassKind = flip.glass();
        glass = glassKind == null ? null : new PanelGlassVisual(this::create, glassKind);

        plate = PanelStyles.SWITCH.isOn(options, PanelStyles.SWITCH_PLATE) ? create(PanelModels.slimPlate()) : null;
        shell = create(PanelModels.slimBody(body));
        paddle = create(ShiftedModels.shifted(LoconauticsPartialModels.SWITCH_PADDLE,
                LoconauticsSpriteShifts.GAUGE_SWITCH,
                SwitchPartRenderer.PADDLE_STRIDE_X * (knobColor % 5),
                SwitchPartRenderer.PADDLE_STRIDE_Y * (knobColor / 5)));
        bulb = PanelStyles.SWITCH.isOn(options, PanelStyles.SWITCH_LIGHT)
                ? new PanelBulbVisual(this::create, LoconauticsPartialModels.SWITCH_BULB) : null;
    }

    @Override
    public boolean matches(PanelPart part) {
        return part instanceof SwitchPart flip && flip.body() == body && flip.knobColor() == knobColor
                && flip.style() == style && flip.shownOptions() == options;
    }

    @Override
    public void update(PanelBlockEntity panel, PanelSlot slot, PoseStack ms, float partialTick,
                       int light, boolean full) {
        if (full) {
            if (plate != null) place(plate, ms, light);
            place(shell, ms, light);
        }
        float fraction = panel.animation(slot).getValue(partialTick);
        if (full || fraction != lastFraction) {
            lastFraction = fraction;
            ms.pushPose();
            ms.translate(SwitchPartRenderer.PIVOT_X, SwitchPartRenderer.PIVOT_Y, SwitchPartRenderer.PIVOT_Z);
            ms.mulPose(Axis.XP.rotationDegrees(SwitchPartRenderer.ROCK_DEGREES * fraction));
            ms.translate(-SwitchPartRenderer.PIVOT_X, -SwitchPartRenderer.PIVOT_Y, -SwitchPartRenderer.PIVOT_Z);
            place(paddle, ms, light);
            ms.popPose();
        }
        if (bulb != null) bulb.update(ms, light, panel.bulbGlow(slot, partialTick), full);
        if (glass != null) glass.update(ms, light, panel.glassAngle(slot), full);
    }
}
