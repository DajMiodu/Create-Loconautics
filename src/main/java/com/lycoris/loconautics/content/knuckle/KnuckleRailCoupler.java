package com.lycoris.loconautics.content.knuckle;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.content.buffer.BufferContactManager;
import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.trainphysics.sublevel.SubLevelContacts;
import com.lycoris.loconautics.common.sable.SubLevelData;
import com.lycoris.loconautics.common.sable.SubLevelOverlap;
import com.lycoris.loconautics.trainphysics.TrainConsist;
import com.lycoris.loconautics.trainphysics.debug.force.ForceSource;
import com.lycoris.loconautics.trainphysics.debug.force.ForceTrace;
import com.lycoris.loconautics.trainphysics.drive.ExternalForceDerail;
import com.lycoris.loconautics.trainphysics.rail.RailFrame;
import com.lycoris.loconautics.trainphysics.state.SableTrainRegistry;
import dev.ryanhcode.sable.api.physics.PhysicsPipeline;
import dev.ryanhcode.sable.api.physics.force.ForceTotal;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.physics.mass.MassData;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.minecraft.server.level.ServerLevel;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static com.lycoris.loconautics.foundation.LogFormat.shortId;

public final class KnuckleRailCoupler {

    private KnuckleRailCoupler() {
    }

    public record Pair(UUID subA, Vector3dc hingeA, UUID subB, Vector3dc hingeB, double length, long tick) {
    }

    private static final class Body {
        private final UUID id;
        private final ServerSubLevel sub;
        private final RigidBodyHandle handle;
        private final double mass;
        private final RailFrame frame;
        private final boolean held;
        private final String heldWhy;
        private final double invM;
        private final double freeInvM;
        private double ds;
        private boolean deep;
        private boolean contact;
        private double closing;
        private double reach;

        private Body(UUID id, ServerSubLevel sub, RigidBodyHandle handle, double mass, RailFrame frame,
                     boolean held, String heldWhy, double invM) {
            this.id = id;
            this.sub = sub;
            this.handle = handle;
            this.mass = mass;
            this.frame = frame;
            this.held = held;
            this.heldWhy = heldWhy;
            this.invM = invM;
            this.freeInvM = held ? 0.0 : 1.0 / mass;
        }

        private double speed() {
            return frame.speed() + ds;
        }
    }

    private static final class Link {
        private final KnuckleBlockEntity owner;
        private final Body a;
        private final Body b;
        private final double coefA;
        private final double coefB;
        private final double railRate;
        private final double target;
        private final double error;
        private final double length;
        private final double invA;
        private final double invB;
        private final double effective;
        private double lambdaNet;
        private double lambdaTotal;
        private double ceiling = Double.MAX_VALUE;
        private SubLevelContacts.Pair contactKey;

        private Link(KnuckleBlockEntity owner, Body a, Body b, double coefA, double coefB, double railRate,
                     double target, double error, double length) {
            this.owner = owner;
            this.a = a;
            this.b = b;
            this.coefA = coefA;
            this.coefB = coefB;
            this.railRate = railRate;
            this.target = target;
            this.error = error;
            this.length = length;
            this.invA = Math.abs(coefA) >= KnuckleTuning.A_MIN ? (owner == null ? a.freeInvM : a.invM) : 0.0;
            this.invB = Math.abs(coefB) >= KnuckleTuning.A_MIN ? (owner == null ? b.freeInvM : b.invM) : 0.0;
            this.effective = coefA * coefA * invA + coefB * coefB * invB;
        }

        private boolean contact() {
            return owner == null;
        }

        private double rate() {
            return railRate + coefA * a.speed() + coefB * b.speed();
        }
    }

    private static final long STALE_TICKS = 6L;
    private static final long TRACE_TICKS = 20L;
    private static final double WAKE_ERROR = 0.002;
    private static final double WAKE_SPEED = 0.01;
    private static final double CONTACT_SLOP = 0.02;
    private static final double MAX_SEPARATE_SPEED = 1.0;
    private static final double SEPARATE_PER_DEPTH = 3.0;
    private static final double KICK_MARGIN = 0.25;
    private static final double DEEP_OVERLAP = 0.5;
    private static final double DEEP_ACCEL = 400.0;
    private static final double CONTACT_ACCEL = 100.0;
    private static final double CONTACT_AXIS_MIN = 0.5;
    private static final double BLOCK_TEST_DV = 0.05;
    private static final double BLOCK_FRACTION = 0.4;
    private static final long BLOCK_STEPS = 12L;
    private static final double PROBE_MIN_DS = 0.01;

