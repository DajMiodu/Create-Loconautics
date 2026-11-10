package com.lycoris.loconautics.trainphysics.debug;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.trainphysics.SableTrain.Bogey;
import com.lycoris.loconautics.trainphysics.SableTrain.Residency;
import com.lycoris.loconautics.common.sable.SubLevelData;
import com.lycoris.loconautics.trainphysics.TrainConsist;
import com.lycoris.loconautics.trainphysics.TrainLength;
import com.lycoris.loconautics.trainphysics.drive.TrainRailAdvance;
import com.lycoris.loconautics.trainphysics.state.TrainResidency;
import com.lycoris.loconautics.trainphysics.rail.RailCarriage;
import com.lycoris.loconautics.trainphysics.state.SableTrainRegistry;
import com.lycoris.loconautics.trainphysics.state.StationChunkTickets;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.simibubi.create.content.trains.entity.TravellingPoint;
import com.simibubi.create.content.trains.entity.TravellingPoint.SteerDirection;
import com.simibubi.create.content.trains.graph.TrackGraph;
import dev.ryanhcode.sable.api.SubLevelHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.system.ticket.PhysicsChunkTicketManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static com.lycoris.loconautics.foundation.LogFormat.shortId;

// temporary, tag consisttrace, follows every car of a consist that has a ghost in it
public final class GhostConsistTrace {

    private static final long LOG_PERIOD = 20L;
    private static final long RECENT_TICKS = 200L;
    private static final long SNATCH_NOTE_TICKS = 100L;
    private static final double JUMP = 0.75;
    private static final double OVERLAP_FRACTION = 0.6;
    private static final double STRETCH = 2.0;
    private static final double SPEED_SPLIT = 0.01;
    private static final double SIDE_MIN = 0.5;
    private static final long SUBSTEP_TICKS = 200L;
    private static final double ARC_STEP = 0.5;
    private static final double ARC_REACH = 40.0;
    private static final double ARC_MATCH = 0.3;
    private static boolean enabled = true;

    private record Snap(Residency residency, Vector3d rail) {}

    private record PairSnap(double gap, double side, double loadedGap, String flags) {}

    private record Step(long tick, double speedIn, double ds, String why) {}

    private static final Map<UUID, Snap> SNAPS = new HashMap<>();
    private static final Map<String, PairSnap> PAIRS = new HashMap<>();
    private static final Map<UUID, Step> STEPS = new HashMap<>();
    private static final Map<UUID, Long> RECENT = new HashMap<>();
    private static final Map<UUID, Long> SNATCH_NOTES = new HashMap<>();
    private static final Map<UUID, Long> EMBODIED_AT = new ConcurrentHashMap<>();

    private GhostConsistTrace() {
    }

    public static boolean enabled() {
        return enabled;
    }

    public static void noteStep(SableTrain train, double speedIn, double ds, boolean split, boolean end,
                                int group, SableTrain leader) {
        if (!enabled) {
            return;
        }
        String why = end ? "railhead hold for the consist" : split ? "split, target zeroed"
                : Math.abs(ds - speedIn) > 1.0e-9 ? "spacing changed it" : "free";
        why += ", consist of " + group;
        if (leader != null) {
            why += ", follows " + shortId(leader.id());
        }
        STEPS.put(train.id(), new Step(train.level().getGameTime(), speedIn, ds, why));
    }

    public static String blockerText(SableTrain train) {
        if (!enabled) {
            return shortId(train.id());
        }
        ServerLevel level = train.level();
        SableTrain.Car car = train.car();
        Vector3d rail = car == null ? null : TrainResidency.railCursor(car);
        boolean bodyLive = car != null && level != null && SubLevelData.resolve(level, car.subLevelId()) != null;
        return String.format(Locale.ROOT, "%s %s derailed=%s physics=%s bodyLive=%s rail=%s pointer=%s bodyChunk=%s "
                        + "extent=%s %s", shortId(train.id()), train.residency(), train.isDerailed(), train.isPhysics(),
                bodyLive, v3(rail), train.bodyPointer(), train.bodyChunk(), train.extent() != null,
                TrainResidency.traceFlags(train));
    }

    public static void noteSnatch(SableTrain train, String what) {
        if (!enabled) {
            return;
        }
        long now = train.level().getGameTime();
        Long last = SNATCH_NOTES.get(train.id());
        if (last != null && now - last < SNATCH_NOTE_TICKS) {
            return;
        }
        SNATCH_NOTES.put(train.id(), now);
        LoconauticsConstants.LOGGER.info("[consisttrace] snatch {} {}", what, blockerText(train));
    }

