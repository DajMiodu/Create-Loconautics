package com.lycoris.loconautics.compat.railways;

import com.lycoris.loconautics.content.casingpicker.BogeyChoice;
import com.lycoris.loconautics.mixin.compat.railways.BogeyMenuHandlerServerAccessor;
import com.simibubi.create.content.trains.bogey.BogeySizes;
import com.simibubi.create.content.trains.bogey.BogeyStyle;
import com.simibubi.create.content.trains.track.TrackMaterial;
import net.createmod.catnip.data.Pair;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;

// Railways is read by reflection, so it is not needed to build, and a missing method leaves the choice as it was
public final class RailwaysBogeyChoice {

    private static final Method GET_MAPPED = lookup();

    private RailwaysBogeyChoice() {
    }

    private static Method lookup() {
        try {
            return Class.forName("com.railwayteam.railways.registry.CRBogeyStyles")
                    .getMethod("getMapped", BogeyStyle.class, TrackMaterial.TrackType.class, boolean.class);
        } catch (Throwable missing) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    public static BogeyChoice fitTo(BogeyChoice choice, TrackMaterial.TrackType trackType) {
        if (GET_MAPPED == null) {
            return choice;
        }
        Optional<BogeyStyle> mapped;
        try {
            mapped = (Optional<BogeyStyle>) GET_MAPPED.invoke(null, choice.style(), trackType, true);
        } catch (Throwable failed) {
            return choice;
        }
        if (mapped.isEmpty() || mapped.get() == choice.style()) {
            return choice;
        }
        BogeyStyle style = mapped.get();
        BogeySizes.BogeySize size = style.validSizes().contains(choice.size())
                ? choice.size() : style.validSizes().iterator().next();
        return new BogeyChoice(style, size);
    }

    public static BogeyChoice selected(UUID player) {
        Pair<BogeyStyle, BogeySizes.BogeySize> picked = BogeyMenuHandlerServerAccessor.loconautics$getStyle(player);
        BogeyStyle style = picked.getFirst();
        BogeySizes.BogeySize size = picked.getSecond();
        if (size == null || !style.validSizes().contains(size)) {
            size = style.validSizes().iterator().next();
        }
        return new BogeyChoice(style, size);
    }
}
