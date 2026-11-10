package com.lycoris.loconautics.mixin.client.gui;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.lycoris.loconautics.foundation.ui.GuiCroppedItem;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(GuiGraphics.class)
public abstract class GuiGraphicsItemCropMixin {

    @Shadow
    @Final
    private PoseStack pose;

    @WrapMethod(method = "renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;IIII)V")
    private void loconautics$cropToSlot(LivingEntity entity, Level level, ItemStack stack, int x, int y, int seed,
            int guiOffset, Operation<Void> original) {
        boolean crop = stack.getItem() instanceof GuiCroppedItem;
        if (crop) {
            Window window = Minecraft.getInstance().getWindow();
            float guiScale = (float) window.getGuiScale();
            Matrix4f matrix = this.pose.last().pose();
            Vector3f min = matrix.transformPosition(new Vector3f(x, y, 0.0f));
            Vector3f max = matrix.transformPosition(new Vector3f(x + 16, y + 16, 0.0f));
            min.mul(guiScale);
            max.mul(guiScale);
            int height = (int) (max.y - min.y);
            RenderSystem.enableScissor((int) min.x, window.getHeight() - (int) min.y - height, (int) (max.x - min.x), height);
        }
        original.call(entity, level, stack, x, y, seed, guiOffset);
        if (crop) {
            RenderSystem.disableScissor();
        }
    }
}
