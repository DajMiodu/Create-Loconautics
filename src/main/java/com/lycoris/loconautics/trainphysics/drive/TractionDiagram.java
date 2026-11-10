package com.lycoris.loconautics.trainphysics.drive;

import com.lycoris.loconautics.trainphysics.SableTrain;
import dev.ryanhcode.sable.api.physics.force.ForceGroups;
import dev.ryanhcode.sable.api.physics.force.QueuedForceGroup;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class TractionDiagram {

    private TractionDiagram() {
    }

    static void record(ServerSubLevel sub, SableTrain train, Vector3dc localImpulse, double carMass, double consistMass) {
        Map<BlockPos, Double> driven = train.drivenBogeys();
        if (driven.isEmpty() || carMass <= 0.0 || !sub.isTrackingIndividualQueuedForces()) {
            return;
        }
        int bogeys = Math.max(driven.size(), consistDrivenBogeys(train));
        Vector3d share = new Vector3d(localImpulse).mul(consistMass / carMass / bogeys);
        QueuedForceGroup group = sub.getOrCreateQueuedForceGroup(ForceGroups.PROPULSION.get());
        BlockPos center = sub.getPlot().getCenterBlock();
        for (BlockPos bogey : driven.keySet()) {
            BlockPos at = center.offset(bogey);
            group.recordPointForce(new Vector3d(at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5), share);
        }
    }

    private static int consistDrivenBogeys(SableTrain train) {
        ConsistPower.Result result = ConsistPower.resultFor(train);
        if (result == null) {
            return 0;
        }
        Set<List<Object>> seen = new HashSet<>();
        for (ConsistPower.Axle axle : result.axles()) {
            seen.add(List.of(axle.subLevelId(), axle.bogeyLocalPos()));
        }
        return seen.size();
    }
}
