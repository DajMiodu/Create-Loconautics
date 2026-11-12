package com.lycoris.loconautics.config;

import com.lycoris.loconautics.LoconauticsConstants;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.ModConfigSpec;

public final class LoconauticsConfig {

    private static final String TRANSLATION_PREFIX = LoconauticsConstants.MOD_ID + ".configuration.";

    public enum Realism { REALISTIC, ARCADE }
    public enum TemperatureUnit { CELSIUS, FAHRENHEIT, KELVIN }

    public static ModConfigSpec.BooleanValue ENABLE_SABLE_TRAINS;
    public static ModConfigSpec.BooleanValue ENABLE_CREATE_TRAINS;
    public static ModConfigSpec.DoubleValue BEARING_AXLE_MAX_SPEED;
    public static ModConfigSpec.DoubleValue TRAIN_MAX_SPEED;
    public static ModConfigSpec.BooleanValue GEARING_ENABLED;
    public static ModConfigSpec.DoubleValue GEARING_OVERDRIVE_SCALE;
    public static ModConfigSpec.BooleanValue GEARING_CAP_AT_CREATE_TOP_SPEED;
    public static ModConfigSpec.IntValue MAX_BOGEYS_PER_CARRIAGE;

    public static ModConfigSpec.BooleanValue STATION_LOAD_CHUNKS;

    public static ModConfigSpec.DoubleValue DYNAMICS_REFERENCE_MASS;
    public static ModConfigSpec.DoubleValue DYNAMICS_BASE_ACCELERATION;
    public static ModConfigSpec.DoubleValue DYNAMICS_BASE_DECELERATION;
    public static ModConfigSpec.DoubleValue DYNAMICS_ROLLING_RESISTANCE;
    public static ModConfigSpec.BooleanValue SLOPE_EFFECTS_ENABLED;


    public static ModConfigSpec.BooleanValue WRENCH_RELOCATION_ENABLED;
    public static ModConfigSpec.BooleanValue WRENCH_RELOCATION_DERAILED_ONLY;

    public static ModConfigSpec.IntValue RAIL_SEARCH_RADIUS;

    public static ModConfigSpec.DoubleValue DERAIL_MAX_TILT_DEGREES;
    public static ModConfigSpec.BooleanValue DERAIL_ON_EXTERNAL_FORCE;
    public static ModConfigSpec.BooleanValue DERAIL_ON_CURVE;
    public static ModConfigSpec.BooleanValue DERAIL_AT_TRACK_END;
    public static ModConfigSpec.DoubleValue DERAILED_SLIP_FACTOR;
    public static ModConfigSpec.DoubleValue DERAIL_COLLISION_SPEED;
    public static ModConfigSpec.BooleanValue DERAIL_COUPLED_COLLISION;

    public static ModConfigSpec.BooleanValue AUTO_RERAIL_ENABLED;
    public static ModConfigSpec.DoubleValue AUTO_RERAIL_MAX_SPEED;

    public static ModConfigSpec.EnumValue<Realism> BOILER_REALISM;
    public static ModConfigSpec.DoubleValue AMBIENT_TEMPERATURE_C;
    public static ModConfigSpec.DoubleValue WORKING_TEMPERATURE_C;

    public static ModConfigSpec.IntValue WARMUP_TICKS;
    public static ModConfigSpec.DoubleValue SU_PER_BODY_BLOCK;

    public static ModConfigSpec.IntValue BOILER_MAX_LENGTH;
    public static ModConfigSpec.IntValue BODY_WATER_CAPACITY;
    public static ModConfigSpec.IntValue INJECTOR_TANK_CAPACITY;

    public static ModConfigSpec.DoubleValue PRESSURE_RISE_PER_TICK;
    public static ModConfigSpec.DoubleValue BURST_PRESSURE;
    public static ModConfigSpec.IntValue CROWN_SHEET_TICKS;
    public static ModConfigSpec.IntValue DRAFT_CHOKE_TICKS;
    public static ModConfigSpec.DoubleValue FAILURE_EXPLOSION_BASE;
    public static ModConfigSpec.DoubleValue FAILURE_EXPLOSION_MAX;

    public static ModConfigSpec.BooleanValue CRASH_EXPLODE_ENABLED;
    public static ModConfigSpec.DoubleValue CRASH_IMPACT_THRESHOLD;
    public static ModConfigSpec.DoubleValue CRASH_EXPLOSION_POWER;

