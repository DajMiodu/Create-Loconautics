package com.lycoris.loconautics.client;

import net.minecraft.core.Direction;

public final class QuadVertices {

    private QuadVertices() {}

    public static void put(int[] data, int index, float x, float y, float z, float u, float v, Direction face) {
        int i = index * 8;
        data[i] = Float.floatToRawIntBits(x);
        data[i + 1] = Float.floatToRawIntBits(y);
        data[i + 2] = Float.floatToRawIntBits(z);
        data[i + 3] = 0xFFFFFFFF;
        data[i + 4] = Float.floatToRawIntBits(u);
        data[i + 5] = Float.floatToRawIntBits(v);
        data[i + 6] = 0;
        int nx = ((byte) (face.getStepX() * 127)) & 0xFF;
        int ny = ((byte) (face.getStepY() * 127)) & 0xFF;
        int nz = ((byte) (face.getStepZ() * 127)) & 0xFF;
        data[i + 7] = nx | (ny << 8) | (nz << 16);
    }
}
