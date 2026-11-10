package com.lycoris.loconautics.common.turntable.sequence;

import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.ArrayList;
import java.util.List;

public class TurntableInstruction {

    public static final int MAX_INSTRUCTIONS = 5;

    public static final StreamCodec<ByteBuf, TurntableInstruction> STREAM_CODEC = StreamCodec.composite(
            TurntableInstructionType.STREAM_CODEC, i -> i.type,
            TurntableSpeedModifier.STREAM_CODEC, i -> i.modifier,
            ByteBufCodecs.VAR_INT, i -> i.value,
            TurntableInstruction::new);

    public static final StreamCodec<ByteBuf, List<TurntableInstruction>> LIST_STREAM_CODEC =
            STREAM_CODEC.apply(ByteBufCodecs.list(MAX_INSTRUCTIONS));

    public TurntableInstructionType type;
    public TurntableSpeedModifier modifier;
    public int value;

    public TurntableInstruction(TurntableInstructionType type) {
        this(type, TurntableSpeedModifier.FORWARD, type.defaultValue);
    }

    public TurntableInstruction(TurntableInstructionType type, TurntableSpeedModifier modifier, int value) {
        this.type = type;
        this.modifier = modifier;
        this.value = value;
    }

    public static List<TurntableInstruction> defaultList() {
        List<TurntableInstruction> list = new ArrayList<>(MAX_INSTRUCTIONS);
        list.add(new TurntableInstruction(TurntableInstructionType.TURN_ANGLE));
        list.add(new TurntableInstruction(TurntableInstructionType.END));
        return list;
    }

    public int signedSpeedModifier() {
        return type.hasSpeedParameter ? modifier.value : 0;
    }

    public CompoundTag write() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Type", type.name());
        tag.putString("Modifier", modifier.name());
        tag.putInt("Value", value);
        return tag;
    }

    public static TurntableInstruction read(CompoundTag tag) {
        TurntableInstructionType type = enumOrDefault(TurntableInstructionType.class, tag.getString("Type"),
                TurntableInstructionType.END);
        TurntableSpeedModifier modifier = enumOrDefault(TurntableSpeedModifier.class, tag.getString("Modifier"),
                TurntableSpeedModifier.FORWARD);
        return new TurntableInstruction(type, modifier, tag.getInt("Value"));
    }

    public static ListTag writeList(List<TurntableInstruction> instructions) {
        ListTag list = new ListTag();
        for (TurntableInstruction instruction : instructions) {
            list.add(instruction.write());
        }
        return list;
    }

    public static List<TurntableInstruction> readList(ListTag list) {
        List<TurntableInstruction> instructions = new ArrayList<>(MAX_INSTRUCTIONS);
        for (int i = 0; i < list.size() && i < MAX_INSTRUCTIONS; i++) {
            instructions.add(read(list.getCompound(i)));
        }
        return instructions.isEmpty() ? defaultList() : instructions;
    }

    public static boolean isScheduleDriven(List<TurntableInstruction> instructions) {
        return !instructions.isEmpty() && instructions.get(0).type == TurntableInstructionType.SCHEDULE;
    }

    public static List<TurntableInstruction> sanitize(List<TurntableInstruction> instructions) {
        if (instructions.isEmpty()) {
            return defaultList();
        }
        for (TurntableInstruction instruction : instructions) {
            if (instruction.type == TurntableInstructionType.SCHEDULE) {
                List<TurntableInstruction> only = new ArrayList<>(1);
                only.add(new TurntableInstruction(TurntableInstructionType.SCHEDULE));
                return only;
            }
        }
        List<TurntableInstruction> copy = new ArrayList<>(instructions.subList(0,
                Math.min(instructions.size(), MAX_INSTRUCTIONS)));
        for (TurntableInstruction instruction : copy) {
            if (instruction.type.hasValueParameter) {
                instruction.value = Math.clamp(instruction.value, 1, instruction.type.maxValue);
            }
        }
        return copy;
    }

    private static <E extends Enum<E>> E enumOrDefault(Class<E> clazz, String name, E fallback) {
        for (E value : clazz.getEnumConstants()) {
            if (value.name().equals(name)) {
                return value;
            }
        }
        return fallback;
    }
}
