package com.lycoris.loconautics.content.schedule;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.common.advancement.TrainAdvancements;
import com.lycoris.loconautics.content.analogcontroller.AnalogControllerBlockEntity;
import com.lycoris.loconautics.content.schedule.condition.ScheduleWaitCondition;
import com.lycoris.loconautics.content.schedule.condition.ScheduledDelay;
import com.lycoris.loconautics.content.schedule.destination.DestinationInstruction;
import com.lycoris.loconautics.network.packets.schedule.BlazeConductorHatPacket;
import com.lycoris.loconautics.network.packets.schedule.ConductorHatPacket;
import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.common.sable.SubLevelBlocks;
import com.lycoris.loconautics.trainphysics.drive.SableTrainDriver;
import com.lycoris.loconautics.trainphysics.state.SableTrainRegistry;
import com.simibubi.create.AllDataComponents;
import com.simibubi.create.AllItems;
import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlockEntity;
import com.simibubi.create.content.trains.station.GlobalStation;
import com.simibubi.create.content.trains.station.StationBlockEntity;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.entity.EntityMountEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.EntityInteractSpecific;
import net.neoforged.neoforge.network.PacketDistributor;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.lycoris.loconautics.foundation.LogFormat.shortId;

@EventBusSubscriber(modid = LoconauticsConstants.MOD_ID)
public final class ScheduleConductor {

    public static final String TRAIN_HAT = "TrainHat";

    private static final String STATION_SCHEDULE = "StationSchedule";

    private static final String ADV_TRAIN = "AdvTrain";

    private ScheduleConductor() {
    }

    @SubscribeEvent
    public static void onInteractWithSeatedMob(EntityInteractSpecific event) {
        Player player = event.getEntity();
        Entity target = event.getTarget();
        if (player == null || target == null || player.isSpectator())
            return;
        if (!(target instanceof LivingEntity mob))
            return;
        if (mob.getVehicle() == null)
            return;
        if (player.getCooldowns().isOnCooldown(AllItems.SCHEDULE.get()))
            return;
        if (subLevelOf(mob.getVehicle()) == null)
            return;

        ItemStack held = event.getItemStack();
        boolean onServer = !event.getLevel().isClientSide;
        boolean isConductor = mob.getPersistentData().contains(TRAIN_HAT);

        if (AllItems.SCHEDULE.isIn(held)) {
            if (onServer) {
                handOverSchedule(player, mob, held, isConductor);
            }
            consume(event, player);
            return;
        }

        if (held.isEmpty() && isConductor) {
            if (event.getHand() == InteractionHand.OFF_HAND)
                return;
            if (onServer) {
                if (resumePaused(mob, player)) {
                    consume(event, player);
                    return;
                }
                CompoundTag carried = takeFrom(mob);
                PacketDistributor.sendToPlayersTrackingEntity(mob, new ConductorHatPacket(mob.getId(), false));
                AllSoundEvents.playItemPickup(player);
                player.displayClientMessage(
                        Component.translatable("loconautics.schedule.removed_conductor",
                                mob.getDisplayName()), true);
                // a co driver whose schedule was already taken back carries nothing, a blank item would be a free one
                if (carried != null) {
                    ItemStack returned = AllItems.SCHEDULE.asStack();
                    returned.set(AllDataComponents.TRAIN_SCHEDULE, carried);
                    player.getInventory().placeItemBackInInventory(returned);
                }
            }
            consume(event, player);
        }
    }

    @SubscribeEvent
    public static void onInteractWithBlazeDriver(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        if (player == null || player.isSpectator() || event.getHand() != InteractionHand.MAIN_HAND)
            return;
        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        if (!(level.getBlockEntity(pos) instanceof BlazeBurnerBlockEntity burner))
            return;
        if (burner.getHeatLevelFromBlock() == BlazeBurnerBlock.HeatLevel.NONE)
            return;
        if (player.getCooldowns().isOnCooldown(AllItems.SCHEDULE.get()))
            return;
        if (subLevelAt(level, pos) == null)
            return;

        ItemStack held = event.getItemStack();
        if (AllItems.SCHEDULE.isIn(held)) {
            if (!level.isClientSide)
                handOverToBlaze(player, level, pos, held, burner);
            player.getCooldowns().addCooldown(AllItems.SCHEDULE.get(), 5);
            event.setCancellationResult(InteractionResult.SUCCESS);
            event.setCanceled(true);
            return;
        }
        if (!held.isEmpty() || level.isClientSide)
            return;
        if (takeBackFromBlaze(player, level, pos, burner)) {
            player.getCooldowns().addCooldown(AllItems.SCHEDULE.get(), 5);
            event.setCancellationResult(InteractionResult.SUCCESS);
            event.setCanceled(true);
        }
    }

