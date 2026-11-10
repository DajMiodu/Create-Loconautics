package com.lycoris.loconautics.trainphysics.rail;

import com.simibubi.create.content.trains.graph.TrackNode;

import java.util.HashMap;
import java.util.Map;
import java.util.function.UnaryOperator;

public final class TurnLog {

    private final Map<Long, TrackNode> crumbs = new HashMap<>();
    private long head;

    public long record(TrackNode node) {
        crumbs.put(head, node);
        return head++;
    }

    public TrackNode at(long seq) {
        return crumbs.get(seq);
    }

    public long head() {
        return head;
    }

    public void pruneBelow(long seq) {
        crumbs.keySet().removeIf(key -> key < seq);
    }

    public void remap(UnaryOperator<TrackNode> live) {
        crumbs.replaceAll((seq, node) -> live.apply(node));
    }

    public void clear() {
        crumbs.clear();
        head = 0L;
    }
}
