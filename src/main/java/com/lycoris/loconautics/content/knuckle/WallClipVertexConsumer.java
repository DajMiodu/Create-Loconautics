package com.lycoris.loconautics.content.knuckle;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class WallClipVertexConsumer implements VertexConsumer {

    private static final float KEEP_EPSILON = 1.0e-4f;

    private static final class Vert {
        float x, y, z, u, v, nx, ny, nz, side;
        int r, g, b, a, overlayU, overlayV, lightU, lightV;

        void lerp(Vert from, Vert to, float t) {
            x = Mth.lerp(t, from.x, to.x);
            y = Mth.lerp(t, from.y, to.y);
            z = Mth.lerp(t, from.z, to.z);
            u = Mth.lerp(t, from.u, to.u);
            v = Mth.lerp(t, from.v, to.v);
            nx = Mth.lerp(t, from.nx, to.nx);
            ny = Mth.lerp(t, from.ny, to.ny);
            nz = Mth.lerp(t, from.nz, to.nz);
            r = Math.round(Mth.lerp(t, from.r, to.r));
            g = Math.round(Mth.lerp(t, from.g, to.g));
            b = Math.round(Mth.lerp(t, from.b, to.b));
            a = Math.round(Mth.lerp(t, from.a, to.a));
            Vert lit = t < 0.5f ? from : to;
            overlayU = lit.overlayU;
            overlayV = lit.overlayV;
            lightU = lit.lightU;
            lightV = lit.lightV;
        }
    }

    private final Vert[] quad = {new Vert(), new Vert(), new Vert(), new Vert()};
    private final Vert[] cuts = {new Vert(), new Vert(), new Vert(), new Vert()};
    private final Vert[] polygon = new Vert[8];
    private final Vector3f planeNormal = new Vector3f();

    private VertexConsumer target;
    private float planeOffset;
    private int filled;

    public void begin(VertexConsumer target, Matrix4f base, float pointX, float pointY, float pointZ,
                      float outwardX, float outwardY, float outwardZ) {
        this.target = target;
        this.filled = 0;
        base.transformDirection(outwardX, outwardY, outwardZ, planeNormal).normalize();
        Vector3f point = base.transformPosition(pointX, pointY, pointZ, new Vector3f());
        planeOffset = -planeNormal.dot(point);
    }

    @Override
    public VertexConsumer addVertex(float x, float y, float z) {
        Vert vert = quad[filled];
        vert.x = x;
        vert.y = y;
        vert.z = z;
        return this;
    }

    @Override
    public VertexConsumer setColor(int r, int g, int b, int a) {
        Vert vert = quad[filled];
        vert.r = r;
        vert.g = g;
        vert.b = b;
        vert.a = a;
        return this;
    }

    @Override
    public VertexConsumer setUv(float u, float v) {
        quad[filled].u = u;
        quad[filled].v = v;
        return this;
    }

    @Override
    public VertexConsumer setUv1(int u, int v) {
        quad[filled].overlayU = u;
        quad[filled].overlayV = v;
        return this;
    }

    @Override
    public VertexConsumer setUv2(int u, int v) {
        quad[filled].lightU = u;
        quad[filled].lightV = v;
        return this;
    }

    @Override
    public VertexConsumer setNormal(float x, float y, float z) {
        Vert vert = quad[filled];
        vert.nx = x;
        vert.ny = y;
        vert.nz = z;
        if (++filled == quad.length) {
            filled = 0;
            clipQuad();
        }
        return this;
    }

    private void clipQuad() {
        int kept = 0;
        for (Vert vert : quad) {
            vert.side = planeNormal.x * vert.x + planeNormal.y * vert.y + planeNormal.z * vert.z + planeOffset;
            if (vert.side <= KEEP_EPSILON) {
                kept++;
            }
        }
        if (kept == quad.length) {
            emit(quad[0], quad[1], quad[2], quad[3]);
            return;
        }
        if (kept == 0) {
            return;
        }
        int count = 0;
        int cut = 0;
        for (int i = 0; i < quad.length; i++) {
            Vert from = quad[i];
            Vert to = quad[(i + 1) % quad.length];
            boolean fromKept = from.side <= KEEP_EPSILON;
            if (fromKept) {
                polygon[count++] = from;
            }
            if (fromKept != (to.side <= KEEP_EPSILON)) {
                Vert crossing = cuts[cut++];
                crossing.lerp(from, to, Mth.clamp(from.side / (from.side - to.side), 0.0f, 1.0f));
                polygon[count++] = crossing;
            }
        }
        for (int i = 1; i + 1 < count; i++) {
            emit(polygon[0], polygon[i], polygon[i + 1], polygon[i + 1]);
        }
    }

    private void emit(Vert first, Vert second, Vert third, Vert fourth) {
        emitCorner(first);
        emitCorner(second);
        emitCorner(third);
        emitCorner(fourth);
    }

    private void emitCorner(Vert vert) {
        target.addVertex(vert.x, vert.y, vert.z)
                .setColor(vert.r, vert.g, vert.b, vert.a)
                .setUv(vert.u, vert.v)
                .setUv1(vert.overlayU, vert.overlayV)
                .setUv2(vert.lightU, vert.lightV)
                .setNormal(vert.nx, vert.ny, vert.nz);
    }
}
