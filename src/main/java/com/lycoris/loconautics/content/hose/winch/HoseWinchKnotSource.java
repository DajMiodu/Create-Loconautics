package com.lycoris.loconautics.content.hose.winch;

import com.lycoris.loconautics.common.strand.StrandKnotPicker;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.BiConsumer;

public final class HoseWinchKnotSource implements StrandKnotPicker.Source {

    private static final Set<HoseWinchBlockEntity> TRACKED = Collections.newSetFromMap(new WeakHashMap<>());

    public static void track(HoseWinchBlockEntity winch) {
        TRACKED.add(winch);
    }

    @Override
    public void collect(BiConsumer<UUID, List<Vector3d>> out) {
        for (HoseWinchBlockEntity winch : new ArrayList<>(TRACKED)) {
            UUID strand = winch.clientStrandId();
            List<Vec3> local = winch.isRemoved() || strand == null ? null : winch.clientRopePoints(0.0f);
            if (local == null) {
                continue;
            }
            ClientSubLevel owner = Sable.HELPER.getContainingClient(winch);
            Pose3dc pose = owner == null ? null : owner.renderPose();
            List<Vector3d> world = new ArrayList<>(local.size());
            for (Vec3 p : local) {
                Vector3d point = new Vector3d(p.x, p.y, p.z);
                world.add(pose == null ? point : pose.transformPosition(point, new Vector3d()));
            }
            out.accept(strand, world);
        }
    }
}
