package com.lycoris.loconautics.trainphysics.drive;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.common.advancement.TrainAdvancements;
import com.lycoris.loconautics.common.impact.ImpactSpread;
import com.lycoris.loconautics.common.impact.TrainImpacts;
import com.lycoris.loconautics.common.impact.TrainKnockback;
import com.lycoris.loconautics.common.train.TrainStatusChat;
import com.lycoris.loconautics.config.LoconauticsConfig;
import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.trainphysics.sublevel.SubLevelContacts;
import com.lycoris.loconautics.common.sable.SubLevelData;
import com.lycoris.loconautics.trainphysics.rail.RailFrame;
import com.lycoris.loconautics.trainphysics.spawn.SubLevelMergeWatcher;
import com.lycoris.loconautics.common.sable.SubLevelOverlap;
import com.lycoris.loconautics.trainphysics.TrainConsist;
import com.lycoris.loconautics.trainphysics.debug.force.ForceSource;
import com.lycoris.loconautics.trainphysics.debug.force.ForceTrace;
import com.lycoris.loconautics.trainphysics.state.SableTrainRegistry;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;


import static com.lycoris.loconautics.foundation.LogFormat.shortId;

public final class CarriageCollisions {

    private CarriageCollisions() {
    }

    private static final double MIN_DEPTH = 0.25;
    private static final double MAX_PUSH = 0.4;
    private static final double PUSH_FRACTION = 0.5;
    private static final double MIN_RAIL_ALIGNMENT = 0.2;
    private static final double MAX_SEPARATING_SPEED = 0.05;
    // a derailed pair has rapier contacts off, so the depth itself has to set how fast it is pushed out
    private static final double MAX_SEPARATE_SPEED = 1.0;
    private static final double SEPARATE_PER_DEPTH = 3.0;
    private static final int LINGER_TICKS = 10;
    private static final int STALL_TICKS = 50;
    // a pair saved overlapping gets this long after load to separate before it can count as stuck
    private static final int LOAD_GRACE_TICKS = 100;
    private static final double STALL_PROGRESS = 0.02;
    // a game tick of two cars closing at top speed is under 4 blocks, so rapier must never see the touch
    private static final double GUARD_NEAR = 6.0;
    private static final double GUARD_CLEAR = 9.0;

    private static final Map<SubLevelContacts.Pair, Integer> ACTIVE = new HashMap<>();
    private static final Map<SubLevelContacts.Pair, Stall> STALLS = new HashMap<>();
    private static final Set<SubLevelContacts.Pair> GUARDED = new HashSet<>();
    private static final Set<SubLevelContacts.Pair> COUPLED = new HashSet<>();
    private static final Map<UUID, Long> SEEN = new HashMap<>();
    private static long age;

    private static final class Stall {
        private double shallowest;
        private int ticks;

        private Stall(double shallowest) {
            this.shallowest = shallowest;
        }
    }

    public static void clear() {
        ACTIVE.clear();
        STALLS.clear();
        GUARDED.clear();
        COUPLED.clear();
        SEEN.clear();
    }

    private record Entry(SableTrain train, ServerSubLevel sub, Set<UUID> coupled) {}

    public static void tick() {
        Map<ServerLevel, List<Entry>> byLevel = new HashMap<>();
        for (SableTrain train : SableTrainRegistry.all()) {
            ServerLevel level = train.level();
            if (level == null || train.isGhost() || train.car() == null) {
                continue;
            }
            ServerSubLevel sub = SubLevelData.resolve(level, train.car().subLevelId());
            if (sub != null) {
                byLevel.computeIfAbsent(level, key -> new ArrayList<>())
                        .add(new Entry(train, sub, TrainConsist.neighbours(level, sub.getUniqueId())));
            }
        }

        age++;
        Set<UUID> present = new HashSet<>();
        for (List<Entry> entries : byLevel.values()) {
            for (Entry entry : entries) {
                present.add(entry.sub().getUniqueId());
                SEEN.putIfAbsent(entry.sub().getUniqueId(), age);
            }
        }
        SEEN.keySet().retainAll(present);
        guardRailedPairs(byLevel.values());
        holdCoupledPairs(byLevel.values());
        Set<SubLevelContacts.Pair> hitting = new HashSet<>();
        for (Map.Entry<ServerLevel, List<Entry>> level : byLevel.entrySet()) {
            List<Entry> entries = level.getValue();
            ListIterator<Entry> outer = entries.listIterator();
            while (outer.hasNext()) {
                Entry a = outer.next();
                for (Entry b : entries.subList(outer.nextIndex(), entries.size())) {
                    pairOfTrains(a, b, hitting);
                }
            }
            for (Entry entry : entries) {
                TrainImpacts.cartIntoCreate(entry.train(), entry.sub());
            }
        }
        expire(hitting);
    }

