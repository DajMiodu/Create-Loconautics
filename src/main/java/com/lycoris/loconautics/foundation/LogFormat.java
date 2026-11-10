package com.lycoris.loconautics.foundation;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import org.joml.Vector3f;

import java.util.Locale;
import java.util.UUID;

public final class LogFormat {

    private LogFormat() {
    }

    public static String shortId(UUID id) {
        return id == null ? "----" : id.toString().substring(0, 8);
    }

    public static String shortId(ServerSubLevel sub) {
        return sub == null ? "WORLD" : shortId(sub.getUniqueId());
    }

    public static String v3d(org.joml.Vector3dc v) {
        return v == null ? "null" : String.format(Locale.ROOT, "(%.3f,%.3f,%.3f)", v.x(), v.y(), v.z());
    }

    public static String v3f(Vector3f v) {
        return v == null ? "null" : String.format(Locale.ROOT, "(%.4f,%.4f,%.4f)", v.x, v.y, v.z);
    }

    public static String f(double d) {
        return String.format(Locale.ROOT, "%.2f", d);
    }
}
