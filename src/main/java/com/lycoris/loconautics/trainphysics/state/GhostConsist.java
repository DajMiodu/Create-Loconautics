package com.lycoris.loconautics.trainphysics.state;

import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.trainphysics.drive.SableTrainDriver;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

final class GhostConsist {

    private GhostConsist() {
    }

    static List<SableTrain> ghosts(SableTrain train, boolean railedOnly) {
        List<SableTrain> out = new ArrayList<>();
        out.add(train);
        for (UUID id : train.consistCache()) {
            SableTrain other = SableTrainRegistry.bySubLevel(id);
            if (other != null && other != train && other.isGhost() && other.car() != null
                    && other.car().subLevelId() != null && other.level() == train.level()
                    && !(railedOnly && (other.isDerailed() || other.isPhysics()))) {
                out.add(other);
            }
        }
        out.sort(Comparator.comparing(member -> member.car().subLevelId()));
        return out;
    }

    static SableTrain leader(List<SableTrain> group) {
        for (SableTrain member : group) {
            if (member.isScheduleDriven()) {
                return member;
            }
        }
        return group.get(0);
    }

    static double senseAgainst(SableTrain train, SableTrain leader) {
        Vec3 mine = SableTrainDriver.scheduleDriveTangent(train);
        Vec3 theirs = SableTrainDriver.scheduleDriveTangent(leader);
        return mine == null || theirs == null || mine.dot(theirs) >= 0.0 ? 1.0 : -1.0;
    }
}
