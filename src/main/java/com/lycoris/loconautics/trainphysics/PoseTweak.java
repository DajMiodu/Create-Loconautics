package com.lycoris.loconautics.trainphysics;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3dc;

public final class PoseTweak {

    public record Applied(Vector3d position, Quaterniond orientation) { }

    private static final double DEFAULT_LERP_SPEED = 0.2;
    private static final double SNAP_DISTANCE = 1.0e-4;

    public static final double MAX_ANGLE = 360.0;
    public static final double MAX_OFFSET = 32.0;

    private double roll;
    private double pitch;
    private double yaw;
    private double x;
    private double y;
    private double z;

    private double targetRoll;
    private double targetPitch;
    private double targetYaw;
    private double targetX;
    private double targetY;
    private double targetZ;

    private double lerpSpeed = DEFAULT_LERP_SPEED;

    public double roll() {
        return roll;
    }

    public double pitch() {
        return pitch;
    }

    public double yaw() {
        return yaw;
    }

    public double x() {
        return x;
    }

    public double y() {
        return y;
    }

    public double z() {
        return z;
    }

    public double targetRoll() {
        return targetRoll;
    }

    public double targetPitch() {
        return targetPitch;
    }

    public double targetYaw() {
        return targetYaw;
    }

    public double targetX() {
        return targetX;
    }

    public double targetY() {
        return targetY;
    }

    public double targetZ() {
        return targetZ;
    }

    public double lerpSpeed() {
        return lerpSpeed;
    }

    public void setLerpSpeed(double lerpSpeed) {
        this.lerpSpeed = Double.isNaN(lerpSpeed) ? DEFAULT_LERP_SPEED : Math.max(0.0, Math.min(1.0, lerpSpeed));
    }

    public void setRoll(double roll) {
        this.targetRoll = clampAngle(roll);
    }

    public void setPitch(double pitch) {
        this.targetPitch = clampAngle(pitch);
    }

    public void setYaw(double yaw) {
        this.targetYaw = clampAngle(yaw);
    }

    public void setOffset(double x, double y, double z) {
        this.targetX = clampOffset(x);
        this.targetY = clampOffset(y);
        this.targetZ = clampOffset(z);
    }

    public static double clampAngle(double degrees) {
        return Double.isNaN(degrees) ? 0.0 : Math.max(-MAX_ANGLE, Math.min(MAX_ANGLE, degrees));
    }

    public static double clampOffset(double blocks) {
        return Double.isNaN(blocks) ? 0.0 : Math.max(-MAX_OFFSET, Math.min(MAX_OFFSET, blocks));
    }

    public void reset() {
        setRoll(0.0);
        setPitch(0.0);
        setYaw(0.0);
        setOffset(0.0, 0.0, 0.0);
    }

    public void advance() {
        roll = step(roll, targetRoll);
        pitch = step(pitch, targetPitch);
        yaw = step(yaw, targetYaw);
        x = step(x, targetX);
        y = step(y, targetY);
        z = step(z, targetZ);
    }

    private double step(double current, double target) {
        double moved = current + (target - current) * lerpSpeed;
        return Math.abs(target - moved) < SNAP_DISTANCE ? target : moved;
    }

    public boolean isSettled() {
        return roll == targetRoll && pitch == targetPitch && yaw == targetYaw
                && x == targetX && y == targetY && z == targetZ;
    }

    public void snap() {
        roll = targetRoll;
        pitch = targetPitch;
        yaw = targetYaw;
        x = targetX;
        y = targetY;
        z = targetZ;
    }

    public boolean isZero() {
        return roll == 0.0 && pitch == 0.0 && yaw == 0.0 && x == 0.0 && y == 0.0 && z == 0.0;
    }

    public Quaterniond local(Vector3d referenceForward) {
        if (roll == 0.0 && pitch == 0.0 && yaw == 0.0) {
            return new Quaterniond();
        }
        Vector3d forward = referenceForward == null || referenceForward.lengthSquared() < 1.0e-9
                ? new Vector3d(1, 0, 0)
                : new Vector3d(referenceForward).normalize();
        Vector3d lateral = new Vector3d(0, 1, 0).cross(forward);
        if (lateral.lengthSquared() < 1.0e-9) {
            lateral.set(0, 0, 1);
        }
        lateral.normalize();
        return new Quaterniond()
                .rotateAxis(Math.toRadians(yaw), 0.0, 1.0, 0.0)
                .rotateAxis(Math.toRadians(pitch), lateral.x, lateral.y, lateral.z)
                .rotateAxis(Math.toRadians(roll), forward.x, forward.y, forward.z)
                .normalize();
    }

    public Vector3d offset() {
        return new Vector3d(x, y, z);
    }

    public static Vector3d anchorCenter(ServerSubLevel sub) {
        BlockPos a = sub.getPlot().getCenterBlock();
        return new Vector3d(a.getX() + 0.5, a.getY(), a.getZ() + 0.5);
    }

    public static Applied apply(ServerSubLevel sub, Vector3d pos, Quaterniond q, Vector3d referenceForward,
                                PoseTweak tweak) {
        if (tweak == null || tweak.isZero()) {
            return new Applied(new Vector3d(pos), new Quaterniond(q));
        }
        Quaterniond turned = new Quaterniond(q).mul(tweak.local(referenceForward)).normalize();
        Vector3dc rotationPoint = sub.logicalPose().rotationPoint();
        Vector3d anchorToPoint = new Vector3d(rotationPoint).sub(anchorCenter(sub));
        Vector3d anchorWorld = q.transform(new Vector3d(anchorToPoint).negate()).add(pos);
        Vector3d moved = turned.transform(new Vector3d(anchorToPoint).add(tweak.offset())).add(anchorWorld);
        return new Applied(moved, turned);
    }

    public static Vector3d counter(ServerSubLevel sub, Vector3d subLevelPos, Vector3d referenceForward,
                                   PoseTweak tweak) {
        if (tweak == null || tweak.isZero()) {
            return new Vector3d();
        }
        Vector3d arm = new Vector3d(subLevelPos).sub(anchorCenter(sub));
        return tweak.local(referenceForward).transformInverse(new Vector3d(arm)).sub(arm).sub(tweak.offset());
    }

    public void save(CompoundTag tag) {
        CompoundTag t = new CompoundTag();
        t.putDouble("Roll", targetRoll);
        t.putDouble("Pitch", targetPitch);
        t.putDouble("Yaw", targetYaw);
        t.putDouble("X", targetX);
        t.putDouble("Y", targetY);
        t.putDouble("Z", targetZ);
        t.putDouble("LerpSpeed", lerpSpeed);
        tag.put("PoseTweak", t);
    }

    public void load(CompoundTag tag) {
        if (!tag.contains("PoseTweak")) {
            reset();
            snap();
            return;
        }
        CompoundTag t = tag.getCompound("PoseTweak");
        targetRoll = t.getDouble("Roll");
        targetPitch = t.getDouble("Pitch");
        targetYaw = t.getDouble("Yaw");
        targetX = t.getDouble("X");
        targetY = t.getDouble("Y");
        targetZ = t.getDouble("Z");
        setLerpSpeed(t.contains("LerpSpeed") ? t.getDouble("LerpSpeed") : DEFAULT_LERP_SPEED);
        snap();
    }

    @Override
    public String toString() {
        return String.format("roll %.2f, pitch %.2f, yaw %.2f, offset (%.3f, %.3f, %.3f), lerp %.2f",
                targetRoll, targetPitch, targetYaw, targetX, targetY, targetZ, lerpSpeed);
    }
}