    public static void noteUnload(SableTrain train, ServerSubLevel sub) {
        if (!enabled) {
            return;
        }
        ServerLevel level = train.level();
        Vector3d body = new Vector3d(sub.logicalPose().position());
        Vector3d rail = TrainResidency.railCursor(train.car());
        long now = level.getGameTime();
        Long since = EMBODIED_AT.get(train.id());
        LoconauticsConstants.LOGGER.info("[consisttrace] UNLOAD car={} body={} rail={} bodyToRail={} speed={} target={} "
                        + "embodiedForTicks={} chunks[{}] mates: {}", shortId(train.id()), v3(body), v3(rail),
                rail == null ? "n/a" : f2(body.distance(rail)), f3(train.speed() * 20.0),
                f3(train.targetSpeed() * 20.0), since == null ? "unknown" : String.valueOf(now - since),
                chunkStates(level, sub), matesText(level, train));
    }

    public static void noteEmbodied(SableTrain train) {
        if (enabled) {
            EMBODIED_AT.put(train.id(), train.level().getGameTime());
        }
    }

    public static void noteSubstep(SableTrain train, ServerSubLevel sub, String mode, double dt, double slide) {
        if (!enabled) {
            return;
        }
        Long since = EMBODIED_AT.get(train.id());
        long now = train.level().getGameTime();
        if (since == null || now - since > SUBSTEP_TICKS) {
            return;
        }
        LoconauticsConstants.LOGGER.info("[consisttrace] substep car={} tick={} age={} {} dt={} speed={} slide={} "
                        + "stress={} settling={} target={} powered={}", shortId(sub.getUniqueId()), now, now - since, mode,
                f3(dt), f3(train.speed() * 20.0), f3(slide), f3(TrainRailAdvance.peekStress(sub.getUniqueId())),
                train.isPoseSettling(), f3(train.targetSpeed() * 20.0), train.isPowered());
    }

    public static void noteSnap(SableTrain train, double gap) {
        if (enabled) {
            LoconauticsConstants.LOGGER.info("[consisttrace] station snap train={} gap={} speed={} target={} settling={}",
                    shortId(train.id()), f3(gap), f3(train.speed() * 20.0), f3(train.targetSpeed() * 20.0),
                    train.isPoseSettling());
        }
    }

    public static void noteLever(SableTrain train, int power) {
        if (enabled) {
            LoconauticsConstants.LOGGER.info("[consisttrace] lever of stopped ghost zeroed train={} power was {} speed={} "
                    + "target={}", shortId(train.id()), power, f3(train.speed() * 20.0), f3(train.targetSpeed() * 20.0));
        }
    }

    public static void noteAnyUnload(SubLevel subLevel) {
        if (!enabled || !(subLevel instanceof ServerSubLevel sub)) {
            return;
        }
        ServerLevel level = sub.getLevel();
        StringBuilder chain = new StringBuilder();
        try {
            for (ServerSubLevel member : SubLevelHelper.getLoadingDependencyChain(sub)) {
                SableTrain owner = SableTrainRegistry.bySubLevel(member.getUniqueId());
                chain.append(shortId(member.getUniqueId())).append(owner == null ? " other" : " car").append(" at ")
                        .append(v3(new Vector3d(member.logicalPose().position()))).append(" unloaded[")
                        .append(unloadedChunks(level, member)).append("] | ");
            }
        } catch (RuntimeException e) {
            chain.append("chain read failed: ").append(e);
        }
        LoconauticsConstants.LOGGER.info("[consisttrace] UNLOADCHAIN first={} chain: {}", shortId(sub.getUniqueId()),
                chain);
    }

    private static String unloadedChunks(ServerLevel level, ServerSubLevel sub) {
        BoundingBox3dc box = sub.boundingBox();
        StringBuilder out = new StringBuilder();
        for (int cx = Mth.floor(box.minX() - 1.0) >> 4; cx <= Mth.floor(box.maxX() + 1.0) >> 4; cx++) {
            for (int cz = Mth.floor(box.minZ() - 1.0) >> 4; cz <= Mth.floor(box.maxZ() + 1.0) >> 4; cz++) {
                if (!PhysicsChunkTicketManager.isChunkLoadedEnough(level, cx, cz)) {
                    out.append(cx).append(',').append(cz).append(' ');
                }
            }
        }
        return out.toString().trim();
    }

