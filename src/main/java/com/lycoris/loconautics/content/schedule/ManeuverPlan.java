package com.lycoris.loconautics.content.schedule;

import com.lycoris.loconautics.common.turntable.TurntableRegistry;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

final class ManeuverPlan {

    record Leg(int rel, @Nullable TurntableRegistry.Exit hop, boolean reversal) {
    }

    private final List<Leg> legs = new ArrayList<>();
    private int index;

    int currentRel() {
        return index < legs.size() ? legs.get(index).rel() : 0;
    }

    void advance() {
        index++;
    }

    static boolean backsOut(TurntableRegistry.Exit hop) {
        return hop.movingHead().equals(hop.entryHead());
    }

    void record(@Nullable TurntableRegistry.Exit hop, int rel) {
        place(new Leg(rel, hop, false));
        if (hop == null) {
            return;
        }
        // an entry hop only reaches the plate, the way out is known once the exit is picked
        int after = !hop.entry() && backsOut(hop) ? -rel : rel;
        legs.add(new Leg(after, null, false));
    }

    // leg one stops past the switch, leg two runs the other way
    void recordReversal(int rel) {
        place(new Leg(rel, null, true));
        legs.add(new Leg(-rel, null, false));
    }

    private void place(Leg leg) {
        while (legs.size() <= index) {
            legs.add(leg);
        }
        legs.set(index, leg);
        while (legs.size() > index + 1) {
            legs.remove(legs.size() - 1);
        }
    }

    @Override
    public String toString() {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < legs.size(); i++) {
            Leg leg = legs.get(i);
            text.append(i == index ? "[" : "").append(leg.rel() > 0 ? "+" : "-")
                    .append(leg.reversal() ? "r" : "")
                    .append(leg.hop() == null ? "" : leg.hop().entry() ? "e" : backsOut(leg.hop()) ? "b" : "s")
                    .append(i == index ? "]" : "").append(i + 1 < legs.size() ? " " : "");
        }
        return text.toString();
    }
}
