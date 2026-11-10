package com.lycoris.loconautics.mixin.sable.swivel;

import com.lycoris.loconautics.common.sable.SubLevelBlocks;
import com.simibubi.create.content.trains.bogey.AbstractBogeyBlock;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.physics.constraint.RotaryConstraintHandle;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.simulated_team.simulated.content.blocks.swivel_bearing.SwivelBearingBlock;
import dev.simulated_team.simulated.content.blocks.swivel_bearing.SwivelBearingBlockEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = SwivelBearingBlockEntity.class, remap = false)
public abstract class SwivelBearingAttachMixin {

    @Inject(method = "attachConstraints", at = @At("HEAD"), remap = false)
    private void loconautics$matchPivotInWorld(ServerSubLevel plateSubLevel, Vector3d attachPos, CallbackInfo ci) {
        SwivelBearingBlockEntity self = (SwivelBearingBlockEntity) (Object) this;
        SubLevel own = Sable.HELPER.getContaining(self);
        if (!loconautics$bothTrainCars(own, plateSubLevel)) {
            return;
        }
        Vec3 anchor = self.getBlockPos().relative(self.getBlockState().getValue(SwivelBearingBlock.FACING)).getCenter();
        Vec3 world = own.logicalPose().transformPosition(anchor);
        Vec3 inPlate = plateSubLevel.logicalPose().transformPositionInverse(world);
        double gap = inPlate.distanceTo(new Vec3(attachPos.x, attachPos.y, attachPos.z));
        if (gap > 0.05) {
        }
        attachPos.set(inPlate.x, inPlate.y, inPlate.z);
    }

    @Inject(method = "attachConstraints", at = @At("TAIL"), remap = false)
    private void loconautics$carsDoNotCollide(ServerSubLevel plateSubLevel, Vector3d attachPos, CallbackInfo ci) {
        SwivelBearingBlockEntity self = (SwivelBearingBlockEntity) (Object) this;
        if (!loconautics$bothTrainCars(Sable.HELPER.getContaining(self), plateSubLevel)) {
            return;
        }
        RotaryConstraintHandle handle = ((SwivelBearingAccessor) (Object) self).loconautics$getHandle();
        if (handle != null && handle.isValid()) {
            handle.setContactsEnabled(false);
        }
    }

    private static boolean loconautics$bothTrainCars(SubLevel own, ServerSubLevel plateSubLevel) {
        return plateSubLevel != null && own != null && own != plateSubLevel
                && SubLevelBlocks.hasBlock(own, AbstractBogeyBlock.class)
                && SubLevelBlocks.hasBlock(plateSubLevel, AbstractBogeyBlock.class);
    }
}