    // railed cars are stopped by the contact link in knucklerailcoupler, rapiers own contact kicks them 8 to 10 bs
    private static void guardRailedPairs(Iterable<List<Entry>> levels) {
        Set<SubLevelContacts.Pair> kept = new HashSet<>();
        Set<SubLevelContacts.Pair> handedOver = new HashSet<>();
        for (List<Entry> entries : levels) {
            for (int i = 0; i < entries.size(); i++) {
                for (int j = i + 1; j < entries.size(); j++) {
                    Entry a = entries.get(i);
                    Entry b = entries.get(j);
                    if (!guardable(a) || !guardable(b)) {
                        continue;
                    }
                    SubLevelContacts.Pair key = SubLevelContacts.Pair.of(a.sub().getUniqueId(), b.sub().getUniqueId());
                    if (a.coupled().contains(b.sub().getUniqueId())
                            || com.lycoris.loconautics.content.buffer.BufferContactManager.engaged(
                                    a.sub().getUniqueId(), b.sub().getUniqueId())) {
                        handedOver.add(key);
                        continue;
                    }
                    if (within(a.sub(), b.sub(), GUARDED.contains(key) ? GUARD_CLEAR : GUARD_NEAR)) {
                        SubLevelContacts.disableCoupled(a.sub(), b.sub());
                        kept.add(key);
                    }
                }
            }
        }
        for (SubLevelContacts.Pair key : GUARDED) {
            if (!kept.contains(key) && !handedOver.contains(key) && !ACTIVE.containsKey(key)) {
                SubLevelContacts.enableCoupled(key.first(), key.second());
            }
        }
        GUARDED.clear();
        GUARDED.addAll(kept);
    }

    private static void holdCoupledPairs(Iterable<List<Entry>> levels) {
        Set<SubLevelContacts.Pair> now = new HashSet<>();
        for (List<Entry> entries : levels) {
            for (Entry a : entries) {
                for (Entry b : entries) {
                    if (a.sub().getUniqueId().compareTo(b.sub().getUniqueId()) >= 0
                            || !a.coupled().contains(b.sub().getUniqueId())) {
                        continue;
                    }
                    SubLevelContacts.disableCoupled(a.sub(), b.sub());
                    now.add(SubLevelContacts.Pair.of(a.sub().getUniqueId(), b.sub().getUniqueId()));
                }
            }
        }
        for (SubLevelContacts.Pair key : COUPLED) {
            if (!now.contains(key) && !GUARDED.contains(key) && !ACTIVE.containsKey(key)) {
                SubLevelContacts.enableCoupled(key.first(), key.second());
            }
        }
        COUPLED.clear();
        COUPLED.addAll(now);
    }

    private static boolean guardable(Entry entry) {
        return RailFrame.modeOf(entry.train()) == RailFrame.Mode.RIGID && !TrainPropulsion.isMonorail(entry.train())
                && !ExternalForceDerail.staffHolds(entry.sub());
    }

    private static boolean within(ServerSubLevel a, ServerSubLevel b, double margin) {
        BoundingBox3dc boxA = a.boundingBox();
        BoundingBox3dc boxB = b.boundingBox();
        if (boxA == null || boxB == null) {
            return false;
        }
        return boxA.minX() - margin <= boxB.maxX() && boxB.minX() - margin <= boxA.maxX()
                && boxA.minY() - margin <= boxB.maxY() && boxB.minY() - margin <= boxA.maxY()
                && boxA.minZ() - margin <= boxB.maxZ() && boxB.minZ() - margin <= boxA.maxZ();
    }

    private static void pairOfTrains(Entry a, Entry b, Set<SubLevelContacts.Pair> hitting) {
        if (a.coupled().contains(b.sub().getUniqueId())) {
            return;
        }
        if (com.lycoris.loconautics.content.buffer.BufferContactManager.engaged(a.sub().getUniqueId(), b.sub().getUniqueId())) {
            return;
        }
        if (SubLevelMergeWatcher.mergingPair(a.sub().getLevel(), a.sub().getUniqueId(), b.sub().getUniqueId())) {
            return;
        }
        SubLevelOverlap.Hit hit = SubLevelOverlap.test(a.sub(), b.sub());
        SubLevelContacts.Pair key = SubLevelContacts.Pair.of(a.sub().getUniqueId(), b.sub().getUniqueId());
        if (hit == null || hit.depth() < MIN_DEPTH) {
            return;
        }
        hitting.add(key);
        boolean fresh = ACTIVE.put(key, LINGER_TICKS) == null;
        if (stalled(key, a, b, hit)) {
            derailStalled(key, a, b, hit);
        }
        resolve(a, b, hit, fresh);
    }

