package com.lycoris.loconautics.trainphysics.debug.force;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.config.DiagnosticsConfig;
import com.lycoris.loconautics.foundation.debug.DebugLines;
import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.common.sable.SubLevelData;
import com.lycoris.loconautics.trainphysics.state.SableTrainRegistry;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.physics.mass.MassData;
import dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysicsData;
import dev.ryanhcode.sable.platform.SableEventPlatform;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.lycoris.loconautics.foundation.LogFormat.shortId;

public final class ForceTrace {

    private ForceTrace() {
    }

    public static boolean chat;
    public static double chatDeltaV = 1.0;
    public static double anomalyDeltaV = 4.0;
    public static double anomalySpin = 2.5;
    private static final double NOISE = 0.01;

    private static final int HISTORY_TICKS = 80;
    private static final int DUMP_LINES = 12;
    private static final double ARROW_SCALE = 0.5;
    private static final int ARROW_TTL = 40;

    public static boolean enabled() {
        return DiagnosticsConfig.Channel.FORCE.enabled();
    }

    public static boolean lines() {
        return DiagnosticsConfig.Channel.FORCE_LINES.enabled();
    }

    public record Entry(long gameTime, int substep, ForceSource source, Vector3d deltaV, Vector3d point,
                        String detail) {

        public double magnitude() {
            return deltaV.length();
        }
    }

    private static final class Ledger {
        final List<Entry> tick = new ArrayList<>();
        final Deque<List<Entry>> history = new ArrayDeque<>();
        final Vector3d substepStart = new Vector3d();
        final Vector3d substepOurs = new Vector3d();
        final Vector3d tickStart = new Vector3d();
        final Vector3d tickStartSpin = new Vector3d();
        boolean sampled;
        int substep;
        long stamp = Long.MIN_VALUE;
        double mass = 1.0;
        final Map<ForceSource, double[]> calibration = new EnumMap<>(ForceSource.class);
    }

    private static final Map<UUID, Ledger> LEDGERS = new HashMap<>();

    public static void register() {
        SableEventPlatform.INSTANCE.onPhysicsTick(ForceTrace::preSubstep);
        SableEventPlatform.INSTANCE.onPostPhysicsTick(ForceTrace::postSubstep);
    }

    private static Ledger ledgerOf(ServerSubLevel sub) {
        return sub == null ? null : LEDGERS.get(sub.getUniqueId());
    }

    public static void impulse(ServerSubLevel sub, ForceSource source, Vector3dc worldImpulse,
                               Vector3dc worldPoint, String detail) {
        Ledger ledger = enabled() ? ledgerOf(sub) : null;
        if (ledger == null || worldImpulse == null) {
            return;
        }
        record(ledger, sub, source, new Vector3d(worldImpulse).mul(1.0 / Math.max(1.0e-6, ledger.mass)),
                worldPoint, detail);
    }

    public static void deltaV(ServerSubLevel sub, ForceSource source, Vector3dc worldDeltaV,
                              Vector3dc worldPoint, String detail) {
        Ledger ledger = enabled() ? ledgerOf(sub) : null;
        if (ledger == null || worldDeltaV == null) {
            return;
        }
        record(ledger, sub, source, new Vector3d(worldDeltaV), worldPoint, detail);
    }

    public static void note(ServerSubLevel sub, ForceSource source, Vector3dc worldPoint, String detail) {
        Ledger ledger = enabled() ? ledgerOf(sub) : null;
        if (ledger == null) {
            return;
        }
        ledger.tick.add(new Entry(stampOf(sub), ledger.substep, source, new Vector3d(),
                worldPoint == null ? null : new Vector3d(worldPoint), detail));
    }

