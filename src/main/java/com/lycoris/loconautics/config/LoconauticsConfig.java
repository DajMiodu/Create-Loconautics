package com.lycoris.loconautics.config;

import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.ModConfigSpec;

public final class LoconauticsConfig {

    public static ModConfigSpec.BooleanValue ENABLE_SABLE_TRAINS;
    public static ModConfigSpec.BooleanValue ENABLE_CREATE_TRAINS;
    public static ModConfigSpec.DoubleValue BEARING_AXLE_MAX_SPEED;
    public static ModConfigSpec.DoubleValue TRAIN_MAX_SPEED;
    public static ModConfigSpec.BooleanValue GEARING_ENABLED;
    public static ModConfigSpec.DoubleValue GEARING_OVERDRIVE_SCALE;
    public static ModConfigSpec.BooleanValue GEARING_CAP_AT_CREATE_TOP_SPEED;
    public static ModConfigSpec.IntValue MAX_BOGEYS_PER_CARRIAGE;

    public static ModConfigSpec.IntValue STATION_SCAN_WIDTH;
    public static ModConfigSpec.IntValue STATION_SCAN_HEIGHT;
    public static ModConfigSpec.IntValue STATION_SCAN_DEPTH;
    public static ModConfigSpec.BooleanValue STATION_LOAD_CHUNKS;

    public static ModConfigSpec.DoubleValue DYNAMICS_REFERENCE_MASS;
    public static ModConfigSpec.DoubleValue DYNAMICS_BASE_ACCELERATION;
    public static ModConfigSpec.DoubleValue DYNAMICS_MASS_EXPONENT;
    public static ModConfigSpec.DoubleValue DYNAMICS_MIN_ACCELERATION;
    public static ModConfigSpec.DoubleValue DYNAMICS_MAX_ACCELERATION;
    public static ModConfigSpec.DoubleValue DYNAMICS_BASE_DECELERATION;
    public static ModConfigSpec.DoubleValue DYNAMICS_TWO_DRIVER_ACCELERATION;
    public static ModConfigSpec.DoubleValue DYNAMICS_THREE_DRIVER_ACCELERATION;
    public static ModConfigSpec.DoubleValue DYNAMICS_ROLLING_RESISTANCE;
    public static ModConfigSpec.BooleanValue SLOPE_EFFECTS_ENABLED;
    public static ModConfigSpec.DoubleValue SLOPE_STRESS_FACTOR;

    public static ModConfigSpec.DoubleValue BOGEY_SWAY_GAIN;
    public static ModConfigSpec.DoubleValue BOGEY_SWAY_MAX;
    public static ModConfigSpec.BooleanValue BOGEY_PHYSICS_PIVOT;

    public static ModConfigSpec.BooleanValue WRENCH_RELOCATION_ENABLED;
    public static ModConfigSpec.BooleanValue WRENCH_RELOCATION_DERAILED_ONLY;

    public static ModConfigSpec.IntValue RAIL_SEARCH_RADIUS;
    public static ModConfigSpec.IntValue RAIL_SEARCH_VERTICAL_RANGE;
    public static ModConfigSpec.DoubleValue RAIL_HOP_MAX_ANGLE;

    public static ModConfigSpec.DoubleValue DERAIL_MAX_TILT_DEGREES;
    public static ModConfigSpec.BooleanValue DERAIL_ON_EXTERNAL_FORCE;
    public static ModConfigSpec.BooleanValue DERAIL_AT_TRACK_END;
    public static ModConfigSpec.DoubleValue DERAIL_MAX_END_IMPACT;
    public static ModConfigSpec.DoubleValue DERAILED_SLIP_FACTOR;
    public static ModConfigSpec.DoubleValue DERAIL_COLLISION_SPEED;
    public static ModConfigSpec.BooleanValue DERAIL_COUPLED_COLLISION;

    public static ModConfigSpec.BooleanValue AUTO_RERAIL_ENABLED;
    public static ModConfigSpec.DoubleValue AUTO_RERAIL_MAX_SPEED;

    public static ModConfigSpec.IntValue INJECTOR_TANK_CAPACITY;

