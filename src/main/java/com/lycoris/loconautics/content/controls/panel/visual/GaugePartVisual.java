package com.lycoris.loconautics.content.controls.panel.visual;

import com.lycoris.loconautics.client.LoconauticsPartialModels;
import com.lycoris.loconautics.client.LoconauticsSpriteShifts;
import com.lycoris.loconautics.content.controls.panel.PanelBlockEntity;
import com.lycoris.loconautics.content.controls.panel.PanelPart;
import com.lycoris.loconautics.content.controls.panel.PanelSlot;
import com.lycoris.loconautics.content.controls.panelgauge.GaugeBlockEntity.Gauge;
import com.lycoris.loconautics.content.controls.panel.style.PanelStyles;
import com.lycoris.loconautics.content.controls.panelgauge.GaugeNeedle;
import com.lycoris.loconautics.content.controls.panelgauge.GaugePartRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.instance.TransformedInstance;
import org.jetbrains.annotations.Nullable;

public final class GaugePartVisual extends PanelPartVisual {

    private static final int FACE_STRIDE = 8;

    private final int background;
    private final int front;
    private final int needleColor;
    private final int options;
    private final GaugeNeedle style;

    @Nullable
    private final TransformedInstance plate;
    private final TransformedInstance back;
    private final TransformedInstance face;
    private final TransformedInstance body;
    private final TransformedInstance needle;
    @Nullable
    private final PanelBulbVisual bulb;

    private float lastShown = Float.NaN;
    private float lastQuiver = Float.NaN;

    public GaugePartVisual(VisualizationContext context, Gauge gauge) {
        super(context);
        background = gauge.background();
        front = gauge.front();
        needleColor = gauge.needleColor();
        options = gauge.options();
        style = gauge.needle();

        plate = PanelStyles.GAUGE.isOn(options, PanelStyles.GAUGE_PLATE) ? create(PanelModels.plate()) : null;
        back = create(ShiftedModels.plain(LoconauticsPartialModels.GAUGE_BACK));
        face = create(ShiftedModels.shifted(LoconauticsPartialModels.GAUGE_FACE,
                LoconauticsSpriteShifts.LOCOMOTIVE_GAUGE,
                FACE_STRIDE * (background % 6), FACE_STRIDE * (background / 6)));
        body = create(PanelModels.body(GaugePartRenderer.bodyForFront(front)));
        needle = create(ShiftedModels.shifted(GaugePartRenderer.partialFor(style),
                LoconauticsSpriteShifts.LOCOMOTIVE_GAUGE,
                style.colorOffsetU(needleColor), style.colorOffsetV(needleColor)));
        bulb = PanelStyles.GAUGE.isOn(options, PanelStyles.GAUGE_LIGHT)
                ? new PanelBulbVisual(this::create, LoconauticsPartialModels.GAUGE_BULB) : null;
    }

    @Override
    public boolean matches(PanelPart part) {
        return part instanceof Gauge gauge && gauge.background() == background && gauge.front() == front
                && gauge.needleColor() == needleColor && gauge.options() == options;
    }

    @Override
    public void update(PanelBlockEntity panel, PanelSlot slot, PoseStack ms, float partialTick,
                       int light, boolean full) {
        if (full) {
            if (plate != null) place(plate, ms, light);
            place(back, ms, light);
            place(face, ms, light);
            place(body, ms, light);
        }
        float shown = panel.animation(slot).getValue(partialTick);
        if (bulb != null) bulb.update(ms, light, panel.bulbGlow(slot, partialTick), full);
        float quiver = GaugePartRenderer.wobble(panel, slot, shown, partialTick);
        if (!full && shown == lastShown && quiver == lastQuiver) return;
        lastShown = shown;
        lastQuiver = quiver;

        ms.pushPose();
        GaugePartRenderer.applyNeedle(ms, style, shown, quiver);
        place(needle, ms, light);
        ms.popPose();
    }
}
