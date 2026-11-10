package com.lycoris.loconautics.content.steelcable;

import com.lycoris.loconautics.common.strand.StrandAttachments;
import com.lycoris.loconautics.common.strand.StrandHolder;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;

import java.util.UUID;

public interface SteelCableHolder extends StrandHolder {

    double WINCH_DRUM_RADIUS = 0.375;

    default boolean isWinch() {
        return false;
    }

    @Override
    default double strandStartTrim() {
        return isWinch() ? WINCH_DRUM_RADIUS : 0.0;
    }

    default int strandCapacity() {
        return 1;
    }

    default boolean acceptsFluidLinks() {
        return false;
    }

    default boolean acceptsPlainCables() {
        return true;
    }

    default boolean accepts(SteelCableType type) {
        return type.carriesFluid() ? acceptsFluidLinks() : acceptsPlainCables();
    }

    default boolean canAttachStrand() {
        return StrandAttachments.isFree(this, SteelCableHolderBehaviour.TYPE);
    }

    default void onStrandAttached(SteelCableStrand strand) {
        StrandAttachments.attach(this, SteelCableHolderBehaviour.TYPE, strand.id);
        if (this instanceof SmartBlockEntity sbe) {
            sbe.notifyUpdate();
        }
    }

    default void onStrandDetached(UUID strandId) {
        StrandAttachments.detach(this, SteelCableHolderBehaviour.TYPE, strandId);
        if (this instanceof SmartBlockEntity sbe) {
            sbe.notifyUpdate();
        }
    }

    default boolean holdsStrand(UUID strandId) {
        return StrandAttachments.holds(this, SteelCableHolderBehaviour.TYPE, strandId);
    }
}
