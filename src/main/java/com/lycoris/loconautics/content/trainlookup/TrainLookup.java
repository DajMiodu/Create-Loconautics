package com.lycoris.loconautics.content.trainlookup;

import com.lycoris.loconautics.content.bearingaxle.BearingAxleBlockEntity;
import com.lycoris.loconautics.trainphysics.storage.ConsistAccess;
import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.common.sable.SubLevelBlocks;
import com.lycoris.loconautics.common.sable.SubLevelData;
import com.lycoris.loconautics.trainphysics.state.SableTrainRegistry;
import com.lycoris.loconautics.trainphysics.state.SableTrainStore;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class TrainLookup {

    public record TrainInfo(UUID trainId, UUID subLevelId, @Nullable String name,
                            ResourceKey<Level> dimension, Vec3 position, boolean loaded) {

        public String displayName() {
            return name != null && !name.isBlank() ? name : trainId.toString();
        }

        public String shortId() {
            return trainId.toString().substring(0, 8);
        }
    }

    public record CarriageInfo(UUID subLevelId, int index, String label, boolean driver) {
    }

    private TrainLookup() {
    }

    public static List<TrainInfo> getAllTrains(ServerLevel level) {
        List<TrainInfo> out = new ArrayList<>();
        if (level.getServer() == null) {
            return out;
        }
        for (Map.Entry<UUID, CompoundTag> record : SableTrainStore.get(level.getServer()).records().entrySet()) {
            TrainInfo info = read(level, record.getKey(), record.getValue());
            if (info != null) {
                out.add(info);
            }
        }
        return out;
    }

    @Nullable
    public static TrainInfo getTrain(ServerLevel level, UUID trainId) {
        if (level.getServer() == null || trainId == null) {
            return null;
        }
        CompoundTag record = SableTrainStore.get(level.getServer()).records().get(trainId);
        return record == null ? null : read(level, trainId, record);
    }

    @Nullable
    private static TrainInfo read(ServerLevel level, UUID trainId, CompoundTag record) {
        CompoundTag car = record.getCompound("Car");
        if (!car.hasUUID("SubLevel")) {
            return null;
        }
        UUID subLevelId = car.getUUID("SubLevel");
        ResourceLocation dim = ResourceLocation.tryParse(record.getString("Dimension"));
        if (dim == null) {
            return null;
        }
        ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, dim);

        ServerSubLevel sub = SubLevelData.resolve(level, subLevelId);
        String name = record.contains("Name") ? record.getString("Name") : null;
        Vec3 position = readVec(record);
        if (sub != null) {
            position = SubLevelData.toVec3(SubLevelData.position(sub));
            if (sub.getName() != null && !sub.getName().isBlank()) {
                name = sub.getName();
            }
        }
        return new TrainInfo(trainId, subLevelId, name, dimension, position, sub != null);
    }

    private static Vec3 readVec(CompoundTag record) {
        if (!record.contains("LastPos")) {
            return Vec3.ZERO;
        }
        CompoundTag v = record.getCompound("LastPos");
        return new Vec3(v.getDouble("x"), v.getDouble("y"), v.getDouble("z"));
    }

    public static List<CarriageInfo> getCarriages(ServerLevel level, UUID trainId) {
        List<CarriageInfo> out = new ArrayList<>();
        TrainInfo info = getTrain(level, trainId);
        if (info == null) {
            return out;
        }
        List<UUID> chain = ConsistAccess.carriages(level, info.subLevelId());
        if (chain.isEmpty()) {
            chain = List.of(info.subLevelId());
        }

        List<Integer> driverSlots = new ArrayList<>();
        for (int i = 0; i < chain.size(); i++) {
            if (hasBearingAxle(level, chain.get(i))) {
                driverSlots.add(i);
            }
        }

        int plainCounter = 0;
        int driverCounter = 0;
        for (int i = 0; i < chain.size(); i++) {
            boolean driver = driverSlots.contains(i);
            String label;
            if (driver) {
                driverCounter++;
                label = driverSlots.size() > 1 ? "Driver" + driverCounter : "Driver";
            } else {
                plainCounter++;
                label = String.valueOf(plainCounter);
            }
            out.add(new CarriageInfo(chain.get(i), i, label, driver));
        }
        return out;
    }

    private static boolean hasBearingAxle(ServerLevel level, UUID subLevelId) {
        ServerSubLevel sub = SubLevelData.resolve(level, subLevelId);
        return sub != null && SubLevelBlocks.find(sub, BearingAxleBlockEntity.class) != null;
    }

    public static Vec3 getDriverPosition(ServerLevel level, UUID trainId) {
        TrainInfo info = getTrain(level, trainId);
        if (info == null) {
            return Vec3.ZERO;
        }
        for (CarriageInfo carriage : getCarriages(level, trainId)) {
            if (!carriage.driver()) {
                continue;
            }
            ServerSubLevel sub = SubLevelData.resolve(level, carriage.subLevelId());
            if (sub != null) {
                return SubLevelData.toVec3(SubLevelData.position(sub));
            }
        }
        return info.position();
    }

    public static Vec3 getVelocity(ServerLevel level, UUID trainId) {
        TrainInfo info = getTrain(level, trainId);
        if (info == null) {
            return Vec3.ZERO;
        }
        ServerSubLevel sub = SubLevelData.resolve(level, info.subLevelId());
        if (sub == null) {
            return Vec3.ZERO;
        }
        Vector3d velocity = SubLevelData.linearVelocity(sub);
        return velocity == null ? Vec3.ZERO : SubLevelData.toVec3(velocity);
    }

    public static boolean isMoving(ServerLevel level, UUID trainId) {
        SableTrain train = SableTrainRegistry.get(trainId);
        if (train != null && Math.abs(train.speed()) > 1.0e-4) {
            return true;
        }
        return getVelocity(level, trainId).lengthSqr() > 1.0e-6;
    }

    public static int getRiderCount(ServerLevel level, UUID trainId) {
        TrainInfo info = getTrain(level, trainId);
        if (info == null) {
            return 0;
        }
        return ConsistAccess.countPlayersAboard(level, info.subLevelId(), ConsistAccess.WHOLE_CONSIST);
    }

    public static boolean isFullyLoaded(ServerLevel level, UUID trainId) {
        List<CarriageInfo> carriages = getCarriages(level, trainId);
        if (carriages.isEmpty()) {
            return false;
        }
        for (CarriageInfo carriage : carriages) {
            if (SubLevelData.resolve(level, carriage.subLevelId()) == null) {
                return false;
            }
        }
        return true;
    }

    public static boolean isDerailed(ServerLevel level, UUID trainId) {
        SableTrain train = SableTrainRegistry.get(trainId);
        if (train != null) {
            return train.isDerailed();
        }
        if (level.getServer() == null) {
            return false;
        }
        CompoundTag record = SableTrainStore.get(level.getServer()).records().get(trainId);
        return record != null && record.getBoolean("Derailed");
    }
}
