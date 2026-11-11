package com.lycoris.loconautics.client.ponder;

import com.lycoris.loconautics.client.gui.SableButtonIcon;
import com.mojang.blaze3d.systems.RenderSystem;
import com.simibubi.create.content.trains.station.WideIconButton;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.gui.widget.IconButton;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import com.simibubi.create.foundation.utility.CreateLang;
import net.createmod.catnip.animation.LerpedFloat;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.element.AnimatedOverlayElementBase;
import net.createmod.ponder.foundation.ui.PonderUI;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public final class StationGui extends AnimatedOverlayElementBase {

    private static final int IDLE = 0;
    private static final int ASSEMBLY = 1;
    private static final int OUTLINE_COLOR = 0xFF7CE08A;
    private static final String NAME = "Track Station";

    private final WideIconButton newTrain = new WideIconButton(84, 65, AllGuiTextures.I_NEW_TRAIN);
    private final WideIconButton sable = new WideIconButton(0, 0, SableButtonIcon.INSTANCE);
    private final WideIconButton assemble = new WideIconButton(0, 0, AllGuiTextures.I_ASSEMBLE_TRAIN);
    private final IconButton cancel = new IconButton(73, 0, AllIcons.I_DISABLE);
    private final IconButton confirm = new IconButton(0, 0, AllIcons.I_CONFIRM);
    private final IconButton close = new IconButton(0, 0, AllIcons.I_PRIORITY_VERY_LOW);

    private int screen = IDLE;
    private boolean pressing;

    public static StationGui open() {
        return new StationGui();
    }

    public void show(CreateSceneBuilder scene) {
        scene.addInstruction(ponder -> {
            screen = IDLE;
            pressing = false;
            ponder.addElement(this);
            setVisible(true);
            fade.startWithValue(0);
            fade.chase(1, 0.4, LerpedFloat.Chaser.EXP);
        });
    }

    public void hide(CreateSceneBuilder scene) {
        scene.addInstruction(ponder -> fade.chase(0, 0.4, LerpedFloat.Chaser.EXP));
    }

    public void assembly(CreateSceneBuilder scene) {
        scene.addInstruction(ponder -> {
            screen = ASSEMBLY;
            pressing = false;
        });
    }

    public void press(CreateSceneBuilder scene, boolean down) {
        scene.addInstruction(ponder -> pressing = down);
    }

    @Override
    public void tick(PonderScene scene) {
        fade.tickChaser();
    }

    @Override
    public void reset(PonderScene scene) {
        screen = IDLE;
        pressing = false;
        fade.startWithValue(0);
        fade.chase(0, 0.4, LerpedFloat.Chaser.EXP);
    }

    @Override
    public void render(PonderScene scene, PonderUI ui, GuiGraphics graphics, float partialTicks, float fade) {
        if (fade < 0.0625f || ui.getActiveScene() != scene) {
            return;
        }
        GuiPonder.stamp();

        AllGuiTextures background = screen == IDLE ? AllGuiTextures.STATION : AllGuiTextures.STATION_ASSEMBLING;
        int x = ui.width * 2 / 5 - background.getWidth() / 2;
        int y = (ui.height - background.getHeight()) / 2 - 10;
        Font font = Minecraft.getInstance().font;

        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 50);
        RenderSystem.enableBlend();
        graphics.setColor(1f, 1f, 1f, fade);
        background.render(graphics, x, y);
        graphics.setColor(1f, 1f, 1f, 1f);

        boolean solid = fade > 0.9f;
        if (screen == IDLE) {
            int nameX = x + background.getWidth() / 2 - (font.width(NAME) + 10) / 2;
            graphics.drawString(font, NAME, nameX, y + 5, 0x592424, false);
            AllGuiTextures.STATION_EDIT_NAME.render(graphics, nameX + font.width(NAME) + 5, y + 1);
            Component idle = CreateLang.translateDirect("station.idle");
            graphics.drawString(font, idle, x + 97 - font.width(idle) / 2, y + 47, 0x7A7A7A, false);
            if (solid) {
                place(confirm, x + background.getWidth() - 33, y + background.getHeight() - 24);
                draw(graphics, confirm, false);
                place(newTrain, x + 84, y + 65);
                draw(graphics, newTrain, pressing);
            }
        } else {
            Component header = CreateLang.translateDirect("station.assembly_title");
            graphics.drawString(font, header, x + background.getWidth() / 2 - font.width(header) / 2, y + 4,
                    0x0E2233, false);
            Component bogeys = CreateLang.translateDirect("station.more_bogeys", 2);
            graphics.drawString(font, bogeys, x + 97 - font.width(bogeys) / 2, y + 47, 0x7A7A7A, false);
            graphics.drawWordWrap(font, CreateLang.translateDirect("station.how_to"), x + 28, y + 62, 134, 0x7A7A7A);
            graphics.drawWordWrap(font, CreateLang.translateDirect("station.how_to_1"), x + 28, y + 94, 134, 0x7A7A7A);
            graphics.drawWordWrap(font, CreateLang.translateDirect("station.how_to_2"), x + 28, y + 117, 138, 0x7A7A7A);
            if (solid) {
                int by = y + background.getHeight() - 24;
                int rowStart = x + (background.getWidth() - 76) / 2 - 8;
                place(close, x + background.getWidth() - 33, by);
                draw(graphics, close, false);
                place(sable, rowStart, by);
                draw(graphics, sable, pressing);
                place(cancel, rowStart + 29, by);
                draw(graphics, cancel, false);
                place(assemble, rowStart + 50, by);
                draw(graphics, assemble, false);
            }
        }
        graphics.pose().popPose();
    }

    private static void place(IconButton button, int x, int y) {
        button.setX(x);
        button.setY(y);
    }

    private static void draw(GuiGraphics graphics, IconButton button, boolean pressed) {
        button.render(graphics, pressed ? button.getX() + 2 : -100, pressed ? button.getY() + 2 : -100, 0f);
        if (pressed) {
            graphics.renderOutline(button.getX() - 1, button.getY() - 1, button.getWidth() + 2,
                    button.getHeight() + 2, OUTLINE_COLOR);
        }
    }
}
