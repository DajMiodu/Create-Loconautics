package com.lycoris.loconautics.content.schedule;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.content.schedule.condition.CargoThresholdCondition;
import com.lycoris.loconautics.content.schedule.condition.FluidThresholdCondition;
import com.lycoris.loconautics.content.schedule.condition.ScheduleWaitCondition;
import com.lycoris.loconautics.content.schedule.destination.ChangeThrottleInstruction;
import com.lycoris.loconautics.content.schedule.destination.DeliverPackagesInstruction;
import com.lycoris.loconautics.content.schedule.destination.DestinationInstruction;
import com.lycoris.loconautics.content.schedule.destination.FetchPackagesInstruction;
import com.lycoris.loconautics.content.schedule.destination.ScheduleInstruction;
import com.lycoris.loconautics.trainphysics.SableTrain;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class ScheduleAnnouncer {

    private static final double HEAR_RANGE = 64.0;

    private final SableScheduleRuntime runtime;
    private final Map<Integer, String> waiting = new HashMap<>();
    private int half;

    ScheduleAnnouncer(SableScheduleRuntime runtime) {
        this.runtime = runtime;
    }

    public void reset() {
        half = 0;
        waiting.clear();
    }

    public void departing(ScheduleInstruction instruction) {
        whole("Departing toward " + target(instruction));
    }

    public void action(ScheduleInstruction instruction) {
        whole(actionText(instruction));
    }

    public void parked(String station, boolean stopped) {
        String where = station == null ? "" : " at " + station;
        whole((stopped ? "Parked" : "Reached the stop") + where);
    }

    public void waiting(int column, int columns, ScheduleWaitCondition condition, String station,
                        CompoundTag context) {
        String progress = progressText(condition, context);
        String key = condition.getId() + progress;
        if (key.equals(waiting.get(column))) {
            return;
        }
        waiting.put(column, key);
        String where = station == null ? "Parked" : "Parked at " + station;
        part(where + " until " + waitingText(condition) + progress + suffix(column, columns));
    }

    public void waitingForBody(int column, int columns, ScheduleWaitCondition condition) {
        String key = condition.getId() + ":unloaded";
        if (key.equals(waiting.get(column))) {
            return;
        }
        waiting.put(column, key);
        part("Cars are unloaded, will check that " + waitingText(condition) + " once they load"
                + suffix(column, columns));
    }

    public void met(int column, int columns, ScheduleWaitCondition condition, CompoundTag context) {
        waiting.remove(column);
        part(metText(condition) + progressText(condition, context) + suffix(column, columns));
    }

    private static String progressText(ScheduleWaitCondition condition, CompoundTag context) {
        if (!(condition instanceof CargoThresholdCondition cargo)) {
            return "";
        }
        int current = Math.max(0, cargo.currentAmount(context));
        String unit = cargo instanceof FluidThresholdCondition ? "buckets"
                : cargo.getMeasure() == 1 ? "stacks" : "items";
        String text = ", " + current + "/" + cargo.getThreshold() + " " + unit;
        ItemStack filter = cargo.getItem(0);
        return filter.isEmpty() ? text : text + " of " + filterName(cargo, filter);
    }

    private static String filterName(CargoThresholdCondition cargo, ItemStack filter) {
        String name = filter.getHoverName().getString();
        if (!(cargo instanceof FluidThresholdCondition)) {
            return name;
        }
        return name.endsWith(" Bucket") ? name.substring(0, name.length() - " Bucket".length()) : name;
    }

    public void complete() {
        whole("Schedule complete");
    }

    public void line(Component text) {
        send("", text);
    }

    private void whole(String text) {
        send("", Component.literal(text));
    }

    private void part(String text) {
        send(letter(), Component.literal(text));
    }

    private String letter() {
        return String.valueOf((char) ('a' + Math.min(half++, 25)));
    }

    private void send(String letter, Component body) {
        int total = runtime.schedule == null ? 0 : runtime.schedule.entries.size();
        SableTrain train = runtime.train();
        String residency = train != null && train.isGhost() ? "{UNLOADED} " : "";
        Component line = Component
                .literal("[loco] " + residency + "Entry " + (runtime.currentEntry + 1) + letter + "/" + total + ": ")
                .append(body);
        LoconauticsConstants.LOGGER.info(line.getString());
        if (train == null || train.level() == null || train.level().getServer() == null) {
            return;
        }
        Vec3 at = train.isTrackless() ? null : runtime.leadRail().leadingPos();
        for (ServerPlayer player : train.level().getServer().getPlayerList().getPlayers()) {
            if (hears(player, train, at)) {
                player.sendSystemMessage(line);
            }
        }
    }

    private static boolean hears(ServerPlayer player, SableTrain train, @Nullable Vec3 at) {
        if (player.getUUID().equals(train.owner())) {
            return true;
        }
        if (player.level() != train.level()) {
            return false;
        }
        return at != null && player.distanceToSqr(at) <= HEAR_RANGE * HEAR_RANGE || rides(player, train);
    }

    // a seat sits in the plot, far from the rails, so a seated rider is matched by plot and not by distance
    private static boolean rides(ServerPlayer player, SableTrain train) {
        Entity seat = player.getVehicle();
        ServerSubLevelContainer container = SubLevelContainer.getContainer(train.level());
        if (seat == null || container == null || train.car() == null) {
            return false;
        }
        Set<UUID> cars = new HashSet<>(train.consistCache());
        if (train.car().subLevelId() != null) {
            cars.add(train.car().subLevelId());
        }
        BlockPos pos = seat.blockPosition();
        for (UUID id : cars) {
            if (container.getSubLevel(id) instanceof ServerSubLevel sub && !sub.isRemoved()
                    && sub.getPlot().getBoundingBox().contains(pos.getX(), pos.getY(), pos.getZ())) {
                return true;
            }
        }
        return false;
    }

    private static String suffix(int column, int columns) {
        return columns <= 1 ? "" : " (column " + (column + 1) + ")";
    }

    private static String target(ScheduleInstruction instruction) {
        if (instruction instanceof DestinationInstruction destination) {
            return destination.getFilter();
        }
        if (instruction instanceof FetchPackagesInstruction fetch) {
            return "a postbox holding packages for \"" + fetch.getFilter() + "\"";
        }
        if (instruction instanceof DeliverPackagesInstruction) {
            return "the stop that takes its packages";
        }
        return instruction.getId().getPath();
    }

    private static String actionText(ScheduleInstruction instruction) {
        if (instruction instanceof ChangeThrottleInstruction throttle) {
            return "Throttle set to " + Math.round(throttle.getThrottle() * 100.0f) + " percent";
        }
        return instruction.getId().getPath().replace('_', ' ');
    }

    private static String waitingText(ScheduleWaitCondition condition) {
        return switch (condition.getId().getPath()) {
            case "delay" -> "the delay runs out";
            case "time_of_day" -> "the time of day comes round";
            case "item_threshold" -> "the item threshold is met";
            case "fluid_threshold" -> "the fluid threshold is met";
            case "redstone_link" -> "the redstone link fires";
            case "player_count" -> "the player count is met";
            case "idle" -> "the cargo stops moving";
            case "unloaded" -> "the station chunk unloads";
            case "powered" -> "the station block is powered";
            default -> condition.getId().getPath().replace('_', ' ') + " is met";
        };
    }

    private static String metText(ScheduleWaitCondition condition) {
        return switch (condition.getId().getPath()) {
            case "delay" -> "Delay finished";
            case "time_of_day" -> "Time of day reached";
            case "item_threshold" -> "Item threshold met";
            case "fluid_threshold" -> "Fluid threshold met";
            case "redstone_link" -> "Redstone link fired";
            case "player_count" -> "Player count met";
            case "idle" -> "Cargo idle";
            case "unloaded" -> "Station chunk unloaded";
            case "powered" -> "Station block powered";
            default -> capitalize(condition.getId().getPath().replace('_', ' ')) + " met";
        };
    }

    private static String capitalize(String text) {
        return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }
}