    public static void tick() {
        if (!enabled) {
            return;
        }
        Set<UUID> seen = new HashSet<>();
        for (SableTrain train : SableTrainRegistry.all()) {
            ServerLevel level = train.level();
            if (level == null || train.car() == null || train.car().subLevelId() == null
                    || seen.contains(train.car().subLevelId())) {
                continue;
            }
            List<SableTrain> cars = chainOf(level, train);
            for (SableTrain car : cars) {
                seen.add(car.car().subLevelId());
            }
            report(level, cars);
        }
        SNAPS.keySet().removeIf(id -> SableTrainRegistry.bySubLevel(id) == null);
        STEPS.keySet().removeIf(id -> SableTrainRegistry.get(id) == null);
    }

    private static List<SableTrain> chainOf(ServerLevel level, SableTrain train) {
        List<SableTrain> cars = new ArrayList<>();
        for (UUID id : TrainConsist.orderedChain(level, train.car().subLevelId())) {
            SableTrain car = SableTrainRegistry.bySubLevel(id);
            if (car != null && car.car() != null && car.level() == level) {
                cars.add(car);
            }
        }
        if (cars.isEmpty()) {
            cars.add(train);
        }
        return cars;
    }

    private static void report(ServerLevel level, List<SableTrain> cars) {
        long now = level.getGameTime();
        String head = shortId(cars.get(0).car().subLevelId());
        boolean ghosts = false;
        for (SableTrain car : cars) {
            ghosts |= car.isGhost();
            Snap before = SNAPS.get(car.car().subLevelId());
            if (car.residency() == Residency.EMBODIED && (before == null || before.residency() != Residency.EMBODIED)) {
                EMBODIED_AT.put(car.id(), now);
            }
            if (before != null && before.residency() != car.residency()) {
                RECENT.put(car.car().subLevelId(), now);
                LoconauticsConstants.LOGGER.info("[consisttrace] TRANSITION consist={} car={} {} to {} rail={} was={} "
                                + "body={} mates: {}", head, shortId(car.car().subLevelId()), before.residency(),
                        car.residency(), v3(TrainResidency.railCursor(car.car())), v3(before.rail()),
                        bodyText(level, car), matesText(level, car));
            }
        }
        boolean recent = false;
        for (SableTrain car : cars) {
            Long at = RECENT.get(car.car().subLevelId());
            recent |= at != null && now - at <= RECENT_TICKS;
        }
        boolean log = now % LOG_PERIOD == 0L;
        watchPairs(level, cars, head, log);
        if (log && (ghosts || recent || cars.size() > 1)) {
            carLines(level, cars, head, now);
        }
        for (SableTrain car : cars) {
            Vector3d rail = TrainResidency.railCursor(car.car());
            SNAPS.put(car.car().subLevelId(), new Snap(car.residency(), rail));
        }
    }

    private static void watchPairs(ServerLevel level, List<SableTrain> cars, String head, boolean log) {
        for (int i = 1; i < cars.size(); i++) {
            SableTrain a = cars.get(i - 1);
            SableTrain b = cars.get(i);
            Vector3d railA = TrainResidency.railCursor(a.car());
            Vector3d railB = TrainResidency.railCursor(b.car());
            String key = a.car().subLevelId() + ">" + b.car().subLevelId();
            if (railA == null || railB == null) {
                if (log) {
                    LoconauticsConstants.LOGGER.info("[consisttrace] pair {} to {} has no rail point, a={} b={}",
                            shortId(a.car().subLevelId()), shortId(b.car().subLevelId()), railA != null, railB != null);
                }
                continue;
            }
            double gap = railA.distance(railB);
            double side = sideOf(a, railA, railB);
            double sizes = (TrainLength.carLength(level, a) + TrainLength.carLength(level, b)) / 2.0;
            PairSnap old = PAIRS.get(key);
            double loadedGap = old == null ? Double.NaN : old.loadedGap();
            boolean bothLoaded = !a.isGhost() && !b.isGhost();
            if (bothLoaded) {
                loadedGap = gap;
            }
            StringBuilder flags = new StringBuilder();
            if (gap < sizes * OVERLAP_FRACTION) {
                flags.append(" OVERLAP");
            }
            if (!Double.isNaN(loadedGap) && gap - loadedGap > STRETCH) {
                flags.append(" STRETCHED");
            }
            if (!Double.isNaN(loadedGap) && loadedGap - gap > STRETCH) {
                flags.append(" SQUEEZED");
            }
            if (Math.abs(a.speed() - b.speed()) > SPEED_SPLIT) {
                flags.append(" SPEEDSPLIT");
            }
            boolean jumped = old != null && Math.abs(gap - old.gap()) > JUMP;
            if (jumped) {
                flags.append(" JUMP");
            }
            boolean flipped = old != null && Math.abs(old.side()) > SIDE_MIN && Math.abs(side) > SIDE_MIN
                    && old.side() * side < 0.0;
            if (flipped) {
                flags.append(" FLIPPED_ORDER");
            }
            String flagText = flags.toString();
            boolean changed = old != null && !old.flags().equals(flagText);
            PAIRS.put(key, new PairSnap(gap, side, loadedGap, flagText));
            if (log || jumped || flipped || changed) {
                LoconauticsConstants.LOGGER.info("[consisttrace] pair consist={} {} {} to {} {} gap={} loadedGap={} "
                                + "wantAtLeast={} behindA={} speedA={} speedB={} dv={} {}{}", head,
                        shortId(a.car().subLevelId()), a.residency(), shortId(b.car().subLevelId()), b.residency(),
                        f2(gap), f2(loadedGap), f2(sizes * OVERLAP_FRACTION), f2(side), f3(a.speed() * 20.0),
                        f3(b.speed() * 20.0), f3((a.speed() - b.speed()) * 20.0), facingGap(a, b), flags);
            }
        }
    }