    // the coupler pushes railed cars apart along their rails, a pair that stops getting shallower cannot be solved that way
    private static boolean stalled(SubLevelContacts.Pair key, Entry a, Entry b, SubLevelOverlap.Hit hit) {
        if (loose(a) || loose(b) || TrainPropulsion.isMonorail(a.train()) || TrainPropulsion.isMonorail(b.train())
                || ExternalForceDerail.staffHolds(a.sub()) || ExternalForceDerail.staffHolds(b.sub())) {
            STALLS.remove(key);
            return false;
        }
        if (settling(a) || settling(b)) {
            STALLS.remove(key);
            return false;
        }
        Stall stall = STALLS.computeIfAbsent(key, ignored -> new Stall(hit.depth()));
        if (hit.depth() < stall.shallowest - STALL_PROGRESS) {
            stall.shallowest = hit.depth();
            stall.ticks = 0;
            return false;
        }
        return ++stall.ticks >= STALL_TICKS;
    }

    private static boolean settling(Entry entry) {
        return age - SEEN.getOrDefault(entry.sub().getUniqueId(), age) < LOAD_GRACE_TICKS;
    }

    private static boolean loose(Entry entry) {
        return entry.train().isDerailed() || entry.train().isPhysics();
    }

    private static void derailStalled(SubLevelContacts.Pair key, Entry a, Entry b, SubLevelOverlap.Hit hit) {
        STALLS.remove(key);
        Vector3d kick = new Vector3d(hit.axis()).mul(separationRoom(a, b, hit) * 0.5);
        TrainImpacts.throwOff(a.train(), a.sub(), new Vector3d(kick).negate());
        TrainImpacts.throwOff(b.train(), b.sub(), kick);
        LoconauticsConstants.LOGGER.info("[collision] cars {} and {} stayed inside each other at depth {}, derailed both",
                shortId(a.sub().getUniqueId()), shortId(b.sub().getUniqueId()),
                String.format(Locale.ROOT, "%.2f", hit.depth()));
    }

    private static void resolve(Entry a, Entry b, SubLevelOverlap.Hit hit, boolean fresh) {
        double closing = closing(a.sub(), b.sub(), hit);
        boolean faultIsA = Math.abs(closing) > 1.0e-3
                ? closingShareOfA(a.sub(), b.sub(), hit) >= 0.5
                : Math.abs(a.train().speed()) >= Math.abs(b.train().speed());
        Entry fault = faultIsA ? a : b;
        Entry other = faultIsA ? b : a;
        if (fresh) {
            TrainStatusChat.crashWith(a.train(), b.train());
            TrainStatusChat.crashWith(b.train(), a.train());
            TrainAdvancements.crashed(a.sub(), a.train());
            TrainAdvancements.crashed(b.sub(), b.train());
            derailOnImpact(a, b, hit, Math.max(0.0, closing));
            ForceTrace.note(fault.sub(), ForceSource.COLLISION, contactPoint(a.sub(), b.sub()),
                    "hit " + shortId(other.sub().getUniqueId()) + " depth=" + String.format(Locale.ROOT, "%.2f",
                            hit.depth()) + " closing=" + String.format(Locale.ROOT, "%.2f", closing));
        }
        Vector3d push = new Vector3d(hit.axis()).mul(faultIsA ? -1.0 : 1.0);
        double distance = Math.min(MAX_PUSH, hit.depth() * PUSH_FRACTION);
        if (fault.train().isDerailed() || fault.train().isPhysics()) {
            SubLevelContacts.disable(fault.sub(), other.sub());
            double room = fresh ? 0.0 : separationRoom(a, b, hit);
            if (room > 0.0) {
                Vector3d dv = new Vector3d(push).mul(room);
                TrainKnockback.applyKnockbackNow(fault.train(), dv);
                ForceTrace.deltaV(fault.sub(), ForceSource.COLLISION, dv, contactPoint(a.sub(), b.sub()),
                        "loose car pushed clear of " + shortId(other.sub().getUniqueId()));
            }
            return;
        }
        if (!other.train().isDerailed() && !other.train().isPhysics()) {
            return;
        }
        Vec3 forward = fault.train().car().carriage().forward();
        double along = push.x * forward.x + push.y * forward.y + push.z * forward.z;
        double sign = Math.abs(along) >= MIN_RAIL_ALIGNMENT
                ? Math.signum(along)
                : -Math.signum(fault.train().speed());
        if (sign != 0.0) {
            rewindConsist(fault, sign * distance, forward);
            ForceTrace.note(fault.sub(), ForceSource.RAIL_REWIND, contactPoint(a.sub(), b.sub()),
                    "backed the rake " + String.format(Locale.ROOT, "%.2f", sign * distance) + " along its rail");
        }
        SubLevelContacts.disable(fault.sub(), other.sub());
    }

