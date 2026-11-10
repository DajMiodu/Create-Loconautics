package com.lycoris.loconautics.common.sable;

import com.lycoris.loconautics.LoconauticsConstants;
import com.simibubi.create.content.contraptions.actors.seat.SeatBlock;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.EntityMovementExtension;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.ArrayList;
import java.util.List;

public final class SubLevelPassengers {

    private static final double SEARCH_MARGIN = 2.0;

    private static final double STRAY_MARGIN = 1.0;

    private static final double HEADROOM = 3.0;

    private static final double TICKS_PER_SECOND = 20.0;

    private static final int WATCH_FIRST_TICKS = 5;

    private static final int WATCH_STEP_TICKS = 2;

    private static final int WATCH_LAST_TICKS = 60;

    private SubLevelPassengers() {
    }

    public record Passenger(Entity entity, Vector3d localPos, Vector3d localVelocity, Vector3d localLook,
                            float yRot, float xRot, @Nullable BlockPos seatBlock) {}

    public record Snapshot(List<Passenger> passengers, BlockPos fromCenter) {}

    public static Snapshot capture(ServerLevel level, ServerSubLevel sub) {
        Pose3dc pose = sub.logicalPose();
        BoundingBox3ic plot = sub.getPlot().getBoundingBox();
        BlockPos fromCenter = sub.getPlot().getCenterBlock();
        List<Passenger> passengers = new ArrayList<>();
        if (plot == null || plot.volume() <= 0) {
            trace("capture car={} has no plot bounds", shortId(sub));
            return new Snapshot(passengers, fromCenter);
        }
        AABB area = worldArea(pose, plot).inflate(SEARCH_MARGIN);
        for (Entity entity : level.getEntities((Entity) null, area, e -> !e.isRemoved())) {
            Passenger passenger = passengerOf(entity, sub, pose, plot);
            if (passenger != null) {
                passengers.add(passenger);
            }
        }
        trace("capture car={} found {} in {} pos={} orient={}", shortId(sub), passengers.size(),
                level.dimension().location(), format(pose.position()), orientation(pose));
        return new Snapshot(passengers, fromCenter);
    }

    public static void restore(ServerLevel destLevel, ServerSubLevel moved, Snapshot snapshot) {
        BlockPos toCenter = moved.getPlot().getCenterBlock();
        for (Passenger passenger : snapshot.passengers()) {
            Entity entity = passenger.entity();
            if (entity.isRemoved()) {
                trace("restore {} skipped, it was removed before the car landed", describe(entity));
                continue;
            }
            Pose3dc pose = moved.logicalPose();
            Vector3d world = pose.transformPosition(new Vector3d(passenger.localPos()), new Vector3d());
            Vector3d velocity = pose.transformNormal(new Vector3d(passenger.localVelocity()), new Vector3d());
            float[] angles = anglesAt(pose, passenger);
            trace("restore {} to {} pose pos={} orient={} local before={} car speed before={} yaw {} pitch {} "
                            + "target world={} target yaw {} pitch {}", describe(entity), destLevel.dimension().location(),
                    format(pose.position()), orientation(pose), format(passenger.localPos()),
                    speed(passenger.localVelocity()), passenger.yRot(), passenger.xRot(), format(world), angles[0],
                    angles[1]);
            destLevel.getChunk(BlockPos.containing(world.x, world.y, world.z));
            Entity landed = place(destLevel, entity, world, velocity, angles[0], angles[1]);
            if (landed == null) {
                continue;
            }
            carry(landed, moved, velocity, angles[0], angles[1]);
            BlockPos seat = passenger.seatBlock() == null ? null
                    : shifted(passenger.seatBlock(), snapshot.fromCenter(), toCenter);
            sitBackDown(destLevel, seat, landed);
            Vector3d after = pose.transformPositionInverse(new Vector3d(landed.getX(), landed.getY(), landed.getZ()),
                    new Vector3d());
            Vec3 motion = landed.getDeltaMovement();
            trace("restore {} landed local after={} car speed after={} world={} vehicle={}", describe(landed),
                    format(after), speed(pose.transformNormalInverse(new Vector3d(motion.x, motion.y, motion.z),
                            new Vector3d())), format(new Vector3d(landed.getX(), landed.getY(), landed.getZ())),
                    landed.getVehicle() != null);
            if (landed instanceof ServerPlayer player) {
                watch(destLevel, moved, passenger, seat, player, 0);
            }
        }
    }

