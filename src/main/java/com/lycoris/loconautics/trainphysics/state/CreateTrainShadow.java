package com.lycoris.loconautics.trainphysics.state;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.compat.crn.CrnTrainEvents;
import com.lycoris.loconautics.content.schedule.RouteDistance;
import com.lycoris.loconautics.content.schedule.SableScheduleRuntime;
import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.common.sable.SubLevelData;
import com.lycoris.loconautics.trainphysics.TrainConsist;
import com.lycoris.loconautics.trainphysics.signal.TrainSignals;
import com.lycoris.loconautics.trainphysics.rail.RailCarriage;
import com.lycoris.loconautics.trainphysics.spawn.SableTrainSpawner;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.Create;
import com.simibubi.create.content.trains.bogey.AbstractBogeyBlock;
import com.simibubi.create.content.trains.entity.AddTrainPacket;
import com.simibubi.create.content.trains.entity.Carriage;
import com.simibubi.create.content.trains.entity.CarriageBogey;
import com.simibubi.create.content.trains.entity.Navigation;
import com.simibubi.create.content.trains.entity.RemoveTrainPacket;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.entity.TrainIconType;
import com.simibubi.create.content.trains.entity.TravellingPoint;
import com.simibubi.create.content.trains.schedule.Schedule;
import com.simibubi.create.content.trains.schedule.ScheduleRuntime;
import com.simibubi.create.content.trains.station.GlobalStation;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.createmod.catnip.platform.CatnipServices;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class CreateTrainShadow {

    private static final long MAX_CAR_GAP = 64;
    private static final Map<UUID, Train> SHADOWS = new HashMap<>();

    private static final Map<UUID, Train> BY_CART = new HashMap<>();

    private static final Map<UUID, List<Member>> LAYOUTS = new HashMap<>();

    private static final Map<UUID, TranslatedSchedule> SCHEDULES = new HashMap<>();

    private static final Map<UUID, GlobalStation> PARKED = new HashMap<>();
    private static final Map<UUID, Double> LAST_CRUISE = new HashMap<>();

    private static final Map<UUID, GlobalStation> RESERVED = new HashMap<>();

    // a marker for has a schedule when the real one translates to nothing, non cyclic because creates departure
    private static final Schedule PRESENT = new Schedule();
    static {
        PRESENT.cyclic = false;
    }

    private record TranslatedSchedule(com.lycoris.loconautics.content.schedule.Schedule source, Schedule translated) {}

    public record Member(UUID cart, boolean reversed, boolean twoBogeys) {}

    // the shadow keeps the id of the cart it is named after, so the head has to stay put while the rake does
    private static final Comparator<SableTrain> HEAD_FIRST = Comparator
            .comparing((SableTrain car) -> !scheduled(car))
            .thenComparing(car -> !SHADOWS.containsKey(car.id()))
            .thenComparing(SableTrain::id);

    public static void sync() {
        Map<UUID, SableTrain> bySubLevel = new HashMap<>();
        for (SableTrain train : SableTrainRegistry.all()) {
            if (train.car() != null) {
                bySubLevel.put(train.car().subLevelId(), train);
            }
        }
        Set<UUID> live = new HashSet<>();
        Set<UUID> placed = new HashSet<>();
        BY_CART.clear();
        for (SableTrain train : SableTrainRegistry.all()) {
            if (placed.contains(train.id())) {
                continue;
            }
            List<SableTrain> cars = consistOf(train, bySubLevel);
            cars.forEach(car -> placed.add(car.id()));
            SableTrain head = cars.stream().min(HEAD_FIRST).orElse(train);
            live.add(head.id());
            try {
                Train shadow = mirror(head, frontFirst(cars, head));
                if (shadow != null) {
                    cars.forEach(car -> BY_CART.put(car.id(), shadow));
                }
            } catch (Throwable t) {
                LoconauticsConstants.LOGGER.error("[trainmap] error mirroring train {}", head.id(), t);
            }
        }
        SCHEDULES.keySet().retainAll(live);
        PARKED.keySet().retainAll(live);
        LAST_CRUISE.keySet().retainAll(live);
        LAYOUTS.keySet().retainAll(live);
        SHADOWS.keySet().removeIf(id -> {
            if (live.contains(id)) {
                return false;
            }
            releaseReservation(id, SHADOWS.get(id));
            Train removed = Create.RAILWAYS.trains.remove(id);
            if (removed != null) {
                CatnipServices.NETWORK.sendToAllClients(new RemoveTrainPacket(removed));
            }
            return true;
        });
    }

    public static Train of(UUID cart) {
        return BY_CART.get(cart);
    }

    public static boolean isShadow(Train train) {
        return SHADOWS.get(train.id) == train;
    }

    public static List<Member> membersOf(Train shadow) {
        List<Member> layout = LAYOUTS.get(shadow.id);
        return layout == null || !isShadow(shadow) ? List.of() : layout;
    }

    public static SableTrain cartAt(Train shadow, Vec3 at) {
        List<Member> layout = LAYOUTS.get(shadow.id);
        if (!isShadow(shadow) || layout == null || shadow.graph == null) {
            return null;
        }
        SableTrain best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int i = 0; i < layout.size() && i < shadow.carriages.size(); i++) {
            TravellingPoint lead = shadow.carriages.get(i).getLeadingPoint();
            TravellingPoint trail = shadow.carriages.get(i).getTrailingPoint();
            SableTrain car = SableTrainRegistry.get(layout.get(i).cart());
            if (car == null || lead.edge == null || trail.edge == null) {
                continue;
            }
            double distance = distanceToSpan(at, lead.getPosition(shadow.graph), trail.getPosition(shadow.graph));
            if (distance < bestDistance) {
                bestDistance = distance;
                best = car;
            }
        }
        return best;
    }

    private static double distanceToSpan(Vec3 at, Vec3 from, Vec3 to) {
        Vec3 span = to.subtract(from);
        double length = span.lengthSqr();
        double t = length < 1.0e-9 ? 0.0 : Mth.clamp(at.subtract(from).dot(span) / length, 0.0, 1.0);
        return from.add(span.scale(t)).distanceToSqr(at);
    }

    public static void hideForSave() {
        for (UUID id : SHADOWS.keySet()) {
            Create.RAILWAYS.trains.remove(id);
        }
    }

    public static void restoreAfterSave() {
        SHADOWS.forEach(Create.RAILWAYS.trains::put);
    }

    public static void clear() {
        for (UUID id : SHADOWS.keySet()) {
            releaseReservation(id, SHADOWS.get(id));
            Create.RAILWAYS.trains.remove(id);
        }
        SHADOWS.clear();
        BY_CART.clear();
        LAYOUTS.clear();
        SCHEDULES.clear();
        PARKED.clear();
        LAST_CRUISE.clear();
        RESERVED.clear();
    }

    private static boolean scheduled(SableTrain car) {
        SableScheduleRuntime runtime = SableScheduleRuntime.get(car.id());
        return runtime != null && runtime.schedule != null && !runtime.completed;
    }

    private static List<SableTrain> consistOf(SableTrain train, Map<UUID, SableTrain> bySubLevel) {
        ServerLevel level = train.level();
        if (level == null || train.car() == null) {
            return List.of(train);
        }
        UUID start = train.car().subLevelId();
        Set<UUID> subLevels = new LinkedHashSet<>(TrainConsist.orderedChain(level, start));
        subLevels.addAll(TrainConsist.connectedSubLevels(level, start));
        List<SableTrain> cars = new ArrayList<>(subLevels.size());
        for (UUID subLevel : subLevels) {
            SableTrain car = subLevel.equals(start) ? train : bySubLevel.get(subLevel);
            if (car != null) {
                cars.add(car);
            }
        }
        return cars;
    }

    private static List<SableTrain> frontFirst(List<SableTrain> cars, SableTrain head) {
        if (2 * cars.indexOf(head) > cars.size() - 1) {
            List<SableTrain> reversed = new ArrayList<>(cars);
            Collections.reverse(reversed);
            return reversed;
        }
        return cars;
    }

    private static Train mirror(SableTrain head, List<SableTrain> cars) {
        List<SableTrain> railed = cars.stream().filter(CreateTrainShadow::onRails).toList();
        Train shadow = SHADOWS.get(head.id());
        if (railed.isEmpty()) {
            return shadow;
        }
        List<Member> layout = layoutOf(railed);
        if (shadow == null) {
            shadow = create(head, railed, layout);
        } else if (!layout.equals(LAYOUTS.get(head.id()))) {
            rebuild(shadow, railed, layout);
        }
        update(shadow, head, cars, railed, layout);
        return shadow;
    }

    private static boolean onRails(SableTrain car) {
        RailCarriage rail = railOf(car);
        return rail != null && rail.graph() != null && rail.leading() != null && rail.trailing() != null;
    }

    private static RailCarriage railOf(SableTrain car) {
        return car.car() == null ? null : car.car().carriage();
    }

    private static List<Member> layoutOf(List<SableTrain> railed) {
        List<Member> layout = new ArrayList<>(railed.size());
        for (int i = 0; i < railed.size(); i++) {
            layout.add(new Member(railed.get(i).id(), reversed(railed, i), bogeyCount(railed.get(i)) >= 2));
        }
        return layout;
    }

    private static boolean reversed(List<SableTrain> railed, int index) {
        if (railed.size() < 2) {
            return false;
        }
        RailCarriage rail = railOf(railed.get(index));
        Vec3 lead = rail.leadingPos();
        Vec3 trail = rail.trailingPos();
        boolean last = index == railed.size() - 1;
        Vec3 neighbour = centerOf(railOf(railed.get(last ? index - 1 : index + 1)));
        if (lead == null || trail == null || neighbour == null) {
            return false;
        }
        boolean leadNearer = lead.distanceToSqr(neighbour) < trail.distanceToSqr(neighbour);
        return last != leadNearer;
    }

    private static Vec3 centerOf(RailCarriage rail) {
        Vec3 lead = rail.leadingPos();
        Vec3 trail = rail.trailingPos();
        return lead == null || trail == null ? null : lead.add(trail).scale(0.5);
    }

    private static Train create(SableTrain head, List<SableTrain> railed, List<Member> layout) {
        Train shadow = new Train(head.id(), head.owner(), railOf(railed.get(0)).graph(), buildCarriages(railed, layout),
                spacingsOf(railed, layout), false, 0);
        shadow.name = nameOf(head);
        shadow.mapColorIndex = head.mapColorIndex();
        shadow.icon = iconOf(head);
        SHADOWS.put(head.id(), shadow);
        LAYOUTS.put(head.id(), layout);
        Create.RAILWAYS.trains.put(head.id(), shadow);
        CatnipServices.NETWORK.sendToAllClients(new AddTrainPacket(shadow));
        return shadow;
    }

    // the stress array create sized off the old spacing is only read by its own tick, and a shadow is never ticked
    private static void rebuild(Train shadow, List<SableTrain> railed, List<Member> layout) {
        shadow.carriages = buildCarriages(railed, layout);
        shadow.carriages.forEach(carriage -> carriage.setTrain(shadow));
        shadow.carriageSpacing = spacingsOf(railed, layout);
        LAYOUTS.put(shadow.id, layout);
        CatnipServices.NETWORK.sendToAllClients(new AddTrainPacket(shadow));
    }

    private static List<Carriage> buildCarriages(List<SableTrain> railed, List<Member> layout) {
        List<Carriage> carriages = new ArrayList<>(railed.size());
        for (int i = 0; i < railed.size(); i++) {
            carriages.add(buildCarriage(railed.get(i), railOf(railed.get(i)), layout.get(i).reversed()));
        }
        return carriages;
    }

    private static List<Integer> spacingsOf(List<SableTrain> railed, List<Member> layout) {
        List<Integer> spacing = new ArrayList<>(Math.max(0, railed.size() - 1));
        for (int i = 1; i < railed.size(); i++) {
            Vec3 back = endOf(railOf(railed.get(i - 1)), !layout.get(i - 1).reversed());
            Vec3 front = endOf(railOf(railed.get(i)), layout.get(i).reversed());
            spacing.add(back == null || front == null ? 1 : (int) Math.min(MAX_CAR_GAP, Math.round(back.distanceTo(front))));
        }
        return spacing;
    }

    private static Vec3 endOf(RailCarriage rail, boolean trailing) {
        return trailing ? rail.trailingPos() : rail.leadingPos();
    }

    private static void update(Train shadow, SableTrain head, List<SableTrain> cars, List<SableTrain> railed,
                               List<Member> layout) {
        Create.RAILWAYS.trains.putIfAbsent(shadow.id, shadow);
        boolean headReversed = false;
        for (int i = 0; i < railed.size(); i++) {
            SableTrain car = railed.get(i);
            RailCarriage rail = railOf(car);
            boolean reversed = layout.get(i).reversed();
            seedDimensional(shadow.carriages.get(i), car, rail, reversed);
            copyPoints(shadow.carriages.get(i), car, rail, reversed);
            headReversed |= car == head && reversed;
        }
        shadow.graph = railOf(railed.get(0)).graph();
        shadow.speed = head.speed();
        shadow.targetSpeed = head.targetSpeed();
        shadow.derailed = cars.stream().anyMatch(SableTrain::isDerailed);
        shadow.currentlyBackwards = (head.axleSign() < 0.0) != headReversed;
        shadow.throttle = throttleOf(shadow, head);
        GlobalStation station = stationOf(head, cars);
        shadow.currentStation = station == null ? null : station.id;
        mirrorSignals(shadow, cars);
        mirrorSchedule(shadow, head, station);
        mirrorStationEvents(shadow, head, station);
        mirrorReservation(shadow, station);
        refreshMapIdentity(shadow, head);
    }

    // create only sets a stations nearest train from navigation.tick, which never runs for a shadow
    private static void mirrorReservation(Train shadow, GlobalStation parkedAt) {
        GlobalStation claim = parkedAt != null ? parkedAt : shadow.navigation.destination;
        GlobalStation was = RESERVED.get(shadow.id);
        if (was != null && was != claim) {
            was.cancelReservation(shadow);
        }
        if (claim == null) {
            RESERVED.remove(shadow.id);
            return;
        }
        claim.reserveFor(shadow);
        RESERVED.put(shadow.id, claim);
    }

    private static void releaseReservation(UUID id, Train shadow) {
        GlobalStation held = RESERVED.remove(id);
        if (held != null && shadow != null) {
            held.cancelReservation(shadow);
        }
    }

    private static GlobalStation stationOf(SableTrain head, List<SableTrain> cars) {
        if (stoppedAt(head) != null) {
            return head.currentStation();
        }
        for (SableTrain car : cars) {
            if (stoppedAt(car) != null) {
                return car.currentStation();
            }
        }
        return null;
    }

    private static GlobalStation stoppedAt(SableTrain car) {
        return car.stationState() == SableTrain.StationState.STOPPED ? car.currentStation() : null;
    }

    private static double throttleOf(Train shadow, SableTrain train) {
        float top = shadow.maxSpeed();
        double cruise = Math.max(Math.abs(train.targetSpeed()), Math.abs(train.speed()));
        if (cruise > 0.0) {
            LAST_CRUISE.put(train.id(), cruise);
        } else {
            cruise = LAST_CRUISE.getOrDefault(train.id(), 0.0);
        }
        if (top <= 0.0f || cruise <= 0.0) {
            return 1.0;
        }
        return Math.min(1.0, cruise / top);
    }

    private static void mirrorStationEvents(Train shadow, SableTrain train, GlobalStation station) {
        GlobalStation was = PARKED.get(train.id());
        UUID wasId = was == null ? null : was.id;
        UUID nowId = station == null ? null : station.id;
        if (Objects.equals(wasId, nowId)) {
            return;
        }
        if (was != null) {
            CrnTrainEvents.departed(shadow, was);
        }
        if (station == null) {
            PARKED.remove(train.id());
            return;
        }
        PARKED.put(train.id(), station);
        CrnTrainEvents.arrived(shadow, station);
    }

    private static void mirrorSignals(Train shadow, List<SableTrain> cars) {
        shadow.occupiedSignalBlocks.clear();
        for (SableTrain car : cars) {
            for (UUID group : TrainSignals.groupsOf(car.id())) {
                shadow.occupiedSignalBlocks.put(group, null);
            }
        }
    }

    // without this every moving cart reads as manually driven, since create decides that on the runtime having no schedule
    private static void mirrorSchedule(Train shadow, SableTrain train, GlobalStation station) {
        SableScheduleRuntime runtime = SableScheduleRuntime.get(train.id());
        ScheduleRuntime mirror = shadow.runtime;
        boolean running = runtime != null && runtime.schedule != null && !runtime.completed;
        if (!running) {
            mirror.schedule = null;
            mirror.paused = runtime != null && runtime.paused;
            setDestination(shadow.navigation, null, 0.0);
            return;
        }

        Schedule translated = translate(train, runtime.schedule);
        mirror.schedule = translated.entries.isEmpty() ? PRESENT : translated;
        mirror.paused = runtime.paused;
        mirror.completed = runtime.completed;
        mirror.state = mirrorState(runtime.state);
        mirror.currentEntry = runtime.currentEntry;
        sizePredictions(mirror);

        double remaining = runtime.remainingToTarget();
        GlobalStation target = station == null ? runtime.targetStation() : null;
        boolean keep = remaining == RouteDistance.UNKNOWN && target != null && target == shadow.navigation.destination;
        setDestination(shadow.navigation, target, keep ? shadow.navigation.distanceToDestination : Math.max(0.0, remaining == RouteDistance.UNKNOWN ? 0.0 : remaining));
    }

    // our runtime has a turntable state creates enum never had, and a blind valueof threw here every tick
    private static ScheduleRuntime.State mirrorState(SableScheduleRuntime.State state) {
        return state == SableScheduleRuntime.State.TURNTABLE
                ? ScheduleRuntime.State.IN_TRANSIT
                : ScheduleRuntime.State.valueOf(state.name());
    }

    private static void setDestination(Navigation navigation, GlobalStation destination, double remaining) {
        navigation.destination = destination;
        if (destination == null) {
            navigation.distanceToDestination = 0.0;
            navigation.distanceStartedAt = 0.0;
            return;
        }
        navigation.distanceToDestination = remaining;
        if (remaining > navigation.distanceStartedAt) {
            navigation.distanceStartedAt = remaining;
        }
    }

    private static void sizePredictions(ScheduleRuntime mirror) {
        List<Integer> ticks = mirror.predictionTicks;
        ticks.clear();
        for (int i = 0; i < mirror.schedule.entries.size(); i++) {
            ticks.add(-1);
        }
    }

    private static Schedule translate(SableTrain train, com.lycoris.loconautics.content.schedule.Schedule source) {
        TranslatedSchedule cached = SCHEDULES.get(train.id());
        if (cached != null && cached.source() == source) {
            return cached.translated();
        }
        HolderLookup.Provider registries = train.level().registryAccess();
        Schedule translated = Schedule.fromTag(registries, source.write(registries));
        SCHEDULES.put(train.id(), new TranslatedSchedule(source, translated));
        return translated;
    }

    private static Carriage buildCarriage(SableTrain train, RailCarriage rail, boolean reversed) {
        List<SableTrain.Bogey> bogeys = train.car().bogeys();
        if (bogeys.size() >= 2) {
            SableTrain.Bogey lead = bogeys.get(reversed ? 0 : bogeys.size() - 1);
            SableTrain.Bogey trail = bogeys.get(reversed ? bogeys.size() - 1 : 0);
            Carriage carriage = new Carriage(bogeyOf(train, lead), bogeyOf(train, trail), spacingOf(rail));
            seedDimensional(carriage, train, rail, reversed);
            return carriage;
        }
        CarriageBogey single = bogeyOf(train, bogeys.isEmpty() ? null : bogeys.get(0));
        Carriage carriage = new Carriage(single, null, spacingOf(rail));
        seedDimensional(carriage, train, rail, reversed);
        return carriage;
    }

    private static CarriageBogey bogeyOf(SableTrain train, SableTrain.Bogey bogey) {
        return new CarriageBogey(bogeyBlock(train, bogey), false, null, new TravellingPoint(), new TravellingPoint());
    }

    private static AbstractBogeyBlock<?> bogeyBlock(SableTrain train, SableTrain.Bogey bogey) {
        if (bogey == null) {
            return AllBlocks.SMALL_BOGEY.get();
        }
        ServerSubLevel sub = SubLevelData.resolve(train.level(), train.car().subLevelId());
        if (sub == null) {
            return AllBlocks.SMALL_BOGEY.get();
        }
        BlockPos cell = sub.getPlot().getCenterBlock().offset(bogey.localPos());
        if (sub.getLevel().getBlockState(cell).getBlock() instanceof AbstractBogeyBlock<?> block) {
            return block;
        }
        return AllBlocks.SMALL_BOGEY.get();
    }

    private static int bogeyCount(SableTrain train) {
        return train.car() == null ? 0 : train.car().bogeys().size();
    }

    private static void copyPoints(Carriage carriage, SableTrain train, RailCarriage rail, boolean reversed) {
        List<SableTrain.Bogey> bogeys = train.car().bogeys();
        if (carriage.bogeys.getSecond() != null && bogeys.size() >= 2) {
            RailCarriage leadRail = bogeys.get(reversed ? 0 : bogeys.size() - 1).rail();
            RailCarriage trailRail = bogeys.get(reversed ? bogeys.size() - 1 : 0).rail();
            copyRail(leadRail, carriage.leadingBogey(), reversed);
            copyRail(trailRail, carriage.trailingBogey(), reversed);
            return;
        }
        copyPoint(reversed ? rail.trailing() : rail.leading(), carriage.getLeadingPoint());
        copyPoint(reversed ? rail.leading() : rail.trailing(), carriage.getTrailingPoint());
    }

    private static void copyRail(RailCarriage rail, CarriageBogey bogey, boolean reversed) {
        if (rail == null || bogey == null) {
            return;
        }
        copyPoint(reversed ? rail.trailing() : rail.leading(), bogey.leading());
        copyPoint(reversed ? rail.leading() : rail.trailing(), bogey.trailing());
    }

    private static void seedDimensional(Carriage carriage, SableTrain train, RailCarriage rail, boolean reversed) {
        Carriage.DimensionalCarriageEntity dimensional = carriage.getDimensional(train.level().dimension());
        Vec3 lead = endOf(rail, reversed);
        Vec3 trail = endOf(rail, !reversed);
        if (lead == null && trail == null) {
            return;
        }
        Vec3 front = lead == null ? trail : lead;
        Vec3 back = trail == null ? lead : trail;
        dimensional.positionAnchor = front.add(back).scale(0.5);
        dimensional.rotationAnchors.setFirst(front);
        dimensional.rotationAnchors.setSecond(back);
    }

    private static void copyPoint(TravellingPoint from, TravellingPoint to) {
        if (from == null || to == null) {
            return;
        }
        to.node1 = from.node1;
        to.node2 = from.node2;
        to.edge = from.edge;
        to.position = from.position;
        to.upsideDown = from.upsideDown;
    }

    private static int spacingOf(RailCarriage rail) {
        return Math.max(1, (int) Math.round(rail.bogeySpacing()));
    }

    private static void refreshMapIdentity(Train shadow, SableTrain train) {
        Component name = nameOf(train);
        TrainIconType icon = iconOf(train);
        if (name.equals(shadow.name) && Objects.equals(shadow.owner, train.owner())
                && shadow.mapColorIndex == train.mapColorIndex() && shadow.icon == icon) {
            return;
        }
        shadow.name = name;
        shadow.owner = train.owner();
        shadow.mapColorIndex = train.mapColorIndex();
        shadow.icon = icon;
        CatnipServices.NETWORK.sendToAllClients(new AddTrainPacket(shadow));
    }

    private static TrainIconType iconOf(SableTrain train) {
        if (train.iconType().isEmpty()) {
            return TrainIconType.getDefault();
        }
        ResourceLocation id = ResourceLocation.tryParse(train.iconType());
        return id == null ? TrainIconType.getDefault() : TrainIconType.byId(id);
    }

    private static Component nameOf(SableTrain train) {
        if (train.level() instanceof ServerLevel level) {
            try {
                String name = SableTrainSpawner.trainName(level, train);
                if (name != null && !name.isBlank()) {
                    return Component.literal(name);
                }
            } catch (NoClassDefFoundError ignored) {
            }
        }
        return Component.literal("Cart " + train.id().toString().substring(0, 8));
    }

    private CreateTrainShadow() {
    }
}
