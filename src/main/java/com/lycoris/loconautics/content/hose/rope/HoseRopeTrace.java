package com.lycoris.loconautics.content.hose.rope;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.common.sable.BodyRef;
import com.lycoris.loconautics.common.strand.StrandEnd;
import com.lycoris.loconautics.content.hose.nozzle.HoseNozzleBlockEntity;
import com.lycoris.loconautics.foundation.debug.DebugLines;
import com.lycoris.loconautics.trainphysics.debug.force.ForceSource;
import com.lycoris.loconautics.trainphysics.debug.force.ForceTrace;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.physics.object.rope.RopeHandle;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static com.lycoris.loconautics.foundation.LogFormat.shortId;

public final class HoseRopeTrace {

    private static final double ARROW_LENGTH = 1.5;
    private static final double DV_ARROW_SCALE = 20.0;
    private static final int ARROW_TTL = 6;
    private static final int PULLS_BODY_COLOR = 0xFF3333;
    private static final int HARMLESS_COLOR = 0x808080;
    private static final int DV_COLOR = 0x00FFFF;
    private static final int JOINT_COLOR = 0xFF00FF;

    private static boolean enabled;
    private static boolean lines;
    private static boolean jointOff;

    private static final Map<UUID, Sample> SAMPLES = new HashMap<>();

    private static final class Sample {
        long tick = Long.MIN_VALUE;
        boolean haveVelocity;
        final Vector3d winchVelocity = new Vector3d();
        final Vector3d nozzleVelocity = new Vector3d();
    }

    private HoseRopeTrace() {
    }

    public static boolean jointDisabled() {
        return jointOff;
    }

    public static boolean toggleEnabled() {
        enabled = !enabled;
        SAMPLES.clear();
        return enabled;
    }

    public static boolean toggleLines() {
        lines = !lines;
        return lines;
    }

    public static boolean toggleJoint() {
        jointOff = !jointOff;
        return jointOff;
    }

    public static void reset() {
        enabled = false;
        lines = false;
        jointOff = false;
        SAMPLES.clear();
    }

    public static String status() {
        return "hose trace " + (enabled ? "on" : "off") + ", arrows " + (lines ? "on" : "off")
                + ", damping joint " + (jointOff ? "DISABLED" : "active");
    }

