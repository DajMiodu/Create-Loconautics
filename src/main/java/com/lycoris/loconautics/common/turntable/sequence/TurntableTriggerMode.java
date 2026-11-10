package com.lycoris.loconautics.common.turntable.sequence;

import io.netty.buffer.ByteBuf;
import net.createmod.catnip.codecs.stream.CatnipStreamCodecBuilders;
import net.createmod.catnip.lang.Lang;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;

import java.util.ArrayList;
import java.util.List;

public enum TurntableTriggerMode {

    PULSE,
    HOLD,
    ALTERNATE;

    public static final StreamCodec<ByteBuf, TurntableTriggerMode> STREAM_CODEC =
            CatnipStreamCodecBuilders.ofEnum(TurntableTriggerMode.class);

    public final String translationKey;

    TurntableTriggerMode() {
        this.translationKey = "loconautics.gui.turntable_bearing.trigger." + Lang.asId(name());
    }

    public static List<Component> getOptions() {
        List<Component> options = new ArrayList<>();
        for (TurntableTriggerMode entry : values()) {
            options.add(Component.translatable(entry.translationKey));
        }
        return options;
    }
}
