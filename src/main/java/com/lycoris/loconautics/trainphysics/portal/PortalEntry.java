package com.lycoris.loconautics.trainphysics.portal;

import com.lycoris.loconautics.network.packets.train.PortalCutPacket;
import com.lycoris.loconautics.common.sable.SubLevelData;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class PortalEntry {

    private static final Map<UUID, PortalCut> SENT = new HashMap<>();

    private static final Map<UUID, Set<UUID>> SENT_FOR_CAR = new HashMap<>();

    private static final Map<UUID, Long> RENEWED = new HashMap<>();

    private static long tick;

    private PortalEntry() {
    }

    public static void sync(ServerLevel home, UUID carSubId, PortalFrame frame, Collection<UUID> homeBodies,
                            Collection<UUID> standbyBodies, ServerLevel far, Collection<UUID> twinBodies) {
        Set<UUID> keep = new LinkedHashSet<>(homeBodies);
        keep.addAll(standbyBodies);
        keep.addAll(twinBodies);
        for (UUID stale : new ArrayList<>(SENT_FOR_CAR.getOrDefault(carSubId, Set.of()))) {
            if (!keep.contains(stale)) {
                clearBody(stale);
            }
        }
        Set<UUID> live = new LinkedHashSet<>();
        for (UUID id : homeBodies) {
            put(home, live, id, frame.nearPoint(), frame.inward(), false, true);
        }
        for (UUID id : standbyBodies) {
            put(home, live, id, frame.nearPoint(), frame.inward(), false, false);
        }
        Vec3 exit = frame.dirToFar(frame.inward());
        for (UUID id : twinBodies) {
            put(far, live, id, frame.farPoint(), exit, true, true);
        }
        SENT_FOR_CAR.put(carSubId, live);
        RENEWED.put(carSubId, tick);
    }

    private static void put(ServerLevel level, Set<UUID> live, UUID id, Vec3 planePoint, Vec3 travelDir,
                            boolean complement, boolean solid) {
        ServerSubLevel sub = level == null ? null : SubLevelData.resolve(level, id);
        PortalCut cut = sub == null ? null : cutOf(sub, id, planePoint, travelDir, complement);
        if (cut == null) {
            PortalTrace.log("cut missing body={} level={} resolves={}", PortalTrace.id(id),
                    level == null ? "none" : level.dimension().location(), sub != null);
            clearBody(id);
            return;
        }
        live.add(id);
        PortalCut previous = SENT.put(id, cut);
        int hiddenColliders = PortalColliders.apply(level, id, solid ? cut : PortalCut.everything(id));
        if (!cut.equals(previous)) {
            PortalTrace.log("cut body={} dim={} axis={} cutoff={} positive={} solid={} hiddenColliders={}",
                    PortalTrace.id(id), level.dimension().location(), cut.axis(), cut.cutoff(), cut.positive(), solid,
                    hiddenColliders);
        }
        // sent every tick, a client that joined or changed dimension mid straddle otherwise drew the car whole
        PortalCutPacket.broadcast(cut);
    }

    // an entry can end where trainportalcrossing never looks again, and every one of those left the cut standing
    public static void sweep() {
        tick++;
        for (Map.Entry<UUID, Long> entry : new ArrayList<>(RENEWED.entrySet())) {
            if (tick - entry.getValue() > 1) {
                clear(entry.getKey());
            }
        }
    }

    public static void clear(UUID carSubId) {
        clear(carSubId, Set.of());
    }

    public static void clear(UUID carSubId, Collection<UUID> kept) {
        RENEWED.remove(carSubId);
        Set<UUID> bodies = SENT_FOR_CAR.remove(carSubId);
        if (bodies == null) {
            if (!kept.contains(carSubId)) {
                clearBody(carSubId);
            }
            return;
        }
        for (UUID id : bodies) {
            if (!kept.contains(id)) {
                clearBody(id);
            }
        }
    }

    public static void release(UUID subLevelId) {
        clearBody(subLevelId);
    }

    private static void clearBody(UUID subLevelId) {
        PortalColliders.clear(subLevelId);
        if (SENT.remove(subLevelId) != null) {
            PortalTrace.log("cut cleared body={}", PortalTrace.id(subLevelId));
            PortalCutPacket.broadcastClear(subLevelId);
        }
    }

    private static PortalCut cutOf(ServerSubLevel sub, UUID subLevelId, Vec3 planePoint, Vec3 travelDir,
                                   boolean complement) {
        Pose3dc pose = sub.logicalPose();
        Vector3d localDir = pose.transformNormalInverse(
                new Vector3d(travelDir.x, travelDir.y, travelDir.z), new Vector3d());
        int axis = dominantAxis(localDir);
        Vector3d localPlane = pose.transformPositionInverse(
                new Vector3d(planePoint.x, planePoint.y, planePoint.z), new Vector3d());
        double along = axis == 0 ? localPlane.x : axis == 1 ? localPlane.y : localPlane.z;
        boolean positive = (axis == 0 ? localDir.x : axis == 1 ? localDir.y : localDir.z) > 0.0;
        return new PortalCut(subLevelId, axis, (int) Math.floor(along), complement != positive);
    }

    private static int dominantAxis(Vector3d v) {
        double ax = Math.abs(v.x);
        double ay = Math.abs(v.y);
        double az = Math.abs(v.z);
        if (ax >= ay && ax >= az) {
            return 0;
        }
        return ay >= az ? 1 : 2;
    }
}
