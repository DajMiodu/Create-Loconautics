package com.lycoris.loconautics.mixin.client.link;

import com.lycoris.loconautics.client.LoconauticsPartialModels;
import com.lycoris.loconautics.content.controls.panel.PanelLink;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.content.redstone.link.LinkRenderer;
import com.simibubi.create.content.redstone.link.RedstoneLinkBlock;
import com.simibubi.create.content.redstone.link.RedstoneLinkBlockEntity;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.render.CachedBuffers;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = LinkRenderer.class, remap = false)
public abstract class LinkRendererPanelMixin {

    private static final int[] LOCONAUTICS$PITCH = new int[6];

    private static final int[] LOCONAUTICS$YAW = new int[6];

    static {
        loconautics$rotation(Direction.UP, 0, 180);
        loconautics$rotation(Direction.DOWN, 180, 180);
        loconautics$rotation(Direction.NORTH, 270, 180);
        loconautics$rotation(Direction.SOUTH, 270, 0);
        loconautics$rotation(Direction.EAST, 270, 270);
        loconautics$rotation(Direction.WEST, 270, 90);
    }

    private static void loconautics$rotation(Direction facing, int pitch, int yaw) {
        LOCONAUTICS$PITCH[facing.ordinal()] = pitch;
        LOCONAUTICS$YAW[facing.ordinal()] = yaw;
    }

    @Inject(method = "renderOnBlockEntity", at = @At("HEAD"), cancellable = true)
    private static void loconautics$renderPanelSkin(SmartBlockEntity be, float partialTicks, PoseStack ms,
                                                    MultiBufferSource buffer, int light, int overlay,
                                                    CallbackInfo ci) {
        if (!(be instanceof RedstoneLinkBlockEntity) || be.isRemoved()) return;
        BlockState state = be.getBlockState();
        if (!PanelLink.isPanel(state)) return;

        PartialModel model = state.getValue(RedstoneLinkBlock.POWERED)
                ? LoconauticsPartialModels.PANEL_RECEIVER_POWERED
                : LoconauticsPartialModels.PANEL_RECEIVER;
        int facing = state.getValue(RedstoneLinkBlock.FACING).ordinal();

        ms.pushPose();
        ms.translate(0.5, 0.5, 0.5);
        ms.mulPose(Axis.YP.rotationDegrees(-LOCONAUTICS$YAW[facing]));
        ms.mulPose(Axis.XP.rotationDegrees(-LOCONAUTICS$PITCH[facing]));
        ms.translate(-0.5, -0.5, -0.5);
        CachedBuffers.partial(model, state)
                .light(light)
                .renderInto(ms, buffer.getBuffer(RenderType.cutout()));
        ms.popPose();
        ci.cancel();
    }

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private static void loconautics$hideFrequencyBoxes(CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !(mc.hitResult instanceof BlockHitResult hit)) return;
        if (PanelLink.isPanel(mc.level.getBlockState(hit.getBlockPos()))) ci.cancel();
    }
}
