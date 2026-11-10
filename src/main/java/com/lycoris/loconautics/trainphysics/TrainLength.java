package com.lycoris.loconautics.trainphysics;

import com.lycoris.loconautics.common.sable.SubLevelBlocks;
import com.lycoris.loconautics.common.sable.SubLevelData;
import com.lycoris.loconautics.content.knuckle.KnuckleBlockEntity;
import com.lycoris.loconautics.content.knuckle.KnuckleTuning;
import com.lycoris.loconautics.trainphysics.drive.SableTrainDriver;
import com.lycoris.loconautics.trainphysics.drive.TrainRailAdvance;
import com.lycoris.loconautics.trainphysics.spawn.ArticulationLinks;
import com.lycoris.loconautics.trainphysics.spacing.CarExtent;
import com.lycoris.loconautics.trainphysics.spawn.SableTrainSpawner;
import com.lycoris.loconautics.trainphysics.state.SableTrainRegistry;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public final class TrainLength {

    public static final double COUPLING_GAP = com.lycoris.loconautics.content.knuckle.KnuckleTuning.REST_LENGTH - 2.0;

    public record BogeyPlace(UUID subLevelId, int carIndex, int bogeyIndex, BlockPos localPos,
                             double offset, Vector3d world) {}

    private TrainLength() {
    }

    public static List<BogeyPlace> places(ServerLevel level, UUID anyId) {
        List<BogeyPlace> walk = collect(level, anyId);
        if (noseLast(level, anyId, walk)) {
            Collections.reverse(walk);
        }
        List<BogeyPlace> out = new ArrayList<>(walk.size());
        for (BogeyPlace place : walk) {
            out.add(new BogeyPlace(place.subLevelId(), place.carIndex(), place.bogeyIndex(), place.localPos(),
                    reach(out, place.world()), place.world()));
        }
        return out;
    }

    public static double consistLength(ServerLevel level, UUID anyId) {
        if (level == null || anyId == null) {
            return 0.0;
        }
        List<SableTrain> cars = new ArrayList<>();
        for (UUID id : TrainConsist.orderedChain(level, anyId)) {
            SableTrain train = SableTrainRegistry.bySubLevel(id);
            if (train != null && train.car() != null) {
                cars.add(train);
            }
        }
        if (cars.isEmpty()) {
            return 0.0;
        }
        double total = endReach(level, cars, true) + endReach(level, cars, false);
        for (int i = 1; i < cars.size(); i++) {
            total += centerSpacing(level, cars.get(i - 1), cars.get(i),
                    carLength(level, cars.get(i - 1)) / 2.0, carLength(level, cars.get(i)) / 2.0);
        }
        return cars.size() == 1 ? carLength(level, cars.get(0)) : total;
    }

    public static double endReach(ServerLevel level, List<SableTrain> cars, boolean first) {
        SableTrain car = cars.get(first ? 0 : cars.size() - 1);
        double half = carLength(level, car) / 2.0;
        if (cars.size() < 2) {
            return half;
        }
        double hinge = hingeOffset(level, car, cars.get(first ? 1 : cars.size() - 2));
        double[] reach = reaches(level, car);
        if (Double.isNaN(hinge) || reach == null) {
            return half;
        }
        return Math.max(reach[2] / 2.0, hinge > 0.0 ? reach[1] : reach[0]);
    }

    public static double hingeOffset(ServerLevel level, SableTrain car, SableTrain neighbour) {
        ServerSubLevel sub = SubLevelData.resolve(level, car.car().subLevelId());
        if (sub == null || car.car().bogeys().isEmpty()) {
            return Double.NaN;
        }
        for (KnuckleBlockEntity knuckle : SubLevelBlocks.findAll(sub, KnuckleBlockEntity.class)) {
            if (knuckle.isConnected() && neighbour.car().subLevelId().equals(knuckle.getPartnerSubLevelId())) {
                Vector3d axis = lengthAxis(car.car(), car.car().bogeys());
                Vector3d mid = bogeyMid(sub, car.car().bogeys());
                Vector3d hinge = knuckle.hingePlot();
                return (hinge.x - mid.x) * axis.x + (hinge.z - mid.z) * axis.z;
            }
        }
        return Double.NaN;
    }

    private static Vector3d bogeyMid(ServerSubLevel sub, List<SableTrain.Bogey> bogeys) {
        BlockPos center = sub.getPlot().getCenterBlock();
        Vector3d mid = new Vector3d();
        for (SableTrain.Bogey bogey : bogeys) {
            mid.add(center.getX() + bogey.localPos().getX() + 0.5, 0.0, center.getZ() + bogey.localPos().getZ() + 0.5);
        }
        return mid.div(bogeys.size());
    }

    public static double centerSpacing(ServerLevel level, SableTrain prev, SableTrain car, double prevHalf, double half) {
        ServerSubLevel subA = SubLevelData.resolve(level, prev.car().subLevelId());
        ServerSubLevel subB = SubLevelData.resolve(level, car.car().subLevelId());
        if (subA != null && subB != null && ArticulationLinks.hinged(level, prev.car().subLevelId(), car.car().subLevelId())) {
            return SableTrainSpawner.bogeyAnchorWorld(subA, prev.car())
                    .distance(SableTrainSpawner.bogeyAnchorWorld(subB, car.car()));
        }
        double fromPrev = hingeOffset(level, prev, car);
        double fromCar = hingeOffset(level, car, prev);
        if (Double.isNaN(fromPrev) || Double.isNaN(fromCar)) {
            double measured = railMidGap(prev, car);
            return Double.isNaN(measured) ? prevHalf + COUPLING_GAP + half : measured;
        }
        return Math.abs(fromPrev) + KnuckleTuning.REST_LENGTH + Math.abs(fromCar);
    }

    private static double railMidGap(SableTrain a, SableTrain b) {
        Vector3d[] first = TrainRailAdvance.bogeyReference(null, a.car());
        Vector3d[] second = TrainRailAdvance.bogeyReference(null, b.car());
        return first == null || second == null ? Double.NaN : first[0].distance(second[0]);
    }

    public static double rake(List<Double> carLengths) {
        double total = 0.0;
        for (double length : carLengths) {
            total += length;
        }
        return carLengths.isEmpty() ? 0.0 : total + COUPLING_GAP * (carLengths.size() - 1);
    }

    public static double carLength(ServerLevel level, SableTrain train) {
        CarExtent extent = extentOf(level, train);
        if (extent == null) {
            return train.car().carriage() == null ? 1.0 : Math.max(1.0, train.car().carriage().bogeySpacing());
        }
        return Math.max(spacingOf(train), 2.0 * extent.halfLength());
    }

    private static double spacingOf(SableTrain train) {
        return train.car().carriage() == null ? 1.0 : Math.max(1.0, train.car().carriage().bogeySpacing());
    }

    public static CarExtent extentOf(ServerLevel level, SableTrain train) {
        CarExtent live = measure(level, train);
        return live != null ? live : train.extent();
    }

    public static CarExtent measure(ServerLevel level, SableTrain train) {
        SableTrain.Car car = train.car();
        ServerSubLevel sub = SubLevelData.resolve(level, car.subLevelId());
        if (sub == null) {
            return null;
        }
        List<SableTrain.Bogey> bogeys = car.bogeys();
        var box = sub.getPlot().getBoundingBox();
        boolean bare = bogeys.isEmpty();
        Vector3d axis = bare ? new Vector3d(1.0, 0.0, 0.0) : lengthAxis(car, bogeys);
        Vector3d mid = bare ? new Vector3d((box.minX() + box.maxX() + 1.0) * 0.5, 0.0,
                (box.minZ() + box.maxZ() + 1.0) * 0.5) : bogeyMid(sub, bogeys);
        double forward = 0.0;
        double backward = 0.0;
        double left = -Double.MAX_VALUE;
        double right = -Double.MAX_VALUE;
        for (int i = 0; i < 4; i++) {
            double x = (i & 1) == 0 ? box.minX() : box.maxX() + 1.0;
            double z = (i & 2) == 0 ? box.minZ() : box.maxZ() + 1.0;
            double along = (x - mid.x) * axis.x + (z - mid.z) * axis.z;
            double across = -(x - mid.x) * axis.z + (z - mid.z) * axis.x;
            forward = Math.max(forward, along);
            backward = Math.max(backward, -along);
            left = Math.max(left, across);
            right = Math.max(right, -across);
        }
        if (bare) {
            forward = 0.0;
            backward = 0.0;
        }
        return new CarExtent(forward, backward, left, right, Math.max(1.0, box.maxY() + 1.0 - box.minY()));
    }

    private static double[] reaches(ServerLevel level, SableTrain train) {
        CarExtent extent = extentOf(level, train);
        if (extent == null) {
            return null;
        }
        return new double[]{extent.forward(), extent.backward(), spacingOf(train)};
    }

    public static double carWidth(ServerLevel level, SableTrain train) {
        CarExtent extent = extentOf(level, train);
        return extent == null ? 1.0 : Math.max(1.0, extent.width());
    }

    public static double carHeight(ServerLevel level, SableTrain train) {
        CarExtent extent = extentOf(level, train);
        return extent == null ? 1.0 : Math.max(1.0, extent.height());
    }

    public static double consistWidth(ServerLevel level, UUID anyId) {
        double widest = 0.0;
        for (SableTrain car : carsOf(level, anyId)) {
            widest = Math.max(widest, carWidth(level, car));
        }
        return widest;
    }

    public static double consistHeight(ServerLevel level, UUID anyId) {
        double tallest = 0.0;
        for (SableTrain car : carsOf(level, anyId)) {
            tallest = Math.max(tallest, carHeight(level, car));
        }
        return tallest;
    }

    private static List<SableTrain> carsOf(ServerLevel level, UUID anyId) {
        List<SableTrain> cars = new ArrayList<>();
        if (level == null || anyId == null) {
            return cars;
        }
        for (UUID id : TrainConsist.orderedChain(level, anyId)) {
            SableTrain train = SableTrainRegistry.bySubLevel(id);
            if (train != null && train.car() != null) {
                cars.add(train);
            }
        }
        return cars;
    }

    // low and high reach of the coupled plot boxes, so overhang is measured and never guessed
    public static double[] reachAlong(ServerLevel level, UUID anyId, Vec3 axis) {
        Vec3 unit = axis.normalize();
        if (unit.lengthSqr() < 1.0e-9) {
            return null;
        }
        double low = Double.MAX_VALUE;
        double high = -Double.MAX_VALUE;
        for (SableTrain car : carsOf(level, anyId)) {
            ServerSubLevel sub = SubLevelData.resolve(level, car.car().subLevelId());
            if (sub == null) {
                continue;
            }
            var box = sub.getPlot().getBoundingBox();
            for (int corner = 0; corner < 8; corner++) {
                Vector3d at = SubLevelData.worldOf(sub, new Vector3d(
                        (corner & 1) == 0 ? box.minX() : box.maxX() + 1.0,
                        (corner & 2) == 0 ? box.minY() : box.maxY() + 1.0,
                        (corner & 4) == 0 ? box.minZ() : box.maxZ() + 1.0));
                double along = at.x * unit.x + at.y * unit.y + at.z * unit.z;
                low = Math.min(low, along);
                high = Math.max(high, along);
            }
        }
        return high < low ? null : new double[]{low, high};
    }

    public static double toBodyMiddle(ServerLevel level, UUID anyId, Vec3 point, Vec3 axis) {
        double[] reach = reachAlong(level, anyId, axis);
        return reach == null ? Double.NaN : Math.abs(point.dot(axis.normalize()) - (reach[0] + reach[1]) * 0.5);
    }

    public static Vector3d lengthAxis(SableTrain.Car car, List<SableTrain.Bogey> bogeys) {
        Vector3d reference = car.carriage() == null ? null : car.carriage().referenceForward();
        if (reference != null) {
            Vector3d u = new Vector3d(reference.x, 0.0, reference.z);
            if (u.lengthSquared() > 1.0e-9) {
                return u.normalize();
            }
        }
        BlockPos a = bogeys.get(0).localPos();
        BlockPos b = bogeys.get(bogeys.size() - 1).localPos();
        Vector3d u = new Vector3d(b.getX() - a.getX(), 0.0, b.getZ() - a.getZ());
        if (u.lengthSquared() > 1.0e-9) {
            return u.normalize();
        }
        return new Vector3d(1.0, 0.0, 0.0);
    }

    public static List<SableTrain.Bogey> noseToTail(SableTrain train, ServerSubLevel sub, Vector3d ahead) {
        List<SableTrain.Bogey> ordered = new ArrayList<>(train.car().bogeys());
        if (ordered.size() < 2) {
            return ordered;
        }
        if (ahead != null) {
            ordered.sort(Comparator.comparingDouble(b -> {
                Vector3d w = worldOf(sub, b);
                return w == null ? Double.MAX_VALUE : ahead.distanceSquared(w);
            }));
            return ordered;
        }
        Vector3d axis = noseAxis(train, sub, ordered);
        if (axis == null) {
            Collections.reverse(ordered);
            return ordered;
        }
        ordered.sort(Comparator.comparingDouble(b -> {
            Vector3d w = worldOf(sub, b);
            return w == null ? Double.MAX_VALUE : -axis.dot(w);
        }));
        return ordered;
    }

    public static Vector3d worldOf(ServerSubLevel sub, SableTrain.Bogey bogey) {
        Vector3d rail = bogey.rail() == null ? null : bogey.rail().center();
        return rail != null ? rail : SubLevelData.worldOfOffset(sub, bogey.localPos());
    }

    private static List<BogeyPlace> collect(ServerLevel level, UUID anyId) {
        List<BogeyPlace> out = new ArrayList<>();
        if (level == null || anyId == null) {
            return out;
        }
        List<UUID> chain = TrainConsist.orderedChain(level, anyId);
        for (int carIndex = 0; carIndex < chain.size(); carIndex++) {
            UUID id = chain.get(carIndex);
            SableTrain train = SableTrainRegistry.bySubLevel(id);
            ServerSubLevel sub = SubLevelData.resolve(level, id);
            if (train == null || train.car() == null || sub == null) {
                continue;
            }
            List<SableTrain.Bogey> ordered = noseToTail(train, sub,
                    out.isEmpty() ? null : out.get(out.size() - 1).world());
            for (int i = 0; i < ordered.size(); i++) {
                Vector3d here = worldOf(sub, ordered.get(i));
                if (here != null) {
                    out.add(new BogeyPlace(id, carIndex, i, ordered.get(i).localPos(), 0.0, here));
                }
            }
        }
        return out;
    }

    private static boolean noseLast(ServerLevel level, UUID anyId, List<BogeyPlace> walk) {
        if (walk.size() < 2) {
            return false;
        }
        Vec3 nose = chainAxleNose(anyId, walk);
        if (nose == null) {
            return false;
        }
        Vector3d span = new Vector3d(walk.get(walk.size() - 1).world()).sub(walk.get(0).world());
        return span.dot(nose.x, nose.y, nose.z) > 0.0;
    }

    // only a car with a bearing axle knows which end is the front, and a hauled wagon asking would otherwise get no answer at all
    private static Vec3 chainAxleNose(UUID anyId, List<BogeyPlace> walk) {
        SableTrain caller = SableTrainRegistry.bySubLevel(anyId);
        Vec3 nose = caller == null ? null : SableTrainDriver.axleNose(caller);
        if (nose != null) {
            return nose;
        }
        for (BogeyPlace place : walk) {
            SableTrain owner = SableTrainRegistry.bySubLevel(place.subLevelId());
            Vec3 axle = owner == null ? null : SableTrainDriver.axleNose(owner);
            if (axle != null) {
                return axle;
            }
        }
        return null;
    }

    private static double reach(List<BogeyPlace> walked, Vector3d here) {
        if (walked.isEmpty()) {
            return 0.0;
        }
        BogeyPlace last = walked.get(walked.size() - 1);
        return last.offset() + last.world().distance(here);
    }

    private static Vector3d noseAxis(SableTrain train, ServerSubLevel sub, List<SableTrain.Bogey> bogeys) {
        Vec3 nose = SableTrainDriver.axleNose(train);
        if (nose != null) {
            return new Vector3d(nose.x, nose.y, nose.z);
        }
        Vector3d rear = worldOf(sub, bogeys.get(0));
        Vector3d front = worldOf(sub, bogeys.get(bogeys.size() - 1));
        if (rear == null || front == null) {
            return null;
        }
        Vector3d chord = new Vector3d(front).sub(rear);
        return chord.lengthSquared() < 1.0e-9 ? null : chord.normalize();
    }
}