    private static void handOverToBlaze(Player player, Level level, BlockPos pos, ItemStack held,
            BlazeBurnerBlockEntity burner) {
        ServerSubLevel sub = subLevelAt(level, pos);
        SableTrain train = trainOf(sub);
        if (train == null) {
            deny(player, level, pos, "no_train");
            return;
        }
        AnalogControllerBlockEntity controller = ScheduleReadiness.controllerAt(sub, pos);
        if (controller == null) {
            deny(player, level, pos, "burner_not_facing");
            return;
        }
        if (SableScheduleRuntime.consistKey(train) != null) {
            joinAsBlaze(player, level, pos, sub, controller, burner);
            return;
        }
        Schedule schedule = validateSchedule(player, level, pos, sub, controller, held);
        if (schedule == null) {
            return;
        }

        ScheduleProgressStore.get(level.getServer())
                .putSchedule(train.id(), schedule.write(player.registryAccess()));
        SableScheduleRuntime.assign(train.id(), schedule);
        setBlazeHat(sub, burner, pos, true);
        AllSoundEvents.CONFIRM.playOnServer(level, pos, 1, 1);
        TrainAdvancements.conductor(player);
        player.displayClientMessage(
                Component.translatable("loconautics.schedule.blaze_now_driver"), true);
        held.shrink(1);
    }

    // the consist already runs a schedule, so this burner shares it and the item stays with the player
    private static void joinAsBlaze(Player player, Level level, BlockPos pos, ServerSubLevel sub,
            AnalogControllerBlockEntity controller, BlazeBurnerBlockEntity burner) {
        if (burner.hat) {
            deny(player, level, pos, "already_assigned");
            return;
        }
        if (!hardwareOk(player, level, pos, sub, controller)) {
            return;
        }
        setBlazeHat(sub, burner, pos, true);
        AllSoundEvents.CONFIRM.playOnServer(level, pos, 1, 1);
        player.displayClientMessage(Component.translatable("loconautics.schedule.blaze_co_driver"), true);
    }

    public static boolean drives(LivingEntity mob, UUID trainId) {
        var data = mob.getPersistentData();
        return data.contains(TRAIN_HAT) && data.hasUUID(ADV_TRAIN) && data.getUUID(ADV_TRAIN).equals(trainId);
    }

    private static boolean resumePaused(LivingEntity mob, Player player) {
        var data = mob.getPersistentData();
        return data.hasUUID(ADV_TRAIN) && announceResume(data.getUUID(ADV_TRAIN), player);
    }

    private static boolean announceResume(UUID trainId, Player player) {
        if (!SableScheduleRuntime.resume(trainId)) {
            return false;
        }
        AllSoundEvents.CONFIRM.playOnServer(player.level(), player.blockPosition(), 1, 1);
        player.displayClientMessage(
                com.simibubi.create.foundation.utility.CreateLang.translateDirect("schedule.continued"), true);
        return true;
    }

    private static boolean takeBackFromBlaze(Player player, Level level, BlockPos pos,
            BlazeBurnerBlockEntity burner) {
        SableTrain train = trainOf(subLevelAt(level, pos));
        if (train == null) {
            return false;
        }
        if (announceResume(train.id(), player)) {
            return true;
        }
        // the store also holds a mob conductors schedule, only the hatted burner may hand one back
        if (!burner.hat) {
            return false;
        }
        CompoundTag tag = takeConsistSchedule(level, train.id());
        setBlazeHat(subLevelAt(level, pos), burner, pos, false);
        AllSoundEvents.playItemPickup(player);
        player.displayClientMessage(
                Component.translatable("loconautics.schedule.blaze_relieved"), true);
        if (tag != null) {
            ItemStack returned = AllItems.SCHEDULE.asStack();
            returned.set(AllDataComponents.TRAIN_SCHEDULE, tag);
            player.getInventory().placeItemBackInInventory(returned);
        }
        return true;
    }

