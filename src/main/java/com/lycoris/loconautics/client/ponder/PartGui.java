package com.lycoris.loconautics.client.ponder;

import com.lycoris.loconautics.client.gui.gauge.GaugePartColumn;
import com.lycoris.loconautics.client.gui.selector.ButtonModelViewer;
import com.lycoris.loconautics.client.gui.selector.SelectorOptionButtons;
import com.lycoris.loconautics.client.gui.selector.SelectorPanel;
import com.lycoris.loconautics.content.boiler.BoilerBlocks;
import com.lycoris.loconautics.content.controls.panel.PanelPartLayout;
import com.lycoris.loconautics.content.controls.panel.PanelPartType;
import com.lycoris.loconautics.content.controls.panel.style.PanelStyleSet;
import com.lycoris.loconautics.content.controls.panel.style.PanelStyles;
import com.lycoris.loconautics.content.controls.panelbutton.ButtonPart;
import com.mojang.blaze3d.systems.RenderSystem;
import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import net.createmod.catnip.animation.LerpedFloat;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.element.AnimatedOverlayElementBase;
import net.createmod.ponder.foundation.ui.PonderUI;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;

public final class PartGui extends AnimatedOverlayElementBase implements DeferredGui {

    public enum Region {
        LABEL, SIGNAL, VIEW, STYLE, BODY, COLOR, OPTIONS, INVERT, CONFIRM
    }

    private static final int OUTLINE_COLOR = 0xFF7CE08A;
    private static final int ARROW = SelectorPanel.ARROW_SIZE;

    private final ButtonModelViewer viewer = new ButtonModelViewer();
    private final ItemStack[] frequency = new ItemStack[2];
    private final Set<Region> marked = EnumSet.noneOf(Region.class);

    private int body;
    private int color;
    private int style;
    private int options;
    private boolean inverted;
    private String label = "";

    public static PartGui open() {
        return new PartGui();
    }

    private PartGui() {
        reset();
    }

    private void reset() {
        Arrays.fill(frequency, ItemStack.EMPTY);
        marked.clear();
        body = ButtonPart.DEFAULT_BODY;
        color = ButtonPart.DEFAULT_CAP_COLOR;
        style = ButtonPart.DEFAULT_STYLE;
        options = ButtonPart.DEFAULT_OPTIONS;
        inverted = false;
        label = "";
    }

    public void show(CreateSceneBuilder scene) {
        scene.addInstruction(ponder -> {
            reset();
            ponder.addElement(this);
            setVisible(true);
            fade.startWithValue(0);
            fade.chase(1, 0.4, LerpedFloat.Chaser.EXP);
        });
    }

    public void hide(CreateSceneBuilder scene) {
        scene.addInstruction(ponder -> fade.chase(0, 0.4, LerpedFloat.Chaser.EXP));
    }

    public void mark(CreateSceneBuilder scene, Region... regions) {
        scene.addInstruction(ponder -> {
            marked.clear();
            marked.addAll(Arrays.asList(regions));
        });
    }

    public void frequency(CreateSceneBuilder scene, int slot, ItemStack stack) {
        scene.addInstruction(ponder -> frequency[slot] = stack.copy());
    }

    public void label(CreateSceneBuilder scene, String text) {
        scene.addInstruction(ponder -> label = text);
    }

    public void body(CreateSceneBuilder scene, int value) {
        scene.addInstruction(ponder -> body = value);
    }

    public void color(CreateSceneBuilder scene, int value) {
        scene.addInstruction(ponder -> color = value);
    }

    public void style(CreateSceneBuilder scene, int value) {
        scene.addInstruction(ponder -> style = value);
    }

    public void toggleOption(CreateSceneBuilder scene, int option) {
        scene.addInstruction(ponder -> options = PanelStyles.BUTTON.toggle(options, option));
    }

    public void inverted(CreateSceneBuilder scene, boolean value) {
        scene.addInstruction(ponder -> inverted = value);
    }

    @Override
    public void tick(PonderScene scene) {
        fade.tickChaser();
    }

