package com.lycoris.loconautics.trainphysics.drive;

import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.trainphysics.SableTrain.Car;
import com.lycoris.loconautics.common.sable.SubLevelData;
import com.lycoris.loconautics.foundation.debug.DebugLines;
import com.lycoris.loconautics.trainphysics.rail.RailCarriage;
import com.lycoris.loconautics.trainphysics.rail.SableRailHop;
import com.lycoris.loconautics.trainphysics.rail.TurnLog;
import com.lycoris.loconautics.trainphysics.rail.TurnMemory;
import com.lycoris.loconautics.trainphysics.spawn.SableTrainSpawner;
import com.lycoris.loconautics.trainphysics.state.SableTrainRegistry;
import com.simibubi.create.content.trains.bogey.AbstractBogeyBlockEntity;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.lycoris.loconautics.foundation.LogFormat.shortId;

public final class TrainRailAdvance {

    private static final java.util.Map<UUID, TurnLog> TURN_LOGS = new java.util.HashMap<>();
    private static final java.util.Map<UUID, Integer> TURN_LEAD = new java.util.HashMap<>();

    // creates train.tick maxstress, kept per car because advancerail runs per substep and the game tick check reads the worst of them
    private static final java.util.Map<UUID, Stress> MAX_STRESS = new java.util.HashMap<>();

    record Stress(double amount, BlockPos bogey) {}

    public static double peekStress(UUID subLevelId) {
        Stress stress = MAX_STRESS.get(subLevelId);
        return stress == null ? 0.0 : stress.amount();
    }

    @Nullable
    static Stress takeMaxStress(UUID subLevelId) {
        return MAX_STRESS.remove(subLevelId);
    }

    static TurnLog turnLogFor(Car car, int leadIdx) {
        UUID id = car.subLevelId();
        TurnLog log = TURN_LOGS.computeIfAbsent(id, key -> new TurnLog());
        Integer last = TURN_LEAD.put(id, leadIdx);
        if (last != null && last != leadIdx) {
            log.clear();
        }
        return log;
    }

    static void retainTurnLogs(java.util.Set<UUID> liveSubLevels) {
        TURN_LOGS.keySet().retainAll(liveSubLevels);
        TURN_LEAD.keySet().retainAll(liveSubLevels);
        MAX_STRESS.keySet().retainAll(liveSubLevels);
        ConsistTurns.retain(liveSubLevels);
    }

    public static void advanceRail(Car car, double ds) {
        advanceRail(car, ds, null);
    }

    @Nullable
    public static Vec3 loneFacing(ServerSubLevel sub, Car car) {
        return car.chain().size() < 2 ? SableTrainSpawner.currentFacing(sub, car) : null;
    }

    public static void advanceRail(Car car, double ds, @Nullable Vec3 loneFacing) {
        var bogeys = car.chain();
        if (bogeys.isEmpty()) {
            double loneSign = travelSign(car.carriage(), car.carriage().forward());
            car.carriage().unstick();
            car.carriage().setTravelIntent(ds * loneSign);
            car.carriage().setSpeed(ds * loneSign);
            car.carriage().tick();
            return;
        }
        int n = bogeys.size();

        Integer heldLead = TURN_LEAD.get(car.subLevelId());
        boolean forward = Math.abs(ds) < 1.0e-9 && heldLead != null
                ? heldLead != 0
                : ds >= 0.0;
        int leadIdx = forward ? n - 1 : 0;
        int toTail = forward ? -1 : 1;
        SableTrain.Bogey leadBogey = bogeys.get(leadIdx);

        TurnLog log = turnLogFor(car, leadIdx);
        for (int i = 0; i < n; i++) {
            bogeys.get(i).rail().setTurnLog(log, i == leadIdx);
        }
        SableTrain owner = SableTrainRegistry.bySubLevel(car.subLevelId());
        if (owner != null && owner.car() == car) {
            TurnMemory memory = ConsistTurns.memoryFor(owner);
            for (SableTrain.Bogey bogey : bogeys) {
                bogey.rail().setTurnMemory(memory, new TurnMemory.BogeyId(car.subLevelId(), bogey.localPos()));
            }
            car.carriage().setTurnMemory(memory, new TurnMemory.BogeyId(car.subLevelId(), null));
        }
        long slowest = Long.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            if (i != leadIdx) {
                slowest = Math.min(slowest, bogeys.get(i).rail().turnCursor());
            }
        }
        if (slowest != Long.MAX_VALUE) {
            log.pruneBelow(slowest);
        }

