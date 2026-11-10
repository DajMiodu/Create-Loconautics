package com.lycoris.loconautics.common.railway;

import com.simibubi.create.content.trains.entity.TravellingPoint.ITrackSelector;
import com.simibubi.create.content.trains.graph.TrackEdge;
import com.simibubi.create.content.trains.graph.TrackNode;
import com.simibubi.create.content.trains.graph.TrackNodeLocation;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class PointOverrides {

    private static final Map<String, String> POINTS = new ConcurrentHashMap<>();

    private PointOverrides() {
    }

    public static String nodeId(TrackNodeLocation location) {
        return location.getDimension().location() + "@" + location.getX() + "," + location.getY() + ","
                + location.getZ();
    }

    public static void set(String junction, String toward) {
        POINTS.put(junction, toward);
    }

    public static void clear(String junction) {
        POINTS.remove(junction);
    }

    public static Map<String, String> all() {
        return Map.copyOf(POINTS);
    }

    public static void clearAll() {
        POINTS.clear();
    }

    public static ITrackSelector wrap(ITrackSelector original) {
        return (graph, pair) -> {
            Map.Entry<TrackNode, TrackEdge> forced = choose(pair.getSecond());
            return forced != null ? forced : original.apply(graph, pair);
        };
    }

    private static Map.Entry<TrackNode, TrackEdge> choose(List<Map.Entry<TrackNode, TrackEdge>> options) {
        if (POINTS.isEmpty() || options.size() < 2) {
            return null;
        }
        for (Map.Entry<TrackNode, TrackEdge> option : options) {
            String toward = POINTS.get(nodeId(option.getValue().node1.getLocation()));
            if (toward != null && toward.equals(nodeId(option.getKey().getLocation()))) {
                return option;
            }
        }
        return null;
    }
}