    @Override
    public void reset(PonderScene scene) {
        reset();
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

        int x = Math.max(4, ui.width * 2 / 5 - SelectorPanel.PANEL_W / 2);
        int y = (ui.height - SelectorPanel.PANEL_H) / 2 - 10;
        Font font = Minecraft.getInstance().font;
        BlockState state = BoilerBlocks.LOCOMOTIVE_GAUGE.get().defaultBlockState();
        PanelStyleSet set = PanelStyles.BUTTON;
        boolean optionsShown = set.hasOptions(style);
        int shownOptions = set.effectiveOptions(options, style);
        int alpha = (int) (fade * 255f) << 24;

        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, -60);
        RenderSystem.enableBlend();
        graphics.setColor(1f, 1f, 1f, fade);
        SelectorPanel.blitPanel(graphics, x, y);
        SelectorPanel.blitSignalRow(graphics, x, y);
        SelectorPanel.blitStrip(graphics, x, y, SelectorPanel.LEFT_STRIP_X);
        SelectorPanel.blitStrip(graphics, x, y, SelectorPanel.RIGHT_STRIP_X);
        graphics.setColor(1f, 1f, 1f, 1f);

        Component signal = Component.translatable("gui.loconautics.bind_frequency.signal");
        graphics.drawString(font, signal,
                x + SelectorPanel.SIGNAL_TEXT_X + (SelectorPanel.SIGNAL_TEXT_W - font.width(signal)) / 2,
                y + SelectorPanel.SIGNAL_TEXT_Y, 0xFFFFFF | alpha, true);

