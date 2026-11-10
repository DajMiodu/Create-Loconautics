package com.lycoris.loconautics.mixin.client.conductor;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.lycoris.loconautics.client.ConductorHats;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock.HeatLevel;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlockEntity;
import com.simibubi.create.content.processing.burner.BlazeBurnerRenderer;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.minecraft.client.renderer.MultiBufferSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = BlazeBurnerRenderer.class, remap = false)
public class BlazeConductorHatMixin {

    @ModifyExpressionValue(
            method = "renderSafe",
            at = @At(
                    value = "FIELD",
                    target = "Lcom/simibubi/create/AllPartialModels;TRAIN_HAT:"
                            + "Ldev/engine_room/flywheel/lib/model/baked/PartialModel;"
            )
    )
    private PartialModel loconautics$conductorsHat(PartialModel original, BlazeBurnerBlockEntity be,
                                                   float partialTicks, PoseStack ms, MultiBufferSource bufferSource,
                                                   int light, int overlay) {
        return ConductorHats.hatFor(be, original);
    }

    @ModifyExpressionValue(
            method = "renderSafe",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/simibubi/create/content/processing/burner/BlazeBurnerBlockEntity;"
                            + "getHeatLevelFromBlock()Lcom/simibubi/create/content/processing/burner/"
                            + "BlazeBurnerBlock$HeatLevel;"
            )
    )
    private HeatLevel loconautics$awakeWithSchedule(HeatLevel original, BlazeBurnerBlockEntity be,
                                                    float partialTicks, PoseStack ms, MultiBufferSource bufferSource,
                                                    int light, int overlay) {
        return ConductorHats.wearsHat(be) && !original.isAtLeast(HeatLevel.FADING) ? HeatLevel.FADING : original;
    }

    @ModifyExpressionValue(
            method = "renderSafe",
            at = @At(value = "FIELD", target = "Lcom/simibubi/create/content/processing/burner/BlazeBurnerBlockEntity;hat:Z")
    )
    private boolean loconautics$drivingHat(boolean original, BlazeBurnerBlockEntity be, float partialTicks,
                                           PoseStack ms, MultiBufferSource bufferSource, int light, int overlay) {
        return ConductorHats.hatOverride(be, original);
    }
}
