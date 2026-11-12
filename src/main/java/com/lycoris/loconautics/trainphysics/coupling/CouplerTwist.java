package com.lycoris.loconautics.trainphysics.coupling;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.common.sable.BodyRef;
import com.lycoris.loconautics.common.sable.SubLevelData;
import com.lycoris.loconautics.content.knuckle.KnuckleTuning;
import com.lycoris.loconautics.foundation.debug.DebugLines;
import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.trainphysics.TrainConsist;
import com.lycoris.loconautics.trainphysics.drive.ExternalForceDerail;
import com.lycoris.loconautics.trainphysics.drive.SeatBlend;
import com.lycoris.loconautics.trainphysics.drive.TrainRailAdvance;
import com.lycoris.loconautics.trainphysics.glue.CarriageRoll;
import com.lycoris.loconautics.trainphysics.portal.PortalTwins;
import com.lycoris.loconautics.trainphysics.state.SableTrainRegistry;
import dev.ryanhcode.sable.api.physics.PhysicsPipeline;
import dev.ryanhcode.sable.api.physics.force.ForceTotal;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.physics.mass.MassData;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static com.lycoris.loconautics.foundation.LogFormat.f;
import static com.lycoris.loconautics.foundation.LogFormat.shortId;

public final class CouplerTwist {
    static final double TWIST_STIFFNESS = 2.0;
    static final double TWIST_SLACK_DEGREES = 3.0;


    // couplertwist is temporary, it goes with the fix confirmation
    public static final boolean TRACE = true;

    private static final long RAMP_TICKS = 8L;
    private static final long TRACE_TICKS = 10L;
    private static final long NEVER = -1_000_000L;
    private static final double MIN_SPAN = 0.05;
    private static final double RIGID_AXIS = 0.707;
    private static final int GREEN = 0x33FF33;
    private static final int YELLOW = 0xFFFF00;
    private static final int RED = 0xFF3333;

    private CouplerTwist() {
    }

    private enum Role { ROLLING, FREE, SKIP }

    private record Side(Role role, String why, ServerSubLevel sub, RigidBodyHandle handle, MassData md,
                        CarriageRoll.Roll roll) {

        static Side skip(String why) {
            return new Side(Role.SKIP, why, null, null, null, null);
        }

        boolean rolling() {
            return role == Role.ROLLING;
        }
    }

    private record Pair(UUID lo, UUID hi) {

        static Pair of(UUID a, UUID b) {
            return a.compareTo(b) <= 0 ? new Pair(a, b) : new Pair(b, a);
        }

        String slot() {
            return "ct_" + shortId(lo) + "_" + shortId(hi);
        }
    }

    private record Entry(Pair pair, TwistLink link) {
    }

    private record Swivel(Pair pair, TwistLink link, boolean rigid, double axisDot) {
    }

    private record Snapshot(long tick, List<Entry> links, List<Swivel> swivels, Map<UUID, SableTrain> trains,
                            Map<UUID, List<UUID>> groups, Map<Pair, TwistLink> shown) {
    }

    private static final class State {
        Quaterniond rest;
        boolean frozen;
        long lastActive = NEVER;
        long rampStart;
        long lastTrace = NEVER;
        boolean capHit;
    }

    private static final class Load {
        final ServerSubLevel sub;
        final RigidBodyHandle handle;
        final ForceTotal forces = new ForceTotal();

        Load(ServerSubLevel sub, RigidBodyHandle handle) {
            this.sub = sub;
            this.handle = handle;
        }
    }

    private static final Map<ResourceKey<Level>, Snapshot> SNAPSHOTS = new ConcurrentHashMap<>();
    private static final Map<Pair, State> STATES = new ConcurrentHashMap<>();
    private static final Set<UUID> ARTICULATED = ConcurrentHashMap.newKeySet();

