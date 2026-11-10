package com.lycoris.loconautics.trainphysics.drive.observer;

import com.lycoris.loconautics.trainphysics.storage.CarriageStorage;
import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.common.sable.SubLevelData;
import com.lycoris.loconautics.trainphysics.TrainLength;
import com.lycoris.loconautics.trainphysics.rail.RailCarriage;
import com.lycoris.loconautics.trainphysics.state.CreateTrainShadow;
import com.simibubi.create.content.logistics.filter.FilterItemStack;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.entity.TravellingPoint;
import com.simibubi.create.content.trains.entity.TravellingPoint.SteerDirection;
import com.simibubi.create.content.trains.observer.TrackObserver;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;

public final class TrainObservers {

    public static void tick(SableTrain train) {
        if (train.isDerailed() || train.isPhysics() || train.car() == null) {
            return;
        }
        RailCarriage rail = train.car().carriage();
        if (rail == null || rail.graph() == null || rail.leading() == null || rail.trailing() == null) {
            return;
        }
        TravellingPoint trailing = rail.trailing();
        if (trailing.edge == null) {
            return;
        }
        Train shadow = CreateTrainShadow.of(train.id());
        if (shadow == null) {
            return;
        }
        ServerLevel level = train.level();
        double spacing = rail.bogeySpacing();
        double length = level == null ? spacing : Math.max(spacing, TrainLength.carLength(level, train));
        double overhang = (length - spacing) * 0.5;
        TravellingPoint scout = new TravellingPoint(trailing.node1, trailing.node2, trailing.edge,
                trailing.position, trailing.upsideDown);
        if (overhang > 0.0) {
            scout.travel(rail.graph(), -overhang, scout.steer(SteerDirection.NONE, rail.upNormal()),
                    scout.ignoreEdgePoints(), scout.ignoreTurns(), scout.ignorePortals());
        }
        scout.travel(rail.graph(), length, scout.follow(rail.leading()), (distance, pair) -> {
            if (pair.getFirst() instanceof TrackObserver observer && matches(train, observer)) {
                observer.keepAlive(shadow);
            }
            return false;
        }, scout.ignoreTurns(), scout.ignorePortals());
    }

    private static boolean matches(SableTrain train, TrackObserver observer) {
        FilterItemStack filter = observer.getFilter();
        if (filter.isEmpty()) {
            return true;
        }
        ServerLevel level = train.level();
        ServerSubLevel sub = level == null ? null : SubLevelData.resolve(level, train.car().subLevelId());
        if (sub == null) {
            return false;
        }
        for (IItemHandler handler : CarriageStorage.items(sub)) {
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                ItemStack stack = handler.getStackInSlot(slot);
                if (!stack.isEmpty() && filter.test(level, stack)) {
                    return true;
                }
            }
        }
        for (IFluidHandler tank : CarriageStorage.fluids(sub)) {
            for (int i = 0; i < tank.getTanks(); i++) {
                FluidStack fluid = tank.getFluidInTank(i);
                if (!fluid.isEmpty() && filter.test(level, fluid)) {
                    return true;
                }
            }
        }
        return false;
    }

    private TrainObservers() {
    }
}
