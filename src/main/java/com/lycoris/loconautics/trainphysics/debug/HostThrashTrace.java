package com.lycoris.loconautics.trainphysics.debug;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.common.sable.TrackHostFrames;
import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.trainphysics.state.SableTrainRegistry;
import dev.ryanhcode.sable.sublevel.SubLevel;

import java.util.UUID;

import static com.lycoris.loconautics.foundation.LogFormat.shortId;

public final class HostThrashTrace {

    private static final int MAX_LINES = 1500;
    private static int lines;

    private HostThrashTrace() {
    }

    public static void added(SubLevel subLevel) {
        UUID id = subLevel.getUniqueId();
        boolean host = TrackHostFrames.frameOf(id) != null;
        SableTrain train = SableTrainRegistry.bySubLevel(id);
        if (!host && train == null) {
            return;
        }
        emit("added " + (host ? "host" : "car") + " " + shortId(id) + " ghostTrain="
                + (train != null && train.isGhost()));
    }

    public static void unloaded(SubLevel subLevel) {
        UUID id = subLevel.getUniqueId();
        boolean host = TrackHostFrames.frameOf(id) != null;
        SableTrain train = SableTrainRegistry.bySubLevel(id);
        if (!host && train == null) {
            return;
        }
        emit("unloaded " + (host ? "host" : "car") + " " + shortId(id));
    }

    public static void embody(SableTrain train, boolean hostMissing, boolean footprint) {
        emit("embody train=" + shortId(train.id()) + " hostMissing=" + hostMissing + " footprintLoaded=" + footprint);
    }

    public static void snatch(SableTrain train, boolean hostMissing, String cursorChunk) {
        emit("snatch train=" + shortId(train.id()) + " hostMissing=" + hostMissing + " cursorChunk=" + cursorChunk);
    }

    private static void emit(String text) {
        if (lines >= MAX_LINES) {
            return;
        }
        lines++;
        LoconauticsConstants.LOGGER.info("[hostthrash] {}", text);
    }
}