    static void sample(HoseRope rope, ServerLevel level) {
        if (!enabled) {
            return;
        }
        long now = level.getGameTime();
        Sample prev = SAMPLES.computeIfAbsent(rope.getUUID(), id -> new Sample());
        if (prev.tick == now) {
            return;
        }
        prev.tick = now;

        StrandEnd startEnd = rope.endOf(RopeHandle.AttachmentPoint.START);
        StrandEnd endEnd = rope.endOf(RopeHandle.AttachmentPoint.END);
        ServerSubLevel winch = startEnd == null ? null : BodyRef.of(level, startEnd.subLevelId()).sub();
        ServerSubLevel nozzle = endEnd == null ? null : BodyRef.of(level, endEnd.subLevelId()).sub();

        Vector3d winchVelocity = velocityOf(winch);
        Vector3d nozzleVelocity = velocityOf(nozzle);
        Vector3d winchDv = prev.haveVelocity ? new Vector3d(winchVelocity).sub(prev.winchVelocity) : new Vector3d();
        Vector3d nozzleDv = prev.haveVelocity ? new Vector3d(nozzleVelocity).sub(prev.nozzleVelocity) : new Vector3d();
        prev.winchVelocity.set(winchVelocity);
        prev.nozzleVelocity.set(nozzleVelocity);
        prev.haveVelocity = true;

        List<Vector3d> pts = rope.getPoints();
        int n = pts.size();
        if (n < 2) {
            return;
        }
        Vector3d startPull = pts.get(1).sub(pts.get(0), new Vector3d());
        Vector3d endPull = pts.get(n - 2).sub(pts.get(n - 1), new Vector3d());
        double firstActual = startPull.length();
        double lastActual = endPull.length();
        startPull.normalize();
        endPull.normalize();

        double current = rope.getCurrentExtension();
        double desired = rope.getDesiredExtension();

        Vector3d jointLinear = new Vector3d();
        Vector3d jointAngular = new Vector3d();
        rope.readJointImpulses(jointLinear, jointAngular);

        HoseNozzleBlockEntity nozzleBe = nozzleEntity(level, nozzle, endEnd);
        boolean docked = nozzleBe != null && nozzleBe.isDocked();
        UUID hatch = nozzleBe == null ? null : nozzleBe.dockHatchSubLevelId();

        List<String> risks = new ArrayList<>();
        if (winch != null && winch == nozzle) {
            risks.add("SAME_BODY");
        }
        if (winch != null && docked && winch.getUniqueId().equals(hatch)) {
            risks.add("NOZZLE_WELDED_TO_WINCH_BODY");
        }
        if (rope.hasJoint()) {
            risks.add("DAMPING_JOINT_ON");
        }

        String line = String.format(Locale.ROOT,
                "[hosetrace] t=%d rope=%s winchSub=%s nozzleSub=%s docked=%s hatch=%s "
                        + "pts=%d seg0=%.3f/%.3f segN=%.3f/1.000 len=%.3f desired=%.3f stretch=%.3f "
                        + "winchDv=%s alongStartPull=%.4f nozzleDv=%s alongEndPull=%.4f "
                        + "jointLin=%s jointAng=%s risks=%s",
                now, shortId(rope.getUUID()), shortId(winch), shortId(nozzle),
                docked, shortId(hatch), n, firstActual, rope.getFirstSegmentRest(), lastActual,
                current, desired, current - desired,
                vec(winchDv), winchDv.dot(startPull), vec(nozzleDv), nozzleDv.dot(endPull),
                vec(jointLinear), vec(jointAngular), risks);
        LoconauticsConstants.LOGGER.info(line);

        Vec3 startAt = toVec(pts.get(0));
        Vec3 endAt = toVec(pts.get(n - 1));
        if (winch != null) {
            ForceTrace.note(winch, ForceSource.HOSE, pts.get(0), line);
        }
        if (nozzle != null) {
            ForceTrace.note(nozzle, ForceSource.HOSE, pts.get(n - 1), line);
        }
        if (!lines) {
            return;
        }
        String slot = "hose:" + shortId(rope.getUUID());
        DebugLines.arrow(level, slot + ":start", startAt, startAt.add(toVec(startPull).scale(ARROW_LENGTH)),
                winch != null ? PULLS_BODY_COLOR : HARMLESS_COLOR,
                DebugLines.DEFAULT_WIDTH, ARROW_TTL);
        DebugLines.arrow(level, slot + ":end", endAt, endAt.add(toVec(endPull).scale(ARROW_LENGTH)),
                nozzle != null ? PULLS_BODY_COLOR : HARMLESS_COLOR, DebugLines.DEFAULT_WIDTH, ARROW_TTL);
        if (rope.hasJoint()) {
            DebugLines.line(level, slot + ":joint", startAt, endAt, JOINT_COLOR, DebugLines.DEFAULT_WIDTH, ARROW_TTL);
        }
        arrowForBody(level, slot + ":winchdv", winch, winchDv);
        arrowForBody(level, slot + ":nozzledv", nozzle, nozzleDv);
    }

    private static void arrowForBody(ServerLevel level, String slot, @Nullable ServerSubLevel body, Vector3d dv) {
        if (body == null) {
            return;
        }
        Vec3 from = toVec(body.logicalPose().position());
        DebugLines.arrow(level, slot, from, from.add(toVec(dv).scale(DV_ARROW_SCALE)), DV_COLOR,
                DebugLines.DEFAULT_WIDTH, ARROW_TTL);
    }

    @Nullable
    private static HoseNozzleBlockEntity nozzleEntity(ServerLevel level, @Nullable ServerSubLevel body,
                                                      @Nullable StrandEnd end) {
        if (end == null) {
            return null;
        }
        BlockEntity be = (body != null ? body.getLevel() : level).getBlockEntity(end.local());
        return be instanceof HoseNozzleBlockEntity nozzle ? nozzle : null;
    }

    private static Vector3d velocityOf(@Nullable ServerSubLevel body) {
        Vector3d velocity = new Vector3d();
        RigidBodyHandle handle = body == null ? null : RigidBodyHandle.of(body);
        if (handle != null && handle.isValid()) {
            handle.getLinearVelocity(velocity);
        }
        return velocity;
    }

    private static Vec3 toVec(org.joml.Vector3dc v) {
        return new Vec3(v.x(), v.y(), v.z());
    }

    private static String vec(Vector3d v) {
        return String.format(Locale.ROOT, "(%.4f,%.4f,%.4f)", v.x, v.y, v.z);
    }
}