    // called once per car, only the first car of the consist still finds the schedule
    public static void returnScheduleOnDisassembly(ServerLevel level, UUID trainId, ServerSubLevel sub,
            Vec3 dropAt, @Nullable ServerPlayer sender) {
        BlockPos at = BlockPos.containing(dropAt.x, dropAt.y, dropAt.z);
        CompoundTag legacy = null;
        for (Entity entity : sub.getLevel().getAllEntities()) {
            if (!(entity instanceof LivingEntity mob) || !drives(mob, trainId)) {
                continue;
            }
            if (legacy == null && mob.getPersistentData().contains(STATION_SCHEDULE)) {
                legacy = mob.getPersistentData().getCompound(STATION_SCHEDULE);
            }
            stripHat(mob);
            PacketDistributor.sendToPlayersTrackingEntity(mob, new ConductorHatPacket(mob.getId(), false));
        }
        for (BlazeBurnerBlockEntity burner : SubLevelBlocks.findAll(sub, BlazeBurnerBlockEntity.class)) {
            if (burner.hat) {
                setBlazeHat(sub, burner, burner.getBlockPos(), false);
            }
        }
        CompoundTag tag = takeConsistSchedule(level, trainId);
        handBackSchedule(level, at, tag != null ? tag : legacy, sender);
    }

    // creates own rule in stationblockentity.dropschedule into an empty main hand, otherwise on the ground
    private static void handBackSchedule(ServerLevel level, BlockPos at, @Nullable CompoundTag tag,
            @Nullable ServerPlayer sender) {
        if (tag == null) {
            return;
        }
        if (sender != null && sender.getMainHandItem().isEmpty()) {
            ItemStack returned = AllItems.SCHEDULE.asStack();
            returned.set(AllDataComponents.TRAIN_SCHEDULE, tag);
            sender.getInventory().placeItemBackInInventory(returned);
            AllSoundEvents.playItemPickup(sender);
            return;
        }
        dropSchedule(level, at, tag);
    }

    @SubscribeEvent
    public static void onStampStation(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        if (player == null || player.isSpectator() || event.getHand() != InteractionHand.MAIN_HAND)
            return;
        if (!player.isShiftKeyDown())
            return;
        ItemStack held = event.getItemStack();
        if (!AllItems.SCHEDULE.isIn(held))
            return;
        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        if (!(level.getBlockEntity(pos) instanceof StationBlockEntity station))
            return;
        if (player.getCooldowns().isOnCooldown(AllItems.SCHEDULE.get()))
            return;

        if (!level.isClientSide)
            addStop(player, level, pos, held, station);
        player.getCooldowns().addCooldown(AllItems.SCHEDULE.get(), 5);
        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
    }

