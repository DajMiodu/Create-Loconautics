package com.lycoris.loconautics.trainphysics.debug.force;

public enum ForceSource {

    TRACTION("traction", 0x33FF33),
    BRAKE("brake", 0xFF3333),
    ROLLING("rolling", 0xFF8000),
    SLOPE_RELIEF("slope relief", 0xA0A000),
    READBACK_CAP("readback cap", 0xFFFF00),
    SPEED_CAP("speed cap", 0xFFD000),
    RAIL_SNAP("rail snap", 0x00FFFF),
    RAIL_PIN("rail pin", 0x00A0FF),
    DERAILED_DRIVE("derailed drive", 0x80FF80),
    KNOCKBACK("knockback", 0xFF00FF),
    DERAIL_LAUNCH("derail launch", 0xFF60C0),
    RAIL_RECOIL("rail recoil", 0xC060FF),
    COLLISION("collision", 0xFF0080),
    RAIL_REWIND("collision rewind", 0xFF9060),
    HOST_REACTION("host reaction", 0x909090),
    EXPLOSION("explosion", 0xFF4000),
    EXTERNAL("external push", 0x00FF80),
    HINGE("wheel hinge", 0x40FFC0),
    CURVE_RELIEF("curve relief", 0xC0FF40),
    KNUCKLE("knuckle", 0xFFC040),
    HOSE("hose", 0x40A0FF),
    SOLVER("solver", 0xFFFFFF);

    private final String label;
    private final int color;

    ForceSource(String label, int color) {
        this.label = label;
        this.color = color;
    }

    public String label() {
        return label;
    }

    public int color() {
        return color;
    }
}
