package com.lycoris.loconautics.common.advancement;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.common.turntable.TurntableRegistry;
import com.lycoris.loconautics.content.analogcontroller.AnalogControllerBlockEntity;
import com.lycoris.loconautics.content.bearingaxle.BearingAxleBlockEntity;
import com.lycoris.loconautics.content.bearingaxle.BearingAxleValidity;
import com.lycoris.loconautics.content.combustion.SmallEngineTuning;
import com.lycoris.loconautics.content.controls.panel.PanelBlockEntity;
import com.lycoris.loconautics.content.controls.panel.PanelPart;
import com.lycoris.loconautics.content.controls.panel.PanelSlot;
import com.lycoris.loconautics.content.controls.panelbutton.ButtonPart;
import com.lycoris.loconautics.content.controls.panellever.LeverPart;
import com.lycoris.loconautics.content.controls.panelswitch.SwitchPart;
import com.lycoris.loconautics.content.controls.panelvalve.ValvePart;
import com.lycoris.loconautics.content.turntablebearing.TurntableBearingBlock;
import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.common.sable.SubLevelData;
import com.lycoris.loconautics.trainphysics.drive.ConsistPower;
import com.lycoris.loconautics.trainphysics.drive.SableTrainDriver;
import com.lycoris.loconautics.trainphysics.drive.TrainPropulsion;
import com.lycoris.loconautics.trainphysics.rail.RailCarriage;
import com.lycoris.loconautics.trainphysics.rail.RailSeams;
import com.lycoris.loconautics.trainphysics.state.SableTrainRegistry;
import com.simibubi.create.content.contraptions.actors.seat.SeatEntity;
import com.simibubi.create.content.trains.entity.TravellingPoint;
import com.simibubi.create.content.trains.graph.TrackEdge;
import com.simibubi.create.foundation.advancement.AllAdvancements;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityMountEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@EventBusSubscriber(modid = LoconauticsConstants.MOD_ID)
public final class TrainAdvancements {

    private static final ResourceLocation SABLE_TRAIN = LoconauticsConstants.id("sable_train");
    private static final ResourceLocation HEAVY_GRINDING = LoconauticsConstants.id("heavy_grinding");
    private static final ResourceLocation RUN_OVER = LoconauticsConstants.id("run_over");
    private static final ResourceLocation HEAVY_DUTY = LoconauticsConstants.id("heavy_duty");
    private static final ResourceLocation SERIOUS_TORQUE = LoconauticsConstants.id("serious_torque");
    private static final ResourceLocation TURNIN = LoconauticsConstants.id("turnin");
    private static final ResourceLocation TRAINCEPTION = LoconauticsConstants.id("trainception");
    private static final ResourceLocation FACTORY_ON_WHEELS = LoconauticsConstants.id("factory_on_wheels");
    private static final ResourceLocation SPEED_LIMIT = LoconauticsConstants.id("speed_limit");
    private static final ResourceLocation AI_TAKEOVER = LoconauticsConstants.id("ai_takeover");
    private static final ResourceLocation DONT_TOUCH = LoconauticsConstants.id("dont_touch");
    private static final ResourceLocation COCKPIT_READY = LoconauticsConstants.id("cockpit_ready");
    private static final ResourceLocation TOO_MUCH_PRESSURE = LoconauticsConstants.id("too_much_pressure");
    private static final ResourceLocation ANY_FUEL_WILL_DO = LoconauticsConstants.id("any_fuel_will_do");
    private static final ResourceLocation OVER_9000 = LoconauticsConstants.id("over_9000");
    private static final ResourceLocation LOCONAUTICS_CUSTOMS = LoconauticsConstants.id("loconautics_customs");
    private static final String ENGINE_FUELS = "LoconauticsEngineFuels";
    private static final String ENGINE_COLORS = "LoconauticsEngineColors";
    private static final int ENGINE_WITNESS_RANGE = 16;
    private static final int DIFFERENT_FUELS = 3;
    private static final int BURST_WITNESS_RANGE = 64;
    private static final int CHECK_INTERVAL = 20;
    private static final double HEAVY_MASS = 1000.0;
    private static final double SERIOUS_TORQUE_SU = 256_000.0;
    private static final int FULL_THROTTLE = 15;
    private static final double ROLLING_SPEED = 0.05;
    private static final int LONG_TRAIN_CARRIAGES = 6;
    private static final double LONG_TRAVEL_DISTANCE = 5000.0;
    private static final String SEAT_MOUNTED_AT = "LoconauticsSeatMountedAt";

