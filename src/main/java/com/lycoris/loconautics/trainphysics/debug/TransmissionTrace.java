package com.lycoris.loconautics.trainphysics.debug;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.content.transmission.TransmissionBlockEntity;

import java.util.Locale;

// temporary, tag transmit
public final class TransmissionTrace {

    private TransmissionTrace() {
    }

    public static void note(TransmissionBlockEntity be, String what) {
        if (be.getLevel() == null || be.getLevel().isClientSide) {
            return;
        }
        LoconauticsConstants.LOGGER.info("[transmit] {} {} {}#{} power={} reversed={} target={} speed={} source={}@{} network={} flicker={}",
                be.getBlockPos().toShortString(), what, be.getLevel().dimension().location().getPath(),
                System.identityHashCode(be), be.getPower(), be.isReversed(),
                String.format(Locale.ROOT, "%.1f", be.getTargetRpm()),
                String.format(Locale.ROOT, "%.1f", be.getTheoreticalSpeed()), be.hasSource(),
                be.source == null ? "none" : be.source.toShortString(), be.network, be.getFlickerScore());
    }

    public static void removed(TransmissionBlockEntity be) {
        if (be.getLevel() == null || be.getLevel().isClientSide) {
            return;
        }
        LoconauticsConstants.LOGGER.info("[transmit] {} removed", be.getBlockPos().toShortString());
    }
}