    private static void record(Ledger ledger, ServerSubLevel sub, ForceSource source, Vector3d deltaV,
                               Vector3dc worldPoint, String detail) {
        ledger.substepOurs.add(deltaV);
        ledger.tick.add(new Entry(stampOf(sub), ledger.substep, source, deltaV,
                worldPoint == null ? null : new Vector3d(worldPoint), detail));
        if (lines() && deltaV.lengthSquared() > NOISE * NOISE && worldPoint != null) {
            Vec3 from = new Vec3(worldPoint.x(), worldPoint.y(), worldPoint.z());
            Vec3 to = from.add(deltaV.x * ARROW_SCALE, deltaV.y * ARROW_SCALE, deltaV.z * ARROW_SCALE);
            DebugLines.arrow(sub.getLevel(), "force:" + shortId(sub.getUniqueId()) + ":" + source.name(),
                    from, to, source.color(), DebugLines.DEFAULT_WIDTH, ARROW_TTL);
        }
    }

    private static long stampOf(ServerSubLevel sub) {
        return sub.getLevel() == null ? 0L : sub.getLevel().getGameTime();
    }

    private static void preSubstep(SubLevelPhysicsSystem system, double timeStep) {
        if (!enabled()) {
            return;
        }
        forEachBody(system.getLevel(), (train, sub) -> {
            Ledger ledger = LEDGERS.computeIfAbsent(sub.getUniqueId(), key -> new Ledger());
            RigidBodyHandle handle = RigidBodyHandle.of(sub);
            if (handle == null) {
                return;
            }
            long now = sub.getLevel().getGameTime();
            if (ledger.stamp != now) {
                ledger.stamp = now;
                ledger.substep = 0;
                handle.getLinearVelocity(ledger.tickStart);
                handle.getAngularVelocity(ledger.tickStartSpin);
            }
            ledger.substep++;
            MassData md = sub.getMassTracker();
            ledger.mass = (md != null && !md.isInvalid() && md.getMass() > 0.0) ? md.getMass() : 1.0;
            handle.getLinearVelocity(ledger.substepStart);
            ledger.substepOurs.zero();
            ledger.sampled = true;
        });
    }

    private static void postSubstep(SubLevelPhysicsSystem system, double timeStep) {
        if (!enabled()) {
            return;
        }
        ServerLevel level = system.getLevel();
        Vector3d gravity = DimensionPhysicsData.getGravity(level);
        forEachBody(level, (train, sub) -> {
            Ledger ledger = ledgerOf(sub);
            RigidBodyHandle handle = ledger == null || !ledger.sampled ? null : RigidBodyHandle.of(sub);
            if (handle == null) {
                return;
            }
            Vector3d measured = handle.getLinearVelocity(new Vector3d()).sub(ledger.substepStart);
            Vector3d residual = new Vector3d(measured).sub(ledger.substepOurs)
                    .sub(gravity.x * timeStep, gravity.y * timeStep, gravity.z * timeStep);
            calibrate(ledger, measured);
            if (residual.length() <= NOISE) {
                return;
            }
            record(ledger, sub, ForceSource.SOLVER, residual, SubLevelData.position(sub),
                    solverDetail(train, sub, measured));
        });
    }

    private static void calibrate(Ledger ledger, Vector3d measured) {
        java.util.EnumSet<ForceSource> fired = java.util.EnumSet.noneOf(ForceSource.class);
        for (Entry entry : ledger.tick) {
            if (entry.substep() == ledger.substep && entry.source() != ForceSource.SOLVER
                    && entry.magnitude() > NOISE) {
                fired.add(entry.source());
            }
        }
        double commanded = ledger.substepOurs.length();
        if (fired.size() != 1 || commanded <= NOISE) {
            return;
        }
        ForceSource only = fired.iterator().next();
        double[] pair = ledger.calibration.computeIfAbsent(only, key -> new double[3]);
        pair[0] += commanded;
        pair[1] += measured.length();
        pair[2]++;
    }

    private static String solverDetail(SableTrain train, ServerSubLevel sub, Vector3d measured) {
        StringBuilder sb = new StringBuilder();
        sb.append("glue=").append(com.lycoris.loconautics.trainphysics.glue.RailGlue.glueModeOf(sub));
        sb.append(" measured=").append(f(measured.length()));
        if (train != null) {
            sb.append(" derailed=").append(train.isDerailed())
              .append(" offTrack=").append(train.offTrackBogeyCount())
              .append(" railSpeed=").append(f(train.speed() * 20.0));
        }
        return sb.toString();
    }

