package com.lycoris.loconautics.content.trainlookup.history;

public enum HistoryEvent {
    FIND_VIEW,
    TELEPORT,
    RETRIEVE_FULL,
    RETRIEVE_CARRIAGE,
    RELOCATE_PLACE,
    RELOCATE_CARRIAGE,
    CONNECTOR_DETACH,
    RIDER_EJECTED,
    REMOVE;

    public static HistoryEvent byName(String name) {
        for (HistoryEvent event : values()) {
            if (event.name().equalsIgnoreCase(name)) {
                return event;
            }
        }
        return null;
    }
}