        for (int i = leadIdx + toTail; i >= 0 && i < n; i += toTail) {
            bogeys.get(i).rail().followPoint(bogeys.get(i - toTail).rail().trailing());
        }
        car.carriage().followPoint(leadBogey.rail().leading());

        Vec3 bodyFwd = bogeyChordForward(bogeys);
        if (bodyFwd == null) {
            bodyFwd = loneFacing != null ? loneFacing : leadBogey.rail().forward();
        }

        double sign = travelSign(leadBogey.rail(), bodyFwd);

        leadBogey.rail().unstick();
        leadBogey.rail().setTravelIntent(ds * sign);
        leadBogey.rail().setSpeed(ds * sign);
        leadBogey.rail().tick();

        if (n == 1) {
            car.carriage().setPortal(leadBogey.rail().portal());
            car.carriage().recenterOn(leadBogey.rail().leading(), leadBogey.rail().graph());
        } else {
            // the span reseats on its own edge at a seam, so it gets its own sign, never the lead bogeys
            double spanSign = travelSign(car.carriage(), bodyFwd);
            car.carriage().unstick();
            car.carriage().setTravelIntent(ds * spanSign);
            car.carriage().setSpeed(ds * spanSign);
            car.carriage().tick();
        }

        boolean docked = DockGroups.grouped(car);
        for (int i = leadIdx + toTail; i >= 0 && i < n; i += toTail) {
            // a rigid group is measured against the lead bogey, neighbour gaps would let the rail polygon shorten the body on a curve
            SableTrain.Bogey ahead = docked ? bogeys.get(leadIdx) : bogeys.get(i - toTail);
            SableTrain.Bogey bogey = bogeys.get(i);
            bogey.rail().unstick();
            double bogeySign = travelSign(bogey.rail(), bodyFwd);
            double move = ds * bogeySign;

            Vector3d cAhead = ahead.rail().center();
            Vector3d cThis = bogey.rail().center();
            if (cAhead != null && cThis != null) {
                double chord = cAhead.distance(cThis);
                double rigid = DockGroups.rigidGap(bogey, ahead);
                double err = chord - rigid;
                MAX_STRESS.merge(car.subLevelId(), new Stress(Math.abs(err), bogey.localPos()),
                        (old, now) -> now.amount() > old.amount() ? now : old);
                Vec3 f = travelDir(bogey.rail());
                double toward = (cAhead.x - cThis.x) * f.x + (cAhead.z - cThis.z) * f.z;
                double cap = SableTrainDriver.MAX_FOLLOW_CORRECTION;
                double corr = Math.max(-cap, Math.min(cap, err));
                move += toward >= 0.0 ? corr : -corr;
            }

            bogey.rail().setTravelIntent(ds * bogeySign);
            bogey.rail().setSpeed(move);
            bogey.rail().tick();
        }