    public static void tickEnd(ServerLevel level) {
        if (!enabled()) {
            return;
        }
        forEachBody(level, (train, sub) -> {
            Ledger ledger = ledgerOf(sub);
            if (ledger == null || ledger.tick.isEmpty()) {
                return;
            }
            RigidBodyHandle handle = RigidBodyHandle.of(sub);
            Vector3d total = handle == null ? new Vector3d()
                    : handle.getLinearVelocity(new Vector3d()).sub(ledger.tickStart);
            Vector3d spin = handle == null ? new Vector3d()
                    : handle.getAngularVelocity(new Vector3d()).sub(ledger.tickStartSpin);
            report(train, sub, ledger, total, spin);
            ledger.history.addLast(List.copyOf(ledger.tick));
            while (ledger.history.size() > HISTORY_TICKS) {
                ledger.history.removeFirst();
            }
            ledger.tick.clear();
        });
    }

    private static void report(SableTrain train, ServerSubLevel sub, Ledger ledger, Vector3d total,
                               Vector3d spin) {
        boolean anomaly = total.length() >= anomalyDeltaV || spin.length() >= anomalySpin;
        Map<ForceSource, Vector3d> bySource = new EnumMap<>(ForceSource.class);
        for (Entry entry : ledger.tick) {
            bySource.computeIfAbsent(entry.source(), key -> new Vector3d()).add(entry.deltaV());
        }
        if (anomaly) {
            dump(train, sub, ledger, total, spin, bySource);
            return;
        }
        for (Map.Entry<ForceSource, Vector3d> hit : bySource.entrySet()) {
            if (hit.getValue().length() < chatDeltaV) {
                continue;
            }
            say(sub, String.format(Locale.ROOT, "%s %s dv=%s dir=%s over %s b/s total",
                    shortId(sub.getUniqueId()), hit.getKey().label(), f(hit.getValue().length()),
                    dir(hit.getValue()), f(total.length())));
        }
    }

    private static void dump(SableTrain train, ServerSubLevel sub, Ledger ledger, Vector3d total,
                             Vector3d spin, Map<ForceSource, Vector3d> bySource) {
        String head = String.format(Locale.ROOT,
                "%s went from %s to %s b/s in one tick, spin %s rad/s, %d pushes",
                shortId(sub.getUniqueId()), f(ledger.tickStart.length()),
                f(new Vector3d(ledger.tickStart).add(total).length()), f(spin.length()), ledger.tick.size());
        say(sub, head);
        LoconauticsConstants.LOGGER.warn("[force] ANOMALY {}", head);
        List<Entry> worst = new ArrayList<>(ledger.tick);
        worst.sort((a, b) -> Double.compare(b.magnitude(), a.magnitude()));
        for (Entry entry : worst.subList(0, Math.min(DUMP_LINES, worst.size()))) {
            String line = describe(entry);
            say(sub, "  " + line);
            LoconauticsConstants.LOGGER.warn("[force]   {}", line);
        }
        for (Map.Entry<ForceSource, Vector3d> hit : bySource.entrySet()) {
            LoconauticsConstants.LOGGER.warn("[force]   total {} dv={} dir={}",
                    hit.getKey().label(), f(hit.getValue().length()), dir(hit.getValue()));
        }
        if (train != null) {
            LoconauticsConstants.LOGGER.warn("[force]   train={} derailed={} powered={} braked={} target={} b/s "
                            + "offTrack={}",
                    shortId(train.id()), train.isDerailed(), train.isPowered(), train.isBraked(),
                    f(train.targetSpeed() * 20.0), train.offTrackBogeyCount());
        }
    }

    private static String describe(Entry entry) {
        return String.format(Locale.ROOT, "s%d %s dv=%s dir=%s at=%s %s",
                entry.substep(), entry.source().label(), f(entry.magnitude()), dir(entry.deltaV()),
                entry.point() == null ? "-" : xz(entry.point()),
                entry.detail() == null ? "" : entry.detail());
    }

