package com.lycoris.loconautics.content.turntablebearing;

import com.lycoris.loconautics.common.turntable.sequence.TurntableInstruction;
import com.lycoris.loconautics.common.turntable.sequence.TurntableInstructionType;
import com.lycoris.loconautics.common.turntable.sequence.TurntableSpeedModifier;
import com.lycoris.loconautics.common.turntable.sequence.TurntableTriggerMode;
import com.lycoris.loconautics.network.packets.turntablebearing.TurntableConfigurePacket;
import com.lycoris.loconautics.registry.LoconauticsRegistries;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.gui.widget.IconButton;
import com.simibubi.create.foundation.gui.widget.ScrollInput;
import com.simibubi.create.foundation.gui.widget.SelectionScrollInput;
import net.createmod.catnip.gui.AbstractSimiScreen;
import net.createmod.catnip.gui.element.GuiGameElement;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

public class TurntableBearingScreen extends AbstractSimiScreen {

    private static final int ROW_HEIGHT = 22;

    private final ItemStack renderedItem =
            new ItemStack(LoconauticsRegistries.TURNTABLE_BEARING_ITEM.get());
    private final AllGuiTextures background = AllGuiTextures.SEQUENCER;
    private final TurntableBearingBlockEntity blockEntity;

    private final List<TurntableInstruction> instructions;
    private final List<List<ScrollInput>> inputs = new ArrayList<>();
    private TurntableTriggerMode trigger;
    private ScrollInput triggerInput;

    public TurntableBearingScreen(TurntableBearingBlockEntity blockEntity) {
        super(Component.translatable("loconautics.gui.turntable_bearing.title"));
        this.blockEntity = blockEntity;
        this.instructions = new ArrayList<>(blockEntity.getInstructions());
        this.trigger = blockEntity.getTrigger();
    }

    @Override
    protected void init() {
        setWindowSize(background.getWidth(), background.getHeight());
        setWindowOffset(-20, 0);
        super.init();

        int x = guiLeft;
        int y = guiTop;

        inputs.clear();
        for (int row = 0; row < TurntableInstruction.MAX_INSTRUCTIONS; row++) {
            inputs.add(new ArrayList<>(3));
        }
        for (int row = 0; row < instructions.size(); row++) {
            initInputsOfRow(row, x, y);
        }

        triggerInput = new SelectionScrollInput(
                x + 30, y + background.getHeight() - 27, 90, 18)
                .forOptions(TurntableTriggerMode.getOptions())
                .calling(state -> trigger = TurntableTriggerMode.values()[state])
                .setState(trigger.ordinal())
                .titled(Component.translatable("loconautics.gui.turntable_bearing.trigger"));
        addRenderableWidget(triggerInput);
        refreshScheduleLock();

        IconButton confirmButton = new IconButton(x + background.getWidth() - 33,
                y + background.getHeight() - 24, AllIcons.I_CONFIRM);
        confirmButton.withCallback(this::onClose);
        addRenderableWidget(confirmButton);
    }

    private void initInputsOfRow(int row, int backgroundX, int backgroundY) {
        int x = backgroundX + 30;
        int y = backgroundY + 20;

        List<ScrollInput> rowInputs = inputs.get(row);
        rowInputs.forEach(this::removeWidget);
        rowInputs.clear();

        TurntableInstruction instruction = instructions.get(row);
        int index = row;

        ScrollInput type = new SelectionScrollInput(x, y + ROW_HEIGHT * row, 50, 18)
                .forOptions(TurntableInstructionType.getOptions())
                .calling(state -> instructionUpdated(index, state))
                .setState(instruction.type.ordinal())
                .titled(Component.translatable("loconautics.gui.turntable_bearing.instruction"));
        ScrollInput value = new ScrollInput(x + 58, y + ROW_HEIGHT * row, 28, 18)
                .calling(state -> instruction.value = state);
        ScrollInput modifier = new SelectionScrollInput(x + 88, y + ROW_HEIGHT * row, 28, 18)
                .forOptions(TurntableSpeedModifier.getOptions())
                .calling(state -> instruction.modifier = TurntableSpeedModifier.values()[state])
                .titled(Component.translatable("loconautics.gui.turntable_bearing.speed"));

        rowInputs.add(type);
        rowInputs.add(value);
        rowInputs.add(modifier);
        rowInputs.forEach(this::addRenderableWidget);
        updateParamsOfRow(row);
    }

