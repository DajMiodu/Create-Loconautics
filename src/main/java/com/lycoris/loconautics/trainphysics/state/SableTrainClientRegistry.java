package com.lycoris.loconautics.trainphysics.state;

import net.minecraft.core.BlockPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Quaternionf;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@OnlyIn(Dist.CLIENT)
public final class SableTrainClientRegistry {

    public record TrainMarker(UUID trainId, double bogeySpacing, double consistLength, boolean derailed, boolean brakeHard, double speed,
                              double wheelDrive, int offTrackBogeys, Map<BlockPos, Float> drivenBogeys,
                              float slipFactor, Set<BlockPos> slippingBogeys, double topSpeed,
                              Set<BlockPos> workingBogeys, boolean endOfTrack) {}

    private static final Map<UUID, TrainMarker> TRAINS = new ConcurrentHashMap<>();

    private static final Set<BlockPos> PARKED_STATIONS = ConcurrentHashMap.newKeySet();

    private static final Map<BlockPos, String> PARKED_NAMES = new ConcurrentHashMap<>();

    private static final Map<BlockPos, int[]> PARKED_CARRIAGES = new ConcurrentHashMap<>();

    private static final Map<BlockPos, Integer> PARKED_MAP_COLORS = new ConcurrentHashMap<>();

    private static final Map<BlockPos, String> PARKED_ICONS = new ConcurrentHashMap<>();

    private static final Map<BlockPos, Float> STATION_APPROACH = new ConcurrentHashMap<>();

    private static final int[] NO_CARRIAGES = new int[0];

    private SableTrainClientRegistry() {
    }

    public static void setStationParked(BlockPos pos, boolean parked, String name, List<Integer> carriages,
                                        int mapColorIndex, String iconType, float approach) {
        if (parked || approach < Float.MAX_VALUE) {
            BlockPos key = pos.immutable();
            if (parked) {
                PARKED_STATIONS.add(key);
            } else {
                PARKED_STATIONS.remove(key);
            }
            STATION_APPROACH.put(key, parked ? 0.0f : approach);
            PARKED_NAMES.put(key, name == null ? "" : name);
            PARKED_MAP_COLORS.put(key, mapColorIndex);
            PARKED_ICONS.put(key, iconType == null ? "" : iconType);
            int[] spans = NO_CARRIAGES;
            if (carriages != null && !carriages.isEmpty()) {
                spans = new int[carriages.size()];
                for (int i = 0; i < spans.length; i++) {
                    spans[i] = carriages.get(i);
                }
            }
            PARKED_CARRIAGES.put(key, spans);
        } else {
            PARKED_STATIONS.remove(pos);
            PARKED_NAMES.remove(pos);
            PARKED_CARRIAGES.remove(pos);
            PARKED_MAP_COLORS.remove(pos);
            PARKED_ICONS.remove(pos);
            STATION_APPROACH.remove(pos);
        }
    }

    public static boolean stationHasTrain(BlockPos pos) {
        return STATION_APPROACH.containsKey(pos);
    }

    public static float stationApproach(BlockPos pos) {
        return STATION_APPROACH.getOrDefault(pos, Float.MAX_VALUE);
    }

    public static int stationMapColorIndex(BlockPos pos) {
        return PARKED_MAP_COLORS.getOrDefault(pos, 0);
    }

    public static String stationIconType(BlockPos pos) {
        return PARKED_ICONS.getOrDefault(pos, "");
    }

    public static boolean isStationParked(BlockPos pos) {
        return PARKED_STATIONS.contains(pos);
    }

    public static String stationTrainName(BlockPos pos) {
        return PARKED_NAMES.getOrDefault(pos, "");
    }

    public static int[] stationCarriages(BlockPos pos) {
        return PARKED_CARRIAGES.getOrDefault(pos, NO_CARRIAGES);
    }

    public static Set<BlockPos> parkedStations() {
        return Set.copyOf(PARKED_STATIONS);
    }

    public static void put(UUID subLevelId, UUID trainId, double bogeySpacing, double consistLength, boolean derailed, boolean brakeHard,
                           double speed, double wheelDrive, int offTrackBogeys, Map<BlockPos, Float> drivenBogeys,
                           float slipFactor, Set<BlockPos> slippingBogeys, double topSpeed,
                           Set<BlockPos> workingBogeys, boolean endOfTrack) {
        TRAINS.put(subLevelId, new TrainMarker(trainId, bogeySpacing, consistLength, derailed, brakeHard, speed, wheelDrive,
                offTrackBogeys, Map.copyOf(drivenBogeys), slipFactor, Set.copyOf(slippingBogeys), topSpeed,
                Set.copyOf(workingBogeys), endOfTrack));
    }

    public static void remove(UUID subLevelId) {
        TRAINS.remove(subLevelId);
        LEANS.remove(subLevelId);
    }

    private record Lean(Quaternionf turn, long receivedMillis) {}

    private static final Map<UUID, Lean> LEANS = new ConcurrentHashMap<>();
    private static final long LEAN_STALE_MILLIS = 2500L;

    public static void putLean(UUID subLevelId, Quaternionf turn) {
        LEANS.put(subLevelId, new Lean(turn, System.currentTimeMillis()));
    }

    public static Quaternionf lean(UUID subLevelId) {
        Lean lean = LEANS.get(subLevelId);
        if (lean == null) {
            return null;
        }
        if (System.currentTimeMillis() - lean.receivedMillis() > LEAN_STALE_MILLIS) {
            LEANS.remove(subLevelId);
            return null;
        }
        return lean.turn();
    }

    public static void clear() {
        TRAINS.clear();
        LEANS.clear();
        PARKED_STATIONS.clear();
        PARKED_NAMES.clear();
        PARKED_CARRIAGES.clear();
        PARKED_MAP_COLORS.clear();
        PARKED_ICONS.clear();
        STATION_APPROACH.clear();
    }

    public static TrainMarker get(UUID subLevelId) {
        return TRAINS.get(subLevelId);
    }

    public static UUID trainIdOf(UUID subLevelId) {
        TrainMarker marker = subLevelId == null ? null : TRAINS.get(subLevelId);
        return marker == null ? null : marker.trainId();
    }

    public static boolean isTrain(UUID subLevelId) {
        return subLevelId != null && TRAINS.containsKey(subLevelId);
    }

    public static boolean isTrainId(UUID trainId) {
        return trainId != null && TRAINS.values().stream().anyMatch(marker -> trainId.equals(marker.trainId()));
    }

    public static java.util.Set<UUID> ids() {
        return java.util.Set.copyOf(TRAINS.keySet());
    }
}
