package com.lycoris.loconautics.trainphysics.drive;

import com.lycoris.loconautics.trainphysics.PoseTweak;
import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.trainphysics.SableTrain.Car;
import com.lycoris.loconautics.trainphysics.rail.RailCarriage;
import com.lycoris.loconautics.trainphysics.spawn.SubLevelSplitWatcher;
import com.lycoris.loconautics.trainphysics.visual.BogeyYawVisual;
import com.simibubi.create.content.trains.bogey.AbstractBogeyBlockEntity;
import com.simibubi.create.content.trains.entity.TravellingPoint;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.joml.Vector3f;
import org.joml.Vector3d;



public final class BogeyVisualUpdates {
    private BogeyVisualUpdates() {
    }

    static void updateYaw(SableTrain train) {
        com.lycoris.loconautics.trainphysics.wheels.GhostWheelBodies.syncSwing(train);
        if (train.isDerailed()) {
            clearBogeyRailPose(train);
            return;
        }
        ServerSubLevelContainer container = SubLevelContainer.getContainer(train.level());
        if (container == null) {
            return;
        }
        Car car = train.car();
        if (car.subLevelId() == null
                || !(container.getSubLevel(car.subLevelId()) instanceof ServerSubLevel sub)
                || sub.isRemoved()) {
            return;
        }
        if (car.bogeys().isEmpty()) {
            return;
        }
        ServerLevel subLevel = sub.getLevel();
        for (SableTrain.Bogey bogey : car.bogeys()) {
            BlockPos worldPos = sub.getPlot().getCenterBlock().offset(bogey.localPos());
            if (subLevel.getBlockEntity(worldPos) instanceof AbstractBogeyBlockEntity be) {
                org.joml.Vector3f offsetLocal = new org.joml.Vector3f();
                org.joml.Vector3d counter = PoseTweak.counter(sub,
                        new org.joml.Vector3d(worldPos.getX() + 0.5, worldPos.getY() + 0.5, worldPos.getZ() + 0.5),
                        car.carriage().referenceForward(), train.poseTweak());
                BogeyYawVisual.setRailPose(be, railLinkOf(bogey.rail()), offsetLocal,
                        new Vector3f((float) counter.x, (float) counter.y, (float) counter.z));
            }
        }
        for (BlockPos local : com.lycoris.loconautics.trainphysics.spawn.SubLevelSplitWatcher.idleBogeysCached(train)) {
            BlockPos worldPos = sub.getPlot().getCenterBlock().offset(local);
            RailCarriage idleRail = com.lycoris.loconautics.trainphysics.spawn.SubLevelSplitWatcher
                    .idleBogeyRail(train, sub, local);
            if (idleRail != null && subLevel.getBlockEntity(worldPos) instanceof AbstractBogeyBlockEntity be) {
                org.joml.Vector3d counter = PoseTweak.counter(sub,
                        new org.joml.Vector3d(worldPos.getX() + 0.5, worldPos.getY() + 0.5, worldPos.getZ() + 0.5),
                        car.carriage().referenceForward(), train.poseTweak());
                BogeyYawVisual.setRailPose(be, railLinkOf(idleRail),
                        new org.joml.Vector3f(),
                        new Vector3f((float) counter.x, (float) counter.y, (float) counter.z));
            }
        }
    }

    static BogeyYawVisual.Link railLinkOf(RailCarriage rail) {
        if (rail == null || rail.graph() == null) {
            return null;
        }
        TravellingPoint lead = rail.leading();
        TravellingPoint trail = rail.trailing();
        if (lead == null || trail == null || lead.node1 == null || lead.node2 == null
                || trail.node1 == null || trail.node2 == null) {
            return null;
        }
        return new BogeyYawVisual.Link(rail.graph().id,
                lead.node1.getNetId(), lead.node2.getNetId(), (float) lead.position,
                trail.node1.getNetId(), trail.node2.getNetId(), (float) trail.position,
                rail.lastTravelSign() < 0.0);
    }

    static void clearBogeyRailPose(SableTrain train) {
        ServerSubLevelContainer container = SubLevelContainer.getContainer(train.level());
        if (container == null) {
            return;
        }
        Car car = train.car();
        if (car.subLevelId() == null
                || !(container.getSubLevel(car.subLevelId()) instanceof ServerSubLevel sub) || sub.isRemoved()) {
            return;
        }
        ServerLevel subLevel = sub.getLevel();
        for (SableTrain.Bogey bogey : car.bogeys()) {
            BlockPos worldPos = sub.getPlot().getCenterBlock().offset(bogey.localPos());
            if (subLevel.getBlockEntity(worldPos) instanceof AbstractBogeyBlockEntity be) {
                BogeyYawVisual.clearRailBodyPose(be);
            }
        }
    }

    static void updateWheels(SableTrain train) {
        Car car = train.car();
        if (SableTrainDriver.useForceGlue(car) || train.isTrackless()) return;
        double ds = train.speed();
        if (ds == 0.0) return;

        ServerSubLevelContainer container = SubLevelContainer.getContainer(train.level());
        if (container == null) return;
        if (car.subLevelId() == null
                || !(container.getSubLevel(car.subLevelId()) instanceof ServerSubLevel sub)
                || sub.isRemoved()) return;

        TrainRailAdvance.animateBogeyWheels(car, sub, ds);
    }
}
