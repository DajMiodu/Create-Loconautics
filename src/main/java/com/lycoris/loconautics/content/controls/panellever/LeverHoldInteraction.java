package com.lycoris.loconautics.content.controls.panellever;

import com.lycoris.loconautics.content.controls.panel.PanelSlot;
import com.lycoris.loconautics.network.packets.controls.LeverSetPacket;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import dev.simulated_team.simulated.index.SimClickInteractions;
import dev.simulated_team.simulated.util.hold_interaction.BlockHoldInteraction;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.gui.UIRenderHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class LeverHoldInteraction extends BlockHoldInteraction {

    private static final LeverHoldInteraction INSTANCE = new LeverHoldInteraction();

    private static final int BAR_WIDTH = 100;

    private static final int BAR_HEIGHT = 14;

    private static final int TEXT_COLOR = 0x4A3A28;

    private PanelSlot slot = PanelSlot.BOTTOM_LEFT;

    private boolean inverted;

    private int signal;

    private int sentSignal;

    private float value;

    private float animatedValue;

    private float lastAnimatedValue;

    public static LeverHoldInteraction registered() {
        return SimClickInteractions.register(INSTANCE);
    }

    public static boolean gripped() {
        return INSTANCE.isActive();
    }

    public static boolean grab(Level level, Player player, BlockPos panelPos, PanelSlot slot, int power,
                               boolean inverted) {
        if (INSTANCE.isActive()) return false;
        INSTANCE.slot = slot;
        INSTANCE.inverted = inverted;
        INSTANCE.signal = INSTANCE.sentSignal = LeverPart.clamp(power);
        INSTANCE.value = INSTANCE.signal / (float) LeverPart.MAX_POWER;
        INSTANCE.animatedValue = INSTANCE.lastAnimatedValue = INSTANCE.value;
        INSTANCE.startHold(level, player, panelPos);
        return true;
    }

    @Override
    public boolean activeTick(Level level, LocalPlayer player) {
        if (getInteractionPos() == null) return true;
        if (!inInteractionRange(player, getInteractionPos().getCenter(), 0.0)) return true;
        lastAnimatedValue = animatedValue;
        animatedValue = animatedValue * 0.15f + value * 0.85f;
        return false;
    }

    @Override
    public boolean activeOnMouseMove(double yaw, double pitch) {
        value = Math.min(1f, Math.max(0f, value - (float) (pitch / 180.0)));
        signal = LeverPart.clamp(Math.round(value * LeverPart.MAX_POWER));
        if (signal != sentSignal) {
            sentSignal = signal;
            PacketDistributor.sendToServer(
                    new LeverSetPacket(getInteractionPos(), slot.ordinal(), signal));
        }
        return true;
    }

    @Override
    public void renderOverlay(GuiGraphics graphics, int width, int height, boolean hideGui) {
        if (hideGui) return;

        int x = width / 2 - BAR_WIDTH / 2 + 16;
        int y = height / 2 - BAR_HEIGHT / 2;
        PoseStack ms = graphics.pose();

        ms.pushPose();
        ms.translate(x + 50f, y + 7f, 0f);
        ms.mulPose(Axis.ZP.rotationDegrees(90f));
        ms.translate(-x - 50f, -y - 7f, 0f);
        AllGuiTextures.BRASS_FRAME_TL.render(graphics, x, y);
        AllGuiTextures.BRASS_FRAME_TR.render(graphics, x + BAR_WIDTH - 4, y);
        AllGuiTextures.BRASS_FRAME_BL.render(graphics, x, y + BAR_HEIGHT - 4);
        AllGuiTextures.BRASS_FRAME_BR.render(graphics, x + BAR_WIDTH - 4, y + BAR_HEIGHT - 4);
        UIRenderHelper.drawStretched(graphics, x, y + 4, 3, 6, 2,
                AllGuiTextures.BRASS_FRAME_LEFT);
        UIRenderHelper.drawStretched(graphics, x + BAR_WIDTH - 3, y + 4, 3, 6, 2,
                AllGuiTextures.BRASS_FRAME_RIGHT);
        UIRenderHelper.drawCropped(graphics, x + 4, y, 92, 3, 2,
                AllGuiTextures.BRASS_FRAME_TOP);
        UIRenderHelper.drawCropped(graphics, x + 4, y + BAR_HEIGHT - 3, 92, 3, 2,
                AllGuiTextures.BRASS_FRAME_BOTTOM);

        int barStep = AllGuiTextures.VALUE_SETTINGS_BAR.getWidth() - 1;
        for (int filled = 0; filled < 94; filled += barStep) {
            UIRenderHelper.drawCropped(graphics, x + 3 + filled, y + 3,
                    Math.min(barStep, 94 - filled), 8, 2,
                    AllGuiTextures.VALUE_SETTINGS_BAR);
        }
        ms.popPose();

        ms.pushPose();
        ms.translate(0f, 0f, 4f);
        float partialTick = AnimationTickHolder.getPartialTicks();
        float shown = lastAnimatedValue * (1f - partialTick) + animatedValue * partialTick;
        float cursorY = (1f - 2f * shown) * 3f * BAR_HEIGHT + 2f;
        int cursorX = x + 50 - 7;

        ms.pushPose();
        ms.translate(0f, y + 7 - 9 + cursorY, 0f);
        AllGuiTextures.VALUE_SETTINGS_CURSOR_LEFT.render(graphics, cursorX - 3, 0);
        UIRenderHelper.drawCropped(graphics, cursorX, 0, 14, 14, 2,
                AllGuiTextures.VALUE_SETTINGS_CURSOR);
        AllGuiTextures.VALUE_SETTINGS_CURSOR_RIGHT.render(graphics, cursorX + 14, 0);
        ms.translate(0f, 0f, 4f);
        graphics.drawString(Minecraft.getInstance().font, String.valueOf(inverted ? LeverPart.MAX_POWER - signal : signal),
                cursorX + 1, 3, TEXT_COLOR, false);
        ms.popPose();
        ms.popPose();
    }
}