    public static ModConfigSpec.DoubleValue MASS_DIVISOR;
    public static ModConfigSpec.DoubleValue BASE_IMPACT;
    public static ModConfigSpec.DoubleValue OPPOSED_STRESS_FACTOR;
    public static ModConfigSpec.IntValue ANALOG_DECAY_TICKS;
    public static ModConfigSpec.IntValue ANALOG_NEGATIVE_CAP;
    public static ModConfigSpec.DoubleValue TRANSMISSION_SU_IMPACT;
    public static ModConfigSpec.DoubleValue TURNTABLE_SU_IMPACT;

    public static ModConfigSpec.DoubleValue STEEL_CABLE_MAX_RANGE;

    public static ModConfigSpec.IntValue KNUCKLE_SCAN_INTERVAL;

    public static ModConfigSpec.IntValue HOSE_MAX_LENGTH;

    public static ModConfigSpec.BooleanValue EXPLOSION_SUBLEVEL_PUSH_ENABLED;
    public static ModConfigSpec.DoubleValue EXPLOSION_SUBLEVEL_PUSH_RADIUS_MULTIPLIER;
    public static ModConfigSpec.DoubleValue EXPLOSION_SUBLEVEL_PUSH_STRENGTH;
    public static ModConfigSpec.DoubleValue EXPLOSION_SUBLEVEL_PUSH_UPWARD;
    public static ModConfigSpec.DoubleValue EXPLOSION_SUBLEVEL_PUSH_MIN_DISTANCE;

    public static ModConfigSpec.DoubleValue DEMOLITION_CHARGE_BASE_POWER;
    public static ModConfigSpec.IntValue DEMOLITION_CHARGE_MAX_CHARGES;
    public static ModConfigSpec.IntValue DEMOLITION_CHARGE_FUSE_TICKS;

    public static ModConfigSpec.BooleanValue CC_REMOTE_CONTROL;

    public static ModConfigSpec.BooleanValue enableRollerPatch;
    public static ModConfigSpec.BooleanValue enablePloughPatch;
    public static ModConfigSpec.IntValue rollerHeadroomClearDepth;
    public static ModConfigSpec.BooleanValue requireMaterialConsumption;

    public static ModConfigSpec.DoubleValue DIAL_WOBBLE_DEGREES;
    public static final ModConfigSpec SERVER_SPEC;
    public static final ModConfigSpec CLIENT_SPEC;

    static {
        ModConfigSpec.Builder server = new ModConfigSpec.Builder();
        defineServer(server);
        SERVER_SPEC = server.build();

        ModConfigSpec.Builder client = new ModConfigSpec.Builder();
        defineClient(client);
        CLIENT_SPEC = client.build();
    }

