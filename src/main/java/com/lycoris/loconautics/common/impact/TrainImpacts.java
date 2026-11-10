package com.lycoris.loconautics.common.impact;

import com.lycoris.loconautics.common.advancement.TrainAdvancements;
import com.lycoris.loconautics.config.LoconauticsConfig;
import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.trainphysics.drive.TrainPropulsion;
import com.lycoris.loconautics.common.sable.SubLevelData;
import com.lycoris.loconautics.trainphysics.TrainConsist;
import com.lycoris.loconautics.trainphysics.drive.ConsistPower;
import com.lycoris.loconautics.trainphysics.rail.RailCarriage;
import com.lycoris.loconautics.trainphysics.state.CreateTrainShadow;
import com.lycoris.loconautics.trainphysics.wheels.GhostWheelBodies;
import com.simibubi.create.Create;
import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.content.trains.entity.Carriage;
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.entity.TravellingPoint;
import dev.ryanhcode.sable.api.physics.mass.MassData;
import dev.ryanhcode.sable.physics.config.block_properties.PhysicsBlockPropertyHelper;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.createmod.catnip.data.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;


public final class TrainImpacts {

    private TrainImpacts() {
    }

    private static final double MIN_TRAVEL = 0.05;
    private static final double NEAR = 40.0;

    private static final Map<Meeting, Long> MET = new HashMap<>();
    private static final Map<Train, Double> CREATE_MASS = new WeakHashMap<>();

    private record Meeting(UUID create, UUID cart) {}

    public static double speedChange(double closing, double selfMass, double otherMass) {
        double sum = selfMass + otherMass;
        return sum <= 1.0e-9 ? closing * 0.5 : closing * otherMass / sum;
    }

    public static boolean derails(double speedChange) {
        return speedChange >= LoconauticsConfig.DERAIL_COLLISION_SPEED.get();
    }

    public static double bodyMass(ServerSubLevel sub) {
        MassData md = sub.getMassTracker();
        return (md != null && !md.isInvalid() && md.getMass() > 0.0) ? md.getMass() : 1.0;
    }

    public static double rakeMass(SableTrain train, ServerSubLevel sub) {
        double mass = ConsistPower.consistMass(train, 0.0);
        if (mass <= 0.0) {
            mass = bodyMass(sub);
        }
        ServerLevel level = sub.getLevel();
        for (UUID car : TrainConsist.connectedSubLevels(level, sub.getUniqueId())) {
            for (UUID plateId : GhostWheelBodies.wheelBodiesOf(car)) {
                ServerSubLevel plate = SubLevelData.resolve(level, plateId);
                if (plate != null && !plate.isRemoved()) {
                    mass += bodyMass(plate);
                }
            }
        }
        return mass;
    }

    public static void throwOff(SableTrain train, ServerSubLevel sub, Vector3d deltaV) {
        if (TrainPropulsion.isMonorail(train)) {
            return;
        }
        train.setDerailed(true, "collision");
        TrainAdvancements.crashed(sub, train);
        TrainKnockback.applyKnockbackNow(train, deltaV);
    }

    public static void createMeetsCart(ServerLevel level, Train create, SableTrain cart, Vec3 axis, Vec3 at) {
        ServerSubLevel sub = cart.car() == null ? null : SubLevelData.resolve(level, cart.car().subLevelId());
        Vec3 createForward = forwardOf(create);
        if (sub == null || createForward == null) {
            return;
        }
        long now = level.getGameTime();
        MET.values().removeIf(tick -> tick < now);
        if (MET.put(new Meeting(create.id, cart.id()), now) != null) {
            return;
        }
        double axisDot = createForward.dot(axis);
        double createAlong = create.speed * 20.0 * axisDot;
        Vector3d velocity = SubLevelData.linearVelocity(sub);
        double cartAlong = velocity == null ? 0.0 : velocity.x * axis.x + velocity.y * axis.y + velocity.z * axis.z;
        double closing = createAlong - cartAlong;
        if (closing <= 0.0) {
            return;
        }
        double cartMass = rakeMass(cart, sub);
        double createMass = createTrainMass(create);
        boolean borrowed = createMass <= 0.0;
        if (borrowed) {
            createMass = cartMass;
        }
        double cartChange = speedChange(closing, cartMass, createMass);
        double createChange = closing - cartChange;
        boolean cartOff = derails(cartChange);
        boolean createOff = derails(createChange);

        Vector3d cartDeltaV = new Vector3d(axis.x, axis.y, axis.z).mul(cartChange);
        if (cartOff) {
            throwOff(cart, sub, cartDeltaV);
        } else {
            TrainKnockback.applyKnockbackNow(cart, cartDeltaV);
        }
        if (createOff) {
            create.crash();
        } else {
            create.speed = Math.abs(axisDot) > 0.5 ? (createAlong - createChange) / 20.0 / axisDot : 0.0;
        }
        double perTick = closing / 20.0;
        if ((cartOff || createOff) && perTick > 0.2) {
            level.explode(null, at.x, at.y, at.z, (float) Math.min(3.0 * perTick, 5.0), Level.ExplosionInteraction.NONE);
        }
    }