    public static List<String> why(SableTrain train) {
        List<String> out = new ArrayList<>();
        ServerSubLevel sub = subOf(train);
        Ledger ledger = ledgerOf(sub);
        if (ledger == null) {
            out.add("no ledger for that car, is /dbgloco force on");
            return out;
        }
        List<Entry> last = ledger.history.isEmpty() ? ledger.tick : ledger.history.peekLast();
        out.add(shortId(sub.getUniqueId()) + " last full tick, " + last.size() + " pushes, mass "
                + f(ledger.mass));
        for (Entry entry : last) {
            out.add("  " + describe(entry));
        }
        return out;
    }

    public static List<String> top(SableTrain train) {
        List<String> out = new ArrayList<>();
        ServerSubLevel sub = subOf(train);
        Ledger ledger = ledgerOf(sub);
        if (ledger == null) {
            out.add("no ledger for that car, is /dbgloco force on");
            return out;
        }
        List<Entry> all = new ArrayList<>();
        for (List<Entry> tick : ledger.history) {
            all.addAll(tick);
        }
        all.addAll(ledger.tick);
        all.sort((a, b) -> Double.compare(b.magnitude(), a.magnitude()));
        out.add(shortId(sub.getUniqueId()) + " worst pushes over the last " + ledger.history.size() + " ticks");
        for (Entry entry : all.subList(0, Math.min(DUMP_LINES, all.size()))) {
            out.add("  t" + entry.gameTime() + " " + describe(entry));
        }
        return out;
    }

    public static List<String> calibration(SableTrain train) {
        List<String> out = new ArrayList<>();
        Ledger ledger = ledgerOf(subOf(train));
        if (ledger == null || ledger.calibration.isEmpty()) {
            out.add("nothing measured yet, drive the car with /dbgloco force on");
            return out;
        }
        for (Map.Entry<ForceSource, double[]> hit : ledger.calibration.entrySet()) {
            double[] pair = hit.getValue();
            out.add(String.format(Locale.ROOT, "  %s commanded=%s measured=%s ratio=%s over %d clean substeps",
                    hit.getKey().label(), f(pair[0]), f(pair[1]),
                    f(pair[0] <= 1.0e-9 ? Double.NaN : pair[1] / pair[0]), (int) pair[2]));
        }
        return out;
    }

    public static void reset() {
        LEDGERS.clear();
        chatDeltaV = 1.0;
        anomalyDeltaV = 4.0;
        anomalySpin = 2.5;
    }

    public static void forget(Set<UUID> liveSubLevels) {
        LEDGERS.keySet().retainAll(liveSubLevels);
    }

    private static ServerSubLevel subOf(SableTrain train) {
        return train == null || train.car() == null ? null
                : SubLevelData.resolve(train.level(), train.car().subLevelId());
    }

    private static void say(ServerSubLevel sub, String message) {
        ServerLevel level = sub.getLevel();
        if (!chat || level == null || !DiagnosticsConfig.debugOutput()) {
            return;
        }
        Component text = Component.literal("[force] " + message);
        for (ServerPlayer player : level.players()) {
            player.sendSystemMessage(text);
        }
    }

    private interface BodyAction {
        void run(SableTrain train, ServerSubLevel sub);
    }

    private static void forEachBody(ServerLevel level, BodyAction action) {
        if (level == null) {
            return;
        }
        for (SableTrain train : SableTrainRegistry.all()) {
            if (train.level() != level || train.car() == null || train.isGhost()) {
                continue;
            }
            ServerSubLevel sub = SubLevelData.resolve(level, train.car().subLevelId());
            if (sub != null && !sub.isRemoved()) {
                action.run(train, sub);
            }
        }
    }

    static String f(double d) {
        return String.format(Locale.ROOT, "%.3f", d);
    }

    private static String dir(Vector3dc v) {
        double length = v.length();
        if (length < 1.0e-9) {
            return "(0,0,0)";
        }
        return String.format(Locale.ROOT, "(%.2f,%.2f,%.2f)", v.x() / length, v.y() / length, v.z() / length);
    }

    private static String xz(Vector3dc v) {
        return String.format(Locale.ROOT, "(%.1f,%.1f,%.1f)", v.x(), v.y(), v.z());
    }
}
