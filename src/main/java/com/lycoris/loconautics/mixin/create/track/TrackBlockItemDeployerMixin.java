package com.lycoris.loconautics.mixin.create.track;

import com.lycoris.loconautics.trainphysics.actors.TrainActorControls;
import com.simibubi.create.AllDataComponents;
import com.simibubi.create.content.kinetics.deployer.DeployerFakePlayer;
import com.simibubi.create.content.trains.track.ITrackBlock;
import com.simibubi.create.content.trains.track.TrackBlockItem;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = TrackBlockItem.class, remap = false)
public abstract class TrackBlockItemDeployerMixin {

    @Inject(method = "useOn", at = @At("HEAD"), cancellable = true)
    private void loconautics$deployerNeverBindsTrack(UseOnContext context,
                                                     CallbackInfoReturnable<InteractionResult> cir) {
        if (!(context.getPlayer() instanceof DeployerFakePlayer deployer)) {
            return;
        }
        if (!TrainActorControls.onTrain(context.getLevel(), deployer.blockPosition())) {
            return;
        }

        ItemStack stack = context.getItemInHand();
        stack.remove(AllDataComponents.TRACK_CONNECTING_FROM);
        stack.remove(AllDataComponents.TRACK_EXTENDED_CURVE);

        if (context.getLevel().getBlockState(context.getClickedPos()).getBlock() instanceof ITrackBlock) {
            cir.setReturnValue(InteractionResult.PASS);
        }
    }
}
