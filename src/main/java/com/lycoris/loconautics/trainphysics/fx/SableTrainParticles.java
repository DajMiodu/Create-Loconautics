package com.lycoris.loconautics.trainphysics.fx;

import com.lycoris.loconautics.trainphysics.portal.PortalCutsClient;
import com.lycoris.loconautics.trainphysics.state.SableTrainClientRegistry;
import com.lycoris.loconautics.trainphysics.visual.BogeyWheelAnimator;
import com.simibubi.create.content.trains.bogey.AbstractBogeyBlock;
import com.simibubi.create.content.trains.bogey.AbstractBogeyBlockEntity;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.createmod.catnip.animation.LerpedFloat;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@OnlyIn(Dist.CLIENT)
public final class SableTrainParticles {

    private static final Map<UUID, SableTrainParticles> ACTIVE = new HashMap<>();

    private static final double SPEED_SCALE = 3.0;
    private static final double MAX_DISTANCE = 64.0;

    public static void tickAll() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            ACTIVE.clear();
            return;
        }
        if (mc.isPaused() || mc.cameraEntity == null) {
            return;
        }
        Set<UUID> ids = SableTrainClientRegistry.ids();
        ACTIVE.keySet().removeIf(id -> !ids.contains(id));
        if (ids.isEmpty()) {
            return;
        }
        SubLevelContainer container = SubLevelContainer.getContainer(mc.level);
        if (container == null) {
            return;
        }
        for (UUID id : ids) {
            SubLevel sub = container.getSubLevel(id);
            if (!(sub instanceof ClientSubLevel clientSub) || sub.isRemoved()) {
                ACTIVE.remove(id);
                continue;
            }
            try {
                ACTIVE.computeIfAbsent(id, k -> new SableTrainParticles()).tick(mc, clientSub);
            } catch (RuntimeException t) {
                com.lycoris.loconautics.LoconauticsConstants.LOGGER
                        .warn("[sabletrain] particle tick failed for train {}, dropping it", id, t);
                ACTIVE.remove(id);
            }
        }
    }


    private final LerpedFloat brakes = LerpedFloat.linear();
    private double prevMotion;
    private Vector3d lastPos;
    private Vec3 derailSmokeOffset;
    private int diagTick;

    private SableTrainParticles() {
    }

    private void tick(Minecraft mc, ClientSubLevel sub) {
        Vector3dc p = sub.renderPose().position();
        Vector3d pos = new Vector3d(p);
        Vector3d motion = lastPos == null ? new Vector3d() : pos.sub(lastPos, new Vector3d());
        lastPos = pos;

        Vec3 camPos = mc.cameraEntity.position();
        if (pos.distanceSquared(camPos.x, camPos.y, camPos.z) > MAX_DISTANCE * MAX_DISTANCE) {
            return;
        }

        SableTrainClientRegistry.TrainMarker marker = SableTrainClientRegistry.get(sub.getUniqueId());
        double rawSpeed = marker != null ? Math.abs(marker.speed()) : motion.length();
        double length = Math.min(rawSpeed * SPEED_SCALE, 2.0);
        RandomSource r = mc.level.random;
        List<AbstractBogeyBlockEntity> bogeys = BogeyWheelAnimator.bogeysOf(sub);
        if (bogeys.isEmpty()) {
            return;
        }

        if (marker != null && marker.derailed()) {
            if (derailSmokeOffset == null) {
                derailSmokeOffset = new Vec3((r.nextDouble() - 0.5) * 3.0, (r.nextDouble() - 0.5) * 0.75,
                        (r.nextDouble() - 0.5) * 3.0);
            }
            if (r.nextFloat() < 1.0f / 20.0f) {
                emit(mc, sub, ParticleTypes.CAMPFIRE_COSY_SMOKE,
                        pos.x + derailSmokeOffset.x, pos.y + derailSmokeOffset.y, pos.z + derailSmokeOffset.z,
                        0.0, 0.04, 0.0);
            }
            double sliding = motion.length();
            if (sliding > 0.02) {
                float cutoff = (float) Math.min(1.0, sliding * 3.0 * 1.2);
                Vec3 drift = new Vec3(motion.x, motion.y, motion.z).scale(0.75);
                for (AbstractBogeyBlockEntity be : bogeys) {
                    Vec3 c = bogeyCenter(sub, be);
                    double side = BogeyWheelAnimator.railSide(be);
                    if (!BogeyWheelAnimator.isGroundedAt(sub.getLevel(), c.x, c.y, c.z, side)) {
                        continue;
                    }
                    emitCorners(mc, sub, be, true, cutoff, false, drift, r);
                    BlockPos groundPos = BlockPos.containing(c.x, c.y - side, c.z);
                    BlockState ground = sub.getLevel().getBlockState(groundPos);
                    if (!ground.isAir()) {
                        BlockParticleOption dust = new BlockParticleOption(ParticleTypes.BLOCK, ground);
                        int n = 1 + r.nextInt(3);
                        for (int k = 0; k < n; k++) {
                            double ux = drift.x + (r.nextDouble() - 0.5) * 0.3;
                            double uy = (0.15 + r.nextDouble() * 0.3) * side;
                            double uz = drift.z + (r.nextDouble() - 0.5) * 0.3;
                            emit(mc, sub, dust,
                                    c.x + (r.nextDouble() - 0.5) * 0.9, c.y - 0.9 * side, c.z + (r.nextDouble() - 0.5) * 0.9,
                                    ux, uy, uz);
                        }
                    }
                }
            }
            return;
        }


        brakes.chase(prevMotion > length + length / 512.0 ? 1.0 : 0.0, 0.25, LerpedFloat.Chaser.exp(0.625));
        brakes.tickChaser();
        prevMotion = length;

        Vec3 drift = new Vec3(motion.x, motion.y, motion.z).scale(0.75);
        float cutoff = length < 0.08f ? 0.0f : Mth.clamp((float) length * 0.6f, 0.15f, 0.5f);
        if (length > 0.1666666716337204) {
            cutoff = Math.max(cutoff, brakes.getValue() * 1.15f);
        }

        if (++diagTick % 20 == 0) {
        }

        Set<BlockPos> slipping = marker != null ? marker.slippingBogeys() : Set.of();
        for (AbstractBogeyBlockEntity be : bogeys) {
            emitCorners(mc, sub, be, true, cutoff, false, drift, r);
            if (!slipping.isEmpty()) {
                BlockPos local = be.getBlockPos().subtract(sub.getPlot().getCenterBlock());
                if (slipping.contains(local)) {
                    emitSlipArc(mc, sub, be, r);
                }
            }
        }
    }

    private record RailFrame(Vec3 center, double fx, double fz, double lx, double lz) {
    }

    @Nullable
    private static RailFrame railFrame(ClientSubLevel sub, AbstractBogeyBlockEntity be) {
        BlockState state = be.getBlockState();
        Direction.Axis axis = state.hasProperty(AbstractBogeyBlock.AXIS)
                ? state.getValue(AbstractBogeyBlock.AXIS) : Direction.Axis.X;
        Vector3d f = axis == Direction.Axis.X ? new Vector3d(1, 0, 0) : new Vector3d(0, 0, 1);
        BogeyWheelAnimator.railWorldRotation(sub, be, new org.joml.Quaternionf()).transform(f);
        double len = Math.sqrt(f.x * f.x + f.z * f.z);
        if (len < 1.0e-6) {
            return null;
        }
        double fx = f.x / len;
        double fz = f.z / len;
        return new RailFrame(bogeyCenter(sub, be), fx, fz, -fz, fx);
    }

    private static void emitSlipArc(Minecraft mc, ClientSubLevel sub, AbstractBogeyBlockEntity be, RandomSource r) {
        if (r.nextFloat() > 0.4f) {
            return;
        }
        RailFrame frame = railFrame(sub, be);
        if (frame == null) {
            return;
        }
        Vec3 center = frame.center();
        double fx = frame.fx();
        double fz = frame.fz();
        double lx = frame.lx();
        double lz = frame.lz();
        ParticleOptions contact = be.getStyle().contactParticle;
        double side = BogeyWheelAnimator.railSide(be);
        for (int j : new int[] {1, -1}) {
            double px = center.x + lx * (j * 1.15) + fx * (r.nextDouble() - 0.5);
            double py = center.y - 1.1 * side;
            double pz = center.z + lz * (j * 1.15) + fz * (r.nextDouble() - 0.5);
            double mx = lx * (j * 0.15);
            double my = (0.75 + r.nextDouble() * 0.25) * side;
            double mz = lz * (j * 0.15);
            emit(mc, sub, contact, px, py, pz, mx, my, mz);
        }
    }

    private static void emitCorners(Minecraft mc, ClientSubLevel sub, AbstractBogeyBlockEntity be,
                                    boolean spark, float cutoff, boolean smokeLeniency,
                                    Vec3 drift, RandomSource r) {
        RailFrame frame = railFrame(sub, be);
        if (frame == null) {
            return;
        }
        Vec3 center = frame.center();
        double fx = frame.fx();
        double fz = frame.fz();
        double lx = frame.lx();
        double lz = frame.lz();

        ParticleOptions contact = be.getStyle().contactParticle;
        ParticleOptions smoke = be.getStyle().smokeParticle;
        double side = BogeyWheelAnimator.railSide(be);
        double yOff = (spark ? -1.1 : -0.4) * side;

        for (int j : new int[] {1, -1}) {
            if (r.nextFloat() > cutoff && (!smokeLeniency || r.nextInt(4) == 0)) {
                continue;
            }
            for (int i : new int[] {1, -1}) {
                if (r.nextFloat() > cutoff && (!smokeLeniency || r.nextInt(4) == 0)) {
                    continue;
                }
                double px = center.x + lx * (j * 1.15) + fx * i;
                double py = center.y + yOff;
                double pz = center.z + lz * (j * 1.15) + fz * i;
                double kick = spark ? 0.5 : 0.25;
                double mx = lx * (j * kick) + drift.x;
                double my = (spark ? 0.49 : -0.29) * side + drift.y;
                double mz = lz * (j * kick) + drift.z;
                emit(mc, sub, spark ? contact : smoke, px, py, pz, mx, my, mz);
            }
        }
    }

    private static void emit(Minecraft mc, ClientSubLevel sub, ParticleOptions options, double x, double y, double z,
                             double vx, double vy, double vz) {
        if (PortalCutsClient.hiddenAtWorld(sub, x, y, z)) {
            return;
        }
        ParticleWorldLock.addParticle(mc.level, options, x, y, z, vx, vy, vz);
    }

    private static Vec3 bogeyCenter(ClientSubLevel sub, AbstractBogeyBlockEntity be) {
        return sub.renderPose().transformPosition(Vec3.atCenterOf(be.getBlockPos()));
    }
}