    // the providers scan block entities, so this is once per game tick, not per substep
    public static void collect(ServerLevel level) {
        long now = level.getGameTime();
        Snapshot previous = SNAPSHOTS.get(level.dimension());
        if (previous != null && previous.tick() == now) {
            return;
        }
        Map<UUID, SableTrain> trains = new HashMap<>();
        for (SableTrain train : SableTrainRegistry.all()) {
            UUID body = train.car() == null ? null : train.car().subLevelId();
            if (body != null && train.level() == level) {
                trains.put(body, train);
            }
        }
        Map<Pair, TwistLink> best = new HashMap<>();
        List<Swivel> swivels = new ArrayList<>();
        for (UUID body : trains.keySet()) {
            TrainConsist.twistLinks(level, body, link -> consider(level, trains, link, best, swivels));
        }
        Set<Pair> rigidPairs = new HashSet<>();
        Map<UUID, UUID> parent = new HashMap<>();
        Map<Pair, TwistLink> shown = new HashMap<>();
        for (Swivel swivel : swivels) {
            shown.put(swivel.pair(), swivel.link());
            if (swivel.rigid()) {
                rigidPairs.add(swivel.pair());
                union(parent, swivel.pair().lo(), swivel.pair().hi());
            }
        }
        List<Entry> links = new ArrayList<>();
        for (Map.Entry<Pair, TwistLink> entry : best.entrySet()) {
            if (!rigidPairs.contains(entry.getKey())) {
                links.add(new Entry(entry.getKey(), entry.getValue()));
                shown.put(entry.getKey(), entry.getValue());
            }
        }
        Snapshot next = new Snapshot(now, links, swivels, trains, groupsOf(parent), shown);
        SNAPSHOTS.put(level.dimension(), next);
        noteLinkChanges(level, previous, next);
    }

    private static void consider(ServerLevel level, Map<UUID, SableTrain> trains, TwistLink link,
                                 Map<Pair, TwistLink> best, List<Swivel> swivels) {
        if (link.subA().equals(link.subB()) || !trains.containsKey(link.subA()) || !trains.containsKey(link.subB())) {
            return;
        }
        Pair pair = Pair.of(link.subA(), link.subB());
        if (link.kind() == TwistLink.Kind.SWIVEL) {
            swivels.add(evaluateSwivel(level, pair, link));
            return;
        }
        TwistLink held = best.get(pair);
        if (held == null || link.strength() > held.strength()) {
            best.put(pair, link);
        }
    }

    // a bearing whose axis is not along the track already locks relative roll in its own rotary joint
    private static Swivel evaluateSwivel(ServerLevel level, Pair pair, TwistLink link) {
        ServerSubLevel own = SubLevelData.resolve(level, link.subA());
        double dot = 0.0;
        boolean known = false;
        if (own != null && link.axisA() != null) {
            Vector3d axis = own.logicalPose().orientation().transform(new Vector3d(link.axisA()));
            for (UUID id : List.of(link.subA(), link.subB())) {
                CarriageRoll.Roll roll = CarriageRoll.rollById(id);
                if (roll != null && roll.lastDriveTick != Long.MIN_VALUE) {
                    known = true;
                    dot = Math.max(dot, Math.abs(axis.dot(roll.axis)));
                }
            }
        }
        return new Swivel(pair, link, !known || dot < RIGID_AXIS, dot);
    }

    private static void union(Map<UUID, UUID> parent, UUID a, UUID b) {
        parent.putIfAbsent(a, a);
        parent.putIfAbsent(b, b);
        parent.put(find(parent, a), find(parent, b));
    }

    private static UUID find(Map<UUID, UUID> parent, UUID id) {
        UUID root = id;
        while (!parent.get(root).equals(root)) {
            root = parent.get(root);
        }
        return root;
    }

    private static Map<UUID, List<UUID>> groupsOf(Map<UUID, UUID> parent) {
        Map<UUID, List<UUID>> byRoot = new HashMap<>();
        for (UUID id : parent.keySet()) {
            byRoot.computeIfAbsent(find(parent, id), key -> new ArrayList<>()).add(id);
        }
        Map<UUID, List<UUID>> groups = new HashMap<>();
        for (List<UUID> members : byRoot.values()) {
            for (UUID member : members) {
                groups.put(member, members);
            }
        }
        return groups;
    }

