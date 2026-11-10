package com.lycoris.loconautics.trainphysics.spacing;

import net.minecraft.nbt.CompoundTag;

public record CarExtent(double forward, double backward, double left, double right, double height) {

    private static final String KEY = "Extent";

    public double width() {
        return left + right;
    }

    public double halfLength() {
        return Math.max(forward, backward);
    }

    public double halfWidth() {
        return Math.max(0.0, Math.max(left, right));
    }

    public void write(CompoundTag tag) {
        CompoundTag t = new CompoundTag();
        t.putDouble("F", forward);
        t.putDouble("B", backward);
        t.putDouble("L", left);
        t.putDouble("R", right);
        t.putDouble("H", height);
        tag.put(KEY, t);
    }

    public static CarExtent read(CompoundTag tag) {
        if (!tag.contains(KEY)) {
            return null;
        }
        CompoundTag t = tag.getCompound(KEY);
        CarExtent read = new CarExtent(t.getDouble("F"), t.getDouble("B"), t.getDouble("L"), t.getDouble("R"),
                t.getDouble("H"));
        return read.valid() ? read : null;
    }

    private boolean valid() {
        return Double.isFinite(forward) && Double.isFinite(backward) && Double.isFinite(left)
                && Double.isFinite(right) && Double.isFinite(height) && forward >= 0.0 && backward >= 0.0
                && left + right > 0.0 && height > 0.0;
    }
}
