package com.lycoris.loconautics.mixin.create.train;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.lycoris.loconautics.common.impact.TrainImpacts;
import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.trainphysics.state.CreateTrainShadow;
import com.simibubi.create.content.trains.entity.Train;
import net.createmod.catnip.data.Pair;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;



// create crashes both trains on any touch, a carts shadow gets weighed by mass and speed instead
@Mixin(value = Train.class, remap = false)
public abstract class TrainSableCollisionMixin {

    @WrapOperation(
            method = "collideWithOtherTrains",
            at = @At(value = "INVOKE",
                    target = "Lcom/simibubi/create/content/trains/entity/Train;findCollidingTrain(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/resources/ResourceKey;)Lnet/createmod/catnip/data/Pair;"))
    private Pair<Train, Vec3> loconautics$weighCartHit(Train self, Level level, Vec3 start, Vec3 end,
                                                       ResourceKey<Level> dimension,
                                                       Operation<Pair<Train, Vec3>> original) {
        Pair<Train, Vec3> hit = original.call(self, level, start, end, dimension);
        if (hit == null || !(level instanceof ServerLevel server)) {
            return hit;
        }
        boolean shadow = CreateTrainShadow.isShadow(hit.getFirst());
        if (!shadow) {
            return hit;
        }
        SableTrain cart = CreateTrainShadow.cartAt(hit.getFirst(), hit.getSecond());
        if (cart == null) {
            return null;
        }
        TrainImpacts.createMeetsCart(server, self, cart, start.subtract(end).normalize(), hit.getSecond());
        return null;
    }
}
