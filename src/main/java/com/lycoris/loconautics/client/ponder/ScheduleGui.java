package com.lycoris.loconautics.client.ponder;

import com.mojang.blaze3d.systems.RenderSystem;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.gui.widget.IconButton;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import net.createmod.catnip.animation.LerpedFloat;
import net.createmod.catnip.gui.UIRenderHelper;
import net.createmod.catnip.gui.element.GuiGameElement;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.element.AnimatedOverlayElementBase;
import net.createmod.ponder.foundation.ui.PonderUI;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public final class ScheduleGui extends AnimatedOverlayElementBase implements DeferredGui {

    public record Card(Component text, ItemStack icon, boolean action) {
    }

    private static final int OUTLINE_COLOR = 0xFF7CE08A;
    private static final int CARD_WIDTH = 195;
    private static final int CARD_HEADER = 22;
    private static final int FIRST_CARD_Y = 25;

    private final List<Card> cards;
    private int marked = -1;

    private ScheduleGui(List<Card> cards) {
        this.cards = cards;
    }

    public static ScheduleGui open(Card... cards) {
        return new ScheduleGui(List.of(cards));
    }

    public static Card travel(String station) {
        return new Card(Component.literal(station), AllBlocks.TRACK_STATION.asStack(), false);
    }

    public static Card maneuver(int percent) {
        return new Card(Component.translatable("loconautics.schedule.instruction.maneuver")
                .append(": " + percent + "% speed"), AllBlocks.TRAIN_CONTROLS.asStack(), true);
    }

    public void show(CreateSceneBuilder scene) {
        scene.addInstruction(ponder -> {
            marked = -1;
            ponder.addElement(this);
            setVisible(true);
            fade.startWithValue(0);
            fade.chase(1, 0.4, LerpedFloat.Chaser.EXP);
        });
    }

    public void hide(CreateSceneBuilder scene) {
        scene.addInstruction(ponder -> fade.chase(0, 0.4, LerpedFloat.Chaser.EXP));
    }

    public void mark(CreateSceneBuilder scene, int card) {
        scene.addInstruction(ponder -> marked = card);
    }

    @Override
    public void tick(PonderScene scene) {
        fade.tickChaser();
    }

    @Override
    public void reset(PonderScene scene) {
        marked = -1;
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
        GuiPonder.stamp();

        AllGuiTextures background = AllGuiTextures.SCHEDULE;
        int x = ui.width / 4 - 100;
        int y = (ui.height - background.getHeight()) / 2 + 14;
        Font font = Minecraft.getInstance().font;
        int alpha = (int) (fade * 255f) << 24;

        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 50);
        RenderSystem.enableBlend();
        graphics.setColor(1f, 1f, 1f, fade);
        background.render(graphics, x, y);

        Component title = Component.translatable("item.create.schedule");
        graphics.drawString(font, title, x + (background.getWidth() - 8) / 2 - font.width(title) / 2, y + 4,
                0x505050 | alpha, false);

        UIRenderHelper.drawStretched(graphics, x + 33, y + 16, 3, 173, 10, AllGuiTextures.SCHEDULE_STRIP_DARK);
        graphics.enableScissor(x + 16, y + 16, x + 236, y + 189);
        int offset = FIRST_CARD_Y;
        int outlineTop = -1;
        int outlineHeight = 0;
        AllGuiTextures.SCHEDULE_POINTER.render(graphics, x, y + offset + 4);
        UIRenderHelper.drawStretched(graphics, x + 33, y + 16, 3, 10, 0, AllGuiTextures.SCHEDULE_STRIP_LIGHT);
        for (int i = 0; i < cards.size(); i++) {
            Card card = cards.get(i);
            int height = card(graphics, font, card, x + 25, y + offset, i > 0, i < cards.size() - 1, alpha);
            if (i == marked) {
                outlineTop = y + offset;
                outlineHeight = height;
            }
            offset += height;
            if (i + 1 < cards.size()) {
                AllGuiTextures.SCHEDULE_STRIP_DOTTED.render(graphics, x + 29, y + offset - 3);
                offset += 10;
            }
        }
        graphics.disableScissor();

        graphics.fillGradient(x + 16, y + 16, x + 236, y + 26, 100, 0x77000000, 0x00000000);
        graphics.fillGradient(x + 16, y + 179, x + 236, y + 189, 100, 0x00000000, 0x77000000);

        graphics.setColor(1f, 1f, 1f, 1f);
        IconButton cyclic = new IconButton(x + 21, y + 196, AllIcons.I_REFRESH);
        cyclic.green = true;
        cyclic.render(graphics, -100, -100, 0);
        IconButton reset = new IconButton(x + 45, y + 196, AllIcons.I_PRIORITY_VERY_HIGH);
        reset.active = false;
        reset.render(graphics, -100, -100, 0);
        new IconButton(x + 63, y + 196, AllIcons.I_PRIORITY_LOW).render(graphics, -100, -100, 0);
        new IconButton(x + background.getWidth() - 42, y + background.getHeight() - 30, AllIcons.I_CONFIRM)
                .render(graphics, -100, -100, 0);

        graphics.pose().translate(0, 0, 150);
        if (outlineTop >= 0) {
            int top = Math.max(outlineTop - 1, y + 16);
            int bottom = Math.min(outlineTop + outlineHeight + 1, y + 189);
            graphics.renderOutline(x + 24, top, CARD_WIDTH + 2, bottom - top, OUTLINE_COLOR);
        }
        graphics.pose().popPose();
    }

    private int card(GuiGraphics graphics, Font font, Card card, int left, int top, boolean hasPrevious,
                     boolean hasNext, int alpha) {
        boolean conditions = !card.action();
        int height = CARD_HEADER + (conditions ? 24 + 18 : 4);
        graphics.pose().pushPose();
        graphics.pose().translate(left, top, 0);

        UIRenderHelper.drawStretched(graphics, 0, 1, CARD_WIDTH, height - 2, 0, AllGuiTextures.SCHEDULE_CARD_LIGHT);
        UIRenderHelper.drawStretched(graphics, 1, 0, CARD_WIDTH - 2, height, 0, AllGuiTextures.SCHEDULE_CARD_LIGHT);
        UIRenderHelper.drawStretched(graphics, 1, 1, CARD_WIDTH - 2, height - 2, 0, AllGuiTextures.SCHEDULE_CARD_DARK);
        UIRenderHelper.drawStretched(graphics, 2, 2, CARD_WIDTH - 4, height - 4, 0,
                AllGuiTextures.SCHEDULE_CARD_MEDIUM);
        UIRenderHelper.drawStretched(graphics, 2, 2, CARD_WIDTH - 4, CARD_HEADER, 0,
                conditions ? AllGuiTextures.SCHEDULE_CARD_LIGHT : AllGuiTextures.SCHEDULE_CARD_MEDIUM);

        AllGuiTextures.SCHEDULE_CARD_REMOVE.render(graphics, CARD_WIDTH - 14, 2);
        AllGuiTextures.SCHEDULE_CARD_DUPLICATE.render(graphics, CARD_WIDTH - 14, height - 14);
        if (hasPrevious) {
            AllGuiTextures.SCHEDULE_CARD_MOVE_UP.render(graphics, CARD_WIDTH, CARD_HEADER - 14);
        }
        if (hasNext) {
            AllGuiTextures.SCHEDULE_CARD_MOVE_DOWN.render(graphics, CARD_WIDTH, CARD_HEADER);
        }

        UIRenderHelper.drawStretched(graphics, 8, 0, 3, height + 10, 0, AllGuiTextures.SCHEDULE_STRIP_LIGHT);
        (conditions ? AllGuiTextures.SCHEDULE_STRIP_TRAVEL : AllGuiTextures.SCHEDULE_STRIP_ACTION)
                .render(graphics, 4, 6);
        if (conditions) {
            AllGuiTextures.SCHEDULE_STRIP_WAIT.render(graphics, 4, 28);
        }

        input(graphics, font, card.icon(), card.text(), 26, 5, 100, false);
        if (conditions) {
            Component wait = Component.translatable("create.schedule.condition.delay_short", "5s");
            int size = input(graphics, font, ItemStack.EMPTY, wait, 26, 29, font.width(wait) + 16, false);
            AllGuiTextures.SCHEDULE_CONDITION_APPEND.render(graphics, 26 + (size - 10) / 2, 29 + 18);
            AllGuiTextures.SCHEDULE_CONDITION_NEW.render(graphics, 26 + size + 7, 29);
        }
        graphics.pose().popPose();
        return height;
    }

    private int input(GuiGraphics graphics, Font font, ItemStack stack, Component text, int x, int y, int minSize,
                      boolean clean) {
        boolean hasItem = !stack.isEmpty();
        int size = Math.min(Math.max(font.width(text) + (hasItem ? 20 : 0) + 16, minSize), 150);
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        UIRenderHelper.drawStretched(graphics, 0, 0, size, 16, 0, AllGuiTextures.SCHEDULE_CONDITION_MIDDLE);
        (clean ? AllGuiTextures.SCHEDULE_CONDITION_LEFT_CLEAN : AllGuiTextures.SCHEDULE_CONDITION_LEFT)
                .render(graphics, clean ? 0 : -3, 0);
        AllGuiTextures.SCHEDULE_CONDITION_RIGHT.render(graphics, size - 2, 0);
        if (hasItem) {
            AllGuiTextures.SCHEDULE_CONDITION_ITEM.render(graphics, 3, 0);
            GuiGameElement.of(stack).at(4, 0).render(graphics);
        }
        graphics.drawString(font, font.substrByWidth(text, 120).getString(), hasItem ? 28 : 8, 4, 0xFFF2F2EE, false);
        graphics.pose().popPose();
        return size;
    }
}
