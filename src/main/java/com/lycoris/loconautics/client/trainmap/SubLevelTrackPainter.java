package com.lycoris.loconautics.client.trainmap;

import com.lycoris.loconautics.common.sable.TrackHostFramesClient;
import com.lycoris.loconautics.trainphysics.rail.RailSeams;
import com.simibubi.create.CreateClient;
import com.simibubi.create.compat.trainmap.TrainMapRenderer;
import com.simibubi.create.content.trains.graph.TrackEdge;
import com.simibubi.create.content.trains.graph.TrackGraph;
import com.simibubi.create.content.trains.graph.TrackNode;
import com.simibubi.create.content.trains.graph.TrackNodeLocation;
import com.simibubi.create.infrastructure.config.CClient;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class SubLevelTrackPainter {

    private static final float HUE_SHIFT = 130.0f / 360.0f;
    private static final float GREY_HUE = 0.48f;
    private static final float GREY_SATURATION = 0.40f;
    private static final float GREY_LIMIT = 0.12f;
    private static final int OUTLINE = 0xFF000000;
    private static final long REDRAW_GAP_NANOS = 150_000_000L;
    private static final double MAX_SEAM_LINK = 3.0;
    private static final int MAX_CURVE_STEPS = 96;
    private static final int TRACKED_CELL_SHIFT = 6;
    private static final int MAX_TRACKED = 256;

    private static final List<TrackNodeLocation> TRACKED = new ArrayList<>();
    private static int lastSignature;
    private static long lastRedrawNanos;

    private record Pixel(int x, int z, double y) {
    }

    private SubLevelTrackPainter() {
    }

    public static boolean needsRedraw() {
        if (TRACKED.isEmpty() && TrackHostFramesClient.frames().isEmpty()) {
            return false;
        }
        if (System.nanoTime() - lastRedrawNanos < REDRAW_GAP_NANOS) {
            return false;
        }
        return signature() != lastSignature;
    }

    private static int signature() {
        int hash = TrackHostFramesClient.version();
        for (TrackNodeLocation location : TRACKED) {
            Pose3dc pose = RailSeams.clientPoseOf(location);
            hash = 31 * hash + (pose == null ? 0 : poseHash(pose));
        }
        return hash;
    }

    private static int poseHash(Pose3dc pose) {
        Vector3dc position = pose.position();
        Quaterniondc q = pose.orientation();
        int hash = Mth.floor(position.x() * 4.0);
        hash = 31 * hash + Mth.floor(position.y() * 4.0);
        hash = 31 * hash + Mth.floor(position.z() * 4.0);
        hash = 31 * hash + Mth.floor(q.x() * 64.0);
        hash = 31 * hash + Mth.floor(q.y() * 64.0);
        hash = 31 * hash + Mth.floor(q.z() * 64.0);
        return 31 * hash + Mth.floor(q.w() * 64.0);
    }

    public static void paint(TrainMapRenderer map, ResourceKey<Level> dimension) {
        TRACKED.clear();
        lastRedrawNanos = System.nanoTime();
        List<Pixel> pixels = new ArrayList<>();
        LongOpenHashSet cells = new LongOpenHashSet();
        for (TrackGraph graph : CreateClient.RAILWAYS.trackNetworks.values()) {
            for (TrackNodeLocation location : graph.getNodes()) {
                if (location.dimension != dimension || !RailSeams.inPlotGrid(location)) {
                    continue;
                }
                noteCell(cells, location);
                if (RailSeams.clientPoseOf(location) == null) {
                    continue;
                }
                TrackNode node = graph.locateNode(location);
                Map<TrackNode, TrackEdge> connections = node == null ? null : graph.getConnectionsFrom(node);
                if (connections == null) {
                    continue;
                }
                for (Map.Entry<TrackNode, TrackEdge> entry : connections.entrySet()) {
                    if (entry.getKey().hashCode() > node.hashCode()) {
                        continue;
                    }
                    collect(pixels, node, entry.getKey(), entry.getValue());
                }
            }
        }
        lastSignature = signature();
        draw(map, pixels);
    }

    private static void noteCell(LongOpenHashSet cells, TrackNodeLocation location) {
        int cellX = Mth.floor(location.getX() / 2.0) >> TRACKED_CELL_SHIFT;
        int cellZ = Mth.floor(location.getZ() / 2.0) >> TRACKED_CELL_SHIFT;
        if (TRACKED.size() < MAX_TRACKED && cells.add(key(cellX, cellZ))) {
            TRACKED.add(location);
        }
    }

    private static void collect(List<Pixel> out, TrackNode node, TrackNode other, TrackEdge edge) {
        TrackNodeLocation a = node.getLocation();
        TrackNodeLocation b = other.getLocation();
        if (edge.isInterDimensional()) {
            if (RailSeams.isSeam(a, b)) {
                seamLink(out, a, b);
            }
            return;
        }
        Pose3dc pose = RailSeams.clientPoseOf(edge.node1.getLocation());
        if (pose == null) {
            return;
        }
        int steps = edge.isTurn() ? Mth.clamp(Mth.ceil(edge.getLength() * 1.5), 2, MAX_CURVE_STEPS) : 1;
        Vec3 previous = worldOf(pose, edge.getPosition(null, 0.0));
        for (int i = 1; i <= steps; i++) {
            Vec3 next = worldOf(pose, edge.getPosition(null, i / (double) steps));
            segment(out, previous, next);
            previous = next;
        }
    }

    private static void seamLink(List<Pixel> out, TrackNodeLocation a, TrackNodeLocation b) {
        Pose3dc poseA = RailSeams.clientPoseOf(a);
        Pose3dc poseB = RailSeams.clientPoseOf(b);
        Vec3 pa = worldOf(poseA, a.getLocation());
        Vec3 pb = worldOf(poseB, b.getLocation());
        if (pa.distanceTo(pb) <= MAX_SEAM_LINK) {
            segment(out, pa, pb);
        }
    }

    private static Vec3 worldOf(Pose3dc pose, Vec3 local) {
        if (pose == null) {
            return local;
        }
        Vector3d w = pose.transformPosition(new Vector3d(local.x, local.y, local.z), new Vector3d());
        return new Vec3(w.x, w.y, w.z);
    }

    private static void segment(List<Pixel> out, Vec3 from, Vec3 to) {
        int x = Mth.floor(from.x);
        int z = Mth.floor(from.z);
        int x2 = Mth.floor(to.x);
        int z2 = Mth.floor(to.z);
        int dx = Math.abs(x2 - x);
        int dz = Math.abs(z2 - z);
        int sx = x < x2 ? 1 : -1;
        int sz = z < z2 ? 1 : -1;
        int total = Math.max(dx, dz);
        int error = dx - dz;
        for (int step = 0; step <= total; step++) {
            double t = total == 0 ? 0.0 : step / (double) total;
            out.add(new Pixel(x, z, Mth.lerp(t, from.y, to.y)));
            int doubled = 2 * error;
            if (doubled > -dz) {
                error -= dz;
                x += sx;
            }
            if (doubled < dx) {
                error += dx;
                z += sz;
            }
        }
    }

    private static void draw(TrainMapRenderer map, List<Pixel> pixels) {
        if (pixels.isEmpty()) {
            return;
        }
        int main = shifted(map.trackingTheme);
        for (Pixel pixel : pixels) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    outline(map, pixel.x() + dx, pixel.z() + dz);
                }
            }
        }
        for (Pixel pixel : pixels) {
            int alpha = alphaOf(pixel.y());
            if (map.alphaAt(pixel.x(), pixel.z()) <= alpha) {
                map.setPixel(pixel.x(), pixel.z(), FastColor.ABGR32.color(alpha, main));
            }
        }
    }

    private static void outline(TrainMapRenderer map, int x, int z) {
        if (map.isEmpty(x, z)) {
            map.setPixel(x, z, OUTLINE);
        }
    }

    private static long key(int x, int z) {
        return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
    }

    private static int alphaOf(double y) {
        int minY = Minecraft.getInstance().level.getMinBuildHeight();
        return Mth.clamp(32 + Mth.floor((y - minY) / 4.0), 0, 255);
    }

    private static int shifted(CClient.TrainMapTheme theme) {
        int main = 0xFF_7C57D4;
        if (theme == CClient.TrainMapTheme.GREY) {
            main = 0xFF_A8B5B5;
        } else if (theme == CClient.TrainMapTheme.WHITE) {
            main = 0xFF_E8F9F9;
        }
        float[] hsv = toHsv(main);
        boolean grey = hsv[1] < GREY_LIMIT;
        return fromHsv(grey ? GREY_HUE : (hsv[0] + HUE_SHIFT) % 1.0f, grey ? GREY_SATURATION : hsv[1], hsv[2]);
    }

    // colors here are abgr, red in the low byte
    private static float[] toHsv(int abgr) {
        float r = (abgr & 0xFF) / 255.0f;
        float g = ((abgr >> 8) & 0xFF) / 255.0f;
        float b = ((abgr >> 16) & 0xFF) / 255.0f;
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float delta = max - min;
        float hue = 0.0f;
        if (delta > 0.0f) {
            if (max == r) {
                hue = ((g - b) / delta) % 6.0f;
            } else if (max == g) {
                hue = (b - r) / delta + 2.0f;
            } else {
                hue = (r - g) / delta + 4.0f;
            }
            hue = (hue / 6.0f + 1.0f) % 1.0f;
        }
        return new float[] {hue, max <= 0.0f ? 0.0f : delta / max, max};
    }

    private static int fromHsv(float hue, float saturation, float value) {
        int rgb = Mth.hsvToRgb(hue, saturation, value);
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        return 0xFF000000 | (b << 16) | (g << 8) | r;
    }
}
