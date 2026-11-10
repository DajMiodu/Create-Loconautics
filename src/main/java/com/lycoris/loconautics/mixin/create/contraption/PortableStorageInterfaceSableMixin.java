package com.lycoris.loconautics.mixin.create.contraption;

import com.lycoris.loconautics.compat.create.psi.SablePsiDriver;
import com.lycoris.loconautics.compat.create.psi.SablePsiFrames;
import com.lycoris.loconautics.compat.create.psi.SablePsiHost;
import com.simibubi.create.content.contraptions.actors.psi.PortableStorageInterfaceBlockEntity;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import net.createmod.catnip.animation.LerpedFloat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

@Mixin(value = PortableStorageInterfaceBlockEntity.class, remap = false)
public abstract class PortableStorageInterfaceSableMixin extends SmartBlockEntity implements SablePsiHost {

    private PortableStorageInterfaceSableMixin(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Shadow
    protected float distance;

    @Shadow
    protected LerpedFloat connectionAnimation;

    @Unique
    private UUID loconautics$partnerSubLevel;

    @Unique
    private BlockPos loconautics$partnerPos;

    @Unique
    private boolean loconautics$linked;

    @Inject(method = "tick", at = @At("TAIL"))
    private void loconautics$driveSableLink(CallbackInfo ci) {
        SablePsiDriver.tick((PortableStorageInterfaceBlockEntity) (Object) this);
    }

    // create tears the connection down here once the keep alive runs out, the sub level side cannot do it itself
    @Inject(method = "stopTransferring", at = @At("TAIL"))
    private void loconautics$dropSableLink(CallbackInfo ci) {
        SablePsiDriver.unlink((PortableStorageInterfaceBlockEntity) (Object) this);
    }

    @Inject(method = "write", at = @At("TAIL"))
    private void loconautics$writeLink(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket,
                                       CallbackInfo ci) {
        if (clientPacket) {
            tag.putBoolean("LoconauticsLinked", loconautics$linked && loconautics$partnerPos != null);
            if (loconautics$partnerPos != null) {
                tag.putLong("LoconauticsPartner", loconautics$partnerPos.asLong());
            }
        }
    }

    @Inject(method = "read", at = @At("TAIL"))
    private void loconautics$readLink(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket,
                                      CallbackInfo ci) {
        if (clientPacket) {
            boolean linked = tag.getBoolean("LoconauticsLinked");
            loconautics$linked = linked;
            loconautics$partnerPos = linked ? BlockPos.of(tag.getLong("LoconauticsPartner")) : null;
        }
    }

    // the drawn gap comes from both bodies drawn poses every frame, the synced distance only moves on packets
    @Inject(method = "getExtensionDistance", at = @At("HEAD"), cancellable = true)
    private void loconautics$liveExtension(float partialTicks, CallbackInfoReturnable<Float> cir) {
        if (!loconautics$linked || loconautics$partnerPos == null || level == null || !level.isClientSide) {
            return;
        }
        Float reach = SablePsiFrames.liveReach((PortableStorageInterfaceBlockEntity) (Object) this,
                loconautics$partnerPos);
        if (reach != null) {
            double eased = Math.pow(connectionAnimation.getValue(partialTicks), 2);
            cir.setReturnValue((float) (eased * reach / 2));
        }
    }

    @Override
    public void loconautics$link(@Nullable UUID partnerSubLevel, BlockPos partnerPos) {
        loconautics$partnerSubLevel = partnerSubLevel;
        loconautics$partnerPos = partnerPos;
        loconautics$linked = true;
    }

    @Override
    public void loconautics$unlink() {
        loconautics$partnerSubLevel = null;
        loconautics$partnerPos = null;
        loconautics$linked = false;
    }

    @Override
    public boolean loconautics$linked() {
        return loconautics$linked;
    }

    @Override
    @Nullable
    public UUID loconautics$partnerSubLevel() {
        return loconautics$partnerSubLevel;
    }

    @Override
    @Nullable
    public BlockPos loconautics$partnerPos() {
        return loconautics$partnerPos;
    }

    @Override
    public void loconautics$setDistance(float value) {
        distance = value;
    }

    @Override
    public float loconautics$distance() {
        return distance;
    }
}
