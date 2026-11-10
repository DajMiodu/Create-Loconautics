package com.lycoris.loconautics.content.controls.panel.visual;

import com.lycoris.loconautics.content.controls.panel.PanelGlass;
import com.lycoris.loconautics.content.controls.panel.PanelGlassRenderer;
import com.lycoris.loconautics.client.LoconauticsPartialModels;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.engine_room.flywheel.api.material.Transparency;
import dev.engine_room.flywheel.api.model.Model;
import dev.engine_room.flywheel.lib.instance.TransformedInstance;

import java.util.function.Function;

public final class PanelGlassVisual {

    private final PanelGlass glass;
    private final TransformedInstance instance;
    private float lastAngle = Float.NaN;

    public PanelGlassVisual(Function<Model, TransformedInstance> factory, PanelGlass glass) {
        this.glass = glass;
        instance = factory.apply(ShiftedModels.withTransparency(
                LoconauticsPartialModels.glass(glass), Transparency.TRANSLUCENT));
    }

    public void update(PoseStack ms, int light, float angle, boolean full) {
        if (!full && angle == lastAngle) return;
        lastAngle = angle;
        ms.pushPose();
        PanelGlassRenderer.hinge(ms, glass, angle);
        PanelPartVisual.place(instance, ms, light);
        ms.popPose();
    }
}