        viewer.setState(state);
        viewer.setBody(body);
        viewer.setColor(color);
        viewer.setStyle(style);
        viewer.setOptions(shownOptions);
        SelectorPanel.renderViewer(graphics, viewer, x, y);
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, -650);
        SelectorPanel.renderViewerOverlay(graphics, font, viewer, x, y);
        graphics.pose().popPose();

        int boxColor = color;
        int boxBody = body;
        GaugePartColumn bodies = SelectorPanel.column(x, y, SelectorPanel.LEFT_STRIP_X, PanelPartType.BODY_VARIANTS,
                (g, i, cx, cy, box) -> ButtonModelViewer.renderButtonIcon(
                        g, state, i, boxColor, style, shownOptions, cx, cy, box));
        bodies.setSelected(boxBody);
        bodies.render(graphics);
        GaugePartColumn colors = SelectorPanel.column(x, y, SelectorPanel.RIGHT_STRIP_X, PanelPartType.PALETTE_COLORS,
                (g, i, cx, cy, box) -> ButtonModelViewer.renderButtonIcon(
                        g, state, boxBody, i, style, shownOptions, cx, cy, box));
        colors.setSelected(boxColor);
        colors.render(graphics);

        SelectorPanel.blitArrow(graphics, x + SelectorPanel.STYLE_PREV_X, y + SelectorPanel.STYLE_ARROW_Y, false, false);
        SelectorPanel.blitArrow(graphics, x + SelectorPanel.STYLE_NEXT_X, y + SelectorPanel.STYLE_ARROW_Y, true, false);
        SelectorPanel.renderStyleDots(graphics, x, y, set.count(), style, -1);

        if (optionsShown) {
            for (int i = 0; i < set.optionCount(); i++) {
                int buttonX = x + SelectorPanel.OPTION_X + i * SelectorPanel.OPTION_STEP;
                SelectorPanel.blitButton(graphics, buttonX, y + SelectorPanel.OPTION_Y, false, set.isOn(options, i));
                SelectorOptionButtons.icon(set.option(i).id()).render(graphics, buttonX + 1, y + SelectorPanel.OPTION_Y + 1);
            }
        }
        SelectorPanel.blitButton(graphics, x + SelectorPanel.INVERT_X, y + SelectorPanel.INVERT_Y, false, inverted);
        AllIcons.I_FLIP.render(graphics, x + SelectorPanel.INVERT_X + 1, y + SelectorPanel.INVERT_Y + 1);
        SelectorPanel.blitButton(graphics, x + SelectorPanel.CONFIRM_X, y + SelectorPanel.CONFIRM_Y, false, false);
        AllIcons.I_CONFIRM.render(graphics, x + SelectorPanel.CONFIRM_X + 1, y + SelectorPanel.CONFIRM_Y + 1);

        Component shown = label.isEmpty()
                ? Component.translatable("gui.loconautics.selector.label.empty").withStyle(ChatFormatting.DARK_GRAY)
                : Component.literal(label);
        int textWidth = Math.min(font.width(shown), SelectorPanel.LABEL_W);
        int labelX = x + SelectorPanel.PANEL_W / 2 - (textWidth + 9 + 5) / 2;
        graphics.drawString(font, shown, labelX, y + SelectorPanel.LABEL_Y + 1, SelectorPanel.LABEL_TEXT_COLOR, false);
        SelectorPanel.blitRename(graphics, labelX + textWidth + 5, y + (SelectorPanel.LABEL_BAR_H - 9) / 2, false);

        graphics.pose().translate(0, 0, 50);
        int[] ghostX = {PanelPartLayout.GHOST_ONE_X, PanelPartLayout.GHOST_TWO_X};
        for (int i = 0; i < frequency.length; i++) {
            if (!frequency[i].isEmpty()) {
                graphics.renderItem(frequency[i], x + ghostX[i], y + PanelPartLayout.GHOST_Y);
            }
        }
        for (Region region : marked) {
            for (int[] rect : rects(region, set.optionCount(), optionsShown)) {
                graphics.renderOutline(x + rect[0], y + rect[1], rect[2], rect[3], OUTLINE_COLOR);
            }
        }
        graphics.pose().popPose();
    }

    private static int[][] rects(Region region, int optionCount, boolean optionsShown) {
        int button = SelectorPanel.BUTTON_SIZE;
        return switch (region) {
            case LABEL -> new int[][]{{0, 0, SelectorPanel.PANEL_W, SelectorPanel.LABEL_BAR_H}};
            case SIGNAL -> new int[][]{{SelectorPanel.ROW_X, SelectorPanel.ROW_Y, SelectorPanel.ROW_W, SelectorPanel.ROW_H}};
            case VIEW -> new int[][]{{SelectorPanel.VIEW_X, SelectorPanel.VIEW_Y,
                    SelectorPanel.VIEW_SIZE, SelectorPanel.VIEW_SIZE}};
            case STYLE -> new int[][]{
                    {SelectorPanel.STYLE_PREV_X, SelectorPanel.STYLE_ARROW_Y, ARROW, ARROW},
                    {SelectorPanel.STYLE_NEXT_X, SelectorPanel.STYLE_ARROW_Y, ARROW, ARROW},
                    {SelectorPanel.VIEW_X + 30, SelectorPanel.STYLE_DOTS_Y - 3, 32, 10}};
            case BODY -> new int[][]{{SelectorPanel.LEFT_STRIP_X, SelectorPanel.STRIP_Y,
                    SelectorPanel.STRIP_W, SelectorPanel.STRIP_H}};
            case COLOR -> new int[][]{{SelectorPanel.RIGHT_STRIP_X, SelectorPanel.STRIP_Y,
                    SelectorPanel.STRIP_W, SelectorPanel.STRIP_H}};
            case OPTIONS -> optionsShown
                    ? new int[][]{{SelectorPanel.OPTION_X - 1, SelectorPanel.OPTION_Y - 1,
                    optionCount * SelectorPanel.OPTION_STEP, button + 2}}
                    : new int[0][];
            case INVERT -> new int[][]{{SelectorPanel.INVERT_X - 1, SelectorPanel.INVERT_Y - 1, button + 2, button + 2}};
            case CONFIRM -> new int[][]{{SelectorPanel.CONFIRM_X - 1, SelectorPanel.CONFIRM_Y - 1, button + 2, button + 2}};
        };
    }
}
