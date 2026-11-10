package com.lycoris.loconautics.content.hose.spray.entity;


import com.lycoris.loconautics.content.hose.spray.interaction.HoseSprayInteraction;
import com.lycoris.loconautics.content.hose.spray.interaction.HoseSprayInteractions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;

public class HoseSprayEntity extends Entity {

    private static final double GRAVITY = 0.04 * 0.35;
    private static final double DRAG = 0.90;
    private static final int SAFETY_MAX_AGE = 60;

    private static final double ARM_DISTANCE_SQ = 1.0;
    private static final double SWEEP_STEP = 0.25;
    private static final double HIT_MARGIN = 0.3;

    private FluidStack fluid = FluidStack.EMPTY;
    private float power = 0.5f;
    private int age;
    private final java.util.Set<java.util.UUID> ignored = new java.util.HashSet<>();
    @org.jetbrains.annotations.Nullable private Vec3 origin;
    @org.jetbrains.annotations.Nullable private java.util.UUID attacker;

    public HoseSprayEntity(EntityType<? extends HoseSprayEntity> type, Level level) {
        super(type, level);
    }

    public static HoseSprayEntity launch(ServerLevel level, Vec3 origin, Vec3 dir, FluidStack fluid, float power,
                                         @org.jetbrains.annotations.Nullable java.util.UUID attacker) {
        HoseSprayEntity e = new HoseSprayEntity(HoseSprayEntities.HOSE_SPRAY.get(), level);
        e.setPos(origin.x, origin.y, origin.z);
        e.origin = origin;
        e.attacker = attacker;
        double speed = 0.30 + 0.30 * power;
        Vec3 d = dir.lengthSqr() < 1.0e-9 ? new Vec3(0.0, -1.0, 0.0) : dir.normalize();
        e.setDeltaMovement(d.scale(speed));
        e.fluid = fluid.copy();
        e.power = power;
        level.addFreshEntity(e);
        return e;
    }

    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    @Override
    public boolean displayFireAnimation() {
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            return;
        }
        if (++age > SAFETY_MAX_AGE || fluid.isEmpty() || getY() < level().getMinBuildHeight()) {
            discard();
            return;
        }
        HoseSprayInteraction interaction = HoseSprayInteractions.get(fluid);
        if (interaction == null) {
            discard();
            return;
        }
        ServerLevel slevel = (ServerLevel) level();
        if (age == 1) {
            for (Entity e : slevel.getEntities(this, getBoundingBox().inflate(Math.sqrt(ARM_DISTANCE_SQ)),
                    HoseSprayEntity::canBeSprayed)) {
                ignored.add(e.getUUID());
            }
        }
        Vec3 from = position();
        Vec3 v = getDeltaMovement().add(0.0, -GRAVITY, 0.0).scale(DRAG);
        setDeltaMovement(v);
        Vec3 to = from.add(v);
        boolean hitEntity = hitEntitiesAlong(slevel, interaction, from, to);
        if (sweepBlocks(slevel, interaction, from, to, v) != null || hitEntity) {
            discard();
            return;
        }
        setPos(to.x, to.y, to.z);
    }

    @org.jetbrains.annotations.Nullable
    private Vec3 sweepBlocks(ServerLevel slevel, HoseSprayInteraction interaction, Vec3 from, Vec3 to, Vec3 v) {
        int steps = Math.max(1, (int) Math.ceil(v.length() / SWEEP_STEP));
        BlockPos last = BlockPos.containing(from);
        for (int i = 1; i <= steps; i++) {
            Vec3 p = from.lerp(to, (double) i / steps);
            BlockPos cell = BlockPos.containing(p);
            if (cell.equals(last)) {
                continue;
            }
            Direction dir = stepDir(last, cell, v);
            last = cell;
            boolean armed = origin == null || p.distanceToSqr(origin) > ARM_DISTANCE_SQ;
            if (interaction.onBlock(slevel, cell, slevel.getBlockState(cell), dir, fluid, power) && armed) {
                return p;
            }
        }
        return null;
    }

    private boolean hitEntitiesAlong(ServerLevel slevel, HoseSprayInteraction interaction, Vec3 from, Vec3 to) {
        AABB sweep = new AABB(from, to).inflate(HIT_MARGIN);
        boolean hit = false;
        LivingEntity who = attacker != null && slevel.getEntity(attacker) instanceof LivingEntity living ? living : null;
        for (Entity e : slevel.getEntities(this, sweep, cand -> canBeSprayed(cand) && !ignored.contains(cand.getUUID()))) {
            AABB box = e.getBoundingBox().inflate(HIT_MARGIN);
            if (box.contains(from) || box.contains(to) || box.clip(from, to).isPresent()) {
                interaction.onEntity(slevel, e, fluid, power, who);
                hit = true;
            }
        }
        return hit;
    }

    private static boolean canBeSprayed(Entity e) {
        return e.isAlive() && !e.isSpectator() && !(e instanceof HoseSprayEntity);
    }

    private static Direction stepDir(BlockPos from, BlockPos to, Vec3 v) {
        int dx = to.getX() - from.getX(), dy = to.getY() - from.getY(), dz = to.getZ() - from.getZ();
        if (Math.abs(dx) + Math.abs(dy) + Math.abs(dz) != 1) {
            return nearestDir(v);
        }
        return Direction.fromDelta(dx, dy, dz);
    }

    private static Direction nearestDir(Vec3 v) {
        double ax = Math.abs(v.x), ay = Math.abs(v.y), az = Math.abs(v.z);
        if (ay >= ax && ay >= az) {
            return v.y < 0 ? Direction.DOWN : Direction.UP;
        }
        if (ax >= az) {
            return v.x < 0 ? Direction.WEST : Direction.EAST;
        }
        return v.z < 0 ? Direction.NORTH : Direction.SOUTH;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }
}
