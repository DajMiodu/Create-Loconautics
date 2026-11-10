package com.lycoris.loconautics.mixin.steelcable;

import com.lycoris.loconautics.content.steelcable.SteelCableHolder;
import com.lycoris.loconautics.content.steelcable.SteelCableHolderBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.simulated_team.simulated.content.blocks.rope.rope_connector.RopeConnectorBlockEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(value = RopeConnectorBlockEntity.class, remap = false)
public abstract class RopeConnectorSteelCableMixin implements SteelCableHolder {

    @Inject(method = "addBehaviours", at = @At("TAIL"))
    private void loconautics$addSteelCableBehaviour(List<BlockEntityBehaviour> behaviours, CallbackInfo ci) {
        behaviours.add(new SteelCableHolderBehaviour((RopeConnectorBlockEntity) (Object) this, strandCapacity()));
    }

    @Override
    public Vec3 getAttachmentPoint() {
        RopeConnectorBlockEntity self = (RopeConnectorBlockEntity) (Object) this;
        return self.getAttachmentPoint(self.getBlockPos(), self.getBlockState());
    }

    @Override
    public int strandCapacity() {
        return 4;
    }

    @Override
    public boolean acceptsFluidLinks() {
        return true;
    }
}