    @Nullable
    private static BlockEntity tickingBlockEntity;

    private TrainAdvancements() {
    }

    public static void assembled(@Nullable ServerPlayer player, int carriages, boolean blockBreakers) {
        if (player == null) {
            return;
        }
        AllAdvancements.TRAIN.awardTo(player);
        if (blockBreakers) {
            AllAdvancements.CONTRAPTION_ACTORS.awardTo(player);
        }
        if (carriages >= LONG_TRAIN_CARRIAGES) {
            AllAdvancements.LONG_TRAIN.awardTo(player);
        }
        award(player, SABLE_TRAIN);
    }

    public static void buttonPlaced(ServerPlayer player) {
        award(player, DONT_TOUCH);
    }

    public static void panelChanged(ServerPlayer player, PanelBlockEntity panel) {
        boolean button = false;
        boolean lever = false;
        boolean toggle = false;
        boolean valve = false;
        for (PanelSlot slot : panel.slots()) {
            PanelPart part = panel.getPart(slot);
            button |= part instanceof ButtonPart;
            lever |= part instanceof LeverPart;
            toggle |= part instanceof SwitchPart;
            valve |= part instanceof ValvePart;
        }
        if (button && lever && toggle && valve) {
            award(player, COCKPIT_READY);
        }
    }

    public static void boilerBurst(ServerLevel level, BlockPos at) {
        for (ServerPlayer player : level.players()) {
            if (player.blockPosition().distSqr(at) < BURST_WITNESS_RANGE * BURST_WITNESS_RANGE) {
                award(player, TOO_MUCH_PRESSURE);
            }
        }
    }

    public static void engineStarted(ServerLevel level, BlockPos at, int fuelOrdinal, int rowLength) {
        for (ServerPlayer player : level.players()) {
            if (player.blockPosition().distSqr(at) > ENGINE_WITNESS_RANGE * ENGINE_WITNESS_RANGE) {
                continue;
            }
            int fuels = player.getPersistentData().getInt(ENGINE_FUELS) | (1 << fuelOrdinal);
            player.getPersistentData().putInt(ENGINE_FUELS, fuels);
            if (Integer.bitCount(fuels) >= DIFFERENT_FUELS) {
                award(player, ANY_FUEL_WILL_DO);
            }
            if (rowLength >= SmallEngineTuning.MAX_TANDEM) {
                award(player, OVER_9000);
            }
        }
    }

    public static void enginePainted(ServerPlayer player, DyeColor color) {
        int colors = player.getPersistentData().getInt(ENGINE_COLORS) | (1 << color.getId());
        player.getPersistentData().putInt(ENGINE_COLORS, colors);
        if (Integer.bitCount(colors) >= DyeColor.values().length) {
            award(player, LOCONAUTICS_CUSTOMS);
        }
    }

    public static void conductor(Player player) {
        AllAdvancements.CONDUCTOR.awardTo(player);
    }

    public static void portal(List<ServerPlayer> riders) {
        for (ServerPlayer rider : riders) {
            AllAdvancements.TRAIN_PORTAL.awardTo(rider);
        }
    }

    public static void crashed(ServerSubLevel sub, SableTrain train) {
        AnalogControllerBlockEntity controller = SableTrainDriver.operatedControllerIn(train);
        UUID driver = controller == null ? null : controller.getCurrentUser();
        for (ServerPlayer rider : riders(sub)) {
            if (!rider.getUUID().equals(driver)) {
                AllAdvancements.TRAIN_CRASH.awardTo(rider);
            }
        }
        if (controller != null && controller.getCurrentPower() < 0) {
            ServerPlayer operator = SableTrainDriver.operatorOf(train, controller);
            if (operator != null) {
                AllAdvancements.TRAIN_CRASH_BACKWARDS.awardTo(operator);
            }
        }
    }

