package com.lycoris.loconautics.compat.create;

import com.google.common.collect.ImmutableList;
import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.foundation.LoconauticsLang;
import com.lycoris.loconautics.registry.LoconauticsRegistries;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.trains.graph.DiscoveredPath;
import com.simibubi.create.content.trains.schedule.Schedule;
import com.simibubi.create.content.trains.schedule.ScheduleRuntime;
import com.simibubi.create.content.trains.schedule.ScheduleRuntime.State;
import com.simibubi.create.content.trains.schedule.destination.ScheduleInstruction;
import com.simibubi.create.foundation.gui.ModularGuiLineBuilder;
import net.createmod.catnip.data.Pair;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class ManeuverEditorInstruction extends ScheduleInstruction {

    public enum Kind {
        DECOUPLE("decouple", true, 1, 1, 65, 1, 1, ""),
        MANEUVER("maneuver", true, LoconauticsConstants.MANEUVER_DEFAULT_PERCENT, 1,
                LoconauticsConstants.MANEUVER_MAX_PERCENT + 1, 1, 5, "%"),
        COUPLE("couple", true, 32, 4, 129, 4, 16, "m");

        private final String path;
        private final boolean hasValue;
        private final int start;
        private final int min;
        private final int max;
        private final int step;
        private final int shiftStep;
        private final String suffix;

        Kind(String path, boolean hasValue, int start, int min, int max, int step, int shiftStep, String suffix) {
            this.path = path;
            this.hasValue = hasValue;
            this.start = start;
            this.min = min;
            this.max = max;
            this.step = step;
            this.shiftStep = shiftStep;
            this.suffix = suffix;
        }
    }

    private static final int OPEN_KNUCKLE_MODEL = 1;

    private final Kind kind;

    public ManeuverEditorInstruction(Kind kind) {
        super();
        this.kind = kind;
        if (kind.hasValue) {
            data.putInt("Value", kind.start);
        }
    }

    public static void register() {
        for (Kind kind : Kind.values()) {
            Schedule.INSTRUCTION_TYPES.add(Pair.of(LoconauticsConstants.id(kind.path),
                    () -> new ManeuverEditorInstruction(kind)));
        }
    }

    @Override
    public Pair<ItemStack, Component> getSummary() {
        return Pair.of(icon(), line());
    }

    @Override
    public ResourceLocation getId() {
        return LoconauticsConstants.id(kind.path);
    }

    @Override
    public ItemStack getSecondLineIcon() {
        return icon();
    }

    @Override
    public boolean supportsConditions() {
        return false;
    }

    @Override
    public List<Component> getTitleAs(String type) {
        return ImmutableList.of(line().copy().withStyle(ChatFormatting.GOLD));
    }

    private Component line() {
        if (!kind.hasValue) {
            return LoconauticsLang.translate("schedule.instruction." + kind.path + ".summary").component();
        }
        int value = intData("Value");
        String key = "schedule.instruction." + kind.path + (value == 1 ? ".summary.one" : ".summary");
        return LoconauticsLang.translate(key, Component.literal(String.valueOf(value)).withStyle(ChatFormatting.WHITE))
                .component();
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void initConfigurationWidgets(ModularGuiLineBuilder builder) {
        if (!kind.hasValue) {
            return;
        }
        builder.addScrollInput(0, kind.suffix.isEmpty() ? 31 : 50, (si, l) -> {
            si.withRange(kind.min, kind.max)
                    .withStepFunction(c -> c.shift ? kind.shiftStep : kind.step)
                    .titled(LoconauticsLang.translate("schedule.instruction." + kind.path + "_edit_box").component());
            if (!kind.suffix.isEmpty()) {
                l.withSuffix(kind.suffix);
            }
        }, "Value");
    }

    @Override
    public List<Component> getSecondLineTooltip(int slot) {
        if (!kind.hasValue) {
            return ImmutableList.of();
        }
        return ImmutableList.of(LoconauticsLang.translate("schedule.instruction." + kind.path + "_edit_box").component(),
                LoconauticsLang.translate("schedule.instruction." + kind.path + "_edit_box_1").component()
                        .withStyle(ChatFormatting.GRAY));
    }

    @Override
    @Nullable
    public DiscoveredPath start(ScheduleRuntime runtime, Level level) {
        runtime.state = State.PRE_TRANSIT;
        runtime.currentEntry++;
        return null;
    }

    private ItemStack icon() {
        return switch (kind) {
            case MANEUVER -> AllBlocks.TRAIN_CONTROLS.asStack();
            case DECOUPLE -> new ItemStack(LoconauticsRegistries.KNUCKLE_ITEM.get());
            case COUPLE -> {
                ItemStack open = new ItemStack(LoconauticsRegistries.KNUCKLE_ITEM.get());
                open.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(OPEN_KNUCKLE_MODEL));
                yield open;
            }
        };
    }
}
