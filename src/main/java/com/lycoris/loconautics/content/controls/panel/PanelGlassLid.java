package com.lycoris.loconautics.content.controls.panel;

public final class PanelGlassLid {

    public static final float SPEED = 2f;

    private static final float BEZIER_HANDLE = 0.1f;
    private static final int BEZIER_STEPS = 16;

    private enum Kind { LINEAR, CATMULL, BEZIER }

    private record Key(float time, float value, Kind kind) {
    }

    // keyframes copied from the open and close animations in the glass bbmodel
    private static final Key[] OPEN = {
            new Key(0f, 0f, Kind.LINEAR),
            new Key(0.5f, 100f, Kind.LINEAR),
            new Key(0.75f, 100f, Kind.CATMULL)
    };

    private static final Key[] CLOSE = {
            new Key(0f, 100f, Kind.CATMULL),
            new Key(0.25f, 100f, Kind.BEZIER),
            new Key(0.75f, 15f, Kind.BEZIER),
            new Key(1f, 0f, Kind.CATMULL)
    };

    private PanelGlassLid() {}

    public static float angle(boolean opening, float seconds) {
        return sample(opening ? OPEN : CLOSE, seconds * SPEED);
    }

    private static float sample(Key[] keys, float time) {
        if (time <= keys[0].time) return keys[0].value;
        Key last = keys[keys.length - 1];
        if (time >= last.time) return last.value;
        int i = 0;
        while (keys[i + 1].time <= time) i++;
        Key before = keys[i];
        Key after = keys[i + 1];
        float alpha = (time - before.time) / (after.time - before.time);
        if (before.kind == Kind.CATMULL || after.kind == Kind.CATMULL) {
            float p0 = i > 0 ? keys[i - 1].value : before.value;
            float p3 = i + 2 < keys.length ? keys[i + 2].value : after.value;
            return catmull(p0, before.value, after.value, p3, alpha);
        }
        if (before.kind == Kind.BEZIER || after.kind == Kind.BEZIER) return bezier(before, after, time);
        return before.value + (after.value - before.value) * alpha;
    }

    private static float catmull(float p0, float p1, float p2, float p3, float t) {
        float t2 = t * t;
        float t3 = t2 * t;
        return 0.5f * (2f * p1 + (p2 - p0) * t + (2f * p0 - 5f * p1 + 4f * p2 - p3) * t2
                + (3f * p1 - p0 - 3f * p2 + p3) * t3);
    }

    // handles sit a fixed time away from each key at the keys own value, so the curve eases in and out
    private static float bezier(Key before, Key after, float time) {
        float x0 = before.time;
        float x1 = before.time + BEZIER_HANDLE;
        float x2 = after.time - BEZIER_HANDLE;
        float x3 = after.time;
        float low = 0f;
        float high = 1f;
        for (int step = 0; step < BEZIER_STEPS; step++) {
            float mid = (low + high) * 0.5f;
            if (cubic(x0, x1, x2, x3, mid) < time) low = mid;
            else high = mid;
        }
        float s = (low + high) * 0.5f;
        return cubic(before.value, before.value, after.value, after.value, s);
    }

    private static float cubic(float a, float b, float c, float d, float s) {
        float inverse = 1f - s;
        return inverse * inverse * inverse * a + 3f * inverse * inverse * s * b
                + 3f * inverse * s * s * c + s * s * s * d;
    }
}
