package com.lycoris.loconautics.foundation.debug;

import com.lycoris.loconautics.network.packets.debug.DebugLinePacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

public final class DebugLines {

    public static final float DEFAULT_WIDTH = 2.0f / 16.0f;
    public static final int DEFAULT_TTL = 20;
    public static final double COLUMN_HEIGHT = 3.0;

    private static final double SEND_RANGE = 128.0;

    private DebugLines() {
    }

    public static int rgb(float r, float g, float b) {
        return ((int) (r * 255) << 16) | ((int) (g * 255) << 8) | (int) (b * 255);
    }

    public static void line(ServerLevel level, String slot, Vec3 start, Vec3 end, int color) {
        line(level, slot, start, end, color, DEFAULT_WIDTH, DEFAULT_TTL);
    }

    public static void line(ServerLevel level, String slot, Vec3 start, Vec3 end, int color,
                            float width, int ttl) {
        if (level == null || slot == null || start == null || end == null) {
            return;
        }
        PacketDistributor.sendToPlayersNear(level, null, start.x, start.y, start.z, SEND_RANGE,
                new DebugLinePacket(slot, start, end, color, width, ttl));
    }

    public static void column(ServerLevel level, String slot, Vec3 at, int color) {
        column(level, slot, at, color, DEFAULT_TTL);
    }

    public static void column(ServerLevel level, String slot, Vec3 at, int color, int ttl) {
        if (at == null) {
            return;
        }
        line(level, slot, at, at.add(0.0, COLUMN_HEIGHT, 0.0), color, DEFAULT_WIDTH, ttl);
    }

    public static void arrow(ServerLevel level, String slot, Vec3 from, Vec3 to, int color, float width, int ttl) {
        if (level == null || slot == null || from == null || to == null) {
            return;
        }
        Vec3 along = to.subtract(from);
        if (along.lengthSqr() < 1.0e-9) {
            return;
        }
        Vec3 dir = along.normalize();
        Vec3 side = dir.cross(new Vec3(0.0, 1.0, 0.0));
        if (side.lengthSqr() < 1.0e-6) {
            side = dir.cross(new Vec3(1.0, 0.0, 0.0));
        }
        side = side.normalize().scale(along.length() * 0.18);
        Vec3 back = to.subtract(dir.scale(along.length() * 0.28));
        line(level, slot, from, to, color, width, ttl);
        line(level, slot + ":barbA", to, back.add(side), color, width, ttl);
        line(level, slot + ":barbB", to, back.subtract(side), color, width, ttl);
    }

    public static void clearArrow(ServerLevel level, String slot) {
        clear(level, slot);
        clear(level, slot + ":barbA");
        clear(level, slot + ":barbB");
    }

    public static void clear(ServerLevel level, String slot) {
        if (level == null || slot == null) {
            return;
        }
        PacketDistributor.sendToPlayersInDimension(level,
                new DebugLinePacket(slot, Vec3.ZERO, Vec3.ZERO, 0, 0.0f, 0));
    }
}
