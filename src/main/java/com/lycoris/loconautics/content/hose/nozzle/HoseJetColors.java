package com.lycoris.loconautics.content.hose.nozzle;

import com.lycoris.loconautics.LoconauticsConstants;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.fluids.FluidStack;

@OnlyIn(Dist.CLIENT)
final class HoseJetColors {

    private static final int[] WATER_COLORS = {0xFF99CCFF, 0xFF88BEF5, 0xFFAEDBFF, 0xFF7FB8F0};
    private static final java.util.Map<net.minecraft.resources.ResourceLocation, int[]> FLUID_COLOR_CACHE =
            new java.util.HashMap<>();

    private HoseJetColors() {
    }

    public static int[] of(FluidStack stack) {
        net.minecraft.world.level.material.Fluid fluid = stack.getFluid();
        int[] override = com.lycoris.loconautics.content.hose.spray.HoseSprayColors.get(fluid);
        if (override != null) {
            return override;
        }
        if (fluid.builtInRegistryHolder().is(net.minecraft.tags.FluidTags.WATER)) {
            return WATER_COLORS;
        }
        var ext = net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions.of(fluid);
        net.minecraft.resources.ResourceLocation tex = ext.getStillTexture(stack);
        if (tex == null) {
            return WATER_COLORS;
        }
        return FLUID_COLOR_CACHE.computeIfAbsent(tex, t -> sampleTexture(t, ext.getTintColor(stack)));
    }

    private static int[] sampleTexture(net.minecraft.resources.ResourceLocation sprite, int tint) {
        int[] out = {0xFFFFFFFF, 0xFFFFFFFF, 0xFFFFFFFF, 0xFFFFFFFF};
        net.minecraft.resources.ResourceLocation png = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                sprite.getNamespace(), "textures/" + sprite.getPath() + ".png");
        var rm = net.minecraft.client.Minecraft.getInstance().getResourceManager();
        var resource = rm.getResource(png);
        if (resource.isEmpty()) {
            return out;
        }
        try (var in = resource.get().open();
             com.mojang.blaze3d.platform.NativeImage img = com.mojang.blaze3d.platform.NativeImage.read(in)) {
            int w = img.getWidth();
            int frame = Math.min(w, img.getHeight());
            float tr = ((tint >> 16) & 0xFF) / 255.0f;
            float tg = ((tint >> 8) & 0xFF) / 255.0f;
            float tb = (tint & 0xFF) / 255.0f;
            java.util.Random rnd = new java.util.Random();
            for (int i = 0; i < 4; i++) {
                for (int tryN = 0; tryN < 16; tryN++) {
                    int px = img.getPixelRGBA(rnd.nextInt(w), rnd.nextInt(frame));
                    if (((px >> 24) & 0xFF) < 20) {
                        continue;
                    }
                    int r = (int) ((px & 0xFF) * tr);
                    int g = (int) (((px >> 8) & 0xFF) * tg);
                    int b = (int) (((px >> 16) & 0xFF) * tb);
                    out[i] = 0xFF000000 | (r << 16) | (g << 8) | b;
                    break;
                }
            }
        } catch (Exception e) {
            LoconauticsConstants.LOGGER.debug("Nozzle sprite tint sample failed, using white fallback", e);
        }
        return out;
    }
}
