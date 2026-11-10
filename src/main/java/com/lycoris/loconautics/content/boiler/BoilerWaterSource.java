package com.lycoris.loconautics.content.boiler;

public interface BoilerWaterSource {

    int getWaterAmount();

    int getWaterCapacity();

    int drawWater(int millibuckets);

    void returnWater(int millibuckets);

    default void setWaterCapacity(int millibuckets) {
    }
}
