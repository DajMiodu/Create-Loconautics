package com.lycoris.loconautics.common.strand;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.common.grab.GrabTarget;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class StrandKnotGrab {

    private static final double GRAVITY = 9.8;
    private static final double DAMPING = 0.92;
    private static final double CONTACT_GRIP = 0.7;
    public static final int TRACE_TICKS = 100;
    private static final double REACH_SLACK = 4.0;
    private static final double TAUT = 0.999;
    private static final int ITERATIONS = 12;
    private static final int AFTER_COLLISION_ITERATIONS = 3;

    private record Request(UUID player, int index, float range) {}

    private volatile Request request;
    private volatile boolean engaged;
    private int index;
    private double[] rest = new double[0];
    private final List<Vector3d> previous = new ArrayList<>();

    public boolean idle() {
        return request == null && !engaged;
    }

    public boolean engaged() {
        return engaged;
    }

    public void request(UUID player, int knotIndex, float range) {
        Request current = request;
        if (current != null && !current.player().equals(player)) {
            return;
        }
        request = new Request(player, knotIndex,
                Mth.clamp(range, GrabTarget.MIN_GRAB_DISTANCE, GrabTarget.MAX_GRAB_DISTANCE));
    }

    public void stop(UUID player) {
        Request current = request;
        if (current != null && current.player().equals(player)) {
            request = null;
        }
    }

    public void reset() {
        request = null;
        engaged = false;
    }

    public boolean tickAndReportRelease(ServerLevel level, List<Vector3d> points, @Nullable Vector3d pinStart,
                        @Nullable Vector3d pinEnd, double dt, double radius) {
        Request req = request;
        Player player = req == null ? null : level.getPlayerByUUID(req.player());
        if (req == null || player == null || !player.isAlive() || player.isSpectator()) {
            return finish(req == null ? "stop requested" : "player gone");
        }
        if (!engaged && !begin(points, req.index())) {
            request = null;
            return false;
        }
        if (rest.length != points.size() - 1) {
            return finish("point count changed");
        }
        Vector3d knot = points.get(index);
        double reach = player.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE) + REACH_SLACK;
        if (player.getEyePosition().distanceToSqr(knot.x, knot.y, knot.z) > reach * reach) {
            return finish("out of reach");
        }
        step(level, points, goal(player, req.range(), pinStart, pinEnd), pinStart, pinEnd, dt, radius);
        return false;
    }

    public int traceTicks;

    private boolean finish(String reason) {
        boolean was = engaged;
        if (was) {
            traceTicks = TRACE_TICKS;
            LoconauticsConstants.LOGGER.info("[knotgrab] released reason={} points={}", reason, previous.size());
        }
        request = null;
        engaged = false;
        previous.clear();
        return was;
    }

    private boolean begin(List<Vector3d> points, int knotIndex) {
        if (knotIndex < 1 || knotIndex > points.size() - 2) {
            return false;
        }
        index = knotIndex;
        rest = new double[points.size() - 1];
        previous.clear();
        for (int i = 0; i < points.size(); i++) {
            previous.add(new Vector3d(points.get(i)));
            if (i > 0) {
                rest[i - 1] = points.get(i).distance(points.get(i - 1));
            }
        }
        engaged = true;
        LoconauticsConstants.LOGGER.info("[knotgrab] engaged index={} points={}", knotIndex, points.size());
        return true;
    }

    private Vector3d goal(Player player, float range, @Nullable Vector3d pinStart, @Nullable Vector3d pinEnd) {
        Vec3 target = player.getEyePosition().add(player.getLookAngle().scale(range));
        Vector3d goal = new Vector3d(target.x, target.y, target.z);
        double before = 0.0;
        double after = 0.0;
        for (int i = 0; i < rest.length; i++) {
            if (i < index) {
                before += rest[i];
            } else {
                after += rest[i];
            }
        }
        for (int pass = 0; pass < 2; pass++) {
            limit(goal, pinStart, before);
            limit(goal, pinEnd, after);
        }
        return goal;
    }

    private static void limit(Vector3d goal, @Nullable Vector3d pin, double slack) {
        if (pin == null) {
            return;
        }
        double max = slack * TAUT;
        double dist = goal.distance(pin);
        if (dist > max && dist > 1.0e-9) {
            goal.sub(pin).mul(max / dist).add(pin);
        }
    }

    private boolean fixed(int i, int last, @Nullable Vector3d pinStart, @Nullable Vector3d pinEnd) {
        return i == index || (i == 0 && pinStart != null) || (i == last && pinEnd != null);
    }

    private void step(ServerLevel level, List<Vector3d> points, Vector3d goal, @Nullable Vector3d pinStart,
                      @Nullable Vector3d pinEnd, double dt, double radius) {
        int last = points.size() - 1;
        double fall = GRAVITY * dt * dt;
        for (int i = 0; i <= last; i++) {
            Vector3d cur = points.get(i);
            Vector3d prev = previous.get(i);
            if (fixed(i, last, pinStart, pinEnd)) {
                cur.set(i == index ? goal : i == 0 ? pinStart : pinEnd);
                prev.set(cur);
                continue;
            }
            double vx = (cur.x - prev.x) * DAMPING;
            double vy = (cur.y - prev.y) * DAMPING;
            double vz = (cur.z - prev.z) * DAMPING;
            prev.set(cur);
            cur.add(vx, vy - fall, vz);
        }
        relax(points, ITERATIONS, pinStart, pinEnd);
        collide(level, points, radius);
        relax(points, AFTER_COLLISION_ITERATIONS, pinStart, pinEnd);
        collide(level, points, radius);
    }

    private void collide(ServerLevel level, List<Vector3d> points, double radius) {
        for (int i = 1; i < points.size() - 1; i++) {
            if (StrandCollision.pushOut(level, points.get(i), radius)) {
                previous.get(i).lerp(points.get(i), CONTACT_GRIP);
            }
        }
    }

    private void relax(List<Vector3d> points, int iterations, @Nullable Vector3d pinStart,
                       @Nullable Vector3d pinEnd) {
        int last = points.size() - 1;
        Vector3d delta = new Vector3d();
        for (int it = 0; it < iterations; it++) {
            for (int i = 0; i < last; i++) {
                boolean fixedA = fixed(i, last, pinStart, pinEnd);
                boolean fixedB = fixed(i + 1, last, pinStart, pinEnd);
                if (fixedA && fixedB) {
                    continue;
                }
                Vector3d a = points.get(i);
                Vector3d b = points.get(i + 1);
                b.sub(a, delta);
                double len = delta.length();
                if (len < 1.0e-9) {
                    continue;
                }
                double push = (len - rest[i]) / len;
                if (fixedA) {
                    b.fma(-push, delta);
                } else if (fixedB) {
                    a.fma(push, delta);
                } else {
                    a.fma(push * 0.5, delta);
                    b.fma(-push * 0.5, delta);
                }
            }
        }
    }
}
