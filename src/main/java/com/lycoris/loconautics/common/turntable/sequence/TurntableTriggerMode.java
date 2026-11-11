package com.lycoris.loconautics.common.turntable.sequence;

import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.INamedIconOptions;
import com.simibubi.create.foundation.gui.AllIcons;
import net.createmod.catnip.lang.Lang;

public enum TurntableTriggerMode implements INamedIconOptions {

    PULSE(AllIcons.I_PLAY),
    HOLD(AllIcons.I_REFRESH),
    ALTERNATE(AllIcons.I_FLIP);

    public final String translationKey;
    private final AllIcons icon;

    TurntableTriggerMode(AllIcons icon) {
        this.icon = icon;
        this.translationKey = "loconautics.gui.turntable_bearing.trigger." + Lang.asId(name());
    }

    @Override
    public AllIcons getIcon() {
        return icon;
    }

    @Override
    public String getTranslationKey() {
        return translationKey;
    }
}
