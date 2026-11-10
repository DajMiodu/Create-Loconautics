package com.lycoris.loconautics.content.controls.panelgauge;

import net.minecraft.util.StringRepresentable;

public enum GaugeNeedle implements StringRepresentable {

    DEFAULT("default", 4.0f, 2.0f, -160f, 0f, 1, 36, 4, 2, 4),
    CLASSIC("classic", 2.13137f, 2.13137f, -80f, 0f, 1, 46, 4, 2, 5),
    VERTICAL("vertical", 6.0f, 4.0f, -150f, 0f, 1, 36, 4, 2, 4),
    LINEAR("linear", 0f, 0f, 0f, 3.5f, 9, 41, 1, 0, 2);

    public static final int COLORS = 8;

    private static final int AUTHORED_COLOR = 4;

    private final String name;
    private final float pivotX;
    private final float pivotZ;
    private final float sweepDegrees;
    private final float slidePixels;
    private final int sheetX;
    private final int sheetY;
    private final int colorsPerBand;
    private final int colorStrideU;
    private final int bandStrideV;

    GaugeNeedle(String name, float pivotX, float pivotZ, float sweepDegrees, float slidePixels,
                int sheetX, int sheetY, int colorsPerBand, int colorStrideU, int bandStrideV) {
        this.name = name;
        this.pivotX = pivotX;
        this.pivotZ = pivotZ;
        this.sweepDegrees = sweepDegrees;
        this.slidePixels = slidePixels;
        this.sheetX = sheetX;
        this.sheetY = sheetY;
        this.colorsPerBand = colorsPerBand;
        this.colorStrideU = colorStrideU;
        this.bandStrideV = bandStrideV;
    }

    public static GaugeNeedle forBackground(int background) {
        return switch (Math.floorMod(background, 6)) {
            case 3 -> DEFAULT;
            case 4 -> CLASSIC;
            case 5 -> VERTICAL;
            default -> LINEAR;
        };
    }

    public float pivotX() {
        return pivotX / 16f;
    }

    public float pivotZ() {
        return pivotZ / 16f;
    }

    public float sweepDegrees() {
        return sweepDegrees;
    }

    public float slidePixels() {
        return slidePixels;
    }

    public boolean slides() {
        return slidePixels != 0f;
    }

    public int colorOffsetU(int color) {
        return (color % colorsPerBand - AUTHORED_COLOR % colorsPerBand) * colorStrideU;
    }

    public int colorOffsetV(int color) {
        return (color / colorsPerBand - AUTHORED_COLOR / colorsPerBand) * bandStrideV;
    }

    public int sheetX(int color) {
        return sheetX + colorOffsetU(color);
    }

    public int sheetY(int color) {
        return sheetY + colorOffsetV(color);
    }

    public int sheetWidth() {
        return this == LINEAR ? 6 : 1;
    }

    public int sheetHeight() {
        return this == LINEAR ? 1 : (this == CLASSIC ? 4 : 3);
    }

    public static int clampColor(int color) {
        return Math.floorMod(color, COLORS);
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
