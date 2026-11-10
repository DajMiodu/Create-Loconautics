package com.lycoris.loconautics.mixin.client.conductor;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.lycoris.loconautics.client.ConductorHats;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock.HeatLevel;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlockEntity;
import com.simibubi.create.content.processing.burner.BlazeBurnerVisual;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import dev.engine_room.flywheel.lib.visual.AbstractBlockEntityVisual;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = BlazeBurnerVisual.class, remap = false)
public abstract class BlazeVisualConductorHatMixin extends AbstractBlockEntityVisual<BlazeBurnerBlockEntity> {

    protected BlazeVisualConductorHatMixin(VisualizationContext ctx, BlazeBurnerBlockEntity blockEntity,
                                           float partialTick) {
        super(ctx, blockEntity, partialTick);
    }

    @Unique
    private boolean loconautics$lastHat;

    @ModifyExpressionValue(
            method = "beginFrame",
            at = @At(value = "INVOKE", target = "isVisible(Lorg/joml/FrustumIntersection;)Z")
    )
    private boolean loconautics$forceHatVisible(boolean original) {
        boolean hat = ConductorHats.wearsHat(blockEntity);
        return original || hat || hat != loconautics$lastHat;
    }

    @ModifyExpressionValue(
            method = "beginFrame",
            at = @At(
                    value = "INVOKE",
                    target = "doDistanceLimitThisFrame(Ldev/engine_room/flywheel/api/visual/"
                            + "DynamicVisual$Context;)Z"
            )
    )
    private boolean loconautics$forceHatFrame(boolean original) {
        boolean hat = ConductorHats.wearsHat(blockEntity);
        boolean changed = hat != loconautics$lastHat;
        loconautics$lastHat = hat;
        return !(hat || changed) && original;
    }

    @ModifyExpressionValue(
            method = "animate",
            at = @At(
                    value = "FIELD",
                    target = "Lcom/simibubi/create/AllPartialModels;TRAIN_HAT:"
                            + "Ldev/engine_room/flywheel/lib/model/baked/PartialModel;"
            )
    )
    private PartialModel loconautics$conductorsHat(PartialModel original) {
        return ConductorHats.hatFor(blockEntity, original);
    }

    @ModifyExpressionValue(
            method = "animate",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/simibubi/create/content/processing/burner/BlazeBurnerBlockEntity;"
                            + "getHeatLevelForRender()Lcom/simibubi/create/content/processing/burner/"
                            + "BlazeBurnerBlock$HeatLevel;"
            )
    )
    private HeatLevel loconautics$awakeWithSchedule(HeatLevel original) {
        return ConductorHats.wearsHat(blockEntity) && !original.isAtLeast(HeatLevel.FADING)
                ? HeatLevel.FADING
                : original;
    }

    @ModifyExpressionValue(
            method = "animate",
            at = @At(value = "FIELD", target = "Lcom/simibubi/create/content/processing/burner/BlazeBurnerBlockEntity;hat:Z")
    )
    private boolean loconautics$drivingHat(boolean original) {
        return ConductorHats.hatOverride(blockEntity, original);
    }
}
