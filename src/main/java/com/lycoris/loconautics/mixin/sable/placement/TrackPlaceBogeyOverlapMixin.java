package com.lycoris.loconautics.mixin.sable.placement;

import com.lycoris.loconautics.trainphysics.state.SableTrainRegistry;
import com.lycoris.loconautics.trainphysics.wheels.GhostWheelBodies;
import com.simibubi.create.content.trains.track.TrackBlockItem;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = BlockPlaceContext.class, priority = 500)
public abstract class TrackPlaceBogeyOverlapMixin extends UseOnContext {

    @Shadow
    protected boolean replaceClicked;

    @Shadow
    public abstract BlockPos getClickedPos();

    protected TrackPlaceBogeyOverlapMixin(Player player, InteractionHand hand, BlockHitResult hit) {
        super(player, hand, hit);
    }

    @Inject(method = "canPlace", at = @At("HEAD"), cancellable = true)
    private void loconautics$trackUnderTrain(CallbackInfoReturnable<Boolean> cir) {
        if (!(getItemInHand().getItem() instanceof TrackBlockItem)) {
            return;
        }
        BlockPos clicked = getClickedPos();
        if (Sable.HELPER.getContaining(getLevel(), (Vec3i) clicked) != null) {
            return;
        }
        Iterable<SubLevel> overlapping = Sable.HELPER.getAllIntersecting(getLevel(),
                (BoundingBox3dc) new BoundingBox3d(clicked));
        boolean any = false;
        for (SubLevel other : overlapping) {
            if (SableTrainRegistry.bySubLevel(other.getUniqueId()) == null && !GhostWheelBodies.isWheelBody(other.getUniqueId())) {
                return;
            }
            any = true;
        }
        if (any) {
            cir.setReturnValue(replaceClicked || getLevel().getBlockState(clicked).canBeReplaced((BlockPlaceContext) (Object) this));
        }
    }
}
