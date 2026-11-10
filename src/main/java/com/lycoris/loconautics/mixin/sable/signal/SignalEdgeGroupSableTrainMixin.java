package com.lycoris.loconautics.mixin.sable.signal;

import com.lycoris.loconautics.trainphysics.signal.TrainSignals;
import com.lycoris.loconautics.trainphysics.state.CreateTrainShadow;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.signal.SignalBoundary;
import com.simibubi.create.content.trains.signal.SignalEdgeGroup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

// a sabletrain is not a create train, so it can never enter group.trains and every signal stayed green forever
@Mixin(value = SignalEdgeGroup.class, remap = false)
public abstract class SignalEdgeGroupSableTrainMixin {

    @Shadow
    public UUID id;

    @Inject(method = "isThisOccupiedUnless(Lcom/simibubi/create/content/trains/entity/Train;)Z",
            at = @At("HEAD"), cancellable = true)
    private void loconautics$sableTrainBlocksCreateTrain(Train train, CallbackInfoReturnable<Boolean> cir) {
        if (CreateTrainShadow.isShadow(train)) {
            Set<UUID> own = new HashSet<>();
            CreateTrainShadow.membersOf(train).forEach(member -> own.add(member.cart()));
            if (TrainSignals.heldBySableTrain(id, own)) {
                cir.setReturnValue(true);
            }
            return;
        }
        if (TrainSignals.occupied(id) || TrainSignals.reserved(id)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "isThisOccupiedUnless(Lcom/simibubi/create/content/trains/signal/SignalBoundary;)Z",
            at = @At("HEAD"), cancellable = true)
    private void loconautics$sableTrainTurnsSignalRed(SignalBoundary boundary, CallbackInfoReturnable<Boolean> cir) {
        if (TrainSignals.occupied(id) || TrainSignals.reservedOtherThan(id, boundary.getId())) {
            cir.setReturnValue(true);
        }
    }
}
