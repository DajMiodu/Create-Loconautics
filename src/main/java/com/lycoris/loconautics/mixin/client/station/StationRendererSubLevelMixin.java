package com.lycoris.loconautics.mixin.client.station;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.trains.station.StationBlockEntity;
import com.simibubi.create.content.trains.station.StationRenderer;
import com.simibubi.create.content.trains.track.BezierTrackPointLocation;
import com.simibubi.create.content.trains.track.TrackTargetingBehaviour;
import com.simibubi.create.content.trains.track.TrackTargetingBehaviour.RenderedTrackOverlayType;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction.AxisDirection;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniondc;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = StationRenderer.class, remap = false)
public abstract class StationRendererSubLevelMixin {

    @WrapOperation(method = "renderSafe", at = @At(value = "INVOKE",
            target = "Lcom/simibubi/create/content/trains/track/TrackTargetingBehaviour;render(Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction$AxisDirection;Lcom/simibubi/create/content/trains/track/BezierTrackPointLocation;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IILcom/simibubi/create/content/trains/track/TrackTargetingBehaviour$RenderedTrackOverlayType;F)V"))
    private void loconautics$overlayInSubLevel(LevelAccessor level, BlockPos pos, AxisDirection direction,
                                               BezierTrackPointLocation bezier, PoseStack ms, MultiBufferSource buffer,
                                               int light, int overlay, RenderedTrackOverlayType type, float scale,
                                               Operation<Void> original, @Local(argsOnly = true) StationBlockEntity be) {
        ClientSubLevel track = level instanceof Level world && Sable.HELPER.getContaining(world, pos) instanceof ClientSubLevel client ? client : null;
        if (track == null || Sable.HELPER.getContainingClient(be) != null) {
            original.call(level, pos, direction, bezier, ms, buffer, light, overlay, type, scale);
            return;
        }
        BlockPos station = be.getBlockPos();
        Vec3 corner = track.renderPose().transformPosition(Vec3.atLowerCornerOf(pos));
        Quaterniondc q = track.renderPose().orientation();
        ms.pushPose();
        Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        // the stack already holds a float translation at plot distance, 2 blocks of precision, so start it over
        ms.last().pose().identity();
        ms.last().normal().identity();
        ms.translate(corner.x - camera.x, corner.y - camera.y, corner.z - camera.z);
        ms.mulPose(new Quaternionf(q.x(), q.y(), q.z(), q.w()));
        original.call(level, pos, direction, bezier, ms, buffer, light, overlay, type, scale);
        ms.popPose();
    }
}
