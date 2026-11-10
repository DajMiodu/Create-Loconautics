package com.lycoris.loconautics.mixin.create.bogey;

import com.lycoris.loconautics.content.bearingaxle.BogeyShaft;
import com.simibubi.create.content.kinetics.RotationPropagator;
import com.simibubi.create.content.kinetics.base.IRotate;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

@Mixin(value = RotationPropagator.class, remap = false)
public abstract class BogeyShaftPropagatorMixin {

    @Inject(method = "getPotentialNeighbourLocations", at = @At("RETURN"), cancellable = true)
    private static void loconautics$reachPastBogey(KineticBlockEntity be, CallbackInfoReturnable<List<BlockPos>> cir) {
        Level level = be.getLevel();
        if (level == null) {
            return;
        }
        List<BlockPos> extended = null;
        for (Direction dir : Direction.values()) {
            BlockPos mid = be.getBlockPos().relative(dir);
            BlockPos far = mid.relative(dir);
            if (!level.isLoaded(far)) {
                continue;
            }
            BlockState midState = level.getBlockState(mid);
            boolean linked = BogeyShaft.passes(level, mid, dir)
                    || ((midState.isAir() || midState.hasProperty(BogeyShaft.SHAFTED)) && BogeyShaft.staleLink(be, far, level));
            if (!linked) {
                continue;
            }
            if (extended == null) {
                extended = new ArrayList<>(cir.getReturnValue());
            }
            extended.add(far);
        }
        if (extended != null) {
            cir.setReturnValue(extended);
        }
    }

    @Inject(method = "getRotationSpeedModifier", at = @At("HEAD"), cancellable = true)
    private static void loconautics$throughBogey(KineticBlockEntity from, KineticBlockEntity to,
                                                  CallbackInfoReturnable<Float> cir) {
        Level level = from.getLevel();
        BlockPos diff = to.getBlockPos().subtract(from.getBlockPos());
        Direction dir = Direction.getNearest(diff.getX(), diff.getY(), diff.getZ());
        if (level == null || !diff.equals(dir.getNormal().multiply(2))) {
            return;
        }
        if (!BogeyShaft.passes(level, from.getBlockPos().relative(dir), dir)) {
            return;
        }
        if (from.getBlockState().getBlock() instanceof IRotate a && to.getBlockState().getBlock() instanceof IRotate b
                && a.hasShaftTowards(level, from.getBlockPos(), from.getBlockState(), dir)
                && b.hasShaftTowards(level, to.getBlockPos(), to.getBlockState(), dir.getOpposite())) {
            cir.setReturnValue(1.0f);
        }
    }
}