    private static void defineServer(ModConfigSpec.Builder BUILDER) {

        BUILDER.push("foundation");

        ENABLE_SABLE_TRAINS = BUILDER
                .comment("Lets a Station assemble Loconautics physics Trains.")
                .define("enableSableTrains", true);

        ENABLE_CREATE_TRAINS = BUILDER
                .comment("Lets a Station assemble regular Create Trains.")
                .define("enableCreateTrains", true);

        BUILDER.pop();

        BUILDER.push("train");

        BEARING_AXLE_MAX_SPEED = BUILDER
                .comment("Top speed at 256 RPM with the Bearing Axle in gear 0. -1 follows Create's train top speed.")
                .defineInRange("bearingAxleMaxSpeed", -1.0, -1.0, 1000.0);

        TRAIN_MAX_SPEED = BUILDER
                .comment("Hard ceiling on any Train's speed, overdrive included. -1 leaves only the physics safety ceiling of 200.")
                .defineInRange("trainMaxSpeed", -1.0, -1.0, 1000.0);

        BUILDER.push("gearing");

        GEARING_ENABLED = BUILDER
                .comment("Lets a redstone signal into a Bearing Axle shift it into an overdrive gear. Off, every axle stays in gear 0.")
                .define("enabled", true);

        GEARING_OVERDRIVE_SCALE = BUILDER
                .comment("How much top speed each spare SU of the kinetic network buys in the highest gear.",
                        "Raise it and a small network reaches silly speeds, lower it and overdrive needs a bigger build.")
                .defineInRange("overdriveScale", 1.0, 0.0, 100.0);

        GEARING_CAP_AT_CREATE_TOP_SPEED = BUILDER
                .comment("Keeps every gear under Create's train top speed. Gears then only reach that speed at a lower RPM.")
                .define("capAtCreateTopSpeed", false);

        BUILDER.pop();

        MAX_BOGEYS_PER_CARRIAGE = BUILDER
                .defineInRange("maxBogeysPerCarriage", 2, 1, 16);

        BUILDER.push("station");

        STATION_SCAN_WIDTH = BUILDER
                .defineInRange("scanWidth", 8, 0, 16);

        STATION_SCAN_HEIGHT = BUILDER
                .defineInRange("scanHeight", 24, 1, 48);

        STATION_SCAN_DEPTH = BUILDER
                .defineInRange("scanDepth", 3, 0, 16);

        STATION_LOAD_CHUNKS = BUILDER
                .define("loadChunksWhileStopped", true);

        BUILDER.pop();

        BUILDER.push("dynamics");

        DYNAMICS_REFERENCE_MASS = BUILDER
                .comment("The consist mass that gets exactly baseAcceleration. Lighter accelerates harder, heavier softer.",
                        "Rolling resistance is weighed against it too.")
                .defineInRange("referenceMass", 400.0, 1.0, 1000000.0);

        DYNAMICS_BASE_ACCELERATION = BUILDER
                .comment("How hard a consist of exactly referenceMass pulls away. Scaled by referenceMass / consist mass.")
                .defineInRange("baseAcceleration", 3.0, 0.0, 1000.0);

        DYNAMICS_MASS_EXPONENT = BUILDER
                .comment("How fast acceleration falls as the consist gets heavier. 1 is strictly proportional, 0 ignores mass.",
                        "Lower keeps long rakes playable.")
                .defineInRange("massExponent", 0.5, 0.0, 2.0);

        DYNAMICS_MIN_ACCELERATION = BUILDER
                .comment("Floor under the scaled acceleration, so the heaviest rake still pulls away.")
                .defineInRange("minAcceleration", 0.75, 0.0, 1000.0);

        DYNAMICS_MAX_ACCELERATION = BUILDER
                .comment("Ceiling over the scaled acceleration, so a near empty cart does not launch.")
                .defineInRange("maxAcceleration", 6.0, 0.0, 1000.0);

        DYNAMICS_BASE_DECELERATION = BUILDER
                .comment("How hard the brakes bite, the same number whatever the train is hauling.")
                .defineInRange("baseDeceleration", 3.0, 0.0, 1000.0);

        DYNAMICS_TWO_DRIVER_ACCELERATION = BUILDER
                .comment("Acceleration multiplier when two seated drivers of one consist push the same way.",
                        "Top speed does not change, drivers pushing opposite ways cancel out.")
                .defineInRange("twoDriverAcceleration", 1.5, 1.0, 10.0);

        DYNAMICS_THREE_DRIVER_ACCELERATION = BUILDER
                .comment("Acceleration multiplier when three or more seated drivers of one consist push the same way.")
                .defineInRange("threeDriverAcceleration", 2.0, 1.0, 10.0);

        DYNAMICS_ROLLING_RESISTANCE = BUILDER
                .comment("How hard a car with no power and no brake slows down on its own.",
                        "Raise it and a shunted car coasts a shorter distance, 0 lets it roll forever.",
                        "Scaled by the car's mass against referenceMass, so a loaded car drags harder.")
                .defineInRange("rollingResistance", 0.6, 0.0, 1000.0);

        SLOPE_EFFECTS_ENABLED = BUILDER
                .comment("Turn off to climb any grade at flat track stress.")
                .define("slopeEffects", true);

        SLOPE_STRESS_FACTOR = BUILDER
                .comment("Extra stress impact per degree of grade while climbing, split across the working axles.",
                        "Raising it makes steep climbs stall the network, 0 makes slopes free.")
                .defineInRange("slopeStressPerDegree", 0.5, 0.0, 100.0);

        BUILDER.pop();

        BUILDER.push("visual");

        BOGEY_SWAY_GAIN = BUILDER
                .defineInRange("bogeySwayGain", 0.0, 0.0, 1000.0);

        BOGEY_SWAY_MAX = BUILDER
                .defineInRange("bogeySwayMax", 1.0, 0.0, 4.0);

        BOGEY_PHYSICS_PIVOT = BUILDER
                .define("bogeyPhysicsPivot", true);

        BUILDER.pop();

        BUILDER.push("relocation");

        WRENCH_RELOCATION_ENABLED = BUILDER
                .define("enabled", true);

        WRENCH_RELOCATION_DERAILED_ONLY = BUILDER
                .define("derailedOnly", false);

        BUILDER.pop();

        BUILDER.pop();

        BUILDER.push("track");

        RAIL_SEARCH_RADIUS = BUILDER
                .defineInRange("railSearchRadius", 3, 0, 16);

        RAIL_SEARCH_VERTICAL_RANGE = BUILDER
                .defineInRange("railSearchVerticalRange", 16, 0, 64);

        RAIL_HOP_MAX_ANGLE = BUILDER
                .comment("How far a rail may point away from a Train's heading and still count as a hop target.",
                        "Raise it and a Train takes sharper mismatched joins, which can snap it sideways.")
                .defineInRange("railHopMaxAngleDegrees", 72.5, 0.0, 90.0);

        BUILDER.push("derailment");

        DERAIL_MAX_TILT_DEGREES = BUILDER
                .comment("How far the rail under a Train may lean away from level before the Train falls off it.",
                        "Set it to 180 and a Train rides a trackway that has rolled right over.")
                .defineInRange("maxTiltDegrees", 55.0, 0.0, 180.0);

        DERAIL_ON_EXTERNAL_FORCE = BUILDER
                .comment("On, a strong enough upward push such as the Physics Staff lifts a Train off the rail. Off, nothing can lift it off.")
                .define("derailOnExternalForce", true);

        DERAIL_AT_TRACK_END = BUILDER
                .define("derailAtTrackEnd", true);

        DERAIL_MAX_END_IMPACT = BUILDER
                .defineInRange("maxEndImpact", 10.0, 0.0, 1.0e9);

        DERAILED_SLIP_FACTOR = BUILDER
                .defineInRange("derailedSlipFactor", 0.25, 0.0, 1.0);

        DERAIL_COLLISION_SPEED = BUILDER
                .comment("Speed change in blocks per second a Carriage can take from hitting another Train before it leaves the rail.",
                        "The lighter side of a hit takes most of the change, so shunting stays on the rail and a fast hit does not.")
                .defineInRange("collisionDerailSpeed", 8.0, 0.0, 1000.0);

        DERAIL_COUPLED_COLLISION = BUILDER
                .comment("Lets two Carriages coupled into the same Train derail each other when they hit.")
                .define("coupledCollisionDerail", false);

        BUILDER.pop();

        BUILDER.push("rerail");

        AUTO_RERAIL_ENABLED = BUILDER
                .define("autoRerailEnabled", true);

        AUTO_RERAIL_MAX_SPEED = BUILDER
                .defineInRange("autoRerailMaxSpeed", 0.5, 0.0, 1000.0);

        BUILDER.pop();

        BUILDER.pop();

        BUILDER.push("hatch");

        INJECTOR_TANK_CAPACITY = BUILDER
                .defineInRange("injectorTankCapacity", 8000, 1000, 1000000);

        BUILDER.pop();

        BUILDER.push("blocks");

        BUILDER.push("bearing_axle");

        MASS_DIVISOR = BUILDER
                .defineInRange("massDivisor", 10.0, 1.0, 100000.0);

        BASE_IMPACT = BUILDER
                .defineInRange("baseImpact", 1.0, 0.0, 100.0);

        OPPOSED_STRESS_FACTOR = BUILDER
                .comment("Stress multiplier for a Bearing Axle pushing against the rest of its train.",
                        "Raising it makes a fight between two engines expensive instead of merely slow.")
                .defineInRange("opposedStressFactor", 1.5, 1.0, 5.0);

        BUILDER.pop();

        BUILDER.push("analog_controller");

        ANALOG_DECAY_TICKS = BUILDER
                .defineInRange("decayTicks", 20, 1, 200);

        ANALOG_NEGATIVE_CAP = BUILDER
                .defineInRange("negativeCap", 5, 0, 15);

        BUILDER.pop();

        BUILDER.push("transmission");

        TRANSMISSION_SU_IMPACT = BUILDER
                .defineInRange("suImpact", 8.0, 0.0, 100.0);

        BUILDER.pop();

        BUILDER.push("turntable_bearing");

        TURNTABLE_SU_IMPACT = BUILDER
                .comment("Stress the Turntable Bearing costs per RPM of plate speed.",
                        "A turntable driven above its input speed is billed for the speed it turns at, not the speed it is fed.")
                .defineInRange("suImpact", 4.0, 0.0, 100.0);

        BUILDER.pop();

        BUILDER.pop();

        BUILDER.push("items");
        BUILDER.push("steel_cable");

        STEEL_CABLE_MAX_RANGE = BUILDER
                .defineInRange("maxRange", 64.0, 1.0, 10000.0);

        BUILDER.pop();
        BUILDER.pop();

        BUILDER.push("couplers");
        BUILDER.push("knuckle");

        KNUCKLE_SCAN_INTERVAL = BUILDER
                .defineInRange("scanInterval", 5, 1, 100);

        BUILDER.pop();
        BUILDER.pop();

        BUILDER.push("hose");

        HOSE_MAX_LENGTH = BUILDER
                .defineInRange("maxLength", 28, 1, 256);

        BUILDER.pop();

        BUILDER.push("explosions");
        BUILDER.push("sub_level_push");

        EXPLOSION_SUBLEVEL_PUSH_ENABLED = BUILDER
                .define("subLevelPushEnabled", true);

        EXPLOSION_SUBLEVEL_PUSH_RADIUS_MULTIPLIER = BUILDER
                .defineInRange("subLevelPushRadiusMultiplier", 3.0, 0.0, 100.0);

        EXPLOSION_SUBLEVEL_PUSH_STRENGTH = BUILDER
                .defineInRange("subLevelPushStrength", 2.0, 0.0, 1000.0);

        EXPLOSION_SUBLEVEL_PUSH_UPWARD = BUILDER
                .defineInRange("subLevelPushUpward", 0.3, 0.0, 10.0);

        EXPLOSION_SUBLEVEL_PUSH_MIN_DISTANCE = BUILDER
                .defineInRange("subLevelPushMinDistance", 1.0, 0.1, 16.0);

        BUILDER.pop();

        BUILDER.push("demolition_charge");

        DEMOLITION_CHARGE_BASE_POWER = BUILDER
                .defineInRange("basePower", 4.0, 0.1, 1000.0);

        DEMOLITION_CHARGE_MAX_CHARGES = BUILDER
                .defineInRange("maxCharges", 4, 1, 4);

        DEMOLITION_CHARGE_FUSE_TICKS = BUILDER
                .defineInRange("fuseTicks", 80, 1, 2000);

        BUILDER.pop();
        BUILDER.pop();

        BUILDER.push("compat");
        BUILDER.push("computercraft");

        CC_REMOTE_CONTROL = BUILDER
                .comment("Lets a computer drive any train, signal, point or turntable by id.",
                        "Off keeps control to computers inside the train.")
                .define("remoteControl", true);

        BUILDER.pop();
        BUILDER.pop();

        BUILDER.push("patch");

        enableRollerPatch = BUILDER
                .define("enableRollerPatch", true);

        enablePloughPatch = BUILDER
                .define("enablePloughPatch", true);

        rollerHeadroomClearDepth = BUILDER
                .defineInRange("rollerHeadroomClearDepth", 2, 0, 8);

        requireMaterialConsumption = BUILDER
                .define("requireMaterialConsumption", true);

        BUILDER.pop();
    }

    private static void defineClient(ModConfigSpec.Builder BUILDER) {
        BUILDER.push("gauges");

        DIAL_WOBBLE_DEGREES = BUILDER
                .defineInRange("dialWobbleDegrees", 1.2, 0.0, 20.0);

        BUILDER.pop();
    }

    private LoconauticsConfig() {
    }
}