package com.lycoris.loconautics.trainphysics.portal;

import java.util.UUID;

public record PortalCut(UUID subLevelId, int axis, int cutoff, boolean positive) {

    public static PortalCut everything(UUID subLevelId) {
        return new PortalCut(subLevelId, 0, Integer.MIN_VALUE, true);
    }

    public boolean hidden(int x, int y, int z) {
        int coord = axis == 0 ? x : axis == 1 ? y : z;
        return positive ? coord > cutoff : coord < cutoff;
    }

    public boolean noCollider(int x, int y, int z) {
        return hidden(x, y, z) || seam(x, y, z);
    }

    public boolean seam(int x, int y, int z) {
        return (axis == 0 ? x : axis == 1 ? y : z) == cutoff;
    }
}