        for (SableTrain.Bogey other : car.bogeys()) {
            if (!other.rail().isIndependent()) {
                continue;
            }
            double otherSign = travelSign(other.rail(), bodyFwd);
            other.rail().unstick();
            other.rail().setTravelIntent(ds * otherSign);
            other.rail().setSpeed(ds * otherSign);
            other.rail().tick();
        }

    }

    static void animateBogeyWheels(Car car, ServerSubLevel sub, double ds) {
        if (ds == 0.0) return;
        float distanceMoved = (float) Math.abs(ds);
        ServerLevel subLevel = sub.getLevel();
        for (SableTrain.Bogey bogey : car.bogeys()) {
            BlockPos worldPos = DockGroups.ownerOf(bogey, sub).getPlot().getCenterBlock().offset(bogey.localPos());
            if (subLevel.getBlockEntity(worldPos) instanceof AbstractBogeyBlockEntity be) {
                be.animate(distanceMoved);
            }
        }
    }

    // a rail seated by a per bogey rerail is never advanced while a bogey is loose, so its sign would stay the default
    public static void seedTravelSign(RailCarriage rail, Vec3 bodyFwd) {
        travelSign(rail, bodyFwd);
    }

    static double travelSign(RailCarriage rail, Vec3 bodyFwd) {
        double dot = travelDot(rail, bodyFwd);
        double sign = rail.lastTravelSign();
        if (sign >= 0.0) {
            if (dot < -SableTrainDriver.TRAVEL_SIGN_FLIP_DEADZONE) {
                sign = -1.0;
            }
        } else if (dot > SableTrainDriver.TRAVEL_SIGN_FLIP_DEADZONE) {
            sign = 1.0;
        }
        rail.setLastTravelSign(sign);
        return sign;
    }

    static double travelDot(RailCarriage rail, Vec3 bodyFwd) {
        Vec3 f = travelDir(rail);
        return f.x * bodyFwd.x + f.z * bodyFwd.z;
    }

    @Nullable
    static Vec3 bodyForward(RailCarriage rail) {
        Vec3 dir = travelDir(rail);
        return dir == null ? null : dir.scale(rail.lastTravelSign());
    }

    static Vec3 travelDir(RailCarriage rail) {
        Vec3 tangent = rail.edgeTangent();
        return tangent != null ? tangent : rail.forward();
    }

    public static Vec3 bogeyChordForward(java.util.List<SableTrain.Bogey> bogeys) {
        if (bogeys.size() < 2) {
            return null;
        }
        Vector3d rear = bogeys.get(0).rail().center();
        Vector3d front = bogeys.get(bogeys.size() - 1).rail().center();
        if (rear == null || front == null) {
            return null;
        }
        Vector3d chord = new Vector3d(front).sub(rear);
        if (chord.lengthSquared() < 1.0e-9) {
            return null;
        }
        chord.normalize();
        return new Vec3(chord.x, chord.y, chord.z);
    }

    static Vector3d[] bogeyReference(Car car) {
        return bogeyReference(null, car);
    }

    public static Vector3d[] bogeyReference(ServerSubLevel sub, Car car) {
        var bogeys = car.chain();
        if (bogeys.isEmpty()) {
            return null;
        }
        if (bogeys.size() >= 2) {
            Vector3d frontC = bogeys.get(bogeys.size() - 1).rail().center();
            Vector3d rearC = bogeys.get(0).rail().center();
            if (frontC != null && rearC != null) {
                Vector3d chord = new Vector3d(frontC).sub(rearC);
                if (chord.lengthSquared() >= 1.0e-9) {
                    return new Vector3d[] { new Vector3d(frontC).add(rearC).mul(0.5), chord.normalize() };
                }
            }
        }
        Vec3 frontP = bogeys.get(bogeys.size() - 1).rail().leadingPos();
        Vec3 rearP = bogeys.get(0).rail().trailingPos();
        if (frontP != null && rearP != null) {
            Vector3d front = new Vector3d(frontP.x, frontP.y, frontP.z);
            Vector3d rear = new Vector3d(rearP.x, rearP.y, rearP.z);
            Vector3d chord = new Vector3d(front).sub(rear);
            if (chord.lengthSquared() >= 1.0e-9) {
                Vector3d mid = new Vector3d(front).add(rear).mul(0.5);
                return new Vector3d[] { mid, faceLikeBody(sub, car, chord.normalize()) };
            }
        }
        RailCarriage rail = bogeys.get(0).rail();
        Vector3d c = rail.center();
        Vec3 t = rail.edgeTangent();
        if (c == null || t == null) {
            return null;
        }
        return new Vector3d[] { c, faceLikeBody(sub, car, new Vector3d(t.x, t.y, t.z)) };
    }

    private static Vector3d faceLikeBody(ServerSubLevel sub, Car car, Vector3d axis) {
        if (sub == null) {
            return axis;
        }
        Vec3 facing = SableTrainSpawner.currentFacing(sub, car);
        return axis.x * facing.x + axis.z * facing.z < 0.0 ? axis.negate() : axis;
    }

    public static Vector3d bogeyAnchorLocal(ServerSubLevel sub, Car car) {
        Vector3d docked = DockGroups.anchorLocal(sub, car);
        if (docked != null) {
            return docked;
        }
        var bogeys = car.chain();
        if (bogeys.isEmpty()) {
            return null;
        }
        BlockPos plot = sub.getPlot().getCenterBlock();
        BlockPos first = plot.offset(bogeys.get(0).localPos());
        BlockPos last = plot.offset(bogeys.get(bogeys.size() - 1).localPos());
        return new Vector3d((first.getX() + last.getX()) * 0.5 + 0.5, (first.getY() + last.getY()) * 0.5 + 0.5,
                (first.getZ() + last.getZ()) * 0.5 + 0.5);
    }

    static boolean railsValid(SableTrain train) {
        Car car = train.car();
        if (car.bogeys().isEmpty()) {
            return car.carriage().edgesValid();
        }
        // the span is rebound too but never votes, only a bogey can lose the rail
        if (car.carriage() != null) {
            car.carriage().edgesValid();
        }
        boolean valid = true;
        for (SableTrain.Bogey bogey : car.bogeys()) {
            valid &= bogey.rail().edgesValid();
        }
        return valid;
    }

    public static boolean anyPointFar(Car car) {
        if (car.carriage() != null && car.carriage().anyPointFar()) {
            return true;
        }
        for (SableTrain.Bogey b : car.bogeys()) {
            if (b.rail() != null && b.rail().anyPointFar()) {
                return true;
            }
        }
        return false;
    }

    static boolean anyEndedFar(Car car, SableTrain train) {
        for (SableTrain.Bogey b : car.chain()) {
            if (train.isBogeyOffTrack(b.localPos())) {
                continue;
            }
            if (b.rail().reachedEnd() && (b.rail().isFar(b.rail().leading()) || b.rail().isFar(b.rail().trailing()))) {
                return true;
            }
        }
        return car.bogeys().isEmpty() && car.carriage().reachedEnd() && car.carriage().anyPointFar();
    }

    // a point parked at a portal plane looks exactly like a railhead, and it must never be hopped or coasted off
    public static boolean anyPendingPortal(Car car) {
        if (car.carriage().pendingPortal() != null) {
            return true;
        }
        for (SableTrain.Bogey b : car.bogeys()) {
            if (b.rail().pendingPortal() != null) {
                return true;
            }
        }
        return false;
    }

    // the car span has no wheels, so it never gets a vote on running out of track, only a bogey can
    public static boolean anyReachedEnd(Car car) {
        if (car.bogeys().isEmpty()) {
            return car.carriage().reachedEnd();
        }
        for (SableTrain.Bogey b : car.chain()) {
            if (b.rail().reachedEnd()) {
                return true;
            }
        }
        return false;
    }

    static boolean anyRailedReachedEnd(Car car, SableTrain train) {
        for (SableTrain.Bogey b : car.chain()) {
            if (!train.isBogeyOffTrack(b.localPos()) && b.rail().reachedEnd()) {
                return true;
            }
        }
        return false;
    }

    static boolean weldEndedFollowers(ServerSubLevel bodySub, Car car, SableTrain train) {
        Vec3 up = SableTrainDriver.bodyUpWorld(bodySub);
        if (car.bogeys().isEmpty()) {
            Vector3d c = car.carriage().center();
            return c != null && car.carriage().reachedEnd()
                    && weldFollower(bodySub, car.carriage(), "span", new Vec3(c.x, c.y, c.z),
                            outwardOf(car.carriage(), bodySub, car, train), up);
        }
        boolean welded = false;
        for (int i = 0; i < car.bogeys().size(); i++) {
            SableTrain.Bogey b = car.bogeys().get(i);
            if (train.isBogeyOffTrack(b.localPos())) {
                continue;
            }
            if (b.rail().reachedEnd() && weldFollower(bodySub, b.rail(), "b" + i, wheelProbe(bodySub, b),
                    outwardOf(b.rail(), bodySub, car, train), up)) {
                welded = true;
            }
        }
        return welded;
    }

    static Vec3 outwardOf(RailCarriage rail, ServerSubLevel bodySub, Car car, SableTrain train) {
        Vec3 outward = rail.railheadOutward();
        if (outward != null) {
            return outward;
        }
        Vector3d t = bodyForwardWorld(bodySub, car);
        Vec3 tan = new Vec3(t.x, t.y, t.z);
        return train.speed() < 0.0 ? tan.scale(-1.0) : tan;
    }

    private static final int HOP_REPORT_TTL = 200;
    private static final int HOP_CAN = 0x00FF33;
    private static final int HOP_CANNOT = 0xFF3030;
    private static final int HOP_HEADING = 0x00CCFF;

    public static List<String> hopReport(SableTrain train) {
        List<String> out = new ArrayList<>();
        Car car = train.car();
        ServerLevel level = train.level();
        ServerSubLevel sub = level == null ? null : SubLevelData.resolve(level, car.subLevelId());
        if (sub == null) {
            out.add("the car's sub level is not loaded, nothing to probe");
            return out;
        }
        Vec3 up = SableTrainDriver.bodyUpWorld(sub);
        if (car.bogeys().isEmpty()) {
            Vector3d c = car.carriage().center();
            reportFollower(out, level, sub, car, train, "span", car.carriage(),
                    c == null ? null : new Vec3(c.x, c.y, c.z), up);
            return out;
        }
        for (int i = 0; i < car.bogeys().size(); i++) {
            SableTrain.Bogey b = car.bogeys().get(i);
            reportFollower(out, level, sub, car, train, "b" + i, b.rail(), wheelProbe(sub, b), up);
        }
        return out;
    }

    private static String xyz(Vec3 v) {
        return v == null ? "null" : String.format("(%.2f, %.2f, %.2f)", v.x, v.y, v.z);
    }

    private static void reportFollower(List<String> out, ServerLevel level, ServerSubLevel sub, Car car,
                                       SableTrain train, String name, RailCarriage rail, Vec3 probe, Vec3 up) {
        if (probe == null) {
            out.add(name + " has no rail position");
            return;
        }
        Vec3 outward = outwardOf(rail, sub, car, train);
        SableRailHop.Probe found = rail.railAheadProbe(level, sub, probe, outward, up, true);
        DebugLines.arrow(level, "hopwhere_dir_" + name, probe, probe.add(outward.scale(2.0)),
                HOP_HEADING, DebugLines.DEFAULT_WIDTH, HOP_REPORT_TTL);
        if (found != null && found.hit() != null) {
            DebugLines.column(level, "hopwhere_" + name, found.hit().railPoint(), HOP_CAN, HOP_REPORT_TTL);
            out.add(String.format("%s CAN hop %.2f ahead onto %s at %s", name, found.step(),
                    found.hit().sub() == null ? "the world" : shortId(found.hit().sub()),
                    xyz(found.hit().railPoint())));
            return;
        }
        DebugLines.column(level, "hopwhere_" + name, probe, HOP_CANNOT, HOP_REPORT_TTL);
        out.add(String.format("%s CANNOT hop from %s, %s", name, xyz(probe),
                found == null ? "the probe had no heading" : found.detail()));
    }

    private static boolean weldFollower(ServerSubLevel bodySub, RailCarriage rail, String name, Vec3 probe,
                                        Vec3 sweep, Vec3 up) {
        ServerLevel level = bodySub.getLevel();
        SableRailHop.Result target = rail.railAheadTarget(level, bodySub, probe, sweep, up, true);
        if (target == null) {
            return false;
        }
        if (!rail.weldAheadTo(target)) {
            return false;
        }
        return true;
    }

    // rail the car could still roll onto without hopping anywhere, which the hop scan never reports
    static boolean ownGraphRailAhead(Car car, ServerSubLevel sub, SableTrain train) {
        ServerLevel level = sub.getLevel();
        if (car.bogeys().isEmpty()) {
            Vector3d c = car.carriage().center();
            return c != null && SableRailHop.ownGraphTrackAhead(level, car.carriage().graph(),
                    car.carriage().activeSub(), new Vec3(c.x, c.y, c.z),
                    outwardOf(car.carriage(), sub, car, train));
        }
        for (SableTrain.Bogey b : car.bogeys()) {
            if (train.isBogeyOffTrack(b.localPos()) || !b.rail().reachedEnd()) {
                continue;
            }
            Vec3 probe = wheelProbe(sub, b);
            Vec3 outward = outwardOf(b.rail(), sub, car, train);
            if (SableRailHop.ownGraphTrackAhead(level, b.rail().graph(), b.rail().activeSub(), probe, outward)) {
                return true;
            }
        }
        return false;
    }

    // must use leadingrailpos, leadingpos is already lifted a full normal off the rail by create and undercounts the drop
    static Vec3 wheelProbe(ServerSubLevel sub, SableTrain.Bogey bogey) {
        Vec3 block = bogeyWorldPos(sub, bogey);
        Vec3 rail = bogey.rail().leadingRailPos();
        double drop = SableTrainDriver.DEFAULT_WHEEL_DROP;
        if (rail != null) {
            double measured = block.y - rail.y;
            if (measured > 0.0 && measured <= 3.0) {
                drop = measured;
            }
        }
        return block.subtract(SableTrainDriver.bodyUpWorld(sub).scale(drop));
    }

    static Vec3 bogeyWorldPos(ServerSubLevel sub, SableTrain.Bogey bogey) {
        BlockPos w = sub.getPlot().getCenterBlock().offset(bogey.localPos());
        Vector3d local = new Vector3d(w.getX() + 0.5, w.getY() + 0.5, w.getZ() + 0.5);
        Vector3d world = sub.logicalPose().transformPosition(local, new Vector3d());
        return new Vec3(world.x, world.y, world.z);
    }

    static Vector3d bodyForwardWorld(ServerSubLevel sub, Car car) {
        Vec3 f = SableTrainSpawner.currentFacing(sub, car);
        return new Vector3d(f.x, f.y, f.z);
    }

    public static ServerSubLevel leadingActiveSub(Car car) {
        var bogeys = car.chain();
        if (!bogeys.isEmpty()) {
            return bogeys.get(bogeys.size() - 1).rail().activeSub();
        }
        return car.carriage().activeSub();
    }

    public static Vector3d railTangentOf(SableTrain.Bogey bogey) {
        Vec3 f = bogey.rail().forward();
        Vector3d t = new Vector3d(f.x, f.y, f.z);
        return t.lengthSquared() < 1.0e-9 ? null : t.normalize();
    }

    private TrainRailAdvance() {
    }
}