    private static void addStop(Player player, Level level, BlockPos pos, ItemStack held,
            StationBlockEntity station) {
        GlobalStation global = station.getStation();
        if (global == null || global.name.isBlank()) {
            deny(player, level, pos, "station_unnamed");
            return;
        }

        Schedule schedule = readSchedule(player, held);
        if (schedule == null)
            schedule = new Schedule();

        CompoundTag data = new CompoundTag();
        data.putString("Text", global.name);
        DestinationInstruction destination = new DestinationInstruction();
        destination.setData(player.registryAccess(), data);
        List<ScheduleWaitCondition> initialConditions = new ArrayList<>();
        initialConditions.add(new ScheduledDelay());
        List<List<ScheduleWaitCondition>> conditions = new ArrayList<>();
        conditions.add(initialConditions);
        schedule.entries.add(new ScheduleEntry(destination, conditions));

        held.set(AllDataComponents.TRAIN_SCHEDULE, schedule.write(player.registryAccess()));
        AllSoundEvents.CONFIRM.playOnServer(level, pos, 1, 1);
        player.displayClientMessage(Component.translatable("loconautics.schedule.stop_added",
                Component.literal(global.name)), true);
    }

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (!(event.getTarget() instanceof LivingEntity mob))
            return;
        if (!mob.getPersistentData().contains(TRAIN_HAT))
            return;
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer sp)
            PacketDistributor.sendToPlayer(sp, new ConductorHatPacket(mob.getId(), true));
    }

    private static void handOverSchedule(Player player, LivingEntity mob, ItemStack held, boolean isConductor) {
        Level level = player.level();
        BlockPos at = mob.blockPosition();

        ServerSubLevel sub = subLevelOf(mob.getVehicle());
        SableTrain train = trainOf(sub);
        boolean shared = train != null && SableScheduleRuntime.consistKey(train) != null;
        // a conductor left over from a schedule that was taken back may start a new one
        if (isConductor && (train == null || shared)) {
            deny(player, level, at, "already_conductor");
            return;
        }
        AnalogControllerBlockEntity controller = sub == null ? null
                : ScheduleReadiness.controllerAt(sub, BlockPos.containing(mob.getVehicle().position()));
        if (controller == null) {
            deny(player, level, at, "seat_not_facing");
            return;
        }
        if (train == null) {
            deny(player, level, at, "no_train");
            return;
        }
        if (shared) {
            joinAsMob(player, mob, at, sub, controller, train);
            return;
        }
        Schedule schedule = validateSchedule(player, level, at, sub, controller, held);
        if (schedule == null) {
            return;
        }

        CompoundTag written = schedule.write(player.registryAccess());
        mob.getPersistentData().putBoolean(TRAIN_HAT, true);
        mob.getPersistentData().putUUID(ADV_TRAIN, train.id());
        ScheduleProgressStore.get(level.getServer()).putSchedule(train.id(), written);
        SableScheduleRuntime.assign(train.id(), schedule);
        PacketDistributor.sendToPlayersTrackingEntity(mob, new ConductorHatPacket(mob.getId(), true));
        AllSoundEvents.CONFIRM.playOnServer(level, at, 1, 1);
        TrainAdvancements.conductor(player);
        player.displayClientMessage(
                Component.translatable("loconautics.schedule.now_conductor", mob.getDisplayName()), true);
        held.shrink(1);
    }

    // the consist already runs a schedule, so this mob shares it and the item stays with the player
    private static void joinAsMob(Player player, LivingEntity mob, BlockPos at, ServerSubLevel sub,
            AnalogControllerBlockEntity controller, SableTrain train) {
        Level level = player.level();
        if (!hardwareOk(player, level, at, sub, controller)) {
            return;
        }
        mob.getPersistentData().putBoolean(TRAIN_HAT, true);
        mob.getPersistentData().putUUID(ADV_TRAIN, train.id());
        PacketDistributor.sendToPlayersTrackingEntity(mob, new ConductorHatPacket(mob.getId(), true));
        AllSoundEvents.CONFIRM.playOnServer(level, at, 1, 1);
        player.displayClientMessage(
                Component.translatable("loconautics.schedule.co_driver", mob.getDisplayName()), true);
    }

    private static boolean hardwareOk(Player player, Level level, BlockPos at,
            ServerSubLevel sub, AnalogControllerBlockEntity controller) {
        LocomotiveHardware.Result hardware = LocomotiveHardware.validate(sub, controller);
        if (!hardware.ok()) {
            deny(player, level, at, hardware.messageKey());
        }
        return hardware.ok();
    }

    private static Schedule validateSchedule(Player player, Level level, BlockPos at,
            ServerSubLevel sub, AnalogControllerBlockEntity controller, ItemStack held) {
        if (!hardwareOk(player, level, at, sub, controller)) {
            return null;
        }
        Schedule schedule = readSchedule(player, held);
        if (schedule == null || schedule.entries.isEmpty()) {
            deny(player, level, at, "no_stops");
            return null;
        }
        return schedule;
    }

    public static Schedule readSchedule(Player player, ItemStack stack) {
        if (!stack.has(AllDataComponents.TRAIN_SCHEDULE)) {
            return null;
        }
        return Schedule.fromTag(player.registryAccess(), stack.get(AllDataComponents.TRAIN_SCHEDULE));
    }

    private static void deny(Player player, Level level, BlockPos at, String messageKey) {
        AllSoundEvents.DENY.playOnServer(level, at, 1, 1);
        player.displayClientMessage(
                Component.translatable("loconautics.schedule." + messageKey), true);
    }

    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player))
            return;
        for (SableTrain train : SableTrainRegistry.all()) {
            ServerSubLevelContainer container = SubLevelContainer.getContainer(train.level());
            UUID id = train.car().subLevelId();
            if (container == null || id == null
                    || !(container.getSubLevel(id) instanceof ServerSubLevel sub) || sub.isRemoved())
                continue;
            for (BlazeConductorHatPacket packet : blazeHats(sub)) {
                PacketDistributor.sendToPlayer(player, packet);
            }
        }
    }

    public static void broadcastBlazeHats(ServerSubLevel sub) {
        for (BlazeConductorHatPacket packet : blazeHats(sub)) {
            PacketDistributor.sendToAllPlayers(packet);
        }
    }

    private static List<BlazeConductorHatPacket> blazeHats(ServerSubLevel sub) {
        List<BlazeConductorHatPacket> packets = new ArrayList<>();
        for (BlazeBurnerBlockEntity burner
                : com.lycoris.loconautics.common.sable.SubLevelBlocks.findAll(sub, BlazeBurnerBlockEntity.class)) {
            if (burner.hat) {
                packets.add(new BlazeConductorHatPacket(sub.getUniqueId(), burner.getBlockPos(), true));
            }
        }
        return packets;
    }

    private static void setBlazeHat(@Nullable ServerSubLevel sub, BlazeBurnerBlockEntity burner,
            BlockPos pos, boolean hat) {
        burner.hat = hat;
        burner.notifyUpdate();
        if (sub != null) {
            PacketDistributor.sendToAllPlayers(new BlazeConductorHatPacket(sub.getUniqueId(), pos, hat));
        }
    }

    private static ServerSubLevel subLevelOf(Entity seat) {
        return seat == null ? null : subLevelAt(seat.level(), BlockPos.containing(seat.position()));
    }

    private static ServerSubLevel subLevelAt(Level level, BlockPos at) {
        for (SableTrain train : SableTrainRegistry.all()) {
            ServerSubLevelContainer container = SubLevelContainer.getContainer(train.level());
            if (container == null)
                continue;
            UUID id = train.car().subLevelId();
            if (id != null && container.getSubLevel(id) instanceof ServerSubLevel sub
                    && !sub.isRemoved() && sub.getLevel() == level
                    && sub.getPlot().getBoundingBox().contains(at.getX(), at.getY(), at.getZ()))
                return sub;
        }
        return null;
    }

    private static SableTrain trainOf(ServerSubLevel sub) {
        return sub == null ? null : SableTrainRegistry.bySubLevel(sub.getUniqueId());
    }

    @SubscribeEvent
    public static void onDismount(EntityMountEvent event) {
        if (!event.isDismounting())
            return;
        if (event.getLevel().isClientSide)
            return;
        if (!(event.getEntityMounting() instanceof LivingEntity mob))
            return;
        if (!mob.getPersistentData().contains(TRAIN_HAT))
            return;
        relieveAndDrop(mob);
        PacketDistributor.sendToPlayersTrackingEntity(mob, new ConductorHatPacket(mob.getId(), false));
    }

    @SubscribeEvent
    public static void onConductorDeath(LivingDeathEvent event) {
        LivingEntity mob = event.getEntity();
        if (mob.level().isClientSide || !mob.getPersistentData().contains(TRAIN_HAT))
            return;
        relieveAndDrop(mob);
        PacketDistributor.sendToPlayersTrackingEntity(mob, new ConductorHatPacket(mob.getId(), false));
    }

    @SubscribeEvent
    public static void onBreakBlazeDriver(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof Level level) || level.isClientSide)
            return;
        BlockPos pos = event.getPos();
        if (!(level.getBlockEntity(pos) instanceof BlazeBurnerBlockEntity burner) || !burner.hat)
            return;
        SableTrain train = trainOf(subLevelAt(level, pos));
        if (train == null || level.getServer() == null)
            return;
        setBlazeHat(subLevelAt(level, pos), burner, pos, false);
        if (stillDriven(train)) {
            return;
        }
        dropSchedule(level, pos, takeConsistSchedule(level, train.id()));
    }

    @SubscribeEvent
    public static void onConductorLoaded(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide || !(event.getEntity() instanceof LivingEntity mob)) {
            return;
        }
        var data = mob.getPersistentData();
        if (!data.contains(TRAIN_HAT) || !data.hasUUID(ADV_TRAIN) || !data.contains(STATION_SCHEDULE)) {
            return;
        }
        MinecraftServer server = event.getLevel().getServer();
        if (server == null) {
            return;
        }
        UUID trainId = data.getUUID(ADV_TRAIN);
        ScheduleProgressStore store = ScheduleProgressStore.get(server);
        // older saves kept a copy on the mob too, two copies could each be handed back as an item
        if (!store.hasSchedule(trainId)) {
            store.putSchedule(trainId, data.getCompound(STATION_SCHEDULE));
        }
        data.remove(STATION_SCHEDULE);
    }

    private static void relieveAndDrop(LivingEntity mob) {
        var data = mob.getPersistentData();
        SableTrain car = data.hasUUID(ADV_TRAIN) ? SableTrainRegistry.get(data.getUUID(ADV_TRAIN)) : null;
        data.remove(TRAIN_HAT);
        if (car != null && stillDriven(car)) {
            LoconauticsConstants.LOGGER.info("[schedshare] conductor on {} left, the consist schedule stays with the other drivers",
                    shortId(car.id()));
            stripHat(mob);
            return;
        }
        dropSchedule(mob.level(), mob.blockPosition(), takeFrom(mob));
    }

    @Nullable
    private static CompoundTag takeFrom(LivingEntity mob) {
        var data = mob.getPersistentData();
        CompoundTag tag = data.hasUUID(ADV_TRAIN) ? takeConsistSchedule(mob.level(), data.getUUID(ADV_TRAIN)) : null;
        if (tag == null && data.contains(STATION_SCHEDULE)) {
            tag = data.getCompound(STATION_SCHEDULE);
        }
        stripHat(mob);
        return tag;
    }

    // the schedule belongs to the consist, so handing it back from any driver ends it for all of them
    @Nullable
    private static CompoundTag takeConsistSchedule(Level level, UUID carId) {
        MinecraftServer server = level.getServer();
        if (server == null) {
            return null;
        }
        UUID key = SableScheduleRuntime.consistKey(carId);
        ScheduleProgressStore store = ScheduleProgressStore.get(server);
        CompoundTag tag = store.getSchedule(key);
        SableScheduleRuntime runtime = SableScheduleRuntime.get(key);
        if (tag == null && runtime != null && runtime.schedule != null) {
            tag = runtime.schedule.write(server.registryAccess());
        }
        tag = withProgress(key, tag);
        SableScheduleRuntime.clear(key);
        store.remove(key);
        return tag;
    }

    private static boolean stillDriven(SableTrain car) {
        for (SableTrain mate : SableScheduleRuntime.consistCars(car)) {
            if (ScheduleReadiness.drivenController(SableTrainDriver.axleSub(mate), mate.id()) != null) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    private static CompoundTag withProgress(@Nullable UUID trainId, @Nullable CompoundTag tag) {
        if (tag == null || trainId == null) {
            return tag;
        }
        SableScheduleRuntime runtime = SableScheduleRuntime.get(trainId);
        if (runtime == null) {
            return tag;
        }
        CompoundTag stamped = tag.copy();
        stamped.putInt("Progress", runtime.currentEntry);
        return stamped;
    }

    static void dropSchedule(Level level, BlockPos pos, @Nullable CompoundTag tag) {
        if (tag == null) {
            return;
        }
        ItemStack stack = AllItems.SCHEDULE.asStack();
        stack.set(AllDataComponents.TRAIN_SCHEDULE, tag);
        Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack);
    }

    private static void stripHat(LivingEntity mob) {
        var data = mob.getPersistentData();
        data.remove(TRAIN_HAT);
        data.remove(STATION_SCHEDULE);
        data.remove(ADV_TRAIN);
    }

    private static void consume(EntityInteractSpecific event, Player player) {
        player.getCooldowns().addCooldown(AllItems.SCHEDULE.get(), 5);
        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
    }
}