    private static void noteLinkChanges(ServerLevel level, Snapshot previous, Snapshot next) {
        for (Map.Entry<Pair, TwistLink> entry : next.shown().entrySet()) {
            if ((previous == null || !previous.shown().containsKey(entry.getKey())) && TRACE) {
                TwistLink link = entry.getValue();
                LoconauticsConstants.LOGGER.info("[couplertwist] link a={} b={} kind={} strength={}",
                        shortId(link.subA()), shortId(link.subB()), link.kind(), f(link.strength()));
            }
        }
        if (previous == null) {
            return;
        }
        for (Pair pair : previous.shown().keySet()) {
            if (next.shown().containsKey(pair)) {
                continue;
            }
            STATES.remove(pair);
            DebugLines.clear(level, pair.slot());
            if (TRACE) {
                LoconauticsConstants.LOGGER.info("[couplertwist] link gone a={} b={}", shortId(pair.lo()),
                        shortId(pair.hi()));
            }
        }
    }

    public static int linksOf(UUID id) {
        int count = 0;
        for (Snapshot snapshot : SNAPSHOTS.values()) {
            for (Entry entry : snapshot.links()) {
                if (id.equals(entry.link().subA()) || id.equals(entry.link().subB())) {
                    count++;
                }
            }
        }
        return count;
    }

    public static String articulatedLock(UUID id) {
        for (Snapshot snapshot : SNAPSHOTS.values()) {
            List<UUID> group = snapshot.groups().get(id);
            if (group == null) {
                continue;
            }
            for (UUID other : group) {
                String reason = other.equals(id) ? null : CarriageRoll.primaryLockOf(other);
                if (reason != null) {
                    if (ARTICULATED.add(id) && TRACE) {
                        LoconauticsConstants.LOGGER.info("[couplertwist] lock articulated car={} because car={} is {}",
                                shortId(id), shortId(other), reason);
                    }
                    return CarriageRoll.LOCK_ARTICULATED;
                }
            }
        }
        if (ARTICULATED.remove(id) && TRACE) {
            LoconauticsConstants.LOGGER.info("[couplertwist] lock articulated cleared car={}", shortId(id));
        }
        return null;
    }

    public static void solve(SubLevelPhysicsSystem system, double dt) {
        ServerLevel level = system.getLevel();
        Snapshot snapshot = SNAPSHOTS.get(level.dimension());
        double kappa = TWIST_STIFFNESS;
        if (snapshot == null || !(dt > 0.0)) {
            return;
        }
        long now = level.getGameTime();
        Map<UUID, Load> loads = new HashMap<>();
        if (kappa > 0.0) {
            double slack = Math.toRadians(TWIST_SLACK_DEGREES);
            for (Entry entry : snapshot.links()) {
                solveLink(level, snapshot, entry, now, dt, kappa, slack, loads);
            }
        }
        traceSwivels(level, snapshot, now);
        PhysicsPipeline pipeline = system.getPipeline();
        for (Load load : loads.values()) {
            pipeline.wakeUp(load.sub);
            load.handle.applyForcesAndReset(load.forces);
        }
    }

