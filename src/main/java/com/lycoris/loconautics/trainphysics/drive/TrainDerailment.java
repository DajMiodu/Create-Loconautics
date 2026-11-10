package com.lycoris.loconautics.trainphysics.drive;

import com.lycoris.loconautics.common.impact.TrainKnockback;
import com.lycoris.loconautics.common.train.TrainStatusChat;
import com.lycoris.loconautics.config.LoconauticsConfig;
import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.trainphysics.SableTrain.Car;
import com.lycoris.loconautics.trainphysics.station.TrainStations;
import com.lycoris.loconautics.trainphysics.spawn.SableTrainSpawner;
import com.lycoris.loconautics.common.sable.SubLevelBlocks;
import com.simibubi.create.content.trains.bogey.AbstractBogeyBlock;
import com.simibubi.create.foundation.utility.CreateLang;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

import static com.lycoris.loconautics.foundation.LogFormat.shortId;

public final class TrainDerailment {
    private TrainDerailment() {
    }

    static void retain(java.util.Set<UUID> live) {
        RAILS_GONE.keySet().retainAll(live);
        TILT_OVER.keySet().retainAll(live);
    }

    static void check(SableTrain train) {
        if (train.isPhysics()) {
            return;
        }
        if (train.isPoseSettling()) {
            return;
        }
        if (train.isDerailed()) {
            if (ExternalForceDerail.holdsRerail(train)) {
                return;
            }
            if (com.lycoris.loconautics.trainphysics.spawn.RerailApproach.tryAutoRerail(train)) {
                TrainStatusChat.clearPartialDerail(train);
            }
            return;
        }
        if (train.hasOffTrackBogey() && !ExternalForceDerail.holdsRerail(train)
                && SableTrainSpawner.tryRerailOffTrackBogeys(train)
                && !train.hasOffTrackBogey()) {
            TrainStatusChat.clearPartialDerail(train);
        }
        ServerSubLevelContainer container = SubLevelContainer.getContainer(train.level());
        if (container == null) {
            return;
        }
        Car car = train.car();
        if (car.subLevelId() == null
                || !(container.getSubLevel(car.subLevelId()) instanceof ServerSubLevel sub) || sub.isRemoved()) {
            return;
        }
        if (!SubLevelBlocks.hasBlock(sub, AbstractBogeyBlock.class)) {
            train.setDerailed(true);
            return;
        }
        if (!checkRailsGone(train, sub)) {
            return;
        }
        if (checkAttitudeDerail(train)) {
            return;
        }
        if (ExternalForceDerail.check(train, sub)) {
            return;
        }
        double approach = Math.max(Math.abs(train.speed()), Math.abs(train.lastRollingSpeed()));
        train.setLastRollingSpeed(train.speed());
        if (LoconauticsConfig.DERAIL_AT_TRACK_END.get() && SableTrainDriver.DEAD_END.contains(sub.getUniqueId())) {
            boolean full = false;
            for (SableTrain.Bogey bogey : car.bogeys()) {
                if (!train.isBogeyOffTrack(bogey.localPos()) && bogey.rail().reachedEnd()) {
                    full |= derailBogey(train, car, bogey, "end_of_track");
                }
            }
            if (full) {
                double impact = SableTrainDriver.trainMass(sub) * approach * 20.0;
                if (impact > LoconauticsConfig.DERAIL_MAX_END_IMPACT.get()) {
                    TrainKnockback.launchOnDerail(train, DERAIL_LAUNCH_LIFT);
                }
                return;
            }
            if (train.hasOffTrackBogey()) {
                return;
            }
        }
        if (!TrainPropulsion.isMonorail(train) && checkCouplingStress(train, sub)) {
            return;
        }
    }

    static final int RAILS_GONE_GRACE_TICKS = 40;

    static final java.util.Map<UUID, Integer> RAILS_GONE = new java.util.HashMap<>();

