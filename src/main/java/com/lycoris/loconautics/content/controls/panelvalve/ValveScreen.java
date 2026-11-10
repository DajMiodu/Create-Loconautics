package com.lycoris.loconautics.content.controls.panelvalve;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.content.controls.panel.PanelSlot;
import com.lycoris.loconautics.foundation.ui.NoBlurScreen;
import com.lycoris.loconautics.network.packets.controls.ValveTurnPacket;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

public class ValveScreen extends NoBlurScreen {

    private static final ResourceLocation OVERLAY =
            LoconauticsConstants.id("textures/gui/valve/overlay.png");
    private static final ResourceLocation WHEEL =
            LoconauticsConstants.id("textures/gui/valve/bg.png");

    private static final int PANEL_W = 123;
    private static final int PANEL_H = 121;
    private static final int SHEET = 256;

    private static final int DIAL_CX = 64;
    private static final int DIAL_CY = 64;

    private static final int WHEEL_SIZE = 128;

    private static final int DEAD_ZONE = 14;

    private static final int READOUT_LIFT = 6;

    private static final int DIGIT_U = 0;
    private static final int DIGIT_V = 153;
    private static final int DIGIT_W = 5;
    private static final int DIGIT_H = 7;
    private static final int DIGIT_ADVANCE = 6;

    private final BlockPos panelPos;
    private final PanelSlot slot;

    private float degrees;
    private int sentDegrees;
    private boolean closing;

    private int guiLeft;
    private int guiTop;

    private ValveScreen(BlockPos panelPos, PanelSlot slot, float degrees) {
        super(Component.translatable("gui.loconautics.valve"));
        this.panelPos = panelPos;
        this.slot = slot;
        this.degrees = ValvePart.wrap(degrees);
        this.sentDegrees = Mth.floor(this.degrees);
    }

    public static void open(BlockPos panelPos, PanelSlot slot, float degrees) {
        Minecraft.getInstance().setScreen(new ValveScreen(panelPos, slot, degrees));
    }

    @Override
    protected void init() {
        guiLeft = (width - PANEL_W) / 2;
        guiTop = (height - PANEL_H) / 2;
    }

    private int centerX() {
        return guiLeft + DIAL_CX;
    }

    private int centerY() {
        return guiTop + DIAL_CY;
    }

    private void turnTo(double mouseX, double mouseY) {
        double dx = mouseX - centerX();
        double dy = mouseY - centerY();
        if (dx * dx + dy * dy < DEAD_ZONE * DEAD_ZONE) return;

        float aimed = ValvePart.wrap((float) Math.toDegrees(Math.atan2(dx, -dy)));
        if (hasShiftDown()) {
            aimed = ValvePart.wrap(Math.round(aimed / ValvePart.SNAP) * ValvePart.SNAP);
        }
        degrees = Mth.clamp(degrees + Mth.degreesDifference(degrees, aimed), 0f, ValvePart.FULL_TURN);
        sendIfMoved();
    }

    private void sendIfMoved() {
        int whole = Mth.floor(degrees);
        if (whole == sentDegrees) return;
        sentDegrees = whole;
        PacketDistributor.sendToServer(new ValveTurnPacket(panelPos, slot.ordinal(), whole));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.blit(OVERLAY, guiLeft, guiTop, 0, 0, PANEL_W, PANEL_H, SHEET, SHEET);

        PoseStack ms = graphics.pose();
        ms.pushPose();
        ms.translate(centerX(), centerY(), 0);
        ms.mulPose(Axis.ZP.rotationDegrees(degrees));
        ms.translate(-WHEEL_SIZE / 2f, -WHEEL_SIZE / 2f, 0);
        graphics.blit(WHEEL, 0, 0, 0, 0, WHEEL_SIZE, WHEEL_SIZE, WHEEL_SIZE, WHEEL_SIZE);
        ms.popPose();

        drawReadout(graphics, Mth.floor(degrees));
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void drawReadout(GuiGraphics graphics, int value) {
        String text = Integer.toString(value);
        int width = text.length() * DIGIT_ADVANCE - 1;
        int x = centerX() - width / 2;
        for (int i = 0; i < text.length(); i++) {
            int digit = text.charAt(i) - '0';
            graphics.blit(OVERLAY, x + i * DIGIT_ADVANCE, guiTop - READOUT_LIFT,
                    DIGIT_U + digit * DIGIT_ADVANCE, DIGIT_V, DIGIT_W, DIGIT_H, SHEET, SHEET);
        }
    }

    @Override
    public void tick() {
        if (!useHeld()) {
            onClose();
        }
    }

    private static boolean useHeld() {
        InputConstants.Key key = Minecraft.getInstance().options.keyUse.getKey();
        long window = Minecraft.getInstance().getWindow().getWindow();
        if (key.getType() == InputConstants.Type.MOUSE) {
            return GLFW.glfwGetMouseButton(window, key.getValue()) == GLFW.GLFW_PRESS;
        }
        return InputConstants.isKeyDown(window, key.getValue());
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        turnTo(mouseX, mouseY);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        turnTo(mouseX, mouseY);
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (Minecraft.getInstance().options.keyUse.matchesMouse(button)) {
            onClose();
        }
        return true;
    }

    @Override
    public void onClose() {
        if (closing) return;
        closing = true;
        PacketDistributor.sendToServer(new ValveTurnPacket(panelPos, slot.ordinal(), Mth.floor(degrees)));
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

}
