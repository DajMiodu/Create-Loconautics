package com.lycoris.loconautics.client.ponder;

import com.mojang.blaze3d.systems.RenderSystem;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import net.createmod.catnip.animation.LerpedFloat;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.element.AnimatedOverlayElementBase;
import net.createmod.ponder.foundation.ui.PonderUI;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.Arrays;

public final class GuiPonder {

    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            "loconautics", "textures/gui/frequency/frequency_bind.png");

    private static final int PANEL_W = 214;
    private static final int PANEL_H = 95;
    private static final int TEXTURE_W = 214;
    private static final int TEXTURE_H = 127;

    private static final int LABEL_CENTER_X = 54;
    private static final int LABEL_Y0 = 40;
    private static final int ROW_STEP = 29;
    private static final int SLOT_FIRST_X = 94;
    private static final int SLOT_SECOND_X = 112;
    private static final int SLOT_Y0 = 32;

    private static final int MARK_COLOR = 0xFF7CE08A;
    private static final long BLUR_WINDOW_NANOS = 150_000_000L;

    private static long blurStamp;

    private GuiPonder() {
    }

    public static boolean blurring() {
        return System.nanoTime() - blurStamp < BLUR_WINDOW_NANOS;
    }

    public static Panel open(CreateSceneBuilder scene, Component... rows) {
        return new Panel(rows);
    }

    public static final class Panel extends AnimatedOverlayElementBase {

        private final Component[] rows;
        private final ItemStack[] slots;
        private int marked = -1;

        private Panel(Component[] rows) {
            this.rows = rows;
            this.slots = new ItemStack[rows.length * 2];
            Arrays.fill(slots, ItemStack.EMPTY);
        }

        public void show(CreateSceneBuilder scene) {
            scene.addInstruction(ponder -> {
                Arrays.fill(slots, ItemStack.EMPTY);
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

        public void put(CreateSceneBuilder scene, int slot, ItemStack stack) {
            scene.addInstruction(ponder -> slots[slot] = stack.copy());
        }

        public void mark(CreateSceneBuilder scene, int row) {
            scene.addInstruction(ponder -> marked = row);
        }

        @Override
        public void tick(PonderScene scene) {
            fade.tickChaser();
        }

        @Override
        public void reset(PonderScene scene) {
            Arrays.fill(slots, ItemStack.EMPTY);
            marked = -1;
            fade.startWithValue(0);
            fade.chase(0, 0.4, LerpedFloat.Chaser.EXP);
        }

        @Override
        public void render(PonderScene scene, PonderUI screen, GuiGraphics graphics, float partialTicks, float fade) {
            if (fade < 0.0625f || screen.getActiveScene() != scene) {
                return;
            }
            blurStamp = System.nanoTime();

            int x = screen.width * 2 / 5 - PANEL_W / 2;
            int y = (screen.height - PANEL_H) / 2 - 10;
            Font font = Minecraft.getInstance().font;
            int alpha = (int) (fade * 255f) << 24;

            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 50);
            RenderSystem.enableBlend();
            graphics.setColor(1f, 1f, 1f, fade);
            graphics.blit(TEXTURE, x, y, 0, 0, PANEL_W, PANEL_H, TEXTURE_W, TEXTURE_H);
            graphics.setColor(1f, 1f, 1f, 1f);

            for (int row = 0; row < rows.length; row++) {
                int labelY = y + LABEL_Y0 + ROW_STEP * row - font.lineHeight / 2;
                graphics.drawString(font, rows[row], x + LABEL_CENTER_X - font.width(rows[row]) / 2, labelY,
                        0xFFFFFF | alpha, true);
            }

            graphics.pose().translate(0, 0, 50);
            for (int slot = 0; slot < slots.length; slot++) {
                if (slots[slot].isEmpty()) {
                    continue;
                }
                int slotX = x + (slot % 2 == 0 ? SLOT_FIRST_X : SLOT_SECOND_X);
                graphics.renderItem(slots[slot], slotX, y + SLOT_Y0 + ROW_STEP * (slot / 2));
            }

            if (marked >= 0 && marked < rows.length) {
                graphics.renderOutline(x + SLOT_FIRST_X - 2, y + SLOT_Y0 + ROW_STEP * marked - 2,
                        SLOT_SECOND_X - SLOT_FIRST_X + 20, 20, MARK_COLOR);
            }
            graphics.pose().popPose();
        }
    }
}
