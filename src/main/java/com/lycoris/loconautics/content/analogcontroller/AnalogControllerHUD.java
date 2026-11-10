package com.lycoris.loconautics.content.analogcontroller;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.trainphysics.state.SableTrainClientRegistry;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.createmod.catnip.animation.LerpedFloat;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.GameType;

public class AnalogControllerHUD {


    public static final LayeredDraw.Layer OVERLAY = AnalogControllerHUD::render;

    private static final ResourceLocation WIDGETS =
            ResourceLocation.fromNamespaceAndPath("create", "textures/gui/widgets.png");
    private static final ResourceLocation LOCK_ICON =
            LoconauticsConstants.id("textures/gui/widget/lock_icon.png");

    private static final int SPEED_BG_W  = 182, SPEED_BG_H  = 5;
    private static final int SPEED_W     = 182, SPEED_H     = 5;
    private static final int THROTTLE_W  = 182, THROTTLE_H  = 5;
    private static final int PTR_W       = 6,   PTR_H       = 9;
    private static final int FRAME_W     = 186, FRAME_H     = 7;

    private static final int DIR_ZONE_X  = 77;

    private static final int LOCK_W = 8, LOCK_H = 10;

    private static final LerpedFloat displayedSpeed    = LerpedFloat.linear();
    private static final LerpedFloat displayedThrottle = LerpedFloat.linear();

    public static Component currentPrompt;
    public static boolean currentPromptShadow;
    public static int promptKeepAlive;
    private static final LerpedFloat displayedPromptSize = LerpedFloat.linear();

    public static void tick() {
        if (promptKeepAlive > 0) {
            promptKeepAlive--;
        } else {
            currentPrompt = null;
        }
        Minecraft mc = Minecraft.getInstance();
        displayedPromptSize.chase(
                currentPrompt != null ? (double) (mc.font.width(currentPrompt) + 17) : 0.0,
                0.5, LerpedFloat.Chaser.EXP);
        displayedPromptSize.tickChaser();
    }

    private static SableTrainClientRegistry.TrainMarker mountedTrain() {
        ClientSubLevel sub = Sable.HELPER.getContainingClient(AnalogControllerClientHandler.getMountedPos());
        return sub == null ? null : SableTrainClientRegistry.get(sub.getUniqueId());
    }

    private static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();

        if (mc.options.hideGui || mc.gameMode == null
                || mc.gameMode.getPlayerMode() == GameType.SPECTATOR) return;

        if (!AnalogControllerClientHandler.isControlling()) return;

        if (mc.level == null) return;
        AnalogControllerBlockEntity ace = AnalogControllerClientHandler.resolveMountedBlockEntity();
        if (ace == null) return;

        boolean locked   = ace.isLocked();
        int maxPower = ace.getMaxPower();

        float partialTicks = deltaTracker.getGameTimeDeltaPartialTick(true);
        SableTrainClientRegistry.TrainMarker marker = mountedTrain();
        double ratio = marker == null ? 0.0 : AnalogControllerClientHandler.speedFraction(marker.speed(), marker);
        displayedSpeed.chase((int) (ratio * 18) / 18.0, 0.5, LerpedFloat.Chaser.EXP);
        displayedSpeed.tickChaser();
        displayedThrottle.chase(maxPower / 15.0, 0.2, LerpedFloat.Chaser.EXP);
        displayedThrottle.tickChaser();
        float animSpeed    = displayedSpeed.getValue(partialTicks);
        float animThrottle = displayedThrottle.getValue(partialTicks);

        int originX = graphics.guiWidth() / 2 - 91;
        int barY    = graphics.guiHeight() - 29;

        graphics.blit(WIDGETS, originX, barY, 0,
                0, 190, SPEED_BG_W, SPEED_BG_H, 256, 256);

        int fillW = Math.round(animSpeed * SPEED_W);
        if (fillW > 0) {
            graphics.blit(WIDGETS, originX, barY, 0,
                    0, 185, fillW, SPEED_H, 256, 256);
        }

        int throttleW = Math.round((1.0f - animThrottle) * THROTTLE_W);
        int invW      = THROTTLE_W - throttleW;
        if (throttleW > 0) {
            graphics.blit(WIDGETS, originX + invW, barY, 0,
                    invW, 195, throttleW, THROTTLE_H, 256, 256);
        }

        graphics.blit(WIDGETS, originX - 2, barY + SPEED_BG_H - 4, 0,
                0, 200, FRAME_W, FRAME_H, 256, 256);

        int ptrX = originX + Math.max(1, invW) - 3;
        graphics.blit(WIDGETS, ptrX, barY - 2, 0,
                0, 209, PTR_W, PTR_H, 256, 256);

        int dirZoneAbsX = originX + DIR_ZONE_X;

        graphics.blit(WIDGETS, dirZoneAbsX, barY - 20, 0,
                77, 165, 28, 20, 256, 256);

        int steer = ace.getSteerInput();
        float steerAngle = steer < 0 ? -45.0f : steer > 0 ? 45.0f : 0.0f;
        PoseStack arrowPose = graphics.pose();
        arrowPose.pushPose();
        arrowPose.translate(originX + 91, barY - 9, 0);
        arrowPose.scale(0.925f, 0.925f, 1.0f);
        net.createmod.catnip.placement.PlacementClient.textured(arrowPose, 0.0f, 0.0f, 1.0f, steerAngle);
        arrowPose.popPose();

        int promptSize = (int) displayedPromptSize.getValue(partialTicks);
        if (promptSize > 1) {
            PoseStack poseStack = graphics.pose();
            poseStack.pushPose();
            poseStack.translate(originX, barY, 0.0f);
            poseStack.pushPose();
            poseStack.translate(promptSize / -2.0f + 91.0f, -27.0f, 0.0f);
            AllGuiTextures.TRAIN_PROMPT_L.render(graphics, -3, 0);
            AllGuiTextures.TRAIN_PROMPT_R.render(graphics, promptSize, 0);
            graphics.blit(AllGuiTextures.TRAIN_PROMPT.location, 0, 0, 0,
                    AllGuiTextures.TRAIN_PROMPT.getStartX() + (128.0f - promptSize / 2.0f),
                    AllGuiTextures.TRAIN_PROMPT.getStartY(), promptSize, AllGuiTextures.TRAIN_PROMPT.getHeight(),
                    256, 256);
            poseStack.popPose();
            Font font = mc.font;
            if (currentPrompt != null && font.width(currentPrompt) < promptSize - 10) {
                poseStack.pushPose();
                poseStack.translate(font.width(currentPrompt) / -2.0f + 82.0f, -27.0f, 100.0f);
                if (currentPromptShadow) {
                    graphics.drawString(font, currentPrompt, 9, 4, 0x544D45);
                } else {
                    graphics.drawString(font, currentPrompt, 9, 4, 0x544D45, false);
                }
                poseStack.popPose();
            }
            poseStack.popPose();
        }

        if (locked) {
            int iconX = originX + (SPEED_BG_W - LOCK_W) / 2;
            int iconY = barY + (SPEED_BG_H - LOCK_H) / 2;
            graphics.blit(LOCK_ICON, iconX, iconY, 0, 0, LOCK_W, LOCK_H, LOCK_W, LOCK_H);
        }
    }
}