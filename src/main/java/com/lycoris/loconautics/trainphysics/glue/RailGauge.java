package com.lycoris.loconautics.trainphysics.glue;

import com.simibubi.create.content.trains.track.TrackMaterial;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;

public final class RailGauge {

    private RailGauge() {
    }

    public static final double STANDARD_HALF = 0.965;

    // from railways segmentangles, wide adds 0.5, narrow and universal take 0.4375
    private static final Map<ResourceLocation, Double> HALF_BY_TYPE = Map.of(
            ResourceLocation.fromNamespaceAndPath("railways", "wide_gauge"), STANDARD_HALF + 0.5,
            ResourceLocation.fromNamespaceAndPath("railways", "narrow_gauge"), STANDARD_HALF - 0.4375,
            ResourceLocation.fromNamespaceAndPath("railways", "universal"), STANDARD_HALF - 0.4375);

    private static final ResourceLocation PHANTOM = ResourceLocation.fromNamespaceAndPath("railways", "phantom");

    public static double halfGauge(TrackMaterial material) {
        if (material == null || material.trackType == null || PHANTOM.equals(material.id)) {
            return STANDARD_HALF;
        }
        return HALF_BY_TYPE.getOrDefault(material.trackType.id, STANDARD_HALF);
    }
}