    private static void solveLink(ServerLevel level, Snapshot snapshot, Entry entry, long now, double dt,
                                  double kappa, double slack, Map<UUID, Load> loads) {
        TwistLink link = entry.link();
        State state = STATES.computeIfAbsent(entry.pair(), key -> new State());
        Side a = classify(level, snapshot, link.subA(), now);
        Side b = classify(level, snapshot, link.subB(), now);
        String why = a.role() == Role.SKIP ? a.why() : b.role() == Role.SKIP ? b.why() : null;
        if (why == null && a.role() == Role.FREE && b.role() == Role.FREE) {
            why = "free";
        }
        if (why == null && portalBlocked(snapshot, a, b)) {
            why = "portal";
        }
        Vector3d pa = null;
        Vector3d pb = null;
        if (why == null) {
            pa = world(a, link.anchorA());
            pb = world(b, link.anchorB());
            if (pa.distance(pb) > KnuckleTuning.BREAK_DISTANCE) {
                why = "far";
            }
        }
        if (why == null && a.rolling() && b.rolling()) {
            refreshRest(state, entry.pair(), a, b);
        } else if (why == null && state.rest == null) {
            why = "no_rest";
        } else if (why == null) {
            freezeRest(state, entry.pair());
        }
        if (why != null) {
            traceSkip(state, entry, a, b, why, now);
            return;
        }

        Vector3d u = lineAxis(a, b, pa, pb);
        Vector3d axisA = a.rolling() ? a.roll().axis : null;
        Vector3d axisB = b.rolling() ? b.roll().axis : null;
        double dotA = axisA == null ? 0.0 : u.dot(axisA);
        double dotB = axisB == null ? 0.0 : u.dot(axisB);
        double theta = twistAbout(a, b, state.rest, u);
        double thetaDot = new Vector3d(b.handle().getAngularVelocity(new Vector3d()))
                .sub(a.handle().getAngularVelocity(new Vector3d())).dot(u);
        double thetaEx = Math.signum(theta) * Math.max(0.0, Math.abs(theta) - slack);

        double inverseA = inverseInertia(a, u, dotA);
        double inverseB = inverseInertia(b, u, dotB);
        if (!(inverseA >= 0.0) || !(inverseB >= 0.0) || !(inverseA + inverseB > 1.0e-9)) {
            traceSkip(state, entry, a, b, "degenerate", now);
            return;
        }
        double inertia = 1.0 / (inverseA + inverseB);
        double hinge = Math.min(a.rolling() ? a.roll().hingeK : Double.MAX_VALUE,
                b.rolling() ? b.roll().hingeK : Double.MAX_VALUE);
        double lever = Math.min(a.rolling() ? a.roll().half : Double.MAX_VALUE,
                b.rolling() ? b.roll().half : Double.MAX_VALUE);
        double massA = a.md().getMass();
        double massB = b.md().getMass();
        double effectiveMass = massA * massB / (massA + massB);
        double kt = Math.min(link.strength() * kappa * hinge, 0.25 * inertia / (dt * dt));
        double ct = Math.min(2.0 * Math.sqrt(kt * inertia), 0.5 * inertia / dt);
        double tauMax = link.strength() * effectiveMass * KnuckleTuning.MAX_ACCEL * lever;

        if (now - state.lastActive > 1L) {
            state.rampStart = now;
        }
        state.lastActive = now;
        double ramp = Math.min(1.0, (double) (now - state.rampStart) / RAMP_TICKS);
        double sign = Math.signum(thetaEx);
        double tauRaw = thetaEx == 0.0 ? 0.0 : kt * thetaEx + ct * thetaDot;
        boolean capped = thetaEx != 0.0 && sign * tauRaw >= tauMax;
        double tau = sign * Math.min(tauMax, Math.max(0.0, sign * tauRaw)) * ramp;

        if (tau != 0.0) {
            Vector3d couple = new Vector3d(u).mul(tau);
            addCouple(loads, a, couple, dt);
            addCouple(loads, b, couple.negate(new Vector3d()), dt);
        }
        noteRoll(a, tau * dotA, theta);
        noteRoll(b, tau * dotB, theta);
        noteCap(state, entry.pair(), capped);

        if (TRACE && now - state.lastTrace >= TRACE_TICKS) {
            state.lastTrace = now;
            int color = capped ? RED : thetaEx != 0.0 ? YELLOW : GREEN;
            DebugLines.line(level, entry.pair().slot(), new Vec3(pa.x, pa.y, pa.z), new Vec3(pb.x, pb.y, pb.z),
                    color, DebugLines.DEFAULT_WIDTH, 2 * (int) TRACE_TICKS);
            LoconauticsConstants.LOGGER.info(
                    "[couplertwist] pair={}/{} kind={} a={} b={} uA={} uB={} theta={} deg track={} deg ex={} deg rate={} kt={} ct={} inertia={} tauRaw={} tau={} tauMax={} ramp={}",
                    shortId(link.subA()), shortId(link.subB()), link.kind(), a.role(), b.role(), f(dotA), f(dotB),
                    f(Math.toDegrees(theta)), trackTwist(a, b, u), f(Math.toDegrees(thetaEx)), f(thetaDot), f(kt),
                    f(ct), f(inertia), f(tauRaw), f(tau), f(tauMax), f(ramp));
        }
    }