    // the two bogeys of a pair that face each other, straight line against the distance along the rail
    private static String facingGap(SableTrain a, SableTrain b) {
        TravellingPoint bestA = null;
        TravellingPoint bestB = null;
        RailCarriage railA = null;
        double best = Double.MAX_VALUE;
        for (Bogey first : endBogeys(a)) {
            for (Bogey second : endBogeys(b)) {
                TravellingPoint from = first.rail().leading();
                TravellingPoint to = second.rail().leading();
                if (from == null || to == null || from.edge == null || to.edge == null || first.rail().graph() == null) {
                    continue;
                }
                double chord = from.getPosition(first.rail().graph()).distanceTo(to.getPosition(first.rail().graph()));
                if (chord < best) {
                    best = chord;
                    bestA = from;
                    bestB = to;
                    railA = first.rail();
                }
            }
        }
        if (bestA == null) {
            return "facingChord=n/a facingArc=n/a";
        }
        return "facingChord=" + f2(best) + " facingArc=" + f2(arcBetween(railA, bestA, bestB));
    }

    private static List<Bogey> endBogeys(SableTrain train) {
        List<Bogey> chain = train.car().chain();
        if (chain.size() < 2) {
            return chain;
        }
        return List.of(chain.get(0), chain.get(chain.size() - 1));
    }

    // walks a copy of the point along the rail both ways until it stands on the other point, nan when out of reach
    private static double arcBetween(RailCarriage rail, TravellingPoint from, TravellingPoint to) {
        TrackGraph graph = rail.graph();
        Vec3 target = to.getPosition(graph);
        for (double direction : new double[] {1.0, -1.0}) {
            TravellingPoint scout = new TravellingPoint(from.node1, from.node2, from.edge, from.position,
                    from.upsideDown);
            double walked = 0.0;
            while (walked <= ARC_REACH) {
                if (scout.getPosition(graph).distanceTo(target) < ARC_MATCH) {
                    return walked;
                }
                double moved = Math.abs(scout.travel(graph, direction * ARC_STEP,
                        scout.steer(SteerDirection.NONE, rail.upNormal()), scout.ignoreEdgePoints(),
                        scout.ignoreTurns(), scout.ignorePortals()));
                if (moved < ARC_STEP * 0.5 || scout.edge == null) {
                    break;
                }
                walked += moved;
            }
        }
        return Double.NaN;
    }

    // a sign change means the two cars swapped places
    private static double sideOf(SableTrain a, Vector3d railA, Vector3d railB) {
        List<Bogey> chain = a.car().chain();
        if (chain.size() < 2) {
            return 0.0;
        }
        Vector3d first = chain.get(0).rail().center();
        Vector3d last = chain.get(chain.size() - 1).rail().center();
        if (first == null || last == null || first.distanceSquared(last) < 1.0e-6) {
            return 0.0;
        }
        return new Vector3d(last).sub(first).normalize().dot(new Vector3d(railB).sub(railA));
    }

