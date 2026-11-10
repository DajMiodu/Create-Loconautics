package com.lycoris.loconautics.mixin.client.conductor;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.lycoris.loconautics.client.LoconauticsPartialModels;
import com.lycoris.loconautics.content.schedule.ScheduleReadiness;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.equipment.hats.EntityHats;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = EntityHats.class, remap = false)
public class ConductorHatModelMixin {

    @ModifyReturnValue(method = "getHatFor", at = @At("RETURN"))
    private static PartialModel loconautics$conductorsHat(PartialModel original, LivingEntity entity) {
        if (original != AllPartialModels.TRAIN_HAT) {
            return original;
        }
        if (!entity.getPersistentData().contains("TrainHat") && !ScheduleReadiness.seatedAtControls(entity)) {
            return original;
        }
        return LoconauticsPartialModels.conductorsHat(entity.getUUID().getLeastSignificantBits());
    }

    @ModifyReturnValue(method = "shouldRenderTrainHat", at = @At("RETURN"))
    private static boolean loconautics$playerAtControls(boolean original, LivingEntity entity) {
        return original || entity instanceof Player && ScheduleReadiness.seatedAtControls(entity);
    }
}