    public static void ranOver(SableTrain train, boolean killed) {
        ServerPlayer driver = driverOf(train);
        if (driver != null) {
            award(driver, RUN_OVER);
            if (killed) {
                AllAdvancements.TRAIN_ROADKILL.awardTo(driver);
            }
        }
    }

    public static void aiTakeover(SableTrain train) {
        ServerSubLevel sub = train.car() == null ? null : SubLevelData.resolve(train.level(), train.car().subLevelId());
        if (sub != null && !sub.isRemoved()) {
            award(crew(sub, train), AI_TAKEOVER);
        }
    }

    public static void check(SableTrain train) {
        if (train.isGhost() || train.car() == null || train.level().getGameTime() % CHECK_INTERVAL != 0) {
            return;
        }
        ServerSubLevel sub = SubLevelData.resolve(train.level(), train.car().subLevelId());
        if (sub == null || sub.isRemoved()) {
            return;
        }
        List<ServerPlayer> crew = crew(sub, train);
        boolean heavy = ConsistPower.consistMass(train, 0.0) >= HEAVY_MASS;
        ServerSubLevel host = railHost(train);
        if (heavy && host != null && TurntableRegistry.isPlate(host.getUniqueId())
                && TurntableRegistry.wholeTrainOn(host.getUniqueId(), train)) {
            award(crew, HEAVY_GRINDING);
        }
        if (heavy) {
            checkFullThrottle(train);
        }
        if (Math.abs(train.speed()) * 20.0 > TrainPropulsion.createTopSpeedMps()) {
            award(crew, SPEED_LIMIT);
        }
        SableTrain below = host == null ? null : SableTrainRegistry.bySubLevel(host.getUniqueId());
        ServerSubLevel belowHost = below == null ? null : railHost(below);
        if (belowHost != null && SableTrainRegistry.bySubLevel(belowHost.getUniqueId()) != null) {
            award(crew, TRAINCEPTION);
        }
        for (BearingAxleBlockEntity axle : BearingAxleValidity.valid(sub)) {
            if (!axle.isOverStressed()
                    && axle.calculateStressApplied() * Math.abs(axle.getSpeed()) > SERIOUS_TORQUE_SU) {
                award(crew, SERIOUS_TORQUE);
            }
        }
    }

    private static void checkFullThrottle(SableTrain train) {
        AnalogControllerBlockEntity controller = SableTrainDriver.operatedControllerIn(train);
        if (controller == null || Math.abs(controller.getCurrentPower()) < FULL_THROTTLE
                || Math.abs(train.speed()) < ROLLING_SPEED) {
            return;
        }
        ServerPlayer driver = SableTrainDriver.operatorOf(train, controller);
        if (driver != null) {
            award(driver, HEAVY_DUTY);
        }
    }

    @Nullable
    private static ServerPlayer driverOf(SableTrain train) {
        AnalogControllerBlockEntity controller = SableTrainDriver.operatedControllerIn(train);
        return controller == null ? null : SableTrainDriver.operatorOf(train, controller);
    }

    @Nullable
    private static ServerSubLevel railHost(SableTrain train) {
        SableTrain.Car car = train.car();
        if (car == null) {
            return null;
        }
        ServerSubLevel host = railHost(car.carriage());
        for (SableTrain.Bogey bogey : car.bogeys()) {
            if (host != null) {
                return host;
            }
            host = railHost(bogey.rail());
        }
        return host;
    }

    @Nullable
    private static ServerSubLevel railHost(@Nullable RailCarriage rail) {
        TravellingPoint point = rail == null ? null : rail.leading();
        TrackEdge edge = point == null ? null : point.edge;
        if (edge == null || edge.node1 == null || edge.node2 == null) {
            return null;
        }
        ServerSubLevel host = RailSeams.serverHostOf(edge.node1.getLocation());
        return host != null ? host : RailSeams.serverHostOf(edge.node2.getLocation());
    }