    private static void carLines(ServerLevel level, List<SableTrain> cars, String head, long now) {
        for (int i = 0; i < cars.size(); i++) {
            SableTrain car = cars.get(i);
            UUID id = car.car().subLevelId();
            Vector3d rail = TrainResidency.railCursor(car.car());
            Step step = STEPS.get(id);
            ChunkPos railChunk = rail == null ? null : new ChunkPos((int) Math.floor(rail.x) >> 4,
                    (int) Math.floor(rail.z) >> 4);
            LoconauticsConstants.LOGGER.info("[consisttrace] car consist={} {}/{} car={} {} speed={} target={} cruise={} "
                            + "derailed={} physics={} accel={} powered={} following={} servo={} brakeHold={} settling={} rail={} railChunk={} footprintLoaded={} plotGrid={} {} edge={} "
                            + "step={} resid={} tickets={}", head, i + 1, cars.size(), shortId(id), car.residency(),
                    f3(car.speed() * 20.0), f3(car.targetSpeed() * 20.0), f3(car.ghostCruise() * 20.0), car.isDerailed(),
                    car.isPhysics(), f3(car.accel() * 400.0), car.isPowered(), car.isFollowing(), car.servoBrake(),
                    car.brakeHold(), car.isPoseSettling(), v3(rail), railChunk, TrainResidency.footprintLoaded(level, rail),
                    rail != null && TrainResidency.inPlotGrid(level, rail), bodyText(level, car, rail), edgeText(car),
                    step == null ? "none" : String.format(Locale.ROOT, "ds=%.4f in=%.4f %s %d ticks ago", step.ds(),
                            step.speedIn(), step.why(), now - step.tick()),
                    TrainResidency.traceFlags(car), StationChunkTickets.describe(car));
        }
    }

    private static String bodyText(ServerLevel level, SableTrain car) {
        return bodyText(level, car, TrainResidency.railCursor(car.car()));
    }

    private static String bodyText(ServerLevel level, SableTrain car, Vector3d rail) {
        ServerSubLevel sub = SubLevelData.resolve(level, car.car().subLevelId());
        if (sub == null) {
            return "body=none";
        }
        Vector3d at = new Vector3d(sub.logicalPose().position());
        return "body=" + v3(at) + " bodyToRail=" + (rail == null ? "n/a" : f2(at.distance(rail)));
    }

    private static String chunkStates(ServerLevel level, ServerSubLevel sub) {
        BoundingBox3dc box = sub.boundingBox();
        StringBuilder out = new StringBuilder();
        for (int cx = Mth.floor(box.minX() - 1.0) >> 4; cx <= Mth.floor(box.maxX() + 1.0) >> 4; cx++) {
            for (int cz = Mth.floor(box.minZ() - 1.0) >> 4; cz <= Mth.floor(box.maxZ() + 1.0) >> 4; cz++) {
                out.append(cx).append(',').append(cz).append(PhysicsChunkTicketManager.isChunkLoadedEnough(level, cx, cz)
                        ? ":loaded " : ":NOT loaded ");
            }
        }
        return out.toString().trim();
    }

    private static String matesText(ServerLevel level, SableTrain train) {
        StringBuilder out = new StringBuilder();
        for (UUID id : TrainConsist.orderedChain(level, train.car().subLevelId())) {
            SableTrain mate = SableTrainRegistry.bySubLevel(id);
            if (mate == null || mate == train || mate.car() == null) {
                continue;
            }
            Vector3d rail = TrainResidency.railCursor(mate.car());
            out.append(shortId(id)).append(' ').append(mate.residency()).append(" rail=").append(v3(rail))
                    .append(" speed=").append(f3(mate.speed() * 20.0)).append(" | ");
        }
        return out.length() == 0 ? "none" : out.toString();
    }

    private static String edgeText(SableTrain car) {
        List<Bogey> chain = car.car().chain();
        if (chain.isEmpty()) {
            return "no bogeys";
        }
        TrackGraph graph = chain.get(chain.size() - 1).rail().graph();
        return "graph " + (graph == null ? "none" : shortId(graph.id)) + " tail "
                + pointText(chain.get(0).rail().trailing()) + " nose "
                + pointText(chain.get(chain.size() - 1).rail().leading());
    }

    private static String pointText(TravellingPoint point) {
        if (point == null || point.edge == null) {
            return "off rail";
        }
        return String.format(Locale.ROOT, "%d>%d@%.2f/%.2f", point.node1.getNetId(), point.node2.getNetId(),
                point.position, point.edge.getLength());
    }

    private static String v3(Vector3d v) {
        return v == null ? "none" : String.format(Locale.ROOT, "(%.1f,%.1f,%.1f)", v.x, v.y, v.z);
    }

    private static String f2(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static String f3(double value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }

    public static void addBranch(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.literal("consisttrace").executes(ctx -> {
            enabled = !enabled;
            SNAPS.clear();
            PAIRS.clear();
            STEPS.clear();
            RECENT.clear();
            SNATCH_NOTES.clear();
            EMBODIED_AT.clear();
            ctx.getSource().sendSuccess(() -> Component.literal("consist trace " + (enabled ? "on" : "off")
                    + ", grep [consisttrace] in latest.log"), false);
            return 1;
        }));
    }
}