    private static final Map<KnuckleBlockEntity, Pair> PAIRS = new ConcurrentHashMap<>();
    private static final Map<KnuckleBlockEntity, Long> TRACED = new ConcurrentHashMap<>();
    private static final Map<UUID, Double> LAST_DS = new ConcurrentHashMap<>();
    private static final Map<UUID, Double> APPLIED_DS = new ConcurrentHashMap<>();
    private static final Map<UUID, Double> SPEED_BEFORE = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> BLOCKED_UNTIL = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> ORDER = new ConcurrentHashMap<>();
    private static final Map<UUID, Double> EXTERNAL_DS = new ConcurrentHashMap<>();
    private static final Map<SubLevelContacts.Pair, Double> SEPARATION = new ConcurrentHashMap<>();
    private static final Set<UUID> LINKED = ConcurrentHashMap.newKeySet();
    private static final Map<UUID, String> HELD_WHY = new ConcurrentHashMap<>();
    private static long orderTick = -1L;
    private static long step;
    private static boolean probed;

    private static final Map<KnuckleBlockEntity, Long> TRACED_308 = new HashMap<>();

    public static void trace308(KnuckleBlockEntity owner, ServerSubLevel subA, Vector3dc hingeA, ServerSubLevel subB,
                                Vector3dc hingeB, boolean bar) {
        long now = subA.getLevel().getGameTime();
        if (now - TRACED_308.getOrDefault(owner, -20L) < 20L) {
            return;
        }
        TRACED_308.put(owner, now);
        Vector3d worldA = subA.logicalPose().transformPosition(new Vector3d(hingeA));
        Vector3d worldB = subB.logicalPose().transformPosition(new Vector3d(hingeB));
        RailFrame frameA = RailFrame.of(subA, SableTrainRegistry.bySubLevel(subA.getUniqueId()));
        double out = worldB.sub(worldA, new Vector3d()).dot(worldA.sub(frameA.anchor(), new Vector3d()));
        LoconauticsConstants.LOGGER.info("[iss308] pair {} {} branch={} gap={} crossed={} A[{}] B[{}]",
                shortId(subA.getUniqueId()), shortId(subB.getUniqueId()), bar ? "bar" : "joint",
                KnuckleTuning.fmt(worldA.distance(worldB)), out < 0.0, state308(subA), state308(subB));
    }

    private static String state308(ServerSubLevel sub) {
        SableTrain train = SableTrainRegistry.bySubLevel(sub.getUniqueId());
        if (train == null) {
            return "no train";
        }
        return "mode=" + RailFrame.modeOf(train) + " derailed=" + train.isDerailed() + " physics=" + train.isPhysics()
                + " offTrack=" + train.hasOffTrackBogey() + " locked=" + ExternalForceDerail.staffLocked(sub)
                + " holds=" + ExternalForceDerail.staffHolds(sub)
                + " blocked=" + (BLOCKED_UNTIL.getOrDefault(sub.getUniqueId(), 0L) > step);
    }

    public static void put(KnuckleBlockEntity owner, Pair pair) {
        PAIRS.put(owner, pair);
    }

    // speed the drive or the resistance gave a linked car since the last solve, so markblocked does not read it as a wall
    public static void noteExternal(UUID body, double ds) {
        if (LINKED.contains(body)) {
            EXTERNAL_DS.merge(body, ds, Double::sum);
        }
    }

    public static void remove(KnuckleBlockEntity owner) {
        PAIRS.remove(owner);
        TRACED.remove(owner);
    }

    public static void releaseAllFor(UUID body) {
        SEPARATION.keySet().removeIf(key -> key.first().equals(body) || key.second().equals(body));
        PAIRS.values().removeIf(p -> p.subA().equals(body) || p.subB().equals(body));
        TRACED.keySet().retainAll(PAIRS.keySet());
        LAST_DS.remove(body);
        APPLIED_DS.remove(body);
        SPEED_BEFORE.remove(body);
        BLOCKED_UNTIL.remove(body);
        ORDER.remove(body);
        EXTERNAL_DS.remove(body);
        LINKED.remove(body);
        HELD_WHY.remove(body);
    }