    public static void cartIntoCreate(SableTrain cart, ServerSubLevel sub) {
        ServerLevel level = cart.level();
        Train near = nearestCreateTrain(SubLevelData.position(sub));
        if (cart.isDerailed() || cart.car() == null) {
            return;
        }
        RailCarriage rail = cart.car().carriage();
        Train shadow = CreateTrainShadow.of(cart.id());
        if (rail == null || shadow == null || rail.graph() == null || rail.leading() == null || rail.trailing() == null) {
            return;
        }
        Vec3 lead = rail.leading().getPosition(rail.graph());
        Vec3 trail = rail.trailing().getPosition(rail.graph());
        Vec3 chord = lead.subtract(trail);
        Vector3d velocity = SubLevelData.linearVelocity(sub);
        if (velocity == null || chord.lengthSqr() < 1.0e-6) {
            return;
        }
        Vec3 dir = chord.normalize();
        double along = velocity.x * dir.x + velocity.y * dir.y + velocity.z * dir.z;
        if (Math.abs(along) < MIN_TRAVEL) {
            return;
        }
        Vec3 start = along > 0.0 ? lead : trail;
        Vec3 end = along > 0.0 ? trail : lead;
        Pair<Train, Vec3> hit = shadow.findCollidingTrain(level, start, end, level.dimension());
        if (hit == null || CreateTrainShadow.isShadow(hit.getFirst())) {
            return;
        }
        createMeetsCart(level, hit.getFirst(), cart, end.subtract(start).normalize(), hit.getSecond());
    }

    private static Train nearestCreateTrain(Vector3d at) {
        Vec3 from = new Vec3(at.x, at.y, at.z);
        Train best = null;
        double bestDistance = NEAR;
        for (Train train : Create.RAILWAYS.trains.values()) {
            if (CreateTrainShadow.isShadow(train)) {
                continue;
            }
            double distance = distanceTo(train, from);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = train;
            }
        }
        return best;
    }

    private static double distanceTo(Train train, Vec3 from) {
        double best = Double.MAX_VALUE;
        if (train.graph == null) {
            return best;
        }
        for (Carriage carriage : train.carriages) {
            TravellingPoint lead = carriage.getLeadingPoint();
            TravellingPoint trail = carriage.getTrailingPoint();
            if (lead.edge == null || trail.edge == null) {
                continue;
            }
            best = Math.min(best, Math.min(lead.getPosition(train.graph).distanceTo(from),
                    trail.getPosition(train.graph).distanceTo(from)));
        }
        return best;
    }

    private static double createTrainMass(Train train) {
        Double cached = CREATE_MASS.get(train);
        if (cached != null) {
            return cached;
        }
        double mass = 0.0;
        for (Carriage carriage : train.carriages) {
            CarriageContraptionEntity entity = carriage.anyAvailableEntity();
            Contraption contraption = entity == null ? null : entity.getContraption();
            if (contraption == null) {
                return 0.0;
            }
            BlockGetter world = contraption.getContraptionWorld();
            for (Map.Entry<BlockPos, StructureBlockInfo> block : contraption.getBlocks().entrySet()) {
                mass += PhysicsBlockPropertyHelper.getMass(world, block.getKey(), block.getValue().state());
            }
        }
        CREATE_MASS.put(train, mass);
        return mass;
    }

    private static Vec3 forwardOf(Train train) {
        if (train.graph == null || train.carriages.isEmpty()) {
            return null;
        }
        Carriage first = train.carriages.get(0);
        TravellingPoint lead = first.getLeadingPoint();
        TravellingPoint trail = first.getTrailingPoint();
        if (lead.edge == null || trail.edge == null) {
            return null;
        }
        Vec3 chord = lead.getPosition(train.graph).subtract(trail.getPosition(train.graph));
        return chord.lengthSqr() < 1.0e-6 ? null : chord.normalize();
    }
}
