package com.lycoris.loconautics.common.turntable.sequence;

import com.simibubi.create.foundation.gui.AllGuiTextures;
import io.netty.buffer.ByteBuf;
import net.createmod.catnip.codecs.stream.CatnipStreamCodecBuilders;
import net.createmod.catnip.lang.Lang;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;

import java.util.ArrayList;
import java.util.List;

public enum TurntableInstructionType {

    TURN_ANGLE("angle", AllGuiTextures.SEQUENCER_INSTRUCTION, true, true, 360, 45, 90),
    DELAY("duration", AllGuiTextures.SEQUENCER_DELAY, true, false, 600, 20, 10),
    AWAIT("", AllGuiTextures.SEQUENCER_AWAIT),
    END("", AllGuiTextures.SEQUENCER_END),
    SCHEDULE("", AllGuiTextures.SEQUENCER_INSTRUCTION);

    public static final StreamCodec<ByteBuf, TurntableInstructionType> STREAM_CODEC =
            CatnipStreamCodecBuilders.ofEnum(TurntableInstructionType.class);

    public final String translationKey;
    public final String descriptiveTranslationKey;
    public final String parameterKey;
    public final boolean hasValueParameter;
    public final boolean hasSpeedParameter;
    public final AllGuiTextures background;
    public final int maxValue;
    public final int shiftStep;
    public final int defaultValue;

    TurntableInstructionType(String parameterName, AllGuiTextures background) {
        this(parameterName, background, false, false, -1, -1, -1);
    }

    TurntableInstructionType(String parameterName, AllGuiTextures background, boolean hasValueParameter,
                          boolean hasSpeedParameter, int maxValue, int shiftStep, int defaultValue) {
        this.hasValueParameter = hasValueParameter;
        this.hasSpeedParameter = hasSpeedParameter;
        this.background = background;
        this.maxValue = maxValue;
        this.shiftStep = shiftStep;
        this.defaultValue = defaultValue;
        this.translationKey = "loconautics.gui.turntable_bearing.instruction." + Lang.asId(name());
        this.descriptiveTranslationKey = translationKey + ".descriptive";
        this.parameterKey = translationKey + "." + parameterName;
    }

    public static List<Component> getOptions() {
        List<Component> options = new ArrayList<>();
        for (TurntableInstructionType entry : values()) {
            options.add(Component.translatable(entry.descriptiveTranslationKey));
        }
        return options;
    }

    public String formatValue(int value) {
        if (this == TURN_ANGLE) {
            return value + Component.translatable("create.generic.unit.degrees").getString();
        }
        if (this == DELAY) {
            return value >= 20 ? (value / 20) + "s" : value + "t";
        }
        return String.valueOf(value);
    }
}
