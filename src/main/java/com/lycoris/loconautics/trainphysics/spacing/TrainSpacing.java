package com.lycoris.loconautics.trainphysics.spacing;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.trainphysics.drive.TrainBrakes;
import com.lycoris.loconautics.trainphysics.drive.TrainPropulsion;
import com.lycoris.loconautics.common.train.TrainStatusChat;
import com.lycoris.loconautics.trainphysics.state.SableTrainRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.lycoris.loconautics.foundation.LogFormat.shortId;

public final class TrainSpacing {

    private static final double MIN_GAP = 1.5;
    private static final double MIN_DEPTH = 0.25;
    private static final int GATE_RETRY_TICKS = 10;
    private static final int HOLD_EXPIRY_TICKS = 40;
    private static final int NUDGE_DELAY_TICKS = 30;
    private static final double NUDGE_STEP = 0.08;
    private static final double NUDGE_MIN_ALIGNMENT = 0.5;
    private static final double LOADED_BRAKE_MARGIN = 0.7;
    private static final int TRACE_PERIOD_TICKS = 20;

    private record Held(long since, long expires, double dirX, double dirZ) {}

    private static final Map<UUID, Held> HELD = new HashMap<>();
    private static final Map<UUID, Long> NEXT_CHECK = new HashMap<>();
    private static final Map<UUID, Long> LAST_TRACE = new HashMap<>();

    private static final Map<UUID, Footprint> FOOTPRINTS = new HashMap<>();
    private static final Map<UUID, Double> BUDGETS = new HashMap<>();
    private static final Map<UUID, String> BLOCKERS = new HashMap<>();
    private static final Map<UUID, SableTrain> BLOCKED_BY = new HashMap<>();
    private static final Map<UUID, BlockPos> BLOCKED_AT = new HashMap<>();
    private static int cachedTick = -1;
    private static MinecraftServer cachedServer;
    private static int ghostCount;

    private TrainSpacing() {
    }

    public static void clear() {
        HELD.clear();
        NEXT_CHECK.clear();
        LAST_TRACE.clear();
        FOOTPRINTS.clear();
        BUDGETS.clear();
        BLOCKERS.clear();
        BLOCKED_BY.clear();
        BLOCKED_AT.clear();
        cachedTick = -1;
        cachedServer = null;
        ghostCount = 0;
    }

    public static void forget(Set<UUID> live) {
        HELD.keySet().retainAll(live);
        NEXT_CHECK.keySet().retainAll(live);
        LAST_TRACE.keySet().retainAll(live);
    }

    public static double ghostStep(SableTrain train) {
        double ds = train.speed();
        MinecraftServer server = serverOf(train);
        if (server == null) {
            return ds;
        }
        long now = train.level().getGameTime();
        Held held = heldFor(train, now);
        if (held != null) {
            return holdStep(train, held, now, server);
        }
        if (ds == 0.0) {
            return 0.0;
        }
        refresh(server);
        double cap = consistBudget(train, true);
        if (cap == Double.POSITIVE_INFINITY) {
            TrainStatusChat.unblocked(train);
            return ds;
        }
        double allowed = Math.min(cap, Math.sqrt(2.0 * loadedBrake() * cap));
        if (Math.abs(ds) <= allowed) {
            return ds;
        }
        double clamped = Math.copySign(allowed, ds);
        train.setSpeed(clamped);
        UUID blocked = blockedMember(train);
        trace(train, now, "ghost brake", String.format("speed=%.3f allowed=%.3f budget=%.2f blocker=%s", ds, allowed,
                cap, blocked == null ? "none" : BLOCKERS.get(blocked)));
        if (BLOCKED_AT.containsKey(blocked)) {
            TrainStatusChat.blockedByBlocks(train, BLOCKED_AT.get(blocked));
        } else if (blocked != null) {
            TrainStatusChat.blockedBy(train, BLOCKED_BY.get(blocked));
        }
        return clamped;
    }

    private static UUID blockedMember(SableTrain train) {
        for (SableTrain member : membersOf(train)) {
            if (BLOCKED_BY.containsKey(member.id()) || BLOCKED_AT.containsKey(member.id())) {
                return member.id();
            }
        }
        return null;
    }

