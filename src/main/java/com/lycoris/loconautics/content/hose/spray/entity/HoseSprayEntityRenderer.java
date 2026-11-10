package com.lycoris.loconautics.content.hose.spray.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

public class HoseSprayEntityRenderer extends EntityRenderer<HoseSprayEntity> {

    public HoseSprayEntityRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(HoseSprayEntity entity) {
        return ResourceLocation.withDefaultNamespace("textures/misc/white.png");
    }

    @Override
    public void render(HoseSprayEntity entity, float yaw, float partialTicks, PoseStack ps,
                       MultiBufferSource buffer, int light) {
        super.render(entity, yaw, partialTicks, ps, buffer, light);
    }
}
