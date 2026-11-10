package com.lycoris.loconautics.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3d;

import java.util.List;

public final class RopeRendering {

    private RopeRendering() {
    }

    public static double orientSegment(Quaternionf orient, Vec3 p0, Vec3 p1) {
        Vector3d dir = new Vector3d(p1.x - p0.x, p1.y - p0.y, p1.z - p0.z);
        double length = dir.length();
        if (length < 1.0e-5) {
            return length;
        }
        dir.div(length);
        orient.rotationTo(0f, 1f, 0f, (float) dir.x, (float) dir.y, (float) dir.z);
        return length;
    }

    private static void orientAlong(Quaternionf out, Vector3d dir) {
        if (dir.y < -0.15) {
            out.rotationTo(0f, -1f, 0f, (float) dir.x, (float) dir.y, (float) dir.z);
            out.rotateZ((float) Math.PI);
        } else {
            out.rotationTo(0f, 1f, 0f, (float) dir.x, (float) dir.y, (float) dir.z);
        }
    }

    public static double orientHangingSegment(Quaternionf orient, Vec3 p0, Vec3 p1) {
        Vector3d dir = new Vector3d(p1.x - p0.x, p1.y - p0.y, p1.z - p0.z);
        double length = dir.length();
        if (length < 1.0e-5) {
            return length;
        }
        dir.div(length);
        orientAlong(orient, dir);
        return length;
    }

    public static void renderHitbox(PoseStack ps, MultiBufferSource buffer,
                                    List<Vec3> points, BlockPos ownerPos, float radius) {
        VertexConsumer lines = buffer.getBuffer(RenderType.lines());
        var pose = ps.last();
        Quaternionf orient = new Quaternionf();
        Vector3d[] corners = {
                new Vector3d(-radius, 0, -radius), new Vector3d(-radius, 0, radius),
                new Vector3d(radius, 0, radius), new Vector3d(radius, 0, -radius)};
        for (int i = 0; i < points.size() - 1; i++) {
            Vec3 p0 = points.get(i);
            Vec3 p1 = points.get(i + 1);
            double length = orientSegment(orient, p0, p1);
            if (length < 1.0e-5) {
                continue;
            }
            for (Vector3d c : corners) {
                Vector3d a = orient.transform(new Vector3d(c))
                        .add(p0.x - ownerPos.getX(), p0.y - ownerPos.getY(), p0.z - ownerPos.getZ());
                Vector3d b = orient.transform(new Vector3d(c))
                        .add(p1.x - ownerPos.getX(), p1.y - ownerPos.getY(), p1.z - ownerPos.getZ());
                Vector3d n = new Vector3d(b).sub(a).normalize();
                lines.addVertex(pose.pose(), (float) a.x, (float) a.y, (float) a.z)
                        .setColor(0f, 0f, 0f, 0.4f).setNormal(pose, (float) n.x, (float) n.y, (float) n.z);
                lines.addVertex(pose.pose(), (float) b.x, (float) b.y, (float) b.z)
                        .setColor(0f, 0f, 0f, 0.4f).setNormal(pose, (float) n.x, (float) n.y, (float) n.z);
            }
        }
    }
}