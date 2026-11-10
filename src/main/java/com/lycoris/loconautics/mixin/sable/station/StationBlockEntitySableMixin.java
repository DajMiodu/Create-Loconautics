package com.lycoris.loconautics.mixin.sable.station;

import com.lycoris.loconautics.config.LoconauticsConfig;
import com.lycoris.loconautics.network.packets.train.StationParkedSyncPacket;
import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.trainphysics.station.ConsistNose;
import com.lycoris.loconautics.trainphysics.station.StationLastStyle;
import com.lycoris.loconautics.trainphysics.spawn.SableTrainSpawner;
import com.simibubi.create.content.contraptions.AssemblyException;
import com.simibubi.create.content.trains.station.GlobalStation;
import com.simibubi.create.content.trains.station.StationBlockEntity;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.UUID;

@Mixin(value = StationBlockEntity.class, remap = false)
public abstract class StationBlockEntitySableMixin extends SmartBlockEntity implements StationLastStyle {

    private StationBlockEntitySableMixin(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Shadow
    public abstract GlobalStation getStation();

    @Shadow
    public abstract boolean isAssembling();

    @Unique
    private boolean loconautics$parked;

    @Unique
    private String loconautics$name = "";

    @Unique
    private List<Integer> loconautics$carriages = List.of();

    @Unique
    private int loconautics$mapColor;

    @Unique
    private String loconautics$icon = "";

    @Unique
    private String loconautics$lastIcon = "";

    @Unique
    private int loconautics$refreshTick;

    @Unique
    private float loconautics$approach = Float.MAX_VALUE;

    @Unique
    private static final double loconautics$APPROACH_RANGE = 30.0;

    @Override
    public String loconautics$getLastIcon() {
        return loconautics$lastIcon;
    }

    @Override
    public void loconautics$setLastIcon(String icon) {
        loconautics$lastIcon = icon == null ? "" : icon;
    }

    @Inject(method = "read", at = @At("TAIL"))
    private void loconautics$readLastIcon(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket,
                                          CallbackInfo ci) {
        loconautics$lastIcon = tag.getString("LoconauticsPrevTrainIcon");
    }

    @Inject(method = "write", at = @At("HEAD"))
    private void loconautics$writeLastIcon(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket,
                                           CallbackInfo ci) {
        tag.putString("LoconauticsPrevTrainIcon", loconautics$lastIcon);
    }

    @Inject(method = "assemble", at = @At("HEAD"), cancellable = true)
    private void loconautics$blockCreateAssembly(UUID playerUUID, CallbackInfo ci) {
        if (LoconauticsConfig.ENABLE_CREATE_TRAINS.get()) {
            return;
        }
        ((StationBlockEntityAccessor) this).loconautics$exception(new AssemblyException(
                Component.translatable("loconautics.station.createtrain.disabled")), -1);
        ci.cancel();
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void loconautics$detectParkedSableTrain(CallbackInfo ci) {
        Level level = this.getLevel();
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        boolean parked = false;
        String name = "";
        List<Integer> carriages = List.of();
        int mapColor = 0;
        String icon = "";
        float approach = Float.MAX_VALUE;
        GlobalStation station = this.getStation();
        if (!this.isAssembling() && station != null) {
            SableTrain train = SableTrainSpawner.findParkedTrain(serverLevel, station);
            if (train != null) {
                parked = true;
                approach = 0.0f;
            } else {
                SableTrain inbound = SableTrainSpawner.findApproachingTrain(serverLevel, station);
                double gap = SableTrainSpawner.approachDistance(inbound, station);
                if (gap < Double.MAX_VALUE && gap <= loconautics$APPROACH_RANGE) {
                    train = ConsistNose.of(inbound).car();
                    approach = (float) Math.max(0.01, gap);
                }
            }
            if (train != null) {
                name = SableTrainSpawner.trainName(serverLevel, train);
                carriages = SableTrainSpawner.consistCarriageIcons(serverLevel, train);
                mapColor = train.mapColorIndex();
                icon = train.iconType();
            }
        }

        // the approach distance changes every tick, so it drives its own resend instead of the 20 tick refresh
        if (approach != loconautics$approach || parked != loconautics$parked || !name.equals(loconautics$name)
                || !carriages.equals(loconautics$carriages) || mapColor != loconautics$mapColor
                || !icon.equals(loconautics$icon) || approach != loconautics$approach) {
            loconautics$parked = parked;
            loconautics$name = name;
            loconautics$carriages = carriages;
            loconautics$mapColor = mapColor;
            loconautics$icon = icon;
            loconautics$approach = approach;
            StationParkedSyncPacket.broadcast(this.getBlockPos(), parked, name, carriages, mapColor, icon, approach);
        } else if (parked && loconautics$refreshTick % 20 == 0) {
            StationParkedSyncPacket.broadcast(this.getBlockPos(), true, name, carriages, mapColor, icon, 0.0f);
        }
        loconautics$refreshTick++;
    }
}
