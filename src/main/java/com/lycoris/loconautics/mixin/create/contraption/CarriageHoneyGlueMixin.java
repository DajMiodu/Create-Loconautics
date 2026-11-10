package com.lycoris.loconautics.mixin.create.contraption;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.content.contraptions.glue.SuperGlueEntity;
import com.simibubi.create.content.trains.entity.CarriageContraption;
import dev.simulated_team.simulated.content.entities.honey_glue.HoneyGlueEntity;
import dev.simulated_team.simulated.service.SimConfigService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Set;

@Mixin(value = Contraption.class, remap = false)
public abstract class CarriageHoneyGlueMixin {

    @WrapOperation(
            method = "moveBlock",
            at = @At(value = "INVOKE",
                    target = "Lcom/simibubi/create/content/contraptions/glue/SuperGlueEntity;isGlued(Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;Ljava/util/Set;)Z"))
    private boolean loconautics$honeyGlueJoinsCarriage(LevelAccessor level, BlockPos pos, Direction side,
                                                       Set<SuperGlueEntity> cached, Operation<Boolean> original) {
        if (original.call(level, pos, side, cached)) {
            return true;
        }
        if (!((Object) this instanceof CarriageContraption)) {
            return false;
        }
        try {
            return honeyGlued(level, pos, pos.relative(side));
        } catch (NoClassDefFoundError ignored) {
            return false;
        }
    }

    private static boolean honeyGlued(LevelAccessor level, BlockPos pos, BlockPos next) {
        int range = SimConfigService.INSTANCE.server().assembly.honeyGlueRange.get();
        for (HoneyGlueEntity glue : level.getEntitiesOfClass(HoneyGlueEntity.class,
                SuperGlueEntity.span(pos, next).inflate(range))) {
            if (glue.contains(pos) && glue.contains(next)) {
                return true;
            }
        }
        return false;
    }
}
