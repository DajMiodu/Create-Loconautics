package com.lycoris.loconautics.trainphysics.debug;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.common.sable.SubLevelOverlap;
import com.lycoris.loconautics.trainphysics.state.SableTrainRegistry;
import com.lycoris.loconautics.trainphysics.wheels.GhostWheelBodies;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.physics.mass.MassData;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static com.lycoris.loconautics.foundation.LogFormat.f;
import static com.lycoris.loconautics.foundation.LogFormat.shortId;

// ridingbody is temporary, it goes with the fix for a heavy body sliding off a car
public final class RidingBodyTrace {

    public static final boolean TRACE = true;
    private static final long EVERY_TICKS = 10L;
    private static final Vector3d RESTING_PROBE = new Vector3d(0.0, -0.1, 0.0);

    private record Sink(double sum, double max, int lifts, int steps) {
        Sink add(double sink) {
            return new Sink(sum + sink, Math.max(max, sink), lifts + (sink > 0.0 ? 1 : 0), steps + 1);
        }
    }

    private static final Map<UUID, Sink> SINKS = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> LAST = new ConcurrentHashMap<>();

    private RidingBodyTrace() {
    }

    public static void noteSink(UUID car, double sink) {
        if (TRACE) {
            SINKS.merge(car, new Sink(sink, sink, sink > 0.0 ? 1 : 0, 1), (old, next) -> old.add(sink));
        }
    }

    public static void trace(ServerSubLevel car, String glueMode, double leanDegrees, long now) {
        if (!TRACE) {
            return;
        }
        Long last = LAST.get(car.getUniqueId());
        if (last != null && now - last < EVERY_TICKS) {
            return;
        }
        LAST.put(car.getUniqueId(), now);
        Sink sink = SINKS.remove(car.getUniqueId());
        ServerSubLevelContainer container = SubLevelContainer.getContainer(car.getLevel());
        RigidBodyHandle carHandle = RigidBodyHandle.of(car);
        MassData carMass = car.getMassTracker();
        if (container == null || carHandle == null || carMass == null || carMass.isInvalid()) {
            return;
        }
        Pose3d carPose = car.logicalPose();
        Vector3d carCenter = carPose.transformPosition(new Vector3d(carMass.getCenterOfMass()), new Vector3d());
        Vector3d carLinear = carHandle.getLinearVelocity(new Vector3d());
        Vector3d carSpin = carHandle.getAngularVelocity(new Vector3d());
        Quaterniond toCar = new Quaterniond(carPose.orientation()).conjugate();
        for (ServerSubLevel rider : container.getAllSubLevels()) {
            if (rider == car || rider.isRemoved() || SableTrainRegistry.bySubLevel(rider.getUniqueId()) != null
                    || GhostWheelBodies.isWheelBody(rider.getUniqueId())) {
                continue;
            }
            SubLevelOverlap.Hit hit = SubLevelOverlap.test(car, rider, new Vector3d(), RESTING_PROBE);
            MassData riderMass = rider.getMassTracker();
            RigidBodyHandle riderHandle = RigidBodyHandle.of(rider);
            if (hit == null || riderMass == null || riderMass.isInvalid() || riderHandle == null) {
                continue;
            }
            Vector3d riderCenter = rider.logicalPose().transformPosition(new Vector3d(riderMass.getCenterOfMass()),
                    new Vector3d());
            Vector3d carAtRider = new Vector3d(carSpin).cross(new Vector3d(riderCenter).sub(carCenter)).add(carLinear);
            Vector3d slide = toCar.transform(riderHandle.getLinearVelocity(new Vector3d()).sub(carAtRider));
            Vector3d onCar = carPose.transformPositionInverse(riderCenter, new Vector3d());
            LoconauticsConstants.LOGGER.info(
                    "[ridingbody] car={} rider={} riderMass={} carMass={} slideInCarFrame=({}, {}, {}) riderAtPlot=({}, {}, {}) "
                            + "depth={} axis={} glue={} lean={} deg sink sum={} max={} lifts={}/{} riderSpin={}",
                    shortId(car), shortId(rider), f(riderMass.getMass()), f(carMass.getMass()), f(slide.x), f(slide.y),
                    f(slide.z), f(onCar.x), f(onCar.y), f(onCar.z), f(hit.depth()), hit.axis(), glueMode,
                    f(leanDegrees), sink == null ? "none" : f(sink.sum()), sink == null ? "none" : f(sink.max()),
                    sink == null ? 0 : sink.lifts(), sink == null ? 0 : sink.steps(),
                    f(riderHandle.getAngularVelocity(new Vector3d()).length()));
        }
    }

    public static void retain(java.util.Set<UUID> live) {
        SINKS.keySet().retainAll(live);
        LAST.keySet().retainAll(live);
    }
}
