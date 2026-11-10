package com.lycoris.loconautics.common.sable;

import com.simibubi.create.content.trains.graph.TrackNodeLocation;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class TrackHostFramesClient {

    private static volatile List<FramePose> frames = List.of();
    private static volatile int version;

    private TrackHostFramesClient() {
    }

    public static void replace(List<FramePose> next) {
        frames = List.copyOf(next);
        version++;
    }

    public static void clear() {
        replace(List.of());
    }

    public static int version() {
        return version;
    }

    public static List<FramePose> frames() {
        return frames;
    }

    @Nullable
    public static Pose3dc poseAt(@Nullable TrackNodeLocation location) {
        List<FramePose> current = frames;
        if (location == null || location.dimension == null || current.isEmpty()) {
            return null;
        }
        int chunkX = Mth.floor(location.getX() / 2.0) >> 4;
        int chunkZ = Mth.floor(location.getZ() / 2.0) >> 4;
        for (FramePose host : current) {
            if (host.dimension().equals(location.dimension.location()) && chunkX >= host.minChunkX()
                    && chunkX <= host.maxChunkX() && chunkZ >= host.minChunkZ() && chunkZ <= host.maxChunkZ()) {
                return host.pose();
            }
        }
        return null;
    }
}
