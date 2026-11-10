package com.lycoris.loconautics.common.sable;

import com.mojang.serialization.DataResult;
import com.simibubi.create.Create;
import com.simibubi.create.content.trains.graph.TrackGraph;
import com.simibubi.create.content.trains.graph.TrackNodeLocation;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public final class TrackHostFrames {

    private static final Map<UUID, Frame> FRAMES = new ConcurrentHashMap<>();
    private static final List<Consumer<UUID>> FORGET_LISTENERS = new CopyOnWriteArrayList<>();

    private TrackHostFrames() {
    }

    public static final class Frame {

        private final UUID host;
        private final ResourceKey<Level> dimension;
        private int minChunkX;
        private int minChunkZ;
        private int maxChunkX;
        private int maxChunkZ;
        private final Pose3d basePose = new Pose3d();
        private double baseAngle;
        private double angle;
        private Vec3 axis = Vec3.ZERO;
        private Vec3 normal = new Vec3(0.0, 1.0, 0.0);
        private Pose3d cached;
        private double cachedAngle = Double.NaN;
        private volatile boolean loaded;

        Frame(UUID host, ResourceKey<Level> dimension) {
            this.host = host;
            this.dimension = dimension;
        }

        public UUID host() {
            return host;
        }

        public ResourceKey<Level> dimension() {
            return dimension;
        }

        public Vec3 axis() {
            return axis;
        }

        public Vec3 normal() {
            return normal;
        }

        public int minChunkX() {
            return minChunkX;
        }

        public int minChunkZ() {
            return minChunkZ;
        }

        public int maxChunkX() {
            return maxChunkX;
        }

        public int maxChunkZ() {
            return maxChunkZ;
        }

        public boolean loaded() {
            return loaded;
        }

        public synchronized double angle() {
            return angle;
        }

        public synchronized void setAngle(double degrees) {
            angle = degrees;
        }

        boolean contains(ResourceKey<Level> dim, int chunkX, int chunkZ) {
            return dimension.equals(dim) && chunkX >= minChunkX && chunkX <= maxChunkX
                    && chunkZ >= minChunkZ && chunkZ <= maxChunkZ;
        }

        public synchronized Pose3dc pose() {
            if (cached != null && cachedAngle == angle) {
                return cached;
            }
            Pose3d out = new Pose3d(basePose);
            double delta = Math.toRadians(angle - baseAngle);
            if (delta != 0.0) {
                Quaterniond turn = new Quaterniond().rotationAxis(delta, normal.x, normal.y, normal.z);
                Vector3d offset = new Vector3d(out.position()).sub(axis.x, axis.y, axis.z);
                turn.transform(offset);
                out.position().set(axis.x + offset.x, axis.y + offset.y, axis.z + offset.z);
                turn.mul(out.orientation(), out.orientation());
                out.orientation().normalize();
            }
            cached = out;
            cachedAngle = angle;
            return out;
        }

        synchronized void rebase(Pose3dc pose, double degrees, Vec3 axisWorld, Vec3 normalWorld) {
            basePose.set(pose);
            baseAngle = degrees;
            angle = degrees;
            axis = axisWorld;
            normal = normalWorld;
            cached = null;
            cachedAngle = Double.NaN;
        }

        synchronized CompoundTag write() {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("Host", host);
            tag.putString("Dimension", dimension.location().toString());
            tag.putInt("MinX", minChunkX);
            tag.putInt("MinZ", minChunkZ);
            tag.putInt("MaxX", maxChunkX);
            tag.putInt("MaxZ", maxChunkZ);
            tag.putDouble("BaseAngle", baseAngle);
            tag.putDouble("Angle", angle);
            tag.putDouble("AxisX", axis.x);
            tag.putDouble("AxisY", axis.y);
            tag.putDouble("AxisZ", axis.z);
            tag.putDouble("NormalX", normal.x);
            tag.putDouble("NormalY", normal.y);
            tag.putDouble("NormalZ", normal.z);
            DataResult<Tag> pose = Pose3d.CODEC.encodeStart(NbtOps.INSTANCE, basePose);
            pose.result().ifPresent(value -> tag.put("Pose", value));
            return tag;
        }
    }

    @Nullable
    public static Frame read(CompoundTag tag) {
        if (!tag.hasUUID("Host") || !tag.contains("Pose")) {
            return null;
        }
        ResourceLocation dimension = ResourceLocation.tryParse(tag.getString("Dimension"));
        Pose3d pose = dimension == null ? null
                : Pose3d.CODEC.parse(NbtOps.INSTANCE, tag.get("Pose")).result().orElse(null);
        if (pose == null) {
            return null;
        }
        Frame frame = new Frame(tag.getUUID("Host"), ResourceKey.create(Registries.DIMENSION, dimension));
        frame.minChunkX = tag.getInt("MinX");
        frame.minChunkZ = tag.getInt("MinZ");
        frame.maxChunkX = tag.getInt("MaxX");
        frame.maxChunkZ = tag.getInt("MaxZ");
        frame.rebase(pose, tag.getDouble("BaseAngle"),
                new Vec3(tag.getDouble("AxisX"), tag.getDouble("AxisY"), tag.getDouble("AxisZ")),
                new Vec3(tag.getDouble("NormalX"), tag.getDouble("NormalY"), tag.getDouble("NormalZ")));
        frame.setAngle(tag.getDouble("Angle"));
        return frame;
    }

    // a frame read off disk never replaces one the running session already holds
    public static void restore(Frame frame) {
        FRAMES.putIfAbsent(frame.host(), frame);
    }

    public static void record(UUID host, ResourceKey<Level> dimension, ServerSubLevel sub, double angle,
                              Vec3 axis, Vec3 normal) {
        Frame frame = FRAMES.computeIfAbsent(host, id -> new Frame(id, dimension));
        fitBounds(frame, sub);
        frame.rebase(sub.logicalPose(), angle, axis, normal);
        frame.loaded = true;
    }

    public static void onUnloaded(ServerSubLevel sub, ResourceKey<Level> dimension) {
        UUID id = sub.getUniqueId();
        Frame known = FRAMES.get(id);
        if (known != null) {
            known.loaded = false;
            if (known.axis.lengthSqr() == 0.0) {
                known.rebase(sub.logicalPose(), 0.0, Vec3.ZERO, known.normal);
            }
            return;
        }
        LevelPlot plot = sub.getPlot();
        if (plot == null || !hostsTrack(dimension, plot.getChunkMin(), plot.getChunkMax())) {
            return;
        }
        Frame frame = new Frame(id, dimension);
        fitBounds(frame, sub);
        frame.rebase(sub.logicalPose(), 0.0, Vec3.ZERO, new Vec3(0.0, 1.0, 0.0));
        FRAMES.put(id, frame);
    }

    public static void onLoaded(UUID host) {
        Frame frame = FRAMES.get(host);
        if (frame != null) {
            frame.loaded = true;
        }
    }

    private static void fitBounds(Frame frame, ServerSubLevel sub) {
        LevelPlot plot = sub.getPlot();
        ChunkPos min = plot.getChunkMin();
        ChunkPos max = plot.getChunkMax();
        frame.minChunkX = min.x;
        frame.minChunkZ = min.z;
        frame.maxChunkX = max.x;
        frame.maxChunkZ = max.z;
    }

    private static boolean hostsTrack(ResourceKey<Level> dimension, ChunkPos min, ChunkPos max) {
        for (TrackGraph graph : networks()) {
            for (TrackNodeLocation location : graph.getNodes()) {
                if (location.dimension != dimension) {
                    continue;
                }
                int chunkX = Mth.floor(location.getX() / 2.0) >> 4;
                int chunkZ = Mth.floor(location.getZ() / 2.0) >> 4;
                if (chunkX >= min.x && chunkX <= max.x && chunkZ >= min.z && chunkZ <= max.z) {
                    return true;
                }
            }
        }
        return false;
    }

    private static List<TrackGraph> networks() {
        return Create.RAILWAYS == null ? List.of() : new ArrayList<>(Create.RAILWAYS.trackNetworks.values());
    }

    public static List<UUID> orphans() {
        List<UUID> gone = new ArrayList<>();
        List<TrackGraph> graphs = networks();
        if (graphs.isEmpty()) {
            return gone;
        }
        for (Frame frame : FRAMES.values()) {
            if (!frame.loaded && !hostsTrack(frame.dimension, new ChunkPos(frame.minChunkX, frame.minChunkZ),
                    new ChunkPos(frame.maxChunkX, frame.maxChunkZ))) {
                gone.add(frame.host());
            }
        }
        return gone;
    }

    @Nullable
    public static Frame frameOf(@Nullable UUID host) {
        return host == null ? null : FRAMES.get(host);
    }

    public static void setAngle(@Nullable UUID host, double degrees) {
        Frame frame = frameOf(host);
        if (frame != null) {
            frame.setAngle(degrees);
        }
    }

    public static void onForget(Consumer<UUID> listener) {
        FORGET_LISTENERS.add(listener);
    }

    public static void forget(@Nullable UUID host) {
        if (host != null) {
            FRAMES.remove(host);
            FORGET_LISTENERS.forEach(listener -> listener.accept(host));
        }
    }

    public static void clear() {
        FRAMES.clear();
    }

    @Nullable
    public static Frame frameAt(@Nullable TrackNodeLocation location) {
        if (location == null || FRAMES.isEmpty() || location.dimension == null) {
            return null;
        }
        int chunkX = Mth.floor(location.getX() / 2.0) >> 4;
        int chunkZ = Mth.floor(location.getZ() / 2.0) >> 4;
        for (Frame frame : FRAMES.values()) {
            if (frame.contains(location.dimension, chunkX, chunkZ)) {
                return frame;
            }
        }
        return null;
    }

    @Nullable
    public static UUID hostId(@Nullable TrackNodeLocation location) {
        Frame frame = frameAt(location);
        return frame == null ? null : frame.host();
    }

    @Nullable
    public static Pose3dc poseAt(@Nullable TrackNodeLocation location) {
        Frame frame = frameAt(location);
        return frame == null ? null : frame.pose();
    }

    public static Collection<Frame> all() {
        return FRAMES.values();
    }

    public static List<Frame> unloaded() {
        List<Frame> out = new ArrayList<>();
        for (Frame frame : FRAMES.values()) {
            if (!frame.loaded) {
                out.add(frame);
            }
        }
        return out;
    }
}