    public static void limitLoaded(SableTrain train) {
        MinecraftServer server = serverOf(train);
        if (server == null || train.isGhost() || train.isDerailed() || train.isPhysics()) {
            return;
        }
        refresh(server);
        if (ghostCount == 0) {
            return;
        }
        double target = train.targetSpeed();
        double cap = consistBudget(train, false);
        if (cap == Double.POSITIVE_INFINITY) {
            return;
        }
        double allowed = Math.sqrt(2.0 * loadedBrake() * cap);
        double motion = Math.abs(train.speed()) > 1.0e-3 ? train.speed() : target;
        if (Math.abs(target) > allowed && target * motion >= 0.0) {
            train.setTargetSpeed(Math.copySign(allowed, target));
            trace(train, train.level().getGameTime(), "loaded brake",
                    String.format("target=%.3f allowed=%.3f budget=%.2f blocker=%s", target, allowed, cap,
                            BLOCKERS.get(train.id())));
        }
        double hardAllowed = Math.sqrt(2.0 * emergencyBrake() * cap);
        if (Math.abs(train.speed()) > hardAllowed) {
            double before = train.speed();
            train.setSpeed(Math.copySign(hardAllowed, before));
            trace(train, train.level().getGameTime(), "loaded emergency",
                    String.format("speed=%.3f hard=%.3f budget=%.2f blocker=%s", before, hardAllowed, cap,
                            BLOCKERS.get(train.id())));
        }
    }

    public static boolean mayEmbody(SableTrain train) {
        MinecraftServer server = serverOf(train);
        if (server == null || train.isDerailed() || train.isPhysics()) {
            return true;
        }
        long now = train.level().getGameTime();
        UUID id = train.id();
        Long next = NEXT_CHECK.get(id);
        if (next != null && now < next) {
            return false;
        }
        refresh(server);
        Footprint blocked = null;
        Footprint blocker = null;
        for (SableTrain member : membersOf(train)) {
            Footprint own = FOOTPRINTS.get(member.id());
            Footprint other = own == null ? null : firstOverlap(member, own);
            if (other != null) {
                blocked = own;
                blocker = other;
                break;
            }
        }
        Held old = HELD.get(id);
        if (blocker == null) {
            NEXT_CHECK.remove(id);
            TrainStatusChat.unblocked(train);
            if (HELD.remove(id) != null) {
                trace(train, now, "gate open", "no overlap left");
            }
            return true;
        }
        NEXT_CHECK.put(id, now + GATE_RETRY_TICKS);
        double dx = blocker.x() - blocked.x();
        double dz = blocker.z() - blocked.z();
        double dist = Math.hypot(dx, dz);
        double aligned = dist < 1.0e-6 ? 0.0 : (dx * blocked.ax() + dz * blocked.az()) / dist;
        double away = Math.abs(aligned) >= NUDGE_MIN_ALIGNMENT ? -Math.signum(aligned) : 0.0;
        HELD.put(id, new Held(old == null ? now : old.since(), now + HOLD_EXPIRY_TICKS, blocked.ax() * away,
                blocked.az() * away));
        TrainStatusChat.blockedBy(train, blocker.train());
        trace(train, now, "gate closed", String.format("car %s overlaps %s depth=%.2f nudge=%s",
                shortId(blocked.train().id()), shortId(blocker.train().id()),
                Footprint.overlapDepth(blocked, blocker), away != 0.0));
        return false;
    }

    public static void release(SableTrain train) {
        NEXT_CHECK.remove(train.id());
        HELD.remove(train.id());
    }

