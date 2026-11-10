package com.lycoris.loconautics.content.controls.panel.visual;

import com.lycoris.loconautics.client.LoconauticsPartialModels;
import com.lycoris.loconautics.client.LoconauticsSpriteShifts;
import com.lycoris.loconautics.content.controls.panel.PanelBlockEntity;
import com.lycoris.loconautics.content.controls.panel.PanelPart;
import com.lycoris.loconautics.content.controls.panel.PanelSlot;
import com.lycoris.loconautics.content.controls.panel.style.PanelStyle;
import com.lycoris.loconautics.content.controls.panel.style.PanelStyles;
import com.lycoris.loconautics.content.controls.panellever.LeverPart;
import com.lycoris.loconautics.content.controls.panellever.LeverPartRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.instance.TransformedInstance;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

public final class LeverPartVisual extends PanelPartVisual {

    private final int body;
    private final int knobColor;
    private final int style;
    private final int options;

    @Nullable
    private final TransformedInstance plate;
    private final TransformedInstance shell;
    @Nullable
    private final TransformedInstance base;
    private final TransformedInstance handle;
    private final TransformedInstance knob;
    @Nullable
    private final PanelBulbVisual bulb;

    private float lastFraction = Float.NaN;

    public LeverPartVisual(VisualizationContext context, LeverPart lever) {
        super(context);
        body = lever.body();
        knobColor = lever.knobColor();
        style = lever.style();
        options = lever.shownOptions();

        PanelStyle.ModelKit kit = PanelStyles.LEVER.get(style).kit();
        LoconauticsPartialModels.StyleKit partials = kit == null ? null
                : LoconauticsPartialModels.LEVER_STYLE_KITS.get(kit.name());
        int knobStrideX = kit == null ? LeverPartRenderer.KNOB_STRIDE_X : kit.knobStrideX();
        int knobStrideY = kit == null ? LeverPartRenderer.KNOB_STRIDE_Y : kit.knobStrideY();

        plate = PanelStyles.LEVER.isOn(options, PanelStyles.LEVER_PLATE) ? create(PanelModels.slimPlate()) : null;
        shell = create(PanelModels.slimBody(body));
        base = partials != null && partials.base() != null
                ? create(ShiftedModels.plain(partials.base())) : null;
        handle = create(ShiftedModels.plain(partials == null ? LoconauticsPartialModels.LEVER_HANDLE
                : partials.handle()));
        knob = create(ShiftedModels.shifted(partials == null ? LoconauticsPartialModels.LEVER_KNOB : partials.knob(),
                LoconauticsSpriteShifts.GAUGE_LEVER,
                knobStrideX * (knobColor % 5), knobStrideY * (knobColor / 5)));
        bulb = PanelStyles.LEVER.isOn(options, PanelStyles.LEVER_LIGHT)
                ? new PanelBulbVisual(this::create, LoconauticsPartialModels.LEVER_BULB) : null;
    }

    @Override
    public boolean matches(PanelPart part) {
        return part instanceof LeverPart lever && lever.body() == body && lever.knobColor() == knobColor
                && lever.style() == style && lever.shownOptions() == options;
    }

    @Override
    public void update(PanelBlockEntity panel, PanelSlot slot, PoseStack ms, float partialTick,
                       int light, boolean full) {
        if (full) {
            if (plate != null) place(plate, ms, light);
            place(shell, ms, light);
            if (base != null) place(base, ms, light);
        }
        float fraction = panel.animation(slot).getValue(partialTick);
        if (full || fraction != lastFraction) {
            lastFraction = fraction;
            ms.pushPose();
            ms.translate(LeverPartRenderer.PIVOT_X, LeverPartRenderer.PIVOT_Y, LeverPartRenderer.PIVOT_Z);
            ms.mulPose(Axis.XP.rotationDegrees(
                    Mth.lerp(fraction, LeverPartRenderer.OFF_DEGREES, LeverPartRenderer.ON_DEGREES)));
            ms.translate(-LeverPartRenderer.PIVOT_X, -LeverPartRenderer.PIVOT_Y, -LeverPartRenderer.PIVOT_Z);
            place(handle, ms, light);
            place(knob, ms, light);
            ms.popPose();
        }
        if (bulb != null) bulb.update(ms, light, panel.bulbGlow(slot, partialTick), full);    }
}
