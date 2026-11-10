package com.lycoris.loconautics.content.controls.panel;

public enum PanelGlass {
    BODY(4f, 2f, 6.5f),
    SLIM(4f, 2f, 6.5f);

    private final float pivotX;
    private final float pivotY;
    private final float pivotZ;

    PanelGlass(float pivotX, float pivotY, float pivotZ) {
        this.pivotX = pivotX / 16f;
        this.pivotY = pivotY / 16f;
        this.pivotZ = pivotZ / 16f;
    }

    public float pivotX() {
        return pivotX;
    }

    public float pivotY() {
        return pivotY;
    }

    public float pivotZ() {
        return pivotZ;
    }
}
