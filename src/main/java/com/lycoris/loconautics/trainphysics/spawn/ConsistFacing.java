package com.lycoris.loconautics.trainphysics.spawn;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.common.sable.SubLevelData;
import com.lycoris.loconautics.trainphysics.TrainLength;
import com.lycoris.loconautics.trainphysics.drive.SableTrainDriver;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.Collections;
import java.util.List;

public final class ConsistFacing {

    private static final double MIN_HINGE = 0.25;
    private static final double MIN_CHORD = 0.5;

    private ConsistFacing() {
    }

    // positive means the front faces the head of the list, negative the tail, zero means unknown
    public static double frontScore(ServerLevel level, List<SableTrain> cars, int index) {
        SableTrain car = cars.get(index);
        double towardHead = index > 0 ? TrainLength.hingeOffset(level, car, cars.get(index - 1)) : Double.NaN;
        double towardTail = index < cars.size() - 1
                ? TrainLength.hingeOffset(level, car, cars.get(index + 1)) : Double.NaN;
        double score = (Double.isNaN(towardHead) ? 0.0 : towardHead) - (Double.isNaN(towardTail) ? 0.0 : towardTail);
        return Math.abs(score) >= MIN_HINGE ? score : chordScore(level, cars, index);
    }

    // adjacent cars only, a chord across two cars lies on a bend, a chord across one hinge does not
    private static double chordScore(ServerLevel level, List<SableTrain> cars, int index) {
        boolean head = index > 0;
        if (!head && cars.size() < 2) {
            return 0.0;
        }
        SableTrain car = cars.get(index);
        SableTrain neighbour = cars.get(head ? index - 1 : index + 1);
        ServerSubLevel sub = SubLevelData.resolve(level, car.car().subLevelId());
        ServerSubLevel other = SubLevelData.resolve(level, neighbour.car().subLevelId());
        if (sub == null || other == null) {
            return 0.0;
        }
        Vector3d here = SableTrainSpawner.bogeyAnchorWorld(sub, car.car());
        Vector3d there = SableTrainSpawner.bogeyAnchorWorld(other, neighbour.car());
        Vec3 facing = SableTrainSpawner.currentFacing(sub, car.car());
        double along = facing.x * (there.x - here.x) + facing.z * (there.z - here.z);
        if (Math.abs(along) < MIN_CHORD) {
            return 0.0;
        }
        return head ? along : -along;
    }

    // puts the end the axle nose points at first
    @Nullable
    public static Vec3 noseFirst(ServerLevel level, List<SableTrain> cars) {
        for (int i = 0; i < cars.size(); i++) {
            Vec3 nose = SableTrainDriver.axleNose(cars.get(i));
            if (nose == null) {
                continue;
            }
            ServerSubLevel sub = cars.size() > 1 ? SubLevelData.resolve(level, cars.get(i).car().subLevelId()) : null;
            if (sub != null) {
                Vec3 facing = SableTrainSpawner.currentFacing(sub, cars.get(i).car());
                double side = facing.x * nose.x + facing.z * nose.z >= 0.0 ? 1.0 : -1.0;
                if (frontScore(level, cars, i) * side < 0.0) {
                    Collections.reverse(cars);
                }
            }
            return nose;
        }
        return null;
    }

    // read off the hinges, so a bend or a u turn cannot change the answer
    public static boolean[] reversed(ServerLevel level, List<SableTrain> cars, @Nullable Vec3 nose) {
        boolean[] out = new boolean[cars.size()];
        for (int i = 0; i < out.length; i++) {
            double score = frontScore(level, cars, i);
            if (score != 0.0) {
                out[i] = score < 0.0;
            } else if (cars.size() == 1) {
                out[i] = SableTrainSpawner.facesAgainst(level, cars.get(i), nose);
            } else {
                out[i] = i > 0 && out[i - 1];
                LoconauticsConstants.LOGGER.info(
                        "[sabletrain] relocate: car {} of {} has no hinge or neighbor to say which way it faces, copying car {}",
                        i + 1, out.length, i);
            }
        }
        return out;
    }
}