    private static void derailOnImpact(Entry a, Entry b, SubLevelOverlap.Hit hit, double closing) {
        double massA = TrainImpacts.rakeMass(a.train(), a.sub());
        double massB = TrainImpacts.rakeMass(b.train(), b.sub());
        double changeA = TrainImpacts.speedChange(closing, massA, massB);
        double changeB = TrainImpacts.speedChange(closing, massB, massA);
        if (!LoconauticsConfig.DERAIL_COUPLED_COLLISION.get()
                && TrainConsist.connectedSubLevels(a.sub().getLevel(), a.sub().getUniqueId()).contains(b.sub().getUniqueId())) {
            return;
        }
        boolean offA = TrainImpacts.derails(changeA);
        boolean offB = TrainImpacts.derails(changeB);
        Vector3d kickA = new Vector3d(hit.axis()).mul(-changeA);
        Vector3d kickB = new Vector3d(hit.axis()).mul(changeB);
        if (offA) {
            TrainImpacts.throwOff(a.train(), a.sub(), kickA);
        }
        if (offB) {
            TrainImpacts.throwOff(b.train(), b.sub(), kickB);
        }
        shoveConsist(a, kickA, offA && !TrainPropulsion.isMonorail(a.train()));
        shoveConsist(b, kickB, offB && !TrainPropulsion.isMonorail(b.train()));
    }

    private static void shoveConsist(Entry struck, Vector3d deltaV, boolean struckAlreadyHit) {
        ImpactSpread.spread(struck.train().level(), struck.train(), deltaV, struckAlreadyHit);
    }

    // how much more separating speed the pair may gain, so a push never leaves a rebound on the cars
    private static double separationRoom(Entry a, Entry b, SubLevelOverlap.Hit hit) {
        double wanted = Math.max(MAX_SEPARATING_SPEED, Math.min(MAX_SEPARATE_SPEED, hit.depth() * SEPARATE_PER_DEPTH));
        return Math.max(0.0, wanted + closing(a.sub(), b.sub(), hit));
    }

    private static double closing(ServerSubLevel a, ServerSubLevel b, SubLevelOverlap.Hit hit) {
        Vector3d point = contactPoint(a, b);
        Vector3d velA = TrackHostMotion.railVelocity(a.getLevel(), a, point, new Vector3d());
        Vector3d velB = TrackHostMotion.railVelocity(b.getLevel(), b, point, new Vector3d());
        return velA.sub(velB).dot(hit.axis());
    }

    // the share of that closing speed a is bringing, so the car standing still is never the one blamed
    private static double closingShareOfA(ServerSubLevel a, ServerSubLevel b, SubLevelOverlap.Hit hit) {
        Vector3d point = contactPoint(a, b);
        double intoB = TrackHostMotion.railVelocity(a.getLevel(), a, point, new Vector3d()).dot(hit.axis());
        double intoA = -TrackHostMotion.railVelocity(b.getLevel(), b, point, new Vector3d()).dot(hit.axis());
        double total = Math.abs(intoA) + Math.abs(intoB);
        return total <= 1.0e-9 ? 0.5 : Math.max(0.0, intoB) / total;
    }

    private static Vector3d contactPoint(ServerSubLevel a, ServerSubLevel b) {
        return SubLevelData.position(a).add(SubLevelData.position(b)).mul(0.5);
    }

    private static void rewindConsist(Entry fault, double ds, Vec3 faultForward) {
        ServerLevel level = fault.train().level();
        for (UUID id : TrainConsist.orderedChain(level, fault.sub().getUniqueId())) {
            SableTrain member = SableTrainRegistry.bySubLevel(id);
            if (member == null || member.car() == null || member.car().carriage() == null
                    || member.isDerailed() || member.isPhysics()) {
                continue;
            }
            Vec3 memberForward = member.car().carriage().forward();
            double dot = memberForward.x * faultForward.x + memberForward.y * faultForward.y
                    + memberForward.z * faultForward.z;
            TrainRailAdvance.advanceRail(member.car(), dot < 0.0 ? -ds : ds);
        }
    }

    private static void expire(Set<SubLevelContacts.Pair> hitting) {
        STALLS.keySet().retainAll(hitting);
        Iterator<Map.Entry<SubLevelContacts.Pair, Integer>> it = ACTIVE.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<SubLevelContacts.Pair, Integer> entry = it.next();
            if (hitting.contains(entry.getKey())) {
                continue;
            }
            int left = entry.getValue() - 1;
            if (left > 0) {
                entry.setValue(left);
                continue;
            }
            if (!GUARDED.contains(entry.getKey())) {
                SubLevelContacts.enableCoupled(entry.getKey().first(), entry.getKey().second());
            }
            it.remove();
        }
    }
}