    public static void clear() {
        PAIRS.clear();
        TRACED.clear();
        LAST_DS.clear();
        APPLIED_DS.clear();
        SPEED_BEFORE.clear();
        BLOCKED_UNTIL.clear();
        ORDER.clear();
        EXTERNAL_DS.clear();
        SEPARATION.clear();
        LINKED.clear();
        HELD_WHY.clear();
    }

    public static boolean glued(ServerSubLevel sub) {
        return RailFrame.modeOf(SableTrainRegistry.bySubLevel(sub.getUniqueId())) == RailFrame.Mode.RIGID;
    }

    public static boolean rigid(ServerSubLevel sub) {
        return RailFrame.modeOf(SableTrainRegistry.bySubLevel(sub.getUniqueId())) != RailFrame.Mode.OTHER;
    }

    // blocks per tick of speed gain the coupler itself just delivered, so the glue read back does not cancel it
    public static double allowance(UUID body) {
        Double ds = LAST_DS.remove(body);
        return ds == null ? 0.0 : Math.abs(ds) / 20.0;
    }

    public static void solve(SubLevelPhysicsSystem system, double dt) {
        if (dt <= 0.0) {
            return;
        }
        ServerLevel level = system.getLevel();
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            return;
        }
        PhysicsPipeline pipeline = system.getPipeline();
        long now = level.getGameTime();
        step++;
        Map<UUID, Body> bodies = new HashMap<>();
        List<Link> links = new ArrayList<>();
        for (Map.Entry<KnuckleBlockEntity, Pair> entry : PAIRS.entrySet()) {
            Pair pair = entry.getValue();
            Body a = bodyOf(container, bodies, pair.subA());
            Body b = bodyOf(container, bodies, pair.subB());
            if (a == null || b == null) {
                continue;
            }
            if (now - pair.tick() > STALE_TICKS) {
                PAIRS.remove(entry.getKey());
                continue;
            }
            Link link = barLink(entry.getKey(), pair, a, b, dt);
            if (link != null) {
                links.add(link);
            }
        }
        sortAlongConsist(level, now, links);
        addContacts(level, container, bodies, links, dt);
        APPLIED_DS.keySet().retainAll(bodies.keySet());
        SPEED_BEFORE.keySet().retainAll(bodies.keySet());
        EXTERNAL_DS.keySet().retainAll(bodies.keySet());
        LINKED.retainAll(bodies.keySet());
        LINKED.addAll(bodies.keySet());
        if (links.isEmpty()) {
            return;
        }
        for (int sweep = 0; sweep < KnuckleTuning.SWEEPS; sweep++) {
            for (int i = 0; i < links.size(); i++) {
                relax(links.get(i));
            }
            for (int i = links.size() - 1; i >= 0; i--) {
                relax(links.get(i));
            }
        }
        for (Link link : links) {
            if (Math.abs(link.error) > WAKE_ERROR || Math.abs(link.rate()) > WAKE_SPEED) {
                pipeline.wakeUp(link.a.sub);
                pipeline.wakeUp(link.b.sub);
            }
            if (!link.contact()) {
                reportBar(link, now);
            } else if (link.contactKey != null) {
                SEPARATION.put(link.contactKey, Math.max(0.0, link.rate()));
            }
        }
        for (Body body : bodies.values()) {
            apply(body, dt);
        }
    }

    private static void relax(Link link) {
        if (link.effective <= 0.0) {
            return;
        }
        double rate = link.rate();
        double lambda;
        if (link.contact()) {
            boolean over = rate > link.ceiling;
            lambda = ((over ? link.ceiling : link.target) - rate) / link.effective;
            double floor = over ? -Double.MAX_VALUE : Math.min(0.0, link.lambdaTotal);
            double next = Math.max(floor, link.lambdaTotal + lambda);
            lambda = next - link.lambdaTotal;
            link.lambdaTotal = next;
        } else {
            lambda = (link.target - rate) / link.effective;
        }
        link.lambdaNet += lambda;
        link.a.ds += lambda * link.coefA * link.invA;
        link.b.ds += lambda * link.coefB * link.invB;
    }

    private static void apply(Body body, double dt) {
        double limit = KnuckleTuning.MAX_DS;
        if (body.contact) {
            double cap = (body.deep ? DEEP_ACCEL : CONTACT_ACCEL) * dt;
            limit = Math.min(limit, Math.max(cap, body.closing + body.reach));
        }
        double ds = Math.max(-limit, Math.min(limit, body.ds));
        if (Math.abs(ds) < 1.0e-9) {
            APPLIED_DS.remove(body.id);
            SPEED_BEFORE.remove(body.id);
            return;
        }
        Vector3d world = new Vector3d(body.frame.tangent()).mul(body.mass * ds);
        Vector3d local = new Quaterniond(body.sub.logicalPose().orientation())
                .conjugate().transform(world, new Vector3d());
        ForceTotal forces = new ForceTotal();
        forces.applyLinearImpulse(local);
        boolean probe = KnuckleTuning.trace && !probed && Math.abs(ds) > PROBE_MIN_DS;
        double before = probe ? alongRail(body) : 0.0;
        body.handle.applyForcesAndReset(forces);
        if (probe) {
            probed = true;
            LoconauticsConstants.LOGGER.info("[knuckle] velocity before=" + KnuckleTuning.fmt(before) + " after="
                    + KnuckleTuning.fmt(alongRail(body)) + " expected change=" + KnuckleTuning.fmt(ds)
                    + " read straight after the impulse in the same substep");
        }
        LAST_DS.put(body.id, ds);
        APPLIED_DS.put(body.id, ds);
        SPEED_BEFORE.put(body.id, body.frame.speed());
        if (ForceTrace.enabled()) {
            ForceTrace.impulse(body.sub, ForceSource.KNUCKLE, world, body.frame.anchor(),
                    "knuckle ds=" + KnuckleTuning.fmt(ds) + " b/s held=" + body.held);
        }
    }

    private static double alongRail(Body body) {
        return body.handle.getLinearVelocity(new Vector3d()).sub(body.frame.railVelocity()).dot(body.frame.tangent());
    }

    private static Link barLink(KnuckleBlockEntity owner, Pair pair, Body a, Body b, double dt) {
        Vector3d hingeA = a.sub.logicalPose().transformPosition(new Vector3d(pair.hingeA()));
        Vector3d hingeB = b.sub.logicalPose().transformPosition(new Vector3d(pair.hingeB()));
        Vector3d line = hingeB.sub(hingeA, new Vector3d());
        double gap = line.length();
        if (gap < 1.0e-6) {
            line.set(b.frame.anchor()).sub(a.frame.anchor());
            if (line.length() < 1.0e-6) {
                return null;
            }
            gap = 0.0;
            line.normalize();
        } else {
            line.div(gap);
        }
        // crossed hinges get a negative gap, or 2 blocks on the wrong side reads as fine
        if (hingeA.sub(a.frame.anchor(), new Vector3d()).dot(line) < 0.0) {
            gap = -gap;
            line.negate();
        }
        double error = gap - pair.length();
        double bias = biasLimit(Math.abs(error));
        double target = Math.abs(error) < KnuckleTuning.SLOP ? 0.0
                : Math.max(-bias, Math.min(bias, -error * KnuckleTuning.BAUMGARTE / dt));
        return new Link(owner, a, b, -line.dot(hingeSpeed(a.frame, hingeA)), line.dot(hingeSpeed(b.frame, hingeB)),
                railRate(a, b, line), target, error, pair.length());
    }

    // a gap far off its length is a crash, not drift, so the correction speed rises with the error
    private static double biasLimit(double error) {
        double deep = Math.max(0.0, Math.min(1.0,
                (error - KnuckleTuning.DEEP_ERROR_START) / KnuckleTuning.DEEP_ERROR_SPAN));
        return KnuckleTuning.maxBiasSpeed + (KnuckleTuning.DEEP_BIAS_SPEED - KnuckleTuning.maxBiasSpeed) * deep;
    }

    // hinge velocity per unit of rail speed, the slide plus the yaw lever
    private static Vector3d hingeSpeed(RailFrame frame, Vector3d hinge) {
        Vector3d radius = hinge.sub(frame.anchor(), new Vector3d());
        return frame.yawPerBlock().cross(radius, new Vector3d()).add(frame.tangent());
    }

    private static double railRate(Body a, Body b, Vector3d line) {
        return new Vector3d(b.frame.railVelocity()).sub(a.frame.railVelocity()).dot(line);
    }

    private static void sortAlongConsist(ServerLevel level, long now, List<Link> links) {
        if (orderTick != now) {
            ORDER.clear();
            orderTick = now;
        }
        links.sort(Comparator.comparingInt(link -> Math.min(indexOf(level, link.a.id), indexOf(level, link.b.id))));
    }

    private static int indexOf(ServerLevel level, UUID id) {
        Integer known = ORDER.get(id);
        if (known != null) {
            return known;
        }
        List<UUID> chain = TrainConsist.orderedChain(level, id);
        for (int i = 0; i < chain.size(); i++) {
            ORDER.put(chain.get(i), i);
        }
        return ORDER.computeIfAbsent(id, key -> 0);
    }

    private static void reportBar(Link link, long now) {
        if (ForceTrace.enabled()) {
            ForceTrace.note(link.a.sub, ForceSource.KNUCKLE, link.a.frame.anchor(),
                    "bar lambda=" + KnuckleTuning.fmt(link.lambdaNet) + " err=" + KnuckleTuning.fmt(link.error));
        }
        if (!KnuckleTuning.trace || now - TRACED.getOrDefault(link.owner, -TRACE_TICKS) < TRACE_TICKS) {
            return;
        }
        TRACED.put(link.owner, now);
        String ids = " a=" + shortId(link.a.id) + " b=" + shortId(link.b.id);
        String coefs = " aA=" + KnuckleTuning.fmt(link.coefA) + " aB=" + KnuckleTuning.fmt(link.coefB);
        if (link.effective <= 0.0) {
            boolean bothHeld = link.a.invM <= 0.0 && link.b.invM <= 0.0;
            LoconauticsConstants.LOGGER.info("[knuckle] " + (bothHeld ? "both held" : "bar weak") + ids + coefs);
            return;
        }
        LoconauticsConstants.LOGGER.info("[knuckle] bar" + ids + " gap=" + KnuckleTuning.fmt(link.length + link.error)
                + " L=" + KnuckleTuning.fmt(link.length) + " err=" + KnuckleTuning.fmt(link.error)
                + " gdot=" + KnuckleTuning.fmt(link.rate()) + " bias=" + KnuckleTuning.fmt(link.target)
                + " lambda=" + KnuckleTuning.fmt(link.lambdaNet) + coefs + " heldA=" + link.a.held
                + " heldB=" + link.b.held + " holdA=" + link.a.heldWhy + " holdB=" + link.b.heldWhy);
    }

    private static void addContacts(ServerLevel level, ServerSubLevelContainer container, Map<UUID, Body> bodies,
                                    List<Link> links, double dt) {
        List<ServerSubLevel> railed = new ArrayList<>();
        for (SableTrain train : SableTrainRegistry.all()) {
            if (train.level() != level || train.isGhost() || RailFrame.modeOf(train) == RailFrame.Mode.OTHER) {
                continue;
            }
            ServerSubLevel sub = SubLevelData.resolve(level, train.car().subLevelId());
            if (sub != null && !sub.isRemoved()) {
                railed.add(sub);
            }
        }
        Set<SubLevelContacts.Pair> touching = new HashSet<>();
        for (int i = 0; i < railed.size(); i++) {
            for (int j = i + 1; j < railed.size(); j++) {
                ServerSubLevel subA = railed.get(i);
                ServerSubLevel subB = railed.get(j);
                SubLevelOverlap.Hit hit = SubLevelOverlap.test(subA, subB);
                if (hit == null || BufferContactManager.engaged(subA.getUniqueId(), subB.getUniqueId())
                        || TrainConsist.neighbours(level, subA.getUniqueId()).contains(subB.getUniqueId())) {
                    continue;
                }
                Body a = bodyOf(container, bodies, subA.getUniqueId());
                Body b = bodyOf(container, bodies, subB.getUniqueId());
                if (a == null || b == null) {
                    continue;
                }
                a.contact = true;
                b.contact = true;
                if (hit.depth() > DEEP_OVERLAP) {
                    a.deep = true;
                    b.deep = true;
                }
                Vector3d axis = contactAxis(hit.axis(), a, b);
                double sunk = Math.max(0.0, hit.depth() - CONTACT_SLOP);
                double push = Math.min(Math.min(MAX_SEPARATE_SPEED, sunk * SEPARATE_PER_DEPTH),
                        sunk * KnuckleTuning.BAUMGARTE / dt);
                Link link = new Link(null, a, b, -axis.dot(a.frame.tangent()), axis.dot(b.frame.tangent()),
                        railRate(a, b, axis), push, -hit.depth(), 0.0);
                SubLevelContacts.Pair key = SubLevelContacts.Pair.of(subA.getUniqueId(), subB.getUniqueId());
                touching.add(key);
                link.contactKey = key;
                link.ceiling = Math.max(SEPARATION.getOrDefault(key, 0.0), push) + KICK_MARGIN;
                a.reach = Math.max(a.reach, push + KICK_MARGIN);
                b.reach = Math.max(b.reach, push + KICK_MARGIN);
                double rate = link.rate();
                double change = Math.max(Math.max(0.0, -rate), rate - link.ceiling);
                a.closing = Math.max(a.closing, change);
                b.closing = Math.max(b.closing, change);
                links.add(link);
            }
        }
        SEPARATION.keySet().retainAll(touching);
    }

    // a deep overlap can report a sideways or vertical axis, which no rail tangent can act on, so fall back to the line between the cars
    private static Vector3d contactAxis(Vector3dc hitAxis, Body a, Body b) {
        Vector3d axis = new Vector3d(hitAxis);
        if (axisGrip(axis, a, b) >= CONTACT_AXIS_MIN) {
            return axis;
        }
        Vector3d line = b.frame.anchor().sub(a.frame.anchor(), new Vector3d());
        if (line.lengthSquared() < 1.0e-6) {
            return axis;
        }
        line.normalize();
        return axisGrip(line, a, b) > axisGrip(axis, a, b) ? line : axis;
    }

    private static double axisGrip(Vector3d axis, Body a, Body b) {
        return Math.min(Math.abs(axis.dot(a.frame.tangent())), Math.abs(axis.dot(b.frame.tangent())));
    }

    // a car that did not take the push we gave it last step is held by a wall or a pop, so it counts as immovable for the bars, a contact still pushes it
    private static void markBlocked(UUID id, double speed, double external) {
        Double applied = APPLIED_DS.get(id);
        Double before = SPEED_BEFORE.get(id);
        if (applied == null || before == null || Math.abs(applied) < BLOCK_TEST_DV) {
            return;
        }
        double realized = (speed - before - external) * Math.signum(applied);
        if (realized < Math.abs(applied) * BLOCK_FRACTION) {
            BLOCKED_UNTIL.put(id, step + BLOCK_STEPS);
        }
    }

    private static String holdReason(RailFrame frame, SableTrain train, ServerSubLevel sub) {
        if (frame.mode() == RailFrame.Mode.PINNED) {
            return "pinned settle=" + train.isPoseSettling() + " lead=" + (train.car().localLead() != null)
                    + " trail=" + (train.car().localTrail() != null);
        }
        return ExternalForceDerail.staffLocked(sub) ? "staff" : null;
    }

    // logs only when a car starts or stops being held, so one run shows what froze it and when
    private static void traceHold(UUID id, String why) {
        String before = why == null ? HELD_WHY.remove(id) : HELD_WHY.put(id, why);
        if (KnuckleTuning.trace && !java.util.Objects.equals(before, why)) {
            LoconauticsConstants.LOGGER.info("[knuckle] hold " + shortId(id) + " " + before + " to " + why);
        }
    }

    private static Body bodyOf(ServerSubLevelContainer container, Map<UUID, Body> bodies, UUID id) {
        Body known = bodies.get(id);
        if (known != null) {
            return known;
        }
        SableTrain train = SableTrainRegistry.bySubLevel(id);
        if (train == null || !(container.getSubLevel(id) instanceof ServerSubLevel sub) || sub.isRemoved()) {
            return null;
        }
        MassData massData = sub.getMassTracker();
        RigidBodyHandle handle = RigidBodyHandle.of(sub);
        if (handle == null || massData == null || massData.isInvalid() || massData.getMass() <= 0.0) {
            return null;
        }
        RailFrame frame = RailFrame.of(sub, train);
        if (frame.mode() == RailFrame.Mode.OTHER) {
            return null;
        }
        String heldWhy = holdReason(frame, train, sub);
        boolean held = heldWhy != null;
        traceHold(id, heldWhy);
        Double external = EXTERNAL_DS.remove(id);
        if (!held && ExternalForceDerail.staffHolds(sub)) {
            BLOCKED_UNTIL.remove(id);
        } else if (!held) {
            markBlocked(id, frame.speed(), external == null ? 0.0 : external);
        }
        boolean fixed = held || BLOCKED_UNTIL.getOrDefault(id, 0L) > step;
        Body body = new Body(id, sub, handle, massData.getMass(), frame, held, heldWhy, fixed ? 0.0 : 1.0 / massData.getMass());
        bodies.put(id, body);
        return body;
    }
}
