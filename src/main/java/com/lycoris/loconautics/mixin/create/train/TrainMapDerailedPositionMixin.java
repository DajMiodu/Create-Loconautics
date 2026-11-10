package com.lycoris.loconautics.mixin.create.train;

import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.common.sable.SubLevelData;
import com.lycoris.loconautics.trainphysics.state.CreateTrainShadow;
import com.lycoris.loconautics.trainphysics.rail.RailSeams;
import com.lycoris.loconautics.trainphysics.state.SableTrainRegistry;
import com.simibubi.create.compat.trainmap.TrainMapSync;
import com.simibubi.create.compat.trainmap.TrainMapSync.TrainMapSyncEntry;
import com.simibubi.create.content.trains.entity.Carriage;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.entity.TravellingPoint;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

// the track, so the map has to read the sub levels own pose instead
@Mixin(value = TrainMapSync.class, remap = false)
public abstract class TrainMapDerailedPositionMixin {

    @Inject(method = "createEntry", at = @At("RETURN"))
    private static void loconautics$followDerailedSubLevel(MinecraftServer server, Train shadow,
                                                           CallbackInfoReturnable<TrainMapSyncEntry> cir) {
        TrainMapSyncEntry entry = cir.getReturnValue();
        if (entry == null || entry.positions == null) {
            return;
        }
        List<CreateTrainShadow.Member> members = CreateTrainShadow.membersOf(shadow);
        for (int i = 0; i < members.size() && i < entry.dimensions.size() && (i + 1) * 6 <= entry.positions.length; i++) {
            CreateTrainShadow.Member member = members.get(i);
            SableTrain train = SableTrainRegistry.get(member.cart());
            if (train == null || !train.isDerailed() || train.car() == null) {
                loconautics$liftHosted(shadow, entry, i);
                continue;
            }
            ServerSubLevel sub = SubLevelData.resolve(train.level(), train.car().subLevelId());
            if (sub == null) {
                Vector3dc lastKnown = train.lastKnownPos();
                if (lastKnown != null) {
                    Vector3d at = new Vector3d(lastKnown);
                    loconautics$write(entry.positions, i * 6, at);
                    loconautics$write(entry.positions, i * 6 + 3, at);
                    entry.dimensions.set(i, train.level().dimension());
                }
                continue;
            }
            Vector3dc localLead = train.car().localLead();
            Vector3dc localTrail = train.car().localTrail();
            Vector3d lead = localLead == null ? SubLevelData.position(sub) : SubLevelData.worldOf(sub, localLead);
            Vector3d trail = localTrail == null ? SubLevelData.position(sub) : SubLevelData.worldOf(sub, localTrail);
            loconautics$write(entry.positions, i * 6, member.reversed() ? trail : lead);
            loconautics$write(entry.positions, i * 6 + 3, member.reversed() ? lead : trail);
            entry.dimensions.set(i, train.level().dimension());
        }
    }

    // a rail on a sub level reports its point in plot coordinates, the map wants the world
    @Unique
    private static void loconautics$liftHosted(Train shadow, TrainMapSyncEntry entry, int index) {
        if (shadow.graph == null || index >= shadow.carriages.size() || entry.dimensions.get(index) == null) {
            return;
        }
        Carriage carriage = shadow.carriages.get(index);
        loconautics$lift(entry.positions, index * 6, carriage.getLeadingPoint());
        loconautics$lift(entry.positions, index * 6 + 3, carriage.getTrailingPoint());
    }

    @Unique
    private static void loconautics$lift(Float[] positions, int at, TravellingPoint point) {
        if (point == null || point.edge == null || point.edge.node1 == null) {
            return;
        }
        Vec3 lifted = RailSeams.worldPointOf(point.edge.node1.getLocation(),
                new Vec3(positions[at], positions[at + 1], positions[at + 2]));
        positions[at] = (float) lifted.x;
        positions[at + 1] = (float) lifted.y;
        positions[at + 2] = (float) lifted.z;
    }

    @Unique
    private static void loconautics$write(Float[] positions, int at, Vector3d pos) {
        positions[at] = (float) pos.x;
        positions[at + 1] = (float) pos.y;
        positions[at + 2] = (float) pos.z;
    }
}