    private static double holdStep(SableTrain train, Held held, long now, MinecraftServer server) {
        train.setSpeed(0.0);
        if (now - held.since() < NUDGE_DELAY_TICKS || (held.dirX() == 0.0 && held.dirZ() == 0.0)) {
            return 0.0;
        }
        refresh(server);
        Footprint own = FOOTPRINTS.get(train.id());
        if (own == null) {
            return 0.0;
        }
        double along = held.dirX() * own.ax() + held.dirZ() * own.az();
        if (Math.abs(along) < NUDGE_MIN_ALIGNMENT) {
            return 0.0;
        }
        double sign = Math.signum(along);
        double room = roomToward(train, own, sign);
        double step = Math.min(NUDGE_STEP, room);
        if (step <= 1.0e-4) {
            trace(train, now, "nudge blocked", String.format("room=%.2f toward sign %.0f", room, sign));
            return 0.0;
        }
        trace(train, now, "nudge", String.format("step=%.3f sign=%.0f room=%.2f", step, sign, room));
        return sign * step;
    }

    private static double roomToward(SableTrain train, Footprint own, double sign) {
        double min = Double.POSITIVE_INFINITY;
        for (SableTrain member : membersOf(train)) {
            Footprint print = FOOTPRINTS.get(member.id());
            if (print == null) {
                continue;
            }
            double dot = member == train ? 1.0 : print.ax() * own.ax() + print.az() * own.az();
            if (Math.abs(dot) < NUDGE_MIN_ALIGNMENT) {
                continue;
            }
            min = Math.min(min, scanBudget(member, print, sign * Math.signum(dot), NUDGE_STEP * 4.0 + MIN_GAP, false));
        }
        return min;
    }

    private static Held heldFor(SableTrain train, long now) {
        Held own = HELD.get(train.id());
        if (own != null) {
            return own.expires() >= now ? own : null;
        }
        Set<UUID> consist = train.consistCache();
        if (consist.size() <= 1) {
            return null;
        }
        for (UUID id : consist) {
            SableTrain mate = SableTrainRegistry.bySubLevel(id);
            Held held = mate == null ? null : HELD.get(mate.id());
            if (held != null && held.expires() >= now) {
                return held;
            }
        }
        return null;
    }

    private static double consistBudget(SableTrain train, boolean ghosts) {
        double cap = memberBudget(train, ghosts);
        if (train.consistCache().size() <= 1) {
            return cap;
        }
        for (UUID id : train.consistCache()) {
            SableTrain member = SableTrainRegistry.bySubLevel(id);
            if (member != null && member.isGhost() == ghosts) {
                cap = Math.min(cap, memberBudget(member, ghosts));
            }
        }
        return cap;
    }

    private static double memberBudget(SableTrain member, boolean ghosts) {
        if (member.isDerailed() || member.isPhysics() || member.car() == null) {
            return Double.POSITIVE_INFINITY;
        }
        Double known = BUDGETS.get(member.id());
        if (known != null) {
            return known;
        }
        double speed = member.isGhost() || Math.abs(member.speed()) > 1.0e-3 ? member.speed() : member.targetSpeed();
        Footprint own = FOOTPRINTS.get(member.id());
        double budget = Double.POSITIVE_INFINITY;
        if (own != null && Math.abs(speed) > 1.0e-4) {
            double v = Math.max(Math.abs(speed), Math.abs(member.targetSpeed()));
            double look = v * v / (2.0 * loadedBrake()) + 2.0 * v + MIN_GAP + 6.0;
            budget = scanBudget(member, own, Math.signum(speed), look, !ghosts);
        }
        BUDGETS.put(member.id(), budget);
        return budget;
    }

