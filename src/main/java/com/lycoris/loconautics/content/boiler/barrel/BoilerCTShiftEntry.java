package com.lycoris.loconautics.content.boiler.barrel;

import com.simibubi.create.foundation.block.connected.CTSpriteShiftEntry;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

public class BoilerCTShiftEntry extends CTSpriteShiftEntry {

    private static final Map<String, BoilerCTShiftEntry> CACHE = new HashMap<>();

    public BoilerCTShiftEntry() {
        super(BoilerCTType.INSTANCE);
    }

    public static BoilerCTShiftEntry get(ResourceLocation original, ResourceLocation connected) {
        String key = original + "->" + connected;
        return CACHE.computeIfAbsent(key, k -> {
            BoilerCTShiftEntry e = new BoilerCTShiftEntry();
            e.set(original, connected);
            return e;
        });
    }

    @Override
    public float getTargetU(float localU, int index) {
        return getTarget().getU(getUnInterpolatedU(getOriginal(), localU));
    }

    @Override
    public float getTargetV(float localV, int index) {
        return getTarget().getV((getUnInterpolatedV(getOriginal(), localV) + index) / BoilerCTType.ROWS);
    }
}