    public static ModConfigSpec.DoubleValue OPPOSED_STRESS_FACTOR;
    public static ModConfigSpec.IntValue ANALOG_DECAY_TICKS;
    public static ModConfigSpec.DoubleValue TRANSMISSION_SU_IMPACT;
    public static ModConfigSpec.DoubleValue TURNTABLE_SU_IMPACT;

    public static ModConfigSpec.DoubleValue STEEL_CABLE_MAX_RANGE;

    public static ModConfigSpec.IntValue KNUCKLE_SCAN_INTERVAL;

    public static ModConfigSpec.IntValue HOSE_MAX_LENGTH;

    public static ModConfigSpec.BooleanValue EXPLOSION_SUBLEVEL_PUSH_ENABLED;
    public static ModConfigSpec.DoubleValue EXPLOSION_SUBLEVEL_PUSH_RADIUS_MULTIPLIER;
    public static ModConfigSpec.DoubleValue EXPLOSION_SUBLEVEL_PUSH_STRENGTH;
    public static ModConfigSpec.DoubleValue EXPLOSION_SUBLEVEL_PUSH_UPWARD;

    public static final boolean DEBUG = false;

    public static ModConfigSpec.BooleanValue CBC_DERAIL_ENABLED;
    public static ModConfigSpec.BooleanValue CBC_RECOIL_ENABLED;
    public static ModConfigSpec.BooleanValue CBC_GLOBAL_KNOCKBACK_ENABLED;
    public static ModConfigSpec.BooleanValue CC_REMOTE_CONTROL;

    public static ModConfigSpec.BooleanValue enableRollerPatch;
    public static ModConfigSpec.BooleanValue enablePloughPatch;
    public static ModConfigSpec.BooleanValue requireMaterialConsumption;

    public static ModConfigSpec.DoubleValue SMOKESTACK_PARTICLES_PER_TICK;
    public static ModConfigSpec.BooleanValue SMOKESTACK_ADAPTIVE;
    public static ModConfigSpec.IntValue SMOKESTACK_TARGET_FPS;
    public static ModConfigSpec.EnumValue<TemperatureUnit> TEMPERATURE_UNIT;
    public static ModConfigSpec.DoubleValue DIAL_WOBBLE_DEGREES;
    public static final ModConfigSpec SERVER_SPEC;
    public static final ModConfigSpec CLIENT_SPEC;

    static {
        ModConfigSpec.Builder server = new ModConfigSpec.Builder();
        defineServer(server);
        SERVER_SPEC = server.build();

        ModConfigSpec.Builder client = new ModConfigSpec.Builder();
        defineClient(client);
        BogeyRenderConfig.define(client);
        CLIENT_SPEC = client.build();
    }

