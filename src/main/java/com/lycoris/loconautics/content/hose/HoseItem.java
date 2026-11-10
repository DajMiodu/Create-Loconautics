package com.lycoris.loconautics.content.hose;

import com.lycoris.loconautics.config.LoconauticsConfig;
import com.lycoris.loconautics.content.steelcable.SteelCableItem;
import com.lycoris.loconautics.content.steelcable.SteelCableType;

public class HoseItem extends SteelCableItem {

    public HoseItem(Properties properties) {
        super(properties);
    }

    @Override
    public SteelCableType cableType() {
        return SteelCableType.HOSE_LINK;
    }

    @Override
    public double maxRange() {
        return LoconauticsConfig.HOSE_MAX_LENGTH.get();
    }
}