    @Nullable
    private static Passenger passengerOf(Entity entity, ServerSubLevel sub, Pose3dc pose, BoundingBox3ic plot) {
        Vector3d local = pose.transformPositionInverse(new Vector3d(entity.getX(), entity.getY(), entity.getZ()),
                new Vector3d());
        Entity vehicle = entity.getVehicle();
        if (vehicle != null && Sable.HELPER.getContaining(vehicle) == null) {
            trace("capture skip {} local={} it rides {} which carries it", describe(entity), format(local),
                    describe(vehicle));
            return null;
        }
        SubLevel tracking = Sable.HELPER.getTrackingSubLevel(entity);
        if (tracking != null && !tracking.getUniqueId().equals(sub.getUniqueId())) {
            trace("capture skip {} local={} it is tracked by another body", describe(entity), format(local));
            return null;
        }
        if (tracking == null && !inVolume(local, plot, 0.0)) {
            trace("capture skip {} local={} not tracked and outside the car volume", describe(entity), format(local));
            return null;
        }
        // a server player reports the last step its client sent, a mob reports its own delta movement
        Vec3 known = entity.getKnownMovement();
        Vector3d localVelocity = pose.transformNormalInverse(new Vector3d(known.x, known.y, known.z), new Vector3d());
        Vec3 look = entity.calculateViewVector(entity.getXRot(), entity.getYRot());
        Vector3d localLook = pose.transformNormalInverse(new Vector3d(look.x, look.y, look.z), new Vector3d());
        BlockPos seatBlock = vehicle == null ? null : BlockPos.containing(vehicle.position());
        trace("capture {} tracked={} local={} car speed={} world delta={} yaw {} pitch {} seat={}", describe(entity),
                tracking != null, format(local), speed(localVelocity),
                format(new Vector3d(known.x, known.y, known.z)), entity.getYRot(), entity.getXRot(),
                seatBlock == null ? "none" : seatBlock.toShortString());
        return new Passenger(entity, local, localVelocity, localLook, entity.getYRot(), entity.getXRot(), seatBlock);
    }

    @Nullable
    private static Entity place(ServerLevel destLevel, Entity entity, Vector3d world, Vector3d velocity, float yRot,
                                float xRot) {
        if (entity.level() == destLevel) {
            if (entity instanceof ServerPlayer player) {
                player.connection.teleport(world.x, world.y, world.z, yRot, xRot);
            } else {
                entity.moveTo(world.x, world.y, world.z, yRot, xRot);
            }
            return entity;
        }
        if (!entity.canChangeDimensions(entity.level(), destLevel)) {
            trace("restore {} stays behind, its type cannot change dimension", describe(entity));
            return null;
        }
        Entity landed = entity.changeDimension(new DimensionTransition(destLevel,
                new Vec3(world.x, world.y, world.z), new Vec3(velocity.x, velocity.y, velocity.z), yRot, xRot,
                DimensionTransition.DO_NOTHING));
        if (landed == null) {
            trace("restore {} lost, changeDimension returned nothing", describe(entity));
            return null;
        }
        landed.setPortalCooldown();
        return landed;
    }

    // a dimension change only applies the speed to mobs, a player keeps its old one and needs the motion packet
    private static void carry(Entity entity, ServerSubLevel moved, Vector3d velocity, float yRot, float xRot) {
        entity.setDeltaMovement(velocity.x, velocity.y, velocity.z);
        entity.setXRot(xRot);
        entity.resetFallDistance();
        if (entity instanceof LivingEntity living) {
            living.setYHeadRot(yRot);
            living.setYBodyRot(yRot);
        }
        if (entity instanceof ServerPlayer) {
            entity.hurtMarked = true;
        }
        ((EntityMovementExtension) entity).sable$setTrackingSubLevel(moved);
    }