    static boolean checkRailsGone(SableTrain train, ServerSubLevel sub) {
        if (TrainRailAdvance.railsValid(train)) {
            RAILS_GONE.remove(train.id());
            return true;
        }
        if (SableTrainSpawner.reseatOnRail(train, sub)) {
            RAILS_GONE.remove(train.id());
            return true;
        }
        if (RAILS_GONE.merge(train.id(), 1, Integer::sum) < RAILS_GONE_GRACE_TICKS) {
            return false;
        }
        RAILS_GONE.remove(train.id());
        train.setSpeed(0.0);
        train.setTargetSpeed(0.0);
        train.setDerailed(true, "track_missing");
        return false;
    }

    static final int TILT_DERAIL_TICKS = 5;

    static final java.util.Map<UUID, Integer> TILT_OVER = new java.util.HashMap<>();

    static boolean checkAttitudeDerail(SableTrain train) {
        double limit = LoconauticsConfig.DERAIL_MAX_TILT_DEGREES.get();
        double tilt = Math.toDegrees(train.car().carriage().hostTilt());
        if (limit >= 180.0 || tilt <= limit) {
            TILT_OVER.remove(train.id());
            return false;
        }
        if (TILT_OVER.merge(train.id(), 1, Integer::sum) < TILT_DERAIL_TICKS) {
            return false;
        }
        TILT_OVER.remove(train.id());
        train.setSpeed(0.0);
        train.setTargetSpeed(0.0);
        train.setDerailed(true, "overturned");
        return true;
    }

    static final double DERAIL_LAUNCH_LIFT = 0.35;

    static boolean derailBogey(SableTrain train, Car car, SableTrain.Bogey bogey, String statusKey) {
        train.setDerailReason(statusKey);
        if (car.bogeys().size() < 2 || bogey == null) {
            train.setDerailed(true, statusKey);
            return true;
        }
        if (train.markBogeyOffTrack(bogey.localPos())) {
            TrainStatusChat.partialDerail(train);
        }
        if (train.offTrackBogeyCount() < car.bogeys().size()) {
            return false;
        }
        train.setDerailed(true, statusKey);
        return true;
    }

    static SableTrain.Bogey nextRailedBogey(SableTrain train) {
        java.util.List<SableTrain.Bogey> onRail = new java.util.ArrayList<>();
        for (SableTrain.Bogey bogey : train.car().bogeys()) {
            if (!train.isBogeyOffTrack(bogey.localPos())) {
                onRail.add(bogey);
            }
        }
        return onRail.isEmpty() ? null : TrainStations.frontBogey(train, onRail);
    }

    static final double MAX_COUPLING_STRESS = 1.5;

    static boolean checkCouplingStress(SableTrain train, ServerSubLevel sub) {
        TrainRailAdvance.Stress stress = TrainRailAdvance.takeMaxStress(sub.getUniqueId());
        if (stress == null || stress.amount() <= MAX_COUPLING_STRESS) {
            return false;
        }
        SableTrain.Bogey culprit = null;
        for (SableTrain.Bogey bogey : train.car().bogeys()) {
            if (bogey.localPos().equals(stress.bogey()) && !train.isBogeyOffTrack(bogey.localPos())) {
                culprit = bogey;
            }
        }
        if (culprit == null) {
            return false;
        }
        derailBogey(train, train.car(), culprit, "coupling_stress");
        tellPlayersAboard(sub, CreateLang.translateDirect("train.status.coupling_stress"));
        return true;
    }

    static void tellPlayersAboard(ServerSubLevel sub, Component message) {
        var bounds = sub.getPlot().getBoundingBox();
        for (ServerPlayer player : sub.getLevel().getServer().getPlayerList().getPlayers()) {
            Vec3 at = player.getVehicle() != null ? player.getVehicle().position() : player.position();
            if (!bounds.contains((int) Math.floor(at.x), (int) Math.floor(at.y), (int) Math.floor(at.z))) {
                continue;
            }
            player.displayClientMessage(CreateLang
                    .translateDirect("train.status", shortId(sub.getUniqueId()))
                    .withStyle(net.minecraft.ChatFormatting.GOLD), false);
            player.displayClientMessage(Component.literal(" - ")
                    .withStyle(net.minecraft.ChatFormatting.GRAY)
                    .append(message.copy().withStyle(st -> st.withColor(0xFFD3B4))), false);
        }
    }
}
