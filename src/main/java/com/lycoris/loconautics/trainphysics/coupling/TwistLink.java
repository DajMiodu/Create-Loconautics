package com.lycoris.loconautics.trainphysics.coupling;

import org.joml.Vector3d;

import java.util.UUID;

public record TwistLink(UUID subA, Vector3d anchorA, UUID subB, Vector3d anchorB, Kind kind, double strength,
                        Vector3d axisA) {

    public enum Kind { KNUCKLE, SPRING, SWIVEL }

    public UUID other(UUID id) {
        return id.equals(subA) ? subB : subA;
    }
}