    private static void defineServer(ModConfigSpec.Builder BUILDER) {

        BUILDER.translation(TRANSLATION_PREFIX + "foundation").push("foundation");

        ENABLE_SABLE_TRAINS = BUILDER
                .comment("Lets a Station assemble Loconautics physics Trains.")
                .translation(TRANSLATION_PREFIX + "foundation.enableSableTrains")
                .define("enableSableTrains", true);

        ENABLE_CREATE_TRAINS = BUILDER
                .comment("Lets a Station assemble regular Create Trains.")
                .translation(TRANSLATION_PREFIX + "foundation.enableCreateTrains")
                .define("enableCreateTrains", true);

        BUILDER.pop();

        BUILDER.translation(TRANSLATION_PREFIX + "train").push("train");

        BEARING_AXLE_MAX_SPEED = BUILDER
                .comment("Top speed at 256 RPM with the Bearing Axle in gear 0. -1 follows Create's train top speed.")
                .translation(TRANSLATION_PREFIX + "train.bearingAxleMaxSpeed")
                .defineInRange("bearingAxleMaxSpeed", -1.0, -1.0, 1000.0);

        TRAIN_MAX_SPEED = BUILDER
                .comment("Hard ceiling on any Train's speed, overdrive included. -1 leaves only the physics safety ceiling of 200.")
                .translation(TRANSLATION_PREFIX + "train.trainMaxSpeed")
                .defineInRange("trainMaxSpeed", -1.0, -1.0, 1000.0);

        BUILDER.translation(TRANSLATION_PREFIX + "train.gearing").push("gearing");

        GEARING_ENABLED = BUILDER
                .comment("Lets a redstone signal into a Bearing Axle shift it into an overdrive gear. Off, every axle stays in gear 0.")
                .translation(TRANSLATION_PREFIX + "train.gearing.enabled")
                .define("enabled", true);

        GEARING_OVERDRIVE_SCALE = BUILDER
                .comment("How much top speed each spare SU of the kinetic network buys in the highest gear.",
                        "Raise it and a small network reaches silly speeds, lower it and overdrive needs a bigger build.")
                .translation(TRANSLATION_PREFIX + "train.gearing.overdriveScale")
                .defineInRange("overdriveScale", 1.0, 0.0, 100.0);

        GEARING_CAP_AT_CREATE_TOP_SPEED = BUILDER
                .comment("Keeps every gear under Create's train top speed. Gears then only reach that speed at a lower RPM.")
                .translation(TRANSLATION_PREFIX + "train.gearing.capAtCreateTopSpeed")
                .define("capAtCreateTopSpeed", false);

        BUILDER.pop();

        MAX_BOGEYS_PER_CARRIAGE = BUILDER
                .translation(TRANSLATION_PREFIX + "train.maxBogeysPerCarriage")
                .defineInRange("maxBogeysPerCarriage", 2, 1, 16);

        BUILDER.translation(TRANSLATION_PREFIX + "train.station").push("station");

        STATION_LOAD_CHUNKS = BUILDER
                .translation(TRANSLATION_PREFIX + "train.station.loadChunksWhileStopped")
                .define("loadChunksWhileStopped", true);

        BUILDER.pop();

        BUILDER.translation(TRANSLATION_PREFIX + "train.dynamics").push("dynamics");

        DYNAMICS_REFERENCE_MASS = BUILDER
                .comment("The consist mass that gets exactly baseAcceleration. Lighter accelerates harder, heavier softer.",
                        "Rolling resistance is weighed against it too.")
                .translation(TRANSLATION_PREFIX + "train.dynamics.referenceMass")
                .defineInRange("referenceMass", 400.0, 1.0, 1000000.0);

        DYNAMICS_BASE_ACCELERATION = BUILDER
                .comment("How hard a consist of exactly referenceMass pulls away. Scaled by referenceMass / consist mass.")
                .translation(TRANSLATION_PREFIX + "train.dynamics.baseAcceleration")
                .defineInRange("baseAcceleration", 3.0, 0.0, 1000.0);

        DYNAMICS_BASE_DECELERATION = BUILDER
                .comment("How hard the brakes bite, the same number whatever the train is hauling.")
                .translation(TRANSLATION_PREFIX + "train.dynamics.baseDeceleration")
                .defineInRange("baseDeceleration", 3.0, 0.0, 1000.0);

        DYNAMICS_ROLLING_RESISTANCE = BUILDER
                .comment("How hard a car with no power and no brake slows down on its own.",
                        "Raise it and a shunted car coasts a shorter distance, 0 lets it roll forever.",
                        "Scaled by the car's mass against referenceMass, so a loaded car drags harder.")
                .translation(TRANSLATION_PREFIX + "train.dynamics.rollingResistance")
                .defineInRange("rollingResistance", 0.6, 0.0, 1000.0);

        SLOPE_EFFECTS_ENABLED = BUILDER
                .comment("Turn off to climb any grade at flat track stress.")
                .translation(TRANSLATION_PREFIX + "train.dynamics.slopeEffects")
                .define("slopeEffects", true);

        BUILDER.pop();

        BUILDER.translation(TRANSLATION_PREFIX + "train.relocation").push("relocation");

        WRENCH_RELOCATION_ENABLED = BUILDER
                .translation(TRANSLATION_PREFIX + "train.relocation.enabled")
                .define("enabled", true);

        WRENCH_RELOCATION_DERAILED_ONLY = BUILDER
                .translation(TRANSLATION_PREFIX + "train.relocation.derailedOnly")
                .define("derailedOnly", false);

        BUILDER.pop();

        BUILDER.pop();

        BUILDER.translation(TRANSLATION_PREFIX + "track").push("track");

        RAIL_SEARCH_RADIUS = BUILDER
                .translation(TRANSLATION_PREFIX + "track.railSearchRadius")
                .defineInRange("railSearchRadius", 3, 0, 16);

        BUILDER.translation(TRANSLATION_PREFIX + "track.derailment").push("derailment");

        DERAIL_MAX_TILT_DEGREES = BUILDER
                .comment("How far the rail under a Train may lean away from level before the Train falls off it.",
                        "Set it to 180 and a Train rides a trackway that has rolled right over.")
                .translation(TRANSLATION_PREFIX + "track.derailment.maxTiltDegrees")
                .defineInRange("maxTiltDegrees", 55.0, 0.0, 180.0);

        DERAIL_ON_EXTERNAL_FORCE = BUILDER
                .comment("On, a strong enough upward push such as the Physics Staff lifts a Train off the rail. Off, nothing can lift it off.")
                .translation(TRANSLATION_PREFIX + "track.derailment.derailOnExternalForce")
                .define("derailOnExternalForce", true);

        DERAIL_ON_CURVE = BUILDER
                .comment("Off, a fast curve only lifts the wheels on the outside a little. On, curves are full physics and a car taken too fast tips off.")
                .define("derailOnCurve", false);

        DERAIL_AT_TRACK_END = BUILDER
                .translation(TRANSLATION_PREFIX + "track.derailment.derailAtTrackEnd")
                .define("derailAtTrackEnd", true);

        DERAILED_SLIP_FACTOR = BUILDER
                .translation(TRANSLATION_PREFIX + "track.derailment.derailedSlipFactor")
                .defineInRange("derailedSlipFactor", 0.25, 0.0, 1.0);

        DERAIL_COLLISION_SPEED = BUILDER
                .comment("Speed change in blocks per second a Carriage can take from hitting another Train before it leaves the rail.",
                        "The lighter side of a hit takes most of the change, so shunting stays on the rail and a fast hit does not.")
                .translation(TRANSLATION_PREFIX + "track.derailment.collisionDerailSpeed")
                .defineInRange("collisionDerailSpeed", 8.0, 0.0, 1000.0);

        DERAIL_COUPLED_COLLISION = BUILDER
                .comment("Lets two Carriages coupled into the same Train derail each other when they hit.")
                .translation(TRANSLATION_PREFIX + "track.derailment.coupledCollisionDerail")
                .define("coupledCollisionDerail", false);

        BUILDER.pop();

        BUILDER.translation(TRANSLATION_PREFIX + "track.rerail").push("rerail");

        AUTO_RERAIL_ENABLED = BUILDER
                .translation(TRANSLATION_PREFIX + "track.rerail.autoRerailEnabled")
                .define("autoRerailEnabled", true);

        AUTO_RERAIL_MAX_SPEED = BUILDER
                .translation(TRANSLATION_PREFIX + "track.rerail.autoRerailMaxSpeed")
                .defineInRange("autoRerailMaxSpeed", 0.5, 0.0, 1000.0);

        BUILDER.pop();

        BUILDER.pop();

        BUILDER.push("boiler");

        BOILER_REALISM = BUILDER
                .defineEnum("boilerRealism", Realism.REALISTIC);

        AMBIENT_TEMPERATURE_C = BUILDER
                .defineInRange("ambientTemperatureC", 20.0, -100.0, 1000.0);

        WORKING_TEMPERATURE_C = BUILDER
                .defineInRange("workingTemperatureC", 200.0, 0.0, 2000.0);

        WARMUP_TICKS = BUILDER
                .defineInRange("warmupTicks", 1800, 20, 72000);

        SU_PER_BODY_BLOCK = BUILDER
                .defineInRange("suPerBodyBlock", 83.0, 0.0, 100000.0);

        BOILER_MAX_LENGTH = BUILDER
                .comment("Most body blocks a boiler may span along its length before it stops working.",
                        "Longer boilers burn more fuel and draw more water.")
                .defineInRange("maxLength", 8, 1, 256);

        BODY_WATER_CAPACITY = BUILDER
                .defineInRange("bodyWaterCapacity", 8000, 1, 1000000);

        INJECTOR_TANK_CAPACITY = BUILDER
                .defineInRange("injectorTankCapacity", 8000, 1000, 1000000);

        BUILDER.push("pressure");

        PRESSURE_RISE_PER_TICK = BUILDER
                .defineInRange("pressureRisePerTick", 0.0015, 0.0, 1.0);

        BURST_PRESSURE = BUILDER
                .defineInRange("burstPressure", 1.6, 1.0, 10.0);

        DRAFT_CHOKE_TICKS = BUILDER
                .defineInRange("draftChokeTicks", 900, 20, 72000);

        BUILDER.pop();

        BUILDER.push("failure");

        CROWN_SHEET_TICKS = BUILDER
                .defineInRange("crownSheetTicks", 600, 20, 72000);

        FAILURE_EXPLOSION_BASE = BUILDER
                .defineInRange("failureExplosionBase", 3.0, 0.0, 100.0);

        FAILURE_EXPLOSION_MAX = BUILDER
                .defineInRange("failureExplosionMax", 9.0, 0.0, 100.0);

        BUILDER.pop();

        BUILDER.push("crash");

        CRASH_EXPLODE_ENABLED = BUILDER
                .define("explodeOnCrash", true);

        CRASH_IMPACT_THRESHOLD = BUILDER
                .defineInRange("crashImpactThreshold", 4.0, 0.0, 1000.0);

        CRASH_EXPLOSION_POWER = BUILDER
                .defineInRange("explosionPower", 4.0, 0.0, 100.0);

        BUILDER.pop();

        BUILDER.pop();

        BUILDER.translation(TRANSLATION_PREFIX + "blocks").push("blocks");

        BUILDER.translation(TRANSLATION_PREFIX + "blocks.bearing_axle").push("bearing_axle");

        OPPOSED_STRESS_FACTOR = BUILDER
                .comment("Stress multiplier for a Bearing Axle pushing against the rest of its train.",
                        "Raising it makes a fight between two engines expensive instead of merely slow.")
                .translation(TRANSLATION_PREFIX + "blocks.bearing_axle.opposedStressFactor")
                .defineInRange("opposedStressFactor", 1.5, 1.0, 5.0);

        BUILDER.pop();

        BUILDER.translation(TRANSLATION_PREFIX + "blocks.analog_controller").push("analog_controller");

        ANALOG_DECAY_TICKS = BUILDER
                .translation(TRANSLATION_PREFIX + "blocks.analog_controller.decayTicks")
                .defineInRange("decayTicks", 20, 1, 200);

        BUILDER.pop();

        BUILDER.translation(TRANSLATION_PREFIX + "blocks.transmission").push("transmission");

        TRANSMISSION_SU_IMPACT = BUILDER
                .translation(TRANSLATION_PREFIX + "blocks.transmission.suImpact")
                .defineInRange("suImpact", 8.0, 0.0, 100.0);

        BUILDER.pop();

        BUILDER.translation(TRANSLATION_PREFIX + "blocks.turntable_bearing").push("turntable_bearing");

        TURNTABLE_SU_IMPACT = BUILDER
                .comment("Stress the Turntable Bearing costs per RPM of plate speed.",
                        "A turntable driven above its input speed is billed for the speed it turns at, not the speed it is fed.")
                .translation(TRANSLATION_PREFIX + "blocks.turntable_bearing.suImpact")
                .defineInRange("suImpact", 4.0, 0.0, 100.0);

        BUILDER.pop();

        BUILDER.pop();

        BUILDER.translation(TRANSLATION_PREFIX + "items").push("items");
        BUILDER.translation(TRANSLATION_PREFIX + "items.steel_cable").push("steel_cable");

        STEEL_CABLE_MAX_RANGE = BUILDER
                .translation(TRANSLATION_PREFIX + "items.steel_cable.maxRange")
                .defineInRange("maxRange", 64.0, 1.0, 10000.0);

        BUILDER.pop();
        BUILDER.pop();

        BUILDER.translation(TRANSLATION_PREFIX + "couplers").push("couplers");
        BUILDER.translation(TRANSLATION_PREFIX + "couplers.knuckle").push("knuckle");

        KNUCKLE_SCAN_INTERVAL = BUILDER
                .translation(TRANSLATION_PREFIX + "couplers.knuckle.scanInterval")
                .defineInRange("scanInterval", 5, 1, 100);

        BUILDER.pop();
        BUILDER.pop();

        BUILDER.translation(TRANSLATION_PREFIX + "hose").push("hose");

        HOSE_MAX_LENGTH = BUILDER
                .translation(TRANSLATION_PREFIX + "hose.maxLength")
                .defineInRange("maxLength", 28, 1, 256);

        BUILDER.pop();

        BUILDER.translation(TRANSLATION_PREFIX + "explosions").push("explosions");
        BUILDER.translation(TRANSLATION_PREFIX + "explosions.sub_level_push").push("sub_level_push");

        EXPLOSION_SUBLEVEL_PUSH_ENABLED = BUILDER
                .translation(TRANSLATION_PREFIX + "explosions.sub_level_push.subLevelPushEnabled")
                .define("subLevelPushEnabled", true);

        EXPLOSION_SUBLEVEL_PUSH_RADIUS_MULTIPLIER = BUILDER
                .translation(TRANSLATION_PREFIX + "explosions.sub_level_push.subLevelPushRadiusMultiplier")
                .defineInRange("subLevelPushRadiusMultiplier", 3.0, 0.0, 100.0);

        EXPLOSION_SUBLEVEL_PUSH_STRENGTH = BUILDER
                .translation(TRANSLATION_PREFIX + "explosions.sub_level_push.subLevelPushStrength")
                .defineInRange("subLevelPushStrength", 2.0, 0.0, 1000.0);

        EXPLOSION_SUBLEVEL_PUSH_UPWARD = BUILDER
                .translation(TRANSLATION_PREFIX + "explosions.sub_level_push.subLevelPushUpward")
                .defineInRange("subLevelPushUpward", 0.3, 0.0, 10.0);

        BUILDER.pop();

        BUILDER.pop();

        BUILDER.translation(TRANSLATION_PREFIX + "compat").push("compat");
        BUILDER.translation(TRANSLATION_PREFIX + "compat.computercraft").push("computercraft");

        CC_REMOTE_CONTROL = BUILDER
                .comment("Lets a computer drive any train, signal, point or turntable by id.",
                        "Off keeps control to computers inside the train.")
                .translation(TRANSLATION_PREFIX + "compat.computercraft.remoteControl")
                .define("remoteControl", true);

        BUILDER.pop();
        BUILDER.pop();

        if (ModList.get().isLoaded("createbigcannons")) {
            BUILDER.translation(TRANSLATION_PREFIX + "compat").push("compat");
            BUILDER.push("createbigcannons");
            BUILDER.push("derailment");

            CBC_DERAIL_ENABLED = BUILDER
                    .define("derailEnabled", true);

            BUILDER.pop();

            BUILDER.push("recoil");

            CBC_RECOIL_ENABLED = BUILDER
                    .define("recoilEnabled", true);

            BUILDER.pop();

            CBC_GLOBAL_KNOCKBACK_ENABLED = BUILDER
                    .define("globalKnockbackEnabled", false);

            BUILDER.pop();
            BUILDER.pop();
        }

        BUILDER.push("debug");

        DiagnosticsConfig.define(BUILDER);

        BUILDER.pop();

        BUILDER.translation(TRANSLATION_PREFIX + "patch").push("patch");

        enableRollerPatch = BUILDER
                .translation(TRANSLATION_PREFIX + "patch.enableRollerPatch")
                .define("enableRollerPatch", true);

        enablePloughPatch = BUILDER
                .translation(TRANSLATION_PREFIX + "patch.enablePloughPatch")
                .define("enablePloughPatch", true);

        requireMaterialConsumption = BUILDER
                .translation(TRANSLATION_PREFIX + "patch.requireMaterialConsumption")
                .define("requireMaterialConsumption", true);

        BUILDER.pop();
    }

