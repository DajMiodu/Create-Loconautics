package com.lycoris.loconautics.common.sable;

import dev.ryanhcode.sable.companion.math.Pose3d;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.UUID;

public record FramePose(UUID host, ResourceLocation dimension, int minChunkX, int minChunkZ, int maxChunkX,
                             int maxChunkZ, Pose3d pose) {

    public static FramePose of(TrackHostFrames.Frame frame) {
        return new FramePose(frame.host(), frame.dimension().location(), frame.minChunkX(), frame.minChunkZ(),
                frame.maxChunkX(), frame.maxChunkZ(), new Pose3d(frame.pose()));
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeUUID(host);
        buf.writeResourceLocation(dimension);
        buf.writeInt(minChunkX);
        buf.writeInt(minChunkZ);
        buf.writeInt(maxChunkX);
        buf.writeInt(maxChunkZ);
        writeVector(buf, pose.position());
        Quaterniond q = pose.orientation();
        buf.writeDouble(q.x);
        buf.writeDouble(q.y);
        buf.writeDouble(q.z);
        buf.writeDouble(q.w);
        writeVector(buf, pose.rotationPoint());
        writeVector(buf, pose.scale());
    }

    public static FramePose read(FriendlyByteBuf buf) {
        UUID host = buf.readUUID();
        ResourceLocation dimension = buf.readResourceLocation();
        int minX = buf.readInt();
        int minZ = buf.readInt();
        int maxX = buf.readInt();
        int maxZ = buf.readInt();
        Vector3d position = readVector(buf);
        Quaterniond orientation = new Quaterniond(buf.readDouble(), buf.readDouble(), buf.readDouble(),
                buf.readDouble());
        Vector3d rotationPoint = readVector(buf);
        Vector3d scale = readVector(buf);
        return new FramePose(host, dimension, minX, minZ, maxX, maxZ,
                new Pose3d(position, orientation, rotationPoint, scale));
    }

    private static void writeVector(FriendlyByteBuf buf, org.joml.Vector3dc v) {
        buf.writeDouble(v.x());
        buf.writeDouble(v.y());
        buf.writeDouble(v.z());
    }

    private static Vector3d readVector(FriendlyByteBuf buf) {
        return new Vector3d(buf.readDouble(), buf.readDouble(), buf.readDouble());
    }
}
