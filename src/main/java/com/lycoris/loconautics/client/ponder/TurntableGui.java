package com.lycoris.loconautics.client.ponder;

import com.lycoris.loconautics.common.turntable.sequence.TurntableInstruction;
import com.lycoris.loconautics.common.turntable.sequence.TurntableInstructionType;
import com.lycoris.loconautics.common.turntable.sequence.TurntableSpeedModifier;
import com.mojang.blaze3d.systems.RenderSystem;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import net.createmod.catnip.animation.LerpedFloat;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.element.AnimatedOverlayElementBase;
import net.createmod.ponder.foundation.ui.PonderUI;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public final class TurntableGui extends AnimatedOverlayElementBase implements DeferredGui {

    public record Row(TurntableInstructionType type, int value, TurntableSpeedModifier modifier) {
    }

    private static final int OUTLINE_COLOR = 0xFF7CE08A;
    private static final int ROW_HEIGHT = 22;
    private static final int DOCK_X = 8;

    private final List<Row> rows = new ArrayList<>();
    private int marked = -1;
    private final LerpedFloat slide = LerpedFloat.linear().startWithValue(0);
    private boolean docked;

    public static TurntableGui open() {
        return new TurntableGui();
    }

    public static Row turn(int degrees, TurntableSpeedModifier modifier) {
        return new Row(TurntableInstructionType.TURN_ANGLE, degrees, modifier);
    }

    public static Row delay(int ticks) {
        return new Row(TurntableInstructionType.DELAY, ticks, TurntableSpeedModifier.FORWARD);
    }

    public static Row await() {
        return new Row(TurntableInstructionType.AWAIT, 0, TurntableSpeedModifier.FORWARD);
    }

    public static Row end() {
        return new Row(TurntableInstructionType.END, 0, TurntableSpeedModifier.FORWARD);
    }

    public static Row schedule() {
        return new Row(TurntableInstructionType.SCHEDULE, 0, TurntableSpeedModifier.FORWARD);
    }

    public void show(CreateSceneBuilder scene, Row... initial) {
        scene.addInstruction(ponder -> {
            rows.clear();
            rows.addAll(List.of(initial));
            marked = -1;
            docked = false;
            slide.startWithValue(0);
            ponder.addElement(this);
            setVisible(true);
            fade.startWithValue(0);
            fade.chase(1, 0.4, LerpedFloat.Chaser.EXP);
        });
    }

    public void hide(CreateSceneBuilder scene) {
        scene.addInstruction(ponder -> fade.chase(0, 0.4, LerpedFloat.Chaser.EXP));
    }

    public void set(CreateSceneBuilder scene, Row... next) {
        scene.addInstruction(ponder -> {
            rows.clear();
            rows.addAll(List.of(next));
        });
    }

    public void mark(CreateSceneBuilder scene, int row) {
        scene.addInstruction(ponder -> marked = row);
    }

    public void dock(CreateSceneBuilder scene) {
        scene.addInstruction(ponder -> {
            docked = true;
            slide.chase(1, 0.2, LerpedFloat.Chaser.EXP);
        });
    }

    public void center(CreateSceneBuilder scene) {
        scene.addInstruction(ponder -> {
            docked = false;
            slide.chase(0, 0.2, LerpedFloat.Chaser.EXP);
        });
    }

    @Override
    public void tick(PonderScene scene) {
        fade.tickChaser();
        slide.tickChaser();
    }

    @Override
    public void reset(PonderScene scene) {
        rows.clear();
        marked = -1;
        docked = false;
        slide.startWithValue(0);
        fade.startWithValue(0);
        fade.chase(0, 0.4, LerpedFloat.Chaser.EXP);
    }

    @Override
    public void render(PonderScene scene, PonderUI ui, GuiGraphics graphics, float partialTicks, float fade) {
    }

    @Override
    public void drawDeferred(PonderScene scene, PonderUI ui, GuiGraphics graphics, float partialTicks, float fade) {
        if (fade < 0.0625f || ui.getActiveScene() != scene) {
            return;
        }
        float shift = slide.getValue(partialTicks);
        if (shift < 0.5f) {
            GuiPonder.stamp();
        }

        AllGuiTextures background = AllGuiTextures.SEQUENCER;
        int centered = ui.width * 2 / 5 - background.getWidth() / 2;
        int x = Math.round(centered + (DOCK_X - centered) * shift);
        int y = (ui.height - background.getHeight()) / 2 - 10;
        Font font = Minecraft.getInstance().font;
        Component title = Component.translatable("loconautics.gui.turntable_bearing.title");

        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 50);
        RenderSystem.enableBlend();
        graphics.setColor(1f, 1f, 1f, fade);
        background.render(graphics, x, y);
        for (int row = 0; row < TurntableInstruction.MAX_INSTRUCTIONS; row++) {
            int rowY = y + 16 + ROW_HEIGHT * row;
            if (row >= rows.size()) {
                AllGuiTextures.SEQUENCER_EMPTY.render(graphics, x, rowY);
                continue;
            }
            rows.get(row).type().background.render(graphics, x, rowY);
        }
        graphics.setColor(1f, 1f, 1f, 1f);

        int alpha = (int) (fade * 255f) << 24;
        graphics.drawString(font, title, x + (background.getWidth() - 8) / 2 - font.width(title) / 2, y + 4,
                0x592424 | alpha, false);
        for (int row = 0; row < rows.size(); row++) {
            Row entry = rows.get(row);
            TurntableInstructionType type = entry.type();
            int labelY = y + 26 + ROW_HEIGHT * row - 1;
            graphics.drawString(font, Component.translatable(type.translationKey), x + 36, labelY,
                    0xFFFFEE | alpha, false);
            if (type.hasValueParameter) {
                String text = type.formatValue(entry.value());
                graphics.drawString(font, text, x + 90 + (12 - font.width(text) / 2), labelY, 0xFFFFEE | alpha, false);
            }
            if (type.hasSpeedParameter) {
                graphics.drawString(font, entry.modifier().label, x + 127, labelY, 0xFFFFEE | alpha, false);
            }
        }

        graphics.pose().translate(0, 0, 50);
        if (marked >= 0 && marked < rows.size()) {
            graphics.renderOutline(x + 28, y + 20 + ROW_HEIGHT * marked - 1, 126, 20, OUTLINE_COLOR);
        }
        graphics.pose().popPose();
    }
}
