package com.lycoris.loconautics.trainphysics.station;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.trainphysics.TrainConsist;
import com.lycoris.loconautics.trainphysics.drive.SableTrainDriver;
import com.lycoris.loconautics.trainphysics.rail.RailCarriage;
import com.lycoris.loconautics.trainphysics.rail.RailSeams;
import com.lycoris.loconautics.trainphysics.state.SableTrainRegistry;
import com.simibubi.create.content.trains.entity.TravellingPoint;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class ConsistNose {

    public record Nose(SableTrain car, RailCarriage rail, TravellingPoint point) {}

    // stored against one cars chord so a turntable cannot flip it
    private record Memory(UUID owner, UUID ref, double sign, boolean pinned) {}

    private record Heading(SableTrain ref, Vec3 dir, double refSign, String source) {}

    private static final int PRUNE_TICKS = 200;

    private static final Map<UUID, Nose> CACHE = new HashMap<>();
    private static final Map<UUID, Nose> TAILS = new HashMap<>();
    private static final Map<UUID, Memory> MEMORY = new HashMap<>();
    private static final Map<UUID, String> LOGGED = new HashMap<>();
    private static final Map<String, Integer> RELATIONS = new HashMap<>();
    private static int cacheTick = Integer.MIN_VALUE;

    public static void clearAll() {
        CACHE.clear();
        TAILS.clear();
        MEMORY.clear();
        LOGGED.clear();
        RELATIONS.clear();
    }

    // the schedule drives this leg toward travel, so the end that leads is whichever end is that way
    public static void pinTravel(SableTrain train, Vec3 travel) {
        SableTrain ref = train == null || travel == null ? null : of(train).car();
        if (ref == null || SableTrainDriver.scheduleDriveTangent(ref) == null) {
            return;
        }
        pin(train, ref, signAgainst(travel, ref));
    }

    // planning always reasons from the cab, whatever the train happens to be doing right now
    public static void pinCab(SableTrain train) {
        Vec3 chord = train == null ? null : SableTrainDriver.scheduleDriveTangent(train);
        Vec3 cab = train == null ? null : SableTrainDriver.headingNose(train);
        if (cab == null) {
            cab = train == null ? null : SableTrainDriver.driverLook(train);
        }
        if (chord == null || cab == null) {
            forget(train);
            return;
        }
        pin(train, train, chord.dot(cab) < 0.0 ? -1.0 : 1.0);
    }

    public static void forget(SableTrain train) {
        UUID id = idOf(train);
        if (id == null) {
            return;
        }
        forget(id);
    }

    private static void forget(UUID carId) {
        boolean removed = MEMORY.values().removeIf(memory -> memory.pinned() && memory.owner().equals(carId));
        if (removed) {
            CACHE.clear();
            TAILS.clear();
        }
    }

    private static void pin(SableTrain train, SableTrain ref, double sign) {
        UUID id = idOf(train);
        ServerLevel level = train.level();
        if (id == null || level == null) {
            return;
        }
        Memory memory = new Memory(id, idOf(ref), sign, true);
        store(consistCars(train), memory);
        MEMORY.put(id, memory);
        CACHE.clear();
        TAILS.clear();
    }

    private static double signAgainst(Vec3 dir, SableTrain car) {
        Vec3 chord = SableTrainDriver.scheduleDriveTangent(car);
        return chord == null || chord.dot(dir) >= 0.0 ? 1.0 : -1.0;
    }

    private static void store(List<SableTrain> cars, Memory memory) {
        for (SableTrain car : cars) {
            UUID carId = car.car().subLevelId();
            Memory old = MEMORY.get(carId);
            if (old != null && old.pinned() && !memory.pinned()) {
                continue;
            }
            MEMORY.put(carId, memory);
        }
    }

    private static UUID idOf(SableTrain train) {
        return train == null || train.car() == null ? null : train.car().subLevelId();
    }

    public static Nose of(SableTrain train) {
        UUID id = idOf(train);
        if (id == null) {
            return local(train);
        }
        refreshCaches();
        return CACHE.computeIfAbsent(id, key -> {
            Nose consist = fromChain(train, false);
            if (consist != null) {
                return consist;
            }
            return local(train);
        });
    }

    // the end that trails, a train backing in lands on this one
    public static Nose tail(SableTrain train) {
        UUID id = idOf(train);
        if (id == null) {
            return of(train);
        }
        refreshCaches();
        Nose tail = TAILS.computeIfAbsent(id, key -> fromChain(train, true));
        return tail != null ? tail : of(train);
    }

    private static void refreshCaches() {
        if (cacheTick == TrainStations.stationTickCounter) {
            return;
        }
        cacheTick = TrainStations.stationTickCounter;
        CACHE.clear();
        TAILS.clear();
        RELATIONS.clear();
        if (cacheTick % PRUNE_TICKS == 0) {
            MEMORY.keySet().removeIf(carId -> SableTrainRegistry.bySubLevel(carId) == null);
            LOGGED.keySet().removeIf(carId -> SableTrainRegistry.bySubLevel(carId) == null);
        }
    }

    public static TravellingPoint point(SableTrain train) {
        return of(train).point();
    }

    public static RailCarriage rail(SableTrain train) {
        return of(train).rail();
    }

    // positive when heading along the nose points travel sense, negative against, zero unknown
    public static int senseOf(SableTrain train) {
        ServerLevel level = train.level();
        UUID id = idOf(train);
        int onNose = level == null || id == null ? 0 : tangentSign(of(train));
        Heading heading = onNose == 0 ? null : heading(consistCars(train));
        if (heading == null) {
            return 0;
        }
        return (int) heading.refSign() * relation(level, heading.ref(), of(train).car()) * onNose;
    }

    // a nose sense times this is the same direction in the speed sign of this car, and back again
    public static int frameFlip(SableTrain train) {
        return train == null || train.car() == null ? 1 : frameFlip(train, of(train));
    }

    public static int frameFlip(SableTrain train, Nose nose) {
        ServerLevel level = train == null || train.car() == null ? null : train.level();
        int onNose = level == null ? 0 : tangentSign(nose);
        return onNose == 0 ? 1 : relation(level, train, nose.car()) * onNose;
    }

    // taken on the nose car itself, so a curve between cars cannot change it
    private static int tangentSign(Nose nose) {
        Vec3 tangent = noseTangent(nose);
        Vec3 chord = tangent == null ? null : SableTrainDriver.scheduleDriveTangent(nose.car());
        if (chord == null) {
            return 0;
        }
        return chord.x * tangent.x + chord.z * tangent.z < 0.0 ? -1 : 1;
    }

    // chords of two cars agree or oppose, read one coupling at a time so a curve cannot fool it
    private static int relation(ServerLevel level, SableTrain from, SableTrain to) {
        UUID fromId = idOf(from);
        UUID toId = idOf(to);
        if (fromId == null || toId == null || fromId.equals(toId)) {
            return 1;
        }
        return RELATIONS.computeIfAbsent(fromId + ">" + toId, key -> walkRelation(level, fromId, toId));
    }

    private static int walkRelation(ServerLevel level, UUID fromId, UUID toId) {
        List<UUID> chain = TrainConsist.orderedChain(level, fromId);
        int start = chain.indexOf(fromId);
        int end = chain.indexOf(toId);
        SableTrain first = SableTrainRegistry.bySubLevel(fromId);
        SableTrain last = SableTrainRegistry.bySubLevel(toId);
        Vec3 previous = first == null ? null : SableTrainDriver.scheduleDriveTangent(first);
        if (start < 0 || end < 0) {
            return previous == null || last == null || signAgainst(previous, last) > 0.0 ? 1 : -1;
        }
        int step = end > start ? 1 : -1;
        int sign = 1;
        for (int i = start + step; i >= 0 && i < chain.size(); i += step) {
            SableTrain car = SableTrainRegistry.bySubLevel(chain.get(i));
            Vec3 chord = car == null ? null : SableTrainDriver.scheduleDriveTangent(car);
            if (chord != null && previous != null && previous.dot(chord) < 0.0) {
                sign = -sign;
            }
            previous = chord != null ? chord : previous;
            if (i == end) {
                break;
            }
        }
        return sign;
    }

    public static Vec3 wheelPos(SableTrain train) {
        Nose nose = of(train);
        if (nose.rail() == null || nose.point() == null) {
            return null;
        }
        return nose.point() == nose.rail().leading() ? nose.rail().leadingPos() : nose.rail().trailingPos();
    }

    public static Vec3 noseTangent(SableTrain train) {
        return train == null || train.car() == null ? null : noseTangent(of(train));
    }

    public static Vec3 noseTangent(Nose nose) {
        TravellingPoint point = nose.point();
        if (point == null || point.edge == null) {
            return null;
        }
        return RailSeams.worldDirOf(point.edge.node1.getLocation(), point.edge.getDirectionAt(point.position));
    }

    private static Nose fromChain(SableTrain train, boolean tail) {
        ServerLevel level = train.level();
        UUID id = idOf(train);
        if (level == null || id == null) {
            return null;
        }
        List<SableTrain> cars = consistCars(train);
        Heading heading = heading(cars);
        if (heading == null) {
            return null;
        }
        Vec3 ahead = tail ? heading.dir().scale(-1.0) : heading.dir();
        Nose chained = frontOfChain(level, id, cars, heading, ahead);
        if (chained != null) {
            if (!tail) {
                trace(id, chained, heading, "chain");
            }
            return chained;
        }
        SableTrain bestCar = null;
        SableTrain.Bogey bestBogey = null;
        double bestReach = -Double.MAX_VALUE;
        List<SableTrain> ends = endCars(level, id, cars);
        for (SableTrain car : ends.isEmpty() ? cars : ends) {
            for (SableTrain.Bogey bogey : car.car().bogeys()) {
                if (!scoutable(bogey.rail())) {
                    continue;
                }
                Vector3d center = bogey.rail().center();
                double reach = center.x * ahead.x + center.y * ahead.y + center.z * ahead.z;
                if (reach > bestReach) {
                    bestReach = reach;
                    bestBogey = bogey;
                    bestCar = car;
                }
            }
        }
        if (bestBogey == null) {
            return null;
        }
        Nose projected = new Nose(bestCar, bestBogey.rail(), outerPoint(bestBogey.rail(), ahead));
        if (!tail) {
            trace(id, projected, heading, "projection");
        }
        return projected;
    }

    // nose2c prints only when the picked end or the heading source changes, so it stays small
    private static void trace(UUID id, Nose nose, Heading heading, String how) {
        String line = how + " " + heading.source() + " nose car " + shortId(nose.car().car().subLevelId())
                + " ref car " + shortId(heading.ref().car().subLevelId())
                + " dir " + String.format(Locale.ROOT, "%.2f,%.2f,%.2f",
                        heading.dir().x, heading.dir().y, heading.dir().z);
        String key = how + heading.source() + nose.car().car().subLevelId() + nose.point().node1.getLocation()
                + signKey(heading.dir());
        if (!key.equals(LOGGED.put(id, key))) {
            LoconauticsConstants.LOGGER.info("[nose2c] consist {} {}", shortId(id), line);
        }
    }

    private static String signKey(Vec3 dir) {
        return (dir.x < 0 ? "-" : "+") + (dir.z < 0 ? "-" : "+");
    }

    public static String shortId(UUID id) {
        return id == null ? "none" : id.toString().substring(0, 8);
    }

    private static Heading heading(List<SableTrain> cars) {
        if (cars.isEmpty()) {
            return null;
        }
        Heading pinned = remembered(cars, true);
        if (pinned != null) {
            return pinned;
        }
        Heading moving = moving(cars);
        if (moving != null) {
            UUID refId = idOf(moving.ref());
            store(cars, new Memory(refId, refId, moving.refSign(), false));
            return moving;
        }
        SableTrain driver = driverCar(cars);
        boolean seated = driver != null && SableTrainDriver.driverRank(driver) == 0;
        if (!seated || stopping(cars)) {
            Heading kept = remembered(cars, false);
            if (kept != null) {
                return kept;
            }
        }
        return cab(cars, driver);
    }

    // a seated driver reads the cab only while standing, a landing crawl keeps the end it came in on
    private static boolean stopping(List<SableTrain> cars) {
        for (SableTrain car : cars) {
            if (car.stationState() == SableTrain.StationState.STOPPING) {
                return true;
            }
        }
        return false;
    }

    private static Heading remembered(List<SableTrain> cars, boolean pinnedOnly) {
        for (SableTrain car : sorted(cars)) {
            Memory memory = MEMORY.get(car.car().subLevelId());
            if (memory == null || pinnedOnly && !memory.pinned()) {
                continue;
            }
            SableTrain ref = carIn(cars, memory.ref());
            Vec3 chord = ref == null ? null : SableTrainDriver.scheduleDriveTangent(ref);
            if (chord != null) {
                return new Heading(ref, chord.scale(memory.sign()), memory.sign(),
                        memory.pinned() ? "pinned" : "memory");
            }
        }
        return null;
    }

    private static Heading moving(List<SableTrain> cars) {
        SableTrain fastest = null;
        for (SableTrain car : sorted(cars)) {
            if (Math.abs(car.speed()) > TrainStations.DEPART_SPEED
                    && (fastest == null || Math.abs(car.speed()) > Math.abs(fastest.speed()))
                    && SableTrainDriver.scheduleDriveTangent(car) != null) {
                fastest = car;
            }
        }
        if (fastest == null) {
            return null;
        }
        Vec3 chord = SableTrainDriver.scheduleDriveTangent(fastest);
        return new Heading(fastest, chord.scale(Math.signum(fastest.speed())), Math.signum(fastest.speed()),
                "motion");
    }

    private static Heading cab(List<SableTrain> cars, SableTrain driver) {
        Vec3 look = driver == null ? null : SableTrainDriver.driverLook(driver);
        if (look != null) {
            return new Heading(driver, look, signAgainst(look, driver), "driver");
        }
        for (SableTrain car : sorted(cars)) {
            Vec3 axle = SableTrainDriver.headingNose(car);
            if (axle != null) {
                return new Heading(car, axle, signAgainst(axle, car), "axle");
            }
        }
        return null;
    }

    // a hash set decides the car order, so ties would pick a different end from one run to the next
    private static List<SableTrain> sorted(List<SableTrain> cars) {
        List<SableTrain> ordered = new ArrayList<>(cars);
        ordered.sort(Comparator.comparing((SableTrain car) -> car.car().subLevelId()));
        return ordered;
    }

    private static SableTrain driverCar(List<SableTrain> cars) {
        SableTrain best = null;
        int bestRank = 3;
        for (SableTrain car : sorted(cars)) {
            int rank = SableTrainDriver.driverRank(car);
            if (rank < bestRank) {
                best = car;
                bestRank = rank;
            }
        }
        return best;
    }

    private static List<SableTrain> endCars(ServerLevel level, UUID id, List<SableTrain> cars) {
        List<UUID> chain = TrainConsist.orderedChain(level, id);
        if (chain.size() < 3) {
            return List.of();
        }
        SableTrain first = null;
        SableTrain last = null;
        for (SableTrain car : cars) {
            if (car.car().subLevelId().equals(chain.get(0))) {
                first = car;
            } else if (car.car().subLevelId().equals(chain.get(chain.size() - 1))) {
                last = car;
            }
        }
        return first == null || last == null ? List.of() : List.of(first, last);
    }

    // a bogey with no graph or no edge makes every station scan return nothing, forever, so it must lose the projection
    public static boolean scoutable(RailCarriage rail) {
        if (rail == null || rail.graph() == null || rail.center() == null) {
            return false;
        }
        TravellingPoint lead = rail.leading();
        TravellingPoint trail = rail.trailing();
        return (lead != null && lead.edge != null) || (trail != null && trail.edge != null);
    }

    // coupling blocks are not loaded for a ghost, so its saved member list is the only rake it has
    private static List<SableTrain> consistCars(SableTrain train) {
        boolean ghost = train.isGhost();
        java.util.Set<UUID> members = new java.util.HashSet<>();
        if (ghost) {
            members.addAll(train.consistCache());
            members.add(idOf(train));
        } else {
            members.addAll(TrainConsist.connectedSubLevels(train.level(), idOf(train)));
        }
        List<SableTrain> cars = new ArrayList<>();
        for (UUID other : members) {
            SableTrain car = SableTrainRegistry.bySubLevel(other);
            if (car != null && car.car() != null && !car.isDerailed() && (ghost || !car.isGhost())) {
                cars.add(car);
            }
        }
        return cars;
    }

    private static Nose frontOfChain(ServerLevel level, UUID id, List<SableTrain> cars, Heading heading, Vec3 look) {
        List<UUID> chain = TrainConsist.orderedChain(level, id);
        int at = chain.indexOf(heading.ref().car().subLevelId());
        if (chain.size() < 2 || at < 0) {
            return null;
        }
        Vector3d before = centerOf(carIn(cars, chain.get(Math.max(at - 1, 0))));
        Vector3d after = centerOf(carIn(cars, chain.get(Math.min(at + 1, chain.size() - 1))));
        if (before == null || after == null) {
            return null;
        }
        boolean towardLast = (after.x - before.x) * look.x + (after.y - before.y) * look.y
                + (after.z - before.z) * look.z >= 0.0;
        int step = towardLast ? -1 : 1;
        for (int i = towardLast ? chain.size() - 1 : 0; i >= 0 && i < chain.size(); i += step) {
            SableTrain end = carIn(cars, chain.get(i));
            if (end == null) {
                continue;
            }
            int inward = i + step;
            SableTrain neighbour = inward >= 0 && inward < chain.size() ? carIn(cars, chain.get(inward)) : null;
            Vector3d from = neighbour == null ? null : centerOf(neighbour);
            SableTrain.Bogey outer = outerBogey(end, from);
            return outer == null ? null : new Nose(end, outer.rail(), outerPoint(outer.rail(), look));
        }
        return null;
    }

    private static SableTrain carIn(List<SableTrain> cars, UUID id) {
        for (SableTrain car : cars) {
            if (car.car().subLevelId().equals(id)) {
                return car;
            }
        }
        return null;
    }

    private static Vector3d centerOf(SableTrain car) {
        if (car == null) {
            return null;
        }
        Vector3d sum = new Vector3d();
        int count = 0;
        for (SableTrain.Bogey bogey : car.car().bogeys()) {
            if (scoutable(bogey.rail())) {
                sum.add(bogey.rail().center());
                count++;
            }
        }
        return count == 0 ? null : sum.div(count);
    }

    private static SableTrain.Bogey outerBogey(SableTrain car, Vector3d from) {
        SableTrain.Bogey best = null;
        double bestGap = -Double.MAX_VALUE;
        for (SableTrain.Bogey bogey : car.car().bogeys()) {
            if (!scoutable(bogey.rail())) {
                continue;
            }
            double gap = from == null ? 0.0 : bogey.rail().center().distanceSquared(from);
            if (best == null || gap > bestGap) {
                best = bogey;
                bestGap = gap;
            }
        }
        return best;
    }

    private static TravellingPoint outerPoint(RailCarriage rail, Vec3 ahead) {
        TravellingPoint lead = rail.leading();
        TravellingPoint trail = rail.trailing();
        if (lead == null || lead.edge == null) {
            return trail;
        }
        if (trail == null || trail.edge == null) {
            return lead;
        }
        Vec3 leadPos = rail.leadingPos();
        Vec3 trailPos = rail.trailingPos();
        if (ahead == null || leadPos == null || trailPos == null) {
            return lead;
        }
        return leadPos.subtract(trailPos).dot(ahead) >= 0.0 ? lead : trail;
    }

    private static Nose local(SableTrain train) {
        List<SableTrain.Bogey> bogeys = train.car().bogeys();
        RailCarriage rail = bogeys.isEmpty()
                ? train.car().carriage()
                : TrainStations.frontBogey(train, bogeys).rail();
        if (!scoutable(rail)) {
            rail = train.car().carriage();
        }
        if (rail == null) {
            return new Nose(train, null, null);
        }
        Nose picked = new Nose(train, rail, outerPoint(rail, SableTrainDriver.axleNose(train)));
        traceGhostNose(train, bogeys, picked);
        return picked;
    }

    private static void traceGhostNose(SableTrain train, List<SableTrain.Bogey> bogeys, Nose picked) {
        if (!train.isGhost() || train.level() == null || picked.rail() == null || picked.point() == null
                || !com.lycoris.loconautics.foundation.debug.TurntableTrace.due("ghostnose" + train.id(),
                train.level().getGameTime(), 10)) {
            return;
        }
        StringBuilder ends = new StringBuilder();
        for (int i = 0; i < bogeys.size(); i++) {
            RailCarriage r = bogeys.get(i).rail();
            Vec3 a = r == null ? null : r.leadingPos();
            Vec3 b = r == null ? null : r.trailingPos();
            ends.append(" bogey").append(i).append(" lead z ").append(a == null ? "none" : String.format("%.2f", a.z))
                    .append(" trail z ").append(b == null ? "none" : String.format("%.2f", b.z));
        }
        Vec3 at = picked.point() == picked.rail().leading() ? picked.rail().leadingPos() : picked.rail().trailingPos();
        com.lycoris.loconautics.foundation.debug.TurntableTrace.log("ghostnose " + shortId(train.id())
                + " speed " + String.format("%.3f", train.speed()) + " picked "
                + (picked.point() == picked.rail().leading() ? "leading" : "trailing") + " of bogey "
                + bogeys.indexOf(bogeys.stream().filter(b -> b.rail() == picked.rail()).findFirst().orElse(null))
                + " at z " + (at == null ? "none" : String.format("%.2f", at.z)) + ends);
    }

    private ConsistNose() {
    }
}