    private static void defineClient(ModConfigSpec.Builder BUILDER) {
        BUILDER.translation(TRANSLATION_PREFIX + "blocks").push("blocks");
        BUILDER.push("smokestack");

        SMOKESTACK_PARTICLES_PER_TICK = BUILDER
                .comment("Smoke particles one smokestack spawns each tick at full power, lower power spawns less.",
                        "Higher is denser smoke and costs fps, most of all with a shader pack.")
                .defineInRange("particlesPerTick", 0.8, 0.1, 2.0);

        SMOKESTACK_ADAPTIVE = BUILDER
                .comment("Spawns less smoke while the fps is under the target and brings it back when the fps recovers.")
                .define("adaptiveParticles", true);

        SMOKESTACK_TARGET_FPS = BUILDER
                .comment("The fps adaptive smoke tries to hold. Higher thins the smoke sooner.")
                .defineInRange("targetFps", 60, 20, 240);

        BUILDER.pop();
        BUILDER.pop();

        BUILDER.translation(TRANSLATION_PREFIX + "gauges").push("gauges");

        TEMPERATURE_UNIT = BUILDER
                .defineEnum("temperatureUnit", TemperatureUnit.CELSIUS);

        DIAL_WOBBLE_DEGREES = BUILDER
                .translation(TRANSLATION_PREFIX + "gauges.dialWobbleDegrees")
                .defineInRange("dialWobbleDegrees", 1.2, 0.0, 20.0);

        BUILDER.pop();
    }

    public static boolean realistic() {
        return BOILER_REALISM.get() == Realism.REALISTIC;
    }

    private LoconauticsConfig() {
    }
}