    // client has no body for a few ticks, so the player falls until it shows up
    private static void watch(ServerLevel destLevel, ServerSubLevel moved, Passenger passenger,
                              @Nullable BlockPos seat, ServerPlayer player, int elapsed) {
        int delay = elapsed == 0 ? WATCH_FIRST_TICKS : WATCH_STEP_TICKS;
        MinecraftServer server = destLevel.getServer();
        server.tell(new TickTask(server.getTickCount() + delay, () -> {
            int now = elapsed + delay;
            if (player.isRemoved() || moved.isRemoved() || player.level() != destLevel) {
                trace("watch {} ended at {} ticks, player or car is gone", describe(player), now);
                return;
            }
            sitBackDown(destLevel, seat, player);
            if (player.getVehicle() != null) {
                trace("watch {} ended at {} ticks, seated", describe(player), now);
                return;
            }
            Pose3dc pose = moved.logicalPose();
            Vector3d local = pose.transformPositionInverse(new Vector3d(player.getX(), player.getY(), player.getZ()),
                    new Vector3d());
            if (inVolume(local, moved.getPlot().getBoundingBox(), STRAY_MARGIN)) {
                trace("watch {} settled at {} ticks, local={}", describe(player), now, format(local));
                return;
            }
            Vector3d world = pose.transformPosition(new Vector3d(passenger.localPos()), new Vector3d());
            Vector3d velocity = pose.transformNormal(new Vector3d(passenger.localVelocity()), new Vector3d());
            float[] angles = anglesAt(pose, passenger);
            trace("watch {} strayed at {} ticks, local={} put back at {}", describe(player), now, format(local),
                    format(world));
            Entity landed = place(destLevel, player, world, velocity, angles[0], angles[1]);
            if (landed != null) {
                carry(landed, moved, velocity, angles[0], angles[1]);
            }
            if (now < WATCH_LAST_TICKS) {
                watch(destLevel, moved, passenger, seat, player, now);
            }
        }));
    }

    private static void sitBackDown(ServerLevel destLevel, @Nullable BlockPos seat, Entity entity) {
        if (seat == null || entity.getVehicle() != null) {
            return;
        }
        if (destLevel.getBlockState(seat).getBlock() instanceof SeatBlock) {
            SeatBlock.sitDown(destLevel, seat, entity);
        }
    }

    private static BlockPos shifted(BlockPos seat, BlockPos fromCenter, BlockPos toCenter) {
        return new BlockPos(seat.getX() - fromCenter.getX() + toCenter.getX(), seat.getY(),
                seat.getZ() - fromCenter.getZ() + toCenter.getZ());
    }

    private static float[] anglesAt(Pose3dc pose, Passenger passenger) {
        Vector3d look = pose.transformNormal(new Vector3d(passenger.localLook()), new Vector3d());
        double flat = Math.sqrt(look.x * look.x + look.z * look.z);
        float yaw = flat < 1.0e-6 ? passenger.yRot()
                : (float) (Mth.atan2(-look.x, look.z) * Mth.RAD_TO_DEG);
        float pitch = (float) (-Mth.atan2(look.y, flat) * Mth.RAD_TO_DEG);
        return new float[] {yaw, pitch};
    }

    private static AABB worldArea(Pose3dc pose, BoundingBox3ic plot) {
        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double minZ = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;
        double maxZ = -Double.MAX_VALUE;
        Vector3d corner = new Vector3d();
        for (int i = 0; i < 8; i++) {
            corner.set((i & 1) == 0 ? plot.minX() : plot.maxX() + 1.0,
                    (i & 2) == 0 ? plot.minY() : plot.maxY() + 1.0,
                    (i & 4) == 0 ? plot.minZ() : plot.maxZ() + 1.0);
            pose.transformPosition(corner);
            minX = Math.min(minX, corner.x);
            minY = Math.min(minY, corner.y);
            minZ = Math.min(minZ, corner.z);
            maxX = Math.max(maxX, corner.x);
            maxY = Math.max(maxY, corner.y);
            maxZ = Math.max(maxZ, corner.z);
        }
        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private static boolean inVolume(Vector3d local, BoundingBox3ic plot, double margin) {
        return local.x >= plot.minX() - margin && local.x <= plot.maxX() + 1.0 + margin
                && local.y >= plot.minY() - margin && local.y <= plot.maxY() + 1.0 + HEADROOM
                && local.z >= plot.minZ() - margin && local.z <= plot.maxZ() + 1.0 + margin;
    }

    private static String describe(Entity entity) {
        return BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()) + " " + entity.getScoreboardName();
    }

    private static String shortId(ServerSubLevel sub) {
        return sub.getUniqueId().toString().substring(0, 8);
    }

    private static String format(Vector3dc v) {
        return String.format("(%.2f %.2f %.2f)", v.x(), v.y(), v.z());
    }

    private static String speed(Vector3d perTick) {
        return String.format("(%.2f %.2f %.2f) %.2f b/s", perTick.x * TICKS_PER_SECOND,
                perTick.y * TICKS_PER_SECOND, perTick.z * TICKS_PER_SECOND, perTick.length() * TICKS_PER_SECOND);
    }

    private static String orientation(Pose3dc pose) {
        Vector3d forward = pose.orientation().transform(new Vector3d(0.0, 0.0, 1.0));
        return String.format("yaw %.1f", Math.toDegrees(Math.atan2(-forward.x, forward.z)));
    }

    private static void trace(String message, Object... args) {
        LoconauticsConstants.LOGGER.info("[rider] " + message, args);
    }
}
