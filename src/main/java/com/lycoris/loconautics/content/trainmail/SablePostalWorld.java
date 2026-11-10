package com.lycoris.loconautics.content.trainmail;

import com.lycoris.loconautics.content.schedule.SableScheduleRuntime;
import com.lycoris.loconautics.content.schedule.RouteTraffic;
import com.lycoris.loconautics.content.schedule.SchedulePath;
import com.lycoris.loconautics.content.schedule.SchedulePathfinder;
import com.lycoris.loconautics.content.schedule.StationRouting;
import com.lycoris.loconautics.trainphysics.storage.ConsistAccess;
import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.trainphysics.TrainLength;
import com.lycoris.loconautics.common.train.TrainStatusChat;
import com.lycoris.loconautics.trainphysics.station.TrainStations;
import com.lycoris.loconautics.trainphysics.rail.RailCarriage;
import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.content.trains.graph.EdgePointType;
import com.simibubi.create.content.trains.graph.TrackGraph;
import com.simibubi.create.content.trains.station.GlobalStation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class SablePostalWorld implements PostalWorld<SchedulePath> {

    private final SableScheduleRuntime runtime;
    private final SableTrain train;
    @Nullable
    private SchedulePathfinder.Routing routing;

    public SablePostalWorld(SableScheduleRuntime runtime) {
        this.runtime = runtime;
        this.train = runtime.train();
    }

    @Override
    public boolean tripped() {
        return runtime.loconautics$tripped();
    }

    @Override
    public List<String> cargoAddresses(String regex) {
        List<String> addresses = new ArrayList<>();
        for (IItemHandler items : ConsistAccess.itemsIn(train.level(), train.car().subLevelId(),
                ConsistAccess.WHOLE_CONSIST)) {
            for (int slot = 0; slot < items.getSlots(); slot++) {
                ItemStack stack = items.getStackInSlot(slot);
                if (PackageItem.isPackage(stack) && PackageItem.getAddress(stack).matches(regex)) {
                    addresses.add(PackageItem.getAddress(stack));
                }
            }
        }
        return addresses;
    }

    @Override
    public List<GlobalStation> stations() {
        RailCarriage rail = TrainStations.noseRail(train);
        TrackGraph graph = rail == null ? null : rail.graph();
        return graph == null ? List.of() : new ArrayList<>(graph.getPoints(EdgePointType.STATION));
    }

    @Override
    @Nullable
    public GlobalStation here() {
        return train.currentStation() != null ? train.currentStation() : train.atStation();
    }

    @Override
    public MinecraftServer server() {
        return train.level().getServer();
    }

    @Override
    @Nullable
    public SchedulePath routeTo(Collection<GlobalStation> wanted) {
        StationRouting.Plan plan = StationRouting.plan(runtime,
                station -> wanted.stream().anyMatch(other -> PostalWorld.same(other, station)), false);
        return plan == null ? null : plan.best;
    }

    @Override
    public void cannotDeliver(String address) {
        TrainStatusChat.failedPackageNoTarget(train, address);
    }

    @Override
    public double legCost(@Nullable GlobalStation from, GlobalStation to) {
        if (from == null) {
            StationRouting.Plan plan = StationRouting.plan(runtime, station -> PostalWorld.same(station, to), false);
            return plan == null || plan.best == null ? Double.POSITIVE_INFINITY : plan.bestCost;
        }
        RailCarriage rail = TrainStations.noseRail(train);
        TrackGraph graph = rail == null ? null : rail.graph();
        if (graph == null) {
            return Double.POSITIVE_INFINITY;
        }
        if (routing == null) {
            routing = SchedulePathfinder.Routing
                    .through(TrainLength.consistLength(train.level(), train.car().subLevelId()))
                    .around(RouteTraffic.around(train, graph));
        }
        return PostalRoute.between(graph, from, to, routing);
    }

    @Override
    public Set<UUID> warned() {
        return runtime.loconautics$warned();
    }

    @Override
    public void sayUnreachable(GlobalStation station) {
        TrainStatusChat.postalUnreachable(train, station.name);
    }
}