    private static Side classify(ServerLevel level, Snapshot snapshot, UUID id, long now) {
        ServerSubLevel sub = BodyRef.of(level, id).sub();
        SableTrain train = snapshot.trains().get(id);
        if (sub == null) {
            return Side.skip("absent");
        }
        if (train == null) {
            return Side.skip("not_train");
        }
        RigidBodyHandle handle = RigidBodyHandle.of(sub);
        MassData md = sub.getMassTracker();
        if (handle == null || md == null || md.isInvalid()) {
            return Side.skip("absent");
        }
        if (ExternalForceDerail.staffLocked(sub)) {
            return Side.skip("staff");
        }
        CarriageRoll.Roll roll = CarriageRoll.rollById(id);
        if (train.isDerailed() || train.isPhysics()) {
            return new Side(Role.FREE, null, sub, handle, md, roll);
        }
        if (roll == null || !roll.rolling || roll.lastDriveTick != now) {
            return Side.skip("locked(" + (roll == null ? "none" : roll.lock) + ")");
        }
        if (roll.settling(now) || SeatBlend.active(id)) {
            return Side.skip("settling");
        }
        return new Side(Role.ROLLING, null, sub, handle, md, roll);
    }

    private static boolean portalBlocked(Snapshot snapshot, Side a, Side b) {
        for (Side side : List.of(a, b)) {
            UUID id = side.sub().getUniqueId();
            SableTrain train = snapshot.trains().get(id);
            if (PortalTwins.isTwin(id) || train.car().carriage() != null && TrainRailAdvance.anyPendingPortal(train.car())) {
                return true;
            }
        }
        return false;
    }

    private static Vector3d world(Side side, Vector3d anchor) {
        return side.sub().logicalPose().transformPosition(new Vector3d(anchor), new Vector3d());
    }

    private static Vector3d lineAxis(Side a, Side b, Vector3d pa, Vector3d pb) {
        Vector3d line = new Vector3d(pb).sub(pa);
        double span = line.length();
        if (span >= MIN_SPAN) {
            return line.div(span);
        }
        Vector3d axis = new Vector3d(a.rolling() ? a.roll().axis : b.roll().axis);
        Vector3d centerA = a.sub().logicalPose().transformPosition(new Vector3d(a.md().getCenterOfMass()),
                new Vector3d());
        Vector3d centerB = b.sub().logicalPose().transformPosition(new Vector3d(b.md().getCenterOfMass()),
                new Vector3d());
        return centerB.sub(centerA).dot(axis) < 0.0 ? axis.negate() : axis;
    }

    // twist about u of the rotation from car a to car b, with their seated relation taken out
    private static double twistAbout(Side a, Side b, Quaterniond rest, Vector3d u) {
        Quaterniond turnedA = new Quaterniond(a.sub().logicalPose().orientation()).mul(rest).invert();
        Quaterniond error = new Quaterniond(b.sub().logicalPose().orientation()).mul(turnedA);
        double theta = 2.0 * Math.atan2(error.x * u.x + error.y * u.y + error.z * u.z, error.w);
        if (theta > Math.PI) {
            theta -= 2.0 * Math.PI;
        } else if (theta < -Math.PI) {
            theta += 2.0 * Math.PI;
        }
        return theta;
    }

    // a rolling car only turns about its chord, a free car about u itself
    private static double inverseInertia(Side side, Vector3d u, double dot) {
        if (side.rolling()) {
            double hingeI = side.roll().hingeI;
            return hingeI > 1.0e-6 ? dot * dot / hingeI : -1.0;
        }
        Vector3d local = new Quaterniond(side.sub().logicalPose().orientation()).conjugate()
                .transform(new Vector3d(u));
        double inertia = local.dot(side.md().getInertiaTensor().transform(local, new Vector3d()));
        return inertia > 1.0e-6 ? 1.0 / inertia : -1.0;
    }

    private static void addCouple(Map<UUID, Load> loads, Side side, Vector3d couple, double dt) {
        Vector3d world = new Vector3d(couple);
        if (side.rolling()) {
            Vector3d axis = side.roll().axis;
            world.set(axis).mul(couple.dot(axis));
        }
        Vector3d local = new Quaterniond(side.sub().logicalPose().orientation()).conjugate()
                .transform(world.mul(dt), new Vector3d());
        loads.computeIfAbsent(side.sub().getUniqueId(), key -> new Load(side.sub(), side.handle()))
                .forces.applyAngularImpulse(local);
    }