    private static double scanBudget(SableTrain train, Footprint own, double sign, double look, boolean ghostsOnly) {
        List<Footprint> near = null;
        for (Footprint other : FOOTPRINTS.values()) {
            if (other.level() != own.level() || (ghostsOnly && !other.train().isGhost())
                    || sameConsist(train, other.train())
                    || own.horizontalDistance(other.x(), other.z()) > look + own.halfLength() + other.reach() + 2.0) {
                continue;
            }
            if (near == null) {
                near = new ArrayList<>();
            }
            near.add(other);
        }
        boolean checkBlocks = !ghostsOnly && GhostObstacles.any(train.level());
        if (near == null && !checkBlocks) {
            return Double.POSITIVE_INFINITY;
        }
        RouteScout.Scan scan = RouteScout.scan(train, own, sign, look, near == null ? List.of() : near,
                checkBlocks ? train.level() : null);
        if (scan == null) {
            return Double.POSITIVE_INFINITY;
        }
        if (scan.block() != null) {
            BLOCKED_AT.put(train.id(), scan.block());
            BLOCKERS.put(train.id(), String.format("blocks at %s free=%.2f", scan.block().toShortString(), scan.free()));
            return Math.max(0.0, scan.free() - MIN_GAP);
        }
        if (scan.hit() == null) {
            return Double.POSITIVE_INFINITY;
        }
        Footprint hit = scan.hit();
        double centers = own.horizontalDistance(hit.x(), hit.z());
        BLOCKED_BY.put(train.id(), hit.train());
        BLOCKERS.put(train.id(), String.format(
                "%s %s free=%.2f centers=%.2f endGap=%.2f ownHalf=%.2f otherHalf=%.2f extentNull=%s/%s [%s]",
                shortId(hit.train().id()), hit.train().isGhost() ? "ghost" : "loaded", scan.free(), centers,
                centers - own.halfLength() - hit.halfLength(), own.halfLength(), hit.halfLength(),
                train.extent() == null, hit.train().extent() == null,
                com.lycoris.loconautics.trainphysics.debug.GhostConsistTrace.blockerText(hit.train())));
        return Math.max(0.0, scan.free() - MIN_GAP);
    }

    private static Footprint firstOverlap(SableTrain train, Footprint own) {
        for (Footprint other : FOOTPRINTS.values()) {
            if (other.level() == own.level() && !sameConsist(train, other.train())
                    && Footprint.overlapDepth(own, other) > MIN_DEPTH) {
                return other;
            }
        }
        return null;
    }

    private static boolean sameConsist(SableTrain a, SableTrain b) {
        return a == b || a.consistCache().contains(b.car().subLevelId())
                || b.consistCache().contains(a.car().subLevelId());
    }

    private static List<SableTrain> membersOf(SableTrain train) {
        List<SableTrain> out = new ArrayList<>();
        out.add(train);
        for (UUID id : train.consistCache()) {
            SableTrain member = SableTrainRegistry.bySubLevel(id);
            if (member != null && member != train && member.isGhost()) {
                out.add(member);
            }
        }
        return out;
    }

    // nothing to build while every body is loaded
    private static void refresh(MinecraftServer server) {
        int tick = server.getTickCount();
        if (tick == cachedTick && server == cachedServer) {
            return;
        }
        cachedTick = tick;
        cachedServer = server;
        FOOTPRINTS.clear();
        BUDGETS.clear();
        BLOCKERS.clear();
        BLOCKED_BY.clear();
        BLOCKED_AT.clear();
        ghostCount = 0;
        for (SableTrain train : SableTrainRegistry.all()) {
            if (train.isGhost()) {
                ghostCount++;
            }
        }
        if (ghostCount == 0) {
            return;
        }
        for (SableTrain train : SableTrainRegistry.all()) {
            Footprint print = Footprint.of(train);
            if (print != null) {
                FOOTPRINTS.put(train.id(), print);
            }
        }
    }

    private static MinecraftServer serverOf(SableTrain train) {
        return train.level() == null || train.car() == null || train.car().subLevelId() == null ? null
                : train.level().getServer();
    }

    private static double emergencyBrake() {
        return TrainBrakes.BRAKE_DECEL * TrainPropulsion.MPS2_TO_BPT2;
    }

    private static double loadedBrake() {
        return TrainPropulsion.brakeDecelLimit() * TrainPropulsion.MPS2_TO_BPT2 * LOADED_BRAKE_MARGIN;
    }

    // temporary, tag ghostcol, one line per train per second at most
    private static void trace(SableTrain train, long now, String what, String detail) {
        Long last = LAST_TRACE.get(train.id());
        if (last != null && now - last < TRACE_PERIOD_TICKS && !what.equals("gate open")) {
            return;
        }
        LAST_TRACE.put(train.id(), now);
        LoconauticsConstants.LOGGER.info("[ghostcol] {} {} {}", shortId(train.id()), what, detail);
    }
}
