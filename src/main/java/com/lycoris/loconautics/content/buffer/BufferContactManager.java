package com.lycoris.loconautics.content.buffer;

import com.lycoris.loconautics.common.sable.SubLevelBlocks;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelObserver;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import com.lycoris.loconautics.trainphysics.sublevel.SubLevelContacts;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class BufferContactManager {

    private BufferContactManager() {
    }

    private static final long ENGAGED_MILLIS = 500L;

    private static final Map<SubLevelContacts.Pair, Long> ENGAGED = new ConcurrentHashMap<>();

    public static boolean engaged(UUID a, UUID b) {
        Long seen = ENGAGED.get(SubLevelContacts.Pair.of(a, b));
        return seen != null && System.currentTimeMillis() - seen < ENGAGED_MILLIS;
    }

    public static void watch(ServerSubLevelContainer container) {
        container.addObserver(new Watcher());
    }

    private record Head(BufferBlockEntity be, ServerSubLevel sub, Vector3d tip, Vector3d outward) {
    }

    private static final class Watcher implements SubLevelObserver {

        private int ticks;

        @Override
        public void tick(SubLevelContainer subLevels) {
            if (!(subLevels instanceof ServerSubLevelContainer container)) {
                return;
            }
            List<Head> heads = collect(container);
            for (Head head : heads) {
                head.be().syncCompression();
            }
            if ((ticks++ % BufferTuning.SCAN_INTERVAL) != 0) {
                return;
            }
            for (Head head : heads) {
                pair(head, heads);
            }
        }

        private static List<Head> collect(ServerSubLevelContainer container) {
            List<Head> heads = new ArrayList<>();
            for (ServerSubLevel sub : container.getAllSubLevels()) {
                if (sub.isRemoved()) {
                    continue;
                }
                for (BufferBlockEntity be : SubLevelBlocks.findAll(sub, BufferBlockEntity.class)) {
                    heads.add(new Head(be, sub, BufferBlockEntity.worldTip(sub, be), BufferBlockEntity.worldOutward(sub, be)));
                }
            }
            return heads;
        }

        private static void pair(Head own, List<Head> heads) {
            Head nearest = null;
            double nearestGap = Double.MAX_VALUE;
            for (Head other : heads) {
                if (other.sub() == own.sub() || own.outward().dot(other.outward()) > BufferTuning.OPPOSED_DOT) {
                    continue;
                }
                Vector3d toOther = other.tip().sub(own.tip(), new Vector3d());
                double along = toOther.dot(own.outward());
                double lateral = toOther.fma(-along, own.outward()).length();
                if (lateral > BufferTuning.MAX_LATERAL || along > BufferTuning.WATCH_REACH
                        || along < -BufferTuning.MAX_PENETRATION || along >= nearestGap) {
                    continue;
                }
                nearest = other;
                nearestGap = along;
            }
            if (nearest == null) {
                own.be().setContact(null, null);
                return;
            }
            own.be().setContact(nearest.sub().getUniqueId(), nearest.be().getBlockPos());
            ENGAGED.put(SubLevelContacts.Pair.of(own.sub().getUniqueId(), nearest.sub().getUniqueId()),
                    System.currentTimeMillis());
        }
    }
}
