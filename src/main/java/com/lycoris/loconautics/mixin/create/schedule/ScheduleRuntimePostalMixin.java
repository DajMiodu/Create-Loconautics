package com.lycoris.loconautics.mixin.create.schedule;

import com.lycoris.loconautics.content.trainmail.CreateTrainMail;
import com.lycoris.loconautics.content.trainmail.PostalState;
import com.simibubi.create.content.trains.schedule.ScheduleRuntime;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Mixin(ScheduleRuntime.class)
public abstract class ScheduleRuntimePostalMixin implements PostalState {

    @Shadow
    public ScheduleRuntime.State state;

    @Shadow
    public abstract void startCooldown();

    @Unique
    private boolean loconautics$tripped;

    @Unique
    private boolean loconautics$repeat;

    @Unique
    private boolean loconautics$deposit;

    @Unique
    private final Set<UUID> loconautics$warned = new HashSet<>();

    @Override
    public boolean loconautics$tripped() {
        return loconautics$tripped;
    }

    @Override
    public void loconautics$setTripped(boolean tripped) {
        loconautics$tripped = tripped;
        if (!tripped) {
            loconautics$warned.clear();
        }
    }

    @Override
    public void loconautics$setRepeat(boolean repeat) {
        loconautics$repeat = repeat;
    }

    @Override
    public boolean loconautics$repeat() {
        return loconautics$repeat;
    }

    @Override
    public boolean loconautics$deposit() {
        return loconautics$deposit;
    }

    @Override
    public void loconautics$setDeposit(boolean deposit) {
        loconautics$deposit = deposit;
    }

    @Override
    public Set<UUID> loconautics$warned() {
        return loconautics$warned;
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void loconautics$postalRecheck(Level level, CallbackInfo ci) {
        CreateTrainMail.recheck((ScheduleRuntime) (Object) this, level);
    }

    @Inject(method = "destinationReached", at = @At("RETURN"))
    private void loconautics$postalArrival(CallbackInfo ci) {
        if (state != ScheduleRuntime.State.POST_TRANSIT) {
            return;
        }
        if (loconautics$repeat) {
            loconautics$repeat = false;
            state = ScheduleRuntime.State.PRE_TRANSIT;
            startCooldown();
            return;
        }
        loconautics$setTripped(false);
    }
}