    private static void noteRoll(Side side, double torque, double theta) {
        if (side.rolling()) {
            side.roll().couplerTorque += Math.abs(torque);
            side.roll().couplerTwist = Math.max(side.roll().couplerTwist, Math.abs(theta));
        }
    }

    private static void refreshRest(State state, Pair pair, Side a, Side b) {
        state.rest = new Quaterniond(a.roll().seat).invert().mul(b.roll().seat);
        if (state.frozen && TRACE) {
            LoconauticsConstants.LOGGER.info("[couplertwist] pair={}/{} Q0 refreshed", shortId(pair.lo()),
                    shortId(pair.hi()));
        }
        state.frozen = false;
    }

    private static void freezeRest(State state, Pair pair) {
        if (!state.frozen && TRACE) {
            LoconauticsConstants.LOGGER.info("[couplertwist] pair={}/{} Q0 frozen", shortId(pair.lo()),
                    shortId(pair.hi()));
        }
        state.frozen = true;
    }

    private static void noteCap(State state, Pair pair, boolean capped) {
        if (capped != state.capHit && TRACE) {
            LoconauticsConstants.LOGGER.info("[couplertwist] pair={}/{} cap {}", shortId(pair.lo()),
                    shortId(pair.hi()), capped ? "hit" : "clear");
        }
        state.capHit = capped;
    }

    // the angle between the two track normals about u, logged only, the law never reads it
    private static String trackTwist(Side a, Side b, Vector3d u) {
        if (!a.rolling() || !b.rolling()) {
            return "na";
        }
        Vector3d normalA = new Vector3d(a.roll().up).fma(-a.roll().up.dot(u), u);
        Vector3d normalB = new Vector3d(b.roll().up).fma(-b.roll().up.dot(u), u);
        return f(Math.toDegrees(Math.atan2(normalA.cross(normalB, new Vector3d()).dot(u), normalA.dot(normalB))));
    }

    private static void traceSkip(State state, Entry entry, Side a, Side b, String why, long now) {
        if (!TRACE || now - state.lastTrace < TRACE_TICKS) {
            return;
        }
        state.lastTrace = now;
        LoconauticsConstants.LOGGER.info("[couplertwist] pair={}/{} kind={} skip {} a={} b={}",
                shortId(entry.link().subA()), shortId(entry.link().subB()), entry.link().kind(), why, a.role(),
                b.role());
    }

    private static void traceSwivels(ServerLevel level, Snapshot snapshot, long now) {
        if (!TRACE) {
            return;
        }
        for (Swivel swivel : snapshot.swivels()) {
            State state = STATES.computeIfAbsent(swivel.pair(), key -> new State());
            if (now - state.lastTrace < TRACE_TICKS) {
                continue;
            }
            state.lastTrace = now;
            TwistLink link = swivel.link();
            Side a = classify(level, snapshot, link.subA(), now);
            Side b = classify(level, snapshot, link.subB(), now);
            String theta = "na";
            if (a.rolling() && b.rolling()) {
                Quaterniond rest = new Quaterniond(a.roll().seat).invert().mul(b.roll().seat);
                theta = f(Math.toDegrees(twistAbout(a, b, rest, new Vector3d(a.roll().axis))));
            }
            LoconauticsConstants.LOGGER.info(
                    "[couplertwist] swivel pair={}/{} axisDot={} {} theta={} deg a={} lockA={} b={} lockB={}",
                    shortId(link.subA()), shortId(link.subB()), f(swivel.axisDot()),
                    swivel.rigid() ? "rigid" : "free", theta, a.role(), lockText(link.subA()), b.role(),
                    lockText(link.subB()));
        }
    }

    private static String lockText(UUID id) {
        CarriageRoll.Roll roll = CarriageRoll.rollById(id);
        return roll == null ? "none" : roll.lock;
    }

    public static void retain(Set<UUID> live) {
        STATES.keySet().removeIf(pair -> !live.contains(pair.lo()) || !live.contains(pair.hi()));
        ARTICULATED.retainAll(live);
    }

    public static void clearAll() {
        STATES.clear();
        SNAPSHOTS.clear();
        ARTICULATED.clear();
    }
}