    @SubscribeEvent
    public static void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !(event.getPlacedBlock().getBlock() instanceof TurntableBearingBlock)
                || !(event.getLevel() instanceof Level level)
                || !(Sable.HELPER.getContaining(level, event.getPos()) instanceof ServerSubLevel sub)) {
            return;
        }
        SableTrain train = SableTrainRegistry.bySubLevel(sub.getUniqueId());
        ServerSubLevel host = train == null ? null : railHost(train);
        if (host != null && TurntableRegistry.isPlate(host.getUniqueId())) {
            award(player, TURNIN);
        }
    }

    public static void blockEntityTicking(@Nullable BlockEntity blockEntity) {
        tickingBlockEntity = blockEntity;
    }

    // the recipe never learns where it ran, only the belt, depot or machine ticking around it knows
    public static void sequencedAssemblyFinished() {
        BlockEntity at = tickingBlockEntity;
        if (at == null || !(Sable.HELPER.getContaining(at) instanceof ServerSubLevel sub)) {
            return;
        }
        SableTrain train = SableTrainRegistry.bySubLevel(sub.getUniqueId());
        if (train != null) {
            award(crew(sub, train), FACTORY_ON_WHEELS);
        }
    }

    private static List<ServerPlayer> crew(ServerSubLevel sub, SableTrain train) {
        List<ServerPlayer> crew = riders(sub);
        ServerPlayer owner = train.owner() == null ? null
                : train.level().getServer().getPlayerList().getPlayer(train.owner());
        if (owner != null && !crew.contains(owner)) {
            crew.add(owner);
        }
        return crew;
    }

    private static void award(List<ServerPlayer> players, ResourceLocation id) {
        for (ServerPlayer player : players) {
            award(player, id);
        }
    }

    private static void award(ServerPlayer player, ResourceLocation id) {
        AdvancementHolder holder = player.server.getAdvancements().get(id);
        if (holder == null) {
            return;
        }
        AdvancementProgress progress = player.getAdvancements().getOrStartProgress(holder);
        for (String criterion : progress.getRemainingCriteria()) {
            player.getAdvancements().award(holder, criterion);
        }
    }

    public static List<ServerPlayer> riders(ServerSubLevel sub) {
        List<ServerPlayer> riders = new ArrayList<>();
        for (ServerPlayer player : sub.getLevel().players()) {
            SubLevel tracking = Sable.HELPER.getTrackingSubLevel(player);
            if (tracking != null && tracking.getUniqueId().equals(sub.getUniqueId())) {
                riders.add(player);
            }
        }
        return riders;
    }

    @SubscribeEvent
    public static void onSeatMount(EntityMountEvent event) {
        if (event.getLevel().isClientSide || !(event.getEntityMounting() instanceof ServerPlayer player)
                || !(event.getEntityBeingMounted() instanceof SeatEntity seat)) {
            return;
        }
        Vec3 at = seatInWorld(seat);
        if (at == null) {
            return;
        }
        CompoundTag data = player.getPersistentData();
        if (event.isMounting()) {
            CompoundTag mounted = new CompoundTag();
            mounted.putDouble("X", at.x);
            mounted.putDouble("Y", at.y);
            mounted.putDouble("Z", at.z);
            data.put(SEAT_MOUNTED_AT, mounted);
            return;
        }
        if (!data.contains(SEAT_MOUNTED_AT)) {
            return;
        }
        CompoundTag mounted = data.getCompound(SEAT_MOUNTED_AT);
        data.remove(SEAT_MOUNTED_AT);
        Vec3 from = new Vec3(mounted.getDouble("X"), mounted.getDouble("Y"), mounted.getDouble("Z"));
        if (!from.closerThan(at, LONG_TRAVEL_DISTANCE)) {
            AllAdvancements.LONG_TRAVEL.awardTo(player);
        }
    }

    @Nullable
    private static Vec3 seatInWorld(Entity seat) {
        if (!(Sable.HELPER.getContaining(seat.level(), seat.position()) instanceof ServerSubLevel sub)
                || SableTrainRegistry.bySubLevel(sub.getUniqueId()) == null) {
            return null;
        }
        Vector3d world = sub.logicalPose().transformPosition(
                new Vector3d(seat.getX(), seat.getY(), seat.getZ()), new Vector3d());
        return new Vec3(world.x, world.y, world.z);
    }
}
