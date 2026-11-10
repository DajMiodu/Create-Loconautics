package com.lycoris.loconautics.common.impact;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.common.sable.SubLevelData;
import com.lycoris.loconautics.trainphysics.TrainConsist;
import com.lycoris.loconautics.trainphysics.state.SableTrainRegistry;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.server.level.ServerLevel;
import org.joml.Vector3d;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class ImpactSpread {

    private static final double FALLOFF_PER_CAR = 0.7;
    private static final double MIN_SPEED_CHANGE = 0.05;

    private ImpactSpread() {
    }

    private record Hop(UUID id, double scale) {
    }

    public static void spread(ServerLevel level, SableTrain struck, Vector3d deltaV, boolean skipStruck) {
        if (struck.car() == null) {
            return;
        }
        UUID startId = struck.car().subLevelId();
        LoconauticsConstants.LOGGER.info("[issue283] spread from {} dv={} neighbours={} skipStruck={}", startId,
                String.format(java.util.Locale.ROOT, "%.2f", deltaV.length()), TrainConsist.neighbours(level, startId),
                skipStruck);
        Set<UUID> visited = new HashSet<>();
        Deque<Hop> queue = new ArrayDeque<>();
        queue.add(new Hop(startId, 1.0));
        while (!queue.isEmpty()) {
            Hop hop = queue.poll();
            if (!visited.add(hop.id())) {
                continue;
            }
            Vector3d carDeltaV = new Vector3d(deltaV).mul(hop.scale());
            if (carDeltaV.length() < MIN_SPEED_CHANGE) {
                continue;
            }
            if (!(skipStruck && hop.id().equals(startId))) {
                hit(level, hop.id(), carDeltaV);
            }
            for (UUID next : TrainConsist.neighbours(level, hop.id())) {
                if (!visited.contains(next)) {
                    queue.add(new Hop(next, hop.scale() * FALLOFF_PER_CAR));
                }
            }
        }
    }

    private static void hit(ServerLevel level, UUID id, Vector3d deltaV) {
        SableTrain car = SableTrainRegistry.bySubLevel(id);
        ServerSubLevel sub = SubLevelData.resolve(level, id);
        if (car == null || sub == null || car.isGhost() || car.car() == null) {
            LoconauticsConstants.LOGGER.info("[issue283] car {} skipped train={} sub={} ghost={}", id, car != null,
                    sub != null, car != null && car.isGhost());
            return;
        }
        double change = deltaV.length();
        LoconauticsConstants.LOGGER.info("[issue283] car {} takes {} b/s derails={}", id,
                String.format(java.util.Locale.ROOT, "%.2f", change), TrainImpacts.derails(change));
        if (TrainImpacts.derails(change)) {
            TrainImpacts.throwOff(car, sub, deltaV);
        } else if (car.isDerailed() || car.isPhysics()) {
            TrainKnockback.applyKnockbackNow(car, deltaV);
        } else {
            TrainKnockback.applyRailRecoil(car, deltaV);
        }
    }
}
