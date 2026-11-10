package com.lycoris.loconautics.content.boiler.firebox;


import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.common.strand.StrandTemplates;
import com.lycoris.loconautics.config.LoconauticsConfig;
import com.lycoris.loconautics.content.bearingaxle.BearingAxleBlockEntity;
import com.lycoris.loconautics.content.bearingaxle.BearingAxleValidity;
import com.lycoris.loconautics.content.boiler.BoilerBlastEffects;
import com.lycoris.loconautics.content.boiler.BoilerContributions;
import com.lycoris.loconautics.content.boiler.BoilerMultiblockValidator;
import com.lycoris.loconautics.content.boiler.BoilerState;
import com.lycoris.loconautics.content.boiler.BoilerStructure;
import com.lycoris.loconautics.content.boiler.BoilerTags;
import com.lycoris.loconautics.content.boiler.BoilerWaterSource;
import com.lycoris.loconautics.content.boiler.barrel.BarrelConnectorBlockEntity;
import com.lycoris.loconautics.content.boiler.barrel.BoilerBarrelBlockEntity;
import com.lycoris.loconautics.content.boiler.safetyvalve.SafetyValveBlockEntity;
import com.lycoris.loconautics.content.boiler.safetyvalve.SmartSafetyValveBlockEntity;
import com.lycoris.loconautics.content.boiler.steamtakeoff.SteamTakeoffBlockEntity;
import com.lycoris.loconautics.content.hatch.HullsteelSocketBridge;
import com.lycoris.loconautics.content.smokestack.SmokestackBlock;
import com.lycoris.loconautics.content.smokestack.SmokestackBlockEntity;
import com.lycoris.loconautics.foundation.LoconauticsLang;
import com.lycoris.loconautics.foundation.smoke.SmokeOptions;
import com.lycoris.loconautics.foundation.smoke.SmokeStyle;
import com.lycoris.loconautics.trainphysics.fx.ParticleWorldLock;
import com.lycoris.loconautics.trainphysics.portal.PortalCutsClient;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import com.lycoris.loconautics.common.advancement.TrainAdvancements;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class FireboxBlockEntity extends BlockEntity implements IHaveGoggleInformation {

    public static final int FUEL_SLOTS = 1;

    public static final int RESCAN_TICKS = 20;

    public static final double NO_TRAIN = -1.0;

    private final ItemStackHandler inventory = new ItemStackHandler(FUEL_SLOTS) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return isFuel(stack);
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
            if (level != null && !level.isClientSide) refreshGroup();
        }
    };

    private final FluidTank waterTank =
            new FluidTank(0,
                    stack -> stack.getFluid() == Fluids.WATER) {
                @Override
                protected void onContentsChanged() {
                    setChanged();
                }
            };

    private final BoilerWaterSource tankSource = new TankSource();

    private final Map<BlockPos, Integer> trackedBarrels = new HashMap<>();

    private static final double BOILING_C = 100.0;
    private static final float COLD_FIRE_EFFICIENCY = 0.15f;

    private BoilerState boilerState = BoilerState.COLD;
    private int warmupTicks;
    private float burnTimeRemaining;
    private int rescanCooldown;
    private int inflowCooldown;

    private int dryTicks;
    private int wasteGas;
    private boolean lockedOut;

    private FailureCause lastFailure;

    private static final float END_BLAST_SHARE = 0.75f;
    private static final float TAKEOFF_DRAW_MULTIPLIER = 2.5f;
    private static final double END_SHATTER_REACH = 3.0;

    private static final float BLOCKED_SMOKE_MIN_DENSITY = 0.5f;
    private static final float BLOCKED_SMOKE_SCALE = 0.35f;
    private static final int BLOCKED_SMOKE_COLOR = 0x2A2622;

    private static final float LOW_WATER_RISE_BOOST = 1.0f;

    private static final float LOW_WATER_HEAT_SHARE = 0.25f;

    public enum FailureCause {
        CROWN_SHEET, OVERPRESSURE;

        public String langKey() {
            return "gui.goggles.boiler_failure_" + name().toLowerCase(Locale.ROOT);
        }

        public String chatKey() {
            return "loconautics.boiler.cause." + name().toLowerCase(Locale.ROOT);
        }

        public String fixKey() {
            return "loconautics.boiler.fix." + name().toLowerCase(Locale.ROOT);
        }
    }

    private BoilerStructure structure;
    @Nullable private String missingKey;

    public static final int DISPLAY_TICKS = 20;

    private int displayCooldown;
    private int jitterCooldown;
    private boolean dispValid;
    private float dispSu;
    private float dispRpm;
    private float dispEfficiency;
    private boolean dispOverfired;
    private int dispWater;
    private int dispWasteGas;
    private int dispRemainingSeconds;
    private float dispTemperatureJitter;
    private float dispWaterJitter;
    private float dispPressure;
    private float dispMaxSu;
    private int dispWaterCapacity;
    @Nullable private String dispStep;
    @Nullable private String dispOptionalStep;

    private float steamPressure;
    private boolean lastBurning;
    private float lastBurnRate;
    private float lastStressRatio;
    private double lastTrainMass = NO_TRAIN;
    private int lastWaterDrawn;
    private float lastSubSpeed;
    private boolean inSubLevel;

    public static final int DOOR_SWING_TICKS = 10;

    private static final float DOOR_SWING_SPEED = 1.35f;

    private static final double DOOR_REACH_PADDING = 0.7;

    private boolean manualDoorsOpen;
    private BlockPos controllerPos;

    private boolean doorsSwingingOpen;
    private float doorOpenTime;
    private float lastDoorOpenTime;

    public FireboxBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public static boolean isFuel(ItemStack stack) {
        return !stack.isEmpty() && stack.getBurnTime(RecipeType.SMELTING) > 0;
    }

    public boolean toggleDoors() {
        manualDoorsOpen = !manualDoorsOpen;
        setChanged();
        sendData();
        return manualDoorsOpen;
    }

    public boolean areDoorsManuallyOpen() {
        return manualDoorsOpen;
    }

    public void clientTick() {
        if (level == null) return;

        if (--jitterCooldown <= 0) {
            jitterCooldown = DISPLAY_TICKS;
            dispWaterJitter = rollWaterJitter();
        }

        lastDoorOpenTime = doorOpenTime;
        boolean open = manualDoorsOpen || stoker() != null;
        if (open != doorsSwingingOpen) {
            level.playLocalSound(worldPosition,
                    open ? SoundEvents.IRON_TRAPDOOR_OPEN
                            : SoundEvents.IRON_TRAPDOOR_CLOSE,
                    SoundSource.BLOCKS, 0.7f, 1.1f, false);
        }
        doorsSwingingOpen = open;
        spewSmoke();
        float dir = open ? 1f : -1f;
        doorOpenTime = Math.max(0f, Math.min(DOOR_SWING_TICKS, doorOpenTime + dir * DOOR_SWING_SPEED));
    }

    private void spewSmoke() {
        BlockState state = getBlockState();
        if (!state.getValue(BlockStateProperties.LIT) || dispWasteGas <= 0) return;
        float choke = Math.min(1f, dispWasteGas / (float) LoconauticsConfig.DRAFT_CHOKE_TICKS.get());
        float density = BLOCKED_SMOKE_MIN_DENSITY + (1f - BLOCKED_SMOKE_MIN_DENSITY) * choke;
        Direction face = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
        Vec3 mouth = Vec3.atCenterOf(worldPosition).add(face.getStepX() * 0.6, -0.1, face.getStepZ() * 0.6);
        if (PortalCutsClient.hiddenAtPlot(mouth.x, mouth.y, mouth.z)) {
            return;
        }
        Vec3 world = Sable.HELPER.projectOutOfSubLevel(level, mouth);
        Vec3 outward = Sable.HELPER.projectOutOfSubLevel(level, mouth.add(face.getStepX(), 0.0, face.getStepZ()))
                .subtract(world);
        for (int i = 0; i < 2; i++) {
            if (level.random.nextFloat() > density) continue;
            ParticleWorldLock.addParticle(level, new SmokeOptions(BLOCKED_SMOKE_COLOR, SmokeStyle.LEAK, BLOCKED_SMOKE_SCALE),
                    world.x + (level.random.nextDouble() - 0.5) * 0.3,
                    world.y + level.random.nextDouble() * 0.3,
                    world.z + (level.random.nextDouble() - 0.5) * 0.3,
                    outward.x * 0.03, 0.0, outward.z * 0.03);
        }
    }

    public float getDoorOpenTime(float partialTicks) {
        return Mth.lerp(partialTicks, lastDoorOpenTime, doorOpenTime);
    }

    @Nullable
    private Player stoker() {
        Vec3 center = worldPosition.getCenter();
        for (Player player : level.getEntitiesOfClass(Player.class,
                new AABB(worldPosition).inflate(8.0))) {
            if (!isFuel(player.getMainHandItem()) && !isFuel(player.getOffhandItem())) continue;
            double reach = player.getAttributeValue(Attributes
                    .BLOCK_INTERACTION_RANGE) + DOOR_REACH_PADDING;
            if (player.getEyePosition().distanceToSqr(center) < reach * reach) return player;
        }
        return null;
    }

    public void tick() {
        if (level == null || level.isClientSide) return;

        if (--rescanCooldown <= 0) {
            rescanCooldown = RESCAN_TICKS;
            rescan();
        }

        readSubLevelSpeed();

        if (lockedOut || structure == null) {
            forceCold();
            return;
        }

        readAxle();

        drainInlets();
        boolean burning = consumeFuel();
        boolean watered = drawWater();

        tickStateMachine(burning, watered);
        if (tickPressure(burning && watered)) return;
        tickWasteGas(burning);
        if (tickFailure(watered)) return;
        updateLit(burning);
        tickDisplay();
    }

    private void rescan() {
        BoilerMultiblockValidator.Scan result = BoilerMultiblockValidator.scan(level, worldPosition);
        boolean wasValid = structure != null;
        structure = result.structure();
        missingKey = result.missingKey();
        reconcileBarrels();

        if (structure == null) {
            boilerState = BoilerState.COLD;
            warmupTicks = 0;
        } else {
            tankSource.setWaterCapacity(structure.waterCapacity());
            reclaimParkedWater();
            resizeFuel(Math.max(1, structure.fireboxPositions().size()));
        }

        if (wasValid == (structure == null)) {
            setChanged();
            sendData();
        }
    }

    @Nullable
    private String computeNextStep() {
        if (lockedOut) return null;
        if (structure == null) return missingKey == null ? "add_boiler_body" : missingKey;
        if (!structure.hasWaterInlet()) return "add_water_inlet";
        if (!structure.hasTakeoff()) return "add_steam_takeoff";
        if (!structure.hasStack()) return "add_smokestack";
        BoilerWaterSource injector = water();
        if (injector == null || injector.getWaterAmount() <= 0) return "add_water";
        if (burnTimeRemaining <= 0f && firstFuelSlot() < 0) return "add_fuel";
        return null;
    }

    @Nullable
    public String nextStepKey() {
        return dispStep == null || dispStep.isEmpty() ? null : dispStep;
    }

    @Nullable
    private String computeOptionalStep() {
        if (lockedOut || structure == null) return null;
        if (boilerState == BoilerState.WARMING || boilerState == BoilerState.HOT) return null;
        return structure.hasSafetyValve() || hasBoundValve() ? null : "add_safety_valve";
    }

    @Nullable
    private SafetyValveBlockEntity safetyValve() {
        if (structure == null || structure.safetyValvePos() == null || level == null) return null;
        return level.getBlockEntity(structure.safetyValvePos())
                instanceof SafetyValveBlockEntity valve ? valve : null;
    }

    public boolean hasBoundValve() {
        return safetyValve() != null;
    }

    public float valveOpening() {
        SafetyValveBlockEntity valve = safetyValve();
        return valve == null ? 0f : valve.opening();
    }

    public float steamPressure() {
        return steamPressure;
    }

    private boolean tickPressure(boolean fed) {
        float burst = (float) (double) LoconauticsConfig.BURST_PRESSURE.get();
        SafetyValveBlockEntity valve = safetyValve();
        if (valve != null) valve.setVenting(steamPressure > 0f);
        if (valve instanceof SmartSafetyValveBlockEntity smart) {
            smart.updateFromBoiler(burst <= 0f ? 0f : steamPressure / burst);
        }
        float rise = fed
                ? (float) (double) LoconauticsConfig.PRESSURE_RISE_PER_TICK.get()
                        * structure.temperatureCap() * draftChoke() * warmthEfficiency()
                        * (1f + LOW_WATER_RISE_BOOST * lowWater(waterTank.getFluidAmount(), waterTank.getCapacity()))
                : 0f;
        float vent = (float) (double) LoconauticsConfig.PRESSURE_VENT_PER_TICK.get() * valveOpening();
        float bleed = fed ? 0f : idleBleed();
        float draw = takeoffDraw();
        float before = steamPressure;
        steamPressure = Math.max(0f, steamPressure + rise - vent - bleed - draw);
        if (steamPressure < before && level.getGameTime() % 20 == 0) {
            LoconauticsConstants.LOGGER.info("[boilerfix] bleed pos={} fed={} before={} after={} rise={} vent={} bleed={} draw={} hasTakeoff={} hasValve={} boundValve={} valveOpening={}",
                    worldPosition, fed, before, steamPressure, rise, vent, bleed, draw,
                    structure.hasTakeoff(), structure.hasSafetyValve(), hasBoundValve(), valveOpening());
        }

        if (steamPressure >= (float) (double) LoconauticsConfig.BURST_PRESSURE.get()) {
            steamPressure = 0f;
            fail(worldPosition, FailureCause.OVERPRESSURE);
            return true;
        }
        return false;
    }

    private float takeoffDraw() {
        float maxRpm = (float) (double) LoconauticsConfig.TAKEOFF_MAX_RPM.get();
        if (maxRpm <= 0f) return 0f;
        float load = Math.max(0f, Math.min(1f, lastStressRatio)) * Math.min(1f, shaftRpm() / maxRpm);
        return (float) (double) LoconauticsConfig.PRESSURE_RISE_PER_TICK.get() * load * TAKEOFF_DRAW_MULTIPLIER;
    }

    private static float lowWater(int amount, int capacity) {
        if (capacity <= 0) return 0f;
        return 1f - Math.min(1f, Math.max(0, amount) / (float) capacity);
    }

    private float idleBleed() {
        boolean canLose = structure != null
                && (structure.hasTakeoff() || structure.hasSafetyValve() || hasBoundValve());
        return canLose ? (float) (double) LoconauticsConfig.PRESSURE_IDLE_BLEED_PER_TICK.get() : 0f;
    }

    @Nullable
    public String optionalStepKey() {
        return dispOptionalStep == null || dispOptionalStep.isEmpty() ? null : dispOptionalStep;
    }

    private void forceCold() {
        steamPressure = Math.max(0f, steamPressure - idleBleed());
        lastBurning = false;
        ventWasteGas();
        tickDisplay();
        if (boilerState == BoilerState.COLD && warmupTicks == 0) return;
        boilerState = BoilerState.COLD;
        warmupTicks = 0;
        updateLit(false);
        setChanged();
        sendData();
    }

    private float burnRate() {
        double mass = Math.max(0.0, lastTrainMass);
        float stress = Math.max(0f, Math.min(1f, lastStressRatio));
        float load = 1f
                + stress * (float) (double) LoconauticsConfig.BURN_STRESS_FACTOR.get()
                + (float) (mass / LoconauticsConfig.BURN_MASS_DIVISOR.get());
        return load * structure.burnMultiplier()
                * (float) (double) LoconauticsConfig.BURN_RATE_SCALE.get();
    }

    private boolean consumeFuel() {
        lastBurnRate = burnRate();
        lastBurning = true;

        if (burnTimeRemaining > 0f) {
            burnTimeRemaining = Math.max(0f, burnTimeRemaining - lastBurnRate);
            setChanged();
            return true;
        }

        int slot = firstFuelSlot();
        if (slot < 0) {
            lastBurning = false;
            return false;
        }

        int burnTime = inventory.getStackInSlot(slot).getBurnTime(RecipeType.SMELTING);
        if (burnTime <= 0) burnTime = 1600;
        burnTimeRemaining = burnTime - lastBurnRate;
        inventory.extractItem(slot, 1, false);
        setChanged();
        return true;
    }

    @Nullable
    private BoilerWaterSource socket() {
        if (structure == null || structure.injectorPos() == null) return null;
        net.minecraft.world.level.block.entity.BlockEntity be = level.getBlockEntity(structure.injectorPos());
        if (be instanceof BoilerWaterSource socket) return socket;
        return be == null ? null : HullsteelSocketBridge.waterSource(be);
    }

    @Nullable
    private BoilerWaterSource water() {
        return structure == null ? null : tankSource;
    }

    private void drainInlets() {
        if (inflowCooldown > 0) inflowCooldown--;
        if (structure == null) return;
        int room = waterTank.getSpace();
        if (room <= 0) return;

        for (BlockPos connector : structure.connectorPositions()) {
            if (room <= 0) break;
            if (level.getBlockEntity(connector)
                    instanceof BarrelConnectorBlockEntity be) {
                room -= acceptWater(be.drainBuffer(room));
            }
        }

        BoilerWaterSource socket = socket();
        if (socket != null && room > 0) {
            acceptWater(socket.drawWater(room));
        }
    }

    private int acceptWater(int millibuckets) {
        if (millibuckets <= 0) return 0;
        inflowCooldown = DISPLAY_TICKS;
        return waterTank.fill(new FluidStack(
                        Fluids.WATER, millibuckets),
                IFluidHandler.FluidAction.EXECUTE);
    }

    public FluidTank getWaterTank() {
        return waterTank;
    }

    public int fillFromHose(FluidStack resource, IFluidHandler.FluidAction action) {
        if (structure == null || resource.getFluid() != Fluids.WATER) return 0;
        int filled = waterTank.fill(resource, action);
        if (filled > 0 && action.execute()) inflowCooldown = DISPLAY_TICKS;
        return filled;
    }

    private boolean drawWater() {
        BoilerWaterSource injector = water();
        if (injector == null) {
            lastWaterDrawn = 0;
            return false;
        }

        float perTick = (float) (double) LoconauticsConfig.WATER_PER_BODY_PER_TICK.get();
        int wanted = Math.round(structure.boilerCapacity() * perTick * outputFraction());
        if (wanted <= 0) {
            lastWaterDrawn = 0;
            return injector.getWaterAmount() > 0;
        }

        int drawn = injector.drawWater(wanted);
        lastWaterDrawn = drawn;
        if (drawn <= 0) {
            return false;
        }

        float recycle = (float) (double) LoconauticsConfig.WATER_RECYCLE_FRACTION.get();
        injector.returnWater((int) (drawn * recycle));
        return true;
    }

    private void tickStateMachine(boolean burning, boolean watered) {
        BoilerState prev = boilerState;
        int warmupTarget = LoconauticsConfig.WARMUP_TICKS.get();
        boolean fed = burning && watered;

        switch (boilerState) {
            case COLD -> {
                if (fed) {
                    boilerState = BoilerState.WARMING;
                }
            }
            case WARMING -> {
                if (fed) {
                    warmupTicks = Math.min(warmupTarget, warmupTicks + 1);
                    if (warmupTicks >= warmupTarget) boilerState = BoilerState.HOT;
                } else if (burning || watered) {
                    boilerState = BoilerState.STARVED;
                } else {
                    coolDown();
                }
            }
            case HOT -> {
                if (!fed) boilerState = BoilerState.STARVED;
            }
            case STARVED -> {
                if (fed) {
                    boilerState = BoilerState.WARMING;
                } else if (burning) {
                    warmupTicks = Math.min(warmupTarget, warmupTicks + 1);
                } else {
                    coolDown();
                }
            }
        }

        if (boilerState != prev) {
            setChanged();
            sendData();
        }
    }

    private void coolDown() {
        warmupTicks = Math.max(0, warmupTicks - 1);
        if (warmupTicks == 0) boilerState = BoilerState.COLD;
    }

    public float outputFraction() {
        if (structure == null || lockedOut) return 0f;

        float raw = Math.min(1f, steamPressure);
        return Math.min(raw, structure.temperatureCap());
    }

    private float draftChoke() {
        float full = LoconauticsConfig.DRAFT_CHOKE_TICKS.get();
        return Math.max(0f, 1f - wasteGas / full);
    }

    public static float quantize4(float value) {
        return Math.round(value / 4f) * 4f;
    }

    public float shaftRpm() {
        if (structure == null || lockedOut) return 0f;
        float fraction = outputFraction();
        if (fraction <= 0f) return 0f;
        float min = (float) (double) LoconauticsConfig.TAKEOFF_MIN_RPM.get();
        float max = (float) (double) LoconauticsConfig.TAKEOFF_MAX_RPM.get();
        return quantize4((min + (max - min) * fraction) * takeoffRegulator());
    }

    private float takeoffRegulator() {
        if (structure == null || structure.takeoffPos() == null) return 1f;
        return level.getBlockEntity(structure.takeoffPos()) instanceof SteamTakeoffBlockEntity takeoff
                ? takeoff.regulator() : 1f;
    }

    public int remainingSeconds() {
        if (structure == null || lastBurnRate <= 0f) return 0;
        float queued = 0f;
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            ItemStack fuel = inventory.getStackInSlot(slot);
            if (isFuel(fuel)) queued += (float) fuel.getBurnTime(RecipeType.SMELTING) * fuel.getCount();
        }
        float ticks = (burnTimeRemaining + queued) / lastBurnRate;
        return Math.round(ticks / 20f);
    }

    public static String formatTime(int sec) {
        int min = sec / 60;
        int hour = min / 60;
        sec = Math.floorMod(sec, 60);
        min = Math.floorMod(min, 60);
        StringBuilder out = new StringBuilder();
        if (hour > 0) out.append(hour).append("h ");
        if (min < 10 && hour > 0) out.append('0');
        if (min > 0 || hour > 0) out.append(min).append("m ");
        if (sec < 10 && (min > 0 || hour > 0)) out.append('0');
        return out.append(sec).append('s').toString();
    }

    public float temperatureFraction() {
        int target = LoconauticsConfig.WARMUP_TICKS.get();
        return target <= 0 ? 0f : warmupTicks / (float) target;
    }

    private static float boilingFraction() {
        double ambient = LoconauticsConfig.AMBIENT_TEMPERATURE_C.get();
        double working = LoconauticsConfig.WORKING_TEMPERATURE_C.get();
        if (working <= BOILING_C) return 0f;
        double ratio = Math.max(0.0, (BOILING_C - ambient) / (working - ambient));
        return (float) (ratio * ratio);
    }

    private float warmthEfficiency() {
        float boiling = boilingFraction();
        float t = temperatureFraction();
        if (t < boiling) return 0f;
        if (boiling >= 1f) return 1f;
        float span = Math.min(1f, (t - boiling) / (1f - boiling));
        return COLD_FIRE_EFFICIENCY + (1f - COLD_FIRE_EFFICIENCY) * span;
    }

    public float steamTemperatureCelsius() {
        double ambient = LoconauticsConfig.AMBIENT_TEMPERATURE_C.get();
        double working = LoconauticsConfig.WORKING_TEMPERATURE_C.get();
        double curve = Math.sqrt(Math.max(0f, temperatureFraction()));
        double lowWaterHeat = 1.0 + LOW_WATER_HEAT_SHARE * lowWater(dispWater, dispWaterCapacity);
        return (float) (ambient + (working - ambient) * curve * lowWaterHeat) + dispTemperatureJitter;
    }

    private float rollTemperatureJitter() {
        float amplitude = (float) (double) LoconauticsConfig.TEMPERATURE_JITTER_C.get();
        if (amplitude <= 0f || level == null) return 0f;
        float triangular = level.random.nextFloat() - level.random.nextFloat();
        return triangular * amplitude * temperatureFraction();
    }

    private float rollWaterJitter() {
        float amplitude = (float) (double) LoconauticsConfig.WATER_JITTER_MB.get();
        if (amplitude <= 0f || level == null) return 0f;
        if (dispWater <= 0 || inflowCooldown <= 0) return 0f;
        float triangular = level.random.nextFloat() - level.random.nextFloat();
        return triangular * amplitude * temperatureFraction();
    }

    public static String formattedTemperature(FireboxBlockEntity be) {
        float celsius = be.steamTemperatureCelsius();
        return format(celsius);
    }

    private static String format(float celsius) {
        return switch (LoconauticsConfig.TEMPERATURE_UNIT.get()) {
            case FAHRENHEIT -> String.format(Locale.ROOT, "%.0f F", celsius * 9f / 5f + 32f);
            case KELVIN -> String.format(Locale.ROOT, "%.0f K", celsius + 273.15f);
            case CELSIUS -> String.format(Locale.ROOT, "%.0f C", celsius);
        };
    }

    public String temperatureFeelKey() {
        float t = temperatureFraction();
        if (t < 0.33f) return "cold";
        if (t < 0.66f) return "warm";
        return "hot";
    }

    public float maxPossibleSu() {
        if (structure == null) return 0f;
        return structure.boilerCapacity()
                * (float) (double) LoconauticsConfig.SU_PER_BODY_BLOCK.get()
                * (float) (double) LoconauticsConfig.TAKEOFF_MAX_RPM.get();
    }

    public int getWaterCapacity() {
        return Math.max(0, dispWaterCapacity);
    }

    public boolean isDisplayValid() { return dispValid; }
    public float getDisplayPressure() { return dispPressure; }
    public float getDisplayMaxSu() { return dispMaxSu; }
    public float getDisplaySu() { return dispSu; }
    public float getDisplayRpm() { return dispRpm; }
    public float getDisplayEfficiency() { return dispEfficiency; }
    public int getDisplayWater() {
        int capacity = getWaterCapacity();
        return Math.max(0, Math.min(capacity, Math.round(dispWater + dispWaterJitter)));
    }
    public int getDisplayRemaining() { return dispRemainingSeconds; }
    public int getDisplayWasteGas() { return dispWasteGas; }
    public float getLastStressRatio() { return lastStressRatio; }
    public double getLastTrainMass() { return lastTrainMass; }
    public boolean onATrain() { return lastTrainMass >= 0.0; }
    public float getSubLevelSpeed() { return lastSubSpeed; }
    public boolean inSubLevel() { return inSubLevel; }

    public float suOutput() {
        if (structure == null) return 0f;
        return quantize4(structure.boilerCapacity()
                * (float) (double) LoconauticsConfig.SU_PER_BODY_BLOCK.get()
                * shaftRpm());
    }

    private void tickWasteGas(boolean burning) {
        if (burning && !hasStack()) {
            wasteGas = Math.min(wasteGas + 1, LoconauticsConfig.DRAFT_CHOKE_TICKS.get());
        } else {
            ventWasteGas();
        }
    }

    private void ventWasteGas() {
        wasteGas = Math.max(0, wasteGas - 2);
    }

    private void tickDisplay() {
        if (--displayCooldown > 0) return;
        displayCooldown = DISPLAY_TICKS;

        BoilerWaterSource injector = water();

        dispValid = structure != null;
        dispSu = suOutput();
        dispRpm = shaftRpm();
        dispEfficiency = structure == null ? 0f : Math.min(1f, structure.heatRatio()) * warmthEfficiency();
        dispOverfired = structure != null && structure.isOverfired();
        dispWater = injector == null ? 0 : injector.getWaterAmount();
        dispWasteGas = wasteGas;
        dispStep = computeNextStep();
        dispOptionalStep = computeOptionalStep();
        dispRemainingSeconds = remainingSeconds();
        dispTemperatureJitter = rollTemperatureJitter();
        float burstPressure = (float) (double) LoconauticsConfig.BURST_PRESSURE.get();
        dispPressure = burstPressure <= 0f ? 0f : steamPressure / burstPressure;
        dispMaxSu = maxPossibleSu();
        dispWaterCapacity = structure == null ? 0 : structure.waterCapacity();

        pushTakeoff();
        pushSmokestack();
        setChanged();
        sendData();
    }

    private boolean hasStack() {
        return structure != null && structure.hasStack();
    }

    private void pushSmokestack() {
        BlockPos base = structure == null ? null : structure.stackPos();
        if (base == null) return;
        BlockPos top = base;
        while (top.getY() - base.getY() + 1 < SmokestackBlock.MAX_HEIGHT && top.getY() < level.getMaxBuildHeight() - 1
                && level.getBlockState(top.above()).is(BoilerTags.SMOKESTACK)) {
            top = top.above();
        }
        int power = lastBurning ? Math.max(1, Math.round(temperatureFraction() * 15f)) : 0;
        for (BlockPos at = base; at.getY() <= top.getY(); at = at.above()) {
            if (level.getBlockEntity(at) instanceof SmokestackBlockEntity stack) {
                stack.setBoilerDriven(true);
                stack.setPower(at.equals(top) ? power : 0);
                stack.setColor(smokeColor());
            }
        }
    }

    private int smokeColor() {
        return switch (boilerState) {
            case COLD -> 0x201C18;
            case WARMING, HOT -> blendSmoke(warmthEfficiency());
            case STARVED -> 0x3A3430;
        };
    }

    private static int blendSmoke(float t) {
        int dark = 0x201C18;
        int light = 0xF0F0F0;
        int out = 0;
        for (int shift = 16; shift >= 0; shift -= 8) {
            int a = (dark >> shift) & 0xFF;
            int b = (light >> shift) & 0xFF;
            out |= Math.round(a + (b - a) * t) << shift;
        }
        return out;
    }

    private void pushTakeoff() {
        if (structure == null || structure.takeoffPos() == null) return;
        if (level.getBlockEntity(structure.takeoffPos()) instanceof SteamTakeoffBlockEntity takeoff) {
            takeoff.supply(shaftRpm(), structure.boilerCapacity());
        }
    }

    private boolean tickFailure(boolean watered) {
        boolean dryAndHot = !watered && warmupTicks > 0;
        dryTicks = dryAndHot ? dryTicks + 1 : Math.max(0, dryTicks - 2);

        if (dryTicks >= LoconauticsConfig.CROWN_SHEET_TICKS.get()) {
            fail(worldPosition, FailureCause.CROWN_SHEET);
            return true;
        }
        return false;
    }

    private void announceFailure(BlockPos at, FailureCause cause) {
        if (!(level instanceof ServerLevel server)) return;
        Component message = Component.translatable("loconautics.boiler.failed",
                        Component.translatable(cause.chatKey()).withStyle(ChatFormatting.RED))
                .withStyle(ChatFormatting.GOLD)
                .append(Component.literal(" "))
                .append(Component.translatable(cause.fixKey()).withStyle(ChatFormatting.GRAY));
        for (ServerPlayer player : server.players()) {
            if (player.blockPosition().distSqr(at) < 64 * 64) player.sendSystemMessage(message);
        }
    }

    private void fail(BlockPos at, FailureCause cause) {
        announceFailure(at, cause);
        lastFailure = cause;
        LoconauticsConstants.LOGGER.info("[boilerfix] fail pos={} cause={} structureNull={} pressure={} realistic={}",
                worldPosition, cause, structure == null, steamPressure, LoconauticsConfig.realistic());
        float burstPressure = (float) (double) LoconauticsConfig.BURST_PRESSURE.get();
        float pressureFraction = cause == FailureCause.OVERPRESSURE || burstPressure <= 0f
                ? 1f : Math.min(1f, steamPressure / burstPressure);
        steamPressure = 0f;
        dryTicks = 0;
        warmupTicks = 0;
        boilerState = BoilerState.COLD;

        if (!LoconauticsConfig.realistic()) {
            lockedOut = true;
            setChanged();
            sendData();
            return;
        }

        float base = (float) (double) LoconauticsConfig.FAILURE_EXPLOSION_BASE.get();
        float max = (float) (double) LoconauticsConfig.FAILURE_EXPLOSION_MAX.get();
        // an explosion can hand the controller to a neighbor firebox, which nulls structure mid failure
        List<BlockPos> bodies = List.copyOf(structure.boilerPositions());
        float scale = Math.min(1f, structure.boilerCapacity() / 24f);
        float power = base + (max - base) * scale;

        Vec3 middle = bodyCenter(bodies);
        if (cause == FailureCause.OVERPRESSURE) {
            Vec3 back = Vec3.atCenterOf(at);
            Vec3 front = Vec3.atCenterOf(frontEnd(bodies, back));
            explodeOutward(back, middle, power * END_BLAST_SHARE);
            explodeOutward(front, middle, power * END_BLAST_SHARE);
            shatterBodyNear(bodies, back, END_SHATTER_REACH);
            shatterBodyNear(bodies, front, END_SHATTER_REACH);
            level.destroyBlock(at, false);
            level.destroyBlock(BlockPos.containing(front), false);
            if (level instanceof ServerLevel server) TrainAdvancements.boilerBurst(server, at);
        } else {
            float scaled = base * 0.5f + (power - base * 0.5f) * pressureFraction;
            level.explode(null, middle.x, middle.y, middle.z, scaled, Level.ExplosionInteraction.BLOCK);
            if (level instanceof ServerLevel server) BoilerBlastEffects.burst(server, middle, scaled, pressureFraction);
            shatterBodyNear(bodies, middle, Double.POSITIVE_INFINITY);
            level.destroyBlock(at, false);
        }
        setChanged();
        sendData();
    }

    private void shatterBodyNear(List<BlockPos> bodies, Vec3 center, double reach) {
        double reachSqr = reach * reach;
        for (BlockPos body : bodies) {
            if (Vec3.atCenterOf(body).distanceToSqr(center) <= reachSqr) level.destroyBlock(body, false);
        }
    }

    private Vec3 bodyCenter(List<BlockPos> bodies) {
        Vec3 sum = Vec3.ZERO;
        for (BlockPos body : bodies) {
            sum = sum.add(Vec3.atCenterOf(body));
        }
        return sum.scale(1.0 / bodies.size());
    }

    private BlockPos frontEnd(List<BlockPos> bodies, Vec3 back) {
        BlockPos front = bodies.getFirst();
        for (BlockPos body : bodies) {
            if (Vec3.atCenterOf(body).distanceToSqr(back) > Vec3.atCenterOf(front).distanceToSqr(back)) front = body;
        }
        return front;
    }

    private void explodeOutward(Vec3 end, Vec3 middle, float power) {
        Vec3 away = end.subtract(middle);
        Vec3 at = away.lengthSqr() < 1.0E-6 ? end : end.add(away.normalize().scale(0.5));
        level.explode(null, at.x, at.y, at.z, power, Level.ExplosionInteraction.BLOCK);
        if (level instanceof ServerLevel server) BoilerBlastEffects.jet(server, at, away, power);
    }

    public boolean isLockedOut() {
        return lockedOut;
    }

    public void clearLockout() {
        if (!lockedOut) return;
        lockedOut = false;
        dryTicks = 0;
        setChanged();
        sendData();
    }

    private void readSubLevelSpeed() {
        SubLevel sub = Sable.HELPER.getContaining(this);
        inSubLevel = sub != null;
        if (sub == null) {
            lastSubSpeed = 0f;
            return;
        }
        org.joml.Vector3d center = new org.joml.Vector3d(
                worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5);
        org.joml.Vector3d now = sub.logicalPose().transformPosition(center, new org.joml.Vector3d());
        org.joml.Vector3d before = sub.lastPose().transformPosition(center, new org.joml.Vector3d());
        lastSubSpeed = (float) (now.sub(before).length() * 20.0);
    }

    private void readAxle() {
        lastStressRatio = readNetworkStress();
        lastTrainMass = readTrainMass();
    }

    private float readNetworkStress() {
        if (structure == null || structure.takeoffPos() == null) return 0f;
        return level.getBlockEntity(structure.takeoffPos()) instanceof SteamTakeoffBlockEntity takeoff
                ? takeoff.networkStressRatio() : 0f;
    }

    private double readTrainMass() {
        if (!(level instanceof ServerLevel)) return NO_TRAIN;
        var sub = Sable.HELPER.getContaining(this);
        if (!(sub instanceof ServerSubLevel serverSub)) return NO_TRAIN;
        BearingAxleBlockEntity axle = BearingAxleValidity.resolve(serverSub);
        return axle == null ? NO_TRAIN : axle.getTrainMass();
    }

    @Nullable
    public BlockPos getControllerPos() {
        return controllerPos;
    }

    public void setControllerPos(@Nullable BlockPos pos) {
        BlockPos next = pos == null ? null : pos.immutable();
        if (Objects.equals(controllerPos, next)) return;
        controllerPos = next;
        setChanged();
        sendData();
    }

    public boolean insertFuel(Player player, ItemStack held) {
        if (!isFuel(held)) return false;
        ItemStack leftover = ItemHandlerHelper.insertItem(inventory, held.copy(), false);
        if (leftover.getCount() == held.getCount()) return false;
        if (!player.isCreative()) held.setCount(leftover.getCount());
        return true;
    }

    private void resizeFuel(int slots) {
        if (inventory.getSlots() == slots) return;
        List<ItemStack> kept = new ArrayList<>();
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            kept.add(inventory.getStackInSlot(slot));
        }
        inventory.setSize(slots);
        for (int slot = 0; slot < kept.size(); slot++) {
            ItemStack stack = kept.get(slot);
            if (stack.isEmpty()) continue;
            if (slot < slots) {
                inventory.setStackInSlot(slot, stack);
            } else {
                spill(stack);
            }
        }
        setChanged();
    }

    private void spill(ItemStack stack) {
        if (level == null || stack.isEmpty()) return;
        Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY() + 1,
                worldPosition.getZ(), stack);
    }

    private int firstFuelSlot() {
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            if (isFuel(inventory.getStackInSlot(slot))) return slot;
        }
        return -1;
    }

    public ItemStack firstFuel() {
        int slot = firstFuelSlot();
        return slot < 0 ? ItemStack.EMPTY : inventory.getStackInSlot(slot);
    }

    public int fuelCapacity() {
        ItemStack fuel = firstFuel();
        int perSlot = Math.min(inventory.getSlotLimit(0),
                fuel.isEmpty() ? Item.DEFAULT_MAX_STACK_SIZE : fuel.getMaxStackSize());
        return inventory.getSlots() * perSlot;
    }

    public int fuelHeld() {
        int held = 0;
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            held += inventory.getStackInSlot(slot).getCount();
        }
        return held;
    }

    public void refreshGroup() {
        if (level != null) FireboxGroups.rerender(level, worldPosition);
    }

    public void dropFuel() {
        if (level == null) return;
        // extractitem never hands back more than one vanilla stack, and a wide firebox holds several
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            ItemStack held = inventory.getStackInSlot(slot).copy();
            inventory.setStackInSlot(slot, ItemStack.EMPTY);
            spill(held);
        }
    }

    public void parkWaterInBarrels() {
        if (level == null) return;
        int totalCapacity = trackedCapacity();
        int water = waterTank.getFluidAmount();
        for (Map.Entry<BlockPos, Integer> barrel : trackedBarrels.entrySet()) {
            if (level.getBlockEntity(barrel.getKey()) instanceof BoilerBarrelBlockEntity be) {
                be.storeWater(shareOf(water, barrel.getValue(), totalCapacity));
            }
        }
        trackedBarrels.clear();
        waterTank.setFluid(FluidStack.EMPTY);
    }

    private void reconcileBarrels() {
        Set<BlockPos> current = structure == null ? Set.of() : new HashSet<>(structure.boilerPositions());
        int totalCapacity = trackedCapacity();
        int water = waterTank.getFluidAmount();
        int leaving = 0;
        Iterator<Map.Entry<BlockPos, Integer>> it = trackedBarrels.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<BlockPos, Integer> barrel = it.next();
            BlockPos pos = barrel.getKey();
            if (current.contains(pos) || !level.isLoaded(pos)) continue;
            int share = shareOf(water, barrel.getValue(), totalCapacity);
            if (!level.getBlockState(pos).is(BoilerTags.BOILER_BODY)) {
                leaving += share;
            } else if (level.getBlockEntity(pos) instanceof BoilerBarrelBlockEntity be) {
                be.storeWater(share);
                leaving += share;
            }
            it.remove();
        }
        if (leaving > 0) waterTank.drain(leaving, IFluidHandler.FluidAction.EXECUTE);
        for (BlockPos pos : current) {
            trackedBarrels.put(pos, BoilerContributions.waterCapacityOf(level.getBlockState(pos)));
        }
        setChanged();
    }

    private int trackedCapacity() {
        int total = 0;
        for (int capacity : trackedBarrels.values()) total += capacity;
        return total;
    }

    private static int shareOf(int water, int capacity, int totalCapacity) {
        return totalCapacity <= 0 ? 0 : (int) ((long) water * capacity / totalCapacity);
    }

    private void reclaimParkedWater() {
        for (BlockPos pos : structure.boilerPositions()) {
            int room = waterTank.getSpace();
            if (room <= 0) return;
            if (level.getBlockEntity(pos) instanceof BoilerBarrelBlockEntity barrel) {
                int taken = barrel.takeWater(room);
                if (taken > 0) waterTank.fill(new FluidStack(Fluids.WATER, taken), IFluidHandler.FluidAction.EXECUTE);
            }
        }
    }

    public static void transferControllerData(FireboxBlockEntity oldController, Level level,
                                                BlockPos oldPos, BlockPos newPos, Direction face) {
        if (!(level.getBlockEntity(newPos) instanceof FireboxBlockEntity newController)) return;
        newController.copyControllerDataFrom(oldController);
        oldController.resetControllerData();
        newController.rescanCooldown = 0;
        newController.setChanged();
        newController.sendData();
        oldController.setChanged();
        oldController.sendData();
    }

    private void copyControllerDataFrom(FireboxBlockEntity other) {
        inventory.setSize(other.inventory.getSlots());
        for (int slot = 0; slot < other.inventory.getSlots(); slot++) {
            inventory.setStackInSlot(slot, other.inventory.getStackInSlot(slot).copy());
        }
        waterTank.setCapacity(other.waterTank.getCapacity());
        waterTank.setFluid(other.waterTank.getFluid().copy());
        trackedBarrels.clear();
        trackedBarrels.putAll(other.trackedBarrels);
        boilerState = other.boilerState;
        warmupTicks = other.warmupTicks;
        burnTimeRemaining = other.burnTimeRemaining;
        dryTicks = other.dryTicks;
        lockedOut = other.lockedOut;
        lastFailure = other.lastFailure;
        steamPressure = other.steamPressure;
        lastBurning = other.lastBurning;
        lastBurnRate = other.lastBurnRate;
        manualDoorsOpen = other.manualDoorsOpen;
    }

    private void resetControllerData() {
        inventory.setSize(FUEL_SLOTS);
        inventory.setStackInSlot(0, ItemStack.EMPTY);
        waterTank.setFluid(FluidStack.EMPTY);
        waterTank.setCapacity(0);
        trackedBarrels.clear();
        boilerState = BoilerState.COLD;
        warmupTicks = 0;
        burnTimeRemaining = 0f;
        dryTicks = 0;
        lockedOut = false;
        lastFailure = null;
        steamPressure = 0f;
        lastBurning = false;
        lastBurnRate = 0f;
        manualDoorsOpen = false;
        structure = null;
    }

    public void shiftReferences(BlockPos shift) {
        if (controllerPos != null) controllerPos = controllerPos.offset(shift);
        Map<BlockPos, Integer> moved = new HashMap<>();
        trackedBarrels.forEach((pos, capacity) -> moved.put(pos.offset(shift), capacity));
        trackedBarrels.clear();
        trackedBarrels.putAll(moved);
        rescanCooldown = 0;
        setChanged();
    }

    public void onBecameController() {
        rescanCooldown = 0;
        setChanged();
        sendData();
    }

    private void updateLit(boolean burning) {
        BlockState state = getBlockState();
        if (!state.hasProperty(BlockStateProperties.LIT)) return;
        if (state.getValue(BlockStateProperties.LIT) == burning) return;
        level.setBlock(worldPosition, state.setValue(BlockStateProperties.LIT, burning), 3);
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        if (level != null) {
            BlockPos controller = FireboxGroups.controllerOf(level, worldPosition);
            if (controller != null && !controller.equals(worldPosition)
                    && level.getBlockEntity(controller) instanceof FireboxBlockEntity owner) {
                return owner.addToGoggleTooltip(tooltip, isPlayerSneaking);
            }
        }
        LoconauticsLang.translate("gui.goggles.boiler_stats").forGoggles(tooltip);
        addFuelSlot(tooltip);
        addFailureWarning(tooltip);

        if (lockedOut) {
            LoconauticsLang.translate("gui.goggles.boiler_locked")
                    .style(ChatFormatting.RED).forGoggles(tooltip);
            return true;
        }
        if (!dispValid) {
            LoconauticsLang.translate("gui.goggles.boiler_incomplete")
                    .style(ChatFormatting.RED).forGoggles(tooltip);
            addNextStep(tooltip);
            return true;
        }

        LoconauticsLang.translate("gui.goggles.boiler_state").style(ChatFormatting.GRAY).forGoggles(tooltip);
        LoconauticsLang.translate("gui.goggles.boiler_state_" + boilerState.name().toLowerCase(Locale.ROOT))
                .style(stateColor()).forGoggles(tooltip, 1);

        LoconauticsLang.translate("gui.goggles.gauge_temperature").style(ChatFormatting.GRAY).forGoggles(tooltip);
        LoconauticsLang.translate("gui.goggles.boiler_feel_" + temperatureFeelKey())
                .style(ChatFormatting.GOLD).forGoggles(tooltip, 1);

        addNextStep(tooltip);
        addOptionalStep(tooltip);
        return true;
    }

    private void addFuelSlot(List<Component> tooltip) {
        LoconauticsLang.translate("gui.goggles.boiler_fuel").style(ChatFormatting.GRAY).forGoggles(tooltip);
        if (firstFuel().isEmpty()) {
            LoconauticsLang.translate("gui.goggles.boiler_fuel_empty")
                    .style(ChatFormatting.DARK_GRAY).forGoggles(tooltip, 1);
            return;
        }
        LoconauticsLang.builder().add(firstFuel().getHoverName().copy())
                .text(" " + fuelHeld() + "/" + fuelCapacity())
                .style(ChatFormatting.WHITE).forGoggles(tooltip, 1);
    }

    private static FailureCause readFailure(String name) {
        if (name == null || name.isEmpty()) return null;
        try {
            return FailureCause.valueOf(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private void addFailureWarning(List<Component> tooltip) {
        if (lastFailure == null) return;
        LoconauticsLang.translate("gui.goggles.boiler_last_failure")
                .style(ChatFormatting.GRAY).forGoggles(tooltip);
        LoconauticsLang.translate(lastFailure.langKey())
                .style(ChatFormatting.RED).forGoggles(tooltip, 1);
    }

    private void addNextStep(List<Component> tooltip) {
        String step = nextStepKey();
        if (step == null) return;
        LoconauticsLang.translate("gui.goggles.boiler_next_step")
                .style(ChatFormatting.GRAY).forGoggles(tooltip);
        LoconauticsLang.translate("gui.goggles.boiler_step_" + step)
                .style(ChatFormatting.YELLOW).forGoggles(tooltip, 1);
    }

    private void addOptionalStep(List<Component> tooltip) {
        String step = optionalStepKey();
        if (step == null) return;
        LoconauticsLang.translate("gui.goggles.boiler_optional_step")
                .style(ChatFormatting.GRAY).forGoggles(tooltip);
        LoconauticsLang.translate("gui.goggles.boiler_step_" + step)
                .style(ChatFormatting.AQUA).forGoggles(tooltip, 1);
    }

    private ChatFormatting stateColor() {
        return switch (boilerState) {
            case COLD -> ChatFormatting.DARK_GRAY;
            case WARMING -> ChatFormatting.GOLD;
            case HOT -> ChatFormatting.GREEN;
            case STARVED -> ChatFormatting.RED;
        };
    }

    public BoilerState getBoilerState() { return boilerState; }
    public int getWarmupTicks() { return warmupTicks; }
    public float getBurnTimeRemaining() { return burnTimeRemaining; }
    public float getLastBurnRate() { return lastBurnRate; }
    public int getLastWaterDrawn() { return lastWaterDrawn; }
    public boolean hasValidStructure() { return structure != null; }
    @Nullable public BoilerStructure getStructure() { return structure; }
    public ItemStackHandler getInventory() { return inventory; }

    private final class TankSource implements BoilerWaterSource {

        @Override
        public int getWaterAmount() {
            return waterTank.getFluidAmount();
        }

        @Override
        public int getWaterCapacity() {
            return waterTank.getCapacity();
        }

        @Override
        public int drawWater(int millibuckets) {
            if (millibuckets <= 0) return 0;
            return waterTank.drain(millibuckets,
                    IFluidHandler.FluidAction.EXECUTE).getAmount();
        }

        @Override
        public void returnWater(int millibuckets) {
            if (millibuckets <= 0) return;
            waterTank.fill(new FluidStack(
                            Fluids.WATER, millibuckets),
                    IFluidHandler.FluidAction.EXECUTE);
        }

        @Override
        public void setWaterCapacity(int millibuckets) {
            int capacity = Math.max(0, millibuckets);
            if (waterTank.getCapacity() == capacity) return;
            waterTank.setCapacity(capacity);
            int overflow = waterTank.getFluidAmount() - capacity;
            if (overflow > 0) {
                waterTank.drain(overflow,
                        IFluidHandler.FluidAction.EXECUTE);
            }
            setChanged();
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Inventory", inventory.serializeNBT(registries));
        tag.put("WaterTank", waterTank.writeToNBT(registries, new CompoundTag()));
        tag.putInt("TankCapacity", waterTank.getCapacity());
        tag.putString("BoilerState", boilerState.name());
        tag.putInt("WarmupTicks", warmupTicks);
        tag.putFloat("BurnTimeRemaining", burnTimeRemaining);
        tag.putInt("DryTicks", dryTicks);
        tag.putInt("WasteGas", wasteGas);
        tag.putBoolean("LockedOut", lockedOut);
        tag.putString("LastFailure", lastFailure == null ? "" : lastFailure.name());
        tag.putBoolean("DispValid", dispValid);
        tag.putFloat("DispSu", dispSu);
        tag.putFloat("DispRpm", dispRpm);
        tag.putFloat("DispEfficiency", dispEfficiency);
        tag.putBoolean("DispOverfired", dispOverfired);
        tag.putInt("DispWater", dispWater);
        tag.putInt("DispWasteGas", dispWasteGas);
        tag.putInt("DispRemaining", dispRemainingSeconds);
        tag.putFloat("DispTempJitter", dispTemperatureJitter);
        tag.putInt("DispWaterDrawn", lastWaterDrawn);
        tag.putFloat("DispPressure", dispPressure);
        tag.putFloat("SteamPressure", steamPressure);
        tag.putFloat("DispMaxSu", dispMaxSu);
        tag.putInt("DispWaterCapacity", dispWaterCapacity);
        tag.putFloat("DispBurnRate", lastBurnRate);
        tag.putFloat("DispStress", lastStressRatio);
        tag.putDouble("DispMass", lastTrainMass);
        tag.putFloat("DispSubSpeed", lastSubSpeed);
        tag.putBoolean("DispInSubLevel", inSubLevel);
        tag.putString("DispStep", dispStep == null ? "" : dispStep);
        tag.putString("DispOptionalStep", dispOptionalStep == null ? "" : dispOptionalStep);
        tag.putBoolean("DoorsOpen", manualDoorsOpen);
        if (controllerPos != null) tag.putLong("Controller", controllerPos.asLong());
        ListTag barrels = new ListTag();
        for (Map.Entry<BlockPos, Integer> barrel : trackedBarrels.entrySet()) {
            CompoundTag entry = new CompoundTag();
            entry.putLong("Pos", barrel.getKey().asLong());
            entry.putInt("Capacity", barrel.getValue());
            barrels.add(entry);
        }
        tag.put("TrackedBarrels", barrels);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ItemStack fuelBefore = inventory.getStackInSlot(0).copy();
        BlockPos ownerBefore = controllerPos;
        inventory.deserializeNBT(registries, tag.getCompound("Inventory"));
        controllerPos = tag.contains("Controller") ? BlockPos.of(tag.getLong("Controller")) : null;
        waterTank.setCapacity(tag.getInt("TankCapacity"));
        waterTank.readFromNBT(registries, tag.getCompound("WaterTank"));
        try {
            boilerState = BoilerState.valueOf(tag.getString("BoilerState"));
        } catch (IllegalArgumentException e) {
            boilerState = BoilerState.COLD;
        }
        warmupTicks = tag.getInt("WarmupTicks");
        burnTimeRemaining = tag.getFloat("BurnTimeRemaining");
        dryTicks = tag.getInt("DryTicks");
        wasteGas = tag.getInt("WasteGas");
        lockedOut = tag.getBoolean("LockedOut");
        lastFailure = readFailure(tag.getString("LastFailure"));
        dispValid = tag.getBoolean("DispValid");
        dispSu = tag.getFloat("DispSu");
        dispRpm = tag.getFloat("DispRpm");
        dispEfficiency = tag.getFloat("DispEfficiency");
        dispOverfired = tag.getBoolean("DispOverfired");
        dispWater = tag.getInt("DispWater");
        dispWasteGas = tag.getInt("DispWasteGas");
        dispRemainingSeconds = tag.getInt("DispRemaining");
        dispTemperatureJitter = tag.getFloat("DispTempJitter");
        lastWaterDrawn = tag.getInt("DispWaterDrawn");
        dispPressure = tag.getFloat("DispPressure");
        steamPressure = tag.getFloat("SteamPressure");
        dispMaxSu = tag.getFloat("DispMaxSu");
        dispWaterCapacity = tag.getInt("DispWaterCapacity");
        lastBurnRate = tag.getFloat("DispBurnRate");
        lastStressRatio = tag.getFloat("DispStress");
        lastTrainMass = tag.contains("DispMass") ? tag.getDouble("DispMass") : NO_TRAIN;
        lastSubSpeed = tag.getFloat("DispSubSpeed");
        inSubLevel = tag.getBoolean("DispInSubLevel");
        dispStep = tag.getString("DispStep");
        dispOptionalStep = tag.getString("DispOptionalStep");
        manualDoorsOpen = tag.getBoolean("DoorsOpen");
        trackedBarrels.clear();
        ListTag barrels = tag.getList("TrackedBarrels", Tag.TAG_COMPOUND);
        for (int i = 0; i < barrels.size(); i++) {
            CompoundTag entry = barrels.getCompound(i);
            trackedBarrels.put(BlockPos.of(entry.getLong("Pos")), entry.getInt("Capacity"));
        }
        if (StrandTemplates.isPlacing()) {
            trackedBarrels.clear();
            steamPressure = 0f;
            dispPressure = 0f;
        }

        if (level != null && level.isClientSide
                && (!ItemStack.matches(fuelBefore, inventory.getStackInSlot(0))
                || !Objects.equals(ownerBefore, controllerPos))) {
            refreshGroup();
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    private void sendData() {
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }
}
