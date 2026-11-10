package com.lycoris.loconautics.trainphysics.drive;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.trainphysics.SableTrain.Bogey;
import com.lycoris.loconautics.trainphysics.SableTrain.Car;
import com.lycoris.loconautics.common.sable.SubLevelData;
import com.lycoris.loconautics.trainphysics.TrainConsist;
import com.lycoris.loconautics.trainphysics.spawn.SableTrainSpawner;
import com.lycoris.loconautics.trainphysics.state.SableTrainRegistry;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class DockGroups {

    private static final int REFRESH_TICKS = 4;

    private static volatile Map<UUID, UUID> leaderOf = Map.of();
    private static volatile Map<UUID, List<UUID>> followersOf = Map.of();
    private static final Map<Bogey, ServerSubLevel> OWNER = Collections.synchronizedMap(new IdentityHashMap<>());
    private static int counter;

    private DockGroups() {
    }

    public static boolean isFollower(UUID subLevelId) {
        return leaderOf.containsKey(subLevelId);
    }

    public static boolean hasFollowers(UUID subLevelId) {
        List<UUID> followers = followersOf.get(subLevelId);
        return followers != null && !followers.isEmpty();
    }

    public static boolean isFollowerOf(UUID subLevelId, UUID leaderId) {
        return leaderId != null && leaderId.equals(leaderOf.get(subLevelId));
    }

    public static void clear() {
        leaderOf = Map.of();
        followersOf = Map.of();
        OWNER.clear();
    }

    private static boolean eligible(SableTrain train) {
        return train.car() != null && train.car().subLevelId() != null && train.car().carriage() != null
                && !train.isGhost() && !train.isDerailed() && !train.isPhysics() && !train.hasOffTrackBogey();
    }

    public static void refresh() {
        if (counter++ % REFRESH_TICKS != 0) {
            return;
        }
        Map<UUID, SableTrain> bySub = new HashMap<>();
        for (SableTrain train : SableTrainRegistry.all()) {
            if (eligible(train)) {
                bySub.put(train.car().subLevelId(), train);
            }
        }
        Map<UUID, UUID> leaders = new HashMap<>();
        Map<UUID, List<UUID>> followers = new HashMap<>();
        Set<UUID> done = new HashSet<>();
        for (SableTrain train : bySub.values()) {
            UUID id = train.car().subLevelId();
            if (done.contains(id)) {
                continue;
            }
            Set<UUID> group = new LinkedHashSet<>();
            group.add(id);
            for (UUID welded : TrainConsist.weldedSubLevels(train.level(), id)) {
                SableTrain other = bySub.get(welded);
                if (other != null && other.level() == train.level()) {
                    group.add(welded);
                }
            }
            if (group.size() < 2) {
                continue;
            }
            done.addAll(group);
            UUID leader = pickLeader(group, bySub);
            List<UUID> rest = new ArrayList<>(group);
            rest.remove(leader);
            followers.put(leader, rest);
            for (UUID follower : rest) {
                leaders.put(follower, leader);
            }
        }
        if (!leaders.equals(leaderOf)) {
            for (UUID follower : leaders.keySet()) {
                if (!leaderOf.containsKey(follower)) {
                    reseatRails(bySub.get(follower));
                }
            }
        }
        leaderOf = leaders;
        followersOf = followers;
        OWNER.clear();
    }

    private static void reseatRails(SableTrain follower) {
        if (follower == null || follower.car() == null) {
            return;
        }
        ServerSubLevel sub = SubLevelData.resolve(follower.level(), follower.car().subLevelId());
        if (sub == null) {
            return;
        }
        double targetSpeed = follower.targetSpeed();
        SableTrainSpawner.reseatOnRail(follower, sub);
        follower.setTargetSpeed(targetSpeed);
    }

    private static UUID pickLeader(Set<UUID> group, Map<UUID, SableTrain> bySub) {
        for (UUID id : group) {
            if (followersOf.containsKey(id) && group.containsAll(followersOf.get(id))) {
                return id;
            }
        }
        UUID best = null;
        for (UUID id : group) {
            if (best == null) {
                best = id;
                continue;
            }
            boolean powered = bySub.get(id).isPowered();
            boolean bestPowered = bySub.get(best).isPowered();
            if (powered != bestPowered ? powered : id.compareTo(best) < 0) {
                best = id;
            }
        }
        return best;
    }

    public static Car composite(ServerSubLevel leaderSub, SableTrain leader, Car car) {
        List<UUID> followers = followersOf.get(car.subLevelId());
        if (followers == null || followers.isEmpty()) {
            return car;
        }
        List<Bogey> all = new ArrayList<>(car.bogeys());
        for (Bogey bogey : car.bogeys()) {
            OWNER.put(bogey, leaderSub);
        }
        for (UUID followerId : followers) {
            SableTrain follower = SableTrainRegistry.bySubLevel(followerId);
            ServerSubLevel followerSub = follower == null || follower.car() == null ? null
                    : SubLevelData.resolve(leaderSub.getLevel(), followerId);
            if (followerSub == null) {
                continue;
            }
            follower.setSpeed(leader.speed());
            for (Bogey bogey : follower.car().bogeys()) {
                OWNER.put(bogey, followerSub);
                all.add(bogey);
            }
        }
        if (all.size() == car.bogeys().size()) {
            return car;
        }
        Vector3d axis = worldAxis(leaderSub, car);
        if (!axis.isFinite()) {
            LoconauticsConstants.LOGGER.warn("[dock] leader {} has a non finite body axis, group skipped", car.subLevelId());
            return car;
        }
        Comparator<Bogey> alongBody = Comparator.comparingDouble(b -> worldOf(leaderSub, b).dot(axis));
        all.sort(alongBody);
        return new Car(car.subLevelId(), car.carriage(), all, car.localLead(), car.localTrail());
    }

    private static Vector3d worldAxis(ServerSubLevel leaderSub, Car car) {
        Vector3d ref = car.carriage().referenceForward();
        if (ref == null || ref.lengthSquared() < 1.0e-9) {
            ref = new Vector3d(0.0, 0.0, 1.0);
        }
        return SubLevelData.directionOf(leaderSub, ref).normalize();
    }

    private static Vector3d worldOf(ServerSubLevel fallback, Bogey bogey) {
        ServerSubLevel sub = OWNER.get(bogey);
        return SubLevelData.worldOfOffset(sub == null || sub.isRemoved() ? fallback : sub, bogey.localPos());
    }

    public static ServerSubLevel ownerOf(Bogey bogey, ServerSubLevel fallback) {
        ServerSubLevel owner = OWNER.get(bogey);
        return owner == null || owner.isRemoved() ? fallback : owner;
    }

    public static boolean grouped(Car car) {
        for (Bogey bogey : car.bogeys()) {
            if (OWNER.containsKey(bogey)) {
                return true;
            }
        }
        return false;
    }

    public static double rigidGap(Bogey a, Bogey b) {
        ServerSubLevel ownerA = OWNER.get(a);
        ServerSubLevel ownerB = OWNER.get(b);
        if (ownerA == null || ownerB == null || ownerA == ownerB) {
            return Math.sqrt(a.localPos().distSqr(b.localPos()));
        }
        return worldOf(ownerA, a).distance(worldOf(ownerB, b));
    }

    public static Vector3d anchorLocal(ServerSubLevel leaderSub, Car car) {
        if (!grouped(car)) {
            return null;
        }
        List<Bogey> chain = car.chain();
        if (chain.isEmpty()) {
            return null;
        }
        Vector3d first = SubLevelData.localOf(leaderSub, worldOf(leaderSub, chain.get(0)));
        Vector3d last = SubLevelData.localOf(leaderSub, worldOf(leaderSub, chain.get(chain.size() - 1)));
        return first.add(last).mul(0.5);
    }
}
