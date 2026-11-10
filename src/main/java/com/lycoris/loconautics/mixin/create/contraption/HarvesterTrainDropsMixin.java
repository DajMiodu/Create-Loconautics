package com.lycoris.loconautics.mixin.create.contraption;

import com.lycoris.loconautics.trainphysics.actors.TrainActorDrops;
import dev.ryanhcode.sable.neoforge.mixinhelper.compatibility.create.harvester.HarvesterTicker;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = HarvesterTicker.class, remap = false)
public abstract class HarvesterTrainDropsMixin {

    @Inject(method = "dropItem", at = @At("HEAD"), cancellable = true, remap = false)
    private static void loconautics$dropIntoCarriage(Level level, ItemStack stack, BlockPos harvesterPos,
                                                     CallbackInfo ci) {
        if (TrainActorDrops.depositInPlace(level, harvesterPos, stack)) {
            ci.cancel();
        }
    }
}
