package com.lycoris.loconautics.content.boiler;

public enum BoilerState {
    COLD,
    WARMING,
    HOT,
    STARVED;

    public boolean isActive() {
        return this == WARMING || this == HOT || this == STARVED;
    }

    public boolean isHot() {
        return this == HOT;
    }
}
