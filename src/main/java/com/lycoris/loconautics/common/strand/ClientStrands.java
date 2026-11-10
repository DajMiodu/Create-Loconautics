package com.lycoris.loconautics.common.strand;

import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ClientStrands {

    private static final int SNAPSHOT_HISTORY_TICKS = 6;

    private record Snapshot(double interpolationTick, Vector3d position) {}

    public static final class Point {

        public final Vector3d position = new Vector3d();
        public final Vector3d previousPosition = new Vector3d();
        private final List<Snapshot> snapshots = new ArrayList<>();

        Point(Vector3d initial) {
            position.set(initial);
            previousPosition.set(initial);
        }

        public Vector3d renderPos(float partialTicks, Vector3d dst) {
            return dst.set(previousPosition).lerp(position, partialTicks);
        }
    }

    public static final class Strand {

        private final List<Point> points = new ArrayList<>();
        private boolean stopped;
        @Nullable private StrandEnd start;
        @Nullable private StrandEnd end;

        @Nullable
        public StrandEnd start() {
            return start;
        }

        @Nullable
        public StrandEnd end() {
            return end;
        }

        public List<Point> points() {
            return points;
        }

        public int size() {
            return points.size();
        }

        private void accept(double tick, List<Vector3d> incoming) {
            if (points.size() != incoming.size()) {
                points.clear();
                for (Vector3d p : incoming) {
                    points.add(new Point(p));
                }
            }
            for (int i = 0; i < incoming.size(); i++) {
                points.get(i).snapshots.add(new Snapshot(tick, new Vector3d(incoming.get(i))));
            }
        }

        void tickInterpolation(double gameTick) {
            for (Point point : points) {
                List<Snapshot> buffer = point.snapshots;
                point.previousPosition.set(point.position);
                while (!buffer.isEmpty() && buffer.get(0).interpolationTick() < gameTick - SNAPSHOT_HISTORY_TICKS) {
                    buffer.remove(0);
                }
                if (buffer.isEmpty()) {
                    continue;
                }
                int beforeIndex = -1;
                Snapshot before = null;
                Snapshot after = null;
                for (int i = 0; i < buffer.size(); i++) {
                    Snapshot snapshot = buffer.get(i);
                    if (snapshot.interpolationTick() == gameTick) {
                        point.position.set(snapshot.position());
                    } else if (snapshot.interpolationTick() < gameTick) {
                        beforeIndex = i;
                        before = snapshot;
                    } else {
                        after = snapshot;
                        break;
                    }
                }
                if (before != null && after != null) {
                    double factor = (gameTick - before.interpolationTick())
                            / (after.interpolationTick() - before.interpolationTick());
                    before.position().lerp(after.position(), factor, point.position);
                    continue;
                }
                if (before != null) {
                    point.position.set(before.position());
                    if (beforeIndex - 1 < 0 || stopped) {
                        continue;
                    }
                    Snapshot beforeBefore = buffer.get(beforeIndex - 1);
                    double lead = Mth.clamp(gameTick - before.interpolationTick(), 0.0, 1.0);
                    double fraction = lead / (before.interpolationTick() - beforeBefore.interpolationTick());
                    point.position.set(beforeBefore.position()).lerp(before.position(), 1.0 + fraction);
                    continue;
                }
                if (after != null) {
                    point.position.set(after.position());
                }
            }
        }
    }

    private static final List<ClientStrands> ALL = new ArrayList<>();

    private final Map<UUID, Strand> strands = new ConcurrentHashMap<>();

    public ClientStrands() {
        ALL.add(this);
    }

    public static void tickAll(double interpolationTick) {
        for (ClientStrands feature : ALL) {
            feature.tickInterpolation(interpolationTick);
        }
    }

    public static void clearAll() {
        for (ClientStrands feature : ALL) {
            feature.clear();
        }
    }

    public void accept(UUID id, int interpolationTick, boolean stopped, List<Vector3d> points,
                       @Nullable StrandEnd start, @Nullable StrandEnd end) {
        if (points.isEmpty()) {
            strands.remove(id);
            return;
        }
        Strand strand = strands.computeIfAbsent(id, key -> new Strand());
        strand.stopped = stopped;
        if (start != null) {
            strand.start = start;
        }
        if (end != null) {
            strand.end = end;
        }
        strand.accept(interpolationTick, points);
    }

    public void tickInterpolation(double interpolationTick) {
        for (Strand strand : strands.values()) {
            strand.tickInterpolation(interpolationTick);
        }
    }

    public Collection<Strand> all() {
        return strands.values();
    }

    public Collection<Map.Entry<UUID, Strand>> entries() {
        return strands.entrySet();
    }

    public Strand get(UUID id) {
        return strands.get(id);
    }

    public void clear() {
        strands.clear();
    }
}
