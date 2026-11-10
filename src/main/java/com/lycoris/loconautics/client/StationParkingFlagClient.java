package com.lycoris.loconautics.client;

import com.lycoris.loconautics.mixin.sable.station.StationBlockEntityAccessor;
import com.lycoris.loconautics.trainphysics.state.SableTrainClientRegistry;
import com.simibubi.create.content.trains.station.StationBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.HashSet;
import java.util.Set;

@OnlyIn(Dist.CLIENT)
public final class StationParkingFlagClient {

    private static final Set<BlockPos> FORCED = new HashSet<>();

    private StationParkingFlagClient() {
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            FORCED.clear();
            return;
        }
        Set<BlockPos> parked = SableTrainClientRegistry.parkedStations();

        for (BlockPos pos : parked) {
            if (mc.level.getBlockEntity(pos) instanceof StationBlockEntity be) {
                StationBlockEntityAccessor acc = (StationBlockEntityAccessor) be;
                acc.loconautics$setTrainPresent(true);
                acc.loconautics$setTrainCanDisassemble(true);
                FORCED.add(pos.immutable());
            }
        }
        FORCED.removeIf(pos -> {
            if (parked.contains(pos)) {
                return false;
            }
            if (mc.level.getBlockEntity(pos) instanceof StationBlockEntity be) {
                StationBlockEntityAccessor acc = (StationBlockEntityAccessor) be;
                acc.loconautics$setTrainPresent(false);
                acc.loconautics$setTrainCanDisassemble(false);
            }
            return true;
        });
    }

    public static void clear() {
        FORCED.clear();
    }
}
