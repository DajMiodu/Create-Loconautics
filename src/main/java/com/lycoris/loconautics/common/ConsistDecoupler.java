package com.lycoris.loconautics.common;

import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.common.sable.SubLevelData;
import com.lycoris.loconautics.trainphysics.TrainConsist;
import com.lycoris.loconautics.trainphysics.drive.SableTrainDriver;
import com.lycoris.loconautics.trainphysics.state.SableTrainRegistry;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class ConsistDecoupler {

    private ConsistDecoupler() {
    }

    public static boolean split(ServerLevel level, UUID trainId, int afterCar) {
        Cut cut = cutOf(level, trainId, afterCar);
        if (cut == null) {
            return false;
        }
        return ConnectorDetachers.decoupleBetween(level, cut.keptEnd, cut.firstOut);
    }

    public static Set<UUID> carsBeyond(ServerLevel level, UUID trainId, int afterCar) {
        Set<UUID> beyond = new HashSet<>();
        Cut cut = cutOf(level, trainId, afterCar);
        if (cut == null) {
            return beyond;
        }
        for (int i = cut.firstIndex; i >= 0 && i < cut.chain.size(); i += cut.step) {
            SableTrain car = SableTrainRegistry.bySubLevel(cut.chain.get(i));
            if (car != null) {
                beyond.add(car.id());
            }
        }
        return beyond;
    }

    private record Cut(List<UUID> chain, int step, int firstIndex, UUID keptEnd, UUID firstOut) {
    }

    private static Cut cutOf(ServerLevel level, UUID trainId, int afterCar) {
        SableTrain loco = SableTrainRegistry.get(trainId);
        Vec3 nose = loco == null ? null : SableTrainDriver.axleNose(loco);
        if (loco == null || nose == null || afterCar < 1) {
            return null;
        }
        List<UUID> chain = TrainConsist.orderedChain(level, loco.car().subLevelId());
        int at = chain.indexOf(loco.car().subLevelId());
        if (at < 0) {
            return null;
        }
        int step = sideBehind(level, chain, at, nose);
        int keptIndex = at + step * (afterCar - 1);
        int outIndex = keptIndex + step;
        if (step == 0 || outIndex < 0 || outIndex >= chain.size()) {
            return null;
        }
        return new Cut(chain, step, outIndex, chain.get(keptIndex), chain.get(outIndex));
    }

    private static int sideBehind(ServerLevel level, List<UUID> chain, int at, Vec3 nose) {
        boolean hasLow = at > 0;
        boolean hasHigh = at < chain.size() - 1;
        if (hasLow && hasHigh) {
            return along(level, chain.get(at), chain.get(at + 1), nose) < 0 ? 1 : -1;
        }
        if (!hasLow && !hasHigh) {
            return 0;
        }
        return hasHigh ? 1 : -1;
    }

    private static double along(ServerLevel level, UUID from, UUID to, Vec3 nose) {
        ServerSubLevel a = SubLevelData.resolve(level, from);
        ServerSubLevel b = SubLevelData.resolve(level, to);
        if (a == null || b == null) {
            return 0.0;
        }
        Vector3d delta = new Vector3d(b.logicalPose().position()).sub(a.logicalPose().position());
        return delta.x * nose.x + delta.y * nose.y + delta.z * nose.z;
    }
}
