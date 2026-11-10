package com.lycoris.loconautics.common.train;

import com.lycoris.loconautics.content.schedule.SableScheduleRuntime;
import com.lycoris.loconautics.common.turntable.TurntableRegistry;
import com.lycoris.loconautics.compat.computercraft.CcEvents;
import com.lycoris.loconautics.foundation.LoconauticsLang;
import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.trainphysics.TrainConsist;
import com.lycoris.loconautics.trainphysics.rail.RailCarriage;
import com.lycoris.loconautics.trainphysics.spawn.SableTrainSpawner;
import com.simibubi.create.foundation.utility.CreateLang;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class TrainStatusChat {

    private static final int GOOD = 0xD5ECC2;
    private static final int BAD = 0xFFD3B4;
    private static final int MAX_QUEUED = 3;

    private static final Set<String> QUIET_ON_PLATE = Set.of("end_of_track", "track_missing", "no_path",
            "no_path_reason", "no_match", "no_package_target", "postal_unreachable", "back_on_track", "partial_derail",
            "navigation_success");

    private static final Map<UUID, State> STATES = new HashMap<>();

    private static final int DERAIL_GRACE_TICKS = 10;

    private static final Map<UUID, Pending> PENDING = new HashMap<>();

    private static final class Pending {
        final SableTrain train;
        final String key;
        int ticksLeft;

        Pending(SableTrain train, String key, int ticksLeft) {
            this.train = train;
            this.key = key;
            this.ticksLeft = ticksLeft;
        }
    }

    private static final Set<UUID> PARTIAL_TOLD = new HashSet<>();

    private static final class State {
        boolean navigation;
        boolean track;
        boolean conductor;
        boolean signal;
        boolean turntable;
        boolean blocked;
        final List<Component> queued = new ArrayList<>();
    }

    private static State state(SableTrain train) {
        return STATES.computeIfAbsent(train.id(), key -> new State());
    }

    public static void retain(Set<UUID> live) {
        STATES.keySet().retainAll(live);
        PENDING.keySet().retainAll(live);
    }

    public static void maneuverDeadEnd(SableTrain train, String destinationName, @Nullable Component why) {
        CcEvents.queue(train, "train_maneuver_dead_end", destinationName);
        SableScheduleRuntime runtime = SableScheduleRuntime.get(train.id());
        if (runtime != null) {
            if (why != null) {
                runtime.announcer().line(why.copy().withStyle(st -> st.withColor(BAD)));
            }
            runtime.announcer().line(LoconauticsLang.translate("train.status.maneuver_dead_end", destinationName)
                    .component().withStyle(st -> st.withColor(BAD)));
        }
    }
    public static Vector3d bodyPosition(SableTrain train) {
        ServerSubLevelContainer container = SubLevelContainer.getContainer(train.level());
        if (container == null || train.car() == null || train.car().subLevelId() == null) {
            return null;
        }
        if (container.getSubLevel(train.car().subLevelId()) instanceof ServerSubLevel sub && !sub.isRemoved()) {
            return new Vector3d(sub.logicalPose().position());
        }
        return null;
    }

    public static void maneuverStarted(SableTrain train) {
        CcEvents.queue(train, "train_maneuver_start");
    }

    public static void maneuverEnded(SableTrain train) {
        CcEvents.queue(train, "train_maneuver_end");
    }

    public static void failedNavigation(SableTrain train) {
        if (state(train).navigation) {
            return;
        }
        display(train, "no_path", false);
        state(train).navigation = true;
    }

    public static void failedNavigation(SableTrain train, Component reason) {
        if (state(train).navigation) {
            return;
        }
        display(train, "no_path_reason", false, reason);
        state(train).navigation = true;
    }

    public static void cannotContinue(SableTrain train, Component reason) {
        if (state(train).navigation) {
            return;
        }
        display(train, "cannot_continue", false, reason);
        state(train).navigation = true;
    }

    public static void failedNavigationNoTarget(SableTrain train, String filter) {
        if (state(train).navigation) {
            return;
        }
        display(train, "no_match", false, Component.literal(filter));
        state(train).navigation = true;
    }

    public static void failedPackageNoTarget(SableTrain train, String address) {
        if (state(train).navigation) {
            return;
        }
        display(train, "no_package_target", false, Component.literal(address));
        state(train).navigation = true;
    }

    public static void postalUnreachable(SableTrain train, String station) {
        display(train, "postal_unreachable", false, Component.literal(station));
    }

    public static void successfulNavigation(SableTrain train) {
        if (!state(train).navigation) {
            return;
        }
        display(train, "navigation_success", true);
        state(train).navigation = false;
    }

    public static void newSchedule(SableTrain train) {
        State state = state(train);
        state.navigation = false;
        state.conductor = false;
        state.signal = false;
    }

    public static void foundConductor(SableTrain train) {
        if (!state(train).conductor) {
            return;
        }
        display(train, "found_driver", true);
        state(train).conductor = false;
    }

    public static void missingConductor(SableTrain train) {
        if (state(train).conductor) {
            return;
        }
        display(train, "missing_driver", false);
        state(train).conductor = true;
    }

    public static void missingCorrectConductor(SableTrain train) {
        if (state(train).conductor) {
            return;
        }
        display(train, "opposite_driver", false);
        state(train).conductor = true;
    }

    public static void manualControls(SableTrain train) {
        display(train, "paused_for_manual", true);
    }

    public static void holdingAtSignal(SableTrain train) {
        if (state(train).signal) {
            return;
        }
        display(train, "waiting_for_signal", false);
        state(train).signal = true;
    }

    public static void signalCleared(SableTrain train) {
        if (!state(train).signal) {
            return;
        }
        display(train, "signal_cleared", true);
        state(train).signal = false;
    }

    public static void turntableNoPower(SableTrain train) {
        if (state(train).turntable) {
            return;
        }
        display(train, "turntable_no_power", false);
        state(train).turntable = true;
    }

    public static void turntableOverhang(SableTrain train) {
        if (state(train).turntable) {
            return;
        }
        display(train, "turntable_overhang", false);
        state(train).turntable = true;
    }

    public static void turntableReady(SableTrain train) {
        if (!state(train).turntable) {
            return;
        }
        display(train, "turntable_ready", true);
        state(train).turntable = false;
    }

    public static void failedMigration(SableTrain train) {
        if (state(train).track) {
            return;
        }
        display(train, "track_missing", false);
        state(train).track = true;
    }

    public static void highStress(SableTrain train) {
        if (state(train).track) {
            return;
        }
        display(train, "coupling_stress", false);
        state(train).track = true;
    }

    public static void doublePortal(SableTrain train) {
        if (state(train).track) {
            return;
        }
        display(train, "double_portal", false);
        state(train).track = true;
    }

    public static void endOfTrack(SableTrain train) {
        if (state(train).track) {
            return;
        }
        display(train, "end_of_track", false);
        state(train).track = true;
    }

    public static void crash(SableTrain train) {
        display(train, "collision", false);
    }

    public static void crashWith(SableTrain train, SableTrain other) {
        display(train, "collision_with", false, nameOf(other));
    }

    public static void blockedBy(SableTrain train, SableTrain other) {
        if (state(train).blocked) {
            return;
        }
        state(train).blocked = true;
        display(train, "blocked_by", false, nameOf(other));
    }

    public static void blockedByBlocks(SableTrain train, BlockPos at) {
        if (state(train).blocked) {
            return;
        }
        state(train).blocked = true;
        display(train, "blocked_by_blocks", false, at.toShortString());
    }

    public static void unblocked(SableTrain train) {
        State state = STATES.get(train.id());
        if (state != null) {
            state.blocked = false;
        }
    }

    public static void successfulMigration(SableTrain train) {
        if (!state(train).track) {
            return;
        }
        display(train, "back_on_track", true);
        state(train).track = false;
    }

    public static void trackOK(SableTrain train) {
        state(train).track = false;
    }

    public static void derailed(SableTrain train, String key) {
        PENDING.put(train.id(), new Pending(train, key, DERAIL_GRACE_TICKS));
    }

    public static void tickPending() {
        if (PENDING.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<UUID, Pending>> it = PENDING.entrySet().iterator();
        List<Pending> due = new ArrayList<>();
        while (it.hasNext()) {
            Pending pending = it.next().getValue();
            if (--pending.ticksLeft > 0) {
                continue;
            }
            it.remove();
            due.add(pending);
        }
        for (Pending pending : due) {
            if (pending.train.isDerailed()) {
                display(pending.train, pending.key, false);
            }
        }
    }

    public static void partialDerail(SableTrain train) {
        UUID consist = consistKey(train);
        if (consist == null || !PARTIAL_TOLD.add(consist)) {
            return;
        }
        display(train, "partial_derail", false);
    }

    public static void clearPartialDerail(SableTrain train) {
        UUID consist = consistKey(train);
        if (consist != null) {
            PARTIAL_TOLD.remove(consist);
        }
    }

    public static void backOnTrack(SableTrain train) {
        if (PENDING.remove(train.id()) != null) {
            return;
        }
        display(train, "back_on_track", true);
    }

    private static UUID consistKey(SableTrain train) {
        ServerLevel level = train.level();
        UUID self = train.car() == null ? null : train.car().subLevelId();
        if (level == null || self == null) {
            return null;
        }
        UUID lowest = self;
        for (UUID id : TrainConsist.connectedSubLevels(level, self)) {
            if (id.compareTo(lowest) < 0) {
                lowest = id;
            }
        }
        return lowest;
    }

    private static Component nameOf(SableTrain train) {
        String name = train.level() == null ? "" : SableTrainSpawner.trainName(train.level(), train);
        return name.isEmpty() ? CreateLang.translateDirect("train.unnamed") : Component.literal(name);
    }

    private static boolean onTurntable(SableTrain train) {
        if (!TurntableRegistry.anyRegistered() || train.car() == null) {
            return false;
        }
        for (SableTrain.Bogey bogey : train.car().bogeys()) {
            if (hostedByTurntable(bogey.rail())) {
                return true;
            }
        }
        return hostedByTurntable(train.car().carriage());
    }

    private static boolean hostedByTurntable(RailCarriage carriage) {
        ServerSubLevel host = carriage == null ? null : carriage.activeSub();
        return host != null && TurntableRegistry.byPlate(host.getUniqueId()) != null;
    }

    private static void display(SableTrain train, String key, boolean good, Object... args) {
        if (QUIET_ON_PLATE.contains(key) && onTurntable(train)) {
            return;
        }
        ServerLevel level = train.level();
        UUID owner = train.owner();
        State state = state(train);
        Component line = Component.literal(" - ").withStyle(ChatFormatting.GRAY)
                .append(LoconauticsLang.translate("train.status." + key, args).component()
                        .withStyle(st -> st.withColor(good ? GOOD : BAD)));
        state.queued.add(line);
        if (state.queued.size() > MAX_QUEUED) {
            state.queued.remove(0);
        }
        if (level == null || level.getServer() == null || owner == null) {
            return;
        }
        Player player = level.getServer().getPlayerList().getPlayer(owner);
        if (player == null) {
            return;
        }

        player.displayClientMessage(LoconauticsLang.translate("train.status", nameOf(train)).component()
                .withStyle(ChatFormatting.GOLD), false);
        state.queued.forEach(message -> player.displayClientMessage(message, false));
        state.queued.clear();
    }
}