    private void updateParamsOfRow(int row) {
        TurntableInstruction instruction = instructions.get(row);
        List<ScrollInput> rowInputs = inputs.get(row);
        TurntableInstructionType def = instruction.type;

        ScrollInput value = rowInputs.get(1);
        value.active = value.visible = def.hasValueParameter;
        if (def.hasValueParameter) {
            value.withRange(1, def.maxValue + 1)
                    .titled(Component.translatable(def.parameterKey))
                    .withShiftStep(def.shiftStep)
                    .setState(instruction.value)
                    .onChanged();
        }
        if (def == TurntableInstructionType.DELAY) {
            value.withStepFunction(context -> {
                int v = context.currentValue;
                if (!context.forward) {
                    v--;
                }
                if (v < 20) {
                    return context.shift ? 20 : 1;
                }
                return context.shift ? 100 : 20;
            });
        } else {
            value.withStepFunction(value.standardStep());
        }

        ScrollInput modifier = rowInputs.get(2);
        modifier.active = modifier.visible = def.hasSpeedParameter;
        if (def.hasSpeedParameter) {
            modifier.setState(instruction.modifier.ordinal());
        }
    }

    private void refreshScheduleLock() {
        boolean locked = TurntableInstruction.isScheduleDriven(instructions);
        triggerInput.active = triggerInput.visible = !locked;
    }

    private void collapseToSchedule() {
        for (int i = instructions.size() - 1; i > 0; i--) {
            instructions.remove(i);
            List<ScrollInput> rowInputs = inputs.get(i);
            rowInputs.forEach(this::removeWidget);
            rowInputs.clear();
        }
        instructions.get(0).type = TurntableInstructionType.SCHEDULE;
        initInputsOfRow(0, guiLeft, guiTop);
        refreshScheduleLock();
    }

    private void instructionUpdated(int index, int state) {
        TurntableInstructionType newValue = TurntableInstructionType.values()[state];
        TurntableInstruction instruction = instructions.get(index);
        instruction.type = newValue;
        instruction.value = newValue.defaultValue;
        updateParamsOfRow(index);
        if (newValue == TurntableInstructionType.SCHEDULE) {
            collapseToSchedule();
            return;
        }
        refreshScheduleLock();
        if (newValue == TurntableInstructionType.END) {
            for (int i = instructions.size() - 1; i > index; i--) {
                instructions.remove(i);
                List<ScrollInput> rowInputs = inputs.get(i);
                rowInputs.forEach(this::removeWidget);
                rowInputs.clear();
            }
            return;
        }
        if (index + 1 < TurntableInstruction.MAX_INSTRUCTIONS && index + 1 == instructions.size()) {
            instructions.add(new TurntableInstruction(TurntableInstructionType.END));
            initInputsOfRow(index + 1, guiLeft, guiTop);
        }
    }

    @Override
    protected void renderWindow(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        int x = guiLeft;
        int y = guiTop;

        background.render(graphics, x, y);

        for (int row = 0; row < TurntableInstruction.MAX_INSTRUCTIONS; row++) {
            int yOffset = AllGuiTextures.SEQUENCER_EMPTY.getHeight() * row;
            if (row >= instructions.size()) {
                AllGuiTextures.SEQUENCER_EMPTY.render(graphics, x, y + 16 + yOffset);
                continue;
            }

            TurntableInstruction instruction = instructions.get(row);
            TurntableInstructionType def = instruction.type;
            def.background.render(graphics, x, y + 16 + yOffset);

            label(graphics, 36, yOffset - 1, Component.translatable(def.translationKey));
            if (def.hasValueParameter) {
                String text = def.formatValue(instruction.value);
                label(graphics, 90 + (12 - font.width(text) / 2), yOffset - 1, Component.literal(text));
            }
            if (def.hasSpeedParameter) {
                label(graphics, 127, yOffset - 1, instruction.modifier.label);
            }
        }

        graphics.drawString(font, title, x + (background.getWidth() - 8) / 2 - font.width(title) / 2,
                y + 4, 0x592424, false);
        GuiGameElement.of(renderedItem)
                .<GuiGameElement.GuiRenderBuilder>at(x + background.getWidth() + 6,
                        y + background.getHeight() - 56, 100)
                .scale(5)
                .render(graphics);
    }

    private void label(GuiGraphics graphics, int x, int y, Component text) {
        graphics.drawString(font, text, guiLeft + x, guiTop + 26 + y, 0xFFFFEE);
    }

    @Override
    public void removed() {
        PacketDistributor.sendToServer(
                new TurntableConfigurePacket(blockEntity.getBlockPos(), instructions, trigger));
    }
}
