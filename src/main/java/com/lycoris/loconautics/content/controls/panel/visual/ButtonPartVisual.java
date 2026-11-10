package com.lycoris.loconautics.content.controls.panel.visual;

import com.lycoris.loconautics.content.controls.panel.PanelGlass;
import com.lycoris.loconautics.client.LoconauticsPartialModels;
import com.lycoris.loconautics.content.controls.panel.PanelBlockEntity;
import com.lycoris.loconautics.content.controls.panel.PanelPart;
import com.lycoris.loconautics.content.controls.panel.PanelSlot;
import com.lycoris.loconautics.content.controls.panel.style.PanelStyles;
import com.lycoris.loconautics.content.controls.panelbutton.ButtonPart;
import com.lycoris.loconautics.content.controls.panelbutton.ButtonPartRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.instance.TransformedInstance;
import org.jetbrains.annotations.Nullable;

public final class ButtonPartVisual extends PanelPartVisual {

    private final int body;
    private final int capColor;
    private final int style;
    private final int options;

    @Nullable
    private final TransformedInstance plate;
    private final TransformedInstance shell;
    private final TransformedInstance cap;
    @Nullable
    private final PanelBulbVisual bulb;
    @Nullable
    private final PanelGlassVisual glass;

    private float lastPressed = Float.NaN;

    public ButtonPartVisual(VisualizationContext context, ButtonPart button) {
        super(context);
        body = button.body();
        capColor = button.capColor();
        style = button.style();
        options = button.shownOptions();
        PanelGlass glassKind = button.glass();
        glass = glassKind == null ? null : new PanelGlassVisual(this::create, glassKind);

        plate = PanelStyles.BUTTON.isOn(options, PanelStyles.BUTTON_PLATE) ? create(PanelModels.plate()) : null;
        ButtonPartRenderer.CapStyle capStyle = ButtonPartRenderer.capStyle(style);
        shell = create(capStyle.big() ? PanelModels.bigBody(body) : PanelModels.body(body));
        cap = create(ShiftedModels.shifted(capStyle.cap(), capStyle.shift(),
                capStyle.strideX() * (capColor % 5), capStyle.strideY() * (capColor / 5)));
        bulb = PanelStyles.BUTTON.isOn(options, PanelStyles.BUTTON_LIGHT)
                ? new PanelBulbVisual(this::create, LoconauticsPartialModels.GAUGE_BULB) : null;
    }

    @Override
    public boolean matches(PanelPart part) {
        return part instanceof ButtonPart button && button.body() == body && button.capColor() == capColor
                && button.style() == style && button.shownOptions() == options;
    }

    @Override
    public void update(PanelBlockEntity panel, PanelSlot slot, PoseStack ms, float partialTick,
                       int light, boolean full) {
        if (full) {
            if (plate != null) place(plate, ms, light);
            place(shell, ms, light);
        }
        if (bulb != null) bulb.update(ms, light, panel.bulbGlow(slot, partialTick), full);
        if (glass != null) glass.update(ms, light, panel.glassAngle(slot), full);
        float pressed = panel.animation(slot).getValue(partialTick);
        if (!full && pressed == lastPressed) return;
        lastPressed = pressed;

        ms.pushPose();
        ms.translate(0, -pressed * ButtonPartRenderer.TRAVEL, 0);
        place(cap, ms, light);
        ms.popPose();
    }
}
