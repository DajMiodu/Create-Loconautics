package com.lycoris.loconautics.content.controls.panel.visual;

import com.lycoris.loconautics.content.controls.panel.PanelBlock;
import com.lycoris.loconautics.content.controls.panel.PanelBlockEntity;
import com.lycoris.loconautics.content.controls.panel.PanelPart;
import com.lycoris.loconautics.content.controls.panel.PanelRenderer;
import com.lycoris.loconautics.content.controls.panel.PanelSlot;
import com.lycoris.loconautics.content.controls.panelbutton.ButtonPart;
import com.lycoris.loconautics.content.controls.panelgauge.GaugeBlockEntity.Gauge;
import com.lycoris.loconautics.content.controls.panellever.LeverPart;
import com.lycoris.loconautics.content.controls.panelswitch.SwitchPart;
import com.lycoris.loconautics.content.controls.panelvalve.ValvePart;
import com.lycoris.loconautics.content.dashboard.DashboardBlock;
import com.lycoris.loconautics.content.dashboard.DashboardRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.engine_room.flywheel.api.instance.Instance;
import dev.ryanhcode.sable.Sable;
import dev.engine_room.flywheel.api.visual.DynamicVisual;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.visual.AbstractBlockEntityVisual;
import dev.engine_room.flywheel.lib.visual.SimpleDynamicVisual;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Consumer;

public class PanelVisual extends AbstractBlockEntityVisual<PanelBlockEntity> implements SimpleDynamicVisual {

    private final VisualizationContext context;
    private final Map<PanelSlot, PanelPartVisual> parts = new EnumMap<>(PanelSlot.class);
    private final PoseStack pose = new PoseStack();

    private BlockState lastState;
    private boolean lastCentered;
    // updatelight runs on a flywheel worker thread while beginframe runs on another, so it only flags the change
    private volatile int light;
    private volatile boolean lightChanged = true;

    public PanelVisual(VisualizationContext context, PanelBlockEntity panel, float partialTick) {
        super(context, panel, partialTick);
        this.context = context;
        light = computePackedLight();
        refresh(partialTick);
    }

    @Override
    public void beginFrame(DynamicVisual.Context ctx) {
        if (blockEntity.isRemoved() || level.getBlockEntity(pos) != blockEntity) {
            delete();
            return;
        }
        // plot position against world camera, both guards are wrong inside a sub level
        boolean inSubLevel = Sable.HELPER.getContainingClient(blockEntity) != null;
        if (!inSubLevel && (doDistanceLimitThisFrame(ctx) || !isVisible(ctx.frustum()))) return;
        refresh(ctx.partialTick());
    }

    @Override
    public void updateLight(float partialTick) {
        light = computePackedLight();
        lightChanged = true;
    }

    private void refresh(float partialTick) {
        BlockState state = blockEntity.getBlockState();
        boolean centered = blockEntity.isCentered();
        boolean full = lightChanged || state != lastState || centered != lastCentered;
        lastState = state;
        lastCentered = centered;
        lightChanged = false;

        for (PanelSlot slot : PanelSlot.values()) {
            PanelPart part = blockEntity.getPart(slot);
            PanelPartVisual visual = parts.get(slot);
            boolean slotFull = full;
            if (part == null) {
                if (visual != null) {
                    visual.delete();
                    parts.remove(slot);
                }
                continue;
            }
            if (visual == null || !visual.matches(part)) {
                if (visual != null) visual.delete();
                visual = create(part);
                if (visual == null) {
                    parts.remove(slot);
                    continue;
                }
                parts.put(slot, visual);
                slotFull = true;
            }

            pose.pushPose();
            pose.translate(visualPos.getX(), visualPos.getY(), visualPos.getZ());
            if (state.getBlock() instanceof DashboardBlock) DashboardRenderer.placeBoard(pose, state);
            else PanelRenderer.placePanel(pose, state);
            double offsetX = centered ? PanelBlock.CENTER_OFFSET : slot.modelX;
            double offsetZ = centered ? PanelBlock.CENTER_OFFSET : slot.modelZ;
            pose.translate(offsetX / 16f, 0, offsetZ / 16f);
            visual.update(blockEntity, slot, pose, partialTick, light, slotFull);
            pose.popPose();
        }
    }

    @Nullable
    private PanelPartVisual create(PanelPart part) {
        return switch (part) {
            case Gauge gauge -> new GaugePartVisual(context, gauge);
            case LeverPart lever -> new LeverPartVisual(context, lever);
            case SwitchPart flip -> new SwitchPartVisual(context, flip);
            case ButtonPart button -> new ButtonPartVisual(context, button);
            case ValvePart valve -> new ValvePartVisual(context, valve);
            default -> null;
        };
    }

    @Override
    public void collectCrumblingInstances(Consumer<Instance> consumer) {
        parts.values().forEach(visual -> visual.collectCrumbling(consumer));
    }

    @Override
    protected void _delete() {
        parts.values().forEach(PanelPartVisual::delete);
        parts.clear();
    }
}
